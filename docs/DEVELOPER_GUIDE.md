# Developer Guide

Practical guide to continuing work on Bun Voyage!. Read [ARCHITECTURE.md](ARCHITECTURE.md) first for how the pieces fit.

- [Ground rules](#ground-rules)
- [Everyday workflow](#everyday-workflow)
- [Restaurant pacing workflow](#restaurant-pacing-workflow)
- [Recipes: adding content](#recipes-adding-content)
- [Debug tools](#debug-tools)
- [Testing on devices](#testing-on-devices)
- [Release checklist](#release-checklist)
- [Known issues & next steps](#known-issues--next-steps)

---

## Ground rules

1. **`game/` stays pure Kotlin.** No `android.*` imports there. That keeps it runnable on the JVM for `RestaurantFlowTest`.
2. **Determinism.** Inside `game/`, use `Rng` (seeded) for every random choice, never `kotlin.random` / `Math.random` / wall-clock time. `RestaurantFlowTest` replays restaurants and relies on identical results.
3. **Rule changes ⇒ re-check pacing.** If you change anything that affects revenue or the build plans, re-run `RestaurantFlowTest`. That covers player speed, cook times, prices, patience, spawn rates, pad costs or order, the bot's logic and layouts. See [Restaurant pacing workflow](#restaurant-pacing-workflow).
4. **Never reorder or remove pads in a plan.** Pad ids are plan positions and are what saves store. Append only.
4. **Assets are looked up by name.** Missing assets fail silently:
   - a missing model (`mdl("x")` returns null) is simply not drawn;
   - a missing UI render (`ArtImage("x")`) shows its vector fallback.
   - After adding content, grep for the names and look at the screen.
5. **Hot paths are allocation-light.** `World.update`, `WorldView.draw` and `Renderer` run 60×/s on low-end phones. Reuse arrays and avoid `listOf`/lambdas/boxing per frame where you can.
6. **Sound goes through `App.sfx(name)`; haptics through `App.haptic(kind)`.** They respect the settings and the volume trims.
7. **UI uses the kit** (`ui/kit/Kit.kt`): `GameButton`, `RoundButton`, `Panel`, `Ribbon`, `DialogCard`, `GameDialog`, `ProgressBar3D`, `CurrencyChip`, `ItemCard`, `OutlinedText`. Use the palette `BV.*` and the text styles `Type.*` (`ui/theme/Theme.kt`). Buttons already play a click sound and haptic.
8. **Originality.** Never import art, audio, text or names from other games or brands, including the reference game whose genre this follows. New assets must come from `tools/` scripts or be original/licensed with the licence recorded in `assets/licenses/`.
9. **Debug-only features** must be gated by `BuildConfig.DEBUG_AD_UNLOCK` or `BuildConfig.DEBUG_TOOLS`. Release builds must contain no mock-ad UI and no cheats.

---

## Everyday workflow

```bash
./gradlew assembleDebug installDebug          # build + install the debug build
./gradlew :app:testDebugUnitTest              # JVM tests (RestaurantFlowTest included, ~20 s)
./gradlew assembleRelease                     # check R8/minified build still starts (install it!)
```
- The **debug FPS overlay** sits at the bottom centre of the in-game HUD.
- **Logcat tags:** `GrillGL` (GL errors), `GameRenderer` (GL init failure), `IconBaker`, `GameRepo` (save errors), `Audio`/`Music`.
- **Fast iteration inside a restaurant:** pause → `BUILD ALL` buys every pad except NEW RESTAURANT; `+CASH` adds 500 × the restaurant's money scale. Settings → `FINISH SHOP` completes the current restaurant and opens the next.
- **Crafted saves on a device:** `run-as` only works on the debug build (the release build is not debuggable), so load test saves with the debug build installed. Mark today's calendar as claimed (`loginClaimedDay` = today's day number) or the daily popup will swallow your first tap on Home.
- **Testing on the target-like emulator:** see [Testing on devices](#testing-on-devices).

---

## Restaurant pacing workflow

Run this whenever gameplay numbers, rules or build plans change:

```bash
./gradlew :app:testDebugUnitTest --tests "*RestaurantFlowTest*" -i | grep -E "^    R[0-9]|novice"
```

**What it checks:**
- the expert `Bot` (with `Restaurants.expectedUpgrades(i)`) completes every restaurant inside its pacing window: restaurant 1 in 8–18 min, the others in 10–45 min;
- a 55 %-skill bot finishes restaurant 1;
- every pad is reachable, and consecutive pads (which can be visible together) never overlap;
- a saved `RestaurantProgress` restores the same stations, levels, tables, drive-thru, staff and visible pads;
- the simulation is deterministic.

The printed table shows minutes, pads, served, walkouts and revenue per restaurant. Compare it before and after a change.

**Knobs** (all in `game/Restaurant.kt`):
- `LAST_PAD`, `LAST_GROWTH`, `EXPAND_MUL`: the cost curve (and so the time per restaurant);
- `PRICE`: the money scale per restaurant;
- the plan builder in `build()`: what unlocks, and in what order;
- `World.arrivalInterval()`: customer flow vs. built capacity.

If a restaurant isn't completed, it's usually one of:
- an unreachable pad: check `World.padPos` and the nav blocking in `World.block*`;
- a pad whose prerequisite never comes true (`World.prerequisiteMet`);
- the bot lacking logic for a new mechanic (`Bot.decide`).

---

## Recipes: adding content

Paths are relative to `app/src/main/java/com/ngstudio/burgerready/` unless they start with `tools/` or `app/`.

### A new food item
1. `game/Types.kt` → add an `Item` entry: `id`, `price`, `station`, `cookTime`, `height` (stack spacing) and `label`. Keep `TRAY` last.
2. If it needs a new machine:
   - add a `StationType(model, label)`;
   - model `st_<x>` in `tools/blender/stations.py` (its `MODELS` table) with an `@out` anchor;
   - follow `docs/ASSET_CONTRACT.md §5`.
3. Model `food_<id>`:
   - add it in `tools/blender/food.py` (`MODELS` table, ≤700 tris, `@top` anchor);
   - run `tools/blender/build_all.sh` (or `Blender -b --factory-startup --python tools/blender/food.py -- food_<id>`).
4. `engine/SpriteAtlas.kt` → add the id to the icon `names` list so `IconBaker` makes `icon_<id>` for order bubbles. There are 12 slots of 112 px from y = 640; there's room for more rows.
5. UI card art: add the id to `FOODS` in `tools/blender/render_cards.py`, then render `card_food_<id>.png`. The Recipe Book card is created automatically from `Item.FOODS`.
6. Add it to `Restaurants.menuFor()` from the restaurant where it should appear; station weights are set in `Restaurants.build()`.
7. **Re-run `RestaurantFlowTest`.**

### A new restaurant (Store)
Restaurants are played in `Stores.ALL` order; append new ones at the end.
1. `game/Economy.kt` → `Stores.ALL`: add a `StoreDef` with:
   - id, name, perk text;
   - floor model, wall/trim/accent colours, ground key;
   - 3 prop model names, sky colour, music variant.
2. Apply the perk in `StatsCalc.stats()` (`when (storeId)`).
3. Model the 3 `store_<id>_<prop>` props in `tools/blender/stores.py` (≤1,500 tris each, contract §7).
4. Visual hooks:
   - `render/WorldView.kt`: `drawGround` (ground colour per ground key) and `treeModel` (outdoor tree swap);
   - `render/GameRenderer.kt`: `weather()` (ambient particles) and `applyLighting` (forced night?).
5. Render the card: add it to `STORE_SPECS` in `tools/blender/render_cards.py`, producing `assets/ui/store_<id>.png` (640×512).
6. The Recipe Book card `store_<id>` is automatic.
7. Extend `Restaurants.PRICE` for the new index, then re-run `RestaurantFlowTest`.

### A new chef skin / species / hat
- **Skin** (existing species and hat):
  - `Cosmetics.SKINS` in `game/Economy.kt` (species, hat, apron tint, price in Bucks *or* Gems);
  - render `skin_<id>.png` by adding a row to `SKINS` in `tools/blender/render_ui.py`.
- **New species:**
  - add it to `Species` (`game/Types.kt`);
  - add a builder to `tools/blender/characters.py` (`SPECIES`, `SPECIES_ORDER`);
  - export `char_<id>`. Part order, pivots and anchors must match contract §1 exactly; check with `python3 -I tools/blender/inspect_bvm.py char_<id>`;
  - render `card_guest_<id>.png`.
  - Customers pick species randomly, so new species appear automatically.
- **New hat:** `tools/blender/hats.py` → `hat_<name>` (pivot at the bottom centre; it fits a 0.36 m head ring). If a species' eyes sit high, extend `WorldView.hatLiftFor`.

### A new pet
1. Add it to `Cosmetics.PETS` (`model = "pet_<id>"`, ability text, price).
2. Implement the ability in `StatsCalc.stats()` (`when (petId)`).
3. Model it in `tools/blender/pets.py` (`PETS` + `BUILDERS`; parts `body` + `head`).
4. Render `pet_<id>.png`.
5. The pet follows the player automatically (`WorldView.drawPlayer`).

### A new interior theme
1. Add it to `Cosmetics.THEMES` (floor, wall, trim and seat tints).
2. Render `theme_<id>.png` via `THEMES` in `tools/blender/render_cards.py`.
3. The tints are applied by `WorldView.draw` through the tint mask.

### A new trouble
1. Add it to `TroubleKind` (`game/Types.kt`), plus the restaurant where it starts in `Restaurants.troublesFor()`.
2. `game/World.kt`:
   - spawn it in `spawnTrouble()`;
   - resolve it in `updateTroubles()`;
   - call `fixTrouble()` when done, which emits `Ev.TROUBLE_FIXED` and records it for cards.
3. **Teach the bot** (`Bot.decide`), or `RestaurantFlowTest` will fail as restaurants become uncompletable.
4. Draw it in `WorldView.drawTroubles` (alert badge + icon from the atlas).
5. Add a sound in `GameScreen.onGameEvent`.
6. The Recipe Book card `trouble_<name>` is automatic.

### A new periodic event (like rush hour or critic visits)
1. Schedule it in `World.updateEvents()` (use `rng`, never the clock).
2. Emit a `GameEvent`; add visuals in `GameRenderer.handle()` and sound in `GameScreen.onGameEvent()`.
3. Re-run `RestaurantFlowTest`.

### A new restaurant layout
1. Add a `Spec(id, name, w, d, stations, tables, drive)` to `Layouts.specs`. `build()` places everything parametrically.
2. Point restaurants at it in `Restaurants.layoutFor()`. Check it in-game with Settings → `FINISH SHOP`.
3. Re-run `RestaurantFlowTest` (it also checks pad reachability and overlap).

### A new permanent upgrade
1. Add it to `Upgrade` (`game/Economy.kt`) with base cost, max level and description.
2. Apply it in `StatsCalc.stats()`.
3. Give it an expected level per restaurant in `Restaurants.expectedUpgrades()`.
4. Render `upgrade_<id>.png` via `ICONS` in `render_cards.py`.
5. Re-run `RestaurantFlowTest`. It appears in the Kitchen tab and the in-game UPGRADES sheet automatically.

### A new screen
1. Add a `Route` in `ui/AppRoot.kt` and a branch in the `when`.
2. Write the composable in `ui/screens/`, using `ScreenFrame(title, onBack)` (in `Settings.kt`) for the standard back button + ribbon header.
3. Back-key handling is already generic for non-Home routes.

### A new sound effect or music cue
1. Add a line to `tools/audio/sfx_list.txt` (name | description).
2. Add a recipe in `tools/audio/sfx.py`. Music lives in `music.py`.
3. Regenerate: `Blender -b --factory-startup --python-exit-code 1 --python tools/audio/synth.py -- sfx --only <name>`.
4. Play it with `App.sfx("<name>")`.
5. If it's loud and sustained, add a trim in `App.TRIM`. The audio QC prints a suggested trim.

### Daily rewards, missions, achievements, cards
| What | Where |
|---|---|
| Login calendar | `LoginRewards.DAYS` |
| Spin wheel | `SpinWheel.SEGMENTS` + `WEIGHTS` |
| Achievement tracks | `Achievements.TRACKS`; values come from `GameRepo.achievementValue()` |
| Card catalogue | `Cards.ALL` |
| Mission pool | `GameRepo.makeMissions()`; progress in `progressMissions()` (keyed by `kind`) |
| Shift counters (cards earned) | Collected in `GameScreen` (`ShiftCounters`, the result `LaunchedEffect`) |

The first four live in `data/Progress.kt`.

### A story beat
The script lives in `game/Story.kt` (`Story.BEATS`); [STORY_SCRIPT.md](STORY_SCRIPT.md) explains the premise, cast and voices.
1. Add a `Beat` with a **new id** (ids are saved: never rename or delete one). Use the helpers `arrive(store, …)`, `cue(store, Cue.X, …)`, `done(store, …)`, or `Beat(...)` for special triggers.
2. A mid-build beat needs a `Cue` that can really happen in that restaurant (for example, no raccoons before restaurant 4, no drive-thru before restaurant 3). `StoryTest.everyMidBeatCanFire` checks this with the bot.
3. Lines: at most 72 characters and two sentences; at most 8 bubbles. Use `…` for a trailing-off pause. `StoryTest` enforces these limits.
4. A new milestone kind: add a `Cue` bit and set it in `Story.cues(w)`. Keep that function read-only and allocation-free; it runs 15×/s on the GL thread.
5. A new character: add a `Speaker`, add a bust to `CAST` in `tools/blender/render_ui.py`, render with `-- cast --only=<id>`, and give it a `card_guest_*` fallback.
6. Regenerate the per-restaurant section of `STORY_SCRIPT.md` from the code, so the doc and the game never disagree. The extraction script used in round 3 is a short regex over `Story.kt`.

### Gold-star challenges (late game)
- `game/GoldStars.kt`: `SECOND` (the restaurant-specific second goal) and `REVENUE` (one-visit revenue, in units of the restaurant's money scale). The first goal is always "serve 100 + 10 × index guests".
- Calibrate with `CALIBRATE=true ./gradlew :app:testDebugUnitTest --tests '*GoldCalibrationTest*' --rerun -i`. It prints what the expert bot does in 3/6/10 minutes in every fully built restaurant.
- `GoldStarsTest.goldStarsAreEarnable` must stay green: the bot earns all three stars within 8 simulated minutes everywhere.
- A new goal kind: add it to `GoalKind`, `GoldGoal.text()`, `GoldStars.value()`, and the visit counters (`VisitStats`, `SessionSnapshot`, `World.snapshot()`).

### A new World Tour region
1. Append a `RegionDef` to `game/TourRegions.kt`. **Never reorder or remove a region, never change an id**: restaurant ids `<region>_<01..20>` are saved.
   - It needs 20 unique names of at most 20 characters, 4 palettes, a prop pool (at least 3 models) and a tree.
   - It also needs a host `Speaker`, a perk family, a 3-item menu lead, its weather and night amounts.
2. Props:
   - add builders to `tools/blender/regions.py` (`build_<region>_<prop>`, ≤ 1,500 tris, ~1.6 m footprint);
   - export: `Blender -b --factory-startup --python tools/blender/regions.py -- region_<region>_* --noprev`.
3. Diorama: add the region to `REGION_SPECS` in `tools/blender/render_cards.py` (generated from `TourRegions.kt`), then render with `-- region_<region>`.
4. Story: add a `RegionBeats` to `game/TourStory.kt` (arrival, halfway, farewell; ≤ 72 characters, ≤ 2 sentences per line).
5. Run the tests:
   - `TourTest` and `ClassicGoldenTest`;
   - before a release, `FULL_TOUR=true ./gradlew :app:testDebugUnitTest --tests '*TourTest.tourPacing*' --rerun`.
6. Regenerate `docs/STORY_SCRIPT.md` §9 from `TourStory.kt`, and `docs/RESTAURANTS.md` with `PRINT_RESTAURANTS=true ./gradlew :app:testDebugUnitTest --tests '*RestaurantListPrint*' --rerun`.

### Tuning the World Tour
| Knob | Where | Effect |
|---|---|---|
| `SizeClass.lastPadSeconds` | `game/Tour.kt` | Build time per size class (the last pad costs this many seconds of the restaurant's earnings) |
| Guests (`patience`, `orderMean`, `orderMax`, `arrivalFloor`, troubles) | `SizeClass` | How hard each size is; the arrival floor caps the crowd |
| `BASE_PRICE`, ×1.55 per region, ×1.022 per step | `Tour.priceMul` | Money scale (numbers only; pacing comes from `earningPower`) |
| `IDLE_SHARE` | `Tour` | Offline income (`TourTest.economyBounds` keeps the median at 0.2–0.6 of the next restaurant per 8 h) |

Watch the summary `TourTest.tourPacing` prints (min / median / max minutes per size) and `PRINT_ECONOMY=true` for the offline ratio per restaurant.

### Changing the save format
`SaveData` → `GameRepo.toJson/fromJson`.
- Add new fields with defaults, read them with `opt*`, and old saves keep working.
- For breaking changes, bump `version` and migrate in `fromJson`.

---

## Debug tools
Debug builds only (`BuildConfig.DEBUG_TOOLS` / `DEBUG_AD_UNLOCK`):

| Where | Tool |
|---|---|
| Play tab chain | Tap the next locked restaurant → **Watch Ad** → 3 s mock ad → it opens (`GameRepo.adUnlockNext`) |
| Spin wheel / Shop | Mock-ad extra spin / bonus Bucks |
| Pause menu | **BUILD ALL** (every pad except NEW RESTAURANT), **+CASH** (500 × money scale), **FINISH** (everything, then NEW RESTAURANT as if bought: plays the completion beat and the celebration; in the last restaurant it triggers the finale), **+GOLD** (awards this finished restaurant's three gold stars; at 36 it plays the epilogue) |
| Settings → Debug tools | **FINISH SHOP** (complete current restaurant + open the next), **+50K $** (+200 gems) |
| HUD | FPS · draw-call counter |
| Settings → Reset progress | Wipes the save, keeping the settings (available in release too) |
| JVM | `DEBUG_TOUR=<store id>` with `--tests '*TourDebugTest*' --rerun` traces the bot through one tour restaurant every 30 s (cash, pads, stack, position, path to the register) |

**Testing late-game states on a device** without playing for hours: write a crafted `save.json` and copy it in with `run-as`. Force-stop the app first, or the autosave overwrites it. Back up the owner's save first and restore it afterwards (round 3 did this for the finale and epilogue):
```bash
adb shell am force-stop com.ngstudio.burgerready
adb exec-out run-as com.ngstudio.burgerready cat files/save.json > backup.json
adb push crafted.json /data/local/tmp/bv.json && adb shell chmod 644 /data/local/tmp/bv.json
adb shell run-as com.ngstudio.burgerready cp /data/local/tmp/bv.json files/save.json
```

---

## Testing on devices

### Emulator matching the target phone
```bash
~/Library/Android/sdk/emulator/emulator -avd BunVoyage_720p -no-boot-anim -no-snapshot -gpu swiftshader_indirect -port 5580
export ANDROID_SERIAL=emulator-5580
adb shell wm density 320            # match the Realme RMX5313
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Driving the joystick from adb** (for scripted play):
```bash
adb shell "input motionevent DOWN 360 1100; input motionevent MOVE 360 950; sleep 1.0; input motionevent UP 360 950"
```
Screen coordinates at 720×1600: Play button ≈ (360, 1338); in-game pause ≈ (658, 64); pause-menu BUILD ALL ≈ (226, 1100), +CASH ≈ (492, 1100); settings gear on Home ≈ (656, 66). Wait for the splash (~6 s after a cold start) before tapping.

**Pitfalls:**
- `-gpu host` crashed the emulator ("Failed to find ColorBuffer"), so use SwiftShader. SwiftShader FPS (~15) means nothing for real performance.
- After `adb install -r`, wait ~2 s before `am start`, or the launch can be swallowed. `am start -W` reports cold-start time.

### Real-device profiling (still to do on the Realme RMX5313: Unisoc UMS9230E / Mali-G57 MP1, ~6 GB RAM, 720×1600 @ 320 dpi, 60/90 Hz, Android 16)
```bash
adb shell dumpsys gfxinfo com.ngstudio.burgerready reset
# play 2–3 minutes in a big restaurant (Settings → FINISH SHOP a few times), then:
adb shell dumpsys gfxinfo com.ngstudio.burgerready           # janky frames, percentiles
adb shell dumpsys meminfo com.ngstudio.burgerready | head -30  # target < 350 MB PSS
```
Also watch the in-game FPS overlay (target a steady 60) and thermals over 30 min.

Performance levers, in order:
1. Settings → Graphics Low/Medium;
2. `Quality` thresholds in `App.quality()`;
3. shadow map size;
4. outlines on fewer batches;
5. fewer decor instances.

---

## Release checklist
1. **Signing:** create a release keystore (never commit it) and replace `signingConfig = signingConfigs.getByName("debug")` in `app/build.gradle.kts` with a proper `signingConfigs { release { … } }` that reads credentials from `~/.gradle/gradle.properties` or environment variables. Prefer an **AAB**: `./gradlew bundleRelease`.
2. **Version:** bump `versionCode` / `versionName` in `app/build.gradle.kts`.
3. **Legal:** in `app/src/main/assets/legal/terms.html` and `privacy.html`, replace "NG Studio" if needed and `support@ngstudio.example` (search for `TODO(owner)`). Host `privacy.html` publicly and put the URL in the Play listing.
4. **Play Console:**
   - Data safety: no data collected (as of v1).
   - Content rating: Everyone; target audience 13+.
   - Category: Simulation/Casual.
5. **Store assets:**
   - `tools/icon/play_store_icon_512.png`, `tools/icon/feature_graphic.png` (1024×500);
   - phone screenshots: capture them from the game, using debug tools to reach later stores.
6. **Smoke test the release build** on a real device:
   - fresh install → onboarding (no skip) → Bun Wagon → build the grill → Home → PLAY: the grill is still there;
   - a locked restaurant chip shakes and explains how to open it, with no ad UI;
   - Terms/Privacy open;
   - background/resume inside a restaurant (it pauses and saves);
   - back button everywhere.
7. **Ads/IAP**, if added before release:
   - implement `AdsProvider`;
   - add Google UMP consent;
   - add `com.google.android.gms.permission.AD_ID`;
   - update the privacy policy and Data safety;
   - keep rewarded ads opt-in only.

---

## Known issues & next steps

| # | Item | Notes |
|---|---|---|
| 1 | **Real-device performance** | Round 2 (2026-11-28): the Realme RMX5313 holds 60–61 fps on MEDIUM in fully built City Rooftop and Space Station Diner (was 48–49). Thermals over 30 min still unmeasured |
| 2 | Release signed with the debug key | See the release checklist |
| 3 | Legal pages contain placeholders | `TODO(owner)` in `assets/legal/*.html` |
| 4 | No real ads / IAP | `ads/Ads.kt` seam. Shop shows "Gem Packs & No-Ads: coming soon" |
| 6 | Each visit creates a new `GameSurfaceView` (new GL context) | Round 4: parsing (`ModelData`, preloaded at app start) and the atlas bitmap are cached per process, so GL init is 35–85 ms on the Realme (was ~250 ms), and a curtain hides the view until its first frame. The UI thread still drops a few frames during the route transition (AndroidView creation). Next step if needed: keep one GL surface/renderer for the app's lifetime |
| 7 | Strings are hard-coded English in Compose | Move them to `res/values/strings.xml` before localising |
| 8 | Accessibility | Images use `contentDescription = null`. Large font scales untested |
| 9 | `game/World.kt` is ~1,300 lines | Candidate split: customers, cars, staff, troubles into separate files (keep `update` order) |
| 10 | Per-frame allocations | Round 2 removed them from `WorldView` (cached names, colours, pad labels) and `World.guidanceTarget(out)`. `GameRenderer.publishHud` still builds a small `HudState` 15×/s by design |
| 11 | GL ↔ UI hand-off | The HUD and save snapshots cross threads via `@Volatile` immutable objects; UI purchases go through `GameSession.cashAdjust`. Keep it that way: never mutate `World` from the UI thread while the GL thread runs |
| 12 | Save `version = 2` | v1 level progress is dropped on load (wallet, cosmetics and opened stores are kept). Pad ids are plan positions: append to plans only |
| 13 | Audio judged by measurement only | Listen on a phone speaker; adjust `App.TRIM` |
| 14 | Planned Compose UI tests not written | e.g. onboarding has no skip, legal screens open, debug-only unlock dialog |
| 15 | Template leftovers | `ExampleUnitTest`, `ExampleInstrumentedTest` can be deleted |
| 16 | Model quirks | Frog + brimmed hats need `hatLift`. The food truck prop is 4.5 m along X, so it gets its own lawn spot. Seat anchors extend past table footprints. Details in `docs/ASSET_CONTRACT.md` |
| 17 | ~~Parrot pet ability not implemented~~ | Round 4: "Fixes troubles twice as fast" (`PlayerStats.fixSpeedMul`, ×2.5 in the Jungle). Default 1×, so pacing tests are unaffected |
| 18 | `StoreDef.music` is unused | Every store plays `level_calm`/`level_rush`. Either render per-store music variants (`tools/audio/music.py`) and pick them in `GameScreen`, or drop the field |
| 19 | Design items not yet built | Interior themes recolour furniture but don't swap models. No off-screen arrows pointing to troubles (HUD lists them instead). Remaining genre gaps (in-world HR/upgrade office, rewarded boosts, events, passes, leaderboards) are prioritised in `docs/GAP_ANALYSIS.md` §3. Story, late game, the in-game task tracker and cash stacks were done in round 3 |
| 21 | Story text is hard-coded English in `game/Story.kt` | Localising means moving `Line.text` to resources keyed by beat id + line index. The game module can't read Android resources, so pass a lookup in from the UI |
| 22 | Voyage Book list | Round 4: every `ScreenFrame` list fades under its ribbon; pages show a postcard illustration. Long transcripts still expand inline (fine for 52 beats) |
| 23 | Story beats pause only for scenes | Round 4: milestone cards moved to the top of the HUD and only a deliberate tap advances them. While a card shows, it covers part of the kitchen for a few seconds |
| 24 | World Tour art is per region | 25 dioramas (`region_<id>`) and 55 new props; the 20 restaurants of a region share the diorama on the hub. Per-restaurant hero art would be 500 images |
| 25 | World Tour menus reuse the 7 items | Regions change the menu lead and weights, not the items. New dishes need models, icons, `Item` entries (append) and a separate card set |
| 26 | One music pair for every restaurant | `level_calm`/`level_rush` everywhere; regions could get their own stems (`tools/audio/music.py`) |
| 27 | Tour pacing relies on the expected upgrades being maxed | `Restaurants.expectedUpgrades` caps every track at its max level; a player who skipped upgrades builds tour restaurants more slowly (no gate) |
| 28 | Gold stars are classic-only | Tour restaurants have no stars; a per-flagship star (25) is a natural next step |
| 24 | Guests and the chef | Round 4: guests step around the chef (`World.yieldToPlayer`) and wait instead of walking into someone ahead of them in the queue (`World.queueBlocked`). Guests still overlap each other briefly on the sidewalk before they join the line |
| 20 | Carried stack is not saved | Leaving a restaurant drops what the chef carries and what sits on the counter racks (transient by design) |
