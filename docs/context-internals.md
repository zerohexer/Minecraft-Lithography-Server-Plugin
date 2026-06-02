# Paper-Lithography — Context & Internals

**Read this fully when starting a fresh Claude session on this project.** It captures the *why* behind every architectural decision, the alternatives we rejected, and the constraints that shape everything else. `CLAUDE.md` at the project root is the on-demand quick reference; this file is the deep context.

---

## 1. Mission

Build a Paper plugin that lets players construct functional Minecraft contraptions — especially redstone — at fractional scale inside a single host block (called a **panel**). The panel can later be picked up as a portable item that preserves the entire circuit inside it.

The metaphor in the project name is deliberate: **silicon lithography** etches transistors and traces at sub-millimeter scale onto a single chip. The plugin does the analogous thing for Minecraft — etch a redstone CPU, or any contraption, into one block of world space.

This was inspired by mods like Tiny Redstone (Forge) and Compact Machines (Forge), but those require every player to install client mods. The whole point of this project is to do it **server-side only**, so any vanilla client can join and use it.

---

## 2. Hard Constraints (and why)

### 2.1. Paper 1.20.1, Paper API only

- The user runs a Paper 1.20.1 server. We target Paper because of its API breadth (PersistentDataContainer, display entities, interaction entities, region scheduler).
- 1.20.1 is the minimum because **display entities** and **interaction entities** were added in 1.19.4 — we need both. 1.20.1 has the stable form of these APIs.
- No NMS / internals unless absolutely forced. Paper API is rich enough; reaching into NMS makes us break on every Paper update.

### 2.2. Vanilla clients only — no mods, no resource pack, no downloads

This is the single most important constraint. It is **non-negotiable**. Every architectural decision flows from it.

The user explicitly ruled out:
- Forge / Fabric / NeoForge (would require client mod install)
- Mohist / Arclight (hybrid servers — would let mods run, but mods still need client install)
- Resource packs (technically server-deliverable, but the user wants zero friction — players join with vanilla Mojang launcher and it just works)
- ComputerCraft / Tiny Redstone / Compact Machines (all Forge mods)

What we ARE allowed to use:
- Anything the vanilla client renders natively from server-sent packets: standard entities, particles, sounds, container GUIs, chat, action bar, boss bar, scoreboards, teams.
- Display entities (`block_display`, `item_display`, `text_display`) — rendered by vanilla 1.19.4+ clients with no extra files.
- Interaction entities — vanilla-rendered (invisible by default).
- Standard inventory container GUIs (`openInventory`, `openWorkbench`, etc.).

### 2.3. No client downloads, ever

Even optional resource packs are out. The user wants this to be friction-free for players. If we can't express it through vanilla-rendered packets, we don't ship it.

---

## 3. Alternatives Rejected (and exactly why)

### 3.1. Rendering: why `block_display` is the only real choice

We surveyed every server-side way to render a block-shaped thing at custom scale:

| Approach | Verdict | Reason |
|---|---|---|
| `block_display` entity | ✅ chosen | Vanilla, scales arbitrarily, renders full block model |
| `item_display` entity | ❌ | Renders item sprite (a repeater item, not a placed repeater block) |
| Armor stand + block on head | ❌ | Max half-size (`Small: 1b`), no finer scaling |
| `falling_block` entity | ❌ | No scaling |
| Particles | ❌ | Not stable, can't be reliably interactive |
| Text display with unicode | ❌ | Looks like ASCII art, not real blocks |
| Player heads w/ custom skins | ❌ | Limited scaling, requires base64 skin data per face = effectively a resource pack workflow |
| Resource pack w/ custom models | ❌ | Violates "no client downloads" |
| Place real blocks somewhere hidden | ❌ | Full 1m³ — defeats the entire point of miniaturization |
| Sub-block world grid | ❌ | Impossible without engine changes; block grid is 1m³ in vanilla |

**Conclusion:** `block_display` is the only viable rendering layer for 1.20.1 vanilla clients. We commit to it.

### 3.2. Interaction: why we need `interaction` entities separately

`block_display` has no hitbox. Vanilla clients can't click on it; the server gets no event. So for each rendered component we spawn a paired **`interaction` entity** at the same position, with width/height matching the scaled block size.

- `PlayerInteractEntityEvent` fires when player right-clicks the interaction entity → place/configure/toggle handler.
- `EntityDamageByEntityEvent` (or `PrePlayerAttackEntityEvent` on Paper) fires when player left-clicks → remove handler.
- Interaction entities are invisible by default and have no collision, but their hitbox is fully clickable by vanilla clients.

This 1:1 pairing (block_display + interaction) is the standard pattern for clickable holograms on Paper servers. It's the right pattern here too.

### 3.3. UI: why in-world rendering, not GUI-only

We considered making the panel a pure-GUI experience (no entities, no rendering — just a chest GUI representing the grid):

- **Pros of GUI-only:** no FPS impact, vastly simpler, no entity lifecycle to manage, no persistence headaches.
- **Cons of GUI-only:** lousy UX for 3D spatial circuits. Repeater orientation, dust crossings, multi-layer signal flow — none of these read clearly in a flat 6×9 inventory grid. Debugging signal propagation becomes archaeology.

