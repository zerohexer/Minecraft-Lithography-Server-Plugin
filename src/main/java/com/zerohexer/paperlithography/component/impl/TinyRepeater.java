package com.zerohexer.paperlithography.component.impl;

import com.zerohexer.paperlithography.component.MiniBlock;
import com.zerohexer.paperlithography.component.MiniBlockType;
import com.zerohexer.paperlithography.panel.GridPos;
import com.zerohexer.paperlithography.panel.Panel;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Repeater;
import org.bukkit.entity.Player;

import com.zerohexer.paperlithography.render.ModelPart;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Directional diode with delay. Reads input from its back face, drives output out
 * the front ({@link #facing}) after {@code delay} ticks. Right-click cycles delay 1-4.
 */
public class TinyRepeater extends MiniBlock {
    private boolean out = false;
    private int delay = 1;       // 1..4 game ticks
    private int countdown = -1;

    @Override
    public MiniBlockType type() {
        return MiniBlockType.REPEATER;
    }

    @Override
    public BlockData renderData() {
        Repeater data = (Repeater) Material.REPEATER.createBlockData();
        data.setDelay(delay);
        // Vanilla repeater model points opposite to our logical output, so flip it.
        data.setFacing(facing.getOppositeFace());
        data.setPowered(out);
        return data;
    }

    /** Chip body + front indicator (direction + power), plus a bedrock lock-bar when locked. */
    @Override
    public List<ModelPart> model() {
        List<ModelPart> parts = new ArrayList<>();
        parts.add(new ModelPart(org.bukkit.Material.POLISHED_BLACKSTONE.createBlockData(),
                new float[]{0.9f, 0.25f, 0.9f}, new float[]{0.05f, 0f, 0.05f}));
        BlockData nub = (out ? org.bukkit.Material.LIME_CONCRETE : org.bukkit.Material.GRAY_CONCRETE).createBlockData();
        parts.add(new ModelPart(nub, new float[]{0.25f, 0.35f, 0.25f}, frontTrans()));
        if (locked) {
            boolean ew = facing == BlockFace.EAST || facing == BlockFace.WEST;
            float[] scale = ew ? new float[]{0.2f, 0.1f, 0.85f} : new float[]{0.85f, 0.1f, 0.2f};
            float[] trans = ew ? new float[]{0.4f, 0.5f, 0.075f} : new float[]{0.075f, 0.5f, 0.4f};
            parts.add(new ModelPart(org.bukkit.Material.BEDROCK.createBlockData(), scale, trans));
        }
        return parts;
    }

    private float[] frontTrans() {
        return switch (facing) {
            case NORTH -> new float[]{0.375f, 0.25f, 0.05f};
            case SOUTH -> new float[]{0.375f, 0.25f, 0.7f};
            case EAST -> new float[]{0.7f, 0.25f, 0.375f};
            case WEST -> new float[]{0.05f, 0.25f, 0.375f};
            default -> new float[]{0.375f, 0.25f, 0.375f};
        };
    }

    private boolean inputPowered(Panel panel, GridPos pos) {
        BlockFace back = facing.getOppositeFace();
        MiniBlock n = panel.get(pos.offset(back));
        // From the neighbour behind us, this repeater lies in direction `facing`.
        return n != null && n.emittedPowerTo(facing) > 0;
    }

    private BlockFace[] sideFaces() {
        return switch (facing) {
            case NORTH, SOUTH -> new BlockFace[]{BlockFace.EAST, BlockFace.WEST};
            case EAST, WEST -> new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH};
            default -> new BlockFace[]{};
        };
    }

    /** Locked when a powered repeater/comparator points into one of our side faces. */
    private boolean isLocked(Panel panel, GridPos pos) {
        for (BlockFace side : sideFaces()) {
            MiniBlock n = panel.get(pos.offset(side));
            if ((n instanceof TinyRepeater || n instanceof TinyComparator)
                    && n.emittedPowerTo(side.getOppositeFace()) > 0) {
                return true;
            }
        }
        return false;
    }

    public boolean isLockedFlag() {
        return locked;
    }

    private transient boolean locked = false;

    @Override
    public int emittedPowerTo(BlockFace dir) {
        return (dir == facing && out) ? 15 : 0;
    }

    @Override
    public boolean needsTicking() {
        return true;
    }

    @Override
    public boolean tick(Panel panel, GridPos pos) {
        // While locked, freeze the output (ignore input changes) — repeater latch / memory.
        boolean nowLocked = isLocked(panel, pos);
        if (nowLocked != locked) {
            locked = nowLocked;
        }
        if (locked) {
            countdown = -1;
            return false;
        }
        boolean target = inputPowered(panel, pos);
        if (target != out) {
            if (countdown < 0) {
                countdown = delay;
            } else {
                countdown--;
                if (countdown <= 0) {
                    out = target;
                    countdown = -1;
                    return true;
                }
            }
        } else {
            countdown = -1;
        }
        return false;
    }

    @Override
    public boolean onUse(Player player) {
        delay = (delay >= 4) ? 1 : delay + 1;
        return true;
    }

    @Override
    public boolean isDirectional() {
        return true;
    }

    @Override
    public String stateText() {
        return "delay " + delay + " → " + facing.name().toLowerCase()
                + (out ? " (on)" : "") + (locked ? " [LOCKED]" : "");
    }

    @Override
    public void write(DataOutputStream o) throws IOException {
        o.writeBoolean(out);
        o.writeByte(delay);
    }

    @Override
    public void read(DataInputStream in) throws IOException {
        out = in.readBoolean();
        delay = in.readByte();
        if (delay < 1 || delay > 4) delay = 1;
    }
}
