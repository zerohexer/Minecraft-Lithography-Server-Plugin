package com.zerohexer.paperlithography.listener;

import com.zerohexer.paperlithography.PaperLithographyPlugin;
import com.zerohexer.paperlithography.component.MiniBlock;
import com.zerohexer.paperlithography.component.MiniBlockType;
import com.zerohexer.paperlithography.panel.GridPos;
import com.zerohexer.paperlithography.panel.Panel;
import com.zerohexer.paperlithography.util.Directions;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Routes clicks on interaction entities in the 3D view:
 * right-click = place (from inventory) / use-flip; left-click = remove (returns the item).
 */
public class EntityInteractionListener implements Listener {
    private final PaperLithographyPlugin plugin;

    public EntityInteractionListener(PaperLithographyPlugin plugin) {
        this.plugin = plugin;
    }

    private GridPos cellOf(Entity entity) {
        Integer packed = entity.getPersistentDataContainer()
                .get(plugin.keys().cellPos, PersistentDataType.INTEGER);
        return packed == null ? null : GridPos.unpack(packed);
    }

    private Block panelBlockOf(Entity entity) {
        String pos = entity.getPersistentDataContainer()
                .get(plugin.keys().panelPos, PersistentDataType.STRING);
        if (pos == null) return null;
        try {
            String[] parts = pos.split(";");
            if (parts.length == 4) { // worldUid;x;y;z
                World w = Bukkit.getWorld(java.util.UUID.fromString(parts[0]));
                if (w == null) return null;
                return w.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            }
            return null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** Resolve the panel for a clicked interaction entity, if the player is editing it. */
    private Panel resolve(Player player, Entity entity) {
        Block panelBlock = panelBlockOf(entity);
        if (panelBlock == null) return null;
        if (!plugin.sessions().isEditing(player, panelBlock.getLocation())) return null;
        return plugin.store().getPanel(panelBlock);
    }

    private void apply(Panel panel, Block panelBlock) {
        plugin.engine().markDirty(panel);
        for (Panel n : panel.neighborPanels()) plugin.engine().markDirty(n);
        plugin.sessions().refreshPanel(panel);
        plugin.store().saveChunk(panelBlock.getChunk());
    }

    private void giveBack(Player player, MiniBlockType type) {
        ItemStack item = plugin.panelItem().createComponentItem(type);
        var leftover = player.getInventory().addItem(item);
        leftover.values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
    }

    /** Right-click: place from inventory, or use/flip an existing part (regardless of sneaking). */
    @EventHandler
    public void onRightClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Entity clicked = event.getRightClicked();
        GridPos cell = cellOf(clicked);
        if (cell == null) return;
        event.setCancelled(true);

        Player player = event.getPlayer();
        Panel panel = resolve(player, clicked);
        if (panel == null) return;
        Block panelBlock = panelBlockOf(clicked);

        MiniBlock existing = panel.get(cell);
        ItemStack hand = player.getInventory().getItemInMainHand();
        MiniBlockType placing = plugin.panelItem().getComponentType(hand);
        boolean creative = player.getGameMode() == GameMode.CREATIVE;

        if (placing != null && existing == null) {
            MiniBlock block = placing.create();
            if (block.isDirectional()) {
                block.setFacing(Directions.fromYaw(player.getLocation().getYaw()));
            }
            panel.set(cell, block);
            if (!creative) hand.setAmount(hand.getAmount() - 1);
        } else if (existing != null) {
            existing.onUse(player); // flip / press / cycle — sneak or not
        } else {
            return;
        }
        apply(panel, panelBlock);
    }

    /** Left-click (attack): remove the part and return its item. */
    @EventHandler
    public void onAttack(PrePlayerAttackEntityEvent event) {
        Entity clicked = event.getAttacked();
        GridPos cell = cellOf(clicked);
        if (cell == null) return;
        event.setCancelled(true);

        Player player = event.getPlayer();
        Panel panel = resolve(player, clicked);
        if (panel == null) return;
        Block panelBlock = panelBlockOf(clicked);

        MiniBlock removed = panel.get(cell);
        if (removed == null) return;
        panel.remove(cell);
        if (player.getGameMode() != GameMode.CREATIVE) {
            giveBack(player, removed.type());
        }
        apply(panel, panelBlock);
    }
}
