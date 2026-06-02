package com.zerohexer.paperlithography.item;

import com.zerohexer.paperlithography.Keys;
import com.zerohexer.paperlithography.component.MiniBlockType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

/** Factory + detector for the panel item and the tiny-component items. */
public final class PanelItem {
    public static final Material PANEL_MATERIAL = Material.LODESTONE;

    private final Keys keys;

    public PanelItem(Keys keys) {
        this.keys = keys;
    }

    // ---- panel item ----

    public ItemStack createPanelItem() {
        ItemStack item = new ItemStack(PANEL_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "Lithography Panel");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Place to start a sub-block circuit.",
                ChatColor.DARK_GRAY + "Sneak + right-click to edit."));
        meta.getPersistentDataContainer().set(keys.panelItem, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isPanelItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(keys.panelItem, PersistentDataType.BYTE);
    }

    /** A panel item carrying a serialized circuit (portable compact block). */
    public ItemStack createPortablePanel(byte[] data, int componentCount) {
        ItemStack item = createPanelItem();
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + "Lithography Panel " + ChatColor.GRAY + "(" + componentCount + " parts)");
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Contains a saved circuit.",
                ChatColor.DARK_GRAY + "Place to restore it."));
        meta.getPersistentDataContainer().set(keys.panelItem, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(keys.panelData, PersistentDataType.BYTE_ARRAY, data);
        item.setItemMeta(meta);
        return item;
    }

    public byte[] getPanelData(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(keys.panelData, PersistentDataType.BYTE_ARRAY);
    }

    // ---- component items ----

    public ItemStack createComponentItem(MiniBlockType type) {
        ItemStack item = new ItemStack(type.itemMaterial);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + type.displayName);
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Right-click a panel cell to place.",
                ChatColor.DARK_GRAY + "Sneak + right-click placed part to remove."));
        meta.getPersistentDataContainer().set(keys.componentType, PersistentDataType.INTEGER, type.id);
        item.setItemMeta(meta);
        return item;
    }

    public MiniBlockType getComponentType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        Integer id = item.getItemMeta().getPersistentDataContainer()
                .get(keys.componentType, PersistentDataType.INTEGER);
        return id == null ? null : MiniBlockType.byId(id);
    }
}
