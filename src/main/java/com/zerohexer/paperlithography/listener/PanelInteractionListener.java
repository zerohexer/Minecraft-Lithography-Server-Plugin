package com.zerohexer.paperlithography.listener;

import com.zerohexer.paperlithography.PaperLithographyPlugin;
import com.zerohexer.paperlithography.item.PanelItem;
import com.zerohexer.paperlithography.panel.Panel;
import com.zerohexer.paperlithography.render.EditSession;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Handles placing panels, entering/leaving edit mode, and breaking panels. */
public class PanelInteractionListener implements Listener {
    private final PaperLithographyPlugin plugin;

    public PanelInteractionListener(PaperLithographyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();

        // Punch (left-click) a panel while editing it in 3D = change the visible layer.
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && clicked != null
                && plugin.store().isPanel(clicked)
                && plugin.sessions().isEditing(player, clicked.getLocation())) {
            event.setCancelled(true);
            int dir = player.isSneaking() ? -1 : 1;
            int layer = plugin.sessions().cycleLayer(player, clicked.getLocation(), dir);
            if (layer >= 0) {
                player.sendActionBar(net.kyori.adventure.text.Component.text(
                        "Layer Y = " + layer + " (punch to change, sneak+punch = down)"));
                syncOpenGuiLayer(player, clicked, layer);
            }
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (clicked == null) return;

        PanelItem items = plugin.panelItem();

        // Interacting with an existing panel: sneak = 3D in-world view, normal = GUI editor.
        if (plugin.store().isPanel(clicked)) {
            event.setCancelled(true);
            if (!player.hasPermission("paperlithography.use")) {
                player.sendMessage(ChatColor.RED + "You don't have permission to edit panels.");
                return;
            }
            if (player.isSneaking()) {
                toggleEdit(player, clicked);
            } else {
                openGui(player, clicked);
            }
            return;
        }

        // Otherwise, placing a panel item creates a new panel.
        ItemStack inHand = event.getItem();
        if (items.isPanelItem(inHand)) {
            event.setCancelled(true);
            if (!player.hasPermission("paperlithography.use")) return;
            placePanel(player, clicked, event.getBlockFace(), inHand);
        }
    }

    /** If the player has the editor GUI open for this panel, move it to the given layer too. */
    private void syncOpenGuiLayer(Player player, Block panelBlock, int layer) {
        if (player.getOpenInventory().getTopInventory().getHolder()
                instanceof com.zerohexer.paperlithography.gui.PanelGuiHolder h
                && h.base.getBlockX() == panelBlock.getX()
                && h.base.getBlockY() == panelBlock.getY()
                && h.base.getBlockZ() == panelBlock.getZ()) {
            h.level = layer;
            com.zerohexer.paperlithography.gui.PanelGui.render(h);
        }
    }

    private void openGui(Player player, Block panelBlock) {
        Panel panel = plugin.store().getPanel(panelBlock);
        if (panel == null) return;
        plugin.engine().markDirty(panel);
        com.zerohexer.paperlithography.gui.PanelGuiHolder holder =
                new com.zerohexer.paperlithography.gui.PanelGuiHolder(panel, panelBlock.getLocation());
        // Adopt the current 3D view's layer/mode so the two stay in sync (don't reset it).
        int current = plugin.sessions().getLayer(player, panelBlock.getLocation());
        if (current >= 0) holder.level = current;
        holder.viewAll = plugin.sessions().isShowAll(player, panelBlock.getLocation());
        holder.showGrid = plugin.sessions().isShowGrid(player, panelBlock.getLocation());
        com.zerohexer.paperlithography.gui.PanelGui.open(plugin, player, holder);
    }

    private void toggleEdit(Player player, Block panelBlock) {
        Panel panel = plugin.store().getPanel(panelBlock);
        if (panel == null) return;
        Location base = panelBlock.getLocation();
        boolean opened = plugin.sessions().toggle(player, panel, base);
        if (opened) {
            plugin.engine().markDirty(panel);
            String linked = panel.hasNeighborPanel() ? ChatColor.GREEN + " [linked]" : "";
            player.sendMessage(ChatColor.AQUA + "3D view: panel "
                    + ChatColor.GRAY + "(" + panel.size() + " parts)" + linked + ". "
                    + ChatColor.DARK_GRAY + "Punch the panel to change layer. "
                    + "Right-click a cell to place / use-flip, left-click a part to remove.");
        } else {
            player.sendMessage(ChatColor.GRAY + "Closed panel editor.");
        }
    }

    private void placePanel(Player player, Block clicked, org.bukkit.block.BlockFace face, ItemStack inHand) {
        Block target = clicked.getRelative(face);
        if (!target.isReplaceable()) {
            player.sendMessage(ChatColor.RED + "No room to place the panel there.");
            return;
        }
        target.setType(PanelItem.PANEL_MATERIAL);

        byte[] data = plugin.panelItem().getPanelData(inHand);
        Panel panel = (data != null) ? Panel.deserialize(data) : plugin.store().createPanel(target);
        if (data != null) {
            plugin.store().putPanel(target, panel);
        }
        plugin.store().saveChunk(target.getChunk());
        plugin.engine().markDirty(panel);
        for (Panel n : panel.neighborPanels()) plugin.engine().markDirty(n);

        if (player.getGameMode() != GameMode.CREATIVE) {
            inHand.setAmount(inHand.getAmount() - 1);
        }
        player.sendMessage(ChatColor.AQUA + "Placed a Lithography panel. "
                + ChatColor.DARK_GRAY + "Sneak + right-click it to edit.");
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!plugin.store().isPanel(block)) return;

        event.setDropItems(false);
        Panel panel = plugin.store().removePanel(block);
        if (panel != null) {
            for (Panel n : panel.neighborPanels()) plugin.engine().markDirty(n);
            plugin.engine().deactivate(panel);
            plugin.sessions().closeByPanel(panel);
        }
        plugin.store().saveChunk(block.getChunk());

        ItemStack drop;
        if (panel != null && !panel.isEmpty()) {
            drop = plugin.panelItem().createPortablePanel(panel.serialize(), panel.size());
        } else {
            drop = plugin.panelItem().createPanelItem();
        }
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), drop);
    }
}
