# Paper-Lithography

Etch functional redstone contraptions onto a sub-block grid inside a **single block** — on a vanilla Paper 1.20.1 server, with **no client mods, no resource packs, no downloads**. Players join with the normal Mojang launcher and it just works.

Inspired by Tiny Redstone / Compact Machines (both Forge mods), but done entirely server-side using Minecraft's vanilla **display entities** and **interaction entities**.

## Build

Requires JDK 17+ (the build targets Java 17).

```sh
./gradlew build
```

Output: `build/libs/PaperLithography-0.1.0.jar`. Drop it into your Paper 1.20.1 server's `plugins/` folder and restart.

## Usage

1. `/lithography give all` — get a panel plus one of every tiny component.
2. Place the **Lithography Panel** (a lodestone) anywhere.
3. **Sneak + right-click** the panel to toggle the editor. The 8×8×8 grid of tiny cells appears, rendered in-world.
4. With a component in hand, **right-click** a cell to place it.
   - **Right-click** a placed part with an empty hand to *use* it: toggle a lever, press a button, cycle a repeater's delay.
   - **Sneak + right-click** a placed part to remove it.
5. Wiring runs even when no one is watching — the editor visuals are spawned on demand and despawned when you close them; the circuit keeps ticking headlessly.
6. **Break the panel** to pick it up as a portable item that carries the entire circuit inside it. Place it again to restore it.

## Components

| Part | Behavior |
|---|---|
| Tiny Lever | Toggle power source (emits 15 while on). |
| Tiny Button | Momentary source (emits 15 for ~1s). |
| Tiny Redstone Dust | **Planar** wire (links sideways within its layer), carries 0–15, decays 1 per cell. |
| Tiny Via | Vertical (all-direction) link — bridges signal between layers, like a chip via. |
| Tiny Repeater | Directional diode, 1–4 tick delay. |
| Tiny Redstone Torch | Inverter; off when its mount is powered. |
| Tiny Lamp | Lights when any neighbour powers it. |

### Editing

- **Right-click a panel** → GUI editor: edit one layer at a time, every cell clickable. Place by grabbing a tiny part from your inventory and left-clicking a cell (consumed); left-click a placed part with an empty hand to take it back.
- **Sneak + right-click** → in-world 3D view (one layer; punch the panel to change layer, or use the GUI's Single/All toggle).
- The GUI and 3D view share the same layer/view state.
- **Place two panels touching** → they link automatically; signals cross the shared face.

## Status

Playable and runtime-tested on Paper 1.20.1: place / craft / edit (GUI + 3D) / wire / link adjacent panels / pick up as a portable item. The redstone simulation is **simplified but functional** — not a bit-exact vanilla reimplementation. Comparator and container components (chest/hopper/furnace) are planned but not yet implemented.

See `CLAUDE.md` for the architecture quick-reference and `docs/context-internals.md` for full design rationale.

## How it works (short version)

- A **panel** is a lodestone block. Its grid of components is stored in the **containing chunk's** `PersistentDataContainer` (plain blocks can't hold PDC; chunks can).
- In edit mode, each occupied cell gets a scaled **`block_display`** (the visible tiny block) and every cell gets an **`interaction`** entity (a clickable hitbox, since display entities have none).
- A per-tick engine propagates signals only for panels that need it.

No part of this requires anything installed on the client.

## License

See `LICENSE` (add one before publishing).
