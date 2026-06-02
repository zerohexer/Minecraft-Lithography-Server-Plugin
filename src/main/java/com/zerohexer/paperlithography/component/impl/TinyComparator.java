package com.zerohexer.paperlithography.component.impl;

import com.zerohexer.paperlithography.component.MiniBlock;
import com.zerohexer.paperlithography.component.MiniBlockType;
import com.zerohexer.paperlithography.panel.GridPos;
import com.zerohexer.paperlithography.panel.Panel;
import com.zerohexer.paperlithography.render.ModelPart;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Comparator;
import org.bukkit.entity.Player;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Analog comparator. Reads the back face (main input) and the two perpendicular side faces.
 * Compare mode: output = back if back ≥ max(side), else 0. Subtract mode: output = max(0,
 * back − max(side)). Right-click toggles the mode. Output is emitted out {@link #facing}.
 */
public class TinyComparator extends MiniBlock {
    private boolean subtract = false;
    private int output = 0;

    @Override
    public MiniBlockType type() {
        return MiniBlockType.COMPARATOR;
    }

    @Override
    public BlockData renderData() {
        Comparator data = (Comparator) Material.COMPARATOR.createBlockData();
        // Vanilla model points opposite to our logical output, so flip it (matches repeater).
        data.setFacing(facing.getOppositeFace());
        data.setMode(subtract ? Comparator.Mode.SUBTRACT : Comparator.Mode.COMPARE);
        data.setPowered(output > 0);
        return data;
    }

    /** Chip body + a front nub showing direction; nub turns red in subtract mode, bright when output>0. */
    @Override
    public List<ModelPart> model() {
        boolean on = output > 0;
        BlockData nub = subtract
                ? (on ? Material.RED_CONCRETE : Material.RED_TERRACOTTA).createBlockData()
                : (on ? Material.LIME_CONCRETE : Material.GREEN_CONCRETE).createBlockData();
        return List.of(
                new ModelPart(Material.POLISHED_BLACKSTONE.createBlockData(),
                        new float[]{0.9f, 0.25f, 0.9f}, new float[]{0.05f, 0f, 0.05f}),
                new ModelPart(nub, new float[]{0.25f, 0.35f, 0.25f}, frontTrans()));
    }

    /** Translation that places the front indicator toward {@link #facing} (output). */
    private float[] frontTrans() {
        return switch (facing) {
            case NORTH -> new float[]{0.375f, 0.25f, 0.05f};
            case SOUTH -> new float[]{0.375f, 0.25f, 0.7f};
            case EAST -> new float[]{0.7f, 0.25f, 0.375f};
            case WEST -> new float[]{0.05f, 0.25f, 0.375f};
            default -> new float[]{0.375f, 0.25f, 0.375f};
        };
    }

    private BlockFace[] sideFaces() {
        return switch (facing) {
            case NORTH, SOUTH -> new BlockFace[]{BlockFace.EAST, BlockFace.WEST};
            case EAST, WEST -> new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH};
            default -> new BlockFace[]{};
        };
    }

    private int inputFrom(Panel panel, GridPos pos, BlockFace face) {
        MiniBlock n = panel.get(pos.offset(face));
        return n == null ? 0 : n.emittedPowerTo(face.getOppositeFace());
    }

    @Override
    public int emittedPowerTo(BlockFace dir) {
        return dir == facing ? output : 0;
    }

    @Override
    public boolean needsTicking() {
        return true; // keep the panel live so output changes propagate to wires next tick
    }

    @Override
    public boolean update(Panel panel, GridPos pos) {
        int back = inputFrom(panel, pos, facing.getOppositeFace());
        int side = 0;
        for (BlockFace sf : sideFaces()) {
            side = Math.max(side, inputFrom(panel, pos, sf));
        }
        int next = subtract ? Math.max(0, back - side) : (back >= side ? back : 0);
        if (next != output) {
            output = next;
            return true;
        }
        return false;
    }

    @Override
    public boolean onUse(Player player) {
        subtract = !subtract;
        return true;
    }

    @Override
    public boolean isDirectional() {
        return true;
    }

    @Override
    public String stateText() {
        return (subtract ? "subtract" : "compare") + " → " + facing.name().toLowerCase() + ", out " + output;
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        out.writeBoolean(subtract);
    }

    @Override
    public void read(DataInputStream in) throws IOException {
        subtract = in.readBoolean();
    }
}
