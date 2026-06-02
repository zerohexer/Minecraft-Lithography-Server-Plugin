package com.zerohexer.paperlithography.gui;

import com.zerohexer.paperlithography.PaperLithographyPlugin;
import com.zerohexer.paperlithography.component.MiniBlock;
import com.zerohexer.paperlithography.component.MiniBlockType;
import com.zerohexer.paperlithography.panel.GridPos;
import com.zerohexer.paperlithography.panel.Panel;
import org.bukkit.GameMode;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles clicks in the layered panel editor GUI.
 *
 * <p>Survival placement: grab a tiny component from your own inventory onto the cursor,
 * then left-click a cell to place it — one is consumed. Left-clicking an occupied cell with
 * an empty cursor removes the part and returns the item. Creative players can also use the
 * palette as an infinite brush.
 */
public class GuiListener implements Listener {
    private final PaperLithographyPlugin plugin;

    public GuiListener(PaperLithographyPlugin plugin) {
        this.plugin = plugin;
    }

    private static BlockFace nextFace(BlockFace f) {
        return switch (f) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.UP;
            case UP -> BlockFace.DOWN;
            default -> BlockFace.NORTH;
        };
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PanelGuiHolder h)) return;
        int topSize = event.getView().getTopInventory().getSize();
        int raw = event.getRawSlot();
        if (raw < 0) return;

        Player player = (Player) event.getWhoClicked();

        // Clicks in the player's own inventory: allow normal pickup so they can grab parts
        // onto the cursor, but block shift-click (which would dump items into the panel).
        if (raw >= topSize) {
            if (event.isShiftClick()) event.setCancelled(true);
            return;
        }

        // Top inventory: we fully manage it.
        event.setCancelled(true);

        // Palette selection (creative quick-brush).
        int paletteCount = MiniBlockType.values().length;
        if (raw >= PanelGui.PALETTE_START && raw < PanelGui.PALETTE_START + paletteCount) {
            h.brush = MiniBlockType.values()[raw - PanelGui.PALETTE_START];
            h.eraser = false;
            PanelGui.render(h);
            return;
        }
        if (raw == PanelGui.ERASER_SLOT) {
            h.eraser = true;
            PanelGui.render(h);
            return;
        }
        if (raw == PanelGui.NAV_DOWN) {
            if (h.level > 0) {
                h.level--;
                PanelGui.render(h);
                plugin.sessions().setLayer(player, h.base, h.level); // sync 3D view
            }
            return;
        }
        if (raw == PanelGui.NAV_UP) {
            if (h.level < GridPos.SIZE - 1) {
                h.level++;
                PanelGui.render(h);
                plugin.sessions().setLayer(player, h.base, h.level); // sync 3D view
            }
            return;
        }
        if (raw == PanelGui.VIEW_TOGGLE) {
            h.viewAll = !h.viewAll;
            plugin.sessions().setShowAll(player, h.base, h.viewAll);
            PanelGui.render(h);
            return;
        }
        if (raw == PanelGui.BUILD_BUTTON) {
            player.closeInventory();
            plugin.buildRooms().enter(player, h.panel, h.base);
            return;
        }
        if (raw == PanelGui.GRID_TOGGLE) {
            h.showGrid = !h.showGrid;
            plugin.sessions().setShowGrid(player, h.base, h.showGrid);
            PanelGui.render(h);
            return;
        }
        if (raw == PanelGui.NAV_INFO) return;

        int[] xz = new int[2];
        if (!PanelGui.cellOfSlot(raw, xz)) return;
        GridPos g = new GridPos(xz[0], h.level, xz[1]);
        MiniBlock existing = h.panel.get(g);

        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        ItemStack cursor = event.getCursor();
        boolean cursorEmpty = cursor == null || cursor.getType().isAir();
        MiniBlockType cursorType = cursorEmpty ? null : plugin.panelItem().getComponentType(cursor);
        boolean changed = false;

        if (event.isShiftClick()) {
            // Shift-click = change a setting (e.g. repeater delay), or toggle.
            if (existing != null) {
                existing.onUse(player);
                changed = true;
            }
        } else if (event.isRightClick()) {
            // Right-click = rotate directional parts; otherwise "use" (toggle lever/button).
            if (existing != null) {
                if (existing.isDirectional()) {
                    existing.setFacing(nextFace(existing.getFacing()));
                } else {
                    existing.onUse(player);
                }
                changed = true;
            }
        } else { // left click
            if (cursorType != null) {
                if (existing == null) {
                    MiniBlock nb = cursorType.create();
                    if (nb.isDirectional()) nb.setFacing(BlockFace.NORTH);
                    h.panel.set(g, nb);
                    if (!creative) consumeOne(player, cursor);
                    changed = true;
                }
            } else if (!cursorEmpty) {
                // Holding some non-component item — ignore.
                return;
            } else if (h.eraser) {
                if (existing != null) {
                    h.panel.remove(g);
                    if (!creative) giveBack(player, existing.type());
                    changed = true;
                }
            } else if (existing != null) {
                // Empty cursor on an occupied cell: pick the part back up.
                h.panel.remove(g);
                if (!creative) giveBack(player, existing.type());
                changed = true;
            } else if (creative) {
                // Empty cell, empty cursor, creative: quick-place the palette brush.
                MiniBlock nb = h.brush.create();
                if (nb.isDirectional()) nb.setFacing(BlockFace.NORTH);
                h.panel.set(g, nb);
                changed = true;
            }
        }

        if (changed) {
            plugin.engine().markDirty(h.panel);
            for (Panel n : h.panel.neighborPanels()) plugin.engine().markDirty(n);
            plugin.sessions().refreshPanel(h.panel);
            plugin.store().saveChunk(h.base.getChunk());
            PanelGui.render(h);
        }
    }

    private void consumeOne(Player player, ItemStack cursor) {
        int amt = cursor.getAmount();
        if (amt <= 1) {
            player.setItemOnCursor(null);
        } else {
            cursor.setAmount(amt - 1);
            player.setItemOnCursor(cursor);
        }
    }

    private void giveBack(Player player, MiniBlockType type) {
        ItemStack item = plugin.panelItem().createComponentItem(type);
        var leftover = player.getInventory().addItem(item);
        leftover.values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof PanelGuiHolder)) return;
        int topSize = event.getView().getTopInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