User confirmed they want components visible in the real world. So we commit to in-world rendering. **The GUI option is preserved as a possible inspector/debugger view later, not as the primary editor.**

### 3.4. Why edit-mode-only rendering

Rendering all components in all panels all the time would tank FPS for anyone near a complex circuit. A 512-component panel = 1024 entities (display + interaction pair per component). Multiply by however many panels exist on the server.

Solution: **entities only exist while a player is actively editing or inspecting a panel.**

- Player shift-right-clicks panel → enter edit mode → plugin spawns all entities for that panel for that player.
- Player exits edit mode (re-shift-right-click, walk away, leave server) → entities despawn.
- Logic (signal propagation, ticks) keeps running headlessly the whole time. Saved state is the source of truth, not the entities.

Optional **"monitor mode"** — show only output components (lamps, signal indicators) so you can glance at results without rendering the whole graph. Lower-fidelity always-on view.

### 3.5. Why a panel block, not a region or dimension

We considered Compact-Machines-style "teleport the player to a hidden dimension where the circuit is built at full scale and looks normal." Rejected because:
- Requires either a separate world (heavy) or teleporting players around (jarring)
- Tiny visuals are the *aesthetic* the user wants; full-scale-in-hidden-dimension defeats it
- Portable compact block is harder if the data lives in a separate world

A single panel block with PDC-stored grid is simpler, more local, and matches the "etch into one block" metaphor exactly.

---

## 4. Architecture

### 4.1. The Panel

A panel is a real, full-size Minecraft block in the world. It has a marker in its `PersistentDataContainer` saying "this is a Paper-Lithography panel" and storing the grid.

- Base block: **`LODESTONE`** (decided). Distinctive, uncommon in survival, no inventory GUI to conflict with.
- Created by: holding a panel item (PDC-tagged via `Keys.panelItem`) and right-clicking a placement spot. The plugin places a lodestone and registers the panel in `PanelStore`.
- Identified by: `PanelStore.isPanel(block)` — a runtime map lookup keyed by block location, **not** a block-PDC check (lodestone has no PDC; see §4.5).

### 4.2. The Grid

