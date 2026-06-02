package com.zerohexer.paperlithography.render;

import com.zerohexer.paperlithography.Keys;
import com.zerohexer.paperlithography.panel.Panel;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks which panels each player is editing. A player may have several panels open at
 * once (keyed by panel base location), so panels can be viewed and linked side by side.
 * Each player's in-world view entities are private (visible only to them).
 */
public class EditSessionManager {
    private final Plugin plugin;
    private final Keys keys;
    // player -> (panelBaseKey -> session)
    private final Map<UUID, Map<String, EditSession>> sessions = new HashMap<>();

    public EditSessionManager(Plugin plugin, Keys keys) {
        this.plugin = plugin;
        this.keys = keys;
    }

    private static String baseKey(Location base) {
        return base.getWorld().getUID() + ":" + base.getBlockX() + ":" + base.getBlockY() + ":" + base.getBlockZ();
    }

    public boolean isEditing(Player player, Location base) {
        Map<String, EditSession> m = sessions.get(player.getUniqueId());
        return m != null && m.containsKey(baseKey(base));
    }

    /** Set the 3D view to single-layer or all-layers, if a session is open. */
    public void setShowAll(Player player, Location base, boolean all) {
        Map<String, EditSession> m = sessions.get(player.getUniqueId());
        if (m == null) return;
        EditSession s = m.get(baseKey(base));
        if (s != null) s.setShowAll(keys, all);
    }

    public boolean isShowAll(Player player, Location base) {
        Map<String, EditSession> m = sessions.get(player.getUniqueId());
        if (m == null) return false;
        EditSession s = m.get(baseKey(base));
        return s != null && s.showAll;
    }

    public void setShowGrid(Player player, Location base, boolean grid) {
        Map<String, EditSession> m = sessions.get(player.getUniqueId());
        if (m == null) return;
        EditSession s = m.get(baseKey(base));
        if (s != null) s.setShowGrid(keys, grid);
    }

    public boolean isShowGrid(Player player, Location base) {
        Map<String, EditSession> m = sessions.get(player.getUniqueId());
        if (m == null) return true; // default: grid shown
        EditSession s = m.get(baseKey(base));
        return s == null || s.showGrid;
    }

    /** Current layer of a player's open 3D session, or -1 if none is open. */
    public int getLayer(Player player, Location base) {
        Map<String, EditSession> m = sessions.get(player.getUniqueId());
        if (m == null) return -1;
        EditSession s = m.get(baseKey(base));
        return s == null ? -1 : s.layer;
    }

    /** Set the visible layer of a player's open 3D session, if any. Returns the new layer, or -1. */
    public int setLayer(Player player, Location base, int layer) {
        Map<String, EditSession> m = sessions.get(player.getUniqueId());
        if (m == null) return -1;
        EditSession s = m.get(baseKey(base));
        if (s == null) return -1;
        s.setLayer(keys, layer);
        return s.layer;
    }

    /** Change the visible layer of a player's open 3D session. Returns the new layer, or -1. */
    public int cycleLayer(Player player, Location base, int delta) {
        Map<String, EditSession> m = sessions.get(player.getUniqueId());
        if (m == null) return -1;
        EditSession s = m.get(baseKey(base));
        if (s == null) return -1;
        int target = s.layer + delta;
        if (target < 0) target = 0;
        if (target > com.zerohexer.paperlithography.panel.GridPos.SIZE - 1) {
            target = com.zerohexer.paperlithography.panel.GridPos.SIZE - 1;
        }
        s.setLayer(keys, target);
        return s.layer;
    }

    /** Toggle a panel's editor for this player. Returns true if it is now open, false if closed. */
    public boolean toggle(Player player, Panel panel, Location base) {
        Map<String, EditSession> m = sessions.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        String key = baseKey(base);
        EditSession existing = m.remove(key);
        if (existing != null) {
            existing.close();
            return false;
        }
        EditSession s = new EditSession(plugin, player.getUniqueId(), panel, base);
        s.open(keys);
        m.put(key, s);
        return true;
    }

    /** Open a full-scale immersive build session for a panel, rendered at {@code renderBase}. */
    public void openBuild(Player player, Panel panel, Location panelBase, Location renderBase) {
        Map<String, EditSession> m = sessions.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        String key = baseKey(panelBase);
        EditSession existing = m.remove(key);
        if (existing != null) existing.close();
        EditSession s = new EditSession(plugin, player.getUniqueId(), panel, panelBase, renderBase, 1.0, 2.0, true);
        s.open(keys);
        m.put(key, s);
    }

    /** Close every panel this player has open. */
    public void close(Player player) {
        Map<String, EditSession> m = sessions.remove(player.getUniqueId());
        if (m != null) {
            for (EditSession s : m.values()) s.close();
        }
    }

    public void closeAll() {
        for (Map<String, EditSession> m : sessions.values()) {
            for (EditSession s : m.values()) s.close();
        }
        sessions.clear();
    }

    /** Re-render every session currently viewing the given panel (any player). */
    public void refreshPanel(Panel panel) {
        for (Map<String, EditSession> m : sessions.values()) {
            for (EditSession s : m.values()) {
                if (s.panel == panel) s.refresh(keys);
            }
        }
    }

    /** Close every session viewing the given panel (e.g. it was broken or unloaded). */
    public void closeByPanel(Panel panel) {
        for (Map<String, EditSession> m : sessions.values()) {
            m.values().removeIf(s -> {
                if (s.panel == panel) {
                    s.close();
                    return true;
                }
                return false;
            });
        }
    }
}
