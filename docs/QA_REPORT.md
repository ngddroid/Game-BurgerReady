# Bun Voyage! — QA Report

_Date: 2026-11-23 · Build: 1.0 (1) · Tester: automated + scripted emulator runs_

> How to re-run these checks: [DEVELOPER_GUIDE.md § Testing on devices](DEVELOPER_GUIDE.md#testing-on-devices).
> Open items: [DEVELOPER_GUIDE.md § Known issues & next steps](DEVELOPER_GUIDE.md#known-issues--next-steps).

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
