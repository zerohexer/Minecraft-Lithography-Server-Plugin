package com.zerohexer.paperlithography.component.impl;

import com.zerohexer.paperlithography.component.MiniBlock;
import com.zerohexer.paperlithography.component.MiniBlockType;
import com.zerohexer.paperlithography.panel.GridPos;
import com.zerohexer.paperlithography.panel.Panel;
import com.zerohexer.paperlithography.render.ModelPart;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Lightable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Clean directional inverter. {@link #facing} is the OUTPUT (front), like a repeater;
 * input is read from the back (opposite of facing). Lit — emitting 15 out the front only —
 * when the back input is NOT powered; off when it is. Short delay so torch clocks oscillate.
 * No block to mount on: it reads whatever component sits behind it directly.
 */
public class TinyTorch extends MiniBlock {
    private boolean lit = true;
    private int countdown = -1;
    private static final int DELAY = 2;

    @Override
    public MiniBlockType type() {
        return MiniBlockType.TORCH;
    }

    @Override
    public BlockData renderData() {
        // Wall torch leans toward its facing → visible direction indicator in 3D.
        boolean horizontal = facing == BlockFace.NORTH || facing == BlockFace.SOUTH
                || facing == BlockFace.EAST || facing == BlockFace.WEST;
        if (horizontal) {
            BlockData data = Material.REDSTONE_WALL_TORCH.createBlockData();
            ((Directional) data).setFacing(facing);
            ((Lightable) data).setLit(lit);
            return data;
        }
        Lightable data = (Lightable) Material.REDSTONE_TORCH.createBlockData();
        data.setLit(lit);
        return data;
    }

    /** TO-92 transistor: a black half-round body on 3 silver legs, with a status dot on the front. */
    @Override
    public List<ModelPart> model() {
        List<ModelPart> parts = new ArrayList<>();
        BlockData leg = Material.IRON_BLOCK.createBlockData();
        // three thin metal legs at the bottom
        parts.add(new ModelPart(leg, new float[]{0.07f, 0.32f, 0.07f}, new float[]{0.30f, 0f, 0.46f}));
        parts.add(new ModelPart(leg, new float[]{0.07f, 0.32f, 0.07f}, new float[]{0.46f, 0f, 0.46f}));
        parts.add(new ModelPart(leg, new float[]{0.07f, 0.32f, 0.07f}, new float[]{0.62f, 0f, 0.46f}));
        // black plastic body sitting on the legs
        parts.add(new ModelPart(Material.BLACKSTONE.createBlockData(),
                new float[]{0.55f, 0.5f, 0.32f}, new float[]{0.225f, 0.3f, 0.34f}));
        // status dot on the front (toward facing): lime active / gray off
        BlockData dot = (lit ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE).createBlockData();
        parts.add(new ModelPart(dot, new float[]{0.12f, 0.12f, 0.12f}, frontTrans()));
        return parts;
    }

    private float[] frontTrans() {
        return switch (facing) {
            case NORTH -> new float[]{0.44f, 0.5f, 0.28f};
            case SOUTH -> new float[]{0.44f, 0.5f, 0.62f};
            case EAST -> new float[]{0.66f, 0.5f, 0.44f};
            case WEST -> new float[]{0.24f, 0.5f, 0.44f};
            default -> new float[]{0.44f, 0.5f, 0.44f};
        };
    }

    private boolean inputPowered(Panel panel, GridPos pos) {
        BlockFace back = facing.getOppositeFace();
        MiniBlock n = panel.get(pos.offset(back));
        // From the back neighbour, this torch lies in direction `facing`.
        return n != null && n.emittedPowerTo(facing) > 0;
    }

    @Override
    public int emittedPowerTo(BlockFace dir) {
        return (dir == facing && lit) ? 15 : 0; // output only out the front
    }

    @Override
    public boolean needsTicking() {
        return true;
    }

    @Override
    public boolean tick(Panel panel, GridPos pos) {
        boolean target = !inputPowered(panel, pos);
        if (target != lit) {
            if (countdown < 0) {
                countdown = DELAY;
            } else {
                countdown--;
                if (countdown <= 0) {
                    lit = target;
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
    public boolean isDirectional() {
        return true;
    }

    @Override
    public String stateText() {
        return (lit ? "lit" : "off") + ", out " + facing.name().toLowerCase();
    }

    @Override
    public void write(DataOutputStream o) throws IOException {
        o.writeBoolean(lit);
    }

    @Override
    public void read(DataInputStream in) throws IOException {
        lit = in.readBoolean();
    }
}
