# Hybrid transport validation

The v2 implementation replaces the old two-slot WebSocket relay with participant-keyed room state, authenticated UDP realtime transport and pre-match WebSocket fallback. The production game remains DUEL_1V1; the eight-participant mode exists only in integration tests.

## Verified before integration

- Local Java 21 test runner: 3433 assertions passed.
- Clean GitHub-hosted Java 21 / Maven `clean verify`: 3433 assertions passed, including independent JVM combat at 240 ticks.
- Packaging regression suite: 3 tests passed, including deterministic archive reconstruction and rejection of nested/unsafe layouts.
- Assembly run: https://github.com/SSSCFW/BCU-java-PC/actions/runs/35204451653 . Its source verification and Maven test steps passed. Its final push was rejected because the Actions token cannot update workflow files; the authenticated GitHub connector is used to integrate the verified tree instead.

The regular PvP build workflow reruns Maven and Gradle on the integrated commit. Only successful feature-branch builds publish the rolling `pvp-dev` development release. The Release ZIP and checksum are the primary downloads; Actions stores the extracted distribution directory rather than wrapping an existing ZIP.

## Scope of tests

Real loopback sockets cover UDP/UDP, UDP/WS, WS/UDP, WS/WS, loss, duplication, reordering, emergency retransmission, blocked-UDP fallback, timeout cleanup, concurrent rooms and eight participant identities. Synthetic character assets drive real BCU battle logic in separate JVMs with 30/60 FPS rendering and matching state hashes.

These results do not certify real WAN conditions, every custom ability combination or every Windows/macOS/JOGL configuration. Use a copy of the existing BCU installation. These historical results were for protocol v2. The editable-room update requires all clients and the server to use v3; current regression evidence is in `docs/superpowers/plans/2026-09-17-room-lobby-audio.md`.