Inside the panel, components live on a 3D integer grid. Default dimensions: `8 × 8 × 8 = 512 cells` (matches Tiny Redstone's panel size as a sanity benchmark).

```
GridPos = (x, y, z) with 0 ≤ each < 8
Component = { type, orientation, state, … }
Panel.grid : Map<GridPos, Component>     // sparse — only occupied cells stored
```

Each cell maps to a **world-space position** by:
```
world_pos = panel_block.center + (gridPos - (3.5, 3.5, 3.5)) * cellSize
cellSize  = 1.0 / 8       // each tiny block is 1/8 of a full block
```

Tune `cellSize` later. 1/8 is a starting point; some components may need 1/4 for readability.

### 4.3. The Component Interface

```java
interface MiniBlock {
    MiniBlockType type();
    BlockData renderBlockData();     // what block_display should show
    int signalLevel();               // 0-15 for redstone-emitting comps
    boolean acceptsSignal();         // can receive from neighbors
    void onTick(Panel panel, GridPos pos);
    void onInteract(Player p, EquipmentSlot hand, ItemStack item);
    void onAttack(Player p);
    void onSignalInput(GridPos from, int level);
    PersistentDataContainer serialize(PersistentDataContainer into);
    void deserialize(PersistentDataContainer from);
}
```

Concrete classes per type: `TinyLever`, `TinyLamp`, `TinyRedstoneDust`, `TinyRepeater`, `TinyComparator`, `TinyTorch`, …

### 4.4. Propagation

Redstone signals propagate **event-driven**, not every-tick. When a component changes state, it queues its neighbors for re-evaluation. The panel runs one propagation pass per game tick (or every 2 ticks for repeater delay), draining the queue.

```
on component state change → enqueue neighbors → next tick:
    while queue not empty:
        comp = dequeue
        new_signal = comp.computeFromNeighbors()
        if new_signal != comp.signal:
            comp.signal = new_signal
            enqueue comp.neighbors
            if rendered: comp.updateBlockDisplay()
```

This mirrors how real Minecraft redstone works under Paper's Alternate Current implementation.

### 4.5. Persistence — chunk PDC, not block PDC

**Critical discovery during implementation:** plain blocks do **not** have a `PersistentDataContainer`. Only `TileState` blocks (containers, signs, skulls, sculk…) expose PDC. Lodestone is not a TileState, so we cannot store panel data on the block itself.

Resolution: store panel data in the **containing chunk's PDC**. `org.bukkit.Chunk` implements `PersistentDataHolder` (since 1.16) and the server persists chunk PDC across restarts. Implementation (`PanelStore`):

- Runtime source of truth: in-memory `Map<locationKey, Panel>`.
- `loadChunk(chunk)` on `ChunkLoadEvent` (and for already-loaded chunks on enable) deserializes the chunk's `chunk_panels` byte[] blob into the map.
- `saveChunk(chunk)` writes all panels located in that chunk back to the blob. Called on `ChunkUnloadEvent`, on disable, and after every edit (durability against crashes).
- `unloadChunk(chunk)` saves then drops the chunk's panels from the runtime map and deactivates them in the engine.

Alternatives considered and rejected: (a) a TileState base block like a barrel — works but its inventory GUI conflicts with our right-click; (b) a flat YAML/JSON file keyed by location — works but duplicates what chunk PDC already gives us for free and complicates world deletion/copy. Chunk PDC travels with the world data, which is the correct ownership.

- PDC size: 512 components × ~4 bytes each ≈ 2KB per panel; trivial.
- **Portable compact block**: on block break, serialize the grid into the dropped ItemStack's PDC (`Keys.panelData`); on placement of such an item, deserialize back into a fresh panel. ItemStacks always support PDC (unlike blocks).

### 4.6. Rendering Layer (edit-mode only)

When a player enters edit mode:
```
for each occupied cell in panel.grid:
    spawn BlockDisplay at cell.worldPos:
        - blockData = component.renderBlockData()
        - scale = (cellSize, cellSize, cellSize)
        - interpolation_duration = 2 ticks  (smooth state changes)
    spawn Interaction at cell.worldPos:
        - width  = cellSize
        - height = cellSize
        - response = false   (don't show attack swing)
    store both UUIDs on the component (transient, not persisted)
```

On exit:
```
for each component with spawned entities:
    remove BlockDisplay
    remove Interaction
    clear UUIDs from component
```

Player edit-mode state lives in a per-player session map (not persisted across restarts).

### 4.7. Tick Architecture

- Use Paper's `Bukkit.getRegionScheduler()` for per-panel ticks. This makes us Folia-compatible by default.
- A panel only schedules a tick when it has pending propagation. Idle panels consume zero CPU.
- Hard cap: max N propagation steps per panel per tick (default 256). Prevents runaway oscillators from freezing the server.

---

## 5. Landscape Research (Existing Plugins / Mods)

We searched for prior art in June 2026:

| Project | Type | What it does | Why it doesn't solve our problem |
|---|---|---|---|
| Miniaturise (GhastCraftHD, Hangar) | Paper plugin | Renders scaled-down builds via `block_display` | Purely visual/decorative. No interactivity, no redstone logic. |
| LogicGates | Paper plugin | Compresses gates into single full-size blocks | Different paradigm — not miniaturization. Useful inspiration only. |
| Mini Blocks (Spigot) | Spigot plugin | Survival-style decorative micro-blocks | Decorative only, not functional. |
| Tiny Redstone | Forge mod | Functional tiny redstone on a panel | Forge — requires client mod. |
| Compact Machines | Forge mod | Hidden interior dimension per block | Forge — requires client mod. Different paradigm (full-size in hidden dim). |
| ComputerCraft / CC: Tweaked | Forge/Fabric mod | Lua-programmable computers | Forge — requires client mod. |

**Conclusion: there is no existing Paper plugin that does what we want.** Miniaturise validates the rendering tier works at scale on vanilla clients. We're building the simulation + interaction tiers on top — a genuinely new project.

---

## 6. Known Limitations (acknowledge, don't try to fix)

### 6.1. Collision

`block_display` has no hitbox. Players cannot walk on, walk through (in the physics sense), or stand inside tiny blocks. The panel itself is a normal block players can stand on; the tiny components inside it are pure visuals + interactive only via paired `interaction` entities.

**This means tiny blocks are for contraptions, not for tiny architecture.** No tiny houses you walk around in. No tiny landscapes. This is a circuit/machine plugin, not a scale-toy.

### 6.2. Fluid flow

Vanilla water/lava use 8-level source detection across many adjacent full-size blocks. Replicating this in a sub-grid is a research project of its own. **Out of scope for v1.** Likely never. If a circuit needs fluid behavior, use a logical "fluid signal" abstraction instead of trying to simulate real water.

### 6.3. Mob interaction

Mobs can't see tiny blocks. They won't path on them, won't trigger pressure plates, won't get killed by tiny lava. Anything mob-driven (mob farms, spawner contraptions, drowner pits) is out of scope. The panel is a closed system the player interacts with directly.

### 6.4. Render distance + entity count

Even with edit-mode-only rendering, a single complex panel in edit mode may spawn hundreds of entities. Mitigation strategies:
- Configurable per-panel render limit (e.g. cap at 1024 entities, beyond that show outputs only)
- LOD: combine multiple components into a single `block_display` "panel skin" when zoomed out
- Per-player culling: don't render to players >N blocks away from the panel even in edit mode

These are v3+ optimizations. Don't pre-build them.

### 6.5. Vanilla redstone interaction at the panel boundary

What happens if a vanilla redstone signal hits the outside of a panel block? We need to define this:
- Option A: panel face acts as configurable I/O — wire vanilla redstone to a face, the face becomes an input pin to the grid edge of that face
- Option B: panel is entirely sealed; only interaction is through opening edit mode
- Option C: panel has dedicated "input" and "output" sub-cells at fixed grid positions adjacent to each face

**Recommended:** Option C. Six faces × one input cell + one output cell per face = 12 reserved grid positions. Simple, deterministic, lets you chain panels.

Decide before Phase 3.

### 6.6. Pistons pushing real blocks at sub-scale

Internal pistons pushing other tiny components within the same panel: fine, just shuffle grid entries.
External — a tiny piston trying to push a real-world full-size block: not supported. The panel is a closed system. Pistons inside affect other components inside.

---

## 7. Build Phases

Strictly sequential. Don't start a phase until the previous one is provably working.

### Phase 0 — Scaffold (target: half day)

- `build.gradle.kts` targeting Paper 1.20.1 API, Java 17
- `paper-plugin.yml` (use new format, not legacy `plugin.yml`)
- Main class `PaperLithographyPlugin extends JavaPlugin`
- Register `/lithography` command stub
- Verify: plugin loads on Paper 1.20.1 server, `/lithography` prints something

### Phase 1 — Panel block + empty grid (target: 1–2 days)

- Define `Panel` class (grid map + panel-block-position reference)
- Define `MiniBlock` interface (abstract)
- Implement panel creation: `/lithography give panel` gives a lore-tagged item; right-click placement creates a panel block with PDC marker.
- Implement PDC read/write for empty grid (serialize empty map, deserialize back)
- No rendering yet, no components yet. Just: place panel, server restart, panel still recognized.
- Verify: panel survives restart with PDC intact.

### Phase 2 — Vertical slice: tiny lever + tiny lamp (target: 2–3 days)

The load-bearing milestone. Smallest possible end-to-end feature.

- Implement `TinyLever` and `TinyLamp` MiniBlock classes.
- Add edit-mode toggle (shift-right-click panel face).
- In edit mode: spawn `block_display` (scaled lever / lamp) + interaction entity per occupied cell.
- Hold "tiny lever" item, right-click on an empty cell's interaction-entity-region → place lever.
- Right-click placed lever's interaction entity → toggle lever state, update block_display to flipped variant.
- Adjacent lamp checks neighbor cells; if any is a powered lever, lamp turns on, update block_display.
- Exit edit mode → entities despawn, panel state persists, lever stays "on" in PDC, logic continues to run (tick scheduler updates lamp state even with no observer).
- Re-enter edit mode → entities respawn with correct visual state.

**If this all works, the architecture is validated.** Everything else is adding component classes.

### Phase 3 — Redstone family (target: 1–2 weeks)

Implement on the framework Phase 2 built:
- `TinyRedstoneDust` (signal level 0–15, decays per cell)
- `TinyRepeater` (configurable delay 1–4 ticks, direction)
- `TinyComparator` (subtract / compare modes)
- `TinyRedstoneTorch` (inverter, soft-power neighbors)
- `TinyButton`, `TinyPressurePlate` (timed signal)

Decide and implement panel-face I/O (§6.5 Option C).

### Phase 4 — Portable compact block (target: 2–3 days)

- On panel block break (by player with permission): cancel the default drop, serialize the grid to a new ItemStack's PDC, drop that item.
- On placement of an item with serialized-panel PDC: place panel block, deserialize grid.
- Stack semantics: panels with different contents do NOT stack (force max stack size = 1 for non-empty panels).

### Phase 5 — Container components (target: optional, 1–2 weeks)

- `TinyChest`, `TinyBarrel`, `TinyHopper`, `TinyFurnace`
- These need vanilla container GUIs the player can open (via `player.openInventory(customInv)`).
- Tiny hopper pulls items from adjacent tiny containers on tick.
- Furnace cooks based on plugin's own fuel/recipe table (probably reuse vanilla recipes via Bukkit's recipe API).

