package com.zerohexer.paperlithography.util;

import org.bukkit.block.BlockFace;

/** Yaw → cardinal direction helper for orienting placed components. */
public final class Directions {
    private Directions() {
    }

    /** The horizontal direction the player is looking, as a cardinal BlockFace. */
    public static BlockFace fromYaw(float yaw) {
        double rot = (yaw % 360 + 360) % 360;
        if (rot >= 315 || rot < 45) return BlockFace.SOUTH;
        if (rot < 135) return BlockFace.WEST;
        if (rot < 225) return BlockFace.NORTH;
        return BlockFace.EAST;
    }
}
