package com.zerohexer.paperlithography.component.impl;

import com.zerohexer.paperlithography.component.MiniBlock;
import com.zerohexer.paperlithography.component.MiniBlockType;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Redstone wire. Carries a 0-15 signal level. The actual level is computed by the
 * {@code PropagationEngine} (BFS from sources with per-cell decay) to avoid stale
 * mutual-support loops — {@link #signal} just stores the result for rendering/emission.
 */
public class TinyDust extends MiniBlock {

    @Override
    public MiniBlockType type() {
        return MiniBlockType.DUST;
    }

    @Override
    public BlockData renderData() {
        // Used only as a fallback; the real visual is the composite trace in model().
        return (signal > 0 ? Material.LIME_CONCRETE : Material.GREEN_CONCRETE).createBlockData();
    }

    /** Circuit-green trace: a centre pad plus a flat bar toward each connected face. */
    @Override
    public java.util.List<com.zerohexer.paperlithography.render.ModelPart> model() {
        java.util.List<com.zerohexer.paperlithography.render.ModelPart> parts = new java.util.ArrayList<>();
        BlockData mat = (signal > 0 ? Material.LIME_CONCRETE : Material.GREEN_CONCRETE).createBlockData();
        float h = 0.12f;
        // centre pad
        parts.add(new com.zerohexer.paperlithography.render.ModelPart(mat,
                new float[]{0.3f, h, 0.3f}, new float[]{0.35f, 0f, 0.35f}));
        // bars toward connected horizontal faces (Panel.FACES order: N,S,E,W,U,D)
        for (int i = 0; i < com.zerohexer.paperlithography.panel.Panel.FACES.length; i++) {
            if ((connMask & (1 << i)) == 0) continue;
            BlockFace f = com.zerohexer.paperlithography.panel.Panel.FACES[i];
            switch (f) {
                case NORTH -> parts.add(bar(mat, new float[]{0.3f, h, 0.5f}, new float[]{0.35f, 0f, 0f}));
                case SOUTH -> parts.add(bar(mat, new float[]{0.3f, h, 0.5f}, new float[]{0.35f, 0f, 0.5f}));
                case EAST -> parts.add(bar(mat, new float[]{0.5f, h, 0.3f}, new float[]{0.5f, 0f, 0.35f}));
                case WEST -> parts.add(bar(mat, new float[]{0.5f, h, 0.3f}, new float[]{0f, 0f, 0.35f}));
                default -> { }
            }
        }
        return parts;
    }

    private static com.zerohexer.paperlithography.render.ModelPart bar(BlockData mat, float[] scale, float[] trans) {
        return new com.zerohexer.paperlithography.render.ModelPart(mat, scale, trans);
    }

    @Override
    public int emittedPowerTo(BlockFace dir) {
        return signal;
    }

    @Override
    public boolean isWire() {
        return true;
    }

    @Override
    public int channelForFace(BlockFace face) {
        // Planar: one channel, links only within its own layer (no straight up/down merging).
        return (face == BlockFace.NORTH || face == BlockFace.SOUTH
                || face == BlockFace.EAST || face == BlockFace.WEST) ? 0 : -1;
    }

    /** Engine sets the resolved level here. */
    public void setLevel(int level) {
        this.signal = Math.max(0, Math.min(15, level));
    }

    public int getLevel() {
        return signal;
    }

    @Override
    public String stateText() {
        return "level " + signal;
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        out.writeByte(signal);
    }

    @Override
    public void read(DataInputStream in) throws IOException {
        signal = in.readByte();
    }
}
