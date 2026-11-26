# Implementation notes — decisions & deviations log

This log records **why** the build differs from [MASTER_GAME_PLAN.md](MASTER_GAME_PLAN.md) and which decisions were taken along the way.
- How the code works: [ARCHITECTURE.md](ARCHITECTURE.md).
- How to change it: [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md).

Add a dated entry whenever you make a decision that a future developer would otherwise have to rediscover.

---

## 2026-11-23 — initial build

### Product decisions
- **Name: "Bun Voyage!"** "Burger Ready" is used by two Play apps and echoes the reference game's name, which is an impersonation risk.
- **Levels kept** (the owner deferred this to the developer). Level-based shifts give clear goals, the "only level 1 unlocked" rule and the ad-unlock hook. They also differentiate the game from persistent-restaurant competitors.
  - Each level **starts with its core kitchen pre-built** and adds 2–9 build/upgrade/hire pads, so the build moment remains without rebuilding from scratch every shift.
  - Levels 1–10 are tutorials with **overtime instead of failure**.
- **Unlocking:** only level 1 is unlocked in debug and release. In debug, tapping a locked level offers a mock rewarded ad (3 s placeholder) that unlocks it. Release has no ad UI at all.
- **No skip button on onboarding.** This is a product requirement, so don't add one.

### Technical decisions
- **Native Kotlin + custom GLES 3 renderer**, not Unity/Godot/Filament:
  - full control of the toon look and of performance on low-end Mali GPUs;
  - no engine downloads or licences;
  - a ~29 MB release APK (the reference is ~231 MB).
- **One Gradle module** with clean packages, instead of separate `:engine`/`:game` modules: simpler with AGP 9's built-in Kotlin. `game/` stays Android-free and JVM-tested.
- **Level targets are tuned offline** by simulation (`LevelTunerTest` → `assets/levels/targets.bin`), rather than computed at runtime. This guarantees winnability and costs 160 KB.
- **Navigation:**
  - 0.25 m nav grid, with an *inflated* copy for planning: 0.5 m cells made characters snag on counters, and paths hugged walls.
  - Customers' orders are remapped to items whose station is already built (`World.availableOrder`). Otherwise early-level customers ordered unbuildable items and the shift stalled.
  - The trash bin has an interior approach point; the original target point put staff outside the wall.
- **All assets are procedurally generated** (Blender + numpy scripts in `tools/`). This avoids licensing, keeps the style consistent and makes every asset reproducible.
- **Release R8:** the template's experimental `optimization.packageScope` caused an `IllegalAccessError` crash on start, so it was removed. Use whole-app optimization.
- **Testing on an emulator** configured like the target phone, because the Realme RMX5313 was in use by another session. The host-GPU emulator mode crashed (an emulator bug), so SwiftShader was used.

### Deviations from the plan
| Plan said | Built | Why |
|---|---|---|
| `:engine` / `:game` modules | Packages in `app` | Simplicity |
| 12 layout templates | 8 templates × mirroring | Enough variety once each is rendered in 12 stores and 8 themes; easy to add more (`Layouts.specs`) |
| Off-screen arrows to troubles | HUD trouble chips + in-world alert badges | Simpler. An arrow would help on big layouts (see Known issues) |
| Compose UI tests | Not written (manual/emulator QA instead) | Time. Listed as a next step |
| Store music variants | All stores use the same calm/rush level music | `StoreDef.music` is reserved, but unused |
| Parrot pet ability | Not implemented | Listed in Known issues |
| Chef Rank rewards | 5 gems per rank | Simple and visible |

### Asset notes from the artists (the scripts)
- `@hat` sits at skull height z = 1.0, not at the apex, so 0.36 m hat rings sit on the head.
- The frog's high eyes are covered by brimmed hats, so `WorldView.hatLiftFor()` lifts and tilts hats on frogs.
- The food truck is 4.5 m long along X; in-game it gets its own lawn spot.
- Seat anchors extend past the table footprints. Chairs overlap the neighbouring cell slightly.
- Shirt *and* sleeve faces are tinted. Corgi and fox white chests are hidden under the shirt.
- The astronaut helmet is an opaque dome, because `.bvm` has no transparency.

---

## 2026-11-25 — documentation pass
- Added `README.md`, `CLAUDE.md`, `docs/ARCHITECTURE.md`, `docs/DEVELOPER_GUIDE.md`, `tools/README.md`, and the `tools/blender/inspect_bvm.py` model inspector.
- Collected the known issues and next steps in `DEVELOPER_GUIDE.md`.
