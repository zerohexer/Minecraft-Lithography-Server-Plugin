package com.zerohexer.paperlithography;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Central holder for all NamespacedKeys used by the plugin.
 * Keep every PDC key here so we never collide with vanilla or other plugins.
 */
public final class Keys {
    /** Marks an ItemStack as a Lithography panel (placeable). */
    public final NamespacedKey panelItem;
    /** Serialized grid stored on a portable-compact panel ItemStack. */
    public final NamespacedKey panelData;
    /** Marks an ItemStack as a tiny component; value = MiniBlockType id. */
    public final NamespacedKey componentType;
    /** Packed GridPos stored on an interaction entity. */
    public final NamespacedKey cellPos;
    /** "bx,by,bz" of the owning panel base block, stored on an interaction entity. */
    public final NamespacedKey panelPos;
    /** Marks display/interaction entities the plugin owns (for cleanup). */
    public final NamespacedKey ownedEntity;
    /** Chunk PDC key holding the serialized panels located in that chunk. */
    public final NamespacedKey chunkPanels;

    public Keys(Plugin plugin) {
        this.panelItem = new NamespacedKey(plugin, "panel_item");
        this.panelData = new NamespacedKey(plugin, "panel_data");
        this.componentType = new NamespacedKey(plugin, "component_type");
        this.cellPos = new NamespacedKey(plugin, "cell_pos");
        this.panelPos = new NamespacedKey(plugin, "panel_pos");
        this.ownedEntity = new NamespacedKey(plugin, "owned_entity");
        this.chunkPanels = new NamespacedKey(plugin, "chunk_panels");
    }
}
