package com.zerohexer.paperlithography.listener;

import com.zerohexer.paperlithography.PaperLithographyPlugin;
import com.zerohexer.paperlithography.panel.Panel;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

/** Loads/saves panels with their chunks and cleans up edit sessions. */
public class WorldStateListener implements Listener {
    private final PaperLithographyPlugin plugin;

    public WorldStateListener(PaperLithographyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Panel p : plugin.store().loadChunk(event.getChunk())) {
            plugin.engine().markDirty(p);
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (Panel p : plugin.store().unloadChunk(event.getChunk())) {
            if (p != null) {
                plugin.engine().deactivate(p);
                plugin.sessions().closeByPanel(p);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.recipes() != null) plugin.recipes().discover(event.getPlayer());
        // Rescue anyone who logged in inside the build world (disconnect mid-edit / restart).
        if (plugin.buildRooms() != null && plugin.buildRooms().isBuildWorld(event.getPlayer().getWorld())) {
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> plugin.buildRooms().evacuate(event.getPlayer()), 1L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.buildRooms() != null) plugin.buildRooms().cleanup(event.getPlayer());
        plugin.sessions().close(event.getPlayer());
    }
}
