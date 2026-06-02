package com.zerohexer.paperlithography.component.impl;

import com.zerohexer.paperlithography.component.MiniBlock;
import com.zerohexer.paperlithography.component.MiniBlockType;
import com.zerohexer.paperlithography.panel.GridPos;
import com.zerohexer.paperlithography.panel.Panel;
import com.zerohexer.paperlithography.render.ModelPart;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Player;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/** Momentary source. Right-click presses it; emits 15 for {@code DURATION} ticks then releases. */
public class TinyButton extends MiniBlock {
    private int ticksLeft = 0;
    private static final int DURATION = 20; // 1 second

    @Override
    public MiniBlockType type() {
        return MiniBlockType.BUTTON;
    }

    @Override
    public BlockData renderData() {
        Switch data = (Switch) Material.STONE_BUTTON.createBlockData();
        data.setPowered(ticksLeft > 0);
        data.setAttachedFace(FaceAttachable.AttachedFace.FLOOR);
        return data;
    }

    /** Push-button: dark base + a cap that's red & raised when idle, lime & depressed when pressed. */
    @Override
    public List<ModelPart> model() {
        boolean pressed = ticksLeft > 0;
        BlockData cap = (pressed ? Material.LIME_CONCRETE : Material.RED_CONCRETE).createBlockData();
        float h = pressed ? 0.15f : 0.32f;
        return List.of(
                new ModelPart(Material.POLISHED_BLACKSTONE.createBlockData(),
                        new float[]{0.7f, 0.1f, 0.7f}, new float[]{0.15f, 0f, 0.15f}),
                new ModelPart(cap, new float[]{0.45f, h, 0.45f}, new float[]{0.275f, 0.1f, 0.275f}));
    }

    @Override
    public int emittedPowerTo(BlockFace dir) {
        return ticksLeft > 0 ? 15 : 0;
    }

    @Override
    public boolean onUse(Player player) {
        boolean wasOff = ticksLeft == 0;
        ticksLeft = DURATION;
        return wasOff; // visual change only on the press edge
    }

    @Override
    public boolean needsTicking() {
        return ticksLeft > 0;
    }

    @Override
    public boolean tick(Panel panel, GridPos pos) {
        if (ticksLeft > 0) {
            ticksLeft--;
            if (ticksLeft == 0) return true; // release edge -> visual change
        }
        return false;
    }

    @Override
    public String stateText() {
        return ticksLeft > 0 ? "pressed" : "idle";
    }

    @Override
    public void write(DataOutputStream o) throws IOException {
        // Don't persist the transient press; a saved button is released.
        o.writeByte(0);
    }

    @Override
    public void read(DataInputStream in) throws IOException {
        in.readByte();
        ticksLeft = 0;
    }
}
