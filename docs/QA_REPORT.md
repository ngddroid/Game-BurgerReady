# Bun Voyage! — QA Report

_Date: 2026-11-23 (rounds 2–4: 2026-11-28) · Build: 1.0 (1) · Tester: automated + scripted emulator runs; rounds 2–4 on the Realme RMX5313_

> How to re-run these checks: [DEVELOPER_GUIDE.md § Testing on devices](DEVELOPER_GUIDE.md#testing-on-devices).
>
> ⚠️ **2026-11-28 update:** the level system below was replaced by persistent restaurants.
> - `RestaurantFlowTest` (5 tests) passes: all 12 restaurants complete in 12.5–33 simulated minutes, a novice finishes restaurant 1, pads are reachable and non-overlapping, saves restore exactly, runs are deterministic.
> - On the Realme RMX5313 (debug build), the game runs at ~60 fps in the Bun Wagon, and a built grill survives Home → PLAY and a reinstall.
> - The release build (R8) compiles.
> - The level-validator rows below are historical.
> Open items: [DEVELOPER_GUIDE.md § Known issues & next steps](DEVELOPER_GUIDE.md#known-issues--next-steps).

## Round 5: World Tour, 500 more restaurants (2026-11-28)

_Build: 1.0 (1) debug + release · Device: Realme RMX5313 over **wireless adb** (720×1600 @ 320 dpi, Android 16), MEDIUM quality · Design: [WORLD_TOUR_DESIGN.md](WORLD_TOUR_DESIGN.md). Testing used crafted saves; screenshots are in the session notes folder, `round5_screens/`._

**What was added**
- **The World Tour:** 25 themed regions × 20 restaurants, after Candy Kingdom: 512 restaurants in all.
  - Each region has its own palette set, ground, sky, time of day, weather, tree, props, menu lead, perk family, host character and 20 handwritten names.
  - Each region follows a 20-stop rhythm (pop-up / diner / hall / grand) to a flagship whose pad opens the next region.
- **Content:**
  - 55 new region props (`tools/blender/regions.py`);
  - 25 region dioramas (`render_cards.py region_`);
  - 8 new layouts (Pop-up Kiosk … Flagship);
  - 11 new weather kinds;
  - 77 story beats (211 lines): the tour opening, arrival / halfway / farewell per region, and the tour finale.
- **Interface:**
  - START THE WORLD TOUR on the classic finale card;
  - the Play hub chain by chapter (‹ › and MAP);
  - a World Map (26 chapters, restaurant grids);
  - World-tab rows per region and an idle-income dialog per region;
  - REGION COMPLETE celebration (+40 gems);
  - the Voyage Book's World Tour part;
  - a "Regions" card set and a "World Traveler" trophy track.
- **Engine:**
  - money is `Long`;
  - the store look is data (classic values unchanged);
  - save v3 stores finished restaurants as `{"all":1}`;
  - tour pad costs are priced from each restaurant's earning power.

### The classic game is unchanged
`ClassicGoldenTest` fingerprints, for the 12 classic restaurants:
- every config (plans, costs, guests);
- layouts 1–8;
- store data and stats (all pets);
- idle income and rewards;
- a 4-minute expert-bot simulation of each.

It was recorded before any change and passed after every step: the money refactor, the data-driven looks, the shared plan builder, new layouts, bot/nav fixes, and the tour itself. `RestaurantFlowTest` also still passes (pacing 11.9–33.3 min, novice R1 13.0 min).

### Verification
| Check | Result |
|---|---|
| `./gradlew :app:testDebugUnitTest` | ✅ 28 tests, 0 failures. New: `ClassicGoldenTest`, `TourTest` (7: catalogue/ids, plans and reachability for all 500, pacing sample of 125, economy bounds, tour story, progress restore, region models exist), `SaveDataJsonTest` v3 |
| Full pacing sweep (`FULL_TOUR=true`) | ✅ all **500** completed by the expert bot. Medians: pop-up 7.9, diner 10.2, hall 13.5, grand 16.9, flagship 18.6 min; ~100 h in total |
| Offline income | 8 h buys a median 0.29 of the next restaurant (max 1.37) |
| `./gradlew assembleRelease` | ✅ 34.8 MB (+4.8 MB: dioramas, props). Installed on the phone: starts, new hub chain, no debug UI, no crash |
| Frame rate | Tour pop-up 61 fps; **fully built 15×15 Flagship layout, busy: 61 fps, 277 draw calls**, 265 MB PSS |
| Owner's save | Backed up before testing (it had moved on: Seaside finished, Retro Diner 21/26). Restored byte-for-byte on the final debug build (md5 `67296f3e…d163`), unchanged after launch |

### Seen working on the phone
| Flow | Result |
|---|---|
| Owner's save on the new build | The hub chain now reads "‹ The Classic Voyage › MAP"; everything else as before |
| Classic finale → tour | Candy Kingdom BUILD ALL → finale → Golden Tour → VOYAGE COMPLETE with **START THE WORLD TOUR** → "Anchor Buns": "THE GOLDEN BOOK · The World Tour" scene, then "WORLD TOUR · REGION 1 OF 25 · Maple Harbor" (Dot) |
| A tour restaurant | Anchor Buns: pop-up layout, harbor palette, lighthouse prop, $76k pads, 61 fps |
| Hub and map | "Maple Harbor · stop 1 of 20 · +8% tips"; Region 1 chain; ‹ › browse to a locked region; World Map "13 of 512 open" with the 20 stops of Maple Harbor |
| Region end | Maple Harbor Hall FINISH → "REGION 1 STAMPED · Farewell, Maple Harbor" → **REGION COMPLETE!**, next region Blossom Valley, +40 gems → Petal Patties with its shake-first menu and kite prop |
| Largest layout | Blossom Grand Hall (Flagship 15×15) fully built, picked from the World Map: 61 fps |
| World tab | WORLD TOUR rows with dioramas, open/finished counts and income per hour |

### Issues found and fixed while building it
| # | Issue | Fix |
|---|---|---|
| 1 | Money was `Int`: tour prices overflow from about restaurant 15 | `Long` money in World, plans, saves and missions. Classic values identical (same Float maths, wider result) |
| 2 | Tour build times ranged from 1 to 60 minutes | Costs priced from each restaurant's earning power; tour guests gentler; per-size arrival floor |
| 3 | Bot froze on an unreachable goal (a seat's approach point in a wall) | Tries nearby approach points |
| 4 | A new table could eject the chef into a dead-end pocket (a human could be trapped too) | Eject picks the first free spot that still reaches the door |
| 5 | The planner could snap a blocked start to a cell behind a wall; the walker pushed into it forever | It only snaps to cells reachable in a straight line |
| 6 | 8 m-deep layout: a corner plant pot left a 0.5 m gap where the chef could wedge | No corner plants in layouts shallower than 9 m |
| 7 | With 24-item carry capacity the bot almost never had empty hands, so it never built | Capacity 18+: it builds while carrying (classic capacities stay below 18) |
| 8 | Expected Menu Prices / Offline Income levels were not capped at their max (+986 % late in the tour) | Capped at the max level (classic levels unaffected) |
| 9 | Idle income was rounded to `Int` (capped at 2.1 B/h) | `Long` rounding (classic values identical) |

### Still open (for later rounds)
- **Region art is shared:** one diorama per region, not per restaurant. The hub shows the same art for all 20 stops.
- **Menus:** the tour reuses the seven items (regions change the lead and weights). New dishes need models, icons and a separate card set.
- **Music and gold stars:**
  - every restaurant shares one music pair;
  - gold stars are classic-only (a per-flagship star is a natural next step).
- **Props:** a few new ones are plain (canoe, haystack, prize claw glass); trees reuse props at decor scale.
- **Long-term balance:** expert-bot only. Real players will need a few region play-throughs to tune the pop-up/flagship split and the offline-income share.

## Round 4: final polish (2026-11-28)

_Build: 1.0 (1) debug + release · Device: Realme RMX5313 (720×1600 @ 320 dpi, Android 16), MEDIUM quality. Testing used crafted saves (all 12 restaurants fully built, a returning player, mid-Seaside, a fresh install, the finale). Screenshots: session notes folder, `round4_screens/`._

### Known items from rounds 2–3
| # | Item | Before | After |
|---|---|---|---|
| 1 | **Speech card vs joystick** | In-game milestone cards sat over the bottom of the screen, where players steer; any touch on the card skipped a line | Cards sit in the HUD's top column, under the top row and trackers (a compact size). Only a deliberate tap advances: a press that moves past touch slop or is held > 450 ms is ignored. Verified on the phone: a drag across the card leaves the line; a tap advances it. Cut-scenes keep their bottom card |
| 2 | **Register cash pile hidden** | The banded cash bricks sat behind the counter; the food racks, the cashier and the order bubbles hid them in big layouts | The pile is drawn 0.35 m further back from the counter, clear of the racks and the cashier (visible in all 12 restaurants). Render-only: moving the collection spot itself was tried at three positions, and each one cost the bot extra trips and failed `GoldStarsTest` (Desert, Volcano or Tokyo); the simulation spot is unchanged |
| 3 | **Gold-star tracker stars small** | 12 dp stars, 12 sp text | 19 dp stars, 13 sp text on a taller pill; text ellipsizes instead of running off. The task pill was shortened ("Serve 25 customers: 0/25", "Task done · claim at Home") and is hidden while Hazel's tutorial tips show |
| 4 | **Voyage Book polish** | The list scrolled under the ribbon with a hard edge; pages had only a small thumbnail | Every `ScreenFrame` list (Voyage Book, Settings, Recipe Book, Trophies) fades out under its ribbon. An opened page shows a **postcard**: the restaurant render on its sky colours, a perforated stamp with the page number, a red BUN VOYAGE postmark once stamped, and "Greetings from …" / "Wish you were here…". The Voyage button gets a badge when a page was stamped since the book was last opened (new `SaveData.voyageSeen`, defaults to 0) |
| 5 | **Three popups in a row** | Returning players got prologue → idle income → daily calendar | At most one popup per visit to Home. The prologue still goes first (it is a story beat); income and the daily day then share one **WELCOME BACK** card (income row, today's calendar day with the week's dots, one COLLECT ALL button, auto-close). Only one due → the old single card. Closing loses nothing: income waits on the INCOME pill, the calendar on Daily |
| 6 | **Entry hitch** | Every visit re-read and re-parsed 116 models and redrew the atlas on the GL thread: GL init ~250 ms (models 192–209 ms), then a pop from blank to the scene | Models are parsed once per process (background thread at app start, `ModelData`), the atlas bitmap is kept: GL init **33–86 ms** (models 21–32 ms, atlas 5 ms on re-entry). A cream curtain covers the view until the first frame, then fades. The UI thread still drops a few frames during the route transition (AndroidView creation) |
| 7 | **Queue overlap** | Guests in long lines walked into each other (groups, back-to-back arrivals) | A guest waits while someone further ahead in the line stands right in front of them (`World.queueBlocked`: 0.62 m ahead, 0.36 m to the side). Only people ahead can hold someone up, so the line cannot deadlock |
| 8 | **Door blocking** | Guests walked through the chef and the chef got stuck at the door | Walking guests step around the chef (`World.yieldToPlayer`): a radial push plus a sidestep across their own path, through the normal collision; the chef is never slowed |
| 9 | **Night window glow** | Night stores (Rooftop, Tokyo, Space) were one flat blue outside | Warm light pools under every back and side window and along the shop front, halos around the street-lamp bulbs and the indoor floor lamps; strength follows the night amount |

### Sweep findings fixed
| # | Area | Finding | Fix |
|---|---|---|---|
| 10 | **Back key** | Back during a cut-scene (completion, finale, epilogue) or on the RESTAURANT COMPLETE / VOYAGE COMPLETE cards closed the app. The completion beat and celebration were lost | Scenes take Back as "wait"; the cards take it as their secondary button (HOME / KEEP COOKING) |
| 11 | Back key | Onboarding: Back on pages 2–4 closed the app. A replay from Settings → How to Play closed it from page 1 too | Back steps to the previous page; on page 1 a replay returns Home. Still no skip button |
| 12 | **Economy** | The daily calendar paid a flat 200–1,200 Bucks in every restaurant (worthless once pads cost millions). The spin wheel's labels showed 150/400/800 while it paid ×`priceMul` | `GameRepo.scaledBucks`: table value × the newest restaurant's money scale, rounded like pad costs; the calendar, the spin labels and its result text all show what is paid. Restaurant 1 keeps the table values |
| 13 | **Pet** | Captain Parrot's ability ("Troubles appear on your radar sooner") did nothing (open since round 1) | Now "Fixes troubles twice as fast" (`PlayerStats.fixSpeedMul`; ×2.5 with the Jungle perk). Default 1×, so pacing is unchanged |
| 14 | Numbers | `formatNum` printed "900.00M", "84.0K" | Three significant digits, floored, trailing zeros trimmed: "900M", "84K", "1.25M", "12.5K" |
| 15 | Top bar | The gem chip wrapped "5,000" as "5,00 / 0" | Currency chips never wrap; long values step down a size |
| 16 | Cut-scenes | The scene title was bare outlined text; over the Home hub card it collided with the restaurant name | Title on a cocoa plate with a gold rim and a spring-in |
| 17 | Kitchen tab | "Customer Patience" wrapped and its card was taller; with the HOT tag it truncated | Names stay on one line; the HOT tag sits on the icon corner |
| 18 | Pause menu | Music / sound / vibration toggles were bare icons | Labels under each: "Music on", "Sound off"… |
| 19 | World tab | "Bucks / hour · up to 8 h" could break between 8 and h | "Bucks/hour · up to 8 h" with a no-break space |
| 20 | Gold-star toast | Used the recipe-book icon | Star icon for gold-star toasts |
| 21 | Per-frame allocations | Queued customers allocated a new one-point path array every frame; the customer loop used an iterator | A reused `Customer.slotPath`; index loop with in-place removal |

### Verification
| Check | Result |
|---|---|
| `./gradlew :app:testDebugUnitTest` | ✅ 18 tests, 0 failures |
| `RestaurantFlowTest` (World changed: queue spacing, yielding guests, parrot stat) | ✅ 5/5. Expert bot: R0 **11.9 min** (was 12.5), others **15.6–33.3 min** (unchanged range). Novice R0: **13.0 min** (was 11.9) |
| `GoldStarsTest` | ✅ all 3 stars in every restaurant at **3.4–5.3 min** (limit 8) |
| `./gradlew assembleRelease` | ✅ 30.0 MB. Installed on the phone as a smoke test: starts, Voyage Book with postcards, a restaurant plays, no debug section in Settings or the pause menu, no crash in logcat |
| Frame rate, every restaurant fully built (MEDIUM) | **60–61 fps** in all 12 (Tokyo busy: 60 fps · 298 dc; Space night: 60 fps) |
| Restaurant entry, Space Station fully built | GL init 252 ms → 86 ms first entry, 33–46 ms re-entry |
| Owner's save | Backed up before testing (identical to round 3's 04:28 backup; md5 `2e3ea5a3…94a7`). Restored byte-for-byte on top of the final debug build; the same checksum after one launch for the screenshot. The prologue was left unseen (no taps) |

### Screens checked on the phone
Splash · onboarding (4 pages, Back) · Home: Play hub, Shop, Kitchen, World, Style (Chefs / Pets / Themes) · Voyage Book (pages, crew, postcards) · Recipe Book · Trophies · Settings (incl. debug block in debug only) · dialogs: Welcome back, Idle income, Daily rewards, Lucky spin, Daily tasks, Chef rank, restaurant detail · in game: HUD with trackers, story card, tutorial tip, UPGRADES sheet, pause, completion cut-scene, RESTAURANT COMPLETE, finale cut-scene, VOYAGE COMPLETE · all 12 restaurants fully built (day and night).

### Still open
- **Entry transition:** the UI thread drops a few frames (~100 ms) while the restaurant route animates in and the GL view is created; the curtain hides it. A single app-lifetime GL surface would remove it (known issue #6).
- **Story card placement:** at the top, a milestone card covers part of the kitchen for a few seconds. Acceptable for auto-advancing lines; a narrower card could be tried.
- **Guests on the sidewalk** still overlap each other briefly before joining the line (only the queue and the chef are handled).
- **Register pile:** drawn 0.35 m from where it is collected (inside the magnet radius, so it still feels right). Moving the simulation spot needs a bot/pacing pass.
- **Idle income uses the current built fraction for the whole away period** (pre-existing, round 3).
- **Owner actions before release:** release signing, `TODO(owner)` in the legal pages, real ads/IAP (see the release checklist).
- Not measured: thermals over 30 minutes; audio listening test on the phone speaker; large font scales.

## Round 3: gap analysis, story and late game (2026-11-28)

_Build: 1.0 (1) debug + release · Device: Realme RMX5313 (720×1600 @ 320 dpi, Android 16), MEDIUM quality · Docs: [GAP_ANALYSIS.md](GAP_ANALYSIS.md), [STORY_SCRIPT.md](STORY_SCRIPT.md)._

**What was added**
- **Story system** (`game/Story.kt`, `ui/screens/StoryUi.kt`):
  - six-character cast with rendered portraits (`cast_*`);
  - 52 beats: arrival, 1–2 milestone and completion beats per restaurant, prologue, gold-star intro, finale, post-game hook and epilogue;
  - speech cards (tap or auto-advance) in play; cut-scenes for completion, finale and the Home prologue;
  - Hazel's tutorial tips, with her portrait, in the Bun Wagon;
  - the **Voyage Book** screen (Play tab → Voyage).
- **Golden Tour** (`game/GoldStars.kt`): 3 one-visit gold stars per finished restaurant (36 in all). Each pays 10 gems and +5 % idle income; the 36th pays +300 gems and plays the epilogue.
  - Shown on the HUD, the Play hub card, the World-tab restaurant detail and the Voyage Book.
- **In-game trackers:** the next gold star, and today's first open daily task (tasks were invisible while playing).
- **Cash stacks:** banded cash bricks (`food_cash`, new Blender model) replace the coin towers at the registers. Flying pickups use the bill icon.
- **Cheap round-2 fixes:**
  - spin-wheel labels moved out from under the pointer;
  - idle income moved from a side button onto the hub card, which made room for the Voyage button.
- **Debug tools:** pause-menu **FINISH** and **+GOLD** (debug builds only).

### Verification
| Check | Result |
|---|---|
| `./gradlew :app:testDebugUnitTest` | ✅ 18 tests, 0 failures. `RestaurantFlowTest` 5/5, pacing unchanged (12.5–33.3 simulated min; novice R1 11.9 min). New: `StoryTest` 6, `GoldStarsTest` 3 (the bot earns all 3 stars in 3.5–5.3 min in every restaurant), `SaveDataJsonTest` 2 (round trip; a pre-round-3 save still loads) |
| `./gradlew assembleRelease` | ✅ 30.0 MB (was 29.1 MB: +5 portraits). Installed on the phone as a smoke test: starts, the Voyage Book works, no debug tools in the pause menu, no crash in logcat |
| Existing save on the phone | ✅ Backed up before testing (twice: the owner played between 04:06 and 04:20). Testing used crafted saves. The owner's 04:28 save was restored byte-for-byte, and the final debug build is installed on top. On next launch the owner sees the prologue, then Seaside Shack's beats |
| Frame rate with speech cards | 60–61 fps on MEDIUM (Seaside with a card: 61 fps · 168 dc; busy Candy Kingdom: 60–61 fps · 300 dc) |

### Seen working on the phone
Screenshots are in the session notes folder, `round3_screens/`.

| Flow | Result |
|---|---|
| Older save → Home | Prologue cut-scene ("Before page one · The Voyage Book"), then the daily popups as before |
| Voyage Book | Stamped/in-progress/locked pages; crew strip with "???" for unmet characters; transcripts with portraits; gold-star rows |
| Seaside Shack (in progress) | Arrival beat with Patty on the right and Fenna on the left while the restaurant runs; the "half built" beat followed after the cool-down |
| Bun Wagon (finished) | Welcome banner "Gold stars here: 0/3", Hazel's gold-star intro, tracker "Serve 100 guests: n/100". A ×3 combo earned star 2 while idle (gems +10, idle income 900 → 945/h). +GOLD → "All gold stars earned!" |
| Completion | FINISH in Seaside → cut-scene "Page 2 stamped · Postcard Two" → RESTAURANT COMPLETE → LET'S GO → Retro Diner arrival beat |
| Finale | Crafted save (11 restaurants done, Candy 39/41) → BUILD ALL → "The last page · Grand Opening" (8 bubbles, whole cast) → "The Golden Tour" → VOYAGE COMPLETE (33/36) |
| Epilogue | +GOLD in Candy → 36 stars → "Every Page Gold". Gems 500 → 830 (3 × 10 + 300) |
| Cash bricks | Green banded stacks next to the register label (large layouts partly hide them behind the cashier; see round 4) |

### Issues found and fixed during round 3
| # | Issue | Fix |
|---|---|---|
| 1 | The task tracker was clipped inside the narrow name column of the HUD | Trackers are now full-width pills under the top row; the rush/combo chips move down when trackers show |
| 2 | **The Home prologue auto-advanced and was marked seen while nobody watched** (caught on the owner's save) | Cut-scenes (prologue, completion, finale, post-game, epilogue) wait for taps; only in-game milestone cards auto-advance. Verified: 40 s idle, still on line 1, save untouched |
| 3 | In the last restaurant the finale waited behind pending milestone beats and the 9 s cool-down | `Story.next` puts the finale first; the director skips the cool-down for it (covered by `StoryTest`) |
| 4 | Seven side buttons don't fit the hub's hero area at 360 dp | Income moved onto the restaurant card (pink INCOME pill with badge) |
| 5 | "Earns … Bucks/hour while you're away" was cut off next to the INCOME pill | Shorter text, two lines allowed |
| 6 | Spin wheel: the top label sat under the pointer (round 2 open item) | Labels at 72 dp instead of 95 dp |

### Deferred (see GAP_ANALYSIS §3)
- **In-world HR / upgrade office:** needs space in all 8 layouts, a model and nav changes.
- **Blocked on SDKs or a backend:** rewarded-ad placements, events and season passes, leaderboards.
- **Still open from round 2:** customers overlapping in long queues, the door "block" feeling, the restaurant-entry GL hitch.

### For round 4 (polish)
- **Speech cards:**
  - the card covers the bottom strip where some players steer from, and touching it advances the line;
  - consider a smaller card placed above the joystick's usual zone, or ignoring touches that start as drags;
  - typewriter speed and auto-advance timing need a read-aloud check.
- **Cash stacks:** the pile sits behind the counter, and the cashier and order bubbles hide it in big layouts. Consider moving it to the guest side of the register, or capping its height.
- **HUD trackers:** stars at 12 dp are small; "Task done! Claim it at Home" could open Tasks on return.
- **Voyage Book:**
  - list `fadeTop` under the ribbon;
  - a stamp/postcard illustration per page;
  - a "new" badge on the Voyage button when a page gets stamped.
- **Home popup stacking:** prologue → income → daily on a returning save is three modal steps in a row.
- **Idle income uses the current built fraction for the whole away period** (pre-existing). A save built up during a long absence shows large pending totals (10.6K after ~3.5 h on the test save).
- **Round 2 leftovers:** entry hitch, queue overlap, door blocking, night window glow.

## Round 2: UI, graphics and bug-fix pass (2026-11-28)

_Build: 1.0 (1) debug + release · Device: Realme RMX5313 (Unisoc / Mali-G57 MP1, 720×1600 @ 320 dpi, Android 16), Auto quality = MEDIUM._

**Coverage.** Every screen was captured on the phone before and after the fixes:
- menus: splash, onboarding (4 pages), Play hub, Shop, Kitchen, World, Style, Settings, Recipe Book, Trophies;
- dialogs: daily, spin (including Back mid-spin), tasks, idle income, chef rank, restaurant detail, "Leaving?";
- in-game: HUD, UPGRADES sheet, pause, the RESTAURANT COMPLETE overlay → LET'S GO;
- restaurants: Bun Wagon (fresh start, tutorial), Seaside Shack, City Rooftop (fully built, night), Desert Drive-In, Tokyo Neon Alley (fully built, night), Space Station Diner (fully built, night, 42 pads), Underwater Dome (fresh, LOW and MEDIUM).

### Verification
| Check | Result |
|---|---|
| `./gradlew :app:testDebugUnitTest` | ✅ `RestaurantFlowTest` 5/5. Pacing unchanged from round 1: 12.5–33.3 simulated minutes, novice finishes R1 in 11.9 min. (`game/` changed only in `World.guidanceTarget`, a render-only query.) |
| `./gradlew assembleRelease` | ✅ 29.1 MB. Installed on the phone as a smoke test: starts, plays, no FPS overlay, no debug tools in the pause menu, no crash in logcat |
| Final debug build on the phone | ✅ installed; the save that was on the phone before this session was restored afterwards |
| Frame rate, fully built Space Station Diner (MEDIUM) | **48–49 fps → 61 fps** (dining view and kitchen view) |
| Frame rate, fully built City Rooftop (MEDIUM) | 49 fps → 60–61 fps |
| Frame rate, Bun Wagon / Seaside / Underwater (fresh) | 61 fps |
| Memory | ~375 MB PSS in a fully built restaurant (GL ≈ 115 MB, native ≈ 120 MB) |

### Issues found and fixed
| # | Area | Before (screenshot notes) | Fix |
|---|---|---|---|
| 1 | **All body text** | Every Nunito label (descriptions, tips, dialogs, chain names) rendered hair-thin and grey: washed out at arm's length | `nunito.ttf` is a variable font whose default instance is ExtraLight (wght 200), so `FontWeight.Bold` had no effect. `Theme.kt` now sets the `wght` axis per weight (500/600/700/800) |
| 2 | Back key on Home | With a Home dialog open (Daily, Spin, Tasks, Income, Rank, restaurant detail), Back left the dialog and stacked "Leaving?" on top | Back closes the open dialog first (`Home.kt`); the spin wheel swallows Back while it turns |
| 3 | Dialog order on Home | Income and Daily popped up again every time the player returned from Settings, the Recipe Book or a restaurant; Income showed for trivial amounts | `AutoPopups` in `Home.kt`: Income at most every 10 min and only when worth ≥ 5 min of income, Daily once per launch, chained Income → Daily |
| 4 | Idle income dialog | After COLLECT the dialog stayed open showing "COLLECT 0"; rows were in save-map order | Closes itself 1.4 s after collecting; rows follow the restaurant chain; renamed "IDLE INCOME" |
| 5 | **Economy bug** | The restaurant being played accrued "while you're away" idle income on top of register cash (Rooftop showed +538 after a short session) | The autosave moves that restaurant's idle clock forward by the time spent playing it (`GameRepo.commitRestaurant(playedMs)`, `Saver` in `GameScreen.kt`) |
| 6 | **Economy bug** | Bun Wagon (open from the first launch) had no idle-income start time, so it earned 0 until the first collect | `GameRepo.startIncomeClocks()` on load and on reset |
| 7 | In-game HUD (720 px) | The wallet pill overlapped the end of the build-progress bar; long names ("Space Station Diner") could run under the wallet | One top row with a weighted middle; long names step down in size |
| 8 | Tutorial tip | The tip box sat on top of the UPGRADES button | Compact tip above the button (top of the screen was tried, but the camera keeps the kitchen there) |
| 9 | UPGRADES sheet | "Customer Patience · Lv 0" ran into the price button | Name on one line, "Lv N · description" below, spacer before the button |
| 10 | RESTAURANT COMPLETE | Next restaurant's name and the +15 gems were left-aligned under centred text | Centred column, "NEXT STOP" label |
| 11 | Order bubbles | Three stacked bubbles above the queue covered the counter and each other | Bubbles for the guest being served and the next in line only (smaller) |
| 12 | Pad labels | The guide pointer covered the price; icon, price and tag were cramped on EXPAND pads | Icon above the price, tag below, spacing scales with the pad; the pointer floats above the icon |
| 13 | Sidewalk pads | The bench / flower bed sat on top of staff pads by the door | They step aside while a pad is showing there (like the signature props) |
| 14 | Kitchen floor | Near-white checker (`D7E8EC`) read as washed out next to the wood dining floors | Mint-teal checker (`C4E4DE`/`A9D4CD`) |
| 15 | Tile-floor restaurants | Rooftop / Tokyo / Space / Underwater dining floors were plain white (lavender at night) | Per-store floor tint in the default theme (`WorldView.storeFloorTint`) |
| 16 | Shadow colour | Shade multiplier `(0.68, 0.62, 0.72)` made cast shadows and the sidewalk purple | Cool slate `(0.62, 0.635, 0.72)`; golden hour slate-violet |
| 17 | Night stores | Space / Rooftop / Tokyo were the same pale lilac inside and out: no sense of evening | Darker blue exterior (night 0.72–0.9), warm lamp light inside the walls (`uInterior` in `TOON_FS`) |
| 18 | Camera | At the grills ~40 % of the screen was back lawn; big restaurants felt tight | Follow target bounded to the restaurant (never letting the chef leave the safe screen area), +2 % distance per metre of width above 8 m (max +14 %) |
| 19 | LOW quality | Blob shadows were almost invisible (alpha 0.32) | Larger, alpha 0.5, warmer tint, lifted off the floor |
| 20 | **Performance** | Fully built big restaurants ran at 48–49 fps on MEDIUM; Compose redrew every frame | GPU: opaque batches drawn front-to-back (early-Z), toon shader at mediump with highp positions, one hardware-PCF shadow tap instead of four, only body/head/legs cast shadows, carried food doesn't. UI: joystick ring redraws on touch only (was a 60 Hz frame loop), the UPGRADES button nudges 3 times instead of pulsing forever, trouble chips static, unchanged HUD states skip recomposition, `OutlinedText` draws 3 layers instead of 10 |
| 21 | Per-frame allocations | `WorldView` built strings, colours, lists, `Triple`s and lambdas every frame; `World.guidanceTarget()` allocated | Cached names, colours, pad labels and rack lists; `guidanceTarget(out)` fills an array; uniform locations cached in `Renderer` |
| 22 | Settings | The stock Material slider (thin bar thumb, stop dot) clashed with the game style | Chunky game slider (`VolumeSlider`) |
| 23 | Play hub | City Rooftop's navy title and Mountain Lodge's brown title were dark-on-dark; chain names were cut ("Mountain Lod…") | `titleColor()` picks a readable store colour; wider chain items |
| 24 | World tab | Locked restaurants were a muddy dark overlay; cards scrolled under the floating currency bar | Desaturated art with a light veil; tab lists fade out under the top bar (`Modifier.fadeTop`) |
| 25 | Text | "Serve a Fries", Trophy tiers via string replace ("IIII" → "IV" bug at tier 5), "Stores" wording left from the level era, gem emoji in buttons | Articles fixed, roman-numeral table, "restaurants" wording, gem icons |

### Not changed (checked)
- **Station output stacks** (capped at 8 on the station, 10 on the counter): they read as a reward and fit under the MAX tag in every layout.
- **MEDIUM keeps shadows, outlines and 4×MSAA**: it holds 60 fps after the fixes; LOW holds 61 fps.

### Still open (for rounds 3 and 4)
- The first frames after entering a restaurant hitch while the GL context, models and icons are rebuilt (known issue #6).
- The joystick is easy to block at the door when a guest walks in (characters don't push each other).
- Customers can briefly overlap one another in long queues (Space Station, Tokyo).
- ~~Spin wheel labels: the top label sits under the pointer.~~ Fixed in round 3.
- Night stores could use window glow and street-lamp halos on the road (round 4 polish).
- ~~Story, late game and the genre gaps in `MARKET_ANALYSIS.md` §6 (round 3).~~ Done in round 3 (story, gold stars, task tracker, cash stacks); the rest is prioritised in `GAP_ANALYSIS.md`.

## Test device
The Realme RMX5313 was busy with another testing session, so **on your instruction all device testing ran on an emulator configured like the Realme**:
- AVD `BunVoyage_720p`: 720×1600 px, density 320, Android 17, 4 cores, 4 GB RAM, host GPU (GLES 3.0 via Metal translation).
- Emulator frame rates are **not** representative of the Mali-G57. A short profiling pass on the Realme is still recommended once it's free (see "Still to verify").

## Automated tests (JVM, `./gradlew :app:testDebugUnitTest`)
| Test | What it proves | Result |
|---|---|---|
| `AllLevelsValidatorTest.allLevelsWinnable` | Simulates **all 10,000 levels** with the expert bot at the expected upgrade strength. Checks the bot beats every 1★ target by ≥ 10 %, 3★ is reachable, every level has a tuned target, and only unlocked layouts/items are used | ✅ pass (≈14 s, multi-threaded) |
| `AllLevelsValidatorTest.tutorialLevelsAreWonByNovice` | Levels 1–10 are won by a 50 %-skill bot | ✅ pass |
| `AllLevelsValidatorTest.difficultyCurveShape` | Chapter averages rise; a boss every 10th level; D(11) < 0.2, D(10000) > 0.95 | ✅ pass |
| `SimSmokeTest` | Spot levels 1–30 and 50…10,000 play through | ✅ pass |
| `LevelTunerTest` (manual, `TUNE=true`) | Regenerates `assets/levels/targets.bin` for 10,000 levels | ✅ 10,000 levels tuned in ≈29 s |

## Scripted emulator checks (debug build)
| Area | Check | Result |
|---|---|---|
| Launch | System splash → animated splash (logo drop, burger assembles, loading bar) | ✅ |
| Onboarding | 4 pages, **no skip button**; "Let's Cook!" starts Level 1 | ✅ |
| Level 1 | Build Grill pad → burgers cook → carry stack → drop at counter → serve at register → collect coins → combo | ✅ |
| Guidance | Tutorial tips; guide arrows follow the walkable path around the counter | ✅ |
| Pause | Music / sound / haptic toggles, Resume, Restart, Quit; debug tools only in debug builds | ✅ |
| Results | Stars stamp in, revenue vs goal, Bucks count-up, gems, new Recipe cards, ×2 (mock ad, debug only), Next / Replay / Home | ✅ |
| Progression | Winning Level 1 unlocks Level 2; stars appear on the map | ✅ |
| **Level locks (debug)** | Only Level 1 unlocked at first; tapping a locked level → "Watch Ad" → 3 s mock ad → Claim → that level unlocks and opens | ✅ |
| Home | Top bar (rank, Bucks, Gems, settings), side buttons with badges, 5-tab bottom bar with a raised Play button | ✅ |
| Daily | Daily Rewards (7-day), claim updates Bucks | ✅ (layout fixed during QA) |
| Tabs | Shop, Kitchen (upgrades + live stats), Stores (income, open/locked), Style (Chefs / Pets / Themes) | ✅ |
| Settings | Audio sliders and toggles, graphics quality, How to Play, Share, Rate, Reset (double confirm) | ✅ |
| Legal | Terms of Service and Privacy Policy open **new screens** rendering the themed HTML from `assets/legal/` | ✅ (title shrink fixed) |
| Later level (32) | Multiple stations, tables, Hire Runner pad, multi-item orders | ✅ |

## Release build (R8 minified, signed with the debug key for local testing)
| Check | Result |
|---|---|
| Starts without crashing (clean install, final build) | ❌ → ✅ The first release build crashed on start (`IllegalAccessError` from the template's experimental R8 `packageScope`). Fixed by using normal whole-app optimization |
| Only Level 1 unlocked; locked tap shakes the node and shows "Finish Level 1 to unlock"; **no ad UI anywhere** | ✅ |
| No debug tools in the pause menu or Settings | ✅ |
| APK size | 29.2 MB release with all HD art and audio (target < 60 MB) |

## Issues found and fixed during QA
1. Order-bubble icons were blank: the icon camera sat inside the 1 m near plane → per-camera near/far planes added.
2. Over-bright lighting → re-balanced sun, sky and ground light.
3. Camera too close / too much street → wider framing, target nudged toward the kitchen.
4. Player could get stuck on coarse nav cells → 0.25 m grid + inflated planning grid.
5. Customers ordered items whose station wasn't built yet → orders adapt to built stations.
6. Bot/staff could path outside the wall to reach the trash bin → interior approach point.
7. Splash logo appeared too late → re-sequenced animation.
8. Pad trigger too strict → radius 0.85 m, 0.22 s dwell.
9. Shadow sampler could read an unbound depth texture → 1×1 fallback depth texture.
10. Release crash from R8 `packageScope` → removed.
11. Daily-reward grid squeezed and showed wrong icons → weighted layout.
12. Level 10 (tutorial boss) didn't look like a boss on the map → flagged as a boss.
13. Recipe cards (VIP, Rush, Drive-Thru) were awarded just for seeing them → now only for actually serving them or surviving a rush; power-up cards wired in.
14. Store signature props were off-screen → placed beside the entrance; outdoor trees now change per store (snowy pines, palms, cacti…).
15. Store income formula mistakenly used the boss reward → fixed.
16. Back press on Home quit instantly → "Leaving?" confirmation dialog.
17. The splash loading bar lagged behind → driven directly.
18. Frog's brimmed hats covered its eyes → per-species hat lift.
19. The emulator's host-GPU mode crashed twice (an emulator bug, "Failed to find ColorBuffer") → testing switched to SwiftShader. Frame rates there are not meaningful.

## Final art pass (HD renders)
- 12 store dioramas, 8 theme rooms, 11 chef portraits, 6 pet portraits, 10 guest cards, 7 food cards, 8 upgrade icons, 4 onboarding illustrations, plus the adaptive launcher icon (with a monochrome themed-icon layer), splash icon, 512 px Play icon and the 1024×500 feature graphic.
- All are verified in the app: Stores tab, Style tab (buying the Bear skin and Pug pet applies them in-game), onboarding, and the launcher.

## Still to verify
- **Real-device performance on the Realme (Mali-G57)** when the phone is free: FPS overlay (debug builds show "fps · draw calls"), thermals over 30 minutes, `dumpsys meminfo`.
- Listening test of the synthesised music and SFX on the phone speaker; the volumes are pre-trimmed from the audio analysis.
- Legal pages: fill in the real developer name and support e-mail (they are marked `TODO(owner)` in `assets/legal/*.html`).
