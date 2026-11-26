# Architecture

How Bun Voyage! is put together. For *how to change it*, see [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md).

All code lives in one Gradle module (`app`), package `com.ngstudio.burgerready`, split into layers:

```
                 ┌─────────────────────────── UI thread (Compose) ───────────────────────────┐
  MainActivity → │ ui/AppRoot (Navigator: stack of Route) → ui/screens/* ← ui/kit/* widgets   │
                 │      │ reads/writes                         │ hosts                        │
                 │  data/GameRepo (SaveData state)        render/GameSurfaceView (GLSurfaceView)│
                 └──────┼──────────────────────────────────────┼────────────────────────────┘
                        │ save JSON (io thread)                │ joystick → GameSession (volatile fields)
                        ▼                                      ▼
                 filesDir/save.json          ┌──────────── GL thread ─────────────┐
                                             │ render/GameRenderer.onDrawFrame     │
                                             │   game/World.update() ×N @ 60 Hz    │  ← pure Kotlin, JVM-testable
                                             │   events → FX, sounds, counters     │
                                             │   render/WorldView → engine/Renderer│  ← GLES 3 instanced toon renderer
                                             │   publish HudState @ 15 Hz          │
                                             └─────────────────────────────────────┘
  audio/AudioEngine: SoundPool (any thread) + MusicMixer thread (AudioTrack)
```

| Package | Responsibility | Android deps? |
|---|---|---|
| `game/` | The whole simulation: rules, levels, layouts, navigation, bots, economy | **None.** Keep it that way: it's unit-tested on the JVM and used by the offline tuner |
| `engine/` | Generic rendering: GL helpers, `.bvm` loading, instanced renderer, shaders, camera, sprite atlas, particles | GLES, Bitmap |
| `render/` | Game-specific glue: draws a `World` with `engine`, runs the GL loop, handles input | GL, View |
| `data/` | Persistent progress and catalogues | Context (files), Compose state |
| `audio/`, `ads/` | Platform services behind small APIs | Yes |
| `ui/` | Every screen and widget (Compose) | Yes |

---

## 1. App start-up
1. `App.onCreate` (`App.kt`):
   - creates `GameRepo` and loads the save;
   - creates `AudioEngine` and loads the SFX on a background thread;
   - picks the `AdsProvider` (`DebugMockAds` if `BuildConfig.DEBUG_AD_UNLOCK`, else `NoAds`);
   - loads `assets/levels/targets.bin` into `LevelGenerator.installTargets()`.
2. `MainActivity` installs the Android 12+ splash (`Theme.BunVoyage.Splash` → `drawable-nodpi/splash_icon.png`), hides the system bars and sets the Compose content `AppRoot`.
3. `AppRoot` starts on `Route.Splash`. It goes to `Onboarding` the first time, otherwise to `Home(tab = 2)`. Finishing onboarding goes straight into `Game(1)`.

## 2. Navigation (`ui/AppRoot.kt`)
A tiny custom navigator: `Navigator.stack` is a `mutableStateListOf<Route>`, and an `AnimatedContent` shows `stack.last()`.

| Route | Screen file |
|---|---|
| `Splash`, `Onboarding` | `ui/screens/SplashOnboarding.kt` |
| `Home(tab)` | `ui/screens/Home.kt` (top bar, bottom bar, dialogs host). Tab content: `PlayTab.kt` (level map), `MetaTabs.kt` (Shop, Kitchen, Stores, Style) |
| `Game(level)` | `ui/screens/GameScreen.kt` (GL view, HUD, pause, results) |
| `Settings`, `Legal(doc)`, `RecipeBook`, `Achievements` | `ui/screens/Settings.kt` |

Home dialogs are the `HomeDialog` sealed class, rendered by `HomeDialogs()` in `Dialogs.kt`: level info, debug unlock, login, spin, missions, income, rank, reward, store detail.

Back key behaviour:
- on sub-screens it pops the stack;
- on Home it shows the "Leaving?" dialog;
- in a level it toggles pause;
- on Splash/Onboarding it does nothing.

## 3. A shift (level) at runtime

