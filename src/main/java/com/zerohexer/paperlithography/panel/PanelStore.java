package com.zerohexer.paperlithography.panel;

import com.zerohexer.paperlithography.Keys;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns all panels at runtime and persists them.
 *
 * <p>Plain blocks (lodestone) cannot hold a PersistentDataContainer — only TileState
 * blocks can. So panel data is stored in the containing <em>chunk's</em> PDC, which
 * vanilla persists across restarts. We keep an in-memory map as the runtime source of
 * truth, load on chunk load, and flush on chunk unload / shutdown.
 */
public class PanelStore implements PanelLookup {
    private final Plugin plugin;
    private final Keys keys;

    private final Map<String, Panel> panels = new HashMap<>();
    private final Map<String, Location> locations = new HashMap<>();

    public PanelStore(Plugin plugin, Keys keys) {
        this.plugin = plugin;
        this.keys = keys;
    }

    private static String key(Location l) {
        return l.getWorld().getUID() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
    }

    public Panel getPanel(Block b) {
        return panels.get(key(b.getLocation()));
    }

    public boolean isPanel(Block b) {
        return panels.containsKey(key(b.getLocation()));
    }

    public Panel createPanel(Block b) {
        Panel p = new Panel();
        putPanel(b, p);
        return p;
    }

    public void putPanel(Block b, Panel p) {
        String k = key(b.getLocation());
        panels.put(k, p);
        locations.put(k, b.getLocation());
        p.bind(b.getWorld(), b.getX(), b.getY(), b.getZ(), this);
    }

    @Override
    public Panel panelAt(org.bukkit.World world, int bx, int by, int bz) {
        return panels.get(world.getUID() + ":" + bx + ":" + by + ":" + bz);
    }

    public Panel removePanel(Block b) {
        String k = key(b.getLocation());
        locations.remove(k);
        return panels.remove(k);
    }

    public Location locationOf(Panel p) {
        for (Map.Entry<String, Panel> e : panels.entrySet()) {
            if (e.getValue() == p) return locations.get(e.getKey());
        }
        return null;
    }

    public Collection<Panel> allPanels() {
        return panels.values();
    }

    // ---- chunk persistence ----

    public List<Panel> loadChunk(Chunk c) {
        List<Panel> loaded = new ArrayList<>();
        byte[] blob = c.getPersistentDataContainer().get(keys.chunkPanels, PersistentDataType.BYTE_ARRAY);
        if (blob == null) return loaded;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(blob))) {
            int count = in.readShort() & 0xFFFF;
            for (int i = 0; i < count; i++) {
                int lx = in.readByte() & 0xF;
                int y = in.readShort();
                int lz = in.readByte() & 0xF;
                int len = in.readInt();
                byte[] pd = new byte[len];
                in.readFully(pd);
                int wx = c.getX() * 16 + lx;
                int wz = c.getZ() * 16 + lz;
                Location loc = new Location(c.getWorld(), wx, y, wz);
                Panel panel = Panel.deserialize(pd);
                panel.bind(c.getWorld(), wx, y, wz, this);
                String k = key(loc);
                panels.put(k, panel);
                locations.put(k, loc);
                loaded.add(panel);
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to load panels in chunk " + c.getX() + "," + c.getZ() + ": " + ex);
        }
        return loaded;
    }

    public void saveChunk(Chunk c) {
        List<String> inChunk = new ArrayList<>();
        for (Map.Entry<String, Location> e : locations.entrySet()) {
            Location l = e.getValue();
            if (l.getWorld().equals(c.getWorld())
                    && (l.getBlockX() >> 4) == c.getX()
                    && (l.getBlockZ() >> 4) == c.getZ()) {
                inChunk.add(e.getKey());
            }
        }
        PersistentDataContainer pdc = c.getPersistentDataContainer();
        if (inChunk.isEmpty()) {
            pdc.remove(keys.chunkPanels);
            return;
        }
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             DataOutputStream o = new DataOutputStream(bos)) {
            o.writeShort(inChunk.size());
            for (String k : inChunk) {
                Location l = locations.get(k);
                byte[] pd = panels.get(k).serialize();
                o.writeByte(l.getBlockX() & 0xF);
                o.writeShort(l.getBlockY());
                o.writeByte(l.getBlockZ() & 0xF);
                o.writeInt(pd.length);
                o.write(pd);
            }
            o.flush();
            pdc.set(keys.chunkPanels, PersistentDataType.BYTE_ARRAY, bos.toByteArray());
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save panels in chunk " + c.getX() + "," + c.getZ() + ": " + ex);
        }
    }

    /** Save a chunk's panels to its PDC then drop them from the runtime maps. */
    public List<Panel> unloadChunk(Chunk c) {
        saveChunk(c);
        List<Panel> removed = new ArrayList<>();
        List<String> keysInChunk = new ArrayList<>();
        for (Map.Entry<String, Location> e : locations.entrySet()) {
            Location l = e.getValue();
            if (l.getWorld().equals(c.getWorld())
                    && (l.getBlockX() >> 4) == c.getX()
                    && (l.getBlockZ() >> 4) == c.getZ()) {
                keysInChunk.add(e.getKey());
            }
        }
        for (String k : keysInChunk) {
            removed.add(panels.remove(k));
            locations.remove(k);
        }
        return removed;
    }

    public void saveAllLoaded() {
        List<Chunk> done = new ArrayList<>();
        for (Location l : new ArrayList<>(locations.values())) {
            Chunk c = l.getChunk();
            if (!done.contains(c)) {
                done.add(c);
                saveChunk(c);
            }
        }
    }
}
