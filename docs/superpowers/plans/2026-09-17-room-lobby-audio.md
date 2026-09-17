# Room lobby and local audio implementation plan

> Use executing-plans and test-driven-development for this approved change.

**Goal:** Editable in-room lobby, host rules, individual live volume, local-only economy notifications.
**Architecture:** A versioned EDITING -> PREPARING -> STARTED server barrier; existing native battle UI with local audio/60FPS adapters.
**Tech Stack:** Java 21 build, Java 8 bytecode, Swing, current WebSocket/UDP stack.
**Spec:** docs/superpowers/specs/2026-09-17-room-lobby-audio.md

- [x] Baseline: recover exact 772738b source; run existing test suite.
- [x] Server: tests for editing barrier/authority/stale revisions; implement RoomRules and lobby state. Update test peers for explicit readiness.
- [x] Simulation/audio: tests for distance and sound ownership/noninterference; add rules-aware arena and scoped sound routing.
- [x] Local audio: fake-clip tests for playing gain updates and mute; add sliders and nonmodal native-battle dialog.
- [x] UI: dedicated room page, existing BasisPage editor, host-only settings, revision/readiness display; maintain native battle UI and friend-server lifetime.
- [ ] Regression: real Swing separate JVMs with edited lineup/rules/60FPS/audio; protocol transport suite and clean builds.
- [ ] Publication: direct source commit to feature branch, PR update, CI, tested pvp-dev ZIP. Never touch upstream common gitlink.

## Local regression evidence

- Before fixes: admission automatically emitted prepare; remote production notifications leaked; active effect gain did not change with sliders.
- Native BasisPage editor and both separate Swing clients tested with synthetic assets. Host distance8000/background4/music7/forced60 applied to both simulations.
- Volume dialog remained open and nonmodal while 150 simulation ticks ran; each client retained its own different volume.
- Review caught and fixed a retained lineup listener after room exit and a late STOP callback re-enqueuing a released audio clip.
- Standard resource availability and typed/ranged rules tested. A forced FPS override leaves the normal global preference unchanged.
- No real Windows/JOGL/audio-device/WAN or full real-game-asset validation is claimed.
