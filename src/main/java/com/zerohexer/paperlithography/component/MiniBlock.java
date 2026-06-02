package com.zerohexer.paperlithography.component;

import com.zerohexer.paperlithography.panel.GridPos;
import com.zerohexer.paperlithography.panel.Panel;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * A single miniaturized block living in one cell of a panel grid.
 *
 * <p>Subclasses implement behavior. The signal model is intentionally simplified
 * but functional: each component reports the power (0-15) it emits toward a given
 * neighbour via {@link #emittedPowerTo(BlockFace)}, and combinational components
 * recompute their own state from neighbours in {@link #update}. Timed components
 * (repeater, torch, button) advance in {@link #tick}.
 */
public abstract class MiniBlock {
    /** Orientation (used by directional components: repeater, torch). */
    protected BlockFace facing = BlockFace.NORTH;
    /** Generic signal/state field; meaning depends on subtype. */
    protected int signal = 0;

    // Transient render handles — never serialized.
    public transient UUID displayId;
    public transient UUID interactionId;

    public abstract MiniBlockType type();

    /** BlockData the block_display renders for this component, reflecting current state. */
    public abstract BlockData renderData();

    /** Model scale as cell fractions {x,y,z} (1.0 = fills the cell). Override for custom 3D shapes. */
    public float[] modelScale() {
        return new float[]{1f, 1f, 1f};
    }

    /** Model translation as cell fractions {x,y,z} (offset within the cell). */
    public float[] modelTranslation() {
        return new float[]{0f, 0f, 0f};
    }

    /** Bitmask (over {@code Panel.FACES} order) of faces this wire links to a neighbour on. Set by the engine. */
    public transient int connMask = 0;

    /**
     * Composite render model: one or more vanilla-block parts. Default is a single part using
     * {@link #renderData()} + {@link #modelScale()}/{@link #modelTranslation()}. Override for
     * multi-part shapes (e.g. wire traces, bridge bars).
     */
    public java.util.List<com.zerohexer.paperlithography.render.ModelPart> model() {
        return java.util.List.of(new com.zerohexer.paperlithography.render.ModelPart(
                renderData(), modelScale(), modelTranslation()));
    }

    /** Power (0-15) this component emits toward the neighbour located in direction {@code dir}. */
    public int emittedPowerTo(BlockFace dir) {
        return 0;
    }

    /** Recompute combinational state from neighbours. Returns true if the visual changed. */
    public boolean update(Panel panel, GridPos pos) {
        return false;
    }

    /** Whether this component needs per-tick processing (timed behavior). */
    public boolean needsTicking() {
        return false;
    }

    /** Per-tick timed processing. Returns true if the visual changed. */
    public boolean tick(Panel panel, GridPos pos) {
        return false;
    }

    /** Right-clicked with an empty hand: "use" the component (toggle lever, press button, cycle delay…). */
    public boolean onUse(Player player) {
        return false;
    }

    /** Short human-readable state for GUI tooltips (e.g. "ON", "delay 2 → north"). */
    public String stateText() {
        return "";
    }

    /** Whether directional rotation (facing) is meaningful for this component. */
    public boolean isDirectional() {
        return false;
    }

    /** Whether this component is a signal-carrying wire (dust / via / bridge). */
    public boolean isWire() {
        return false;
    }

    /**
     * Number of independent signal channels a wire carries. Dust/via = 1 (one shared net).
     * A bridge = 3 (X, Z, Y axes kept separate so signals cross without mixing).
     */
    public int channelCount() {
        return isWire() ? 1 : 0;
    }

    /** Which channel a face routes through for this wire, or -1 if the face isn't a wire face. */
    public int channelForFace(BlockFace face) {
        return -1;
    }

    /** Level (0-15) of a wire channel. Default single-channel wires use {@link #signal}. */
    public int channelLevel(int channel) {
        return signal;
    }

    /** Engine setter for a wire channel's resolved level. */
    public void setChannelLevel(int channel, int level) {
        this.signal = Math.max(0, Math.min(15, level));
    }

    public BlockFace getFacing() {
        return facing;
    }

    public void setFacing(BlockFace facing) {
        this.facing = facing;
    }

    public int getSignal() {
        return signal;
    }

    /** Serialize subtype-specific fields. Type id and facing are written by {@link Panel}. */
    public void write(DataOutputStream out) throws IOException {
    }

    /** Deserialize subtype-specific fields. */
    public void read(DataInputStream in) throws IOException {
    }
}
