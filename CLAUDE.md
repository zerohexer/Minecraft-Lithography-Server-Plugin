# CLAUDE.md

## Paper-Lithography — Quick Reference

Server-side Paper plugin that miniaturizes Minecraft blocks (especially redstone) onto a sub-block grid. Players build functional circuits/machines at fractional scale inside a "panel" block, can compact panels into portable items. **Vanilla clients only — no mods, no resource pack, no client downloads.**

The name is a metaphor: silicon lithography etches circuits at sub-millimeter scale onto a single chip. This plugin does the same for Minecraft — etch full redstone contraptions into one block.

### Hard Constraints (never violate)

1. **Paper 1.20.1**, Paper API only. No Bukkit-internal hacks, no NMS unless absolutely required.
2. **Vanilla client compatibility.** Anything that requires a client mod, resource pack, or modified launcher is OUT. Players join with the official Mojang launcher and it just works.
3. **No client downloads, ever.** Including optional resource packs. If we can't ship it server-side via standard packets, we don't ship it.
4. **No Forge / Fabric / Mohist.** Pure Paper.

### Core Architecture

```
Panel Block (real world block, e.g. lodestone w/ PDC marker)
  └── Grid (Map<GridPos, Component>) stored in PersistentDataContainer
        └── Component (lever, lamp, dust, repeater, …)
              ├── logical state (signal level, orientation, config)
              ├── block_display entity UUID    (rendered ONLY in edit mode)
              └── interaction entity UUID      (rendered ONLY in edit mode)

Tick Scheduler
  └── per-panel propagation pass (event-driven; only panels that changed)

Edit Mode (per-player, per-panel toggle)
  ├── spawn block_display + interaction entities for all components
  ├── handle left-click (remove) / right-click (place/config) on interactions
  └── on exit: despawn entities, logic keeps running headless
```

### Rendering — Use `block_display`, edit-mode-only

- `block_display` is the only vanilla entity that renders an actual block model at arbitrary scale. `item_display` renders the item icon (wrong for blocks). Armor stands cap at half-size. Falling blocks don't scale.
- **Display entities have NO hitbox / NO collision.** Players cannot stand on, walk through, or physically interact with tiny blocks. This is the central limitation that shapes everything else.
- Therefore: tiny blocks are for *contraptions inside a panel*, not for building tiny structures players move through. The panel block itself is full-size; players interact with its faces.
- Render only when a player is actively in **edit mode** for that panel. Despawn entities on exit. Logic runs headlessly. This is how we avoid FPS death at scale.

### Interaction — `interaction` entities, one per component

- `block_display` has no click events. Pair each rendered component with an `interaction` entity at the same position with matching width/height.
- Left-click (PlayerAttackEntityEvent on interaction) → remove component
- Right-click (PlayerInteractEntityEvent on interaction) → place / configure / toggle (depending on held item and component type)
- Reach distance is vanilla ~4.5 blocks. Panels should be sized so all sub-cells stay within reach when the player stands at the panel.

### Current State (verified live on `Server_Test/`)

MVP + several UX iterations, **runtime-tested on a live Paper 1.20.1 server** (loads, places, renders, wires, persists). Grid is **4×4×4** (cell = 1/4 block) for clickability. Built jar: `build/libs/PaperLithography-0.1.0.jar`.

**Components (9):** lever, lamp, dust, repeater, torch, button, **via** (vertical link), **bridge** (crossover), **comparator** (compare/subtract, analog). Register a new one = new `MiniBlock` subclass + `MiniBlockType` entry. Tiny parts craft **4 at a time**; panel crafts 1. (GUI palette = full bottom row of 9; Eraser moved to nav row, slot 38.)

**Two editors, shared layer state:**
- **GUI editor** (right-click panel) — chest UI, one Y-layer at a time, every cell clickable. The precise editor (no 3D occlusion). Has layer nav + a "3D View: Single/All" toggle.
- **3D in-world view** (sneak + right-click panel) — floating tiny blocks; shows **one layer** at a time (punch panel = change layer; sneak+punch = down) or all layers via the GUI toggle.
- GUI ↔ 3D layer & view-mode are **bidirectionally synced**.