### Objects
- **`LevelConfig`** (`game/LevelGen.kt`): an immutable description of a level, from `LevelGenerator.generate(id)`.
- **`World`** (`game/World.kt`): all mutable state for one shift (player, stations, tables, customers, cars, staff, pads, coin piles, troubles, power-ups, score). It has no rendering or Android code.
- **`GameSession`** (`render/GameSession.kt`): created by `GameScreen` on the UI thread. It owns the `World` and holds `@Volatile` fields both threads touch: `paused`, `joyX/joyY`, `finishRequested`, `debugAction`, and `hud`.
- **`GameRenderer`** (`render/GameRenderer.kt`): the GL thread's `Renderer` callback.

### Frame loop (`GameRenderer.onDrawFrame`, GL thread)
1. Convert the joystick (screen direction) into world input via `Camera.screenDirToWorld`.
2. Run `World.update(1/60 s)` with a fixed-timestep accumulator, at most 6 steps per frame.
3. After each step, turn `world.events` (a list of `GameEvent`) into particles, floating text and camera punch/shake/focus (`handle()`). Forward them to `session.onEvent` → `GameScreen.onGameEvent` (sounds, haptics, mission counters).
4. Update the camera: damped follow + look-ahead, cinematic focus on newly built things, shake.
5. `applyLighting`: day → golden hour → dusk across the shift. Night is forced for the Night Shift mutator and the Space/Rooftop stores. Then `weather()` (per-store particles).
6. `WorldView.draw()` queues everything into `engine/Renderer`, then `renderer.end()` draws it.
7. Publish an immutable `HudState` about every 1/15 s. `GameScreen` polls `session.hud` each frame and recomposes.
8. When `world.finished`, wait 1.2 s, then call `session.onFinished(result)`. That sets Compose state, and `GameScreen` shows `ResultsOverlay` and calls `GameRepo.completeLevel`.

### `World.update` order
`updateRush → updateBoosts → updatePlayer (movement + interact) → updateStations → spawnCustomers → updateCustomers → updateCars → updateStaff → updatePads → updatePiles → updateTroubles → updatePowerUps → updateCombo → checkEnd`

All interactions are **proximity-based** (`World.interact`):
- standing near a tray picks up;
- standing behind the counter drops food;
- standing at the cashier spot serves;
- standing on a pad pays for it;
- walking near a coin pile collects it.

There are no tap targets in-game.

### Determinism
- Every random choice uses `Rng` (SplitMix64) seeded from the level id (`Difficulty.hash`).
- Customer arrivals are pre-generated in `World.buildArrivals()`.
- The same level with the same `PlayerStats` and the same inputs gives the same result. The offline tuner and the validator depend on this.
- ⚠️ **Any change to `World`, `Bot`, `Layouts` or `LevelGen` rules changes simulated revenue, so the level targets must be re-tuned** (see the developer guide).

### Events
`Ev` (in `World.kt`) is the only channel from the simulation to presentation. To add feedback for something new:
1. emit a `GameEvent` in `World`;
2. handle it in `GameRenderer.handle()` (visuals);
3. handle it in `GameScreen.onGameEvent()` (audio, haptics, counters).

## 4. Levels and difficulty (`game/LevelGen.kt`)

| Piece | What it does |
|---|---|
| `Difficulty.of(n)` | D ∈ [0,1]. Fast learning ramp + long mastery climb + a gentle creep, plus a per-chapter sawtooth. Levels where n % 50 == 0 are breathers, and there's ±0.01 jitter. Exact formula: `MASTER_GAME_PLAN.md §5.3` |
| `Tutorial.level(1..10)` | Handcrafted levels; `overtime = true`, so they can't be failed |
| `LevelGenerator.procedural(id)` | From D and the seed it picks the mutators, layout (+mirror), menu, stations (some unbuilt → build pads), pads (upgrades/hires), spawn interval, rush waves, patience, troubles, VIP/critic rates and the drive-thru |
| `bossFor(id)` | Every 10th level rotates through Critic, Festival, Celebrity, Contest, Rally, Reopening. Level 10 is a gentle Critic |
| Feature unlocks | Items: `Item.unlockLevel`. Troubles: `troubleUnlock()`. Layouts: `Layouts.specs.minLevel`. Mutators from level 50 |
| Targets | `generate()` overrides the analytic target with the tuned triple from `targets.bin` when present |

