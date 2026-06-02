package com.zerohexer.paperlithography.render;

import com.zerohexer.paperlithography.Keys;
import com.zerohexer.paperlithography.panel.GridPos;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Spawns the vanilla-rendered entities for a panel's cells, parameterized by a render base
 * location, cell size, and Y offset — so the same grid can render compact near the panel
 * (cell = 1/4 block, +1 above it) or full-size in the build world (cell = 1 block).
 *
 * <p>block_display has no hitbox, so each cell also gets an {@link Interaction} entity. Its
 * {@code panelPos} PDC always points at the real panel block (for click routing), independent
 * of where it is rendered.
 */
public final class PanelRenderer {
    /** Compact (near-panel) cell size: 1/4 of a block. */
    public static final double CELL = 1.0 / GridPos.SIZE;
    /** Compact render is one block above the panel base so the solid panel block doesn't occlude it. */
    public static final double Y_OFFSET = 1.0;

    private PanelRenderer() {
    }

    /** Min corner of a cell (where a full-size block model is anchored). */
    public static Location displayLoc(Location base, GridPos g, double cell, double yOff) {
        return base.clone().add(g.x * cell, yOff + g.y * cell, g.z * cell);
    }

    /** Bottom-center of a cell (interaction hitbox anchor). */
    public static Location interactionLoc(Location base, GridPos g, double cell, double yOff) {
        return base.clone().add((g.x + 0.5) * cell, yOff + g.y * cell, (g.z + 0.5) * cell);
    }

    /** Transformation from cell-fraction scale + translation, scaled to the given cell size. */
    public static Transformation transform(float[] scale, float[] trans, double cell) {
        float c = (float) cell;
        return new Transformation(
                new Vector3f(trans[0] * c, trans[1] * c, trans[2] * c),
                new Quaternionf(),
                new Vector3f(scale[0] * c, scale[1] * c, scale[2] * c),
                new Quaternionf());
    }

    /** Spawn one composite model part at the given cell. */
    public static BlockDisplay spawnPart(Location base, GridPos g, ModelPart p, Keys keys, double cell, double yOff) {
        Location loc = displayLoc(base, g, cell, yOff);
        return loc.getWorld().spawn(loc, BlockDisplay.class, d -> {
            d.setBlock(p.data);
            d.setTransformation(transform(p.scale, p.trans, cell));
            d.setPersistent(false);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setInterpolationDuration(2);
            d.getPersistentDataContainer().set(keys.ownedEntity, PersistentDataType.BYTE, (byte) 1);
        });
    }

    /** Spawn a clickable interaction for a cell; {@code panelPos} routes clicks to the real panel. */
    public static Interaction spawnInteraction(Location base, GridPos g, Keys keys, double cell, double yOff,
                                               String panelPos) {
        Location loc = interactionLoc(base, g, cell, yOff);
        float c = (float) cell;
        return loc.getWorld().spawn(loc, Interaction.class, it -> {
            it.setInteractionWidth(c);
            it.setInteractionHeight(c);
            it.setResponsive(false);
            it.setPersistent(false);
            it.getPersistentDataContainer().set(keys.ownedEntity, PersistentDataType.BYTE, (byte) 1);
            it.getPersistentDataContainer().set(keys.cellPos, PersistentDataType.INTEGER, g.pack());
            it.getPersistentDataContainer().set(keys.panelPos, PersistentDataType.STRING, panelPos);
        });
    }
}
