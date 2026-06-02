package com.zerohexer.paperlithography.sim;

import com.zerohexer.paperlithography.component.MiniBlock;
import com.zerohexer.paperlithography.component.impl.TinyBridge;
import com.zerohexer.paperlithography.component.impl.TinyDust;
import com.zerohexer.paperlithography.component.impl.TinyLever;
import com.zerohexer.paperlithography.panel.GridPos;
import com.zerohexer.paperlithography.panel.Panel;
import org.bukkit.block.BlockFace;

/** Offline check of the wire/bridge propagation logic (no server needed). */
public class BridgeTest {
    public static void main(String[] args) {
        PropagationEngine eng = new PropagationEngine(null);

        // --- Scenario A: E-W (X channel) line through a bridge ---
        Panel a = new Panel();
        TinyLever lvA = new TinyLever();
        lvA.onUse(null); // turn on
        a.set(new GridPos(0, 0, 0), lvA);
        a.set(new GridPos(1, 0, 0), new TinyDust());
        TinyBridge bA = new TinyBridge();
        a.set(new GridPos(2, 0, 0), bA);
        a.set(new GridPos(3, 0, 0), new TinyDust());
        eng.recomputeWires(a);
        System.out.println("[A: E-W] D1=" + lvl(a, 1, 0, 0)
                + " bridge.ch0(X)=" + bA.channelLevel(0)
                + " bridge.emit(EAST)=" + bA.emittedPowerTo(BlockFace.EAST)
                + " D2=" + lvl(a, 3, 0, 0));

        // --- Scenario B: N-S (Z channel) line through a bridge ---
        Panel b = new Panel();
        TinyLever lvB = new TinyLever();
        lvB.onUse(null);
        b.set(new GridPos(0, 0, 0), lvB);
        b.set(new GridPos(0, 0, 1), new TinyDust());
        TinyBridge bB = new TinyBridge();
        b.set(new GridPos(0, 0, 2), bB);
        b.set(new GridPos(0, 0, 3), new TinyDust());
        eng.recomputeWires(b);
        System.out.println("[B: N-S] D1=" + lvl(b, 0, 0, 1)
                + " bridge.ch1(Z)=" + bB.channelLevel(1)
                + " bridge.emit(SOUTH)=" + bB.emittedPowerTo(BlockFace.SOUTH)
                + " D2=" + lvl(b, 0, 0, 3));

        // --- Scenario C: both lines crossing through ONE bridge ---
        Panel c = new Panel();
        TinyLever lx = new TinyLever(); lx.onUse(null);
        TinyLever lz = new TinyLever(); lz.onUse(null);
        TinyBridge bC = new TinyBridge();
        c.set(new GridPos(2, 0, 2), bC);
        // X line: lever - dust - [bridge] - dust
        c.set(new GridPos(0, 0, 2), lx);
        c.set(new GridPos(1, 0, 2), new TinyDust());
        c.set(new GridPos(3, 0, 2), new TinyDust());
        // Z line: lever - dust - [bridge] - dust
        c.set(new GridPos(2, 0, 0), lz);
        c.set(new GridPos(2, 0, 1), new TinyDust());
        c.set(new GridPos(2, 0, 3), new TinyDust());
        eng.recomputeWires(c);
        System.out.println("[C: cross] X-out D(3,0,2)=" + lvl(c, 3, 0, 2)
                + " Z-out D(2,0,3)=" + lvl(c, 2, 0, 3)
                + " bridge.ch0(X)=" + bC.channelLevel(0)
                + " bridge.ch1(Z)=" + bC.channelLevel(1));
    }

    private static int lvl(Panel p, int x, int y, int z) {
        MiniBlock m = p.get(new GridPos(x, y, z));
        return m == null ? -1 : m.channelLevel(0);
    }
}
