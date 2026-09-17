# BCU PvP transport / multiplayer-ready server design

Date: 2026-09-17
Branch: `feature/online-pvp-30tps`
Status: approved direction, implementation pending

## 1. Goals

This change keeps the current 1v1 game mode, but restructures the room/network layer so it does not need to be rewritten when 3+ player modes are added later.

It also separates reliable control/data transfer from latency-sensitive battle traffic:

- WSS/WebSocket over TCP for room/auth/configuration/custom-Pack transfer.
- Authenticated UDP for realtime battle input/frame traffic.
- WebSocket realtime fallback when UDP is unavailable.
- Fixed 30 simulation TPS remains independent from each client's 30/60 FPS renderer.
- The current game mode remains `DUEL_1V1`; server data structures may support more participants, but no unsupported multiplayer battle mode is exposed yet.
- GitHub Actions test artifacts remain available for diagnostics, while the user-facing portable ZIP is published directly as a rolling GitHub Release asset.

## 2. Non-goals for this iteration

- No 2v2, FFA, co-op, spectators, reconnect/resume, matchmaking, ranking, or competitive anti-cheat.
- No P2P networking, NAT hole punching, STUN/TURN, or peer-hosted authoritative simulation.
- No QUIC dependency in this iteration. The transport interface is designed so QUIC could replace the WSS+UDP pair later without changing battle/room code.
- No change to the existing deterministic 30 TPS battle rules beyond adapting network input containers from two fixed slots to participant IDs.

## 3. Architecture

The server is split into transport-independent room state and two transport planes.

```text
                     RoomServerCore
                  /        |         \
          RoomRegistry  MatchCore  BundleStore
                |           |          |
                +-----------+----------+
                            |
                +-----------+-----------+
                |                       |
          ControlPlane             RealtimePlane
             WSS/TCP               UDP preferred
                |                  WS fallback
                |                       |
       room/auth/config/pack        input/frame/ack
```

`RoomServerCore` owns rules and room state. WebSocket and UDP adapters may parse/serialize packets, but they do not directly mutate battle state except through core methods.

This prevents the present `RoomServer` class from continuing to combine WebSocket callbacks, room lifecycle, asset transfer, lockstep scheduling, authentication, and player indexing in one class.

## 4. Participant model

Replace fixed-length arrays and `1-slot` peer selection with participant objects.

```text
RoomSession
  roomId
  matchId
  gameFingerprint
  GameMode gameMode
  Map<PlayerId, Participant> participants
  BundleStore bundles
  LockstepState lockstep
  room lifecycle timestamps
  password verifier

Participant
  PlayerId id
  displayName
  Seat seat
  TeamId team
  controlConnection
  RealtimeTransport realtimeTransport
  udpEndpoint
  udpSessionId
  bundleHash
  ready
  nextInputTick
  lastCheckpointTick
  connection/liveness timestamps
```

A `PlayerId` is an opaque server-issued integer/UUID-like identifier and is not inferred from array position.

### Current game mode

`DUEL_1V1` exposes exactly two seats:

- `LEFT`, team 0
- `RIGHT`, team 1

The room core has a configurable technical participant capacity (default 8), but `DUEL_1V1.maxPlayers()` returns 2, so a third player is rejected today.

Future modes can define their own seat/team rules without changing transport code.

## 5. GameMode interface

Introduce a small immutable game-mode contract. It defines:

- mode ID / protocol name
- min/max player count
- available seats
- team mapping
- readiness/start validation
- conversion between participant inputs and the battle representation
- winner/result validation rules

Only `DUEL_1V1` is implemented in this iteration.

No generic multiplayer battle simulation is attempted until a future mode explicitly defines how more than two castles/teams behave.

## 6. Control plane: WSS / WebSocket

Keep WebSocket for traffic that benefits from reliable ordered delivery:

- create/join/leave
- password authentication
- protocol/game fingerprint negotiation
- participant roster and seat assignment
- game mode and match configuration
- readiness
- custom Pack manifests and bytes
- UDP session bootstrap
- explicit fallback signalling
- fatal match/control errors

For LAN/private-network testing, development `ws://` remains possible. Public Internet operation remains `wss://` by default.

### Passwords

Keep salted PBKDF2 verification in memory. Passwords are never persisted. Authentication/rate limits remain enforced in the room core/control adapter.

## 7. Realtime plane: authenticated UDP

Realtime battle traffic uses UDP when available.

The client first establishes WSS control connectivity and joins a room. The server then issues:

- `udpSessionId`
- random per-session AEAD key material
- UDP server endpoint/port
- protocol version and input-delay bounds

The client sends a UDP hello authenticated with the issued key. The server binds the observed source address/port to that participant after successful authentication.

No client accepts unauthenticated realtime datagrams.

### Packet size

Keep UDP datagrams at or below 1200 bytes by design to avoid relying on IP fragmentation across typical Internet paths.

### Encryption/authentication

