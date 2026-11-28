# Bun Voyage! — All restaurants

**512 restaurants in total:** the 12 of the Classic Voyage plus the 500 of the World Tour (25 regions × 20). Every one is
built pad by pad and kept forever. This list is generated from the game data (`RestaurantListPrint`, see the end),
so it always matches the code.

| Part | Restaurants | Opens |
|---|---|---|
| Chapter 1: The Classic Voyage (Bun Wagon → Candy Kingdom) | 12 | Bun Wagon from the start; each next one by its NEW RESTAURANT pad |
| The World Tour: 25 regions × 20 | 500 | START THE WORLD TOUR after Candy Kingdom's grand opening; each next one by its NEW RESTAURANT pad, each next region by its flagship's |
| **Total** | **512** | |

**World Tour by size** (every region follows the same 20-stop rhythm, ending in a flagship):

| Size | Per region | Total | Layouts | Typical build (expert bot) |
|---|---|---|---|---|
| Pop-up | 5 | 100 | Pop-up Kiosk, Corner Café, Bun Stand, Corner Grill | ~8 min |
| Diner | 8 | 200 | Classic Diner, Patio Bistro, Garden Terrace, Drive Hub | ~10 min |
| Hall | 6 | 150 | Twin Kitchen, Food Court, Long Counter, Market Hall | ~13 min |
| Grand | 1 | 25 | Mega Diner, Grand Hall | ~17 min |
| Flagship | 1 | 25 | Flagship, Burger Palace | ~19 min |

## The 25 regions

