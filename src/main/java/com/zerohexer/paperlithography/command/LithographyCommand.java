package com.zerohexer.paperlithography.command;

import com.zerohexer.paperlithography.PaperLithographyPlugin;
import com.zerohexer.paperlithography.component.MiniBlock;
import com.zerohexer.paperlithography.component.MiniBlockType;
import com.zerohexer.paperlithography.panel.GridPos;
import com.zerohexer.paperlithography.panel.Panel;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LithographyCommand implements CommandExecutor, TabCompleter {
    private final PaperLithographyPlugin plugin;

    public LithographyCommand(PaperLithographyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("recipes")) {
            recipes(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("build")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Players only.");
                return true;
            }
            if (!player.hasPermission("paperlithography.use")) {
                player.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            if (plugin.buildRooms().isInRoom(player)) {
                plugin.buildRooms().exit(player);
                return true;
            }
            Block pb = findPanelBlock(player);
            if (pb == null) {
                player.sendMessage(ChatColor.RED + "Look at (or stand near) a panel, then /lithography build.");
                return true;
            }
            plugin.buildRooms().enter(player, plugin.store().getPanel(pb), pb.getLocation());
            return true;
        }

        if (args[0].equalsIgnoreCase("debug")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Players only.");
                return true;
            }
            debug(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Only players can receive items.");
                return true;
            }
            if (!player.hasPermission("paperlithography.give")) {
                player.sendMessage(ChatColor.RED + "You don't have permission for that.");
                return true;
            }
            String what = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "panel";
            give(player, what);
            return true;
        }

        help(sender);
        return true;
    }

    private void give(Player player, String what) {
        if (what.equals("panel")) {
            player.getInventory().addItem(plugin.panelItem().createPanelItem());
            player.sendMessage(ChatColor.AQUA + "Gave a Lithography panel.");
            return;
        }
        if (what.equals("all")) {
            player.getInventory().addItem(plugin.panelItem().createPanelItem());
            for (MiniBlockType t : MiniBlockType.values()) {
                player.getInventory().addItem(plugin.panelItem().createComponentItem(t));
            }
            player.sendMessage(ChatColor.AQUA + "Gave a panel and one of each component.");
            return;
        }
        MiniBlockType type = MiniBlockType.byName(what);
        if (type == null) {
            player.sendMessage(ChatColor.RED + "Unknown item '" + what + "'. Try: panel, all, "
                    + componentNames());
            return;
        }
        player.getInventory().addItem(plugin.panelItem().createComponentItem(type));
        player.sendMessage(ChatColor.YELLOW + "Gave a " + type.displayName + ".");
    }

    private String componentNames() {
        StringBuilder sb = new StringBuilder();
        for (MiniBlockType t : MiniBlockType.values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(t.name().toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    /** The panel block the player is looking at, or the nearest one within 8 blocks. */
    private Block findPanelBlock(Player player) {
        Block target = player.getTargetBlockExact(6);
        if (target != null && plugin.store().isPanel(target)) return target;
        double best = Double.MAX_VALUE;
        Block found = null;
        for (Panel p : plugin.store().allPanels()) {
            Location loc = plugin.store().locationOf(p);
            if (loc == null || !loc.getWorld().equals(player.getWorld())) continue;
            double d = loc.distanceSquared(player.getLocation());
            if (d < best && d < 64) {
                best = d;
                found = loc.getBlock();
            }
        }
        return found;
    }

    /** Dump the looked-at (or nearest) panel's components and wire channel levels. */
    private void debug(Player player) {
        Block pb = findPanelBlock(player);
        Panel panel = pb == null ? null : plugin.store().getPanel(pb);
        if (panel == null) {
            player.sendMessage(ChatColor.RED + "No panel found — look at one or stand near it.");
            return;
        }
        player.sendMessage(ChatColor.AQUA + "Panel dump (" + panel.size() + " parts) — written to debug.txt");
        List<String> lines = new ArrayList<>();
        lines.add("=== Panel dump (" + panel.size() + " parts) @ " + plugin.store().locationOf(panel) + " ===");
        for (Map.Entry<GridPos, MiniBlock> e : panel.components().entrySet()) {
            MiniBlock m = e.getValue();
            StringBuilder sb = new StringBuilder();
            sb.append(e.getKey()).append(" ").append(m.type().name());
            if (m.isWire()) {
                sb.append(" ch=[");
                for (int c = 0; c < m.channelCount(); c++) {
                    if (c > 0) sb.append(",");
                    sb.append(m.channelLevel(c));
                }
                sb.append("]");
            }
            if (m.isDirectional()) sb.append(" facing=").append(m.getFacing());
            if (!m.stateText().isEmpty()) sb.append(" {").append(m.stateText()).append("}");
            lines.add(sb.toString());
        }
        for (String l : lines) {
            player.sendMessage(ChatColor.GRAY + l);
            plugin.getLogger().info(l);
        }
        try {
            plugin.getDataFolder().mkdirs();
            java.nio.file.Files.write(
                    new java.io.File(plugin.getDataFolder(), "debug.txt").toPath(), lines);
        } catch (java.io.IOException ex) {
            plugin.getLogger().warning("debug write failed: " + ex);
        }
    }

    private void recipes(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "Lithography recipes " + ChatColor.GRAY + "(also in your recipe book):");
        sender.sendMessage(ChatColor.YELLOW + "Panel" + ChatColor.GRAY + " — 8 Glass blocks around 1 Block of Redstone (3x3).");
        sender.sendMessage(ChatColor.GRAY + "Tiny parts (shapeless, drop items together):");
        sender.sendMessage(ChatColor.GRAY + " • Lever + Glass Pane → Tiny Lever");
        sender.sendMessage(ChatColor.GRAY + " • Redstone Lamp + Glass Pane → Tiny Lamp");
        sender.sendMessage(ChatColor.GRAY + " • Redstone + Glass Pane → Tiny Dust");
        sender.sendMessage(ChatColor.GRAY + " • Repeater + Glass Pane → Tiny Repeater");
        sender.sendMessage(ChatColor.GRAY + " • Redstone Torch + Glass Pane → Tiny Torch");
        sender.sendMessage(ChatColor.GRAY + " • Stone Button + Glass Pane → Tiny Button");
        sender.sendMessage(ChatColor.GRAY + " • Redstone + 2 Glass Panes → Tiny Via (vertical link)");
        sender.sendMessage(ChatColor.GRAY + " • 2 Redstone + Glass Pane → Tiny Bridge (crossover)");
        sender.sendMessage(ChatColor.GRAY + " • Comparator + Glass Pane → Tiny Comparator");
        sender.sendMessage(ChatColor.DARK_GRAY + "(tiny parts craft 4 at a time)");
    }

    private void help(CommandSender sender) {
        sender.sendMessage(ChatColor.AQUA + "Paper-Lithography " + ChatColor.GRAY + "— etch redstone into one block.");
        sender.sendMessage(ChatColor.GRAY + "/lithography give panel " + ChatColor.DARK_GRAY + "— get a panel block");
        sender.sendMessage(ChatColor.GRAY + "/lithography give <component> " + ChatColor.DARK_GRAY + "— get a tiny part");
        sender.sendMessage(ChatColor.GRAY + "/lithography give all " + ChatColor.DARK_GRAY + "— get a panel + every part");
        sender.sendMessage(ChatColor.GRAY + "/lithography recipes " + ChatColor.DARK_GRAY + "— how to craft everything");
        sender.sendMessage(ChatColor.GRAY + "/lithography build " + ChatColor.DARK_GRAY + "— enter/leave the full-size build room");
        sender.sendMessage(ChatColor.DARK_GRAY + "Place the panel, then sneak + right-click it to edit. "
                + "Right-click cells to place parts; sneak + right-click a part to remove it.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : new String[]{"help", "give", "recipes", "build"}) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            out.add("panel");
            out.add("all");
            for (MiniBlockType t : MiniBlockType.values()) {
                out.add(t.name().toLowerCase(Locale.ROOT));
            }
            out.removeIf(s -> !s.startsWith(args[1].toLowerCase(Locale.ROOT)));
        }
        return out;
    }
}
