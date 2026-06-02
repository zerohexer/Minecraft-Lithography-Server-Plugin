package com.zerohexer.paperlithography.panel;

import org.bukkit.World;

/** Resolves a panel by its base block coordinates — used for cross-panel (linked) lookups. */
public interface PanelLookup {
    Panel panelAt(World world, int bx, int by, int bz);
}
