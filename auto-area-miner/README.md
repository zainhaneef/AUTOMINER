# Auto Area Miner (Fabric, Minecraft 1.21.11) - manual-confirmation mode

A client-side quarry assistant. You pick two corners; it scans the area, plans a layer-by-layer
serpentine route, shows you the next block to mine (and the best centre block for multi-block
pickaxes), and counts progress only when the world confirms a block is gone.

**It never moves, aims or clicks for you.** DonutSMP's rules were reported (by a third-party
source, not verified) to ban macros/automation, so this build is guidance-only. Check the server's
own rules before using any mod there.

## STATUS: NOT COMPILED
This source was written without being able to download Minecraft/Fabric, so it has never been
built or run. Expect a few compile errors against the 1.21.11 APIs on the first build; send them
to Claude and they get fixed. The riskiest spots: `render/WorldOverlay.java`, the HUD registration
and `getChunkManager().isChunkLoaded` (see "If the build fails").

## Build (Windows)
1. Install Java 21 (JDK).
2. This zip has no Gradle wrapper (it is a binary file). Copy `gradlew`, `gradlew.bat` and the
   `gradle\` folder from another Fabric project you already build (for example AutoBuild Pro)
   into this folder. The wrapper must be Gradle 9.x. Alternative: with Gradle installed run
   `gradle wrapper --gradle-version 9.2.1` here once.
3. Double-click `build.bat` (or run `gradlew build`). If the full build fails it retries
   automatically without the in-world overlay.
4. Your mod is `build\libs\auto-area-miner-1.0.0.jar` (not the `-sources` one). Put it in
   `.minecraft\mods` together with Fabric API for 1.21.11.

## If the build fails
- Gradle can't resolve a version: edit `gradle.properties` (loader, Fabric API, Loom, yarn).
  Current values came from third-party pages and are unverified.
- Error in `WorldOverlay.java` only: run `gradlew build -PnoWorldRender`.
- Anything else: paste the full error output to Claude.

## Commands
| Command | What it does |
|---|---|
| `/miner pos1`, `/miner pos2` | Set corners (block you look at, else your position) |
| `/miner start` / `pause` / `resume` / `stop` | Control the run |
| `/miner status` | Print state, progress, speed, pickaxe |
| `/miner clear` | Remove the selection |
| `/miner save <name>`, `load <name>`, `list` | Quarry presets |
| `/miner profile [id\|auto]` | Show / force a pickaxe profile |
| `/miner calibrate`, `calibrate save <id>` | Measure what a special pickaxe really breaks |
| `/miner retry` | Restore blocks that were skipped after failed attempts |
| `/miner rescan` | Stop and rescan |
| `/miner gui` | Control panel with buttons |
| `/miner overlay` | Toggle in-world outlines |
| `/miner reload` | Reload both config files |

## How a run goes
SCANNING (budgeted per tick) -> PLANNING (one layer, serpentine, centre selection) ->
MOVING (out of reach) -> POSITIONING (in reach, not aimed) -> MINING (aimed) ->
VERIFYING (centre broke, waiting for server updates) -> next target. RECOVERING handles retries
and unloaded chunks; PAUSED has a stated reason; COMPLETED / ERROR end the run.

## Pickaxe profiles (`config/areaminer-pickaxes.json`)
Each profile has match rules (`nameContains`, `loreContains`, `itemId`) and a pattern
(`squareSize` for NxN in the layer plane, or explicit `offsets` as [dx,dy,dz]).
Defaults: `normal` (1 block) and `amethyst` (3x3, matched by name). The Amethyst details are
assumptions: use `/miner calibrate` on a real block to record the true pattern, then
`/miner calibrate save amethyst`.

Rules the planner follows:
- Progress counts only blocks the world shows as gone, each once.
- Centre blocks must be real mineable blocks, and by default the whole pattern must stay inside
  the quarry. Edge blocks that can't be covered that way pause the run with a message asking you
  to swap to a normal pickaxe (or set `allowOvershoot`).
- If a mine breaks only one block when several were expected twice in a row, you get a warning
  (ability on cooldown / not activated / wrong profile).
- Mine each centre from its TOP face so a flat 3x3 stays inside the layer.

## Config (`config/areaminer.json`)
Scan/sweep/plan budgets per tick, `maxVolume`, `reach`, timeouts and retries, `allowOvershoot`,
inventory and durability pauses, `ignoredBlocks`, HUD position and map size.

## Not included (on purpose / honestly)
- No automatic walking, aiming or clicking.
- No automatic item dropping. Inventory-full and low-durability only pause.
- No separate worker threads: world reads must happen on the client thread, so work is sliced
  per tick with budgets instead.
- Block changes are detected by polling the pattern every tick plus a per-layer sweep, not by
  intercepting network packets.
