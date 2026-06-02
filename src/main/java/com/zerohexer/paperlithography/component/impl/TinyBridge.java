package com.zerohexer.paperlithography.component.impl;

import com.zerohexer.paperlithography.component.MiniBlock;
import com.zerohexer.paperlithography.component.MiniBlockType;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;

/**
 * Crossover. Carries three independent signal channels in one cell — X (east/west),
 * Z (north/south), Y (up/down) — so two (or three) wires can cross through the same cell
 * without their signals mixing. The missing primitive for same-layer crossovers.
 */
public class TinyBridge extends MiniBlock {
    // channel 0 = X (E/W), 1 = Z (N/S), 2 = Y (U/D)
    private final int[] ch = new int[3];

    @Override
    public MiniBlockType type() {
        return MiniBlockType.BRIDGE;
    }

    @Override
    public BlockData renderData() {
        // Fallback; real visual is the two crossing bars in model().
        return (Math.max(ch[0], ch[1]) > 0 ? Material.LIME_CONCRETE : Material.GREEN_CONCRETE).createBlockData();
    }

    /** Two thin crossing green bars (E-W over/under N-S) — clearly a crossover, not a junction. */
    @Override
    public java.util.List<com.zerohexer.paperlithography.render.ModelPart> model() {
        BlockData x = (ch[0] > 0 ? Material.LIME_CONCRETE : Material.GREEN_CONCRETE).createBlockData();
        BlockData z = (ch[1] > 0 ? Material.LIME_CONCRETE : Material.GREEN_CONCRETE).createBlockData();
        return java.util.List.of(
                // E-W bar, low
                new com.zerohexer.paperlithography.render.ModelPart(x,
                        new float[]{1.0f, 0.12f, 0.25f}, new float[]{0f, 0.0f, 0.375f}),
                // N-S bar, slightly raised so it reads as crossing over
                new com.zerohexer.paperlithography.render.ModelPart(z,
                        new float[]{0.25f, 0.12f, 1.0f}, new float[]{0.375f, 0.16f, 0f}));
    }

    @Override
    public boolean isWire() {
        return true;
    }

    @Override
    public int channelCount() {
        return 3;
    }

    @Override
    public int channelForFace(BlockFace face) {
        return switch (face) {
            case EAST, WEST -> 0;
            case NORTH, SOUTH -> 1;
            case UP, DOWN -> 2;
            default -> -1;
        };
    }

    @Override
    public int channelLevel(int channel) {
        return (channel >= 0 && channel < 3) ? ch[channel] : 0;
    }

    @Override
    public void setChannelLevel(int channel, int level) {
        if (channel >= 0 && channel < 3) ch[channel] = Math.max(0, Math.min(15, level));
    }

    @Override
    public int emittedPowerTo(BlockFace dir) {
        int c = channelForFace(dir);
        return c < 0 ? 0 : ch[c];
    }

    @Override
    public String stateText() {
        return "X=" + ch[0] + " Z=" + ch[1] + " Y=" + ch[2];
    }
}
