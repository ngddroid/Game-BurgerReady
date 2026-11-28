# Market analysis: the arcade-idle restaurant genre (2026-11-27)

Why the game moved from 10,000 timed levels to persistent restaurants, and what the genre does that we copy *as mechanics* (never as art, names or text). This summarises desk research done on 2026-11-27 (trade press, publisher blogs, store listings and screenshots). Items marked *unverified* come from player reports rather than primary sources.

## 1. How the top games are structured

| Game (publisher) | Structure |
|---|---|
| Burger Please! (Supercent) | One shop that grows from a counter to a drive-thru; then new branches. "Level" is a player XP level shown as a star + bar. |
| Pizza Ready! (Supercent) | Several restaurants; old ones keep earning idle income; a map travels between them. HR room + Upgrade room with 3 stats × 5 levels each. |
| My Mini Mart (Supersonic) | First market ≈ 30–40 min, then a new location. Unlock order: register → shelf → first producer; each unlock ≈ 2–3 cycles of current income. |
| My Perfect Hotel (SayGames) | Each hotel levels up to a cap, which unlocks the next hotel. |
| Cooking Fever (contrast: timed levels) | Even here, kitchen upgrades persist across all levels of a restaurant. |

**Key finding:** no successful title takes back what the player bought inside a location. Our old per-level reset (build a table, lose it next shift) was the genre's biggest anti-pattern and the most common complaint.

## 2. Core loop and pacing
- **No fail state, no level clock.** Timers exist only at small scale: cooking, customer patience, boosts. Patience only costs a sale and the combo.
- **Pads in a guided sequence.** One or two purchasable spots visible at a time, an arrow to the affordable one, and a camera pan when a new spot appears. Each unlock costs about 2–3 cycles of current income, so growth feels exponential.
- **Physical money.** Customers leave cash at the register; the player walks over it. Later hires automate parts of the loop.
- **Benchmarks** (publisher figures): first-day playtime 20–30 min; day-1 retention ≈ 35% average and 48–52% for the top 20; first location 30–40 min.
- **Offline income:** capped (Eatventure: 3 h), doubled for a rewarded ad, with an upgrade to raise it.

## 3. Monetisation and retention (for later; ads are not integrated yet)
- Rewarded ads: floating boost icons (2× speed or money), unlocking upgrade levels, 2× offline income, costumes after N ads.
- IAP: permanent versions of the ad boosts (no ads + 2× money + speed + capacity), starter packs, a battle pass.
- Interstitial frequency is the genre's top complaint (reported every 20–60 s in some titles). Keep them out of the first session.
- Retention: daily login, daily challenges (VIP guests, delivery orders, sudden crowds), seasonal events.

## 4. Art direction
- **Camera:** high, narrow FOV, nearly orthographic; about a third of the location on screen, with locked areas peeking in at the edges.
- **Palette:** light, quiet large surfaces; saturated colour reserved for interactables (stations, counters, chairs, food, money). One dominant colour, 2–3 related colours, 1 accent.
- **Light:** one soft key light, short soft contact shadows, **no haze/fog**, shadows tinted as a darker version of the base colour (never grey), no blown-out highlights.
- **Readability:** outlines on characters and key objects only; floor labels and arrows; "MAX" tags; tall item stacks as visual reward.
- **Washed-out vs premium:** everything at the same brightness, grey shadows, haze and clipped whites read cheap. A clean light base with a few saturated accents and coloured shadows reads premium.

## 5. What we changed (round 1)

| Finding | Change in Bun Voyage! |
|---|---|
| Persistent locations | 12 restaurants (`game/Restaurant.kt`), each with a fixed build plan of 15–42 pads. Progress is saved per restaurant (`SaveData.restaurants`) and restored by `World`, so nothing locks again. |
| Promotion to a new location | The last pad of each plan is **NEW RESTAURANT**. Buying it shows a celebration and opens the next restaurant; finished ones keep earning idle income on the World tab. |
| No fail state | The shift timer, stars and level targets are gone. Patience costs only the sale and the combo. Rush hours and critic visits are periodic events. |
| Guided pad sequence | Two pads visible at a time, in plan order; arrows point at any affordable pad; the camera pans to newly revealed pads; affordable pads glow. |
| Exponential pacing | Costs follow a geometric curve per restaurant, scaled by the restaurant's money multiplier. `RestaurantFlowTest` keeps the expert bot inside its pacing window: ≈12.5 min for restaurant 1 and 15–33 min for later ones. Humans take roughly 1.5–2× longer. |
| Level = XP | The HUD shows the Chef Rank star with an XP ring instead of a level number. |
| One wallet | In-world cash *is* Bucks: earned at the register, spent on pads, Kitchen upgrades (also buyable in-game), skins, pets and themes. |
| Art | The toon shader uses a coloured shade (not grey ambient), a highlight shoulder (no clipped whites), a small saturation lift and thicker outlines. Fog is pushed out of view, and there's no dusk dimming outside night-themed stores. Ground, sidewalk and kitchen palettes are richer, affordable pads glow, the door frame fades when the chef walks behind it, and there are blob shadows on LOW quality. |

## 6. Known gaps (input for round 3)
> **Round 3 (2026-11-28):** superseded by [GAP_ANALYSIS.md](GAP_ANALYSIS.md). It has a fresh competitor comparison and prioritised gaps. Done there: story, late game (gold stars), the in-game task tracker and cash-stack money. Still open: the in-world HR/upgrade office, rewarded ads, events and passes.

- **In-world HR / Upgrade rooms** (genre standard) instead of a HUD button.
- **Rewarded-ad placements:** boosts, offline ×2, upgrade levels. These need a real ads SDK behind `ads/Ads.kt`.
- **Daily challenges and events** tied to the restaurant loop (delivery orders, VIP days).
- **Money visuals:** cash-brick stacks instead of coins, plus a register automation hire.
- **Story/meta:** there is no narrative thread across the 12 restaurants (who is Chef Patty travelling for? what is the goal after restaurant 12?).
- **Late game:** after restaurant 12 there is no endless goal (prestige, renovation tiers or seasonal locations).
