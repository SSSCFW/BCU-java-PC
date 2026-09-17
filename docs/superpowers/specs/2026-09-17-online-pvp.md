# Online PvP design

Approved in chat on 2026-09-17. Target: BCU-java-PC slow_kotlin at
3e77c4bcea0e2a3b236705f4baa853989107108b, common at
8920447e73bea56289a2da5a2a9294e24ff08c67.

## Requirements

Password-protected rooms with exactly two players, user-selected physical castle
side, custom character/asset exchange without overwriting local content, blue
left-side wave visuals with the same mirrored hit geometry as right-side pink
waves. Authoritative simulation runs at 30 ticks per second. Display may run at
30 or 60 FPS independently. Existing single-player behavior remains available.

## Architecture

A Java WebSocket relay owns room membership, input order, the start barrier,
30 Hz pacing and hash comparison. It does not claim cheat-resistant server-side
combat validation. Each game executes the same two-player inputs on a shared
battlefield with separate money, cooldowns, lineups, cannons and castles.

Peer bundles are size-limited, checksummed ZIPs containing a frozen lineup,
required custom packs and animation dependencies. Imported identifiers are
rewritten into an isolated match/player namespace. The original workspace is
never edited. Both peers must acknowledge the same bundle pair before starting.
The default game-data fingerprint and protocol build must match.

Simulation uses full animation steps, independent of the display setting.
Rendering receives a cloned battle snapshot; animation-only updates never touch
the live simulation. Logic hashes exclude rendering/camera state. Missing input
stalls a tick; a timeout or hash mismatch aborts explicitly rather than inventing
input. No reconnect/rollback, spectator, ranking, public matchmaking or automatic
server deployment is in this change.

Transport is WSS in production, or explicit WS for local/private development.
No Java object deserialization; imported JSON class tags are allowlisted. Archive
paths, expanded size, entry count and image dimensions are checked before load.

## Upstream integration

The common repository is a pinned submodule. Modified common classes are retained
as source overlays in src/pvp/java; Maven/Gradle assemble a generated source tree
with those files taking precedence. This avoids uncommitted submodule patches or
changes to an upstream repository not owned by this project.

## Acceptance

Real socket tests for wrong password, room capacity, start barrier, tick ordering,
desync and disconnect. Bundle tests for same name/id separation, dependency
rewriting, checksum failures and traversal rejection. Headless engine tests for
symmetric spawn, damage/waves and identical hashes under 30/60 display schedules.
Build the full desktop application in CI; document any unverified graphical or
external-network behavior separately from automated results.