| Region | Name | Restaurants (#) | Host | Perk family | Menu lead | Weather |
|---|---|---|---|---|---|---|
| 1 | [Maple Harbor](#region-1-maple-harbor-20) | 13–32 | Dot | tips | Classic Burger, Fries, Soda | mist |
| 2 | [Blossom Valley](#region-2-blossom-valley-20) | 33–52 | Hazel | patience | Milkshake, Classic Burger, Fries | petals |
| 3 | [Sunbaked Savanna](#region-3-sunbaked-savanna-20) | 53–72 | Fenna | drive | Double Stack, Soda, Fries | dust |
| 4 | [Polar Outpost](#region-4-polar-outpost-20) | 73–92 | Scraps | cook | Cheese Burger, Milkshake, Fries | snow |
| 5 | [Riverboat Delta](#region-5-riverboat-delta-20) | 93–112 | Dot | vip | Veggie Burger, Soda, Classic Burger | rain |
| 6 | [Castle Hill](#region-6-castle-hill-20) | 113–132 | Clementine | tips | Double Stack, Cheese Burger, Soda | none |
| 7 | [Frontier Gulch](#region-7-frontier-gulch-20) | 133–152 | Fenna | drive | Classic Burger, Double Stack, Soda | dust |
| 8 | [Coconut Cove](#region-8-coconut-cove-20) | 153–172 | Dot | patience | Milkshake, Fries, Veggie Burger | hearts |
| 9 | [Alpine Meadow](#region-9-alpine-meadow-20) | 173–192 | Hazel | cook | Cheese Burger, Milkshake, Classic Burger | butterflies |
| 10 | [Pixel Arcade](#region-10-pixel-arcade-20) | 193–212 | Scraps | combo | Soda, Classic Burger, Fries | stars |
| 11 | [Moonlit Hollow](#region-11-moonlit-hollow-20) | 213–232 | Scraps | combo | Milkshake, Veggie Burger, Classic Burger | fireflies |
| 12 | [Funfair Pier](#region-12-funfair-pier-20) | 233–252 | Dot | vip | Milkshake, Soda, Fries | confetti |
| 13 | [Sandstone Ruins](#region-13-sandstone-ruins-20) | 253–272 | Clementine | tips | Double Stack, Veggie Burger, Soda | dust |
| 14 | [Skyreach Isles](#region-14-skyreach-isles-20) | 273–292 | Fenna | carry | Classic Burger, Milkshake, Soda | clouds |
| 15 | [Crystal Caverns](#region-15-crystal-caverns-20) | 293–312 | Scraps | cook | Cheese Burger, Fries, Soda | sparkle |
| 16 | [Orchard Farm](#region-16-orchard-farm-20) | 313–332 | Hazel | patience | Veggie Burger, Classic Burger, Milkshake | butterflies |
| 17 | [Lunar Colony](#region-17-lunar-colony-20) | 333–352 | Fenna | carry | Double Stack, Soda, Milkshake | stars |
| 18 | [Lantern Lagoon](#region-18-lantern-lagoon-20) | 353–372 | Dot | vip | Fries, Veggie Burger, Soda | fireflies |
| 19 | [Amber Woods](#region-19-amber-woods-20) | 373–392 | Hazel | tips | Classic Burger, Cheese Burger, Milkshake | leaves |
| 20 | [Gearworks City](#region-20-gearworks-city-20) | 393–412 | Fenna | staff | Double Stack, Fries, Soda | steam |
| 21 | [Mushroom Glade](#region-21-mushroom-glade-20) | 413–432 | Scraps | pet | Veggie Burger, Milkshake, Fries | fireflies |
| 22 | [Bamboo Springs](#region-22-bamboo-springs-20) | 433–452 | Clementine | patience | Soda, Veggie Burger, Cheese Burger | steam |
| 23 | [Frostfest Village](#region-23-frostfest-village-20) | 453–472 | Hazel | combo | Milkshake, Double Stack, Cheese Burger | snow |
| 24 | [Fossil Valley](#region-24-fossil-valley-20) | 473–492 | Dot | cook | Double Stack, Classic Burger, Fries | embers |
| 25 | [Golden Capital](#region-25-golden-capital-20) | 493–512 | Hazel | all | Double Stack, Cheese Burger, Milkshake | confetti |

Ids are `<region>_<01..20>` (for example `harbor_01`). They are saved, so regions are only ever appended
(`CLAUDE.md` rule 15). Design and tuning: [WORLD_TOUR_DESIGN.md](WORLD_TOUR_DESIGN.md).

---

## Chapter 1: The Classic Voyage (12)

| # | Restaurant | Layout | Pads | Perk |
|---|---|---|---|---|
| 1 | Bun Wagon | Bun Stand | 15 | Your first stand |
| 2 | Seaside Shack | Corner Grill | 20 | +10% tips |
| 3 | Retro Diner | Classic Diner | 26 | +10% patience |
| 4 | Mountain Lodge | Patio Bistro | 27 | +8% cook speed |
| 5 | City Rooftop | Twin Kitchen | 33 | More VIP guests |
| 6 | Desert Drive-In | Food Court | 37 | Drive-thru pays +20% |
| 7 | Tokyo Neon Alley | Mega Diner | 38 | Combo builds faster |
| 8 | Jungle Treehouse | Burger Palace | 42 | Pet ability +50% |
| 9 | Volcano Grill | Burger Palace | 42 | +12% cook speed |
| 10 | Space Station Diner | Burger Palace | 42 | Carry +2 (low gravity) |
| 11 | Underwater Dome | Burger Palace | 42 | +15% patience |
| 12 | Candy Kingdom | Burger Palace | 41 | +20% tips |

## Region 1: Maple Harbor (20)

_A foggy fishing port where the gulls queue too._ Host: Dot · perk family: tips · menu lead: Classic Burger, Fries, Soda · weather: mist

| # | Stop | Restaurant | Size | Layout | Pads | Perk | Time |
|---|---|---|---|---|---|---|---|
| 13 | 1 | Anchor Buns | Pop-up | Pop-up Kiosk | 15 | +8% tips | day |
| 14 | 2 | Pier Pressure Grill | Diner | Patio Bistro | 27 | +9% tips | day |
| 15 | 3 | Lighthouse Grill | Pop-up | Bun Stand | 15 | +9% tips | day |
| 16 | 4 | Foghorn Fries | Diner | Drive Hub | 26 | +10% tips | night |
| 17 | 5 | Tidewater Diner | Hall | Twin Kitchen | 33 | +11% tips | day |
| 18 | 6 | Net & Bun | Pop-up | Corner Café | 15 | +11% tips | day |
| 19 | 7 | Gull's Galley | Diner | Garden Terrace | 28 | +12% tips | day |
| 20 | 8 | Dockside Stack | Hall | Market Hall | 38 | +12% tips | day |
| 21 | 9 | Salt Crate Café | Diner | Classic Diner | 26 | +13% tips | night |
| 22 | 10 | Buoy Burger Bar | Hall | Food Court | 37 | +14% tips | day |
| 23 | 11 | Harbor Lights | Pop-up | Bun Stand | 15 | +14% tips | day |
| 24 | 12 | Rope & Relish | Diner | Drive Hub | 26 | +15% tips | day |
| 25 | 13 | Driftwood Diner | Hall | Twin Kitchen | 33 | +16% tips | day |
| 26 | 14 | Low Tide Bites | Diner | Patio Bistro | 27 | +16% tips | night |
| 27 | 15 | Captain's Counter | Grand | Mega Diner | 38 | +17% tips | day |
| 28 | 16 | Wharf Side Grill | Diner | Drive Hub | 26 | +17% tips | day |
| 29 | 17 | Keel Over Kitchen | Hall | Twin Kitchen | 33 | +18% tips | day |
| 30 | 18 | Mooring Munch | Diner | Patio Bistro | 27 | +19% tips | day |
| 31 | 19 | Fog Bell Bistro | Hall | Long Counter | 31 | +19% tips | night |
| 32 | 20 | Maple Harbor Hall | Flagship | Burger Palace | 42 | +20% tips | day |

## Region 2: Blossom Valley (20)

_Spring orchards, all in pink._ Host: Hazel · perk family: patience · menu lead: Milkshake, Classic Burger, Fries · weather: petals

| # | Stop | Restaurant | Size | Layout | Pads | Perk | Time |
|---|---|---|---|---|---|---|---|
| 33 | 1 | Petal Patties | Pop-up | Corner Café | 15 | +8% patience | day |
| 34 | 2 | Bloom & Bun | Diner | Garden Terrace | 28 | +9% patience | day |
| 35 | 3 | Cherry Stack | Pop-up | Corner Grill | 20 | +9% patience | day |
| 36 | 4 | Pink Petal Grill | Diner | Classic Diner | 26 | +10% patience | day |
| 37 | 5 | Orchard Breeze | Hall | Food Court | 37 | +11% patience | day |
| 38 | 6 | Blossom Booth | Pop-up | Bun Stand | 15 | +11% patience | day |
| 39 | 7 | Spring Sizzle | Diner | Drive Hub | 26 | +12% patience | day |
| 40 | 8 | Kite Hill Kitchen | Hall | Twin Kitchen | 33 | +12% patience | day |
| 41 | 9 | Petalfall Diner | Diner | Patio Bistro | 27 | +13% patience | day |
| 42 | 10 | Rosy Bun Café | Hall | Long Counter | 31 | +14% patience | day |
| 43 | 11 | Pollen & Pickles | Pop-up | Corner Grill | 20 | +14% patience | day |
| 44 | 12 | Bud & Burger | Diner | Classic Diner | 26 | +15% patience | day |
| 45 | 13 | Breezy Blossom | Hall | Food Court | 37 | +16% patience | day |
| 46 | 14 | Branch & Bun | Diner | Garden Terrace | 28 | +16% patience | day |
| 47 | 15 | Petal Path Grill | Grand | Grand Hall | 42 | +17% patience | day |
| 48 | 16 | Springtime Stack | Diner | Classic Diner | 26 | +17% patience | day |
| 49 | 17 | Pink Lantern Grill | Hall | Food Court | 37 | +18% patience | day |
| 50 | 18 | Valley Bloom Bar | Diner | Garden Terrace | 28 | +19% patience | day |
| 51 | 19 | Honeybee Bites | Hall | Market Hall | 38 | +19% patience | day |
| 52 | 20 | Blossom Grand Hall | Flagship | Flagship | 44 | +20% patience | day |

## Region 3: Sunbaked Savanna (20)

_Golden grass, long shadows, big appetites._ Host: Fenna · perk family: drive · menu lead: Double Stack, Soda, Fries · weather: dust

| # | Stop | Restaurant | Size | Layout | Pads | Perk | Time |
|---|---|---|---|---|---|---|---|
| 53 | 1 | Acacia Grill | Pop-up | Bun Stand | 15 | Drive-thru pays +10% | day |
| 54 | 2 | Sunset Safari Stop | Diner | Drive Hub | 26 | Drive-thru pays +11% | day |
| 55 | 3 | Golden Grass Diner | Pop-up | Pop-up Kiosk | 15 | Drive-thru pays +12% | day |
| 56 | 4 | Watering Hole Café | Diner | Patio Bistro | 27 | Drive-thru pays +12% | dusk |
| 57 | 5 | Long Shadow Bites | Hall | Long Counter | 31 | Drive-thru pays +13% | day |
| 58 | 6 | Tall Grass Grill | Pop-up | Corner Grill | 20 | Drive-thru pays +14% | day |
| 59 | 7 | Dusty Trail Diner | Diner | Classic Diner | 26 | Drive-thru pays +15% | day |
| 60 | 8 | Sundowner Stack | Hall | Food Court | 37 | Drive-thru pays +16% | day |
| 61 | 9 | Jeep Stop Burgers | Diner | Garden Terrace | 28 | Drive-thru pays +16% | dusk |
| 62 | 10 | Baobab Bites | Hall | Market Hall | 38 | Drive-thru pays +17% | day |
| 63 | 11 | Amber Plains Café | Pop-up | Pop-up Kiosk | 15 | Drive-thru pays +18% | day |
| 64 | 12 | Heatwave Kitchen | Diner | Patio Bistro | 27 | Drive-thru pays +19% | day |
| 65 | 13 | Drumbeat Diner | Hall | Long Counter | 31 | Drive-thru pays +19% | day |
| 66 | 14 | Horizon Grill | Diner | Drive Hub | 26 | Drive-thru pays +20% | dusk |
| 67 | 15 | Lion's Share Bistro | Grand | Mega Diner | 38 | Drive-thru pays +21% | day |
| 68 | 16 | Savanna Sizzle | Diner | Patio Bistro | 27 | Drive-thru pays +22% | day |
| 69 | 17 | Warm Wind Grill | Hall | Long Counter | 31 | Drive-thru pays +23% | day |
| 70 | 18 | Thornbush Bites | Diner | Drive Hub | 26 | Drive-thru pays +23% | day |
| 71 | 19 | Grassland Galley | Hall | Twin Kitchen | 33 | Drive-thru pays +24% | dusk |
| 72 | 20 | Savanna Grand Lodge | Flagship | Burger Palace | 42 | Drive-thru pays +25% | day |

## Region 4: Polar Outpost (20)

_An arctic research station under the aurora._ Host: Scraps · perk family: cook · menu lead: Cheese Burger, Milkshake, Fries · weather: snow

| # | Stop | Restaurant | Size | Layout | Pads | Perk | Time |
|---|---|---|---|---|---|---|---|
| 73 | 1 | Igloo Grill | Pop-up | Corner Grill | 20 | +6% cook speed | night |
| 74 | 2 | Frostbite Bites | Diner | Classic Diner | 26 | +6% cook speed | night |
| 75 | 3 | Snow Station Café | Pop-up | Corner Café | 15 | +7% cook speed | night |
| 76 | 4 | Aurora Diner | Diner | Garden Terrace | 28 | +7% cook speed | day |
| 77 | 5 | Ice Shelf Stack | Hall | Market Hall | 38 | +8% cook speed | night |
| 78 | 6 | Polar Patty Hut | Pop-up | Pop-up Kiosk | 15 | +8% cook speed | night |
| 79 | 7 | Blizzard Burgers | Diner | Patio Bistro | 27 | +9% cook speed | night |
| 80 | 8 | Glacier Galley | Hall | Long Counter | 31 | +9% cook speed | night |
| 81 | 9 | Northern Glow Grill | Diner | Drive Hub | 26 | +10% cook speed | day |
| 82 | 10 | Penguin Pit Stop | Hall | Twin Kitchen | 33 | +10% cook speed | night |
| 83 | 11 | Snowdrift Diner | Pop-up | Corner Café | 15 | +11% cook speed | night |
| 84 | 12 | Iceberg Bites | Diner | Garden Terrace | 28 | +11% cook speed | night |
| 85 | 13 | Frozen Fries Hut | Hall | Market Hall | 38 | +12% cook speed | night |
| 86 | 14 | Chill Factor Café | Diner | Classic Diner | 26 | +12% cook speed | day |
| 87 | 15 | Arctic Anchor | Grand | Grand Hall | 42 | +13% cook speed | night |
| 88 | 16 | Whiteout Kitchen | Diner | Garden Terrace | 28 | +13% cook speed | night |
| 89 | 17 | Tundra Table | Hall | Market Hall | 38 | +14% cook speed | night |
| 90 | 18 | Sled Stop Snacks | Diner | Classic Diner | 26 | +14% cook speed | night |
| 91 | 19 | Cold Snap Grill | Hall | Food Court | 37 | +15% cook speed | day |
| 92 | 20 | Polar Grand Station | Flagship | Flagship | 44 | +15% cook speed | night |

## Region 5: Riverboat Delta (20)

_A rainforest river town, rain on the roofs._ Host: Dot · perk family: vip · menu lead: Veggie Burger, Soda, Classic Burger · weather: rain

| # | Stop | Restaurant | Size | Layout | Pads | Perk | Time |
|---|---|---|---|---|---|---|---|
| 93 | 1 | Paddlewheel Grill | Pop-up | Pop-up Kiosk | 15 | +2% VIP guests | day |
| 94 | 2 | Mangrove Munch | Diner | Patio Bistro | 27 | +2% VIP guests | day |
| 95 | 3 | River Bend Diner | Pop-up | Bun Stand | 15 | +2% VIP guests | day |
| 96 | 4 | Lily Pad Café | Diner | Drive Hub | 26 | +2% VIP guests | night |
| 97 | 5 | Delta Dock Bites | Hall | Twin Kitchen | 33 | +3% VIP guests | day |
| 98 | 6 | Monsoon Kitchen | Pop-up | Corner Café | 15 | +3% VIP guests | day |
| 99 | 7 | Canopy Stack | Diner | Garden Terrace | 28 | +3% VIP guests | day |
| 100 | 8 | Muddy Boots Diner | Hall | Market Hall | 38 | +3% VIP guests | day |
| 101 | 9 | Rainleaf Grill | Diner | Classic Diner | 26 | +3% VIP guests | night |
| 102 | 10 | Ferry Stop Burgers | Hall | Food Court | 37 | +3% VIP guests | day |
| 103 | 11 | Backwater Bites | Pop-up | Bun Stand | 15 | +4% VIP guests | day |
| 104 | 12 | Riverside Galley | Diner | Drive Hub | 26 | +4% VIP guests | day |
| 105 | 13 | Fern Bank Café | Hall | Twin Kitchen | 33 | +4% VIP guests | day |
| 106 | 14 | Drizzle & Bun | Diner | Patio Bistro | 27 | +4% VIP guests | night |
| 107 | 15 | Treeline Table | Grand | Mega Diner | 38 | +4% VIP guests | day |
| 108 | 16 | Croaky Corner | Diner | Drive Hub | 26 | +4% VIP guests | day |
| 109 | 17 | Wet Season Grill | Hall | Twin Kitchen | 33 | +5% VIP guests | day |
| 110 | 18 | Current Café | Diner | Patio Bistro | 27 | +5% VIP guests | day |
| 111 | 19 | Rapids Rest Stop | Hall | Long Counter | 31 | +5% VIP guests | night |
| 112 | 20 | Delta Grand Steamer | Flagship | Burger Palace | 42 | +5% VIP guests | day |

## Region 6: Castle Hill (20)

_A hilltop market town under old towers._ Host: Clementine · perk family: tips · menu lead: Double Stack, Cheese Burger, Soda · weather: none

| # | Stop | Restaurant | Size | Layout | Pads | Perk | Time |
|---|---|---|---|---|---|---|---|
| 113 | 1 | Drawbridge Diner | Pop-up | Corner Café | 15 | +8% tips | day |
| 114 | 2 | Turret Burgers | Diner | Garden Terrace | 28 | +9% tips | day |
| 115 | 3 | Moat Side Grill | Pop-up | Corner Grill | 20 | +9% tips | day |
| 116 | 4 | Cobble Corner Café | Diner | Classic Diner | 26 | +10% tips | night |
| 117 | 5 | Banner Hall Bites | Hall | Food Court | 37 | +11% tips | day |
| 118 | 6 | Market Square Stack | Pop-up | Bun Stand | 15 | +11% tips | day |
| 119 | 7 | Knight Shift Grill | Diner | Drive Hub | 26 | +12% tips | day |
| 120 | 8 | Crest & Crumb | Hall | Twin Kitchen | 33 | +12% tips | day |
| 121 | 9 | Gatehouse Galley | Diner | Patio Bistro | 27 | +13% tips | night |
| 122 | 10 | Tower Top Table | Hall | Long Counter | 31 | +14% tips | day |
| 123 | 11 | Royal Relish | Pop-up | Corner Grill | 20 | +14% tips | day |
| 124 | 12 | Battlement Bites | Diner | Classic Diner | 26 | +15% tips | day |
| 125 | 13 | Lantern Lane Diner | Hall | Food Court | 37 | +16% tips | day |
| 126 | 14 | Old Keep Kitchen | Diner | Garden Terrace | 28 | +16% tips | night |
| 127 | 15 | Herald's Hearth | Grand | Grand Hall | 42 | +17% tips | day |
| 128 | 16 | Squire's Snacks | Diner | Classic Diner | 26 | +17% tips | day |
| 129 | 17 | Archway Grill | Hall | Food Court | 37 | +18% tips | day |
| 130 | 18 | Crown Court Café | Diner | Garden Terrace | 28 | +19% tips | day |
| 131 | 19 | Hilltop Hearth | Hall | Market Hall | 38 | +19% tips | night |
| 132 | 20 | The Great Hall | Flagship | Flagship | 44 | +20% tips | day |

## Region 7: Frontier Gulch (20)

_A dusty boomtown where the fastest window wins._ Host: Fenna · perk family: drive · menu lead: Classic Burger, Double Stack, Soda · weather: dust

| # | Stop | Restaurant | Size | Layout | Pads | Perk | Time |
|---|---|---|---|---|---|---|---|
| 133 | 1 | Saloon Sliders | Pop-up | Bun Stand | 15 | Drive-thru pays +10% | day |
| 134 | 2 | Tumbleweed Grill | Diner | Drive Hub | 26 | Drive-thru pays +11% | day |
| 135 | 3 | Dust Devil Diner | Pop-up | Pop-up Kiosk | 15 | Drive-thru pays +12% | day |
| 136 | 4 | Horseshoe Hut | Diner | Patio Bistro | 27 | Drive-thru pays +12% | night |
| 137 | 5 | Gold Rush Grill | Hall | Long Counter | 31 | Drive-thru pays +13% | day |
| 138 | 6 | Canyon Corner | Pop-up | Corner Grill | 20 | Drive-thru pays +14% | day |
| 139 | 7 | Rattle & Relish | Diner | Classic Diner | 26 | Drive-thru pays +15% | day |
| 140 | 8 | Hay Bale Bites | Hall | Food Court | 37 | Drive-thru pays +16% | day |
| 141 | 9 | Boomtown Burgers | Diner | Garden Terrace | 28 | Drive-thru pays +16% | night |
| 142 | 10 | Sunset Stage Stop | Hall | Market Hall | 38 | Drive-thru pays +17% | day |
| 143 | 11 | Lasso Lunch | Pop-up | Pop-up Kiosk | 15 | Drive-thru pays +18% | day |
| 144 | 12 | Spur & Spatula | Diner | Patio Bistro | 27 | Drive-thru pays +19% | day |
| 145 | 13 | Mesa Grill | Hall | Long Counter | 31 | Drive-thru pays +19% | day |
| 146 | 14 | Dry Creek Diner | Diner | Drive Hub | 26 | Drive-thru pays +20% | night |
| 147 | 15 | Wheel & Whistle | Grand | Mega Diner | 38 | Drive-thru pays +21% | day |
| 148 | 16 | Copper Kettle Café | Diner | Patio Bistro | 27 | Drive-thru pays +22% | day |
| 149 | 17 | Prospector's Pantry | Hall | Long Counter | 31 | Drive-thru pays +23% | day |
| 150 | 18 | Red Rock Stack | Diner | Drive Hub | 26 | Drive-thru pays +23% | day |
| 151 | 19 | High Noon Grill | Hall | Twin Kitchen | 33 | Drive-thru pays +24% | night |
| 152 | 20 | Gulch Grand Hotel | Flagship | Burger Palace | 42 | Drive-thru pays +25% | day |

## Region 8: Coconut Cove (20)

_An island village on turquoise water._ Host: Dot · perk family: patience · menu lead: Milkshake, Fries, Veggie Burger · weather: hearts

| # | Stop | Restaurant | Size | Layout | Pads | Perk | Time |
|---|---|---|---|---|---|---|---|
| 153 | 1 | Coconut Shack | Pop-up | Corner Grill | 20 | +8% patience | day |
| 154 | 2 | Hammock Grill | Diner | Classic Diner | 26 | +9% patience | day |
| 155 | 3 | Palm Shade Café | Pop-up | Corner Café | 15 | +9% patience | day |
| 156 | 4 | Treasure Bites | Diner | Garden Terrace | 28 | +10% patience | dusk |
| 157 | 5 | Cove Corner Grill | Hall | Market Hall | 38 | +11% patience | day |
| 158 | 6 | Island Hopper | Pop-up | Pop-up Kiosk | 15 | +11% patience | day |
| 159 | 7 | Seashell Sliders | Diner | Patio Bistro | 27 | +12% patience | day |
| 160 | 8 | Sandbar Stack | Hall | Long Counter | 31 | +12% patience | day |
| 161 | 9 | Breezy Bay Diner | Diner | Drive Hub | 26 | +13% patience | dusk |
| 162 | 10 | Driftnet Diner | Hall | Twin Kitchen | 33 | +14% patience | day |
| 163 | 11 | Shipwreck Snacks | Pop-up | Corner Café | 15 | +14% patience | day |
| 164 | 12 | Reef Break Grill | Diner | Garden Terrace | 28 | +15% patience | day |
| 165 | 13 | Conch Corner | Hall | Market Hall | 38 | +16% patience | day |
| 166 | 14 | Sunny Isle Café | Diner | Classic Diner | 26 | +16% patience | dusk |
| 167 | 15 | Pineapple Patties | Grand | Grand Hall | 42 | +17% patience | day |
| 168 | 16 | Castaway Kitchen | Diner | Garden Terrace | 28 | +17% patience | day |
| 169 | 17 | Flip-Flop Grill | Hall | Market Hall | 38 | +18% patience | day |
| 170 | 18 | Low Sun Lounge | Diner | Classic Diner | 26 | +19% patience | day |
| 171 | 19 | Tidepool Tables | Hall | Food Court | 37 | +19% patience | dusk |
| 172 | 20 | Cove Grand Pavilion | Flagship | Flagship | 44 | +20% patience | day |

## Region 9: Alpine Meadow (20)

_A mountain dairy village with cowbells._ Host: Hazel · perk family: cook · menu lead: Cheese Burger, Milkshake, Classic Burger · weather: butterflies

| # | Stop | Restaurant | Size | Layout | Pads | Perk | Time |
|---|---|---|---|---|---|---|---|
| 173 | 1 | Chalet Burgers | Pop-up | Pop-up Kiosk | 15 | +6% cook speed | day |
| 174 | 2 | Cowbell Café | Diner | Patio Bistro | 27 | +6% cook speed | day |
| 175 | 3 | Edelweiss Grill | Pop-up | Bun Stand | 15 | +7% cook speed | day |
| 176 | 4 | Hayloft Grill | Diner | Drive Hub | 26 | +7% cook speed | night |
| 177 | 5 | Yodel Diner | Hall | Twin Kitchen | 33 | +8% cook speed | day |
| 178 | 6 | Mountain Dairy Bar | Pop-up | Corner Café | 15 | +8% cook speed | day |
| 179 | 7 | Summit Stack | Diner | Garden Terrace | 28 | +9% cook speed | day |
| 180 | 8 | Pine Ridge Grill | Hall | Market Hall | 38 | +9% cook speed | day |
| 181 | 9 | Cheese Wheel Café | Diner | Classic Diner | 26 | +10% cook speed | night |
| 182 | 10 | Valley View Diner | Hall | Food Court | 37 | +10% cook speed | day |
| 183 | 11 | Trailhead Bites | Pop-up | Bun Stand | 15 | +11% cook speed | day |
| 184 | 12 | Butter Churn Bistro | Diner | Drive Hub | 26 | +11% cook speed | day |
| 185 | 13 | Cable Car Café | Hall | Twin Kitchen | 33 | +12% cook speed | day |
| 186 | 14 | Goat Path Grill | Diner | Patio Bistro | 27 | +12% cook speed | night |
| 187 | 15 | Snowcap Snacks | Grand | Mega Diner | 38 | +13% cook speed | day |
| 188 | 16 | Alpenglow Kitchen | Diner | Drive Hub | 26 | +13% cook speed | day |
| 189 | 17 | High Pasture Grill | Hall | Twin Kitchen | 33 | +14% cook speed | day |
| 190 | 18 | Woodcarver's Café | Diner | Patio Bistro | 27 | +14% cook speed | day |
| 191 | 19 | Bell Tower Bites | Hall | Long Counter | 31 | +15% cook speed | night |
| 192 | 20 | Alpine Grand Chalet | Flagship | Burger Palace | 42 | +15% cook speed | day |

## Region 10: Pixel Arcade (20)

_A retro arcade street that never sleeps._ Host: Scraps · perk family: combo · menu lead: Soda, Classic Burger, Fries · weather: stars

| # | Stop | Restaurant | Size | Layout | Pads | Perk | Time |
|---|---|---|---|---|---|---|---|
| 193 | 1 | Pixel Patties | Pop-up | Corner Café | 15 | Combo builds 15% faster | night |
| 194 | 2 | Joystick Grill | Diner | Garden Terrace | 28 | Combo builds 16% faster | night |
| 195 | 3 | High Score Diner | Pop-up | Corner Grill | 20 | Combo builds 17% faster | night |
| 196 | 4 | Coin-Op Café | Diner | Classic Diner | 26 | Combo builds 17% faster | night |
| 197 | 5 | 8-Bit Bites | Hall | Food Court | 37 | Combo builds 18% faster | night |
| 198 | 6 | Bonus Round Burgers | Pop-up | Bun Stand | 15 | Combo builds 19% faster | night |
| 199 | 7 | Insert Coin Kitchen | Diner | Drive Hub | 26 | Combo builds 20% faster | night |
| 200 | 8 | Power-Up Stack | Hall | Twin Kitchen | 33 | Combo builds 21% faster | night |
| 201 | 9 | Glitch Grill | Diner | Patio Bistro | 27 | Combo builds 21% faster | night |
| 202 | 10 | Level Up Lounge | Hall | Long Counter | 31 | Combo builds 22% faster | night |
| 203 | 11 | Combo Counter | Pop-up | Corner Grill | 20 | Combo builds 23% faster | night |
| 204 | 12 | Button Mash Bites | Diner | Classic Diner | 26 | Combo builds 24% faster | night |
| 205 | 13 | Arcade Alley Diner | Hall | Food Court | 37 | Combo builds 24% faster | night |
| 206 | 14 | Neon Sprite Café | Diner | Garden Terrace | 28 | Combo builds 25% faster | night |
| 207 | 15 | Respawn Grill | Grand | Grand Hall | 42 | Combo builds 26% faster | night |
| 208 | 16 | Pause Menu Munch | Diner | Classic Diner | 26 | Combo builds 27% faster | night |
| 209 | 17 | Extra Life Eatery | Hall | Food Court | 37 | Combo builds 28% faster | night |
| 210 | 18 | Boss Level Burgers | Diner | Garden Terrace | 28 | Combo builds 28% faster | night |
| 211 | 19 | Retro Glow Grill | Hall | Market Hall | 38 | Combo builds 29% faster | night |
| 212 | 20 | Grand Arcade Hall | Flagship | Flagship | 44 | Combo builds 30% faster | night |

## Region 11: Moonlit Hollow (20)

_A friendly spooky village of pumpkins and lanterns._ Host: Scraps · perk family: combo · menu lead: Milkshake, Veggie Burger, Classic Burger · weather: fireflies

| # | Stop | Restaurant | Size | Layout | Pads | Perk | Time |
|---|---|---|---|---|---|---|---|
| 213 | 1 | Pumpkin Patch Grill | Pop-up | Bun Stand | 15 | Combo builds 15% faster | night |
| 214 | 2 | Hollow Moon Café | Diner | Drive Hub | 26 | Combo builds 16% faster | night |
| 215 | 3 | Lantern Glow Diner | Pop-up | Pop-up Kiosk | 15 | Combo builds 17% faster | night |
| 216 | 4 | Creaky Gate Grill | Diner | Patio Bistro | 27 | Combo builds 17% faster | dusk |
| 217 | 5 | Whisper Woods Café | Hall | Long Counter | 31 | Combo builds 18% faster | night |
| 218 | 6 | Midnight Munch | Pop-up | Corner Grill | 20 | Combo builds 19% faster | night |
| 219 | 7 | Batwing Bites | Diner | Classic Diner | 26 | Combo builds 20% faster | night |
| 220 | 8 | Spooky Spoon | Hall | Food Court | 37 | Combo builds 21% faster | night |
| 221 | 9 | Owl Hoot Diner | Diner | Garden Terrace | 28 | Combo builds 21% faster | dusk |
| 222 | 10 | Misty Moor Grill | Hall | Market Hall | 38 | Combo builds 22% faster | night |
| 223 | 11 | Candlewick Café | Pop-up | Pop-up Kiosk | 15 | Combo builds 23% faster | night |
| 224 | 12 | Full Moon Patties | Diner | Patio Bistro | 27 | Combo builds 24% faster | night |
| 225 | 13 | Cauldron Kitchen | Hall | Long Counter | 31 | Combo builds 24% faster | night |
| 226 | 14 | Moonbeam Stack | Diner | Drive Hub | 26 | Combo builds 25% faster | dusk |
| 227 | 15 | Shadow Lane Snacks | Grand | Mega Diner | 38 | Combo builds 26% faster | night |
| 228 | 16 | Cobweb Corner | Diner | Patio Bistro | 27 | Combo builds 27% faster | night |
| 229 | 17 | Hollow Tree Grill | Hall | Long Counter | 31 | Combo builds 28% faster | night |
| 230 | 18 | Night Shift Grill | Diner | Drive Hub | 26 | Combo builds 28% faster | night |
| 231 | 19 | Twilight Table | Hall | Twin Kitchen | 33 | Combo builds 29% faster | dusk |
| 232 | 20 | Hollow Grand Manor | Flagship | Burger Palace | 42 | Combo builds 30% faster | night |

## Region 12: Funfair Pier (20)

_A seaside carnival with rides and prizes._ Host: Dot · perk family: vip · menu lead: Milkshake, Soda, Fries · weather: confetti

| # | Stop | Restaurant | Size | Layout | Pads | Perk | Time |
|---|---|---|---|---|---|---|---|
| 233 | 1 | Ferris Fries | Pop-up | Corner Grill | 20 | +2% VIP guests | day |
| 234 | 2 | Carousel Café | Diner | Classic Diner | 26 | +2% VIP guests | day |
| 235 | 3 | Big Top Burgers | Pop-up | Corner Café | 15 | +2% VIP guests | day |
| 236 | 4 | Ticket Booth Bites | Diner | Garden Terrace | 28 | +2% VIP guests | night |
| 237 | 5 | Ring Toss Grill | Hall | Market Hall | 38 | +3% VIP guests | day |
| 238 | 6 | Candy Floss Café | Pop-up | Pop-up Kiosk | 15 | +3% VIP guests | day |
| 239 | 7 | Midway Munch | Diner | Patio Bistro | 27 | +3% VIP guests | day |
| 240 | 8 | Roller Coaster Grill | Hall | Long Counter | 31 | +3% VIP guests | day |
| 241 | 9 | Prize Wheel Diner | Diner | Drive Hub | 26 | +3% VIP guests | night |
| 242 | 10 | Bumper Car Bites | Hall | Twin Kitchen | 33 | +3% VIP guests | day |
| 243 | 11 | Balloon Pop Café | Pop-up | Corner Café | 15 | +4% VIP guests | day |
| 244 | 12 | Boardwalk Burgers | Diner | Garden Terrace | 28 | +4% VIP guests | day |
| 245 | 13 | Pier End Grill | Hall | Market Hall | 38 | +4% VIP guests | day |
| 246 | 14 | Popcorn Patties | Diner | Classic Diner | 26 | +4% VIP guests | night |
| 247 | 15 | Helter Stack | Grand | Grand Hall | 42 | +4% VIP guests | day |
| 248 | 16 | Fun House Fries | Diner | Garden Terrace | 28 | +4% VIP guests | day |
| 249 | 17 | Lucky Dip Diner | Hall | Market Hall | 38 | +5% VIP guests | day |
| 250 | 18 | Showtime Stack | Diner | Classic Diner | 26 | +5% VIP guests | day |
| 251 | 19 | Sea Breeze Grill | Hall | Food Court | 37 | +5% VIP guests | night |
| 252 | 20 | Grand Funfair Hall | Flagship | Flagship | 44 | +5% VIP guests | day |

## Region 13: Sandstone Ruins (20)

_Ancient desert temples with one very old oven._ Host: Clementine · perk family: tips · menu lead: Double Stack, Veggie Burger, Soda · weather: dust

| # | Stop | Restaurant | Size | Layout | Pads | Perk | Time |
|---|---|---|---|---|---|---|---|
| 253 | 1 | Pillar Patties | Pop-up | Pop-up Kiosk | 15 | +8% tips | day |
| 254 | 2 | Old Temple Grill | Diner | Patio Bistro | 27 | +9% tips | day |
| 255 | 3 | Sun Dial Diner | Pop-up | Bun Stand | 15 | +9% tips | day |
| 256 | 4 | Sandstone Stack | Diner | Drive Hub | 26 | +10% tips | night |
| 257 | 5 | Oasis Café | Hall | Twin Kitchen | 33 | +11% tips | day |
| 258 | 6 | Carved Stone Café | Pop-up | Corner Café | 15 | +11% tips | day |
| 259 | 7 | Dune Top Diner | Diner | Garden Terrace | 28 | +12% tips | day |
| 260 | 8 | Lost Relic Grill | Hall | Market Hall | 38 | +12% tips | day |
| 261 | 9 | Glyph Wall Grill | Diner | Classic Diner | 26 | +13% tips | night |
| 262 | 10 | Urn & Bun | Hall | Food Court | 37 | +14% tips | day |
| 263 | 11 | Golden Sand Grill | Pop-up | Bun Stand | 15 | +14% tips | day |
| 264 | 12 | Explorer's Rest | Diner | Drive Hub | 26 | +15% tips | day |
| 265 | 13 | Ancient Oven | Hall | Twin Kitchen | 33 | +16% tips | day |
| 266 | 14 | Mirage Munch | Diner | Patio Bistro | 27 | +16% tips | night |
| 267 | 15 | Desert Star Diner | Grand | Mega Diner | 38 | +17% tips | day |
| 268 | 16 | Temple Steps Café | Diner | Drive Hub | 26 | +17% tips | day |
| 269 | 17 | Sunstone Sizzle | Hall | Twin Kitchen | 33 | +18% tips | day |
| 270 | 18 | Courtyard Kitchen | Diner | Patio Bistro | 27 | +19% tips | day |
| 271 | 19 | Scroll & Spatula | Hall | Long Counter | 31 | +19% tips | night |
| 272 | 20 | Ruins Grand Temple | Flagship | Burger Palace | 42 | +20% tips | day |

## Region 14: Skyreach Isles (20)

_Floating islands where the wind delivers._ Host: Fenna · perk family: carry · menu lead: Classic Burger, Milkshake, Soda · weather: clouds

| # | Stop | Restaurant | Size | Layout | Pads | Perk | Time |
|---|---|---|---|---|---|---|---|
| 273 | 1 | Cloud Nine Grill | Pop-up | Corner Café | 15 | Carry +1 | day |
| 274 | 2 | Windmill Burgers | Diner | Garden Terrace | 28 | Carry +1 | day |
| 275 | 3 | Updraft Diner | Pop-up | Corner Grill | 20 | Carry +1 | day |
| 276 | 4 | Sky Dock Café | Diner | Classic Diner | 26 | Carry +1 | dusk |
| 277 | 5 | Balloon Basket Bites | Hall | Food Court | 37 | Carry +1 | day |
| 278 | 6 | Floating Fries | Pop-up | Bun Stand | 15 | Carry +1 | day |
| 279 | 7 | High Wind Grill | Diner | Drive Hub | 26 | Carry +1 | day |
| 280 | 8 | Nimbus Kitchen | Hall | Twin Kitchen | 33 | Carry +1 | day |
| 281 | 9 | Drift Isle Diner | Diner | Patio Bistro | 27 | Carry +1 | dusk |
| 282 | 10 | Breeze Block Burgers | Hall | Long Counter | 31 | Carry +1 | day |
| 283 | 11 | Skyline Stack | Pop-up | Corner Grill | 20 | Carry +2 | day |
| 284 | 12 | Feather Light Café | Diner | Classic Diner | 26 | Carry +2 | day |
| 285 | 13 | Gust & Bun | Hall | Food Court | 37 | Carry +2 | day |
| 286 | 14 | Rainbow Span Grill | Diner | Garden Terrace | 28 | Carry +2 | dusk |
| 287 | 15 | Sunshower Café | Grand | Grand Hall | 42 | Carry +2 | day |
| 288 | 16 | Kite String Kitchen | Diner | Classic Diner | 26 | Carry +2 | day |
| 289 | 17 | Horizon Hop | Hall | Food Court | 37 | Carry +2 | day |
| 290 | 18 | Stratus Snacks | Diner | Garden Terrace | 28 | Carry +2 | day |
| 291 | 19 | Cumulus Café | Hall | Market Hall | 38 | Carry +2 | dusk |
| 292 | 20 | Skyreach Grand Port | Flagship | Flagship | 44 | Carry +2 | day |

## Region 15: Crystal Caverns (20)

_Glowing caves full of hungry miners._ Host: Scraps · perk family: cook · menu lead: Cheese Burger, Fries, Soda · weather: sparkle

| # | Stop | Restaurant | Size | Layout | Pads | Perk | Time |
|---|---|---|---|---|---|---|---|
| 293 | 1 | Crystal Cave Grill | Pop-up | Bun Stand | 15 | +6% cook speed | night |
| 294 | 2 | Glowstone Grill | Diner | Drive Hub | 26 | +6% cook speed | night |
| 295 | 3 | Mine Cart Munch | Pop-up | Pop-up Kiosk | 15 | +7% cook speed | night |
| 296 | 4 | Gem Vein Diner | Diner | Patio Bistro | 27 | +7% cook speed | night |
| 297 | 5 | Echo Chamber Café | Hall | Long Counter | 31 | +8% cook speed | night |
| 298 | 6 | Stalactite Stack | Pop-up | Corner Grill | 20 | +8% cook speed | night |
| 299 | 7 | Lantern Mine Café | Diner | Classic Diner | 26 | +9% cook speed | night |
| 300 | 8 | Geode Grill | Hall | Food Court | 37 | +9% cook speed | night |
| 301 | 9 | Deep Shaft Diner | Diner | Garden Terrace | 28 | +10% cook speed | night |
| 302 | 10 | Amethyst Bites | Hall | Market Hall | 38 | +10% cook speed | night |
| 303 | 11 | Pickaxe Patties | Pop-up | Pop-up Kiosk | 15 | +11% cook speed | night |
| 304 | 12 | Underground Oven | Diner | Patio Bistro | 27 | +11% cook speed | night |
| 305 | 13 | Quartz Corner | Hall | Long Counter | 31 | +12% cook speed | night |
| 306 | 14 | Glow Worm Grill | Diner | Drive Hub | 26 | +12% cook speed | night |
| 307 | 15 | Cavern Cove Café | Grand | Mega Diner | 38 | +13% cook speed | night |
| 308 | 16 | Shimmer Shaft Stack | Diner | Patio Bistro | 27 | +13% cook speed | night |
| 309 | 17 | Rocky Ledge Diner | Hall | Long Counter | 31 | +14% cook speed | night |
| 310 | 18 | Boulder Burgers | Diner | Drive Hub | 26 | +14% cook speed | night |
| 311 | 19 | Sparkle Seam Grill | Hall | Twin Kitchen | 33 | +15% cook speed | night |
| 312 | 20 | Caverns Grand Hall | Flagship | Burger Palace | 42 | +15% cook speed | night |

## Region 16: Orchard Farm (20)

_Barns, hay and apples as far as you can see._ Host: Hazel · perk family: patience · menu lead: Veggie Burger, Classic Burger, Milkshake · weather: butterflies

| # | Stop | Restaurant | Size | Layout | Pads | Perk | Time |
|---|---|---|---|---|---|---|---|
| 313 | 1 | Red Barn Burgers | Pop-up | Corner Grill | 20 | +8% patience | day |
| 314 | 2 | Haystack Grill | Diner | Classic Diner | 26 | +9% patience | day |
| 315 | 3 | Apple Cart Café | Pop-up | Corner Café | 15 | +9% patience | day |
| 316 | 4 | Tractor Stop Diner | Diner | Garden Terrace | 28 | +10% patience | dusk |
| 317 | 5 | Farmhouse Kitchen | Hall | Market Hall | 38 | +11% patience | day |
| 318 | 6 | Corn Row Café | Pop-up | Pop-up Kiosk | 15 | +11% patience | day |
| 319 | 7 | Silo Snacks | Diner | Patio Bistro | 27 | +12% patience | day |
| 320 | 8 | Berry Patch Bites | Hall | Long Counter | 31 | +12% patience | day |
| 321 | 9 | Henhouse Grill | Diner | Drive Hub | 26 | +13% patience | dusk |
| 322 | 10 | Windvane Diner | Hall | Twin Kitchen | 33 | +14% patience | day |
| 323 | 11 | Fresh Field Café | Pop-up | Corner Café | 15 | +14% patience | day |
| 324 | 12 | Muddy Paws Diner | Diner | Garden Terrace | 28 | +15% patience | day |
| 325 | 13 | Orchard Row Grill | Hall | Market Hall | 38 | +16% patience | day |
| 326 | 14 | Milk Pail Café | Diner | Classic Diner | 26 | +16% patience | dusk |
| 327 | 15 | Picket Fence Grill | Grand | Grand Hall | 42 | +17% patience | day |
| 328 | 16 | Sunflower Stack | Diner | Garden Terrace | 28 | +17% patience | day |
| 329 | 17 | Hayride Burgers | Hall | Market Hall | 38 | +18% patience | day |
| 330 | 18 | Country Lane Café | Diner | Classic Diner | 26 | +19% patience | day |
| 331 | 19 | Harvest Table | Hall | Food Court | 37 | +19% patience | dusk |
| 332 | 20 | Grand Barn Hall | Flagship | Flagship | 44 | +20% patience | day |

## Region 17: Lunar Colony (20)

_Moon domes, moon dust and very hungry pilots._ Host: Fenna · perk family: carry · menu lead: Double Stack, Soda, Milkshake · weather: stars

| # | Stop | Restaurant | Size | Layout | Pads | Perk | Time |
|---|---|---|---|---|---|---|---|
| 333 | 1 | Crater Grill | Pop-up | Pop-up Kiosk | 15 | Carry +1 | night |
| 334 | 2 | Moon Rover Diner | Diner | Patio Bistro | 27 | Carry +1 | night |
| 335 | 3 | Dome Sweet Dome | Pop-up | Bun Stand | 15 | Carry +1 | night |
| 336 | 4 | Low-G Grill | Diner | Drive Hub | 26 | Carry +1 | night |
| 337 | 5 | Lunar Lander Café | Hall | Twin Kitchen | 33 | Carry +1 | night |
| 338 | 6 | Dust Bowl Diner | Pop-up | Corner Café | 15 | Carry +1 | night |
| 339 | 7 | Orbit Stack | Diner | Garden Terrace | 28 | Carry +1 | night |
| 340 | 8 | Eclipse Café | Hall | Market Hall | 38 | Carry +1 | night |
| 341 | 9 | Starport Burgers | Diner | Classic Diner | 26 | Carry +1 | night |
| 342 | 10 | Moonwalk Munch | Hall | Food Court | 37 | Carry +1 | night |
| 343 | 11 | Quiet Sea Café | Pop-up | Bun Stand | 15 | Carry +2 | night |
| 344 | 12 | Regolith Grill | Diner | Drive Hub | 26 | Carry +2 | night |
| 345 | 13 | Rocket Fuel Fries | Hall | Twin Kitchen | 33 | Carry +2 | night |
| 346 | 14 | Gravity Well Diner | Diner | Patio Bistro | 27 | Carry +2 | night |
| 347 | 15 | Airlock Bites | Grand | Mega Diner | 38 | Carry +2 | night |
| 348 | 16 | Crescent Kitchen | Diner | Drive Hub | 26 | Carry +2 | night |
| 349 | 17 | Space Spud Stack | Hall | Twin Kitchen | 33 | Carry +2 | night |
| 350 | 18 | Silver Dust Grill | Diner | Patio Bistro | 27 | Carry +2 | night |
| 351 | 19 | Moonrise Diner | Hall | Long Counter | 31 | Carry +2 | night |
| 352 | 20 | Lunar Grand Base | Flagship | Burger Palace | 42 | Carry +2 | night |

## Region 18: Lantern Lagoon (20)

_A floating night market on calm water._ Host: Dot · perk family: vip · menu lead: Fries, Veggie Burger, Soda · weather: fireflies

| # | Stop | Restaurant | Size | Layout | Pads | Perk | Time |
|---|---|---|---|---|---|---|---|
| 353 | 1 | Lantern Float Café | Pop-up | Corner Café | 15 | +2% VIP guests | night |
| 354 | 2 | Canoe Corner | Diner | Garden Terrace | 28 | +2% VIP guests | night |
| 355 | 3 | Lagoon Lights Grill | Pop-up | Corner Grill | 20 | +2% VIP guests | night |
| 356 | 4 | Night Market Bites | Diner | Classic Diner | 26 | +2% VIP guests | dusk |
| 357 | 5 | Paper Lantern Diner | Hall | Food Court | 37 | +3% VIP guests | night |
| 358 | 6 | Ripple Grill | Pop-up | Bun Stand | 15 | +3% VIP guests | night |
| 359 | 7 | Raft Row Café | Diner | Drive Hub | 26 | +3% VIP guests | night |
| 360 | 8 | Moonwater Stack | Hall | Twin Kitchen | 33 | +3% VIP guests | night |
| 361 | 9 | Reed Boat Bites | Diner | Patio Bistro | 27 | +3% VIP guests | dusk |
| 362 | 10 | Glowfish Grill | Hall | Long Counter | 31 | +3% VIP guests | night |
| 363 | 11 | Stilt House Diner | Pop-up | Corner Grill | 20 | +4% VIP guests | night |
| 364 | 12 | Dock Lantern Café | Diner | Classic Diner | 26 | +4% VIP guests | night |
| 365 | 13 | Firefly Raft Grill | Hall | Food Court | 37 | +4% VIP guests | night |
| 366 | 14 | Still Water Kitchen | Diner | Garden Terrace | 28 | +4% VIP guests | dusk |
| 367 | 15 | Starlit Pier | Grand | Grand Hall | 42 | +4% VIP guests | night |
| 368 | 16 | Oar & Onion | Diner | Classic Diner | 26 | +4% VIP guests | night |
| 369 | 17 | Lotus Pad Café | Hall | Food Court | 37 | +5% VIP guests | night |
| 370 | 18 | Calm Bay Grill | Diner | Garden Terrace | 28 | +5% VIP guests | night |
| 371 | 19 | Lagoon Lamp Stack | Hall | Market Hall | 38 | +5% VIP guests | dusk |
| 372 | 20 | Lagoon Grand Market | Flagship | Flagship | 44 | +5% VIP guests | night |

## Region 19: Amber Woods (20)

_An autumn forest, golden and crunchy._ Host: Hazel · perk family: tips · menu lead: Classic Burger, Cheese Burger, Milkshake · weather: leaves

| # | Stop | Restaurant | Size | Layout | Pads | Perk | Time |
|---|---|---|---|---|---|---|---|
| 373 | 1 | Maple Leaf Grill | Pop-up | Bun Stand | 15 | +8% tips | day |
| 374 | 2 | Acorn Bites | Diner | Drive Hub | 26 | +9% tips | day |
| 375 | 3 | Amber Glow Café | Pop-up | Pop-up Kiosk | 15 | +9% tips | day |
| 376 | 4 | Fallen Log Diner | Diner | Patio Bistro | 27 | +10% tips | night |
| 377 | 5 | Crunchy Leaf Café | Hall | Long Counter | 31 | +11% tips | day |
| 378 | 6 | Woodsmoke Grill | Pop-up | Corner Grill | 20 | +11% tips | day |
| 379 | 7 | Chestnut Stack | Diner | Classic Diner | 26 | +12% tips | day |
| 380 | 8 | Squirrel Stop Snacks | Hall | Food Court | 37 | +12% tips | day |
| 381 | 9 | Golden Canopy Grill | Diner | Garden Terrace | 28 | +13% tips | night |
| 382 | 10 | Fireside Café | Hall | Market Hall | 38 | +14% tips | day |
| 383 | 11 | Forest Path Diner | Pop-up | Pop-up Kiosk | 15 | +14% tips | day |
| 384 | 12 | Pinecone Patties | Diner | Patio Bistro | 27 | +15% tips | day |
| 385 | 13 | Hazelnut Hut | Hall | Long Counter | 31 | +16% tips | day |
| 386 | 14 | Autumn Ember Grill | Diner | Drive Hub | 26 | +16% tips | night |
| 387 | 15 | Copper Leaf Café | Grand | Mega Diner | 38 | +17% tips | day |
| 388 | 16 | Bramble Bites | Diner | Patio Bistro | 27 | +17% tips | day |
| 389 | 17 | Rustle & Bun | Hall | Long Counter | 31 | +18% tips | day |
| 390 | 18 | Trailside Kitchen | Diner | Drive Hub | 26 | +19% tips | day |
| 391 | 19 | Harvest Hearth | Hall | Twin Kitchen | 33 | +19% tips | night |
| 392 | 20 | Amber Grand Lodge | Flagship | Burger Palace | 42 | +20% tips | day |

## Region 20: Gearworks City (20)

_A clockwork town that ticks, whistles and steams._ Host: Fenna · perk family: staff · menu lead: Double Stack, Fries, Soda · weather: steam

| # | Stop | Restaurant | Size | Layout | Pads | Perk | Time |
|---|---|---|---|---|---|---|---|
| 393 | 1 | Cog & Bun | Pop-up | Corner Grill | 20 | Helpers +6% speed | day |
| 394 | 2 | Gearbox Grill | Diner | Classic Diner | 26 | Helpers +6% speed | day |
| 395 | 3 | Steam Whistle Diner | Pop-up | Corner Café | 15 | Helpers +7% speed | day |
| 396 | 4 | Clocktower Café | Diner | Garden Terrace | 28 | Helpers +7% speed | night |
| 397 | 5 | Brass Burger Works | Hall | Market Hall | 38 | Helpers +8% speed | day |
| 398 | 6 | Piston Patties | Pop-up | Pop-up Kiosk | 15 | Helpers +8% speed | day |
| 399 | 7 | Boiler Room Bites | Diner | Patio Bistro | 27 | Helpers +9% speed | day |
| 400 | 8 | Rivet Row Diner | Hall | Long Counter | 31 | Helpers +9% speed | day |
| 401 | 9 | Chimney Stack | Diner | Drive Hub | 26 | Helpers +10% speed | night |
| 402 | 10 | Spring Coil Café | Hall | Twin Kitchen | 33 | Helpers +10% speed | day |
| 403 | 11 | Copper Pipe Grill | Pop-up | Corner Café | 15 | Helpers +11% speed | day |
| 404 | 12 | Smokestack Snacks | Diner | Garden Terrace | 28 | Helpers +11% speed | day |
| 405 | 13 | Ticktock Kitchen | Hall | Market Hall | 38 | Helpers +12% speed | day |
| 406 | 14 | Wrench & Relish | Diner | Classic Diner | 26 | Helpers +12% speed | night |
| 407 | 15 | Bolt Street Burgers | Grand | Grand Hall | 42 | Helpers +13% speed | day |
| 408 | 16 | Valve House Café | Diner | Garden Terrace | 28 | Helpers +13% speed | day |
| 409 | 17 | Furnace Fries | Hall | Market Hall | 38 | Helpers +14% speed | day |
| 410 | 18 | Pressure Gauge Grill | Diner | Classic Diner | 26 | Helpers +14% speed | day |
| 411 | 19 | Iron Gate Diner | Hall | Food Court | 37 | Helpers +15% speed | night |
| 412 | 20 | Grand Gearworks Hall | Flagship | Flagship | 44 | Helpers +15% speed | day |

## Region 21: Mushroom Glade (20)

_A fairy forest of glowing caps and fireflies._ Host: Scraps · perk family: pet · menu lead: Veggie Burger, Milkshake, Fries · weather: fireflies

| # | Stop | Restaurant | Size | Layout | Pads | Perk | Time |
|---|---|---|---|---|---|---|---|
| 413 | 1 | Toadstool Grill | Pop-up | Pop-up Kiosk | 15 | Pet ability +25% | night |
| 414 | 2 | Spore & Bun | Diner | Patio Bistro | 27 | Pet ability +27% | night |
| 415 | 3 | Glade Glow Café | Pop-up | Bun Stand | 15 | Pet ability +29% | night |
| 416 | 4 | Fairy Ring Diner | Diner | Drive Hub | 26 | Pet ability +31% | dusk |
| 417 | 5 | Moss Bed Bites | Hall | Twin Kitchen | 33 | Pet ability +32% | night |
| 418 | 6 | Puffball Patties | Pop-up | Corner Café | 15 | Pet ability +34% | night |
| 419 | 7 | Cap & Stem Café | Diner | Garden Terrace | 28 | Pet ability +36% | night |
| 420 | 8 | Dewdrop Diner | Hall | Market Hall | 38 | Pet ability +38% | night |
| 421 | 9 | Glowcap Grill | Diner | Classic Diner | 26 | Pet ability +40% | dusk |
| 422 | 10 | Fern Hollow Café | Hall | Food Court | 37 | Pet ability +42% | night |
| 423 | 11 | Twinkle Stump Stack | Pop-up | Bun Stand | 15 | Pet ability +43% | night |
| 424 | 12 | Morel Munch | Diner | Drive Hub | 26 | Pet ability +45% | night |
| 425 | 13 | Lichen Lane Grill | Hall | Twin Kitchen | 33 | Pet ability +47% | night |
| 426 | 14 | Pixie Path Diner | Diner | Patio Bistro | 27 | Pet ability +49% | dusk |
| 427 | 15 | Mushroom Mile | Grand | Mega Diner | 38 | Pet ability +51% | night |
| 428 | 16 | Glimmer Grove Grill | Diner | Drive Hub | 26 | Pet ability +53% | night |
| 429 | 17 | Burrow Bites | Hall | Twin Kitchen | 33 | Pet ability +54% | night |
| 430 | 18 | Thimble Table | Diner | Patio Bistro | 27 | Pet ability +56% | night |
| 431 | 19 | Moonmoss Café | Hall | Long Counter | 31 | Pet ability +58% | dusk |
| 432 | 20 | Grand Toadstool Hall | Flagship | Burger Palace | 42 | Pet ability +60% | night |

## Region 22: Bamboo Springs (20)

_A quiet hot-spring retreat among the bamboo._ Host: Clementine · perk family: patience · menu lead: Soda, Veggie Burger, Cheese Burger · weather: steam

| # | Stop | Restaurant | Size | Layout | Pads | Perk | Time |
|---|---|---|---|---|---|---|---|
| 433 | 1 | Bamboo Grove Grill | Pop-up | Corner Café | 15 | +8% patience | day |
| 434 | 2 | Hot Spring Café | Diner | Garden Terrace | 28 | +9% patience | day |
| 435 | 3 | Stone Lantern Diner | Pop-up | Corner Grill | 20 | +9% patience | day |
| 436 | 4 | Steam Bath Bites | Diner | Classic Diner | 26 | +10% patience | night |
| 437 | 5 | Jade Pool Grill | Hall | Food Court | 37 | +11% patience | day |
| 438 | 6 | Koi Pond Café | Pop-up | Bun Stand | 15 | +11% patience | day |
| 439 | 7 | Misty Spring Stack | Diner | Drive Hub | 26 | +12% patience | day |
| 440 | 8 | Pebble Path Diner | Hall | Twin Kitchen | 33 | +12% patience | day |
| 441 | 9 | Warm Water Grill | Diner | Patio Bistro | 27 | +13% patience | night |
| 442 | 10 | Teahouse Burgers | Hall | Long Counter | 31 | +14% patience | day |
| 443 | 11 | Bamboo Shoot Bites | Pop-up | Corner Grill | 20 | +14% patience | day |
| 444 | 12 | Willow Bend Café | Diner | Classic Diner | 26 | +15% patience | day |
| 445 | 13 | Rock Garden Grill | Hall | Food Court | 37 | +16% patience | day |
| 446 | 14 | Ripple Pool Diner | Diner | Garden Terrace | 28 | +16% patience | night |
| 447 | 15 | Quiet Garden Grill | Grand | Grand Hall | 42 | +17% patience | day |
| 448 | 16 | Soak & Snack | Diner | Classic Diner | 26 | +17% patience | day |
| 449 | 17 | Panda Path Grill | Hall | Food Court | 37 | +18% patience | day |
| 450 | 18 | Cloud Bath Café | Diner | Garden Terrace | 28 | +19% patience | day |
| 451 | 19 | Gentle Steam Stack | Hall | Market Hall | 38 | +19% patience | night |
| 452 | 20 | Grand Bathhouse | Flagship | Flagship | 44 | +20% patience | day |

## Region 23: Frostfest Village (20)

_A winter festival week of lights and gifts._ Host: Hazel · perk family: combo · menu lead: Milkshake, Double Stack, Cheese Burger · weather: snow

| # | Stop | Restaurant | Size | Layout | Pads | Perk | Time |
|---|---|---|---|---|---|---|---|
| 453 | 1 | Snowglobe Grill | Pop-up | Bun Stand | 15 | Combo builds 15% faster | night |
| 454 | 2 | Gingerbread Diner | Diner | Drive Hub | 26 | Combo builds 16% faster | night |
| 455 | 3 | Sleigh Bell Café | Pop-up | Pop-up Kiosk | 15 | Combo builds 17% faster | night |
| 456 | 4 | Tinsel Stack | Diner | Patio Bistro | 27 | Combo builds 17% faster | day |
| 457 | 5 | Candlelight Café | Hall | Long Counter | 31 | Combo builds 18% faster | night |
| 458 | 6 | Ice Rink Grill | Pop-up | Corner Grill | 20 | Combo builds 19% faster | night |
| 459 | 7 | Mitten Market Bites | Diner | Classic Diner | 26 | Combo builds 20% faster | night |
| 460 | 8 | Holly Hearth Diner | Hall | Food Court | 37 | Combo builds 21% faster | night |
| 461 | 9 | Ribbon Wrap Café | Diner | Garden Terrace | 28 | Combo builds 21% faster | day |
| 462 | 10 | Festive Fries | Hall | Market Hall | 38 | Combo builds 22% faster | night |
| 463 | 11 | Snowflake Stack | Pop-up | Pop-up Kiosk | 15 | Combo builds 23% faster | night |
| 464 | 12 | Cocoa Corner | Diner | Patio Bistro | 27 | Combo builds 24% faster | night |
| 465 | 13 | Wreath & Bun | Hall | Long Counter | 31 | Combo builds 24% faster | night |
| 466 | 14 | Frosty Window Grill | Diner | Drive Hub | 26 | Combo builds 25% faster | day |
| 467 | 15 | Jingle Junction | Grand | Mega Diner | 38 | Combo builds 26% faster | night |
| 468 | 16 | Gift Box Grill | Diner | Patio Bistro | 27 | Combo builds 27% faster | night |
| 469 | 17 | Starlit Square Café | Hall | Long Counter | 31 | Combo builds 28% faster | night |
| 470 | 18 | Warm Socks Diner | Diner | Drive Hub | 26 | Combo builds 28% faster | night |
| 471 | 19 | Lantern Fair Grill | Hall | Twin Kitchen | 33 | Combo builds 29% faster | day |
| 472 | 20 | Frostfest Grand Hall | Flagship | Burger Palace | 42 | Combo builds 30% faster | night |

## Region 24: Fossil Valley (20)

_A prehistoric valley of ferns and footprints._ Host: Dot · perk family: cook · menu lead: Double Stack, Classic Burger, Fries · weather: embers

| # | Stop | Restaurant | Size | Layout | Pads | Perk | Time |
|---|---|---|---|---|---|---|---|
| 473 | 1 | Fossil Grill | Pop-up | Corner Grill | 20 | +6% cook speed | day |
| 474 | 2 | Fern Valley Diner | Diner | Classic Diner | 26 | +6% cook speed | day |
| 475 | 3 | Dino Bites | Pop-up | Corner Café | 15 | +7% cook speed | day |
| 476 | 4 | Amber Stone Café | Diner | Garden Terrace | 28 | +7% cook speed | night |
| 477 | 5 | Tar Pit Patties | Hall | Market Hall | 38 | +8% cook speed | day |
| 478 | 6 | Big Footprint Grill | Pop-up | Pop-up Kiosk | 15 | +8% cook speed | day |
| 479 | 7 | Ash Cloud Café | Diner | Patio Bistro | 27 | +9% cook speed | day |
| 480 | 8 | Clawprint Café | Hall | Long Counter | 31 | +9% cook speed | day |
| 481 | 9 | Bone Dig Diner | Diner | Drive Hub | 26 | +10% cook speed | night |
| 482 | 10 | Lava Glow Grill | Hall | Twin Kitchen | 33 | +10% cook speed | day |
| 483 | 11 | Mossback Munch | Pop-up | Corner Café | 15 | +11% cook speed | day |
| 484 | 12 | Old Era Eatery | Diner | Garden Terrace | 28 | +11% cook speed | day |
| 485 | 13 | Stomp & Bun | Hall | Market Hall | 38 | +12% cook speed | day |
| 486 | 14 | Pebble Egg Café | Diner | Classic Diner | 26 | +12% cook speed | night |
| 487 | 15 | Smoke Ridge Grill | Grand | Grand Hall | 42 | +13% cook speed | day |
| 488 | 16 | Long Neck Diner | Diner | Garden Terrace | 28 | +13% cook speed | day |
| 489 | 17 | Fern Frond Café | Hall | Market Hall | 38 | +14% cook speed | day |
| 490 | 18 | Rumble Rock Stack | Diner | Classic Diner | 26 | +14% cook speed | day |
| 491 | 19 | Primeval Pantry | Hall | Food Court | 37 | +15% cook speed | night |
| 492 | 20 | Fossil Grand Dig | Flagship | Flagship | 44 | +15% cook speed | day |

## Region 25: Golden Capital (20)

_The city of gold, the last stop of the tour._ Host: Hazel · perk family: all · menu lead: Double Stack, Cheese Burger, Milkshake · weather: confetti

| # | Stop | Restaurant | Size | Layout | Pads | Perk | Time |
|---|---|---|---|---|---|---|---|
| 493 | 1 | Gilded Grill | Pop-up | Pop-up Kiosk | 15 | +5% tips, patience and cook speed | day |
| 494 | 2 | Crown Square Café | Diner | Patio Bistro | 27 | +5% tips, patience and cook speed | day |
| 495 | 3 | Fountain Court Diner | Pop-up | Bun Stand | 15 | +6% tips, patience and cook speed | day |
| 496 | 4 | Marble Hall Bites | Diner | Drive Hub | 26 | +6% tips, patience and cook speed | night |
| 497 | 5 | Golden Bun Bistro | Hall | Twin Kitchen | 33 | +6% tips, patience and cook speed | day |
| 498 | 6 | Statue Square Grill | Pop-up | Corner Café | 15 | +6% tips, patience and cook speed | day |
| 499 | 7 | Grand Avenue Stack | Diner | Garden Terrace | 28 | +7% tips, patience and cook speed | day |
| 500 | 8 | Palace Steps Café | Hall | Market Hall | 38 | +7% tips, patience and cook speed | day |
| 501 | 9 | Sunlit Spire Grill | Diner | Classic Diner | 26 | +7% tips, patience and cook speed | night |
| 502 | 10 | Velvet Rope Diner | Hall | Food Court | 37 | +7% tips, patience and cook speed | day |
| 503 | 11 | Gold Leaf Kitchen | Pop-up | Bun Stand | 15 | +8% tips, patience and cook speed | day |
| 504 | 12 | Parade Route Grill | Diner | Drive Hub | 26 | +8% tips, patience and cook speed | day |
| 505 | 13 | Mint Street Burgers | Hall | Twin Kitchen | 33 | +8% tips, patience and cook speed | day |
| 506 | 14 | Treasury Table | Diner | Patio Bistro | 27 | +8% tips, patience and cook speed | night |
| 507 | 15 | Chandelier Café | Grand | Mega Diner | 38 | +9% tips, patience and cook speed | day |
| 508 | 16 | Promenade Grill | Diner | Drive Hub | 26 | +9% tips, patience and cook speed | day |
| 509 | 17 | Golden Mile Diner | Hall | Twin Kitchen | 33 | +9% tips, patience and cook speed | day |
| 510 | 18 | Victory Arch Grill | Diner | Patio Bistro | 27 | +9% tips, patience and cook speed | day |
| 511 | 19 | Hall of Buns | Hall | Long Counter | 31 | +10% tips, patience and cook speed | night |
| 512 | 20 | The Golden Bun | Flagship | Burger Palace | 41 | +10% tips, patience and cook speed | day |

---

**Regenerate this list** after changing the catalogue:
```bash
PRINT_RESTAURANTS=true ./gradlew :app:testDebugUnitTest --tests '*RestaurantListPrint*' --rerun
```
It prints the body between `RESTAURANTS_MD_BEGIN` / `RESTAURANTS_MD_END` in the test's output
(`app/build/test-results/testDebugUnitTest/TEST-…RestaurantListPrint.xml`).
