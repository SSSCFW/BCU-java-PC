# Lobby usability fixes implementation plan

> **For agentic workers:** Use executing-plans and test-driven-development task by task.

**Goal:** Make room passwords optional, persist display name/server address, and make same-PC hosting/joining/retrying reliable.
**Architecture:** Preserve protocol v2 and the hybrid transport. Keep optional authentication in the room core, store only the two non-secret fields in an atomic per-installation properties file, and separate a lobby connection attempt from the embedded host lifecycle.
**Tech Stack:** Java 8-compatible sources on Java 21; Swing; Java-WebSocket; existing main-based tests plus real Swing tests under Xvfb.
**Spec:** User request on 2026-09-17 and the existing hybrid transport specification. No extra approval required.

## Global constraints
- Work on feature/online-pvp-30tps; do not change the common submodule or offline battle behavior.
- Empty password disables the password gate. A newly set password remains 8–128 characters; empty/wrong input on a protected room must be AUTH, not a protocol crash.
- Save display name and server text only, never password, room ID, UDP keys, or implicit plaintext trust.
- Never disable WSS checks for public hosts. Loopback WS needs no private-network consent.
- Room leave/retry preserves the running embedded server; leaving the online screen closes it.
- No missing input is fabricated; fixed 30TPS and 30/60FPS independence remain unchanged.

## Tasks
- [x] Add failing real-socket password tests in `src/test/java/online/tests/PasswordRoomTests.java`; run against the published v2 JAR and capture blank-password rejection.
- [x] Add actual Swing regressions in `src/test/java/online/ui/LobbyUiTests.java` for saved fields, room-ID status, local connection helper, retry/leave host retention. Run against the published JAR under `xvfb-run`.
- [x] Implement optional password policy in `online/net/core/PasswordVerifier.java` and `RoomServerCore.java`; add `passwordRequired` to joined metadata.
- [x] Add `online/ui/LobbyPreferences.java` (`load(Path,String)`, `save(Path)`) and bind it to debounced field changes and connection/exit boundaries.
- [x] Repair `OnlineLobbyPage` attempt ownership with a generation-guarded listener. Cancel/reset a match without closing `FriendServerPanel`; restore setup controls after failure; retain room identity in status output.
- [x] Extend `FriendServerPanel` with an explicit same-PC URL helper using the BCU folder's server config. This must not start a second server or alter firewall/router settings.
- [x] Test two independent actual Swing clients over loopback with a blank room password through share/ready/battle ticks, plus retries and saved settings across JVMs. Preserve transcripts in CI.
- [ ] Publish the verified source/CI updates, verify clean Maven/Gradle/package/release jobs, and update PR #1. Local source and actual Swing checks are complete.

## Verification commands
```sh
mvn -B -ntp clean verify dependency:copy-dependencies -DoutputDirectory=target/lib
xvfb-run -a java -ea -Dfile.encoding=UTF-8 -cp 'target/test-classes:target/classes:target/lib/*' online.ui.LobbyUiTests
./gradlew --no-daemon check
python3 tools/test-package-pvp.py
bash tools/package-pvp.sh
```

## Initial observations
The old published JAR's 3433 assertions pass. An actual Swing host can start and connect over loopback with a valid password, but the password validator rejects blank input before connecting, connection failures permanently disable setup until the user exits, exit destroys the embedded host, and transport status overwrites the room-ID instructions. The original report has no error text; do not present any one observation as a proven explanation of the user's exact failure. Exercise the real lobby rather than only RoomClient in the regression suite.

## Local verification

Old published JAR: blank room creation fails with `PROTOCOL: Name, 8+ character password and game fingerprint required`; saved-field, retry-enable, local-helper, and room-ID-status regressions fail as expected.

Updated classes: core runner passes 3453 assertions; actual Swing processes pass save/load across restart, failed-connect retry, host retention after room leave, stale callback isolation, BCU-folder config selection, and empty-password two-client play for 150 ticks with 30/60FPS. This uses generated assets on Linux/Xvfb, not the user's Windows machine or full original assets.