**Tuning pipeline**:
- `LevelTunerTest` (opt-in) simulates each level with the expert `Bot` at `EconomyModel.expectedStats(level)`.
- Target = bot revenue × efficiency(D), where efficiency is 0.40 at D=0 and 0.80 at D=1.
- star2 = min(1.2·target, 0.92·bot); star3 = min(1.45·target, 0.98·bot).
- Results are written to `assets/levels/targets.bin` (int32 LE quadruples: `id, target, star2, star3`), with a human-readable sample in `docs/level_targets_sample.csv`.
- `AllLevelsValidatorTest` then re-simulates all 10,000 levels and fails if any target isn't beaten by ≥10 %, if 3★ is unreachable, or if a level uses a locked layout or item.

## 5. Layouts and navigation (`game/Layouts.kt`)
- 8 templates (Bun Stand → Burger Palace), unlocked by level, optionally mirrored.
- Interior coordinates: **x ∈ [0, w], z ∈ [0, d]**.
  - Back wall and stations at z ≈ 1.
  - Counter at z = 3.9, with the register at its +x end.
  - Dining area beyond the counter.
  - Front wall at z = d, with the door at `doorX`.
  - Sidewalk and road at z > d. The drive-thru lane runs along x > w.
- `NavGrid`:
  - 0.25 m cells with hard `blocked` cells for collision;
  - an `inflated` copy (one extra cell) for path planning, so characters ≈0.24 m wide always fit;
  - A* (octile heuristic) + string-pulling.
  - Customers, staff, the raccoon, the bot and the tutorial guide arrows all use it.

## 6. Rendering (`engine/`, `render/WorldView.kt`)

### Models (`.bvm`)
- The format is documented at the top of `tools/blender/bvlib.py`.
- A model is a list of **parts**, each with a pivot and an optional parent, plus named **anchors** (`@hand`, `@hat`, `@out`, `@rack0`…).
- Vertices are 20 bytes: position, a normal with the emission strength in the 4th byte, and an RGBA colour where AO is pre-baked and **alpha = tint mask**.
- `ModelCache` loads them from assets on the GL thread. `GameRenderer` preloads all of them at surface creation.

### `engine/Renderer` (instanced forward renderer)
- `begin(camera)` → any number of `draw(mesh, matrix, tint, alpha, flash, emit…)` and `sprite(...)` calls → `end()`.
- Draws are batched by mesh and drawn with `glDrawElementsInstanced`. One instance is 24 floats: the model matrix, a tint RGBA, and fx (flash, emission, darken).
- Passes:
  - shadow map (PCF, light frustum follows the camera);
  - opaque toon pass: two-band terminator, hemisphere ambient, rim light, emission, fog;
  - inverted-hull **outline** pass for batches drawn with `outline = true`;
  - transparent pass;
  - sprite pass (billboards and ground decals from one atlas, with radial-fill support for progress rings).
- `Quality`:
  - LOW: no shadows/outlines/MSAA;
  - MEDIUM: 1024² shadows + outlines + 4×MSAA;
  - HIGH: 2048² shadows.
  - `App.quality()` picks a tier from RAM; Settings can override it.

### Other engine pieces
- **`SpriteAtlas`**: one 1024² texture drawn with `Canvas` at start-up. It holds the glyphs (Lilita One with an outline), shapes, rings, arrows, bubbles and emotes. The 12 food icons in it are rendered from the 3D models by **`IconBaker`**, so order bubbles always match the models.
- **`WorldView`**: draws the ground/road mesh (built in code by `MeshBuilder`), floors, walls, decor, store signature props, stations (with output stacks and progress rings), counter racks, tables, coin piles, pads (dashed ring, fill, price, ghost preview), troubles, power-ups, customers (with order bubbles and patience bars), cars, staff, the player (stack + pet), and the guide arrows along the nav path.
- **Characters**: rigid-part, procedural animation in `WorldView.walkPose`/`drawCharacter`. Part names are `body, head, eyes, armL, armR, legL, legR, tail`. Blinking scales `eyes` on Y; hats attach at `@hat` on the head; carried stacks start at `@hand`; `hatLift` handles frog eyes.
- **Theme/store colours**: they reach models through the tint mask (wall trims, chair seats, shirts, car bodies).

### Coordinate systems
| Space | Convention |
|---|---|
| Engine/world | Metres. **+y up**, **+z = front** (towards the street and the camera). Yaw 0 faces +z |
| Blender (asset scripts) | Z-up; front = **−Y**. Exporter maps engine (x, y, z) = Blender (x, z, −y) |
| Camera | Yaw 0, pitch 52°, FOV 32°, distance 20 (22.5 on tall screens). Screen-up = world −z (towards the kitchen) |
| Joystick | Screen dx (right) / dy (down) → world via `Camera.screenDirToWorld` |

