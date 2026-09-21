# Hybrid Transport Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Deliver authenticated UDP battle traffic with WebSocket control/fallback, N-participant room infrastructure, friend-server launchers, and direct Release ZIP downloads.

**Architecture:** A transport-independent RoomServerCore owns RoomSession, Participant and LockstepState. WebSocket/UDP adapters normalize input; the unchanged duel engine is reached through a DuelRoster adapter. Room-scoped, bounded disk bundles replace two-peer streaming.

**Tech Stack:** Java 21 toolchain / Java 8 bytecode; existing Java-WebSocket 1.6 and Gson; JDK DatagramSocket and AES/GCM. No native runtime dependency.

**Spec:** docs/superpowers/specs/2026-09-17-pvp-transport-multiplayer-design.md (approved on feature/online-pvp-30tps, fd5c583)

## Global constraints

- Fixed 30 TPS; display 30/60 FPS independently; only DUEL_1V1 is playable.
- Technical capacity 8; PlayerId is independent of seat/team. Never use 1-slot in generic core/transport.
- Control/Pack transport WSS (explicit loopback/private-network WS only for testing/trusted VPN).
- UDP <=1200 bytes; AES-256-GCM, independent HMAC-derived C2S/S2C keys, per-direction nonce prefix + sequence, 64-packet anti-replay, ack + 32-bit ackBits.
- Four-tick normal redundancy plus requested missing tick; bounded 128-tick history; timer-driven retransmission even while simulation stalls.
- Up to four hello packets 250 ms apart; 1500 ms probing fallback. Three seconds without authenticated UDP while active aborts the match.
- 250 ms stalled progress enables bounded WebSocket emergency recovery; no silent midmatch transport switch.
- Input delay 3..8 ticks fixed for each match. No automatic midmatch adaptation.
- Preserve upstream common gitlink and PvE rules. Do not overwrite editor/user assets.
- Publish pvp-dev prerelease assets only after Maven AND Gradle succeed on the feature branch; diagnostics remain Actions artifacts.

## Task 1: Core, identities, and cache
Files: online/net/config/ServerConfig.java; online/net/core/{GameMode,Duel1v1Mode,Participant,RoomSession,LockstepState,ControlPeer,RoomServerCore}.java; online/net/bundle/BundleStore.java; tests/TransportCoreTests.java.
Interfaces: LockstepState(Collection<Integer>, int maxAhead), input(int,long,int), resolve(), checkpoint(int,long,String), nextInput(int); ResolvedFrame(long,Map<Integer,Integer>). BundleStore.offer(String,long), append(String,byte[]), finish(String), open(String), close().
- [x] Write/observe failing tests for eight identities, out-of-order input, conflicting duplicates, late checkpoints, configuration bounds, deduplicated uploads and cleanup.
- [x] Implement immutable participant-keyed frames and bounded lockstep/cached history.
- [x] Implement room-scoped streaming cache with content hashes, strict file quota, cleanup of readers/writers before deletion.
- [x] Implement core lifecycle using ControlPeer (no socket imports), explicit mode registry and start barrier.
- [x] Run tests and retain baseline duel tests when adapting protocols.

## Task 2: Authenticated UDP and reliability
Files: online/net/realtime/{RealtimeData,UdpPacketCodec,ReplayWindow,ReliabilityWindow,TrafficPacer,UdpLink,UdpRealtimeServer,UdpRealtimeClient,RealtimeTransport,WebSocketRealtimeTransport}.java; tests/UdpTransportTests.java.
Interfaces: UdpPacketCodec(master,sessionId,server), encode(type,seq,ack,bits,tick,body), decode(bytes); ReplayWindow.accept(long); RealtimeData.toBytes()/fromBytes(byte[]). UDP adapters deliver authenticated normalized data outside codec locks.
- [x] Test tampering, wrong session/direction, unsigned sequence ordering, window edges, nonce exhaustion, MTU and malformed body before implementing.
- [x] Implement four-tick redundancy with cumulative application acknowledgments and one explicit old-hole retransmission; packet ACKs are not confused with applied ticks.
- [x] Add timer-driven UDP probing, endpoint pinning, idle keepalive, bounded RTT/loss-aware pacing and active timeout.
- [x] Ensure no lock is held across application callbacks/socket close.
- [x] Run deterministic codec/replay/reordering/loss tests.

## Task 3: End-to-end control/client integration
Files: online/net/{Protocol,RoomServer,RoomClient}.java; online/net/duel/DuelRoster.java; online/ui/OnlineLobbyPage.java; online/bundle/MatchBundle.java; tests/{NetworkTests,CheckpointTests,HeadlessPeer,HybridNetworkTests,UdpLossProxy}.java.
Interfaces: Listener.bundle(int playerId,Path,String), ready(), pollResolvedFrame(); DuelRoster.read(JsonObject), toDuel(ResolvedFrame), indexOf(playerId).
- [x] Write socket tests for UDP/UDP, UDP/WS, WS/UDP, WS/WS, blocked UDP, reordered/duplicate/burst loss, emergency rescue and timeout.
- [x] Replace two-peer arrays with core delegation and per-participant upload offers / requested cache downloads.
- [x] Adapt duel frontend and tests to participant roster; allow stable participant IDs for pack namespaces.
- [x] Verify one canonical input schedule through both transports; never fabricate missing commands.
- [x] Run existing real-engine and independent-JVM tests, including 30/60 FPS display independence.

## Task 4: Friend operation and release
Files: online/ui/FriendServerPanel.java; online/net/{PvpServerMain,ServerHost}.java; distribution/{start-pvp-server.bat,start-pvp-server.sh,pvp-server.properties}; .github/workflows/pvp-ci.yml; tools/package-pvp.sh; docs/ONLINE_PVP_JA.md.
- [x] Write launcher/config/lifecycle and distribution structure checks.
- [x] Add embedded start/stop UI and advertised address candidates without opening routers/firewalls.
- [x] Add properties-based dedicated startup, validation, helpful bind failures and shutdown cleanup.
- [x] Upload the uncompressed portable directory to Actions (one ZIP layer) and publish the exact portable ZIP + SHA256 as pvp-dev assets after both CI builds pass.
- [ ] Verify ZIP contents and hashes, both clean builds, Release asset names and commit, then update PR #1. Report actual WAN/OS coverage honestly.

## Verification commands

```sh
bash tools/test-pvp-local.sh
mvn -B -ntp clean verify dependency:copy-dependencies -DoutputDirectory=target/lib
./gradlew --no-daemon check
bash tools/package-pvp.sh
(cd target && sha256sum -c bcu-pvp-portable.zip.sha256)
```

Local recovery compiles overlays/online changes against the verified previous portable JAR; clean Maven/Gradle builds run in GitHub Actions. Source is recovered through an authenticated connector artifact because this container cannot resolve github.com. No runtime secrets enter repository files or logs.

## Local execution evidence

2026-09-17 recovery: the remote branch contained only three of six transfer pieces. Complete source records were restored, and missing realtime/core/UI/test implementations were completed in this session. The previous 268-assertion build was checked as a baseline. The current local suite passed 3433 assertions, including independently executed JVMs, UDP/WS combinations, packet loss injection, eight participants, two concurrent rooms, URI security boundaries and startup port cleanup. Three packaging regression tests also passed. The BindException in the test log is intentionally triggered to verify cleanup after TCP bind failure. Clean Maven/Gradle CI and direct Release publication remain the final gates; older interrupted-session assertion counts are not evidence for this build.
