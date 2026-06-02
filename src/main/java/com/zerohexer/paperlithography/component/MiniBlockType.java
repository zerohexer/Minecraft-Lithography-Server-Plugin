package com.zerohexer.paperlithography.component;

import com.zerohexer.paperlithography.component.impl.TinyButton;
import com.zerohexer.paperlithography.component.impl.TinyDust;
import com.zerohexer.paperlithography.component.impl.TinyLamp;
import com.zerohexer.paperlithography.component.impl.TinyLever;
import com.zerohexer.paperlithography.component.impl.TinyRepeater;
import com.zerohexer.paperlithography.component.impl.TinyBridge;
import com.zerohexer.paperlithography.component.impl.TinyComparator;
import com.zerohexer.paperlithography.component.impl.TinyTorch;
import com.zerohexer.paperlithography.component.impl.TinyVia;
import org.bukkit.Material;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Registry of all component types. The {@code id} is the stable serialization id —
 * never reuse or renumber existing ids.
 */
public enum MiniBlockType {
    LEVER(1, "Tiny Lever", Material.LEVER, TinyLever::new),
    LAMP(2, "Tiny Lamp", Material.REDSTONE_LAMP, TinyLamp::new),
    DUST(3, "Tiny Redstone Dust", Material.REDSTONE, TinyDust::new),
    REPEATER(4, "Tiny Repeater", Material.REPEATER, TinyRepeater::new),
    TORCH(5, "Tiny Redstone Torch", Material.REDSTONE_TORCH, TinyTorch::new),
    BUTTON(6, "Tiny Button", Material.STONE_BUTTON, TinyButton::new),
    VIA(7, "Tiny Via (vertical link)", Material.END_ROD, TinyVia::new),
    BRIDGE(8, "Tiny Bridge (crossover)", Material.IRON_BARS, TinyBridge::new),
    COMPARATOR(9, "Tiny Comparator", Material.COMPARATOR, TinyComparator::new);

    public final int id;
    public final String displayName;
    public final Material itemMaterial;
    private final Supplier<MiniBlock> factory;

    MiniBlockType(int id, String displayName, Material itemMaterial, Supplier<MiniBlock> factory) {
        this.id = id;
        this.displayName = displayName;
        this.itemMaterial = itemMaterial;
        this.factory = factory;
    }

    public MiniBlock create() {
        return factory.get();
    }

    public static MiniBlockType byId(int id) {
        for (MiniBlockType t : values()) {
            if (t.id == id) return t;
        }
        return null;
    }

    /** Lookup by command argument, e.g. "lever", "dust". */
    public static MiniBlockType byName(String name) {
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