## 7. Progress and save (`data/`)
- `GameRepo.state` is a Compose `mutableStateOf<SaveData>`, so screens recompose automatically.
- Every mutation goes through `GameRepo.update { it.copy(...) }`. That writes JSON on a single IO thread: `save.tmp` → rename to `filesDir/save.json`, with the previous file copied to `save.bak.json`. `load()` falls back to the backup if the main file is corrupt.
- `SaveData` (`data/Progress.kt`) holds:
  - progress: `highestUnlocked`, `stars` (one digit character per level, `'0'..'3'`), `adUnlocked` (debug);
  - economy: Bucks, Gems, upgrades, stores + renovations + last-collect times, owned/selected cosmetics;
  - daily state: login, spin, missions, free chest;
  - collection: cards, sets, counters, achievements, chef XP;
  - settings.
- `fromJson` is tolerant: missing keys fall back to defaults. `version` is 1, and there are no migrations yet.
- **Unlock rules**: `GameRepo.isUnlocked(level) = level ≤ highestUnlocked || level in adUnlocked`. `adUnlock()` is a no-op unless `BuildConfig.DEBUG_AD_UNLOCK`.
- **Rewards**: `completeLevel()` computes Bucks (`LevelGenerator.reward × starMultiplier`, with a 25 % consolation on a fail), gems, new Recipe cards, chef XP and mission progress, and unlocks the next level on a win.
- **Store income**: `incomePerHour × (1 + 0.10·renovation) × (1 + 0.04·Store Income upgrade)`, capped at 8 h of accrual.

## 8. Economy and cosmetics (`game/Economy.kt`)
- **`Upgrade`**: 8 permanent tracks; cost = `base · 1.2^level`.
- **`StatsCalc.stats(upgrades, store, pet)`**: produces the `PlayerStats` the `World` uses (speed, capacity, cook speed, patience, tips, start cash, staff speed, magnet, drive-thru pay, VIP chance, combo gain). Store and pet perks are applied here.
- **`Stores.ALL`**: 12 stores with cost, perk, floor model, colours, signature props and sky colour.
- **`Cosmetics`**: `SKINS` (species + hat + apron tint), `PETS`, `THEMES` (floor/wall/trim/seat tints).
- **`EconomyModel`**: a "typical player" model. It spends 55 % of an average 2★ reward on the cheapest upgrade; the tuner uses it to know how strong a player should be at level N.

## 9. Audio (`audio/AudioEngine.kt`)
- **SFX**: every WAV in `assets/audio/sfx/` is loaded into `SoundPool` (14 streams). Always play through **`App.sfx(name, vol, rate)`**, which applies per-sound volume trims (`App.TRIM`, measured by the audio QC) and rate-limits repeats. Pickup pitch rises with stack height.
- **Music**: `MusicMixer` streams one or two stereo stems on its own thread through `AudioTrack`. `level_calm`/`level_rush` have identical length, and `setRush(true)` crossfades them sample-synchronously during rush waves. Boss levels use `boss_theme`; menus use `menu_theme`.
- **Haptics**: `App.haptic(0..3)` (tick, click, double, heavy). It respects the setting.

## 10. Ads seam (`ads/Ads.kt`)
- `AdsProvider { rewardedAvailable; isMock }`.
- Debug builds use `DebugMockAds`; the UI then shows `MockAdOverlay` (a 3 s placeholder) for:
  - unlocking a locked level;
  - ×2 results reward;
  - an extra spin;
  - Shop bonus Bucks.
- Release builds use `NoAds`, which hides every ad button.
- A real SDK should implement this interface and be selected in `App.onCreate`.

## 11. Build flags
| `BuildConfig` field | debug | release | Used for |
|---|---|---|---|
| `DEBUG_AD_UNLOCK` | true | false | Mock-ad unlock of locked levels, mock rewarded actions |
| `DEBUG_TOOLS` | true | false | FPS/draw-call overlay; pause-menu WIN ★★★ / +$500; Settings +10/+100 levels, +50K Bucks |

Release builds run whole-app R8 optimization. **Don't add `packageScope`** to `optimization {}`: the template's `packageScope` caused a start-up `IllegalAccessError` crash.
