# Developer Guide

Practical guide to continuing work on Bun Voyage!. Read [ARCHITECTURE.md](ARCHITECTURE.md) first for how the pieces fit.

- [Ground rules](#ground-rules)
- [Everyday workflow](#everyday-workflow)
- [Level tuning workflow](#level-tuning-workflow)
- [Recipes: adding content](#recipes-adding-content)
- [Debug tools](#debug-tools)
- [Testing on devices](#testing-on-devices)
- [Release checklist](#release-checklist)
- [Known issues & next steps](#known-issues--next-steps)

---

## Ground rules

1. **`game/` stays pure Kotlin.** No `android.*` imports there. That keeps it runnable on the JVM for the tests and the tuner.
2. **Determinism.** Inside `game/`, use `Rng` (seeded) for every random choice, never `kotlin.random` / `Math.random` / wall-clock time. The tuner and validator replay levels and rely on identical results.
3. **Rule changes ⇒ re-tune.** If you change anything that affects revenue, re-run the tuner and then the validator: player speed, cook times, prices, patience, spawn rates, pad costs, the bot's logic, layouts, or what `LevelGenerator` generates. See [Level tuning workflow](#level-tuning-workflow).
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
./gradlew :app:testDebugUnitTest              # JVM tests (validator included, ~15 s)
./gradlew assembleRelease                     # check R8/minified build still starts (install it!)
```
- The **debug FPS overlay** sits at the bottom centre of the in-game HUD.
- **Logcat tags:** `GrillGL` (GL errors), `GameRenderer` (GL init failure), `IconBaker`, `GameRepo` (save errors), `Audio`/`Music`.
- **Fast iteration inside a level:** pause → `WIN ★★★` finishes it instantly with 3 stars. Settings → `+10 LVL` unlocks levels quickly.
- **Testing on the target-like emulator:** see [Testing on devices](#testing-on-devices).

---

## Level tuning workflow

Run this whenever gameplay numbers or rules change.

```bash
TUNE=true ./gradlew :app:testDebugUnitTest --tests "*LevelTunerTest*"     # ~30 s, writes assets/levels/targets.bin
./gradlew :app:testDebugUnitTest --tests "*AllLevelsValidatorTest*"        # ~15 s, must report 0 failures
```

**What the tuner does:**
- It simulates every level with the expert `Bot` at the player strength `EconomyModel` expects for that level.
- It sets target = bot × (0.40 → 0.80 by difficulty), with stars 2 and 3 above that.
- It also refreshes `docs/level_targets_sample.csv`. Diff that CSV to see how a change moved the curve.

**What the validator checks:**
- every level 11–10,000 has a tuned target;
- the bot beats each target by ≥ 10 % and reaches 3★;
- levels 1–10 are won by a 50 %-skill bot;
- the difficulty curve has the planned shape;
- no level uses a locked layout or item.

**Changing difficulty itself:**
- The curve: `Difficulty.of()` (`game/LevelGen.kt`).
- What D controls: `LevelGenerator.procedural()`.
- How hard targets are: `LevelTunerTest.efficiency()`.
- Re-tune after any of these.

**Changing tutorials:** `Tutorial.level(1..10)` is handcrafted. These targets are not tuned, but the novice-bot test must still pass.

If the validator reports a level the bot can't beat, it's usually one of:
- an order for an item with no station (`World.availableOrder` should prevent this);
- an unreachable pad or station: check its nav-grid blocking in `World.block*`;
- the bot lacks logic for a new mechanic (`Bot.decide`).

---

## Recipes: adding content

Paths are relative to `app/src/main/java/com/ngstudio/burgerready/` unless they start with `tools/` or `app/`.

### A new food item
1. `game/Types.kt` → add an `Item` entry: `id`, `price`, `unlockLevel`, `station`, `cookTime`, `height` (stack spacing) and `label`. Keep `TRAY` last.
2. If it needs a new machine:
   - add a `StationType(model, label)`;
   - model `st_<x>` in `tools/blender/stations.py` (its `MODELS` table) with an `@out` anchor;
   - follow `docs/ASSET_CONTRACT.md §5`.
3. Model `food_<id>`:
   - add it in `tools/blender/food.py` (`MODELS` table, ≤700 tris, `@top` anchor);
   - run `tools/blender/build_all.sh` (or `Blender -b --factory-startup --python tools/blender/food.py -- food_<id>`).
4. `engine/SpriteAtlas.kt` → add the id to the icon `names` list so `IconBaker` makes `icon_<id>` for order bubbles. There are 12 slots of 112 px from y = 640; there's room for more rows.
5. UI card art: add the id to `FOODS` in `tools/blender/render_cards.py`, then render `card_food_<id>.png`. The Recipe Book card is created automatically from `Item.FOODS`.
6. `LevelGenerator.procedural` picks menus from items unlocked by `unlockLevel`; check the station weights there.
7. **Re-tune + validate.**

### A new Store (world location)
1. `game/Economy.kt` → `Stores.ALL`: add a `StoreDef` with:
   - id, name, cost, expectedLevel, perk text;
   - floor model, wall/trim/accent colours, ground key;
   - 3 prop model names, sky colour, music variant.
2. Apply the perk in `StatsCalc.stats()` (`when (storeId)`).
3. Model the 3 `store_<id>_<prop>` props in `tools/blender/stores.py` (≤1,500 tris each, contract §7).
4. Visual hooks:
   - `render/WorldView.kt`: `drawGround` (ground colour per ground key) and `treeModel` (outdoor tree swap);
   - `render/GameRenderer.kt`: `weather()` (ambient particles) and `applyLighting` (forced night?).
5. Render the card: add it to `STORE_SPECS` in `tools/blender/render_cards.py`, producing `assets/ui/store_<id>.png` (640×512).
6. The Recipe Book card `store_<id>` is automatic.

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
1. Add it to `TroubleKind` (`game/Types.kt`), plus its unlock level in `LevelGenerator.troubleUnlock()`.
2. `game/World.kt`:
   - spawn it in `spawnTrouble()`;
   - resolve it in `updateTroubles()`;
   - call `fixTrouble()` when done, which emits `Ev.TROUBLE_FIXED` and records it for cards.
3. **Teach the bot** (`Bot.decide`), or the validator will fail as targets become unreachable.
4. Draw it in `WorldView.drawTroubles` (alert badge + icon from the atlas).
5. Add a sound in `GameScreen.onGameEvent`.
6. The Recipe Book card `trouble_<name>` is automatic.

### A new mutator or boss
1. Add it to `Mutator` or `BossType` (`game/Types.kt`). Bosses rotate through `BossType.entries` in `bossFor()`.
2. Implement the effect where it belongs:
   - generation-time effects (spawn rate, order size, menu, layout) in `LevelGenerator.procedural()`;
   - runtime effects in `World`.
3. Cards `mut_<name>` / `boss_<name>` are automatic.
4. **Re-tune + validate.**

### A new restaurant layout
1. Add a `Spec(id, name, w, d, stations, tables, drive, minLevel)` to `Layouts.specs`. `build()` places everything parametrically.
2. Check the scale with debug level jumps.
3. Re-tune.

### A new permanent upgrade
1. Add it to `Upgrade` (`game/Economy.kt`) with base cost, max level and description.
2. Apply it in `StatsCalc.stats()`.
3. Decide whether `EconomyModel` should buy it: it skips `INCOME` and `START_CASH`.
4. Render `upgrade_<id>.png` via `ICONS` in `render_cards.py`.
5. Re-tune.

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

### Changing the save format
`SaveData` → `GameRepo.toJson/fromJson`.
- Add new fields with defaults, read them with `opt*`, and old saves keep working.
- For breaking changes, bump `version` and migrate in `fromJson`.

---

## Debug tools
Debug builds only (`BuildConfig.DEBUG_TOOLS` / `DEBUG_AD_UNLOCK`):

| Where | Tool |
|---|---|
| Level map | Tap a locked level → **Watch Ad** → 3 s mock ad → that level unlocks (`GameRepo.adUnlock`) |
| Results screen | **x2 REWARD (AD)** mock |
| Spin wheel / Shop | Mock-ad extra spin / bonus Bucks |
| Pause menu | **WIN ★★★** (instant 3-star win), **+$500** in-level cash |
| Settings → Debug tools | **+10 LVL**, **+100 LVL** (raise `highestUnlocked`), **+50K $** (+200 gems) |
| HUD | FPS · draw-call counter |
| Settings → Reset progress | Wipes the save, keeping the settings (available in release too) |

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
Screen coordinates at 720×1600: Play button ≈ (360, 1340); level dialog PLAY ≈ (360, 1026); pause ≈ (656, 62); settings gear ≈ (656, 66).

**Pitfalls:**
- `-gpu host` crashed the emulator ("Failed to find ColorBuffer"), so use SwiftShader. SwiftShader FPS (~15) means nothing for real performance.
- After `adb install -r`, wait ~2 s before `am start`, or the launch can be swallowed. `am start -W` reports cold-start time.

### Real-device profiling (still to do on the Realme RMX5313: Unisoc UMS9230E / Mali-G57 MP1, ~6 GB RAM, 720×1600 @ 320 dpi, 60/90 Hz, Android 16)
```bash
adb shell dumpsys gfxinfo com.ngstudio.burgerready reset
# play 2–3 minutes of a mid-game level (e.g. 200+), then:
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
   - fresh install → onboarding (no skip) → Level 1 → win → Level 2 unlocked;
   - locked level shows "Finish Level N to unlock" and no ad UI;
   - Terms/Privacy open;
   - background/resume mid-level;
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
| 1 | **Real-device performance not profiled** | Tested on an emulator only. Profile on the Realme (Mali-G57) before release |
| 2 | Release signed with the debug key | See the release checklist |
| 3 | Legal pages contain placeholders | `TODO(owner)` in `assets/legal/*.html` |
| 4 | No real ads / IAP | `ads/Ads.kt` seam. Shop shows "Gem Packs & No-Ads: coming soon" |
| 5 | Not a git repository | See the README "First commit" |
| 6 | Each level creates a new `GameSurfaceView` (new GL context) | Every level start re-uploads all models, rebuilds the atlas and re-bakes icons (~1–2 s on low-end). Next step: keep one GL surface/renderer for the app's lifetime |
| 7 | Strings are hard-coded English in Compose | Move them to `res/values/strings.xml` before localising |
| 8 | Accessibility | Images use `contentDescription = null`. Large font scales untested |
| 9 | `game/World.kt` is ~1,300 lines | Candidate split: customers, cars, staff, troubles into separate files (keep `update` order) |
| 10 | Some per-frame allocations in `WorldView` | e.g. `listOf`, `lambdas`, string building for labels. Profile GC on device; cache label strings |
| 11 | GL thread callbacks touch UI state | `session.onFinished` sets Compose state from the GL thread, and `ShiftCounters` is mutated there. It works, but posting to the main thread would be cleaner |
| 12 | Save `version = 1`, no migration code yet | `fromJson` tolerates missing keys |
| 13 | Audio judged by measurement only | Listen on a phone speaker; adjust `App.TRIM` |
| 14 | Planned Compose UI tests not written | e.g. onboarding has no skip, legal screens open, debug-only unlock dialog |
| 15 | Template leftovers | `ExampleUnitTest`, `ExampleInstrumentedTest` can be deleted |
| 16 | Model quirks | Frog + brimmed hats need `hatLift`. The food truck prop is 4.5 m along X, so it gets its own lawn spot. Seat anchors extend past table footprints. Details in `docs/ASSET_CONTRACT.md` |
| 17 | **Parrot pet ability not implemented** | `Cosmetics.PETS` says "Troubles appear on your radar sooner", but `StatsCalc.stats()` has no `"parrot"` case. Idea: spawn trouble alerts/off-screen arrows earlier, or show an arrow to every trouble |
| 18 | `StoreDef.music` is unused | Every store plays `level_calm`/`level_rush`. Either render per-store music variants (`tools/audio/music.py`) and pick them in `GameScreen`, or drop the field |
| 19 | Design items not yet built | Interior themes recolour furniture but don't swap models. No in-level revive ("+30 s") or rewarded ad on a failed shift. No off-screen arrows pointing to troubles (HUD lists them instead) |
