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
 * A vertical (and all-direction) signal link — the lithography "via". Where planar dust
 * keeps each layer independent, a via deliberately bridges signal between layers (and to
 * dust in its own layer). Carries a 0-15 level like dust, computed by the engine.
 */
public class TinyVia extends MiniBlock {

    @Override
    public MiniBlockType type() {
        return MiniBlockType.VIA;
    }

    /** Thin vertical green pillar (matches the circuit-wire theme) — a clean cross-layer "via". */
    @Override
    public BlockData renderData() {
        return (signal > 0 ? Material.LIME_CONCRETE : Material.GREEN_CONCRETE).createBlockData();
    }

    @Override
    public float[] modelScale() {
        return new float[]{0.25f, 1.0f, 0.25f}; // thin, full cell height
    }

    @Override
    public float[] modelTranslation() {
        return new float[]{0.375f, 0f, 0.375f}; // centered horizontally
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
        return 0; // one channel, links in all 6 directions — the deliberate cross-layer connector
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
