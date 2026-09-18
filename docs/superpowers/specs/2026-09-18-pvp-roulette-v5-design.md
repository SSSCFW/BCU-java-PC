# PvP roulette v5 design

## Goal

Add the Tobidasu! Battle Cats versus roulette as a mutually exclusive host rule with the existing cannon and a disabled option. Keep the canonical battle at 30 TPS and derive every roulette result from deterministic match state.

## Reverse-engineered 3DS behavior

Evidence was taken from the user's `code(1).bin` plus the extracted CSP tables in `SSSCFW/BattleCats3DS_analyze`.

The roulette contains 14 results in this order:

1. knockback
2. heal
3. production recovery
4. cat cannon
5. production shorten
6. worker efficiency up
7. cost down
8. money max
9. slow
10. stop
11. attack up
12. HP up
13. move speed up
14. petit baby rush

The 43-slot source reel has weights:
`[5,2,3,4,3,2,2,2,3,3,4,4,4,2]`.
The native constructor shuffles the 43 slots and repairs adjacent equal results.

The native effect dispatcher shows:
- heal restores one half of max HP and clamps to max;
- production recovery zeroes all ten production cooldowns;
- production shorten halves current cooldowns and permanently raises the shortening modifier;
- worker efficiency progresses 100 -> 150 -> 200 -> 300 -> 500;
- cost-down doubles its modifier up to 16;
- money-max fills current money to max;
- slow and stop use 150 native ticks;
- attack, HP, and move-speed bonuses are permanent for the match and use native multiplier levels
  `1.5x, 2.5x, 4.5x, 8.0x`;
- attack/HP/move have four positive upgrade applications, matching the 3DS UI labels
  Level 1 / Level 2 / Level 3 / MAX.

The game's bookkeeping also uses a saturated 0..4 stock/display value. Therefore the implementation represents the permanent-bonus stock as five states including the unboosted/base state, while four roulette hits advance Level 1 -> 2 -> 3 -> MAX. It must not invent a fifth bonus multiplier beyond the binary's 8.0x MAX.

## Match rule

Add `BattleSpecialMode`:
- `CANNON` (default; preserves existing rooms)
- `ROULETTE`
- `NONE`

Only the host edits it. Changing it bumps the room rules revision and clears ready state.

Because this changes deterministic battle semantics, bump protocol v4 to v5.

## Simulation

Each physical player owns a `PvpRouletteState` inside `PvpStageBasis`. It is part of clone/hashable canonical state.

The roulette gauge is 0..1000. It fills from battlefield activity/frontline pressure using only canonical positions and events. At 1000 it automatically begins spinning, matching the 3DS state-machine behavior. The normal special-action button/key (the former cannon input bit) stops an active reel. A stopped reel advances deterministically through the shuffled 43-slot sequence and applies one effect.

For networking the existing bit 11 becomes semantic `SPECIAL`; `CANNON` remains an alias. This avoids widening realtime packets.

`NONE` ignores special input and shows no usable special meter.

## Effect ownership

Effects target the roulette owner unless the original effect is hostile:
- knockback: opponent deployed units;
- heal: owner's deployed units;
- production recovery/shorten, worker efficiency, cost down, money max: owner;
- slow/stop: opponent deployed units;
- attack/HP/move: owner, existing and future units;
- roulette cannon: owner fires the normal basic cannon immediately regardless of the room's special mode;
- petit baby rush: owner gets **10 seconds (300 logic ticks at fixed 30 TPS) of zero production cooldown only**. It does not alter worker level or money and is distinct from the paid God Baby Rush miracle.

Permanent modifiers live on each `StageBasis` player state and persist until the match ends.

## UI

Lobby adds a host-only combo:
`にゃんこ砲 / 対戦ルーレット / なし`.

Battle keeps the original BCU battle UI. In roulette mode the cannon-side control is overlaid with a compact roulette meter/result/level indicator rather than replacing the battle HUD.

In cannon mode both physical sides use the normal pink cannon wave animation. Unit waves remain left=blue, right=pink.

## Tests

Cover:
- rules JSON/server host validation and v5 rejection of v4;
- all 14 result mappings;
- exact 43-slot weights/no adjacent duplicate after shuffle;
- deterministic same-seed results and clone equality;
- permanent levels and MAX saturation, including no 5th multiplier;
- immediate/temporary effects and 150-tick expiry;
- SPECIAL behavior for CANNON/ROULETTE/NONE;
- both physical sides;
- cannon wave visual pink on both sides;
- 30/60 render mix and UDP/WS lockstep unchanged;
- Swing lobby combo and native battle page.
