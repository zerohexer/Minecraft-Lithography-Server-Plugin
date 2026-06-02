package com.zerohexer.paperlithography.panel;

import org.bukkit.block.BlockFace;

/**
 * Immutable position inside a panel's 8x8x8 grid. Each axis is 0..7.
 * Packs into 9 bits: (x &lt;&lt; 6) | (y &lt;&lt; 3) | z.
 */
public final class GridPos {
    // 4x4x4 = 64 cells. Bigger, clickable cells for a usable in-world editor.
    // (Packing reserves 3 bits/axis, so this can grow back to 8 without format changes.)
    public static final int SIZE = 4;

    public final int x, y, z;

    public GridPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public boolean inBounds() {
        return x >= 0 && x < SIZE && y >= 0 && y < SIZE && z >= 0 && z < SIZE;
    }

    public GridPos offset(BlockFace face) {
        return new GridPos(x + face.getModX(), y + face.getModY(), z + face.getModZ());
    }

    public int pack() {
        return (x << 6) | (y << 3) | z;
    }

    public static GridPos unpack(int p) {
        return new GridPos((p >> 6) & 7, (p >> 3) & 7, p & 7);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GridPos g)) return false;
        return x == g.x && y == g.y && z == g.z;
    }

    @Override
    public int hashCode() {
        return pack();
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + "," + z + ")";
    }
}
