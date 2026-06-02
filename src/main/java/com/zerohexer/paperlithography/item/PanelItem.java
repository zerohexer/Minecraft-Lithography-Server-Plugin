package com.zerohexer.paperlithography.item;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.zerohexer.paperlithography.Keys;
import com.zerohexer.paperlithography.component.MiniBlockType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.UUID;

/** Factory + detector for the panel item and the tiny-component items. */
public final class PanelItem {
    public static final Material PANEL_MATERIAL = Material.LODESTONE;

    private final Keys keys;
    private final HeadTextures heads;

    public PanelItem(Keys keys, HeadTextures heads) {
        this.keys = keys;
        this.heads = heads;
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
        String tex = heads == null ? null : heads.get(type);
        ItemStack item;
        ItemMeta meta;
        if (tex != null) {
            item = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skull = (SkullMeta) item.getItemMeta();
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", tex));
            skull.setPlayerProfile(profile);
            meta = skull;
        } else {
            item = new ItemStack(type.itemMaterial);
            meta = item.getItemMeta();
        }
        meta.setDisplayName(ChatColor.YELLOW + type.displayName);
        meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Right-click a panel cell to place.",
                ChatColor.DARK_GRAY + "Left-click a placed part to remove."));
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
