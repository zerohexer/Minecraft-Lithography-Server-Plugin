package com.zerohexer.paperlithography.render;

import com.zerohexer.paperlithography.Keys;
import com.zerohexer.paperlithography.component.MiniBlock;
import com.zerohexer.paperlithography.panel.GridPos;
import com.zerohexer.paperlithography.panel.Panel;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One player's live in-world view of one panel, focused on a single Y layer (to avoid 3D
 * occlusion — only that layer's cells are shown and clickable). The player punches the panel
 * to change layer. Each shown cell gets an interaction hitbox plus a visual (marker if empty,
 * component display if occupied). Entities are non-persistent and removed on {@link #close()}.
 */
public class EditSession {
    public final UUID playerId;
    public final Panel panel;
    public final Location base;
    private final World world;
    private final Plugin plugin;

    public int layer = 0;
    public boolean showAll = false;
    public boolean showGrid = true;

    private final Map<Integer, List<UUID>> visuals = new HashMap<>(); // composite model parts per cell
    private final Map<Integer, Boolean> isComponent = new HashMap<>();
    private final List<UUID> interactions = new ArrayList<>();

    // Marker shown for empty cells (single translucent part).
    private static final ModelPart MARKER_PART = new ModelPart(
            org.bukkit.Material.LIGHT_GRAY_STAINED_GLASS.createBlockData(),
            new float[]{0.45f, 0.45f, 0.45f}, new float[]{0.275f, 0.275f, 0.275f});
    private static final java.util.List<ModelPart> MARKER_MODEL = java.util.List.of(MARKER_PART);

    public EditSession(Plugin plugin, UUID playerId, Panel panel, Location base) {
        this.plugin = plugin;
        this.playerId = playerId;
        this.panel = panel;
        this.base = base.clone();
        this.world = base.getWorld();
    }

    /** Make an entity visible only to this session's owner (private 3D view). */
    private void privatize(Entity e) {
        e.setVisibleByDefault(false);
        Player p = Bukkit.getPlayer(playerId);
        if (p != null) p.showEntity(plugin, e);
    }

    private int loYter() {
        return showAll ? 0 : layer;
    }

    private int hiYInclusive() {
        return showAll ? GridPos.SIZE - 1 : layer;
    }

    public void open(Keys keys) {
        for (int x = 0; x < GridPos.SIZE; x++) {
            for (int y = loYter(); y <= hiYInclusive(); y++) {
                for (int z = 0; z < GridPos.SIZE; z++) {
                    GridPos g = new GridPos(x, y, z);
                    Interaction it = PanelRenderer.spawnInteraction(world, base, g, keys);
                    privatize(it);
                    interactions.add(it.getUniqueId());
                }
            }
        }
        refresh(keys);
    }

    /** Toggle between showing a single layer and the whole panel. */
    public void setShowAll(Keys keys, boolean all) {
        if (all == showAll) return;
        close();
        this.showAll = all;
        open(keys);
    }

    /** Toggle whether empty-cell grid markers are shown (off = only placed parts visible). */
    public void setShowGrid(Keys keys, boolean grid) {
        if (grid == showGrid) return;
        close();
        this.showGrid = grid;
        open(keys);
    }

    /** Switch the visible layer: despawn current entities and rebuild for the new layer. */
    public void setLayer(Keys keys, int newLayer) {
        if (newLayer < 0) newLayer = 0;
        if (newLayer > GridPos.SIZE - 1) newLayer = GridPos.SIZE - 1;
        if (showAll) { this.layer = newLayer; return; } // all layers already shown
        if (newLayer == layer && !visuals.isEmpty()) return;
        close();
        this.layer = newLayer;
        open(keys);
    }

    /** Sync the visible cells with the panel's current contents (in place, no flicker). */
    public void refresh(Keys keys) {
        for (int x = 0; x < GridPos.SIZE; x++) {
            for (int y = loYter(); y <= hiYInclusive(); y++) {
                for (int z = 0; z < GridPos.SIZE; z++) {
                    syncCell(keys, new GridPos(x, y, z));
                }
            }
        }
    }

    private void syncCell(Keys keys, GridPos g) {
        int key = g.pack();
        MiniBlock comp = panel.get(g);
        boolean want = comp != null;
        List<ModelPart> parts = want ? comp.model() : (showGrid ? MARKER_MODEL : List.of());

        List<UUID> existing = visuals.get(key);
        Boolean wasComponent = isComponent.get(key);

        // Reuse displays in place if the part count and kind match (no flicker on state changes).
        boolean reuse = existing != null && existing.size() == parts.size()
                && wasComponent != null && wasComponent == want;
        if (reuse) {
            for (int i = 0; i < parts.size(); i++) {
                Entity e = Bukkit.getEntity(existing.get(i));
                if (e instanceof BlockDisplay bd) {
                    bd.setBlock(parts.get(i).data);
                    bd.setTransformation(PanelRenderer.transform(parts.get(i).scale, parts.get(i).trans));
                } else {
                    reuse = false;
                    break;
                }
            }
        }
        if (reuse) return;

        // Rebuild: despawn old, spawn fresh parts.
        if (existing != null) {
            for (UUID id : existing) {
                Entity e = Bukkit.getEntity(id);
                if (e != null) e.remove();
            }
        }
        List<UUID> ids = new ArrayList<>(parts.size());
        for (ModelPart p : parts) {
            BlockDisplay bd = PanelRenderer.spawnPart(world, base, g, p, keys);
            privatize(bd);
            ids.add(bd.getUniqueId());
        }
        visuals.put(key, ids);
        isComponent.put(key, want);
    }

    public void close() {
        for (List<UUID> ids : new ArrayList<>(visuals.values())) {
            for (UUID id : ids) {
                Entity e = Bukkit.getEntity(id);
                if (e != null) e.remove();
            }
        }
        for (UUID id : interactions) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        visuals.clear();
        isComponent.clear();
        interactions.clear();
    }
}
