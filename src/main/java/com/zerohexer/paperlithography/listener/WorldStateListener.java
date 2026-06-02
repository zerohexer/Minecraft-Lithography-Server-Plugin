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
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.sessions().close(event.getPlayer());
    }
}
