package com.zerohexer.paperlithography.render;

import com.zerohexer.paperlithography.Keys;
import com.zerohexer.paperlithography.panel.GridPos;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Spawns the vanilla-rendered entities for a panel's cells.
 *
 * <p>The grid is rendered in the air <em>one block above</em> the panel base, because the
 * panel base block itself is solid and would otherwise occlude everything inside it.
 *
 * <p>Each occupied cell gets a {@link BlockDisplay} (the visible tiny block); each empty
 * cell gets a small translucent marker so the editable lattice is visible; and every cell
 * gets an {@link Interaction} entity (a clickable hitbox, since block_display has none).
 */
public final class PanelRenderer {
    public static final double CELL = 1.0 / GridPos.SIZE;
    public static final float SCALE = (float) CELL;
    /** Render the grid this many blocks above the panel base so it isn't hidden inside it. */
    public static final double Y_OFFSET = 1.0;
    /** Empty-cell marker block. */
    public static final Material MARKER = Material.LIGHT_GRAY_STAINED_GLASS;
    private static final float MARKER_SCALE = (float) (CELL * 0.45);

    private PanelRenderer() {
    }

    /** Min corner of the cell — where a full-size block model is anchored. */
    public static Location displayLoc(Location base, GridPos g) {
        return base.clone().add(g.x * CELL, Y_OFFSET + g.y * CELL, g.z * CELL);
    }

    /** Bottom-center of the cell — where the interaction hitbox is anchored. */
    public static Location interactionLoc(Location base, GridPos g) {
        return base.clone().add((g.x + 0.5) * CELL, Y_OFFSET + g.y * CELL, (g.z + 0.5) * CELL);
    }

    private static final float[] FULL_SCALE = {1f, 1f, 1f};
    private static final float[] ZERO_TRANS = {0f, 0f, 0f};

    public static BlockDisplay spawnDisplay(World world, Location base, GridPos g, BlockData data, Keys keys) {
        return spawnDisplay(world, base, g, data, keys, FULL_SCALE, ZERO_TRANS);
    }

    /** Build a Transformation from cell-fraction scale + translation. */
    public static Transformation transform(float[] scale, float[] trans) {
        return new Transformation(
                new Vector3f(trans[0] * SCALE, trans[1] * SCALE, trans[2] * SCALE),
                new Quaternionf(),
                new Vector3f(scale[0] * SCALE, scale[1] * SCALE, scale[2] * SCALE),
                new Quaternionf());
    }

    /** Spawn a cell display with a per-component model scale + translation (cell fractions). */
    public static BlockDisplay spawnDisplay(World world, Location base, GridPos g, BlockData data, Keys keys,
                                            float[] scale, float[] trans) {
        Location loc = displayLoc(base, g);
        return world.spawn(loc, BlockDisplay.class, d -> {
            d.setBlock(data);
            d.setTransformation(transform(scale, trans));
            d.setPersistent(false);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setInterpolationDuration(2);
            d.getPersistentDataContainer().set(keys.ownedEntity, PersistentDataType.BYTE, (byte) 1);
        });
    }

    /** Spawn one composite model part. */
    public static BlockDisplay spawnPart(World world, Location base, GridPos g, ModelPart p, Keys keys) {
        return spawnDisplay(world, base, g, p.data, keys, p.scale, p.trans);
    }

    /** A small translucent cube centered in an empty cell, so the grid is visible. */
    public static BlockDisplay spawnMarker(World world, Location base, GridPos g, Keys keys) {
        float t = (float) ((CELL - MARKER_SCALE) / 2.0);
        return world.spawn(displayLoc(base, g), BlockDisplay.class, d -> {
            d.setBlock(MARKER.createBlockData());
            d.setTransformation(new Transformation(
                    new Vector3f(t, t, t),
                    new Quaternionf(),
                    new Vector3f(MARKER_SCALE, MARKER_SCALE, MARKER_SCALE),
                    new Quaternionf()));
            d.setPersistent(false);
            d.setBrightness(new Display.Brightness(15, 15));
            d.getPersistentDataContainer().set(keys.ownedEntity, PersistentDataType.BYTE, (byte) 1);
        });
    }

    public static Interaction spawnInteraction(World world, Location base, GridPos g, Keys keys) {
        String panelPos = base.getBlockX() + "," + base.getBlockY() + "," + base.getBlockZ();
        return world.spawn(interactionLoc(base, g), Interaction.class, it -> {
            it.setInteractionWidth(SCALE);
            it.setInteractionHeight(SCALE);
            it.setResponsive(false);
            it.setPersistent(false);
            it.getPersistentDataContainer().set(keys.ownedEntity, PersistentDataType.BYTE, (byte) 1);
            it.getPersistentDataContainer().set(keys.cellPos, PersistentDataType.INTEGER, g.pack());
            it.getPersistentDataContainer().set(keys.panelPos, PersistentDataType.STRING, panelPos);
        });
    }
}
