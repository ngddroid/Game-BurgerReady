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
| `game/` | The whole simulation: rules, restaurants and build plans, layouts, navigation, bots, economy | **None.** Keep it that way: it's unit-tested on the JVM by `RestaurantFlowTest` |
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
   - picks the `AdsProvider` (`DebugMockAds` if `BuildConfig.DEBUG_AD_UNLOCK`, else `NoAds`).
2. `MainActivity` installs the Android 12+ splash (`Theme.BunVoyage.Splash` → `drawable-nodpi/splash_icon.png`), hides the system bars and sets the Compose content `AppRoot`.
3. `AppRoot` starts on `Route.Splash`. It goes to `Onboarding` the first time, otherwise to `Home(tab = 2)`. Finishing onboarding goes straight into `Game(selectedStore)` (the Bun Wagon).

## 2. Navigation (`ui/AppRoot.kt`)
A tiny custom navigator: `Navigator.stack` is a `mutableStateListOf<Route>`, and an `AnimatedContent` shows `stack.last()`.

| Route | Screen file |
|---|---|
| `Splash`, `Onboarding` | `ui/screens/SplashOnboarding.kt` |
| `Home(tab)` | `ui/screens/Home.kt` (top bar, bottom bar, dialogs host). Tab content: `PlayTab.kt` (restaurant hub: hero art, build progress, the 12-restaurant chain, PLAY), `MetaTabs.kt` (Shop, Kitchen, World = my restaurants, Style) |
| `Game(store)` | `ui/screens/GameScreen.kt` (GL view, HUD, in-game upgrades sheet, pause, "restaurant complete" overlay) |
| `Settings`, `Legal(doc)`, `RecipeBook`, `Achievements` | `ui/screens/Settings.kt` |
| `VoyageBook` | `ui/screens/StoryUi.kt` (Hazel's twelve pages: stamps, gold stars, the cast, story transcripts) |

Home dialogs are the `HomeDialog` sealed class, rendered by `HomeDialogs()` in `Dialogs.kt`: debug unlock-next, login, spin, missions, income, welcome back, rank, reward, restaurant detail.

**Popups Home opens by itself** (`AutoPopups` in `Home.kt`): at most one per visit to Home. The prologue cut-scene (older saves) always goes first. Then idle income (at most every 10 min, only when worth ≥ 5 min of income) and the daily calendar (once per launch); when both are due they share one **WELCOME BACK** card with a single COLLECT ALL button (round 4; round 3 showed prologue → income → daily back to back). Closing a popup loses nothing: income waits on the INCOME pill, the calendar on the Daily button.

Back key behaviour:
- on sub-screens it pops the stack;
- on Home it shows the "Leaving?" dialog;
- in a restaurant it toggles pause (and saves);
- on Splash/Onboarding it does nothing.

## 3. A restaurant at runtime

### Objects
- **`RestaurantConfig`** (`game/Restaurant.kt`): an immutable description of one restaurant, from `Restaurants.config(index | storeId)`. It holds the layout, the menu, the stations, and the ordered **build plan** (`pads`).
- **`RestaurantProgress`**: what the save keeps per restaurant: completed pad ids, plus partial payments.
- **`World`** (`game/World.kt`): all mutable state for one visit. That covers the player, stations, tables, customers, cars, staff, pads, coin piles, troubles, power-ups and the wallet (`cash`). It is built from a config, the saved progress (restored silently, so bought things are simply *there*) and the wallet. It has no timer and no fail state; `expanded` turns true when the EXPAND pad is bought.
- **`GameSession`** (`render/GameSession.kt`): created by `GameScreen` on the UI thread. It owns the `World` and holds the `@Volatile` fields both threads touch:
  - `paused`, `joyX/joyY`, `debugAction`;
  - `hud` (published about 15 Hz) and `snapshot` (published about 1 Hz and on every pad change);
  - `cashAdjust` (UI purchases) and `newStats` (in-game upgrades).
- **`GameRenderer`** (`render/GameRenderer.kt`): the GL thread's `Renderer` callback.

### Frame loop (`GameRenderer.onDrawFrame`, GL thread)
1. Apply `cashAdjust` / `newStats` from the UI, then convert the joystick (screen direction) into world input via `Camera.screenDirToWorld`.
2. Run `World.update(1/60 s)` with a fixed-timestep accumulator, at most 6 steps per frame.
3. After each step, turn `world.events` into particles, floating text and camera punch/shake/focus (`handle()`). Forward them to `session.onEvent` → `GameScreen.onGameEvent` (sounds, haptics).
4. Update the camera: damped follow + look-ahead, kept inside the restaurant (`frameRestaurant` → `Camera.setBounds`) without letting the chef leave the safe screen area, cinematic focus on newly revealed pads, shake. Wider layouts pull the camera back up to 14 %.
5. `applyLighting`: a slow day cycle with a golden hour. Night is forced only for night-themed stores (Space, Rooftop/Tokyo evening); there the restaurant's floor rectangle (`Lighting.interior`) is lit by warm lamp light while the outside stays night-blue. Then `weather()`.
6. `WorldView.draw()` queues everything into `engine/Renderer`, then `renderer.end()` draws it.
7. Publish `HudState` about every 1/15 s (including the story milestone mask `cues` from `Story.cues(world)` and Hazel's tutorial tip from `Story.tutorialTip`), and a `SessionSnapshot` about every second or on any pad change.

### Saving (`GameScreen`)
- **Every 3 s:**
  - the latest snapshot is diffed against the last committed one;
  - `GameRepo.commitRestaurant()` stores the restaurant progress and the wallet;
  - the counters (served, revenue, items…) go to missions, achievements, cards and chef XP.
- **Also saves:** on pause, on `ON_PAUSE` (app backgrounded) and on exit.
- **Exit save:** this one runs after `GLSurfaceView.onPause()`, so it reads the `World` directly, including any purchase still queued in `cashAdjust`.
- **EXPAND:** when the pad is bought, `GameRepo.openNextRestaurant()` unlocks and selects the next store, and the overlay offers "LET'S GO!" (`nav.replace(Game(next))`).

### `World.update` order
`updateEvents (rush/critic schedule) → updateBoosts → updatePlayer (movement + interact) → updateStations → spawnCustomers → updateCustomers → updateCars → updateStaff → updatePads → updatePiles → updateTroubles → updatePowerUps → updateCombo`

**Spawning:** customers arrive as fast as the *built* kitchen can feed them: `orderMean / (0.72 × Σ station rate)`, clamped to a floor that shrinks in bigger restaurants. Nothing spawns until the first station exists.

**Interactions** are all proximity-based (`World.interact`):
- standing near a tray picks up;
- standing behind the counter drops food;
- standing at the cashier spot serves;
- standing on a pad pays for it, draining it in about 1.3 s;
- walking near a coin pile collects it.

There are no tap targets in-game.

### Determinism
- Every random choice uses `Rng` (SplitMix64) seeded from the restaurant (`RestaurantConfig.seed`).
- The same restaurant with the same progress, stats and inputs gives the same result; `RestaurantFlowTest` relies on this.
- ⚠️ **Any change to `World`, `Bot`, `Layouts` or `Restaurant` changes pacing.** Re-run `RestaurantFlowTest` (see the developer guide).

### Events
`Ev` (in `World.kt`) is the only channel from the simulation to presentation. To add feedback for something new:
1. emit a `GameEvent` in `World`;
2. handle it in `GameRenderer.handle()` (visuals);
3. handle it in `GameScreen.onGameEvent()` (audio, haptics).

### Story beats and gold stars during a visit (`GameScreen`)
See §12 for the data. At runtime:
- **Story director** (a `LaunchedEffect` keyed on `hud.cues`, `hud.built` and the overlay states): when no beat, menu or celebration is showing and the cool-down (9 s after the last beat; 2.6 s after entering) has passed, it asks `Story.next(index, cues, seen, completeOnEntry)` for a beat. `completeOnEntry` = the restaurant was already finished when the visit began; then only the gold-stars intro (and, in the last restaurant, the finale) can fire.
- **Milestone beats** show as a compact `DialogueCard` in the HUD's top column (under the top row and trackers, round 4), while the restaurant keeps running. The bottom half of the screen is where players steer, so the card stays out of it, and only a deliberate tap advances it (a press that drags or is held > 450 ms is ignored). **Completion** (after `openNextRestaurant`), **finale**, **post-game** and **epilogue** beats play as a `StoryScene` with the session paused. The completion beat plays before the RESTAURANT COMPLETE card; the finale chains into the Golden Tour beat and the VOYAGE COMPLETE card.
- Each finished beat is saved with `GameRepo.markStorySeen(id)`.
- **Gold stars** (checked once a second): the visit's counters since the restaurant was finished (`SessionSnapshot.visitSince(base)`) go through `GoldStars.met`, and `GameRepo.awardGold` saves new stars (gems + idle-income bonus). The base is the visit start when `completeOnEntry`, or the moment the last restaurant is fully built. During the visit that finishes an earlier restaurant nothing is awarded.
- The same tick refreshes the HUD **trackers**: the next gold star here and today's first open daily task.

## 4. Restaurants and the build plan (`game/Restaurant.kt`)

| Piece | What it does |
|---|---|
| `Restaurants.CLASSIC` / `TOTAL` | 12 classic restaurants (`Stores.ALL`, Bun Wagon → Candy Kingdom), then the 500-restaurant World Tour (§13): 512 in all. Indices 0–11 classic, 12–511 tour |
| `layoutFor(i)` | Classic: layout `min(i + 1, 8)` (templates 9–16 are the tour's); bigger restaurants have more station slots and tables, and a drive-thru from restaurant 3 |
| `menuFor(i)` | Items introduced over the chain: burger + fries, then soda, cheese, double, shake, veggie |
| `priceMul(i)` | The restaurant's money scale (×1 … ×420). Menu prices and pad costs are multiplied by it, so each new restaurant is a step up |
| Build plan | The first station, then its first table, a second station and table, an upgrade and a cashier. After that come interleaved stations, tables and level-2 upgrades, with staff and the drive-thru at fixed fractions, then level-3 upgrades, then **EXPAND**. Stations fill the slots nearest the counter first |
| Costs | Geometric from $10 to `LAST_PAD × (1 + LAST_GROWTH × i)`, weighted per kind, times `priceMul`. EXPAND costs `EXPAND_MUL ×` the last pad. From restaurant 2 the first grill costs 0 and is built on arrival |
| Events | Rush hours (every 150–220 s), critic visits (from restaurant 2), troubles unlocked by restaurant index, power-ups after 4 pads |
| Visibility | Two pads at a time, in plan order. An upgrade pad needs its station; the drive-thru cashier needs the drive-thru; EXPAND needs everything else |
| Idle income | `incomePerHour(i, builtFraction)` = `900 × priceMul × (0.15 + 0.85 × built)`, then +10 % per renovation star and +4 % per Offline Income upgrade, capped at 8 h |

**Validation** (`app/src/test/.../RestaurantFlowTest.kt`):
- **Pacing:** the expert bot completes every restaurant inside its pacing window (restaurant 1: 8–18 min; others 10–45 min; it currently takes ≈12.5 min and 15–33 min).
- **Novice:** a 55 %-skill bot finishes restaurant 1.
- **Pads:** every pad is reachable, and consecutive pads (which can show together) never overlap.
- **Saves:** progress restores exactly.
- **Determinism:** the simulation gives identical results on a rerun.

**Pad ids are plan positions and are what the save stores.** Only append to a plan; never reorder or remove steps.

## 5. Layouts and navigation (`game/Layouts.kt`)
- 8 templates (Bun Stand → Burger Palace); restaurant N uses template min(N + 1, 8).
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
  - shadow map (one hardware-PCF tap, light frustum follows the camera; only character bodies/heads/legs and props cast);
  - opaque toon pass, drawn front-to-back for early-Z, at mediump (positions highp): crisp cel terminator between a lit colour (`sunColor`) and a **coloured** shade (`skyColor`, a cool slate, never grey), a small saturation lift (more in shade), rim light, emission, a highlight shoulder so bright paint never clips to white, an optional lamp-lit interior rectangle, and distant fog only;
  - inverted-hull **outline** pass for batches drawn with `outline = true`;
  - transparent pass;
  - sprite pass (billboards and ground decals from one atlas, with radial-fill support for progress rings).
- `Quality`:
  - LOW: no shadows/outlines/MSAA;
  - MEDIUM: 1024² shadows + outlines + 4×MSAA;
  - HIGH: 2048² shadows.
  - `App.quality()` picks a tier from RAM; Settings can override it.

### Other engine pieces
- **Restaurant entry** (round 4): every visit still gets a new `GameSurfaceView` and GL context, but the expensive CPU work is now done once per process: `ModelData` parses all `.bvm` files on a background thread at app start (`App.onCreate`), so `ModelCache` only uploads buffers, and the drawn `SpriteAtlas` bitmap is kept and re-uploaded. GL init went from ~250 ms to 35–85 ms on the Realme. A cream curtain covers the view until the renderer reports its first frame (`GameRenderer.onFirstFrame` → `GameSurfaceView.firstFrame`), then fades out in 280 ms. `GameRenderer` logs the init timing (`GL init … ms`).
- **`SpriteAtlas`**: one 1024² texture drawn with `Canvas` once per process (cached, see above). It holds the glyphs (Lilita One with an outline), shapes, rings, arrows, bubbles and emotes. The 12 food icons in it are rendered from the 3D models by **`IconBaker`**, so order bubbles always match the models.
- **`WorldView`**: draws the ground/road mesh (built in code by `MeshBuilder`), floors, walls, decor, store signature props, stations (with output stacks and progress rings), counter racks, tables, coin piles, pads (dashed ring, fill, price, ghost preview), troubles, power-ups, customers (with order bubbles and patience bars), cars, staff, the player (stack + pet), and the guide arrows along the nav path. Money waiting at a register is drawn as stacks of banded cash bricks (`food_cash`, 2×2 footprint, up to 8 high); the register pile is drawn 0.35 m behind its collection point so the counter racks and cashier no longer hide it (render-only: the simulation spot is unchanged). Night stores also get warm light pools under every window and along the shop front, and halos on the street and floor lamps.
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
  - progress: `restaurants` (store id → `RestaurantProgress`: done pad ids + partial payments), `stores` (opened restaurants → renovation stars), `selectedStore` (the restaurant being played);
  - story and late game: `story` (ids of the story beats already shown), `gold` (store id → gold-star bitmask, bits 0–2);
  - economy: **one wallet** (Bucks, also the in-world cash), Gems, upgrades, last idle-income collect times, owned/selected cosmetics;
  - daily state: login, spin, missions, free chest;
  - collection: cards, sets, counters, achievements, chef XP;
  - settings.
- `fromJson` is tolerant: missing keys fall back to defaults. `version` is 3: a fully bought restaurant is written as `{"all":1}` (hundreds of them with the World Tour) and expanded on load; unknown ids are skipped. Money is `Long` everywhere (pad costs, partial payments, piles, missions). Version-2 saves load unchanged. A v1 (level-based) save keeps its wallet, cosmetics and opened stores; level progress is dropped.
- **Unlock rules:** a restaurant is open when it's in `stores`. Only the Bun Wagon starts open; buying EXPAND opens the next (`openNextRestaurant`, +15 gems, store card). `adUnlockNext()` is a no-op unless `BuildConfig.DEBUG_AD_UNLOCK`.
- **Commits:** `commitRestaurant(store, progress, cash, delta)` writes progress and the wallet, and adds the counters (served, revenue, built, rushes, critics, troubles, trays, VIPs) to missions, achievements, cards and chef XP.
- **Idle income:** see §4, × (1 + 0.05 per gold star of that restaurant). Chests, missions and shop bundles scale with `Restaurants.rewardUnit(frontier)`; daily-calendar and spin Bucks with `GameRepo.scaledBucks` (= table value × `priceMul(frontier)`, rounded like pad costs). The calendar used to pay a flat 200–1,200 Bucks in every restaurant.
- **Gold stars:** `awardGold(store, metMask)` only counts finished restaurants (`SaveData.isComplete`); +10 gems per star, +300 more for the 36th.

## 8. Economy and cosmetics (`game/Economy.kt`)
- **`Upgrade`**: 8 permanent tracks; cost = `base · 1.2^level`.
- **`StatsCalc.stats(upgrades, store, pet)`**: produces the `PlayerStats` the `World` uses (speed, capacity, cook speed, patience, tips, menu prices, staff speed, magnet, drive-thru pay, VIP chance, combo gain). Store and pet perks are applied here.
- **`Cosmetics`**: `SKINS` (species + hat + apron tint), `PETS`, `THEMES` (floor/wall/trim/seat tints).
- **`Restaurants.expectedUpgrades(i)`**: the Kitchen upgrade levels a typical player has when starting restaurant i (two levels per track per restaurant); `RestaurantFlowTest` simulates with them.
- **`Stores.ALL`** is now the restaurant chain: name, perk, floor, colours, signature props and sky. Restaurants are opened by progression, not bought.

## 9. Audio (`audio/AudioEngine.kt`)
- **SFX**: every WAV in `assets/audio/sfx/` is loaded into `SoundPool` (14 streams). Always play through **`App.sfx(name, vol, rate)`**, which applies per-sound volume trims (`App.TRIM`, measured by the audio QC) and rate-limits repeats. Pickup pitch rises with stack height.
- **Music**: `MusicMixer` streams one or two stereo stems on its own thread through `AudioTrack`. `level_calm`/`level_rush` have identical length, and `setRush(true)` crossfades them sample-synchronously during rush waves. Restaurants use `level_calm`/`level_rush`; menus use `menu_theme` (`boss_theme` is currently unused).
- **Haptics**: `App.haptic(0..3)` (tick, click, double, heavy). It respects the setting.

## 10. Ads seam (`ads/Ads.kt`)
- `AdsProvider { rewardedAvailable; isMock }`.
- Debug builds use `DebugMockAds`; the UI then shows `MockAdOverlay` (a 3 s placeholder) for:
  - opening the next restaurant early;
  - an extra spin;
  - Shop bonus Bucks.
- Release builds use `NoAds`, which hides every ad button.
- A real SDK should implement this interface and be selected in `App.onCreate`.

## 11. Build flags
| `BuildConfig` field | debug | release | Used for |
|---|---|---|---|
| `DEBUG_AD_UNLOCK` | true | false | Mock-ad unlock of the next restaurant, mock rewarded actions |
| `DEBUG_TOOLS` | true | false | FPS/draw-call overlay; pause-menu BUILD ALL / +CASH / FINISH / +GOLD; Settings FINISH SHOP, +50K Bucks |

Release builds run whole-app R8 optimization. **Don't add `packageScope`** to `optimization {}`: the template's `packageScope` caused a start-up `IllegalAccessError` crash.

## 12. Story and the Golden Tour (`game/Story.kt`, `game/GoldStars.kt`, `ui/screens/StoryUi.kt`)
The script and the reasons behind it are in [STORY_SCRIPT.md](STORY_SCRIPT.md); the market case is in [GAP_ANALYSIS.md](GAP_ANALYSIS.md).

| Piece | What it does |
|---|---|
| `Speaker` | The six-character cast: display name, role, portrait asset (`cast_*`, or `skin_patty`), fallback (`card_guest_<species>`), name-tag colour |
| `Beat` / `Line` | One beat = id (saved, never rename), restaurant index (−1 = not tied to one), `Trigger`, title, lines, optional `Cue` |
| `Trigger` | `ARRIVE`, `CUE` (milestone), `COMPLETE` (EXPAND bought), `FINALE` (last restaurant fully built), `POSTGAME`, `PROLOGUE` (Home, older saves), `GOLD_INTRO` (first visit to a finished restaurant), `EPILOGUE` (36 gold stars) |
| `Cue` | Milestone bits read from a running `World` by `Story.cues(w)`: first table, any hire, drive-thru, rush, critic or VIP in the queue, raccoon or grease fire active, combo ≥ ×2, half built, fully built. Read-only and allocation-free; it never changes the simulation, so pacing and determinism are untouched |
| `Story.next` | Picks the beat to show: arrival first; the finale outranks pending milestone beats; milestone beats in any order once their cue appears; nothing for a restaurant that was already finished on entry |
| `Story.tutorialTip` | The Bun Wagon tutorial tips, in Hazel's voice (replaces the old `TutorialTips`) |
| `GoldStars` | 3 challenges per restaurant (serve N, a restaurant-specific one, one-visit revenue), `met(index, VisitStats)` → bitmask. Targets were calibrated on the expert bot (`GoldCalibrationTest`, manual) |
| `DialogueCard`, `StoryScene` | Speech cards: portrait (Patty on the right), name tag, typewriter text, deliberate tap to finish/continue, auto-advance (≈2.3–4.5 s per bubble). `compact` = the in-game size. Scenes put the title on a plate and take Back as "wait" (it no longer closes the app mid-scene) |
| `VoyageBookScreen` | Play tab → **Voyage**. Shows a beat if it was seen, or if its restaurant is finished (so saves from before the story can read the old pages). An opened page shows a **postcard** (store art, stamp with the page number, a postmark once stamped). The Voyage button shows a badge when a page was stamped since the book was last opened (`SaveData.voyageSeen`) |

**Tests:**
- `StoryTest`: catalogue structure, line length (≤ 72 characters, ≤ 2 sentences), trigger order, cue mask, and every mid-beat cue being reachable by the bot.
- `GoldStarsTest`: three goals each; the expert bot earns all 36 stars within 8 minutes per restaurant (3.4–5.3 min today).
- `SaveDataJsonTest`: `story`/`gold` round trip; a pre-round-3 save still loads.

## 13. The World Tour (`game/Tour.kt`, `game/TourRegions.kt`, `game/TourStory.kt`, `ui/screens/WorldTourUi.kt`)
500 restaurants in 25 regions × 20, appended after the classic 12 ([WORLD_TOUR_DESIGN.md](WORLD_TOUR_DESIGN.md)). **The classic restaurants never change**: `ClassicGoldenTest` fingerprints their configs, layouts 1–8, store data, stats and a 4-minute simulation of each.

| Piece | What it does |
|---|---|
| `TourRegions.ALL` | 25 `RegionDef`s: id, name, blurb, ground/sky/floor colours, 4 palettes, night amounts, weather, tree, prop pool, host, perk family, menu lead, 20 names. **Append only** (ids `<region>_<01..20>` are saved) |
| `Tour.size(step)` | The 20-step rhythm `S M S M L S M L M L S M L M XL M L M L F` → `SizeClass` (layout pool, guests, arrival floor, pacing window, `lastPadSeconds`) |
| `Tour.store(g)` | The generated `StoreDef`: palette, time of day (1 in 5 at the region's other time), 3 props rotated from the pool, weather, perk strength rising to the flagship |
| `Tour.build(g)` | The `RestaurantConfig`: same plan builder as the classic game (`Restaurants.planSteps`), all seven items with the region's lead first, guests from the size class. **Costs are priced from the restaurant's own earning power** (`earningPower`: menu value, station throughput, arrival floor, drive-thru, the player's expected upgrades), so a cheap-menu pop-up and a double-stack hall pace alike; the last pad costs `lastPadSeconds` of it. Money scale `3,400 × 1.55^region × 1.022^step` |
| Idle income | Tour restaurants earn `earnPerSec × 3600 × IDLE_SHARE (0.0003) × money scale` per hour: 8 h offline buys about a third of the next restaurant (median 0.29) |
| Layouts 9–16 | Pop-up Kiosk, Corner Café, Long Counter, Garden Terrace, Drive Hub, Market Hall, Grand Hall, Flagship (appended specs; shallow layouts skip the corner plant pots) |
| Unlocking | The classic finale's VOYAGE COMPLETE card has START THE WORLD TOUR (`GameRepo.openWorldTour`); saves already past the finale get it on load. Each restaurant's NEW RESTAURANT pad opens the next; a flagship's opens the next region (+40 gems, a `region_<id>` card) |
| Story | `TourStory`: the opening and each region's arrival (cut-scenes), a halfway card at stop 10, a farewell at the flagship, and the tour finale (`Story.tourBeats`) |
| UI | Play hub chain by chapter (‹ › and MAP); World Map (`WorldMapScreen`); World tab rows per region; idle-income dialog per region; Voyage Book part two (region pages); region dioramas `assets/ui/region_<id>.png` (`RegionBadge` is the drawn fallback) |
| Gold stars | Classic only (the 36-star Golden Tour is unchanged) |

**Engine changes that came with it** (classic behaviour identical, proven by the fingerprint):
- `StoreDef` carries the look as data (`night`, `weather`, `tree`, `floorTint`, `groundColor`, perk) instead of renderer switches on the classic ids.
- `RestaurantConfig.arrivalFloor` and `earnPerSec`.
- 11 new weather kinds in `GameRenderer.weather`.
- Bot:
  - with a carry capacity of 18+ it builds even while carrying (tour players have maxed upgrades);
  - it tries nearby approach points when a goal is unreachable.
- World and nav:
  - `World.ejectPlayer` never drops the chef into a dead end;
  - `NavGrid` only snaps a blocked start to a cell it can reach in a straight line.

**Tests:** `TourTest`.
- Catalogue and ids; every plan sound; layouts 9–16 reachable.
- Pacing on 125 restaurants (every region at stops 1, 2, 5, 15, 20). `FULL_TOUR=true` runs all 500 (~100 expert hours of simulated play).
- Economy bounds; story structure; progress restore; every model a region names exists.
- `TourDebugTest`, manual: `DEBUG_TOUR=<id>` traces the bot through one restaurant.
