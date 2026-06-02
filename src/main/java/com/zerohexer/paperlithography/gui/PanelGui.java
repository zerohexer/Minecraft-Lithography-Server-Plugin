package com.zerohexer.paperlithography.gui;

import com.zerohexer.paperlithography.PaperLithographyPlugin;
import com.zerohexer.paperlithography.component.MiniBlock;
import com.zerohexer.paperlithography.component.MiniBlockType;
import com.zerohexer.paperlithography.panel.GridPos;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Layered 2D editor for a panel. Shows one X–Z layer at a time (4x4) and steps through Y
 * levels, so every cell is directly clickable — no 3D occlusion. A palette selects the
 * "brush"; clicking a cell paints/uses it.
 *
 * <p>Layout (6x9 chest): rows 0..3 cols 0..3 = the layer cells; row 4 = layer navigation;
 * row 5 = component palette + eraser.
 */
public final class PanelGui {
    public static final int NAV_DOWN = 36;
    public static final int NAV_INFO = 40;
    public static final int VIEW_TOGGLE = 42;
    public static final int GRID_TOGGLE = 43;
    public static final int NAV_UP = 44;
    public static final int PALETTE_START = 45; // components fill the bottom row 45..53 (one per type)
    public static final int ERASER_SLOT = 38;   // moved into the nav row since the palette fills row 5

    private PanelGui() {
    }

    public static void open(PaperLithographyPlugin plugin, Player player, PanelGuiHolder holder) {
        Inventory inv = Bukkit.createInventory(holder, 54,
                ChatColor.DARK_AQUA + "Lithography Panel Editor");
        holder.setInventory(inv);
        render(holder);
        player.openInventory(inv);
    }

    /** Returns true if the slot maps to a grid cell; fills out[] = {x, z} when it does. */
    public static boolean cellOfSlot(int slot, int[] out) {
        int col = slot % 9;
        int row = slot / 9;
        if (row < GridPos.SIZE && col < GridPos.SIZE) {
            out[0] = col; // x
            out[1] = row; // z
            return true;
        }
        return false;
    }

    public static void render(PanelGuiHolder h) {
        Inventory inv = h.getInventory();
        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        // Cells of the current layer.
        for (int z = 0; z < GridPos.SIZE; z++) {
            for (int x = 0; x < GridPos.SIZE; x++) {
                GridPos g = new GridPos(x, h.level, z);
                MiniBlock b = h.panel.get(g);
                inv.setItem(z * 9 + x, b == null ? emptyCell(x, h.level, z) : compIcon(b, x, h.level, z));
            }
        }

        // Navigation.
        inv.setItem(NAV_DOWN, button(Material.RED_STAINED_GLASS_PANE,
                ChatColor.RED + "◀ Layer Down", ChatColor.GRAY + "Currently Y = " + h.level));
        inv.setItem(NAV_INFO, button(Material.PAPER,
                ChatColor.YELLOW + "Layer  Y = " + h.level,
                ChatColor.GRAY + "Layers 0–" + (GridPos.SIZE - 1) + " (bottom→top)",
                ChatColor.GRAY + "Rows = Z, Columns = X",
                " ",
                ChatColor.GOLD + "Survival: " + ChatColor.GRAY + "grab a tiny part from your",
                ChatColor.GRAY + "inventory, then left-click a cell to place.",
                ChatColor.GRAY + "Left-click a part (empty hand) to take it back."));
        inv.setItem(NAV_UP, button(Material.LIME_STAINED_GLASS_PANE,
                ChatColor.GREEN + "Layer Up ▶", ChatColor.GRAY + "Currently Y = " + h.level));
        inv.setItem(VIEW_TOGGLE, button(h.viewAll ? Material.SPYGLASS : Material.ENDER_EYE,
                ChatColor.LIGHT_PURPLE + "3D View: " + (h.viewAll ? "All layers" : "Single layer"),
                ChatColor.GRAY + "Click to toggle the in-world view.",
                ChatColor.DARK_GRAY + "(open it with sneak + right-click)"));
        inv.setItem(GRID_TOGGLE, button(h.showGrid ? Material.GLASS : Material.GLASS_PANE,
                ChatColor.LIGHT_PURPLE + "Grid: " + (h.showGrid ? "Shown" : "Hidden"),
                ChatColor.GRAY + (h.showGrid ? "Empty cells show markers." : "Only placed parts are shown."),
                ChatColor.DARK_GRAY + "Click to toggle (affects the 3D view)."));

        // Palette.
        MiniBlockType[] types = MiniBlockType.values();
        for (int i = 0; i < types.length; i++) {
            inv.setItem(PALETTE_START + i, paletteIcon(types[i], !h.eraser && h.brush == types[i]));
        }
        inv.setItem(ERASER_SLOT, eraserIcon(h.eraser));
    }

    // ---- item builders ----

    private static ItemStack pane(Material mat, String name) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name);
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack button(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name);
        m.setLore(Arrays.asList(lore));
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack emptyCell(int x, int y, int z) {
        return button(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                ChatColor.DARK_GRAY + "Empty",
                ChatColor.GRAY + "(" + x + ", " + y + ", " + z + ")",
                ChatColor.DARK_GRAY + "Left-click: place selected part");
    }

    private static ItemStack compIcon(MiniBlock b, int x, int y, int z) {
        ItemStack it = new ItemStack(b.type().itemMaterial);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(ChatColor.AQUA + b.type().displayName);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "(" + x + ", " + y + ", " + z + ")");
        if (!b.stateText().isEmpty()) lore.add(ChatColor.YELLOW + b.stateText());
        if (b.isDirectional()) {
            lore.add(ChatColor.DARK_GRAY + "Right-click: rotate");
            lore.add(ChatColor.DARK_GRAY + "Shift-click: change setting");
        } else {
            lore.add(ChatColor.DARK_GRAY + "Right-click: use");
        }
        lore.add(ChatColor.DARK_GRAY + "Left-click (empty hand): take back");
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack paletteIcon(MiniBlockType type, boolean selected) {
        ItemStack it = new ItemStack(type.itemMaterial);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(ChatColor.YELLOW + "Place: " + type.displayName);
        m.setLore(Arrays.asList(
                selected ? ChatColor.GREEN + "● Selected" : ChatColor.GRAY + "Click to select",
                ChatColor.DARK_GRAY + "Creative quick-place brush"));
        if (selected) glow(m);
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack eraserIcon(boolean selected) {
        ItemStack it = new ItemStack(Material.BARRIER);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(ChatColor.RED + "Eraser");
        m.setLore(Arrays.asList(selected
                ? ChatColor.GREEN + "● Selected"
                : ChatColor.GRAY + "Click to select, then left-click cells"));
        if (selected) glow(m);
        it.setItemMeta(m);
        return it;
    }

    private static void glow(ItemMeta m) {
        m.addEnchant(Enchantment.DURABILITY, 1, true);
        m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
    }
}
