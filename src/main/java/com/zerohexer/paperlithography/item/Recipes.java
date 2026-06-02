package com.zerohexer.paperlithography.item;

import com.zerohexer.paperlithography.PaperLithographyPlugin;
import com.zerohexer.paperlithography.component.MiniBlockType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Survival crafting recipes for the panel and the tiny components.
 *
 * <p>Theme: a glass pane is a "lithography lens" — combine it with any supported full-size
 * block to etch a miniature version. The panel itself is a block of redstone framed in glass.
 * All recipe shapes are distinct from vanilla, so they coexist with normal crafting.
 */
public final class Recipes {
    private final PaperLithographyPlugin plugin;
    private final List<NamespacedKey> keys = new ArrayList<>();

    public Recipes(PaperLithographyPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        registerPanel();
        for (MiniBlockType type : MiniBlockType.values()) {
            registerComponent(type);
        }
    }

    /** Unlock all our recipes into a player's recipe book so they're discoverable. */
    public void discover(Player player) {
        player.discoverRecipes(keys);
    }

    private void registerPanel() {
        NamespacedKey key = new NamespacedKey(plugin, "panel");
        Bukkit.removeRecipe(key);
        ItemStack result = plugin.panelItem().createPanelItem();
        ShapedRecipe r = new ShapedRecipe(key, result);
        r.shape("GGG", "GRG", "GGG");
        r.setIngredient('G', Material.GLASS);
        r.setIngredient('R', Material.REDSTONE_BLOCK);
        Bukkit.addRecipe(r);
        keys.add(key);
    }

    private void registerComponent(MiniBlockType type) {
        NamespacedKey key = new NamespacedKey(plugin, "tiny_" + type.name().toLowerCase(Locale.ROOT));
        Bukkit.removeRecipe(key);
        ItemStack result = plugin.panelItem().createComponentItem(type);
        result.setAmount(4); // miniatures: one full block yields 4 tiny parts
        ShapelessRecipe r = new ShapelessRecipe(key, result);
        if (type == MiniBlockType.VIA) {
            // Via: redstone through two stacked "lenses" -> a vertical link.
            r.addIngredient(1, Material.REDSTONE);
            r.addIngredient(2, Material.GLASS_PANE);
        } else if (type == MiniBlockType.BRIDGE) {
            // Bridge: two crossing redstone lines under a lens.
            r.addIngredient(2, Material.REDSTONE);
            r.addIngredient(1, Material.GLASS_PANE);
        } else {
            // The full block + a glass-pane "lens" -> the tiny variant.
            r.addIngredient(1, type.itemMaterial);
            r.addIngredient(1, Material.GLASS_PANE);
        }
        Bukkit.addRecipe(r);
        keys.add(key);
    }
}