Phase 5 is "nice to have." Phases 0–4 are the MVP.

---

## 8. What NOT To Build (yet, or ever)

Avoid scope creep. These are explicitly out:

- **Tiny mobs** — no scaled-down zombies, no tiny villagers. Mobs can't interact with tiny blocks anyway.
- **Tiny players** — players are full-size; they look "into" the panel through edit mode.
- **World-edit-style schematics** — paste a 100×100 build at 1/8 scale. Cool idea, but it's Miniaturise's territory and isn't aligned with the functional-circuit goal.
- **Cross-panel teleportation / quantum-entangled panels** — keep the data model local. If users want signal between panels, they wire vanilla redstone between two panels' I/O faces.
- **Tiny generators / power systems beyond redstone** — no FE/RF/EU equivalents. Vanilla redstone signal levels (0–15) are the only "power."
- **Resource-pack-based custom block textures** — violates the no-downloads constraint.
- **Cosmetic tiny blocks at the expense of functional ones** — every component should *do something*. Decorative-only is Miniaturise's job.

---

## 9. Decisions Log (timeline of why-this)

Append-only. When a future decision contradicts an earlier one, add a new entry referencing the previous.

### 2026-06-01

- **Project named "Paper-Lithography"** — metaphor for sub-block circuit etching.
- **Folder created** at `C:\Users\andreas\Paper-Lithography\`.
- **Committed to Paper 1.20.1 + vanilla clients only.** Forge / Mohist / resource packs all rejected.
- **Rendering: `block_display`** (no alternative is viable for vanilla 1.20.1 scaled block rendering).
- **Interaction: paired `interaction` entity per rendered component.**
- **UI strategy: in-world rendering as primary editor, GUI possibly added later as inspector.**
- **FPS strategy: edit-mode-only rendering.** Entities only exist while a player is actively editing or inspecting.
- **Build order: vertical slice (Phase 2 lever+lamp) before component breadth.**
- **Researched existing plugins:** Miniaturise (visual only), LogicGates (compressed but full-size), Mini Blocks (decorative). Confirmed real gap. We're building something new.

### 2026-06-01 — MVP implementation (phases 0–4)

- **Reversal: use classic `plugin.yml`, not `paper-plugin.yml`.** Classic format registers commands + permissions declaratively with no bootstrap class; more robust and less ceremony for our needs. The earlier "use new format" note in CLAUDE.md was over-cautious.
- **Reversal: panel data in chunk PDC, not block PDC** (see §4.5). Forced by the fact that lodestone (a non-TileState block) has no PDC. This is the single most important implementation finding — a fresh session must not "fix" this back to block PDC.
- **Base block = LODESTONE; cell size = 1/8** (8×8×8 grid exactly fills the host block).
- **Scheduler: global Bukkit scheduler**, one 1-tick repeating task over an active-panel set (`PropagationEngine`). Region scheduler / Folia deferred; single swap point documented.
- **Rendering at edit time spawns all 512 interaction entities** (so empty cells are clickable for placement) plus block_displays for occupied cells only. Acceptable for MVP; LOD/culling deferred.
- **Removal UX:** sneak + right-click a placed part (reliable `PlayerInteractEntityEvent`). Left-click removal via `EntityDamageByEntityEvent` is wired as best-effort but not relied upon (Interaction attack events are inconsistent).
- **Redstone model is simplified** (see CLAUDE.md "Redstone Model"): per-tick dust recompute-from-scratch to avoid stale loops; timed diodes via countdowns. **Comparator and panel-face I/O (§6.5) deferred** — additive, framework supports them.
- **Components implemented:** lever, lamp, dust, repeater, torch, button.
- **Verified:** compiles against real Paper 1.20.1 API (downloaded jars); runnable jar produced. **Not yet runtime-tested on a live server** — no server/client in dev env. That is the top open item.
- **Phase 5 (containers) not started** — explicitly optional per §7.

### 2026-06-01 — live testing + UX iteration (full detail in §11)

Tested on the real `Server_Test` server and iterated heavily. Headlines (see §11 for the why of each):
- Build must be **Java 17 bytecode** (remapper rejects JDK 25 major-69). §11.1
- Fixed invisible grid: **render above the block (Y+1)** + **markers for empty cells**. §11.2
- Grid **8³ → 4³** (1/4-block cells) for clickability. §11.3
- Added a **GUI layered editor** (the precise, occlusion-free editor); 3D view now **single-layer with punch-to-change**, plus a Single/All toggle. §11.4
- **Linking = automatic world adjacency** via `Panel.get()` edge bridging (`PanelLookup`). §11.5
- **Dust planar + new Via** component for deliberate vertical links (matches vanilla and real lithography). §11.6
- **Survival placement**: consume from inventory / return on removal; creative keeps infinite palette. §11.7
- **Crafting recipes** + recipe-book auto-unlock on join + `/lithography recipes`. §11.8
- **GUI↔3D layer/view bidirectional sync** (adopt current layer on open — don't reset). §11.9
- **Components now 8:** added `via`, then `bridge` (crossover, 3-channel wire). Removal also wired to `EntityDamageByEntityEvent` + empty-hand left-click in GUI.
- **Tiny-part recipes yield 4** (miniatures); panel yields 1.
- **Deployed to the live remote server** `sshd.kernelq.com` (see §13) via SSH key + screen.
- **Wire engine generalized to a per-channel model** (for the bridge) — see §11.6.
- **Added `comparator`** (analog, compare/subtract; `update()` + `needsTicking()`). Components now 9. GUI palette fills the whole bottom row; Eraser moved to nav slot 38.
- **Torch redefined as a clean directional inverter** (option B): `facing` = OUTPUT/front (like a repeater), input read from the back, emits 15 out the **front only** (was: mount-direction input + emit to all 5 non-mount sides). Rationale: enables NOT/NAND/NOR/SR-latch without solid blocks and without accidental side-merges in dense gate builds. **Key insight for CPU-building: Lithography has no solid blocks and torches don't mount on blocks — the torch reads the component directly behind it. The vanilla "dust-on-block + torch-on-side" idiom becomes "dust cell → torch facing away."**
- **Per-player private 3D views**: edit-mode display/interaction entities use `setVisibleByDefault(false)` + owner `showEntity` (EditSession needs the Plugin). Players no longer see each other's floating grids; late joiners don't either.

### 2026-06-02 — visuals + gate-building polish

- **Torch = clean directional inverter** (option B, see earlier): `facing` = OUTPUT/front, input from back, emits only out the front. Rendered as a **wall torch** that leans toward `facing` (free direction indicator). Vertical facings fall back to floor torch.
- **Repeater & comparator render facing is FLIPPED** (`setFacing(facing.getOppositeFace())`): Minecraft's repeater/comparator block-state `facing` renders 180° opposite to our logical output convention. Logic uses `facing`=output; render uses the opposite so the model points the right way. **Don't "fix" this back.**
- **Repeater locking** implemented (`TinyRepeater.isLocked`): a `TinyRepeater`/`TinyComparator` on a perpendicular side, powered and pointing into us, freezes `out`. Enables repeater latches / memory. Locked state shown by an **emulated bedrock bar** model part (no vanilla block-state for the lock overlay) + `[LOCKED]` in tooltip.
- **Composite model system** (`render/ModelPart`, `MiniBlock.model()`): a component renders as a *list* of scaled/positioned vanilla-block parts → custom 3D shapes with **zero downloads** (the modern armor-stand trick). `EditSession` holds a `List<UUID>` of part-displays per cell, reused in place on state change, rebuilt when part count/kind changes. This is the sanctioned way to "retexture" — shape from vanilla blocks; true pixel textures still require a resource pack (ruled out).
- **Circuit-green visuals**: dust = green PCB traces (centre pad + bars toward connected faces; `connMask` set by the engine each tick; LIME powered / GREEN off). Bridge = two thin crossing green bars. Via = thin green vertical pillar. Lamp = SEA_LANTERN when lit. Per-component `modelScale()/modelTranslation()` give single-part custom shapes; `model()` gives multi-part.
- **Geometry rule for all directional parts (torch/repeater/comparator): BACK = input, FRONT (facing) = output.** Most "it doesn't power / won't turn off" reports are the part oriented so its back faces empty space — rotate it. Confirmed twice via `/lithography debug`.

---

## 10. Open Questions

Resolved:
- **Base block:** ✅ `LODESTONE`.
- **Cell size / grid:** ✅ **1/4 block, 4×4×4** (was 1/8, 8×8×8 — too small to click in survival reach; see §11). Packing still reserves 3 bits/axis so it can grow back to 8 without a format change.
- **Permissions:** ✅ `paperlithography.use` (default true), `.give` / `.admin` (op).
- **Linking model:** ✅ automatic by world adjacency (§11.5), not the reserved-I/O-cells idea.

Still open:
- **Panel-to-panel explicit I/O pins (§6.5):** superseded for now by automatic adjacency linking; an explicit pin/config could still be added.
- **Comparator** component (subtract/compare) — not implemented.
- **Configurable component palette** (YAML enable/disable per server) — before Phase 5.
- **Folia support** — still on the global scheduler; single swap point in `PropagationEngine.start()`.
- **Grid > 4³ / paging in the GUI** — the GUI layout assumes ≤4 rows of cells; growing the grid needs GUI paging.

---

## 11. Live Testing & UX Iteration (session 2 — read this!)

Everything below was found/built while testing on the real `Server_Test` server. A fresh session should treat these as settled — don't re-derive or revert them.

### 11.1. Build must be Java 17 bytecode
First live load failed: every class threw `Unsupported class file major version 69`. Cause: hand-`javac` on JDK 25 emits major-version 69; Paper's plugin remapper only accepts ≤ Java 17 (major 61). Fix: `javac --release 17` (Gradle already sets `release 17`). Verify with `od -An -tx1 -j6 -N2 X.class` → `00 3d` (61). The `Server_Test` JVM is JDK 25, which also causes a harmless `jansi` native crash on console shutdown (Paper 1.20.1 supports JDK 17–21) — it happens *after* the world saves, so it's cosmetic.

### 11.2. The grid was invisible — two bugs
- **Rendered inside the lodestone.** The grid was drawn in the panel block's own 1×1×1 volume; the solid lodestone model occluded all of it. Fix: render **one block above** the panel (`PanelRenderer.Y_OFFSET = 1.0`).
- **Empty panels rendered nothing.** Only occupied cells got displays, so a fresh panel's editor was invisible (just invisible interaction hitboxes). Fix: spawn a small translucent **marker** (`LIGHT_GRAY_STAINED_GLASS`, ~0.45 cell, centered) for every empty cell so the lattice is visible and you can see where to click.

### 11.3. Grid shrunk 8³ → 4³
1/8-block cells were too small to reliably click within survival reach, and 512 cells over-dense. Settled on **4×4×4, cell = 1/4 block**. Much more usable; still enough for real circuits. Tunable later via `GridPos.SIZE` (+ GUI paging if > 4).

### 11.4. Two editors (GUI is the precise one)
Clicking tiny cells in 3D always hits the nearest cell toward the camera — interior/back cells are unreachable (raycast occlusion). So we added a **GUI editor** (`gui/`): a chest UI showing one Y-layer as a 4×4 of cells, with layer nav, a palette, an eraser, and a "3D View: Single/All" toggle. Every cell is directly clickable. The **3D in-world view** remains as a spatial visualizer/editor but now shows **one layer at a time** (punch the panel to change layer) to avoid the same occlusion; the GUI's "All" toggle brings back the full 4³ overview.

### 11.5. Linking = automatic adjacency (not an item)
Most accurate to *both* vanilla and real lithography: planar layers + deliberate vertical links. For panel-to-panel, we made it **automatic by world adjacency** — `Panel.get()` resolves a one-cell-out-of-bounds lookup to the touching panel's matching edge cell (via `PanelLookup` implemented by `PanelStore`; panels are `bind()`-ed to their world coords on create/load). Touching panels (any of 6 faces — grids line up because each renders in its own block's Y+1 space) share signals across the seam. The engine keeps linked panels in the active set so signals keep crossing; placing/breaking a panel marks neighbours dirty. No special link item — snapping panels together *is* the link.

### 11.6. Dust is planar; the Via is the vertical link
User asked whether dust should be omni or "one line." Answer (settled): **planar dust + an explicit vertical connector**, because that's accurate to *both* vanilla (stacked dust does NOT auto-connect straight up) *and* real lithography (planar traces + vias). Implementation:
- `MiniBlock.isWire()` / `wireConnects(face)`. **Dust** connects only N/S/E/W (planar) → independent wire layers, no blob. **Via** (`TinyVia`, end-rod item icon, climbing-redstone visual) connects all 6 → deliberate cross-layer bridge.
- `PropagationEngine.recomputeWires` generalizes the old dust pass to all wires; two wires connect across face *f* if `A.wireConnects(f) || B.wireConnects(opposite)` (so a via bridges into planar dust).
- Emitters/sinks (lever/lamp/repeater/torch) stay omnidirectional — only dust↔dust wiring is planar. So lever→lamp stacked still works; only dust *wire* won't merge vertically.
- Via recipe is redstone + **2** glass panes (distinct from dust's 1 pane).

**Bridge / crossover (added after).** A trace or "bidirectional dust" was considered and rejected: making dust axis-locked would kill effortless fan-out, and a same-layer crossover fundamentally needs one cell to carry two independent signals — a trace can't (the crossing point is a single cell). The right primitive is a **bridge**: `TinyBridge` carries **3 independent channels** (X=E/W, Z=N/S, Y=U/D) so two or three wires cross through one cell without mixing. This forced generalizing the wire engine from one-signal-per-wire to a **per-channel model**: `MiniBlock.channelForFace(f)` (which channel a face routes through, −1 if none), `channelCount()`, `channelLevel/setChannelLevel`. Dust = 1 channel/planar, via = 1 channel/all-dirs, bridge = 3 channels. `recomputeWires` now relaxes per channel; link rule: across face *f*, connect if either side exposes a channel there, routing into the receiver's channel for *f* (default 0 for single-channel wires) — this keeps via's promiscuous bridging working. Bridge recipe = 2 redstone + 1 pane; item icon = iron bars. Crossovers were already possible via layers+vias; the bridge just allows them on a single layer.

**Bridge visual iterations (don't repeat these dead ends):** first rendered as a 4-way redstone cross — looked like a *joined* junction (misleading). Then as a two-line "overpass" (E-W line on the floor + raised N-S line) — this (a) made users think the raised line was a *separate layer*, and (b) the two stacked redstone-wire `block_display`s z-fought, so the floor line appeared not to work. **The logic was never wrong** — verified with an offline harness (`test/com/zerohexer/.../sim/BridgeTest.java`, which calls the now package-private `recomputeWires`): both axes carry signal and two lines cross one bridge independently. Final visual (chosen by user): a **single iron-bars block**, with bars extended along whichever axis carries signal (live hint) and exact X/Z/Y in the GUI tooltip. One display → no z-fight, no layer confusion. Lesson: don't represent one logical cell with two overlapping flat display entities.

### 11.7. Survival placement (consume / return)
The palette was creative-style (infinite). Now: in survival you **grab a tiny part from your own inventory onto the cursor and left-click a cell** — one is consumed; **left-click a placed part with an empty cursor returns the item**. Creative keeps the infinite palette brush. Same consume/return logic applies to the 3D in-world placement (consume held item / return on removal). Implemented in `GuiListener` and `EntityInteractionListener`. GUI click handling allows normal pickup in the player's own inventory but cancels shift-click (so items can't be dumped into the panel) and cancels all top-inventory default clicks.

### 11.8. Crafting + discoverability
Recipes registered in `item/Recipes` (`Bukkit.addRecipe`, keys removed-then-added to survive `/reload`). Panel = 8 Glass blocks (solid, **not** panes) around a Block of Redstone. Tiny part = full block + Glass Pane ("lens"); via = redstone + 2 panes. Recipes are **auto-unlocked into each player's recipe book on join** (`player.discoverRecipes`) so they're discoverable in-game, plus `/lithography recipes` lists them. (Earlier "panel won't craft" reports were the glass-block-vs-pane / partial-fill confusion, not a registration bug.)

### 11.9. Controls & GUI↔3D sync
- GUI cell: left-click = place from cursor (consume) / remove (empty cursor, returns item); right-click = rotate (directional) or use (lever/button); shift-click = change setting (repeater delay). Right-click panel opens GUI; sneak+right-click opens 3D.
- **Layer & view mode are one shared state across GUI and 3D, bidirectional:** opening the GUI *adopts* the 3D session's current layer (must NOT reset to 0 — that was a reported bug); changing layer in the GUI moves the 3D view; punching the panel updates an open GUI too; the Single/All toggle is shared. Helpers live in `EditSessionManager` (`getLayer`/`setLayer`/`setShowAll`/`isShowAll`) and the GUI mirrors `viewAll` in its holder.
- Players may have **multiple panels open at once** (`EditSessionManager` keys sessions by player + panel base). Interaction entities carry their panel's base coords (`Keys.panelPos`) so clicks route to the right panel.

---

## 12. Dev / Test Workflow (the `Server_Test` harness)

`Server_Test/` is a real Paper 1.20.1 server (paper-1.20.1-196.jar, EULA accepted, offline mode, the dev player `The_Zerohexer` is opped in `ops.json`).

Compile + package (no Gradle needed in the dev box; jars cached in `.libs/`):
```
CP=$(ls .libs/*.jar | tr '\n' ';')
javac --release 17 -d build/classes -cp "$CP" @sources.txt      # find src/main/java -name "*.java" > sources.txt
sed 's/${version}/0.1.0/' src/main/resources/plugin.yml > build/classes/plugin.yml
jar --create --file build/libs/PaperLithography-0.1.0.jar -C build/classes .
```

Run with a **command pipe** so the console is drivable from a background shell:
```
cd Server_Test && : > cmds.txt && tail -n +1 -f cmds.txt | java -Xmx2G -jar paper-1.20.1-196.jar nogui   # run_in_background
```
Send console commands by appending to `cmds.txt`, e.g. `echo "op NAME" >> cmds.txt`, `echo "stop" >> cmds.txt`.

Redeploy/restart loop: append `stop`, wait for `All dimensions are saved`, force-kill any lingering `java.exe` (JDK 25 hangs on the jansi shutdown crash — the save already completed so it's safe), copy the new jar into `plugins/`, relaunch. The `.libs/` compile jars are gitignored. To watch for errors while playing, tail the task output filtered for `ERROR|Exception|[PaperLithography|joined the game`.

Caveat: in-world *visual* correctness (does the grid look right, do clicks land) can only be confirmed by a connected human client — the dev box can verify load/enable/console behaviour but not rendering.

**Offline logic testing (no server):** the propagation engine's pure logic (`channelForFace`, `emittedPowerTo`, `recomputeWires`) touches no Bukkit runtime, so it can be unit-tested headlessly. `recomputeWires` was made package-private for this. Example: `test/com/zerohexer/paperlithography/sim/BridgeTest.java` builds Panels with levers/dust/bridges and asserts channel levels — compile it against `build/classes` + `.libs/*` and run `java`. Use this to settle "is it logic or rendering?" questions (it proved the bridge logic was fine and the bug was purely visual). The `test/` dir is NOT compiled into the shipped jar (build only globs `src/main/java`).

---

## 13. Remote Production Server (`sshd.kernelq.com`)

The user's live server: `zerohexer@sshd.kernelq.com:~/Minecraft-Server`. Paper **git-196 (MC 1.20.1)**, **Java 17.0.14** (matches our build exactly — no jansi issue). Launches via `run.sh` = `java -Xmx4G -Xms4G -jar server.jar nogui`. Other plugins: `chatclef` (has a pre-existing load failure, not ours) and `SkinsRestorer`. Runs inside a detached `screen` named `minecraft`.

**Access:** Claude's `~/.ssh/id_ed25519` (generated 2026-06-01, comment `claude-deploy`) is in the server's `authorized_keys`, so SSH/SCP work non-interactively (`-o BatchMode=yes`). The user authorized it once by appending the pubkey via their own password login. SSH prints a post-quantum warning to stderr — filter it out of output parsing.

**Deploy procedure** (see CLAUDE.md "Remote Deploy" for the exact commands): `scp` the jar into `Minecraft-Server/plugins/`, send `stop` to the `minecraft` screen's console (`screen -S minecraft -p 0 -X stuff "$(printf 'stop\r')"`), wait, then `screen -dmS minecraft ./run.sh`. Using `-dmS` keeps it alive across SSH disconnect.

**Gotcha that cost real debugging time (2026-06-01):** `logs/latest.log` on this server is **owned by `root` and frozen** (from a previous run started as root). The server runs as `zerohexer` and therefore cannot write or rotate it — `latest.log` shows an ancient run and never updates. After deploying, the new server's console output only exists in the **screen buffer**, not in `latest.log`. Verifying the deploy by reading `latest.log` falsely showed "plugin didn't load." Correct verification: `screen -S minecraft -p 0 -X hardcopy -h /tmp/mc.txt` then read `/tmp/mc.txt`. (Real fix for the server owner: `sudo rm logs/latest.log` so Paper recreates it as `zerohexer`, or `chown` it.) Also: file mtimes and in-log timestamps appear in different timezones here, so don't reason about ordering from timestamps. Several stale `MC-server` screens linger from past runs — harmless, ignore; only the `minecraft` screen matters.
