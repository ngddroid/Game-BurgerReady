# Bun Voyage! — World Tour design (500 new restaurants)

_Status: **implemented in round 5 (2026-11-28).** Numbers below are as built; §5 lists where the build differs from the first draft._

**Goal.** Add 500 new restaurants in 25 themed regions after the existing 12, for 512 in all.
- The 12 classic restaurants stay exactly as they are: same plans, pacing, look, story, gold stars and save data.
- Nothing a player owns changes meaning, and nothing is ever taken back.

Everything here is original: region names, restaurant names, characters, props and lines were written for this game.

---

## 1. Pillars
1. **The classic Voyage is untouched.**
   - Restaurants 1–12, their build plans, pad ids, pacing windows, story beats, gold stars (36 + epilogue), cards and trophies keep working byte-for-byte.
   - The tour is appended after them; it never changes them.
2. **Each region is a place you can name.** A region has its own:
   - look: palette, ground, sky, time of day, weather, trees and signature props;
   - menu emphasis, perk family and host character;
   - arrival and farewell scenes.
   Twenty restaurants in a region are twenty variations on that place, never twenty copies.
3. **Short loops, long journey.**
   - A tour restaurant takes 8–20 minutes, about 13 on average.
   - The whole tour is roughly 100+ hours of active play: a long tail for players who finished the classic game.
4. **One wallet, no resets, deterministic, bot-validated.** These are the same rules as the classic game (see `CLAUDE.md`). Every tour restaurant is generated from fixed data and checked by tests.
5. **Append-only catalogue.** Region ids and restaurant ids are saved, so they are never reordered or renamed. New regions go at the end.

## 2. Player journey
| Stage | What happens |
|---|---|
| Classic Voyage (1–12) | Unchanged. Candy Kingdom's grand opening ends with Hazel's golden book: "Now you write your own" |
| **The World Tour opens** | The finale's last card (VOYAGE COMPLETE) gains a button: **START THE WORLD TOUR**. It opens region 1, restaurant 1. Saves that already passed the finale get the tour on their next launch |
| A region (20 restaurants) | Arrival scene at restaurant 1 (the host introduces the place). Restaurants 1–19 open one after another through their NEW RESTAURANT pad. Restaurant 20 is the **flagship**: bigger, with a farewell scene and a region stamp |
| Next region | The flagship's pad reads **NEXT REGION**. Its celebration card says "New region!" and shows the region art |
| End of the tour (512) | The last flagship (Golden Capital) has no pad after it. Finale scene for the tour, then everything stays playable for gold stars, idle income and renovations |

