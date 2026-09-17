# Editable room lobby and player-local audio

Approved scope: user requested direct implementation on feature/online-pvp-30tps without further confirmation. Preserve the original BCU battle UI.

Connection page -> dedicated RoomLobbyPage -> original BasisPage for editing -> room lobby -> native BattleInfoPage.
Only after every participant confirms the current room revision does the server freeze the roster and request character bundles. The final existing verified-bundle barrier then starts the battle automatically. No editable assets race an active transfer. A rules/lineup change during editing clears everyone's readiness and increases the revision; stale ready requests are nonfatal and receive current state. Prepared/battling rooms cannot be edited.

Protocol v3 has host-only RoomRules: distance BETWEEN the two castle coordinates (default 4400, 1000..24000; stage length = distance + 1600), default-pack background ID, default-pack BGM ID or -1 for silence, force60Fps. Default-pack selections avoid unshared custom background/music dependencies. The server validates ranges/authority and freezes identical rules into prepare/start. Clients validate actual assets before consenting. A room's force60 is a render-only override, never a simulation-rate or saved-settings change. Thirty logic ticks per second stays fixed.

Every client has its own BGM, battle SE and operation/notification volume controls in the room and a nonmodal battle audio dialog. They use the existing local BCMusic settings and normal config persistence. Changes apply to playing clips as well as future clips, including mute. These values are never networked.

Player-owned production/failure/worker/cannon-charge/cooldown sounds are filtered at source using a thread-local local-player context around canonical stepping. Shared battlefield impacts/waves remain audible. Audio filtering cannot change RNG, timers, state hashes or saved settings, and offline behavior remains unchanged.
