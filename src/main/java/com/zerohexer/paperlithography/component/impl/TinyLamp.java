package com.zerohexer.paperlithography.component.impl;

import com.zerohexer.paperlithography.component.MiniBlock;
import com.zerohexer.paperlithography.component.MiniBlockType;
import com.zerohexer.paperlithography.panel.GridPos;
import com.zerohexer.paperlithography.panel.Panel;
import com.zerohexer.paperlithography.render.ModelPart;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/** Output sink. Lit when any neighbour emits power toward it. */
public class TinyLamp extends MiniBlock {
    private boolean lit = false;

    @Override
    public MiniBlockType type() {
        return MiniBlockType.LAMP;
    }

    @Override
    public BlockData renderData() {
        // Fallback only; real visual is the LED model().
        return (lit ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE).createBlockData();
    }

    /** Indicator LED: dark base + a matte lime dome when lit (full-bright via the display, no lantern texture). */
    @Override
    public List<ModelPart> model() {
        BlockData dome = (lit ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE).createBlockData();
        return List.of(
                new ModelPart(Material.POLISHED_BLACKSTONE.createBlockData(),
                        new float[]{0.6f, 0.1f, 0.6f}, new float[]{0.2f, 0f, 0.2f}),
                new ModelPart(dome,
                        new float[]{0.45f, 0.45f, 0.45f}, new float[]{0.275f, 0.1f, 0.275f}));
    }

    @Override
    public boolean update(Panel panel, GridPos pos) {
        boolean powered = false;
        for (BlockFace f : Panel.FACES) {
            MiniBlock n = panel.get(pos.offset(f));
            if (n == null) continue;
            if (n.emittedPowerTo(f.getOppositeFace()) > 0) {
                powered = true;
                break;
            }
        }
        if (powered != lit) {
            lit = powered;
            signal = lit ? 1 : 0;
            return true;
        }
        return false;
    }

    @Override
    public String stateText() {
        return lit ? "LIT" : "off";
    }

    @Override
    public void write(DataOutputStream out) throws IOException {
        out.writeBoolean(lit);
    }

    @Override
    public void read(DataInputStream in) throws IOException {
        lit = in.readBoolean();
        signal = lit ? 1 : 0;
    }
}
