package com.zerohexer.paperlithography.panel;

import com.zerohexer.paperlithography.component.MiniBlock;
import com.zerohexer.paperlithography.component.MiniBlockType;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The logical contents of one panel: a sparse 8x8x8 grid of components.
 * Pure data + (de)serialization; world placement and ticking live elsewhere.
 */
public class Panel {
    /** The six axis-aligned neighbour directions, in a stable serialization-friendly order. */
    public static final BlockFace[] FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST,
            BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    private static final byte FORMAT_VERSION = 1;

    private final Map<GridPos, MiniBlock> components = new HashMap<>();

    // World binding for cross-panel (linked) neighbour lookups. Set by PanelStore.
    private transient World world;
    private transient int bx, by, bz;
    private transient PanelLookup lookup;

    /** Bind this panel to its world position and a lookup, enabling adjacency linking. */
    public void bind(World world, int bx, int by, int bz, PanelLookup lookup) {
        this.world = world;
        this.bx = bx;
        this.by = by;
        this.bz = bz;
        this.lookup = lookup;
    }

    /**
     * Component at a grid position. If the position is exactly one cell out of bounds and a
     * panel is placed against that face in the world, the adjacent panel's edge cell is
     * returned — this is how linked panels pass signals across their shared boundary.
     */
    public MiniBlock get(GridPos p) {
        if (p == null) return null;
        if (p.inBounds()) return components.get(p);
        if (lookup == null || world == null) return null;

        int ox = 0, oy = 0, oz = 0, cx = p.x, cy = p.y, cz = p.z, out = 0;
        if (p.x == -1) { ox = -1; cx = GridPos.SIZE - 1; out++; }
        else if (p.x == GridPos.SIZE) { ox = 1; cx = 0; out++; }
        else if (p.x < 0 || p.x >= GridPos.SIZE) return null;
        if (p.y == -1) { oy = -1; cy = GridPos.SIZE - 1; out++; }
        else if (p.y == GridPos.SIZE) { oy = 1; cy = 0; out++; }
        else if (p.y < 0 || p.y >= GridPos.SIZE) return null;
        if (p.z == -1) { oz = -1; cz = GridPos.SIZE - 1; out++; }
        else if (p.z == GridPos.SIZE) { oz = 1; cz = 0; out++; }
        else if (p.z < 0 || p.z >= GridPos.SIZE) return null;
        if (out != 1) return null; // only bridge across a single face, not edges/corners

        Panel np = lookup.panelAt(world, bx + ox, by + oy, bz + oz);
        return np == null ? null : np.components.get(new GridPos(cx, cy, cz));
    }

    /** Panels placed directly against this one's six faces (for dirty propagation). */
    public List<Panel> neighborPanels() {
        List<Panel> out = new ArrayList<>();
        if (lookup == null || world == null) return out;
        int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] o : d) {
            Panel np = lookup.panelAt(world, bx + o[0], by + o[1], bz + o[2]);
            if (np != null) out.add(np);
        }
        return out;
    }

    public boolean hasNeighborPanel() {
        return !neighborPanels().isEmpty();
    }

    public Map<GridPos, MiniBlock> components() {
        return components;
    }

    public void set(GridPos p, MiniBlock b) {
        if (p.inBounds()) components.put(p, b);
    }

    public void remove(GridPos p) {
        components.remove(p);
    }

    public boolean isEmpty() {
        return components.isEmpty();
    }

    public int size() {
        return components.size();
    }

    // ---- serialization ----

    public byte[] serialize() {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             DataOutputStream o = new DataOutputStream(bos)) {
            o.writeByte(FORMAT_VERSION);
            o.writeShort(components.size());
            for (Map.Entry<GridPos, MiniBlock> e : components.entrySet()) {
                o.writeShort(e.getKey().pack());
                MiniBlock b = e.getValue();
                o.writeByte(b.type().id);
                o.writeByte(faceId(b.getFacing()));
                b.write(o);
            }
            o.flush();
            return bos.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException("Panel serialize failed", ex);
        }
    }

    public static Panel deserialize(byte[] data) {
        Panel panel = new Panel();
        if (data == null || data.length == 0) return panel;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            in.readByte(); // format version (only 1 exists)
            int count = in.readShort() & 0xFFFF;
            for (int i = 0; i < count; i++) {
                GridPos pos = GridPos.unpack(in.readShort() & 0xFFFF);
                int typeId = in.readByte();
                BlockFace face = faceById(in.readByte());
                MiniBlockType t = MiniBlockType.byId(typeId);
                if (t == null) continue;
                MiniBlock b = t.create();
                b.setFacing(face);
                b.read(in);
                panel.components.put(pos, b);
            }
        } catch (IOException ex) {
            throw new RuntimeException("Panel deserialize failed", ex);
        }
        return panel;
    }

    private static int faceId(BlockFace f) {
        for (int i = 0; i < FACES.length; i++) {
            if (FACES[i] == f) return i;
        }
        return 0;
    }

    private static BlockFace faceById(int id) {
        return (id >= 0 && id < FACES.length) ? FACES[id] : BlockFace.NORTH;
    }
}