Pages of the **Golden Book** (the Voyage Book's second part) are the 25 regions. Each fills up as its 20 restaurants are finished.

## 3. Region catalogue (25)
| # | Region | Place | Look (ground · sky · time · weather) | Signature props (existing + new) | Host | Perk family | Menu lead |
|---|---|---|---|---|---|---|---|
| 1 | **Maple Harbor** | Fishing port, foggy mornings | cobble · grey-blue · morning · drifting mist | lighthouse*, crate stack*, anchor*, umbrella | Dot | tips | burger, fries, soda |
| 2 | **Blossom Valley** | Spring orchards in bloom | meadow · pink haze · day · falling petals | blossom tree*, paper kite*, picnic table | Hazel | patience | shake, burger, fries |
| 3 | **Sunbaked Savanna** | Grassland at golden hour | dry grass · amber · golden hour · heat sparkles | acacia tree*, safari jeep*, drum* | Fenna | drive-thru | double, soda, fries |
| 4 | **Polar Outpost** | Arctic research station | snow · ice blue · night (aurora) · snow | igloo*, ice-block bench*, snowman | Scraps | cook speed | cheese, shake, fries |
| 5 | **Riverboat Delta** | Rainforest river town | mud-green · teal · day · light rain | riverboat*, lily-pad planter*, big leaf | Dot | VIP | veggie, soda, burger |
| 6 | **Castle Hill** | Hilltop market town | cobble · lavender · day · banners in wind | stone tower*, market stall*, barrels* | Clementine | tips | double, cheese, soda |
| 7 | **Frontier Gulch** | Dusty boomtown | clay · peach · golden hour · tumble dust | saloon sign*, hay bale*, cactus, water tower | Fenna | drive-thru | burger, double, soda |
| 8 | **Coconut Cove** | Island village | sand · turquoise · day · sea breeze | hammock*, treasure chest*, palm, surfboard | Dot | patience | shake, fries, veggie |
| 9 | **Alpine Meadow** | Mountain dairy village | meadow · clear blue · morning · butterflies | chalet sign*, milk churn*, fence*, pine | Hazel | cook speed | cheese, shake, burger |
| 10 | **Pixel Arcade** | Retro arcade street | asphalt · indigo · night · neon sparkles | arcade cabinet*, prize claw*, neon star | Scraps | combo | soda, burger, fries |
| 11 | **Moonlit Hollow** | Friendly spooky village | moss · purple · night · fireflies | pumpkin stack*, crooked lamp*, lantern | Scraps | combo | shake, veggie, burger |
| 12 | **Funfair Pier** | Seaside carnival | boardwalk · sunset · golden hour · confetti | mini ferris wheel*, ticket booth*, balloon stand | Dot | VIP | shake, soda, fries |
| 13 | **Sandstone Ruins** | Ancient desert temples | sand · gold · day · heat sparkles | sandstone column*, clay urn*, palm | Clementine | tips | double, veggie, soda |
| 14 | **Skyreach Isles** | Floating islands | cloud-white · sky blue · day · drifting clouds | windmill*, hot-air balloon*, cloud | Fenna | carry | burger, shake, soda |
| 15 | **Crystal Caverns** | Glowing caves | slate · deep violet · night · crystal glints | crystal cluster*, mine cart*, glow mushroom* | Scraps | cook speed | cheese, fries, soda |
| 16 | **Orchard Farm** | Countryside farm | meadow · warm blue · morning · pollen | barn front*, haystack*, apple crate* | Hazel | patience | veggie, burger, shake |
| 17 | **Lunar Colony** | Moon base | grey dust · black · night · star sparkles | habitat dome*, moon rover*, rocket model | Fenna | carry | double, soda, shake |
| 18 | **Lantern Lagoon** | Floating night market | water-edge · navy · dusk · lantern glow | floating lantern*, canoe*, coral | Dot | VIP | fries, veggie, soda |
| 19 | **Amber Woods** | Autumn forest | leaf litter · orange · golden hour · falling leaves | maple tree*, leaf pile*, log pile | Hazel | tips | burger, cheese, shake |
| 20 | **Gearworks City** | Clockwork factory town | brick · bronze · dusk · steam puffs | giant gear*, chimney stack*, steam vent | Fenna | staff speed | double, fries, soda |
| 21 | **Mushroom Glade** | Fairy forest | moss · teal · night · fireflies | giant mushroom*, toadstool stool*, big leaf | Scraps | pet ability | veggie, shake, fries |
| 22 | **Bamboo Springs** | Hot-spring retreat | stone · jade · dusk · steam | bamboo grove*, stone lantern*, bonsai | Clementine | patience | soda, veggie, cheese |
| 23 | **Frostfest Village** | Winter festival | snow · rose-gold · night · snow | festive pine*, gift stack*, string lights | Hazel | combo | shake, double, cheese |
| 24 | **Fossil Valley** | Prehistoric valley | clay · hazy green · day · ash sparkles | fern clump*, friendly dino statue*, lava rock | Dot | cook speed | double, burger, fries |
| 25 | **Golden Capital** | City of gold, the final stop | gold tile · cream · golden hour · fireworks | fountain*, golden statue of Patty*, string lights | the whole cast | all (small) | all seven |

\* = new model (two to four per region; ~70 in all, built by a new `tools/blender/regions.py`). Unstarred props already exist and are recoloured through the tint mask where useful.

**Region data** (`game/Tour.kt`, one `RegionDef` each): id, name, blurb, ground colour, sky colour, floor model + tint, wall/trim/accent palettes (4 variants), night amount per time of day, weather kind, tree model, prop pool, host `Speaker`, perk family, menu lead order, 20 restaurant names.

## 4. Restaurants inside a region
### 4.1 The 20-step pattern
Every region follows the same rhythm, so the difficulty curve is predictable and each region builds up to its flagship.

| Step | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14 | 15 | 16 | 17 | 18 | 19 | 20 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Size | S | M | S | M | L | S | M | L | M | L | S | M | L | M | XL | M | L | M | L | **F** |

| Size | Layouts | Pads | Target time (expert bot) | Notes |
|---|---|---|---|---|
| S (pop-up) | small templates (8–10 m) | ~18–22 | 7–11 min | Quick wins between bigger builds; no drive-thru |
| M (diner) | medium (10–12 m) | ~25–30 | 10–15 min | Drive-thru in half of them |
| L (hall) | large (12–14 m) | ~32–38 | 13–19 min | Drive-thru, two runners |
| XL / F (flagship) | largest (14–15 m) | ~40–44 | 16–24 min | Full staff. The flagship's last pad is NEXT REGION |

Average ≈ 13 min, so 500 restaurants ≈ 108 h of expert play, and humans take 1.5–2× longer.

### 4.2 Variation per restaurant
These rotate deterministically with the step number and a per-region seed, so neighbours never look alike:
- **Layout:** chosen from the size class's templates. Eight new templates (ids 9–16) are appended to the existing eight; they differ in width/depth ratio, station count, table count and drive-thru:

  | id | Name | w × d | Stations | Tables | Drive |
  |---|---|---|---|---|---|
  | 9 | Pop-up Kiosk | 8 × 8 | 3 | 2 | no (no corner plant pots: in an 8 m room they left a gap narrower than the chef) |
  | 10 | Corner Café | 9 × 9 | 3 | 3 | no |
  | 11 | Long Counter | 13 × 10 | 6 | 6 | yes |
  | 12 | Garden Terrace | 11 × 13 | 5 | 8 | no |
  | 13 | Drive Hub | 11 × 11 | 5 | 5 | yes |
  | 14 | Market Hall | 14 × 12 | 7 | 9 | yes |
  | 15 | Grand Hall | 15 × 13 | 8 | 10 | yes |
  | 16 | Flagship | 15 × 15 | 8 | 12 | yes |

  Templates 1–8 stay as they are; tour sizes also reuse 2–8.
- **Palette:** one of the region's four wall/trim/accent variants.
- **Time of day:** each region has a base time; one restaurant in five is set at the "other" time (night in day regions, golden hour in night regions).
- **Props:** three of the region's prop pool, rotated. Trees and ground come from the region.
- **Menu:** the region's lead items are built first (station order). The other items follow as stations allow, and the region weights orders toward its lead.
- **Perk:** the region's perk family, with strength stepping up within the region (for example tips +8 % → +20 % at the flagship).
- **Guests:** patience, order size and troubles come from the size class and the region's tier, not from the classic formulas (which flatten out after restaurant 12).

### 4.3 Names and ids
- Restaurant ids are `<region id>_<two-digit step>`, for example `harbor_01` … `harbor_20`. Region ids are short lowercase words.
- **These ids are saved. Never rename them, never reorder regions; append new regions only.**
- Names are handwritten, 20 per region (500 in all), such as "Anchor Buns" or "Lighthouse Grill". A test checks they are unique, at most 20 characters, and fit the HUD.

## 5. Economy (one wallet, long numbers), as built
### 5.1 Money scale
- Classic `priceMul` stays as it is (×1 … ×420).
- The tour uses tiers: `priceMul(region r, step k) = 3,400 × 1.55^r × 1.022^k`.
- Region 1 starts at about 8× Candy Kingdom. The first draft had ×600, but then the 12 finished classic restaurants' idle income alone paid for the first tour restaurants twice over.
- The last flagship reaches about ×1.9·10⁸, with pad costs up to about 10¹⁴.
- Money is `Long` everywhere: pad costs, partial payments, piles, order pay, missions. Floating texts keep exact numbers below a billion and shorten above.

### 5.2 Pad costs and pacing: priced from earning power
- Tour players have every Kitchen upgrade maxed, and region menus differ (a double-stack lead earns about 3× a fries-and-soda lead). Fixed costs therefore gave build times from 1 to 60 minutes.
- Each tour restaurant now estimates its full-build earnings per second (`Tour.earningPower`) from:
  - menu value and station throughput;
  - the size class's arrival floor;
  - the drive-thru;
  - the player's expected price and tip upgrades.
- The last pad costs `lastPadSeconds` of that: S 180 s, M 175 s, L/XL 155 s, F 160 s. The rest follow the classic geometric curve and kind weights.
- Expert-bot build times over **all 500** (`FULL_TOUR=true`):

  | Size | Restaurants | Min | Median | Max |
  |---|---|---|---|---|
  | Pop-up (S) | 100 | 6.4 min | 7.9 min | 12.1 min |
  | Diner (M) | 200 | 8.0 min | 10.2 min | 19.9 min |
  | Hall (L) | 150 | 9.4 min | 13.5 min | 25.2 min |
  | Grand (XL) | 25 | 12.5 min | 16.9 min | 26.9 min |
  | Flagship (F) | 25 | 11.0 min | 18.6 min | 28.5 min |

  That is about **100 hours** of expert play; humans take 1.5–2× longer.
- Tour guests are gentler than late classic guests (patience 50–64, order mean 1.6–2.5), and a per-size **arrival floor** (1.0–1.5 s) replaces the classic index-based floor.

### 5.3 Idle income
- Tour restaurants earn `earnPerSec × 3600 × 0.0003 × priceMul` per hour.
- 8 h offline buys a median **0.29** of the next restaurant (max 1.37, a pop-up), checked by `TourTest.economyBounds`.
- The classic restaurants' idle income stays as it is and is a small help at the start of the tour (under half of the first tour restaurant per 8 h).

### 5.4 Rewards that scale
`Restaurants.rewardUnit(frontier)` reads the tour money scale, so chests, spins, tasks and the daily calendar stay meaningful. A finished region pays +40 gems and its card in the "Regions" Recipe Book set (500 gems for the full set).

## 6. Story: the Golden Book
- **Opening (finale add-on):** Hazel's gift, the blank golden book, becomes the tour.
  - The VOYAGE COMPLETE card gets START THE WORLD TOUR.
  - A short scene follows: Fenna offers her map, Dot already has tickets, Scraps carries the bags.
- **Per region:** each host character carries the region.
  - An **arrival** scene at step 1 (2–4 bubbles) plays as a cut-scene, waiting for taps like the classic completion scenes.
  - A **farewell** scene plays at the flagship's NEXT REGION, with Hazel's postcard reply pointing to the next region.
  - One **mid-region** milestone card at step 10 (2 bubbles) plays while you play.
  - That is 75 beats, handwritten, under the existing writing rules (≤ 72 characters, ≤ 2 sentences, ids never renamed).
- **Tour finale** (Golden Capital flagship): the whole cast, Hazel's last postcard, and a hook for future regions ("The map has more edges than you think").
- The **Voyage Book** gets a second part, **The Golden Book**:
  - 25 region pages with stamps (x / 20 restaurants finished), the host's portrait, and the region's scenes to re-read;
  - locked regions show "???".

## 7. Interface
| Place | Change |
|---|---|
| Play hub | The restaurant chain shows the **current chapter**: Classic Voyage (12) or the current region (20), with ‹ › to browse chapters and a **MAP** button. The hero art is the region diorama for tour restaurants |
| **World Map** (new screen) | 26 chapter cards (Classic + 25 regions): art, name, host, progress x/20, region stamp, locked state. Tap a chapter for its restaurant grid (4 columns) → select → PLAY |
| World tab (idle income) | Grouped by chapter: collapsed rows with income per hour; expand for per-restaurant rows and renovations. "Collect all" stays on top |
| Idle-income dialog | One row per chapter instead of per restaurant |
| NEW RESTAURANT celebration | Unchanged inside a region. At a flagship: "REGION COMPLETE!", the region stamp, then "Next region" with its art |
| HUD | Unchanged. The restaurant name scales down for long names (already done); a small region chip ("Polar Outpost · 7/20") sits under the progress bar |
| Recipe Book | New card set **Regions** (25 cards, one per finished region). The classic "Stores" set stays 12 |
| Trophies | New track **World Traveler** (regions finished: 1, 5, 10, 18, 25). Existing tracks unchanged |

Large lists use lazy layouts and never build all 512 configs on the UI thread at once (§8.3).

## 8. Technical design
### 8.1 Code
| Area | Change |
|---|---|
| `game/Restaurant.kt` | `Restaurants.CLASSIC = 12`, `COUNT = CLASSIC + Tour.COUNT`. `config(i)` delegates to the tour generator for `i ≥ 12`. `isLast` covers both the classic finale restaurant (no NEW RESTAURANT pad, as today) and the last tour flagship. Money values (`PadSpec.cost`, `roundCost`) become `Long` |
| `game/Tour.kt` (new) | `RegionDef` catalogue, size pattern, layout choice, palette/time/prop rotation, names, `priceMul`, pacing constants, `StoreDef` for each tour restaurant |
| `game/Economy.kt` | `StoreDef` gains data fields instead of id switches: `night`, `weather`, `tree`, `groundColor`, `floorTint`, `perk` (kind + amount), `regionId`. **The 12 classic entries get exactly the values the id switches give today**, so their look and perks don't change. `Stores.byId` resolves tour ids. `StatsCalc` reads the perk from the definition |
| `game/World.kt` | `Pad.paid`, coin-pile amounts and order pay become `Long`; `priced()` uses `Double` |
| `game/Layouts.kt` | Specs 9–16 appended (ids are what tour configs reference) |
| `data/Progress.kt`, `GameRepo.kt` | `RestaurantProgress.paid: Map<Int, Long>` (reads old `Int` JSON fine). `openWorldTour()`, tour-aware `openNextRestaurant` (crossing a region adds the region card). Save compaction (§8.2) |
| `render/WorldView.kt`, `GameRenderer.kt` | Read `night`, `weather`, `tree`, `groundColor`, `floorTint` from `StoreDef`; new weather kinds (mist, petals, leaves, rain, fireflies, dust, confetti) as sprite particles drawn in `SpriteAtlas` |
| `ui/…` | World Map screen, chapter chain on the Play hub, grouping in the World tab and income dialog, Golden Book part of the Voyage Book, region celebration |
| `game/Story.kt` | Region beats keyed by global restaurant index (step 1 / step 10 / flagship), plus the tour opening and finale. `Story` tests split into classic (unchanged) and tour |
| `tools/blender/regions.py` (new) | ~70 new props (≤ 1,500 tris each, same conventions as `stores.py`) |
| `tools/blender/render_cards.py` | `region_<id>.png` dioramas (25, 480 × 384) built from region specs |

### 8.2 Save format (`version` 3)
- A fully built restaurant is written as `{"all": 1}` instead of its full list of pad ids. That keeps the file small with hundreds of restaurants; it is expanded back on load.
- Partial payments are read with `optLong`, so version-2 saves load unchanged.
- New fields: none required (opened restaurants already live in `stores`). Whether the tour is open comes from `stores` containing `harbor_01`.
- `SaveDataJsonTest` covers v2 → v3 and compaction round trips.

### 8.3 Performance
- Configs are built lazily and cached (as today). Summaries like `builtTotal()` and idle income use per-chapter caches, so the UI never builds 500 configs per frame.
- Restaurant data is small: about 2 KB per config, so 1 MB if all 512 are built.
- The save stays under ~60 KB thanks to compaction. It is written on the IO thread, as now.
- Per-frame paths don't change: one restaurant is simulated and drawn at a time.

### 8.4 Rules added to `CLAUDE.md`
- Region and restaurant ids are saved and append-only. Never reorder regions or the 20-step pattern of an existing region.
- Classic restaurants 1–12 must not change. A golden test hashes their configs (plans, costs, layouts, perks, colours) and fails on any difference.
- New tour content must pass `TourFlowTest`, `TourEconomyTest` and the naming/structure tests.

## 9. Late game inside the tour
- **Region stamps** (cards + Golden Book): the visible goal per region.
- **Gold stars stay classic-only** (36 + epilogue, unchanged). A later option is one gold star per tour flagship (25), using the same one-visit goal system with formula targets.
- **Renovations** work for every restaurant.
- **Idle income** grows through the tour, tuned so active play still matters.

## 10. Validation
| Test | Covers | Runtime budget |
|---|---|---|
| `ClassicGoldenTest` | The 12 classic configs are identical to a stored fingerprint | < 1 s |
| `TourStructureTest` | All 500: ids unique and stable, names unique, pads reachable and non-overlapping, costs increasing and within `Long`, EXPAND rules (last flagship has none), layouts 9–16 valid | < 10 s |
| `TourFlowTest` | Expert bot pacing for a sample covering every region × size class (100 restaurants). Each must finish inside its size window. `FULL_TOUR=true` runs all 500 before a release | sample < 2 min |
| `TourEconomyTest` | Offline-income ratio (§5.3) and largest values stay inside `Long` | < 1 s |
| `StoryTest` (extended) | Region beats: one arrival, one mid, one farewell per region, writing rules, ids unique | < 1 s |
| `SaveDataJsonTest` (extended) | v2 → v3, compaction, `Long` payments | < 1 s |
| On the device | A crafted save at the end of the classic game → START THE WORLD TOUR → region 1 arrival → FINISH ×19 → flagship farewell → region 2. Frame rate in the largest tour layouts (target 60 fps on MEDIUM); save size and load time with 500 restaurants open | — |

## 11. Implementation plan
1. **Engine prep (no visible change).**
   - Money to `Long`; `StoreDef` data fields with the classic values; layouts 9–16; `Restaurants.CLASSIC`.
   - The golden fingerprint test is written *first* and must stay green through every step.
2. **Tour catalogue and generator.**
   - 25 regions (data, names, palettes, perks, menus).
   - Generator, pacing constants, `TourStructure/Flow/Economy` tests.
   - At this point all 500 restaurants are playable with existing props.
3. **Interface.** World Map, chapter chain, grouping, region celebration, Golden Book, cards and trophies.
4. **Story.** Tour opening, 75 region beats, tour finale.
5. **Art.**
   - New weather particles.
   - `regions.py` props, region by region (two to four each).
   - Region dioramas.
6. **Device QA and docs.**
   - The device checks in §10.
   - `ARCHITECTURE`, `DEVELOPER_GUIDE` (recipe: "a new region"), `ASSET_CONTRACT`, `QA_REPORT` round 5, `CLAUDE.md` rules.

**Definition of done:**
- 512 restaurants are playable from a real save, and the classic 12 are unchanged (golden test).
- All tests are green, the release build passes, and the device walkthrough is recorded.
- Every region has its own look, props, names and scenes.

## 12. Risks and answers
| Risk | Answer |
|---|---|
| Tour restaurants feel samey | Region identity (look, props, weather, menu, host, scenes) plus per-restaurant rotation (layout, palette, time, props); the 20-step rhythm builds to a flagship |
| Offline income makes play pointless | Tour idle factor 0.06 and the economy test bound (§5.3) |
| Numbers overflow | `Long` money, a tiered curve that tops out around 10¹⁴, `Qa`/`Qi` formatting, and the economy test checks the maxima |
| Classic game changes by accident | Fingerprint test; classic values moved into data unchanged; no classic plan is touched |
| Long test runs | Structure for all 500 every run; pacing on a 100-restaurant sample; the full sweep before releases |
| Save size and load time | Compaction of finished restaurants; lazy configs |
| Asset volume (70 props, 25 dioramas) | Built in batches of five regions. Each region is fully playable with existing props, so the props raise the look but never block play |
