# PvP roulette v5 implementation plan

1. Extend room rules/protocol with BattleSpecialMode and v5; add host lobby control and tests.
2. Add deterministic PvpRouletteState with exact 43-slot weighted reel, spin/stop state, per-player permanent modifiers, clone-safe fields and tests.
3. Route SPECIAL input in PvpStageBasis for CANNON/ROULETTE/NONE and disable ordinary cannon charging/usage where appropriate.
4. Implement the 14 roulette effects and integrate persistent modifiers with economy, ELineUp, EUnit attack/HP/move, and temporary slow/stop.
5. Add compact roulette rendering to the native BattleBox HUD while preserving standard battle controls.
6. Remove the PvP left-side blue cannon-wave override so both cannon waves are pink.
7. Update checkpoint hashing/docs/package metadata/release text for protocol v5.
8. Run Maven, Gradle, Swing two-client, UDP/WS, packaging and release verification; fast-forward feature/online-pvp-30tps only after green.
