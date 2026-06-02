package com.zerohexer.paperlithography.render;

import org.bukkit.block.data.BlockData;

/**
 * One piece of a composite component model: a vanilla block scaled and positioned within
 * the cell (values are cell fractions, 0..1). Several parts compose a custom 3D shape with
 * zero client downloads.
 */
public class ModelPart {
    public final BlockData data;
    public final float[] scale; // {x,y,z} cell fractions (1.0 = fills the cell)
    public final float[] trans; // {x,y,z} cell-fraction offset within the cell

    public ModelPart(BlockData data, float[] scale, float[] trans) {
        this.data = data;
        this.scale = scale;
        this.trans = trans;
    }
}
