package com.zerohexer.paperlithography.component.impl;

import com.zerohexer.paperlithography.component.MiniBlock;
import com.zerohexer.paperlithography.component.MiniBlockType;
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
import java.util.ArrayList;
import java.util.List;

/** Manual power source. Toggles on/off; emits 15 to all neighbours while on. */
public class TinyLever extends MiniBlock {
    private boolean on = false;

    @Override
    public MiniBlockType type() {
        return MiniBlockType.LEVER;
    }

    @Override
    public BlockData renderData() {
        Switch data = (Switch) Material.LEVER.createBlockData();
        data.setPowered(on);
        data.setAttachedFace(FaceAttachable.AttachedFace.FLOOR);
        return data;
    }

    /** Toggle switch: dark base + a nub that slides (lime on / gray off). */
    @Override
    public List<ModelPart> model() {
        List<ModelPart> parts = new ArrayList<>();
        parts.add(new ModelPart(Material.POLISHED_BLACKSTONE.createBlockData(),
                new float[]{0.8f, 0.15f, 0.8f}, new float[]{0.1f, 0f, 0.1f}));
        BlockData nub = (on ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE).createBlockData();
        float nx = on ? 0.5f : 0.2f;
        parts.add(new ModelPart(nub, new float[]{0.3f, 0.35f, 0.4f}, new float[]{nx, 0.12f, 0.3f}));
        return parts;
    }

    @Override
    public int emittedPowerTo(BlockFace dir) {
        return on ? 15 : 0;
    }

    @Override
    public boolean onUse(Player player) {
        on = !on;
        signal = on ? 15 : 0;
        return true;
    }

    @Override
    public String stateText() {
        return on ? "ON" : "OFF";
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        out.writeBoolean(on);
    }

    @Override
    public void read(DataInputStream in) throws IOException {
        on = in.readBoolean();
        signal = on ? 15 : 0;
    }
}
