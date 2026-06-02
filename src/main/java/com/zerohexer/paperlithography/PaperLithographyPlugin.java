package com.zerohexer.paperlithography;

import com.zerohexer.paperlithography.command.LithographyCommand;
import com.zerohexer.paperlithography.item.PanelItem;
import com.zerohexer.paperlithography.listener.EntityInteractionListener;
import com.zerohexer.paperlithography.listener.PanelInteractionListener;
import com.zerohexer.paperlithography.listener.WorldStateListener;
import com.zerohexer.paperlithography.panel.Panel;
import com.zerohexer.paperlithography.panel.PanelStore;
import com.zerohexer.paperlithography.render.EditSessionManager;
import com.zerohexer.paperlithography.sim.PropagationEngine;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class PaperLithographyPlugin extends JavaPlugin {
    private Keys keys;
    private PanelItem panelItem;
    private PanelStore store;
    private EditSessionManager sessions;
    private PropagationEngine engine;
    private com.zerohexer.paperlithography.item.Recipes recipes;
    private com.zerohexer.paperlithography.world.BuildRoomManager buildRooms;

    @Override
    public void onEnable() {
        this.keys = new Keys(this);
        this.panelItem = new PanelItem(keys);
        this.store = new PanelStore(this, keys);
        this.sessions = new EditSessionManager(this, keys);
        this.engine = new PropagationEngine(this);
        this.engine.setSessions(sessions);
        this.buildRooms = new com.zerohexer.paperlithography.world.BuildRoomManager(this);
        this.buildRooms.ensureWorld();
        this.buildRooms.start();

        // Sweep any orphaned display/interaction entities left by a crash.
        sweepOrphanEntities();

        // Load panels in already-loaded chunks (e.g. after /reload).
        for (World w : getServer().getWorlds()) {
            for (org.bukkit.Chunk c : w.getLoadedChunks()) {
                for (Panel p : store.loadChunk(c)) {
                    engine.markDirty(p);
                }
            }
        }

        getServer().getPluginManager().registerEvents(
                new PanelInteractionListener(this), this);
        getServer().getPluginManager().registerEvents(
                new EntityInteractionListener(this), this);
        getServer().getPluginManager().registerEvents(
                new WorldStateListener(this), this);
        getServer().getPluginManager().registerEvents(
                new com.zerohexer.paperlithography.gui.GuiListener(this), this);

        LithographyCommand cmd = new LithographyCommand(this);
        if (getCommand("lithography") != null) {
            getCommand("lithography").setExecutor(cmd);
            getCommand("lithography").setTabCompleter(cmd);
        }

        this.recipes = new com.zerohexer.paperlithography.item.Recipes(this);
        this.recipes.register();
        for (org.bukkit.entity.Player p : getServer().getOnlinePlayers()) {
            recipes.discover(p);
        }

        engine.start();
        getLogger().info("Paper-Lithography enabled. Panels are stored in chunk PDC.");
    }

    @Override
    public void onDisable() {
        if (buildRooms != null) {
            buildRooms.stop();
            buildRooms.cleanupAll();
        }
        if (sessions != null) sessions.closeAll();
        if (engine != null) engine.stop();
        if (store != null) store.saveAllLoaded();
        sweepOrphanEntities();
    }

    /** Remove every entity tagged as owned by this plugin (display + interaction). */
    public void sweepOrphanEntities() {
        for (World w : getServer().getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getPersistentDataContainer().has(keys.ownedEntity, PersistentDataType.BYTE)) {
                    e.remove();
                }
            }
        }
    }

    public Keys keys() {
        return keys;
    }

    public PanelItem panelItem() {
        return panelItem;
    }

    public PanelStore store() {
        return store;
    }

    public EditSessionManager sessions() {
        return sessions;
    }

    public PropagationEngine engine() {
        return engine;
    }

    public com.zerohexer.paperlithography.item.Recipes recipes() {
        return recipes;
    }

    public com.zerohexer.paperlithography.world.BuildRoomManager buildRooms() {
        return buildRooms;
    }
}
