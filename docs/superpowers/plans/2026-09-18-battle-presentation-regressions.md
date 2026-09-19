# Battle presentation regression repair

Goal: preserve the requested roulette sounds and end sequence on both peers,
including entering directly in large mode, muted/unavailable audio, and missing
Java Sound STOP notifications. Work branch: feature/online-pvp-30tps.

Observed with the last released artifact: initial-large Canvas z-order hides
rouletteDebugMax; global Page.fontSetter shrinks result headings; muted audio
skips the entire ending; publishing gauge changes without painting emits no sound.
All eight supplied OGGs decode to non-silent PCM (FFmpeg and the shipped Vorbis SPI).

1. Reproduce first with BattlePresentationTests and opt-in RecordingMixerProvider.
   It records actual Java Sound PCM, not audible hardware output. Preserve RED logs.
2. BattleInfoPage: keep native Canvas behind controls on initial construction and
   resize; validate the top-level AWT hierarchy. Freeze a HUD-free scene into a
   lightweight backdrop on ending, then hide the native Canvas to eliminate mixed
   heavyweight/lightweight result occlusion. Protect title and button fonts from
   Page.fontSetter. Make result presentation and acknowledgement idempotent.
3. Observe roulette changes on canonical ticks (listener-local, no RNG/wire/state
   modifications), initialized from the initial snapshot. Publish-only fallback
   must work without rendering. Separate start/loop/confirm and opponent events.
4. PvpSoundBank: cached PCM, device I/O off EDT, stop/release every clip, cancellation
   fence for late tasks, all-volume refresh, natural-end callback plus bounded
   missing-STOP recovery, and equal presentation duration when muted/unavailable.
5. Regression gates: real Swing initial-large/small + resize + result screenshots;
   all eight decoded nonzero clips; start/stop/loop/master mute/missing STOP;
   two real RoomClient JVMs finish by castle destruction and timeout, both display
   readable OK and return to the same room after both acknowledgements. Verify
   result music id 30 is started once and is not restarted in the lobby.
6. Run all existing integration tests and Maven/Gradle CI on the final commit.
   Do not equate fake-device PCM tests with listening on the user's Windows device.
