# Restore BCU's native battle UI

> Use executing-plans and test-driven-development to execute this correction.

**Goal:** Remove the custom PvP battlefield/button grid. Enter the existing BattleInfoPage,
using its BattleBox/BBPainter/BBCtrl, native icons, money/worker/cannon UI, camera, row mode,
large/normal layout and key bindings. Keep existing lobby/password/preferences/network features.

**Architecture:** A read-only online SBCtrl adapter feeds native painting with disposable display
copies and forwards native action indices to RoomClient. Canonical state remains in the lobby's
30TPS network loop. Native painting receives the local participant's HUD state without swapping
the physical world. PvP-only rendering handles mirrored units and two player castles/cannons.
No independent gameplay update, pause, speed-up, replay or continue is allowed in the native page.

**Scope:** User requested using original UI, with no confirmation. Do not redesign it or extend
multiplayer gameplay. Existing submodule gitlink must remain unchanged.

- [x] Recover current branch, inspect original renderer/page/input and run baseline tests.
- [x] Add regression that fails with custom PvpCanvas and Swing button grid.
- [x] Implement read-only controller and native page constructor/lifecycle.
- [x] Add minimal renderer hooks for local HUD, mirrored entities, castles and cannons.
- [x] Replace lobby battle view; retain cleanup/server lifetime and preferences.
- [x] Verify native hit targets/keys, 1/2 rows, camera, both sides, snapshots, debug suppression.
- [x] Run real Swing two-client entry/battle/return tests and existing network regression suite.
- [ ] Run clean Maven/Gradle CI, verify portable JAR and publish updated pvp-dev release.

## Regression evidence before publication

- Original lobby structural test failed because of the custom Swing button grid (red).
- Original camera cached a previous display snapshot: native pan after publish changed the old state,
  not the current one. A failing regression now passes after rebinding camera state.
- Native painter tests use real BBPainter/BBCtrl and synthetic textures, not screenshots of actual game artwork.
- Both physical sides, local-only icons and economy, original mouse hit targets and key semantics,
  single/two-row layout, local row switching, camera, no hitboxes, 60FPS display-only updates,
  canonical state integrity and unchanged single-player debug behavior are covered.
- Real Swing tests navigate through MainFrame to the native child page, use KeyHandler Q input,
  run two JVMs with 30/60FPS and optional passwords, and return to lobby without stopping the friend server.
- No user Windows/JOGL or complete real-asset visual verification is claimed.
