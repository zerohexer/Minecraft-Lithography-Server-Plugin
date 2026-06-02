package com.zerohexer.paperlithography.world;

import com.zerohexer.paperlithography.PaperLithographyPlugin;
import com.zerohexer.paperlithography.panel.GridPos;
import com.zerohexer.paperlithography.panel.Panel;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the immersive build room: a void world where a panel's 4x4x4 grid is rendered at
 * full block scale and the player is teleported in to edit it live (shared with the panel).
 */
public class BuildRoomManager {
    public static final String WORLD_NAME = "lithography_build";
    private static final int SPOT_SPACING = 64;

    private final PaperLithographyPlugin plugin;
    private World buildWorld;
    private int spotCounter = 0;
    private final Map<UUID, Room> rooms = new HashMap<>();
    private BukkitTask flightTask;

    private record Room(Location returnLoc, Location spot, boolean prevFlight, boolean prevFlying, Location panelBase) {
    }

    public BuildRoomManager(PaperLithographyPlugin plugin) {
        this.plugin = plugin;
    }

    /** Create or load the void build world (call on enable). */
    public void ensureWorld() {
        buildWorld = Bukkit.getWorld(WORLD_NAME);
        if (buildWorld != null) return;
        try {
            buildWorld = new WorldCreator(WORLD_NAME)
                    .generator(new VoidGenerator())
                    .environment(World.Environment.NORMAL)
                    .createWorld();
            if (buildWorld != null) {
                buildWorld.setSpawnFlags(false, false);
                buildWorld.setDifficulty(Difficulty.PEACEFUL);
                buildWorld.setStorm(false);
                try {
                    buildWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                    buildWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                    buildWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                    buildWorld.setGameRule(GameRule.FALL_DAMAGE, false);
                } catch (Throwable ignored) {
                }
                buildWorld.setTime(6000);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not create build world: " + t);
        }
    }

    public boolean isInRoom(Player player) {
        return rooms.containsKey(player.getUniqueId());
    }

    /** Keep flight enabled for players in a room (survival flight gets reset after teleport). */
    public void start() {
        flightTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (rooms.isEmpty()) return;
            for (UUID id : rooms.keySet()) {
                Player p = Bukkit.getPlayer(id);
                if (p == null || !p.isOnline()) continue;
                if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) continue;
                if (!p.getAllowFlight()) p.setAllowFlight(true);
            }
        }, 20L, 20L);
    }

    public void stop() {
        if (flightTask != null) flightTask.cancel();
    }

    /** Toggle: enter the build room for a panel, or leave if already in one. */
    public void enter(Player player, Panel panel, Location panelBase) {
        if (isInRoom(player)) {
            exit(player);
            return;
        }
        if (buildWorld == null) {
            player.sendMessage(ChatColor.RED + "Build world is unavailable.");
            return;
        }

        int idx = spotCounter++;
        int sx = idx * SPOT_SPACING;
        int sy = 100;
        int sz = 0;
        Location spot = new Location(buildWorld, sx, sy, sz);
        buildPlatform(spot);

        // Keep the real panel's chunk loaded so the live link + persistence keep working.
        panelBase.getChunk().addPluginChunkTicket(plugin);

        Room room = new Room(player.getLocation().clone(), spot,
                player.getAllowFlight(), player.isFlying(), panelBase.clone());
        rooms.put(player.getUniqueId(), room);

        // renderBase = platform corner; grid floats +2 above the platform (see openBuild yOffset).
        Location renderBase = new Location(buildWorld, sx, sy, sz);
        plugin.sessions().openBuild(player, panel, panelBase, renderBase);
        plugin.engine().markDirty(panel);

        // Stand at the front-center of the grid, looking toward it (+Z / south), able to fly.
        Location tp = new Location(buildWorld, sx + GridPos.SIZE / 2.0, sy + 1, sz - 3.0, 0f, 10f);
        player.setAllowFlight(true);
        player.teleport(tp);
        player.setFlying(true);
        // Re-apply flight after the cross-world teleport settles (it otherwise gets reset),
        // so survival players can fly inside the room too.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && isInRoom(player)) {
                player.setAllowFlight(true);
                player.setFlying(true);
            }
        }, 2L);
        player.sendMessage(ChatColor.AQUA + "Entered the build room "
                + ChatColor.GRAY + "(full-size). Right-click a cell to place/use, left-click to remove. "
                + ChatColor.DARK_GRAY + "Run /lithography build again to leave.");
    }

    /** Leave the build room: close the session, restore flight, teleport back. */
    public void exit(Player player) {
        Room r = rooms.remove(player.getUniqueId());
        if (r == null) return;
        plugin.sessions().close(player);
        clearPlatform(r.spot());
        try {
            r.panelBase().getChunk().removePluginChunkTicket(plugin);
        } catch (Throwable ignored) {
        }
        player.setFlying(false);
        player.setAllowFlight(r.prevFlight());
        if (player.getAllowFlight()) player.setFlying(r.prevFlying());
        player.teleport(r.returnLoc());
        player.sendMessage(ChatColor.GRAY + "Left the build room.");
    }

    /** On quit: free the room without teleporting (player is offline). */
    public void cleanup(Player player) {
        Room r = rooms.remove(player.getUniqueId());
        if (r != null) {
            clearPlatform(r.spot());
            try {
                r.panelBase().getChunk().removePluginChunkTicket(plugin);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Rescue a player who logged in inside the build world without an active room
     * (disconnected mid-edit, or server restarted) — teleport them to safety so they don't
     * fall into the void and die.
     */
    public void evacuate(Player player) {
        rooms.remove(player.getUniqueId());
        Location safe = player.getBedSpawnLocation();
        if (safe == null) {
            for (World w : Bukkit.getWorlds()) {
                if (!w.getName().equals(WORLD_NAME)) {
                    safe = w.getSpawnLocation();
                    break;
                }
            }
        }
        if (safe == null) return;
        player.setFlying(false);
        player.setAllowFlight(false);
        player.teleport(safe);
        player.sendMessage(ChatColor.YELLOW + "Returned you from the build room (you disconnected while inside).");
    }

    public boolean isBuildWorld(World w) {
        return w != null && w.getName().equals(WORLD_NAME);
    }

    public void cleanupAll() {
        for (Map.Entry<UUID, Room> e : rooms.entrySet()) {
            clearPlatform(e.getValue().spot());
            try {
                e.getValue().panelBase().getChunk().removePluginChunkTicket(plugin);
            } catch (Throwable ignored) {
            }
        }
        rooms.clear();
    }

    private void buildPlatform(Location spot) {
        World w = spot.getWorld();
        int sx = spot.getBlockX();
        int sy = spot.getBlockY();
        int sz = spot.getBlockZ();
        for (int x = sx - 2; x <= sx + GridPos.SIZE + 1; x++) {
            for (int z = sz - 4; z <= sz + GridPos.SIZE + 1; z++) {
                w.getBlockAt(x, sy, z).setType(Material.GRAY_CONCRETE, false);
            }
        }
    }

    private void clearPlatform(Location spot) {
        World w = spot.getWorld();
        int sx = spot.getBlockX();
        int sy = spot.getBlockY();
        int sz = spot.getBlockZ();
        for (int x = sx - 2; x <= sx + GridPos.SIZE + 1; x++) {
            for (int z = sz - 4; z <= sz + GridPos.SIZE + 1; z++) {
                w.getBlockAt(x, sy, z).setType(Material.AIR, false);
            }
        }
    }
}