**Done:** scaffold, panel+grid+chunk-PDC persistence, lever/lamp slice, redstone family, portable compact block, crafting recipes (+ recipe-book unlock on join, `/lithography recipes`), multi-panel viewing, **automatic adjacency linking** (touching panels share signals across the shared face), **survival placement** (consume from inventory / return on removal; creative = infinite palette), GUI layered editor, planar dust + via.
**Deferred (framework supports, additive):** explicit panel-face I/O config, container components (chest/hopper/furnace = Phase 5).

### Controls (current)

| Action | GUI editor | 3D in-world view |
|---|---|---|
| Open | right-click panel | sneak + right-click panel |
| Place part | grab part from inventory → left-click cell (creative: palette brush) | hold part → right-click cell (faces where you look) |
| Use (toggle lever / press button / repeater delay) | right-click cell (non-directional) / shift-click | right-click cell (empty hand) |
| Rotate directional (repeater/torch) | right-click cell | place facing your look direction |
| Remove (returns item) | left-click cell with empty hand (or Eraser + left-click) | sneak + right-click cell |
| Change layer | ◀ / ▶ buttons | punch panel (sneak+punch = down) |
| Single vs all layers | "3D View" toggle button | (set via GUI toggle) |
| Pick up panel | — | break the lodestone → portable item with the circuit inside |

### Build & Run

```
./gradlew build          # → build/libs/PaperLithography-0.1.0.jar  (sets Java 17 bytecode)
```
Drop the jar in a Paper 1.20.1 `plugins/` folder, restart. No client mods. `/lithography give all` for testing; everything is also craftable (see `/lithography recipes`).

Dev/test loop and the local `Server_Test` harness are documented in `docs/context-internals.md` §11.

### Remote Deploy — `sshd.kernelq.com` (live server)

Live Paper server: `zerohexer@sshd.kernelq.com:~/Minecraft-Server` (Paper git-196 / MC **1.20.1**, **Java 17**). Claude's deploy SSH key is authorized (key auth, no password). Other plugins present: chatclef (pre-existing load error, unrelated), SkinsRestorer. Deploy = upload jar → stop → relaunch in a `screen` named `minecraft` (so it survives disconnect).

