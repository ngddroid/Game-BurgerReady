# Asset Contract (engine ⇄ asset scripts)

This file is the single source of truth for every runtime asset name, part name, anchor and size the engine expects.
Asset scripts live in `tools/`; outputs go to `app/src/main/assets/`.
Read `tools/blender/bvlib.py`: it explains the `.bvm` format, the axes (Blender Z-up → engine Y-up, characters face Blender **-Y**), pivots, tint mask and anchors.

> **Status (2026-11-25): every asset listed below exists.**
> - 116 models in total. `tools/blender/verify_models.py` passes for all props, and `tools/blender/inspect_bvm.py` inspects any model.
> - How to run the scripts: [`tools/README.md`](../tools/README.md).
>
> **As-built differences (the engine already accounts for these; keep them unless you also change the code):**
> - Anchors described as "N m out" are measured from the model **pivot**, e.g. counter `@queue` = (0.88, 0, 0.90), `@cashier` = (0.88, 0, −0.85).
> - Character `@hat` is at skull height (z = 1.0), not at the apex. The bunny's `@bubble` is raised to 1.41 (its ears reach 1.35).
> - Frog eyes sit high. The engine lifts and tilts hats on frogs (`WorldView.hatLiftFor`).
> - Shirt **and sleeve** faces are tint-masked. Collars are fixed cream and shorts are fixed slate (#4B587E).
> - `hat_helmet` is an opaque glass-coloured dome, because `.bvm` has no transparency.
> - `store_wagon_truck` is 4.5 m long along X with the hatch facing −Y. The engine places props with a radius > 1.6 m on their own lawn spot.
> - Burger buns are 0.306 m, but the lettuce and cheese overhang to ~0.35 m.
> - `drive_window` is an open-back counter with no roof, so the racks stay visible from the camera.
> - Seat anchors sit at 0.62 m (table2) and 0.75 m (table4) from the centre, beyond the table footprint.
> - Tiles have no bottom faces; their top surface is exactly at y = 0.
> - `scooter` is a single `root` part.
> - Audio lives in `tools/audio/` (see its README). The list of sound names is `tools/audio/sfx_list.txt`.

## Global art direction (all assets)
- "Sunset diner" toy look: chunky, rounded, **bevelled edges everywhere** (no razor-sharp boxes), slightly exaggerated proportions, readable silhouettes from a high 3/4 camera, saturated but warm colours. Use the `bvlib.PAL` palette keys.
- Must be **original**. Don't imitate any existing game, brand, mascot or logo; no text or brand marks on models (signs use abstract burger and star shapes only).
- Colour comes only from flat materials via `bvlib.mat(...)` (AO is baked automatically on export). `tint=True` marks faces the engine recolours: customer shirts, car bodies, chair seats, store trims.
- Keep meshes light. Budgets are in triangles **after** modifiers. Check the `EXPORTED ... tris=` line.
- Every script must run headless with no errors: `~/Applications/Blender.app/Contents/MacOS/Blender -b --factory-startup --python tools/blender/<script>.py`
- Write a preview PNG for every model into `tools/previews/<category>/<name>.png`, and look at it to check shape and colour.

## Scale
1 unit = 1 m. A character is ~1.05 m tall. Counter height is 0.95 m. Table height is 0.62 m. Chair seat height is 0.40 m. The gameplay grid cell is 1 m.

---

## 1. Characters: `models/char_<species>.bvm` (budget ≤ 2,600 tris each)
Species: `corgi, cat, bunny, bear, panda, frog, penguin, fox, duck, raccoon`

Chibi proportions: the head is ~45 % of the height. Big glossy eyes (black sphere with a small white highlight sphere), small muzzle/nose, species ears built into the head. Short stubby arms and legs, a rounded egg-shaped torso.

Parts. The order in the file must be exactly this, with Blender parents as shown. Each pivot is the object origin.

| Part | Parent | Pivot (Blender coords, x, y, z) | Notes |
|---|---|---|---|
| `body` | – | (0, 0, 0.40) hip centre | Torso. **Shirt faces `tint=True`** (white base colour so the tint shows true). Includes a neck/collar. Feet are NOT here |
| `head` | body | (0, 0, 0.62) neck | Head + ears + muzzle + nose (no eyes). Fur colours fixed (not tinted) |
| `eyes` | head | (0, -0.20, 0.80) between the eyes | Both eyes in one part (the engine scales Y to blink) |
| `armL` | body | (0.17, 0, 0.58) left shoulder | Arm hangs down; paw/hand at the end. Character's left = Blender +X |
| `armR` | body | (-0.17, 0, 0.58) | Mirror of armL |
| `legL` | body | (0.085, 0, 0.36) left hip | Short leg + rounded foot pointing toward -Y |
| `legR` | body | (-0.085, 0, 0.36) | Mirror |
| `tail` | body | (0, 0.14, 0.40) back of hip | Species tail (frog/penguin: a tiny nub is fine) |

Anchors (empties named with a leading `@`, model space, rest pose):
- `@hat` at the very top of the skull (where a hat's bottom-centre sits).
- `@hand` at the carry point in front of the chest, about (0, -0.26, 0.55). Stacks start here.
- `@bubble` about 0.25 above the head top (where order bubbles and emotes sit).

Species looks (fur colour fixed):
- corgi: orange + white face, belly and muzzle; big pointed ears; fluffy short tail.
- cat: soft grey with white muzzle; triangle ears; long tail.
- bunny: white; long upright ears with pink insides; pink nose; pom tail.
- bear: brown; round ears; tan muzzle; small round tail.
- panda: white head with black ears and eye-patches; black arms and legs.
- frog: green; eyes are bumps on top of the head (still in the `eyes` part); wide smile line.
- penguin: navy back and head, white face; orange beak and feet; flipper arms.
- fox: orange; white chest and muzzle; black-tipped ears; big bushy tail with a white tip.
- duck: yellow; orange bill and feet; small wing arms.
- raccoon: grey; black eye-mask band; ringed tail (grey/black).

Plus `models/char_mannequin.bvm` is **not** needed. All NPCs use the species models.

## 2. Hats: `models/hat_<name>.bvm` (≤ 600 tris, one part `hat`, pivot = bottom centre at origin)
`toque` (tall puffy white chef hat), `cap` (tint=True crown, fixed visor), `bandana` (tint), `crown` (gold, VIP), `party` (cone, tint, with pom), `helmet` (round astronaut bubble: glass colour + white rim), `beanie` (tint + pom), `cowboy` (brown), `flower` (flower crown), `beret` (tint).
They must fit a head ~0.5 m wide; the hat bottom ring is ~0.36 m diameter.

## 3. Pets: `models/pet_<name>.bvm` (≤ 1,500 tris; parts `body` (pivot at the ground centre) + `head` (child, pivot at the neck); anchor `@bubble`)
`pug` (fawn with black mask, ~0.35 m tall), `parrot` (red/blue/yellow, hops), `hamster` (orange/white ball), `kitten` (calico), `robot` (rounded white/teal bot with one antenna and a screen face, emissive cyan eyes: use `mat(..., emit=1.5)`).

---

## 4. Food & items: `models/food_<name>.bvm` (≤ 700 tris each; one part `item`, pivot at the bottom centre; anchor `@top` = where the next stacked item sits)
These appear in stacks of up to 30, so keep them low-poly but rounded.
| Name | Size (approx.) | Look |
|---|---|---|
| `burger` | Ø0.30, h0.20 | Classic: bun bottom, patty, lettuce wave, bun top with 6–8 sesame seeds |
| `cheese` | same | + square cheese slice with corner drips |
| `double` | Ø0.30, h0.27 | Two patties + cheese |
| `veggie` | same as burger | Green patty, tomato, lettuce, seeded wholegrain bun (darker) |
| `fries` | 0.20×0.14, h0.24 | Red tapered paper cone with ~9 fry sticks fanned out |
| `soda` | Ø0.14, h0.26 | Cup (tint=True body, white lid band) + straw |
| `shake` | Ø0.14, h0.30 | Clear-ish cup, pink shake, whipped cream dome, cherry |
| `tray` | 0.36×0.26, h0.08 | Dirty tray: wrapper balls, empty cup, crumbs (what you clean) |
| `bag` | 0.24×0.16, h0.26 | Paper takeaway bag (kraft colour) with a folded top |
| `coin` | Ø0.16, h0.035 | Chunky gold coin with a raised rim and an embossed bun shape (gold/gold_dark), metal look. Used for the baked `icon_coin` sprite |
| `cash` | 0.22×0.11, h0.047 | Banded brick of green bills (mustard band, tomato stamp, dark ovals). Stacked 2×2 × up to 8 at registers by `WorldView.drawPile` (≤ 400 tris; 256 today) |
| `gem` | 0.14 | Faceted teal gem |
| `patty_raw` | Ø0.24, h0.05 | Pink raw patty (sits on the grill) |

---

## 5. Stations & furniture: `models/<name>.bvm` (one part `root` unless noted; pivot at the floor centre)
Footprints are in grid cells (X × Y in Blender). **Front = Blender -Y** (the side facing the player/customers).
| Name | Footprint | Budget | Anchors | Notes |
|---|---|---|---|---|
| `st_grill` | 1.4×1.0 | 4,000 | `@out` (output tray, front-right top surface), `@cook` (grill plate centre top), `@smoke` (chimney top) | Flat-top griddle station with a hood/chimney, knobs, side tray. Steel + tomato-red body trims |
| `st_fryer` | 1.0×1.0 | 3,500 | `@out`, `@smoke` | Twin fry baskets in an oil well, mustard body |
| `st_soda` | 1.0×0.8 | 3,500 | `@out` | Fountain with 3 levers, cup rack, teal body, translucent-looking dispenser panel |
| `st_shake` | 1.0×0.8 | 3,500 | `@out`, `@spin` | Blender machine with a pink accent and a tall cup on the spindle (part `spinner`, child of root, pivot at the spindle) |
| `counter` | 2.4×0.9, height 0.95 | 4,000 | `@register` (register screen), `@cashier` (floor spot behind the register, +Y side), `@rack0..@rack3` (display rack slots on top, left→right), `@queue` (floor spot in front of the register, -Y side, 0.9 m out) | Long service counter: wood top, cream front with tint=True trim band, cash register at the +X end |
| `drive_window` | 1.6×1.2 | 3,000 | `@rack0`, `@rack1`, `@cashier`, `@car` (where cars stop, -Y side, 1.8 m out) | Kiosk-booth with a sliding window and an awning (tint stripes) |
| `table2` | 1.0×1.0 | 1,200 | `@seat0`, `@seat1` (chair floor spots), `@tray0`, `@tray1` (table top) | Round café table |
| `table4` | 1.6×1.6 | 1,500 | `@seat0..3`, `@tray0..3` | Square booth-style table |
| `chair` | 0.45×0.45 | 600 | – | Seat + back faces tint=True |
| `trash_bin` | 0.6×0.6 | 1,000 | `@in` | Round bin with a flap lid and a little burger icon shape |
| `pad` | Ø1.3 | 800 | – | Unlock "plate" pad: flat white plate, raised rim, tint=True inner disc (the engine draws the dashed progress ring and price) |
| `upgrade_pad` | Ø1.1 | 800 | – | Same style with a fixed up-arrow emboss |
| `staff_pad` | Ø1.1 | 800 | – | Same style with a fixed name-badge emboss |
| `register_stand` | 0.6×0.6 | 800 | – | Small "stand here" floor marker (cashier spot) |

## 6. Building & environment: `models/<name>.bvm`
| Name | Size | Budget | Notes |
|---|---|---|---|
| `floor_wood` | 1×1 tile, 0.04 thick, top at z=0 | 60 | 3 planks; slightly varied wood tones |
| `floor_checker` | 1×1 | 40 | 2×2 checker: cream + tint=True squares |
| `floor_terrazzo` | 1×1 | 80 | cream with a few tiny flat chips |
| `floor_tile` | 1×1 | 40 | Rounded-bevel tile, tint=True |
| `wall` | 1.0 long (along X) × 0.18 thick × 1.2 tall, sitting on z=0 | 300 | Cream wall with a rounded top cap; tint=True trim band at the bottom |
| `wall_window` | same | 500 | Lower wall + glass panel (glass colour) + frame |
| `wall_post` | 0.26×0.26×1.3 | 200 | Rounded corner post (tint cap) |
| `door_frame` | 1.6 wide | 600 | Archway with an awning (tint stripes); open, no door leaf |
| `ground_grass` | 1×1 | 20 | Flat grass tile |
| `ground_sidewalk` | 1×1 | 40 | Light grey slab with a bevel |
| `road` | 2×2 | 40 | Asphalt with a yellow dashed centre line on one axis |
| `crosswalk` | 2×2 | 80 | White stripes |
| `tree_round` | ~1.2×2.4 | 900 | Lollipop tree, 2–3 blobs |
| `tree_pine` | ~1.0×2.6 | 600 | Stacked cones (snow variant `tree_pine_snow` with white caps) |
| `palm` | ~1.4×2.8 | 900 | For the beach |
| `bush` | 0.8 | 400 | |
| `flower_bed` | 1.0×0.5 | 600 | Planter box with coloured blobs |
| `plant_pot` | 0.5 | 600 | Indoor potted plant |
| `lamp_street` | 0.4×3.0 | 500 | Lamp head with an emissive bulb (`emit=2`) |
| `lamp_floor` | 0.4×1.6 | 400 | Indoor lamp, emissive shade |
| `bench` | 1.4×0.5 | 600 | |
| `menu_board` | 1.2 wide | 500 | Abstract coloured blocks + burger silhouette, no text |
| `neon_burger` | 1.0 | 800 | Neon burger outline sign (`emit=3`) |
| `umbrella` | Ø1.6 | 700 | Patio umbrella, tint=True canopy stripes |
| `car` | 1.8×3.6, parts `root` + `wheels` (child, all 4 wheels in one part) | 2,500 | Rounded toy car; body tint=True; windows glass colour |
| `van` | 2.0×4.2 | 2,500 | Food-delivery van; same parts |
| `scooter` | 0.6×1.4 | 1,200 | Delivery scooter with a box (tint) |
| `cloud` | 3.0 | 300 | Puffy low-poly cloud (white, for sky backdrops) |

## 7. Store signature props: `models/store_<store>_<prop>.bvm` (≤ 1,500 tris each, 3 per store)
| Store | Props |
|---|---|
| `wagon` (Bun Wagon, sunny park) | `truck` (food-truck body, tint stripes, ~2×4.5 m), `balloon_stand`, `picnic_table` |
| `seaside` | `surfboard`, `lifeguard_tower`, `beach_chair` |
| `diner` (Retro neon 50s) | `jukebox` (emissive), `stool`, `neon_star` |
| `lodge` (mountain) | `fireplace` (emissive fire), `log_pile`, `snowman` |
| `rooftop` (city night) | `ac_unit`, `water_tower`, `string_lights` (emissive bulbs) |
| `desert` (drive-in) | `cactus`, `cow_skull`, `retro_sign` |
| `tokyo` | `lantern` (emissive), `vending_machine` (emissive panel), `bonsai` |
| `jungle` | `vine_arch`, `tiki_torch` (emissive), `big_leaf` |
| `volcano` | `lava_rock` (emissive cracks), `obsidian_pillar`, `steam_vent` |
| `space` | `console` (emissive), `astro_plant`, `rocket_model` |
| `underwater` | `coral`, `clam`, `bubble_column` (glass colour) |
| `candy` | `lollipop`, `candy_cane`, `gumdrop_tree` |

---

## 7b. World Tour region props: `models/region_<region>_<prop>.bvm` (≤ 1,500 tris each; one part `root`, pivot at the bottom centre)
Built by `tools/blender/regions.py`, 2–3 per region (55 in all), same conventions as the store props.
- Footprint about 1.6 m and height at most 3 m, so they fit the three signature spots by the entrance.
- Regions list them first in their prop pool (`game/TourRegions.kt`), followed by recoloured existing props.
- Some double as a region's tree: `region_blossom_tree`, `region_savanna_acacia`, `region_amber_maple_tree`, `region_frostfest_festive_pine`, `region_springs_bamboo_grove`, `region_fossil_fern_clump`, `region_glade_giant_mushroom`.
- `TourTest.regionModelsExist` fails if a name has no file.

Region dioramas: `assets/ui/region_<region>.png` (480×384), rendered by `tools/blender/render_cards.py region_`. They show the hub/map art for every restaurant of the region; when one is missing, a drawn `RegionBadge` stands in.

Layouts 9–16 (`game/Layouts.kt`) are generated in code like 1–8; no model work is needed.

## 8. Story cast portraits: `assets/ui/cast_<id>.png` (512×512 RGBA)
Rendered by `tools/blender/render_ui.py cast` with the same bust framing as the chef skins, from the game's own character and hat builders. Ids are what `game/Story.kt` `Speaker.portrait` names; when a file is missing the card shows `card_guest_<species>` instead (silently, so check the names).

| Id | Character | Species + hat | Apron / hat tint |
|---|---|---|---|
| `cast_hazel` | Hazel, the mentor | bear + toque | brown / white |
| `cast_fenna` | Fenna, the rival | fox + cap | navy / blue |
| `cast_dot` | Dot, the regular | duck + beanie | mustard / teal |
| `cast_scraps` | Scraps, the raccoon | raccoon + bandana | plum / red |
| `cast_clementine` | Clementine, the critic | cat + beret | white / purple |

Chef Patty uses `skin_patty`.

---

## 9. Audio: `assets/audio/sfx/<name>.wav` and `assets/audio/music/<name>.wav`
SFX: 44.1 kHz mono 16-bit, peak ≈ -1 dBFS, no clicks at start/end (5 ms fades). Music: 22.05 kHz **stereo** 16-bit, seamless loops.
See `tools/audio/README.md` (written by the audio script author) for the final list. The engine expects at least the names in `tools/audio/sfx_list.txt`.