Use AES-GCM with a per-session key and unique nonce derived from session+sequence. Associated data includes protocol version and session ID. Packets with invalid tags, stale windows, invalid participant IDs, or replayed sequence numbers are dropped before entering the room core.

The WSS control connection is the trust bootstrap for UDP key exchange.

## 8. Reliable-UDP behaviour

UDP is not made globally reliable like TCP. Reliability is applied only to battle data whose loss would prevent deterministic lockstep.

Each realtime packet has at least:

```text
version
udpSessionId
sequence
ack
ackBits
latestTick
payloadType
payload
AEAD tag
```

`ack` acknowledges the highest received packet sequence. `ackBits` is a selective-ack bitmap covering the preceding packet window.

### Input redundancy

Client input packets include the newest scheduled input plus a short history of recent/unconfirmed input ticks, for example:

```text
latestTick=104
inputs: 102, 103, 104
```

Therefore a lost packet normally recovers from the next datagram without waiting for a timeout/retransmission request.

The exact redundancy window is bounded and configurable; initial target is 3-6 input ticks, subject to 1200-byte packet size.

### Explicit retransmit

If a required tick remains missing as its execution deadline approaches, the server sends a small missing-tick/retransmit request. The client immediately includes that tick in its next datagram.

The match never fabricates an input for a missing participant and never advances a deterministic tick until all required inputs for that tick are available.

### Server frame redundancy

Resolved server frames are also sent with recent frame history. Clients deduplicate by tick and sequence. A single lost server datagram is therefore normally repaired by the next datagram.

### RTT / jitter / loss metrics

Track per-participant:

- smoothed RTT
- RTT variance / jitter estimate
- recent packet-loss estimate
- last UDP receive time

These metrics are diagnostic in this iteration and provide the basis for future adaptive input delay.

## 9. Input delay

Simulation remains fixed at 30 TPS.

The existing three-tick delay is retained as the initial default, but it moves from a compile-time universal constant to match configuration:

```text
inputDelayTicks = 3
minInputDelayTicks = 3
maxInputDelayTicks = 8
```

The wire protocol therefore does not assume exactly 3 forever.

Automatic delay adaptation is not required to change during an active match in this iteration; the architecture only makes it possible later. The negotiated delay is fixed once the battle starts to avoid adding a second source of determinism complexity now.

## 10. WebSocket realtime fallback

UDP probing begins only after WSS room/auth setup succeeds.

State machine:

```text
CONTROL_CONNECTED
  -> UDP_PROBING
      -> UDP_ACTIVE
      -> WS_REALTIME_FALLBACK
```

If UDP hello/ack does not complete within a short bounded probe window, the participant automatically uses WebSocket realtime messages.

A room may contain mixed transports in future server tests:

```text
P0 UDP
P1 WebSocket fallback
P2 UDP
```

The room core sees only normalized `RealtimeInput` and `ResolvedFrame` messages; it does not care which transport delivered them.

For the current `DUEL_1V1`, both combinations must be tested: UDP/UDP, UDP/WS, WS/WS.

If an already active UDP path dies during a battle, this iteration fails the match safely rather than silently switching transports mid-tick. Mid-match migration can be added later with an explicit synchronization barrier.

## 11. Lockstep state generalized for N participants

Replace:

```text
int[2] frame
String[2] checkpoint
```

with keyed state:

```text
Map<Tick, Map<PlayerId, InputMask>> pendingInputs
Map<Tick, Map<PlayerId, Hash>> checkpoints
```

For performance, the implementation may use indexed arrays internally after assigning compact participant indices, but no API may encode `otherPlayer = 1 - slot` or assume exactly two players outside `DUEL_1V1` conversion code.

A tick is resolved when every active participant required by the current game mode has supplied input.

Checkpoint comparison uses all required participants. For `DUEL_1V1`, both hashes must match as today.

## 12. Bundle / custom Pack distribution

Asset transfer stays on reliable WSS/TCP.

Instead of relaying each uploader directly to exactly one opponent, introduce a room-scoped `BundleStore`:

- key bundles by SHA-256
- stream upload to bounded temporary files rather than holding entire Pack ZIPs in heap
- deduplicate identical hashes
- validate size and checksums before publication
- serve required bundles to each participant that lacks them
- remove room temporary files on room close/timeout/server shutdown

This is useful today and avoids O(N^2) repeated asset uploads/download planning later.

The existing namespace remapping/isolation rules for custom Packs remain unchanged.

## 13. Server operation modes

Use the same `RoomServerCore` in both deployment styles.

### Embedded friend server

Add a lobby action such as `このPCで友人用サーバーを起動`.

It starts the control WebSocket server and UDP realtime socket using the same core. The UI displays detected candidate addresses and ports. It does not automatically configure routers, NAT, firewalls, Tailscale, or TLS.

### Dedicated server

Provide:

- `start-pvp-server.bat`
- `start-pvp-server.sh`
- `pvp-server.properties`

Example configuration:

```properties
bind=0.0.0.0
controlPort=8766
udpPort=8767
maxRooms=32
maxConnections=128
maxParticipantsPerRoom=8
roomIdleMinutes=10
bundleMaxMiB=32
friendsMode=true
```

