package com.zerohexer.paperlithography.gui;

import com.zerohexer.paperlithography.component.MiniBlockType;
import com.zerohexer.paperlithography.panel.Panel;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Identifies our editor inventory and carries its editing context (panel, layer, brush). */
public class PanelGuiHolder implements InventoryHolder {
    public final Panel panel;
    public final Location base;
    public int level = 0;
    public MiniBlockType brush = MiniBlockType.LEVER;
    public boolean eraser = false;
    public boolean viewAll = false; // mirrors the linked 3D view's mode for the toggle button
    public boolean showGrid = true; // mirrors the linked 3D view's grid-marker visibility

    private Inventory inventory;

    public PanelGuiHolder(Panel panel, Location base) {
        this.panel = panel;
        this.base = base;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
