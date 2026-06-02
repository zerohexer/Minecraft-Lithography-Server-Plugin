package com.zerohexer.paperlithography.sim;

import com.zerohexer.paperlithography.component.MiniBlock;
import com.zerohexer.paperlithography.panel.GridPos;
import com.zerohexer.paperlithography.panel.Panel;
import com.zerohexer.paperlithography.render.EditSessionManager;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Drives signal propagation for "active" panels every game tick.
 *
 * <p>Per tick, for each active panel:
 * <ol>
 *   <li>Advance timed components (repeater/torch/button) against last tick's network.</li>
 *   <li>Recompute the dust network fresh (reset to 0, BFS from sources with decay) —
 *       resetting each tick avoids stale mutual-support loops.</li>
 *   <li>Recompute combinational sinks (lamps).</li>
 *   <li>Refresh visuals for any player editing the panel.</li>
 * </ol>
 *
 * <p>Panels containing timed components stay active (cheap: panels are &le;512 cells).
 * Combinational-only panels settle and leave the active set after one tick.
 */
public class PropagationEngine {
    private static final int MAX_DUST_PASSES = 32;

    private final Plugin plugin;
    private EditSessionManager sessions;
    private final Set<Panel> active = Collections.newSetFromMap(new IdentityHashMap<>());
    private BukkitTask task;

    public PropagationEngine(Plugin plugin) {
        this.plugin = plugin;
    }

    public void setSessions(EditSessionManager sessions) {
        this.sessions = sessions;
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickAll, 1L, 1L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    public void markDirty(Panel panel) {
        if (panel != null) active.add(panel);
    }

    public void deactivate(Panel panel) {
        active.remove(panel);
    }

    private void tickAll() {
        if (active.isEmpty()) return;
        for (Panel p : new ArrayList<>(active)) {
            if (!tickPanel(p)) {
                active.remove(p);
            }
        }
    }

    private boolean tickPanel(Panel panel) {
        boolean visualChanged = false;
        boolean needMore = false;

        // 1. Timed components.
        for (Map.Entry<GridPos, MiniBlock> e : panel.components().entrySet()) {
            MiniBlock b = e.getValue();
            if (b.needsTicking()) {
                if (b.tick(panel, e.getKey())) visualChanged = true;
            }
            if (b.needsTicking()) needMore = true;
        }

        // 2. Wire network (dust + vias).
        if (recomputeWires(panel)) visualChanged = true;

        // 3. Combinational sinks.
        for (Map.Entry<GridPos, MiniBlock> e : panel.components().entrySet()) {
            if (e.getValue().update(panel, e.getKey())) visualChanged = true;
        }

        if (visualChanged && sessions != null) {
            sessions.refreshPanel(panel);
        }
        // When a linked edge changes, wake neighbours so the signal keeps crossing.
        if (visualChanged) {
            for (Panel n : panel.neighborPanels()) active.add(n);
        }
        // Linked panels stay active so cross-boundary signals propagate continuously.
        return needMore || panel.hasNeighborPanel();
    }

    /**
     * Rebuild wire (dust + via) levels from scratch: inject from non-wire emitters along
     * each wire's connectable faces, then relax wire→wire with −1 decay. Two adjacent wires
     * connect across face f if either wire allows that direction (so a via bridges layers
     * even though planar dust does not).
     */
    boolean recomputeWires(Panel panel) {
        List<Map.Entry<GridPos, MiniBlock>> wires = new ArrayList<>();
        for (Map.Entry<GridPos, MiniBlock> e : panel.components().entrySet()) {
            if (e.getValue().isWire()) wires.add(e);
        }
        if (wires.isEmpty()) return false;

        // Snapshot current channel levels for change detection.
        List<int[]> before = new ArrayList<>(wires.size());
        for (Map.Entry<GridPos, MiniBlock> e : wires) {
            MiniBlock w = e.getValue();
            int[] b = new int[w.channelCount()];
            for (int c = 0; c < b.length; c++) b[c] = w.channelLevel(c);
            before.add(b);
        }

        // Inject from non-wire emitters, per channel.
        for (Map.Entry<GridPos, MiniBlock> e : wires) {
            GridPos pos = e.getKey();
            MiniBlock w = e.getValue();
            for (int c = 0; c < w.channelCount(); c++) {
                int inject = 0;
                for (BlockFace f : Panel.FACES) {
                    if (w.channelForFace(f) != c) continue;
                    MiniBlock n = panel.get(pos.offset(f));
                    if (n == null || n.isWire()) continue;
                    inject = Math.max(inject, n.emittedPowerTo(f.getOppositeFace()));
                }
                w.setChannelLevel(c, inject);
            }
        }

        // Relax wire-to-wire with decay. Link across face f if either side exposes a channel
        // there; route the received signal into the channel f maps to (default 0).
        for (int pass = 0; pass < MAX_DUST_PASSES; pass++) {
            boolean changed = false;
            for (Map.Entry<GridPos, MiniBlock> e : wires) {
                GridPos pos = e.getKey();
                MiniBlock w = e.getValue();
                for (BlockFace f : Panel.FACES) {
                    MiniBlock n = panel.get(pos.offset(f));
                    if (n == null || !n.isWire()) continue;
                    int wc = w.channelForFace(f);
                    int nc = n.channelForFace(f.getOppositeFace());
                    if (wc < 0 && nc < 0) continue;
                    int recv = wc >= 0 ? wc : 0;
                    int src = nc >= 0 ? nc : 0;
                    int lvl = n.channelLevel(src) - 1;
                    if (lvl > w.channelLevel(recv)) {
                        w.setChannelLevel(recv, lvl);
                        changed = true;
                    }
                }
            }
            if (!changed) break;
        }

        // Recompute each wire's connection mask (faces with an adjacent component on a
        // connectable channel) for trace rendering; treat a mask change as a visual change.
        boolean changed = false;
        for (int i = 0; i < wires.size(); i++) {
            GridPos pos = wires.get(i).getKey();
            MiniBlock w = wires.get(i).getValue();
            int mask = 0;
            for (int fi = 0; fi < Panel.FACES.length; fi++) {
                BlockFace f = Panel.FACES[fi];
                if (w.channelForFace(f) < 0) continue;
                if (panel.get(pos.offset(f)) != null) mask |= (1 << fi);
            }
            if (mask != w.connMask) {
                w.connMask = mask;
                changed = true;
            }
            int[] b = before.get(i);
            for (int c = 0; c < b.length; c++) {
                if (w.channelLevel(c) != b[c]) changed = true;
            }
        }
        return changed;
    }
}