Configuration values are validated and bounded at startup.

For public Internet use, WSS/TLS termination may remain in Nginx for the control plane. UDP is exposed directly on its configured UDP port; firewall documentation must state this explicitly.

## 14. Release/download design

The existing GitHub Actions artifact contains `bcu-pvp-portable.zip` inside GitHub's artifact ZIP wrapper. GitHub Actions artifacts remain useful for test reports and diagnostics, but they are not the primary user download.

CI adds a rolling development release, e.g. tag `pvp-dev`, whose assets are uploaded directly:

- `bcu-pvp-portable.zip`
- `bcu-pvp-portable.zip.sha256`

The release asset is therefore directly downloadable as the actual portable ZIP, without an outer Actions-artifact ZIP.

Release publication runs only after the Maven and Gradle verification jobs pass on the feature branch. Release permissions are scoped to `contents: write` only for the release job; ordinary test jobs keep read-only contents permission.

## 15. Error handling

Transport errors are classified rather than collapsed into generic room closure:

- control protocol/auth/version errors
- bundle validation/transfer errors
- UDP authentication/replay errors
- UDP probe failure -> fallback before battle
- realtime timeout/missing required tick
- deterministic checkpoint mismatch
- participant disconnect

Before a match starts, recoverable transport failures may return the participant to lobby state. During a started deterministic battle, ambiguous realtime failure ends the match safely rather than guessing state.

## 16. Testing

### Unit tests

- participant collection and stable PlayerId/index mapping
- `DUEL_1V1` still rejects participant 3
- no `1-slot` assumptions remain in generic room/transport code
- selective ACK bitmap handling
- duplicate/out-of-order/replayed UDP datagrams
- AES-GCM tag failure/replay-window rejection
- redundant-input recovery after dropped datagrams
- explicit missing-tick retransmission
- bundle deduplication and cleanup
- bounded configuration parsing

### Integration tests

Use actual loopback sockets/processes:

1. WSS/WS control + UDP/UDP 1v1.
2. Drop selected client->server UDP packets and prove input is recovered from redundant history without desync.
3. Drop selected server->client frame packets and prove frame history recovers them.
4. Force enough loss to require an explicit missing-tick retransmit.
5. Block UDP and verify pre-match automatic WebSocket realtime fallback.
6. Mixed UDP/WS participants with identical deterministic hashes.
7. Existing 30FPS/60FPS mixed-render test remains deterministic.
8. Multiple rooms do not leak bundles, participant IDs, keys, or UDP endpoints into one another.
9. Technical room structures can hold >2 participants under a test-only mode/stub without changing transport/core APIs; production still exposes only `DUEL_1V1`.
10. Server shutdown removes temporary bundle files and terminates both control/UDP worker threads.

### CI/release tests

- clean Maven `verify`
- Gradle `check`
- portable ZIP creation
- ZIP content validation
- SHA-256 generation
- release job gated on successful build/tests

## 17. Compatibility / migration

This feature branch is still pre-release, so protocol compatibility with the earlier test build is not required. Increment protocol/engine identifiers when the new transport is merged into the branch. Old clients must fail with a clear version mismatch rather than partially connecting.

Existing offline/PvE behaviour and the original common git submodule remain unaffected by transport changes.

## 18. Implementation boundaries

Expected focused units:

```text
online/net/core/RoomServerCore
online/net/core/RoomSession
online/net/core/Participant
online/net/core/GameMode
online/net/core/Duel1v1Mode
online/net/control/WebSocketControlServer
online/net/realtime/RealtimeTransport
online/net/realtime/UdpRealtimeServer
online/net/realtime/UdpPacketCodec
online/net/realtime/ReliabilityWindow
online/net/realtime/WebSocketRealtimeTransport
online/net/bundle/BundleStore
online/net/config/ServerConfig
online/net/PvpServerMain
```

The exact package layout may be adjusted to match existing naming, but the boundaries above are required: room rules, control WebSocket, UDP reliability, bundle storage, and configuration must not collapse back into one monolithic server class.

## 19. Acceptance criteria

The change is complete when:

- current 1v1 behaviour works with fixed 30 TPS and independent 30/60 FPS rendering;
- battle traffic uses authenticated UDP by default after successful WSS bootstrap;
- ordinary single-packet loss is recovered without waiting for TCP or desynchronizing;
- prolonged missing required inputs stall safely and use explicit retransmit requests;
- UDP-unavailable clients automatically select WebSocket realtime before battle start;
- generic room/server code has no fixed-two-player peer arrays or `1-slot` logic;
- production still limits `DUEL_1V1` to exactly two players;
- custom Pack transfer remains reliable and supports bundle deduplication;
- embedded and dedicated friend-server launch paths share one server core;
- CI publishes `bcu-pvp-portable.zip` directly as a GitHub Release asset after tests pass;
- Maven/Gradle/integration tests pass in a clean CI environment.
