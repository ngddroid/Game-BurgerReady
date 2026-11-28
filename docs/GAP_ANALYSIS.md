# Bun Voyage! — Gap Analysis (round 3, 2026-11-28)

What our game is missing compared with the arcade-idle / restaurant market, and what we did about it. This builds on
`MARKET_ANALYSIS.md` (round 1). Everything is summarised in our own words; nothing is copied. Sources are inline.
**(unverified)** marks claims that rest on player reviews or that no source confirmed.

**Method.**
- Desk research (store listings, publisher blogs, trade press, benchmark reports), done 2026-11-28.
- Our side: a read of the code (`game/`, `data/`, `ui/`), plus play on the owner's Realme RMX5313 with the existing save (Bun Wagon finished, Seaside Shack started).

**Titles compared.**
- Burger Please! (BP), Pizza Ready! (PR), My Mini Mart (MMM), My Perfect Hotel (MPH), Eatventure (EV), Cooking Fever (CF).
- 2025–26 entrants: Chainsaw Juice King (CJK), Cafe Life (CL), and Sunday City (SC), a tycoon-adjacent life sim.

## 1. Market snapshot (what changed since round 1)
- **Scale.**
  - PR passed 300M downloads and was the #1 US iOS idle game by downloads in Q3 2025 ([Sensor Tower](https://sensortower.com/blog/2025-q3-ios-top-5-idler%20games-units-us-602ae7fb241bc16eb874f8e1)).
  - BP reached 15M+ downloads in its first months; most of its early IAP was "No Ads" ([Sensor Tower](https://sensortower.com/ko/blog/Burger-Please-becomes-the-1-downloaded-mobile-tycoon-game-with-over-15M-downloads)).
  - MPH has made $100M+ ([PocketGamer.biz](http://www.pocketgamer.biz/the-top-50-mobile-game-makers-of-2026/)).
- **New entrants** keep the same loop and add live-ops:
  - CJK (2025): random thieves and giant-fruit enemies, island travel, seasonal events and a premium pass ([App Store](https://apps.apple.com/app/id6450706268), [SayGames](https://blog.say.games/posts/saygames-reports-300-million-in-idle-arcades-revenue-partners-with-loadcomplete-to-launch-chainsaw-juice-king)).
  - CL (2025): city-themed cafés, a leaderboard, and character cards from special orders ([App Store](https://apps.apple.com/us/app/id6747019708)).
  - AppMagic's Q3 2025 hybrid-casual top 10 had no restaurant idle game. Revenue spikes there came from passes, leaderboards and races ([AppMagic](https://appmagic.rocks/blog/q3hybrid2025)).
- **Benchmarks.**
  - GameAnalytics' 2025 data:
    - D1 retention just over 30 % for the top quartile, about 40 % for the top 10 %;
    - median D7 just under 4 %;
    - sessions about 5 min, 5–6 sessions a day ([summary](https://gamedevreports.substack.com/p/gameanalytics-mobile-and-pc-game)).
  - Arcade-idle holds players for 30+ min early but retains worse than other idle games ([Homa](https://www.homagames.com/blog/arcade-idle-a-new-hybridcasual-genre-enters-the-game)).
- **Ads are the genre's sore point.**
  - Ad frequency is a complaint in the listings of every ad-led title. PR, MMM and CL reviews say ads still show after buying No Ads (unverified).
  - In a Deloitte survey, 52 % of players quit a game over repeated disruptive ads ([Deloitte](https://www.deloitte.com/us/en/about/press-room/deloitte-improve-mobile-game-advertising.html)).
  - Rewarded ads offered at a moment of need get far more engagement than ads between levels (38 % vs 24 %) ([Unity via PocketGamer.biz](http://www.pocketgamer.biz/unity-global-rewarded-ad-engagement-rose-by-32-in-2023/)).
- **Narrative.**
  - None of the core arcade-idle titles has a story: every one is "small stand to empire" with an anonymous avatar.
  - Reviewers of EV and CL complain that new locations feel like reskins (unverified).
  - Story and dialogue are growing among top casual games, and merge games without an ongoing story have struggled ([GameRefinery](https://gamerefinery.com/game-changing-storytelling-how-to-integrate-narrative-elements-in-mobile-games)).
  - A light story with about six characters lengthens the retention tail ([MAG Interactive on mobilegamer.biz](https://mobilegamer.biz/mags-alice-bowman-on-how-narrative-can-help-ua-retention-monetisation-and-more/)).
  - The only verified arcade-idle case of characters with dialogue is MPH's 2026 Barbie event; collecting those characters drove the event ([PocketGamer.biz](https://www.pocketgamer.biz/inside-saygames-barbie-collaboration-and-the-live-ops-strategy-behind-my-perfect-hotel/)).

## 2. Feature-by-feature comparison
Status: **have** = on par; **weaker** = present but thinner than the leaders; **missing** = not in the game. The last
column says what round 3 changed (✅ done this round, ➕ improved, — not touched).

| Area | Feature | Market (who) | Bun Voyage before round 3 | Status | Round 3 |
|---|---|---|---|---|---|
| Core loop | Joystick chef carrying stacks | BP, PR, MMM, MPH, CJK | Yes; stack sway, capacity upgrades | have | — |
| Core loop | Guided unlocks: arrows, camera pan to new spots | MMM ([PocketGamer.biz](https://www.pocketgamer.biz/under-the-hood-deconstructing-the-top-hypercasual-games/)), BP | Two pads at a time, floor arrows, camera focus | have | — |
| Core loop | Staff hires (cashier, runner, cleaner) | all | Hire pads on the sidewalk, 3 roles | have | — |
| Core loop | In-world HR / upgrade office paid with cash | BP ([LevelWinner](https://www.levelwinner.com/?p=91879)), PR | HUD UPGRADES button + Kitchen tab | weaker | — (round 4 candidate) |
| Core loop | Per-staff upgrades (speed, carry) | BP, PR, MPH | One global Staff Speed track | weaker | — |
| Core loop | Drive-thru | BP, PR, EV | From restaurant 3 | have | — |
| Core loop | Physical money you walk over | BP, PR, MMM | Coin piles at the register | weaker (coins, not cash stacks) | ✅ cash-brick stacks |
| Core loop | Random disruptions | BP (crowds), CJK (thieves), MPH (breakdowns) | Spills, grease fires, raccoon thief, jams, sleepy guests | have | ➕ each gets a story face |
| Core loop | Special guests / crowds | BP (VIPs, crowds, delivery) | VIPs, food critics, rush hours | have (no delivery orders) | — |
| Core loop | Temporary boost pickups | BP, CJK | Espresso, Magnet, Golden Spatula | have | — |
| Meta | Many locations, old ones keep earning | all | 12 restaurants, idle income (8 h cap) | have | — |
| Meta | Per-location renovation | EV (tiers), BP (ad-boosted branch income) | 5 renovation stars per restaurant (World tab) | have | — |
| Meta | Collections | EV (gear), CL (character cards) | Recipe cards (7 sets), 50 achievements, chef rank | have | ➕ Voyage Book |
| Meta | Reason to revisit old locations | CJK (revisiting, Oct 2026), CF (levels per restaurant) | Could revisit, but nothing to do there | missing | ✅ gold stars |
| Meta | Prestige / reset for multiplier | MPH ("upgrade the whole hotel", players dislike it) | None. Our rule: never take back what was bought | missing by design | ✅ non-reset alternative (gold stars) |
| Onboarding | Play within seconds, no menus | PR ([Gamigion](https://www.gamigion.com/pizza/)) | 4 onboarding pages, then straight into the Bun Wagon | have | — |
| Onboarding | Tutorial with personality | none verified | Neutral system tips | weaker | ✅ Hazel's voice + portrait |
| Live-ops | Daily login calendar | PR, CF, CJK | 7-day calendar | have | — |
| Live-ops | Daily challenges | MPH, CF | 3 daily tasks + chest, Home screen only | weaker (invisible while playing) | ✅ in-game task tracker |
| Live-ops | Daily wheel / chest / shop | CJK (daily shop) | Spin, free chest | have | — |
| Live-ops | Seasonal / limited-time events | PR (Christmas), MPH (themed hotels), EV (weekly event restaurants), CF, CJK | None | missing | — (needs content + remote config) |
| Live-ops | Season / battle pass | PR, MPH, EV, CF, CJK, SC | None | missing | — (needs IAP) |
| Social | Leaderboards, contests, clubs | PR (weekly contest), MPH, EV (clubs), CL, SC | None (share link only) | missing | — (needs a backend) |
| Monetisation | Rewarded-ad boosts (×2 income, speed, offline ×2) | BP, PR, MMM, MPH, EV, CJK | `AdsProvider` seam; mock in debug only | missing (no SDK) | — |
| Monetisation | No Ads / packs / piggy bank | BP, PR, MMM, MPH, CF, CJK | None | missing (no SDK) | — |
| Monetisation | Interstitials | almost all (top complaint) | None | (deliberately absent) | — |
| Late game | Goal after the last location | none clear. BP and PR reviewers report repetition, CF players ask for free play ([App Store](https://apps.apple.com/au/app/id714796093?l=en-au)) | Nothing after restaurant 12 | missing | ✅ Golden Tour: 36 gold stars + epilogue |
| Story | Recurring cast with dialogue | none in the base games; MPH event only | None | missing | ✅ 6 characters, 52 beats |
| Story | Reason to travel between locations | none | "Travel the world" onboarding page | missing | ✅ Hazel's Voyage Book |
| Story | Per-location beats tied to milestones | none | None | missing | ✅ arrival, 1–2 milestone and completion beats |
| Story | Finale / ending | none | None | missing | ✅ grand opening + post-game hook |
| Story | Re-read / log | — | None | missing | ✅ Voyage Book screen |

## 3. Prioritised gaps
Score = player impact (1–5) × ease (1–5, 5 = cheapest). Effort counts our constraints:
- no ads or IAP SDK and no backend this round;
- `game/` must stay deterministic and bot-testable;
- art comes from our own scripts.

| # | Gap | Impact | Ease | Score | Decision |
|---|---|---|---|---|---|
| 1 | Story: cast, premise, per-restaurant beats, finale | 5 | 3 | 15 | **Done this round.** Unique in the genre (§1); turns reskinned locations into chapters |
| 2 | Late-game goal after restaurant 12 + reason to revisit | 5 | 3 | 15 | **Done:** gold stars (3 per restaurant, one-visit challenges, gems + idle income) |
| 3 | Daily tasks visible while playing | 3 | 5 | 15 | **Done:** HUD tracker chip + "task complete" toast |
| 4 | Tutorial with personality | 3 | 5 | 15 | **Done:** Hazel's lines + portrait in the tip box |
| 5 | Story log / character collection | 3 | 4 | 12 | **Done:** Voyage Book (pages, stamps, gold stars, cast, transcripts) |
| 6 | Cash-stack money visuals | 3 | 3 | 9 | **Done:** cash bricks instead of coins at the register and drive-thru |
| 7 | In-world HR / upgrade office | 3 | 2 | 6 | Round 4: needs layout space in all 8 templates, a model and nav changes |
| 8 | Rewarded-ad placements (×2 income, offline ×2, boosts) | 4 | 2 | 8 | Blocked on an ads SDK. The `AdsProvider` seam is ready; place offers at moments of need (Unity data) |
| 9 | Limited-time events / event restaurant | 4 | 1 | 4 | Later: needs content, remote config and a calendar |
| 10 | Season pass, No Ads / packs | 4 | 1 | 4 | Blocked on IAP |
| 11 | Leaderboards / clubs | 3 | 1 | 3 | Blocked on a backend |
| 12 | Delivery orders | 2 | 2 | 4 | Later: new customer type + bot support |
| 13 | Per-staff upgrades | 2 | 3 | 6 | Later: goes with the HR office (#7) |

## 4. Story gaps in detail (input for `STORY_SCRIPT.md`)
| Question a player asks | Before round 3 | Now |
|---|---|---|
| Who is Chef Patty and why travel? | A corgi "with a big dream" (onboarding page 1) | Patty, Hazel's ex-dishwasher, fills the twelve pages of Hazel's Voyage Book |
| Who else is in this world? | Anonymous guests and staff | Hazel (mentor), Fenna (rival), Dot (regular), Scraps (the raccoon thief), Clementine (critic) |
| Why does this restaurant matter? | New menu and perk | Arrival beat, then 1–2 milestone beats tied to what the place teaches (drive-thru, fires, VIPs, combos, raccoons) |
| What happens when I finish one? | Celebration card | Completion beat: the page is stamped, Hazel's postcard points to the next page |
| Does it end? | No; after restaurant 12 nothing happens | Grand-opening finale with the whole cast; the Golden Tour opens; epilogue at 36 gold stars |
| Can I re-read it? | — | Voyage Book on the Play tab |

## 5. What we deliberately keep different
- **No interstitials and no forced ads.** Ad frequency is the genre's loudest complaint. If ads come, they should be rewarded-only, at moments of need.
- **No resets.** MPH's room-reset upgrade draws complaints, and our rule is that nothing bought is ever taken back. The late game adds goals on top of what was built.
- **Light story.** At most 8 bubbles per beat, auto-advancing, with the restaurant still running underneath. This fits the ~5-minute sessions the benchmarks report.