```
# after building build/libs/PaperLithography-0.1.0.jar
scp build/libs/PaperLithography-0.1.0.jar zerohexer@sshd.kernelq.com:Minecraft-Server/plugins/

ssh zerohexer@sshd.kernelq.com '
cd Minecraft-Server
screen -S minecraft -p 0 -X stuff "$(printf "stop\r")"   # graceful stop (sends to console)
sleep 8
screen -dmS minecraft ./run.sh                            # relaunch detached
'
```
**Verify via the screen, NOT `latest.log`** — `logs/latest.log` is **root-owned and stale** (frozen May 3; the server runs as `zerohexer` and can't write it). Read the live console:
```
ssh zerohexer@sshd.kernelq.com 'screen -S minecraft -p 0 -X hardcopy -h /tmp/mc.txt; grep -aE "PaperLithography|Done \(|ERROR" /tmp/mc.txt | tail'
```
Gotchas: `run.sh` = `java -Xmx4G -Xms4G -jar server.jar nogui`; plugins dir is `Minecraft-Server/plugins/`. Always use the `minecraft`-named screen. Stale leftover `MC-server` screens exist — ignore them. SSH prints a harmless post-quantum warning; filter it. (Detail in `docs/context-internals.md` §13.)

### Critical Gotchas (don't waste turns rediscovering these)

1. **`block_display` has no collision.** Cannot be walked on. Cannot interact via block events (only via paired interaction entity). See "Rendering" above.
2. **`item_display` is wrong for blocks.** Renders item sprite, not the block model. Always use `block_display` for blocks.
3. **No native auto-install for client mods** — but irrelevant for us because we don't have any. Vanilla clients join, plugin handles everything server-side.
4. **Reach distance** is ~4.5 blocks survival. Panels larger than that need either multi-block panels or in-panel teleport / mini-map navigation.
5. **Entity rendering scales poorly.** A panel with 512 components = up to 1024 entities (display + interaction). Edit-mode-only is non-negotiable.
6. **Interaction entity hitbox vs `block_display` visual must match** or clicks miss. Set interaction `width`/`height` to the scaled block size.
7. **Display entity transformations must use interpolation** (`interpolation_duration`, `start_interpolation`) for smooth state changes — otherwise visible snap on every signal update.
8. **Plain blocks have NO PersistentDataContainer** — only `TileState` blocks (chests, signs…) do. The panel base is a lodestone (not a TileState), so panel data is stored in the **containing chunk's PDC** (`Chunk` is a `PersistentDataHolder`), with an in-memory cache in `PanelStore`. Loaded on chunk load, flushed on chunk unload / shutdown / every edit. PDC keys are namespaced (`new NamespacedKey(plugin, "...")`) — all centralized in `Keys.java`.
9. **Tick scheduler is event-driven.** Only panels marked dirty (or containing timed comps / linked to a neighbour) tick. `PropagationEngine` holds an active-panel set.
10. **No existing plugin does this.** Miniaturise (Hangar) is visual-only. LogicGates compresses gates into full-size blocks. We're filling a real gap.
11. **Build MUST be Java 17 bytecode.** Paper's plugin remapper rejects newer class files (`Unsupported class file major version 69` from JDK 25). Use `./gradlew build`, or `javac --release 17` if hand-compiling. The `Server_Test` JVM is JDK 25 → harmless jansi native crash on console shutdown only.
12. **Render the grid ABOVE the panel block (Y+1), not inside it.** The lodestone is solid and occludes anything drawn in its own 1×1×1 volume — that bug made the whole grid invisible. See `PanelRenderer.Y_OFFSET`.
13. **Empty cells need visible markers.** An empty panel has no component displays → editor looked empty/broken. `EditSession` spawns a small translucent marker per empty cell so the lattice is visible/clickable.
14. **Wires use a per-channel model** (`MiniBlock.channelForFace`/`channelLevel`). **Dust** = 1 channel, planar (N/S/E/W only, so layers don't merge). **Via** = 1 channel, all 6 dirs (deliberate vertical/layer link). **Bridge** = 3 channels (X=E/W, Z=N/S, Y=U/D) kept independent so crossing signals don't mix (same-layer crossover). `PropagationEngine.recomputeWires` resets from scratch each tick (avoids stale loops); two wires link across a face if either side exposes a channel there, routing into the receiving wire's channel for that face (default 0). Emitters/sinks stay omnidirectional; only wire↔wire routing is channel-gated.
15. **Linking is automatic by adjacency.** `Panel.get()` bridges one-cell-out-of-bounds lookups to the world-adjacent panel's edge cell (via `PanelLookup`/`PanelStore`). Touching panels share signals; the engine keeps linked panels active. No explicit link item.
16. **GUI ↔ 3D layer/view are one shared state.** Opening the GUI adopts the 3D session's current layer (don't reset to 0). Changing layer in either updates the other. Same for the Single/All view toggle.
17. **Directional parts: BACK = input, FRONT (facing) = output** (torch, repeater, comparator). Most "won't power / won't turn off" reports = the part rotated so its back faces empty space. Repeater/comparator **render facing is flipped** (`facing.getOppositeFace()`) because vanilla's block-state renders 180° opposite to our logical output — don't revert it.
18. **Custom looks = composite vanilla-block models, NOT textures.** `render/ModelPart` + `MiniBlock.model()` render a component as scaled/positioned vanilla blocks (zero downloads). True pixel textures need a resource pack (ruled out). Wires are circuit-green (LIME powered / GREEN off): dust = traces (centre + bars per `connMask`), via = green pillar, bridge = two crossing bars. `EditSession` keeps a `List<UUID>` of part-displays per cell. Per-player private (`setVisibleByDefault(false)` + owner `showEntity`).
19. **Repeater locking** works (latch/memory): a powered repeater/comparator into a side freezes output; shown via an emulated **bedrock bar** model part (`[LOCKED]` in tooltip).
20. **Diagnose with `/lithography debug`** — dumps the looked-at panel's parts + wire channel levels to `plugins/PaperLithography/debug.txt` (console log is unreliable: `latest.log` is root-owned/stale on the remote). Read that file, not the log.

### Conventions (codebase)

- Java 17 (Paper 1.20.1 baseline). Gradle Kotlin DSL (`build.gradle.kts`), wrapper committed.
- Package root: `com.zerohexer.paperlithography`.
- **`plugin.yml` (classic), NOT `paper-plugin.yml`.** Reversed the original doc note — classic registers commands/permissions without a bootstrap class and is more robust. See decisions log 2026-06-01.
- Components extend the abstract `MiniBlock` (component/MiniBlock.java): `renderData()`, `emittedPowerTo(face)`, `update()` (combinational), `tick()` (timed), `onUse()`, `write()/read()`. Register a new type in `MiniBlockType`.
- Persistent state: chunk PDC for placed panels (see gotcha #8), ItemStack PDC for portable panels. No flat files.
- Tick work currently uses the **global** Bukkit scheduler (`PropagationEngine`), one task at 1-tick interval over an active-panel set — NOT the region scheduler yet. Folia support deferred (single swap point in `PropagationEngine.start()`).

### Code Map (where things live)

```
PaperLithographyPlugin   wiring: keys, store, sessions, engine, recipes, listeners, command; orphan sweep
Keys                     all NamespacedKeys (incl. panelPos on interaction entities)
command/LithographyCommand   /lithography give|recipes|help
panel/GridPos            packed position (SIZE=4, 4³); 3-bit/axis packing (can grow to 8)
panel/Panel              sparse grid + binary (de)serialization; cross-panel get() bridging
panel/PanelLookup        interface: resolve a panel by world block coords (linking)
panel/PanelStore         runtime map + chunk-PDC persistence; implements PanelLookup
component/MiniBlock      abstract base + wire-channel API; MiniBlockType registry; impl/* the 9 parts (incl. TinyVia, TinyBridge, TinyComparator)
sim/PropagationEngine    per-tick: timed comps → wire net (reset+relax) → sinks → refresh; linked panels stay active
render/PanelRenderer     spawns block_display + interaction; cell math (1/4 block), Y+1 offset, empty-cell markers
render/EditSession[Manager]   per-player live 3D view; single-layer or all; layer/showAll state; bidir sync helpers
gui/PanelGui[Holder]     layered chest editor: 4×4 layer cells + nav + view-toggle + palette
gui/GuiListener          GUI clicks: place(consume)/use/rotate/remove, layer nav, view toggle
item/PanelItem           panel item, portable panel, component items + detection
item/Recipes             crafting recipes + recipe-book unlock (discover on join)
listener/PanelInteractionListener   place panel, open GUI / toggle 3D, punch=change layer, break→portable
listener/EntityInteractionListener  click interaction entities → place/use/remove (3D); survival consume/return
listener/WorldStateListener         chunk load/unload persistence, join=recipe unlock, quit cleanup
util/Directions          yaw → cardinal facing
```

### Redstone Model (simplified but functional)

Not a faithful vanilla reimplementation. Each component reports `emittedPowerTo(neighbourDir)`. **Wires** (dust/via/bridge) are recomputed **from scratch every tick** (`recomputeWires`) over **channels** (see gotcha #14): reset, inject from non-wire emitters per channel, relax wire→wire with −1 decay (capped passes). Resetting each tick prevents stale loops. Dust = 1 planar channel; via = 1 all-dir channel; bridge = 3 independent axis channels (crossover). Repeater/torch/button are timed via per-tick countdowns. Comparator is analog (compare/subtract), computed in `update()` with `needsTicking()=true` so its output reaches wires the next tick. 3D stacking works because emitters/sinks read all 6 sides. Finer vanilla analog subtleties (signal-strength quirks, container reads) not modeled.

### When To Read `docs/context-internals.md`

- Starting a fresh Claude session on this project → read it once, fully.
- Adding a new component type and unsure how propagation/rendering interacts → read the relevant section.
- Tempted to add a feature that violates a Hard Constraint → re-read the "Why we ruled out X" sections first.
- Questioning a past architectural decision (display entities, edit-mode rendering, panel block) → check the alternatives-considered section.

### Project Files

```
Paper-Lithography/
├── CLAUDE.md                    ← this file (every-prompt)
├── README.md                    ← build + usage for end users
├── docs/
│   └── context-internals.md     ← full design rationale (one-time read)
├── build.gradle.kts             ← Gradle build (Paper API, Java 17)
├── settings.gradle.kts
├── gradlew / gradlew.bat / gradle/wrapper/   ← committed Gradle wrapper
├── .libs/                       ← downloaded compile jars for the dev env (gitignored)
├── build/libs/PaperLithography-0.1.0.jar     ← built plugin
└── src/main/
    ├── java/com/zerohexer/paperlithography/  ← plugin source (see Code Map)
    └── resources/plugin.yml
```
