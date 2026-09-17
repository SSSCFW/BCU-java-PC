# Online PvP Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement task-by-task.

**Goal:** Add passworded two-player online battles to the existing BCU desktop.

**Architecture:** Fixed 30 Hz lockstep through a WebSocket relay, separate per-side
combat state and match-scoped asset registries. A render snapshot isolates the
simulation from 30/60 FPS and GUI changes.

**Tech Stack:** Java 8-compatible code, existing Kotlin 2.2.0/Gson, Java-WebSocket
1.6.0, Maven and Gradle, JDK test harness.

**Spec:** docs/superpowers/specs/2026-09-17-online-pvp.md

## Global Constraints

- Exactly two players; input/control identity is assigned by the server.
- 30 logic TPS, 30/60 display FPS; never skip an authoritative tick.
- No writes to existing user packs; no executable peer payloads.
- Keep the common submodule pinned; use src/pvp/java overlays.
- WSS for public servers; WS restricted to explicit development use.

### Task 1: Protocol, clock and room state
Files: src/main/java/online/net/{Protocol,RoomServer,RoomClient,Room}.java,
src/main/java/online/sync/{FixedTickClock,InputFrame}.java,
src/test/java/online/tests/{ClockTests,NetworkTests}.java.
Interfaces: FixedTickClock.due(long now), InputFrame(long tick,int left,int right),
RoomClient.Listener for lobby, bundle, start, frame, error callbacks.
- [x] Write and run failing clock/frame/room tests.
- [x] Add bounded messages, password auth, membership and ready barrier.
- [x] Pace ordered input frames at 30 Hz; cap future ticks/queued packets.
- [x] Verify wrong passwords, third clients, duplicate/out-of-order inputs,
  timeouts, disconnect and hash mismatch on real loopback WebSockets.

### Task 2: Safe match bundles
Files: src/main/java/online/bundle/{SafeArchive,MatchBundle,MatchSource,SafeJson,AssetChecks}.java,
src/test/java/online/tests/BundleTests.java.
Interfaces: MatchBundle.export(BasisLU), MatchBundle.read(Path).mount(String,int),
AutoCloseable scope removes only identifiers it owns.
- [x] Write and run failing path/hash/namespace tests.
- [x] Export a frozen lineup plus the transitive custom-pack/resource closure.
- [x] Validate and rewrite references before registering temporary user packs.
- [x] Test same original IDs with different contents, local animation references,
  missing dependencies, class-tag rejection, limits and cleanup after failure.

### Task 3: Symmetric battle core
Files: src/pvp/java/common/battle/{PvpStageBasis,PvpTiming,PvpDamage,StageBasis}.java,
src/main/java/online/sync/BattleDigest.java and targeted entity/attack overlays.
Tests: src/test/java/online/tests/{CoreTests,CombatTests,ProcessTests}.java and synthetic animation fixtures.
Interfaces: PvpStageBasis.step(InputFrame), PvpStageBasis.displayCopy(),
BattleDigest.of(StageBasis); physical left direction is +1, right is -1.
- [x] Write and run failing two-side economy/spawn and wave geometry tests.
- [x] Add ownership-aware accessors; retain player-unit ability processing on both sides.
- [x] Use simultaneous PvP update phases, mirrored wave origin/width, side-aware cannons.
- [x] Run full-frame animation simulation inside PvpTiming; clone only for rendering.
- [x] Compare long identical battles at different render schedules and verify
  offline PvE keeps its old direction-specific geometry.

### Task 4: Desktop UI and packaging
Files: src/main/java/online/ui/{OnlineLobbyPage,PvpCanvas}.java,
src/main/java/page/MainPage.java, pom.xml, build.gradle.kts, docs/ONLINE_PVP_JA.md.
- [x] Integrate build overlays and run a complete compile before wiring UI.
- [x] Add create/join controls, password, side selection, lineup, transfer progress,
  two-player ready state, battle inputs and explicit disconnect/error handling.
- [x] Add server/client launch scripts and deployment instructions.
- [ ] Run all tests locally and full build in CI, inspect the diff and report
  tested versus untested behavior without claiming internet deployment.

## Validation record

Local JDK21 compilation and 268 assertions passed, including independent JVMs through tick240.
Two additional complete repeated runs also passed after fixing the socket-close lock inversion.
See docs/ONLINE_PVP_JA.md for full-pack sharing scope and untested real-asset/WAN combinations.
