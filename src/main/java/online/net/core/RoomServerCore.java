package online.net.core;

import com.google.gson.*;
import online.bundle.Hashes;
import online.net.Protocol;
import online.net.lobby.RoomRules;
import online.net.bundle.BundleStore;
import online.net.config.ServerConfig;
import online.net.realtime.*;
import online.sync.ResolvedFrame;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

/** Transport-independent admission, room lifecycle, asset cache and lockstep coordination. */
public final class RoomServerCore implements AutoCloseable {
    private static final long SECOND = 1_000_000_000L, RESCUE = 250_000_000L;
    private final ServerConfig config;
    private final UdpService udp;
    private final Map<String, GameMode> modes;
    private final Map<String, RoomSession> rooms = new HashMap<>();
    private final Map<ControlPeer, RoomSession> membership = new HashMap<>();
    private final Map<ControlPeer, Participant> participants = new HashMap<>();
    private final Map<ControlPeer, Long> opened = new HashMap<>();
    private final Map<String, long[]> attempts = new HashMap<>();
    private final Set<ControlPeer> authenticating = new HashSet<>();
    private final SecureRandom random = new SecureRandom();
    private final ExecutorService authentication = pool("pvp-auth", 2, 32);
    private final ExecutorService downloads = pool("pvp-bundle-download", 4, 64);
    private boolean closed;
    private long rescueCount, completedUploads;

    public RoomServerCore(ServerConfig config, UdpService udp) {
        this(config, udp, Collections.singletonMap("DUEL_1V1", new Duel1v1Mode()));
    }
    /** Mode injection is used by server-core tests; production exposes only DUEL_1V1. */
    public RoomServerCore(ServerConfig config, UdpService udp, Map<String, GameMode> modes) {
        this.config = config; this.udp = udp; this.modes = Collections.unmodifiableMap(new HashMap<>(modes));
    }
    private static ExecutorService pool(String name, int threads, int capacity) {
        return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(capacity),
                r -> { Thread t = new Thread(r, name); t.setDaemon(true); return t; }, new ThreadPoolExecutor.AbortPolicy());
    }
    public synchronized int roomCount() { return rooms.size(); }
    public synchronized long rescueCount() { return rescueCount; }
    public synchronized long completedUploads() { return completedUploads; }
    public synchronized void opened(ControlPeer peer) {
        if (closed || opened.size() >= config.maxConnections) { error(peer, "BUSY", "Server connection limit"); peer.closeLater(); return; }
        opened.put(peer, System.nanoTime());
    }
    public void text(ControlPeer peer, String text) {
        try {
            JsonObject o = Protocol.parse(text); String type = Protocol.string(o, "type", 32);
            if (type.equals("create") || type.equals("join")) {
                synchronized (this) {
                    if (closed || !opened.containsKey(peer) || membership.containsKey(peer) || !authenticating.add(peer))
                        throw new IOException("Unexpected room admission");
                }
                try { authentication.execute(() -> enter(peer, o, type.equals("create"))); }
                catch (RejectedExecutionException e) { synchronized (this) { authenticating.remove(peer); } error(peer, "BUSY", "Authentication queue is busy"); }
                return;
            }
            synchronized (this) {
                RoomSession room = membership.get(peer); Participant p = participants.get(peer);
                if (room == null || room.closed || p == null) throw new IOException("Not in a room");
                switch (type) {
                    case "rules": case "lineup": case "lobby_ready": case "player_rules": editLobby(room,p,o,type); break;
                    case "transport_select": selectTransport(room, p, Protocol.string(o, "transport", 8)); break;
                    case "bundle": offerBundle(room, p, o); break;
                    case "bundle_end": finishBundle(room, p); break;
                    case "bundle_get": download(room, p, Protocol.string(o, "hash", 64)); break;
                    case "ready": ready(room, p, o); break;
                    case "realtime":
                        if (p.transportState != Participant.TransportState.WS) throw new IOException("Wrong realtime transport");
                        accept(room, p, RealtimeData.fromControl(o)); break;
                    case "rescue":
                        if (p.transportState == Participant.TransportState.UDP && room.started) {
                            RealtimeData data = RealtimeData.fromControl(o);
                            long now = System.nanoTime();
                            if (now - room.progressed >= RESCUE || data.nextExpected < room.lockstep.tick()) {
                                accept(room, p, data);
                                if (now - p.lastRescue >= RESCUE) { p.control.send(outbound(room, p).control("rescue")); p.lastRescue = now; rescueCount++; }
                            }
                        }
                        break;
                    case "result": result(room, p, o); break;
                    case "result_ack": resultAck(room,p); break;
                    case "battle_abort": abortBattle(room,p); break;
                    case "leave": closeRoom(room, "CLOSED", "Player left the room"); break;
                    default: throw new IOException("Unknown request");
                }
            }
        } catch (Exception e) { fail(peer, e); }
    }
    private void enter(ControlPeer peer, JsonObject o, boolean create) {
        PasswordVerifier verifier = null;
        try {
            if (Protocol.integer(o, "version") != Protocol.VERSION || !Protocol.ENGINE.equals(Protocol.string(o, "engine", 128))) {
                error(peer, "VERSION", "PvP versions differ; update both clients and server"); return;
            }
            String name = Protocol.string(o, "name", 40).trim(), password = Protocol.string(o, "password", 128), game = Protocol.string(o, "game", 64);
            if (name.isEmpty() || !Hashes.valid(game)) throw new IOException("Name and game fingerprint required");
            if (create && !password.isEmpty() && password.length() < 8)
                throw new IOException("Password must be empty (open room) or 8-128 characters");
            RoomSession existing;
            synchronized (this) {
                if (!opened.containsKey(peer) || !peer.isOpen() || closed) return;
                long now = System.nanoTime(); long[] a = attempts.get(peer.address());
                if (a == null || now - a[0] > 60 * SECOND) { a = new long[]{now, 0}; attempts.put(peer.address(), a); }
                if (++a[1] > 12) { error(peer, "RATE", "Too many password attempts; wait one minute"); return; }
                existing = create ? null : rooms.get(Protocol.string(o, "room", 32).trim().toUpperCase(Locale.ROOT));
            }
            // Password derivation cannot stall ticks in other rooms.
            if (create) verifier = new PasswordVerifier(password, random);
            else if (existing == null || !existing.password.matches(password)) { error(peer, "AUTH", "Room or password is incorrect"); return; }
            synchronized (this) {
                if (closed || !opened.containsKey(peer) || !peer.isOpen() || membership.containsKey(peer)) return;
                RoomSession room = existing; GameMode.Seat seat;
                if (create) {
                    if (rooms.size() >= config.maxRooms) { error(peer, "BUSY", "Room limit reached"); return; }
                    String modeId = o.has("mode") ? Protocol.string(o, "mode", 32) : "DUEL_1V1";
                    GameMode mode = modes.get(modeId);
                    if (mode == null || mode.maxPlayers() > config.maxParticipantsPerRoom) throw new IOException("Unsupported game mode");
                    seat = mode.assign(Collections.emptyList(), Protocol.string(o, "side", 16));
                    String id; do { byte[] b = new byte[9]; random.nextBytes(b); id = Hashes.hex(b).toUpperCase(Locale.ROOT); } while (rooms.containsKey(id));
                    room = new RoomSession(id, UUID.randomUUID().toString().replace("-", ""), game, mode, config.inputDelayTicks, verifier,
                            config.bundleMaxMiB * 1024L * 1024, config.maxParticipantsPerRoom);
                    verifier = null; rooms.put(id, room);
                } else {
                    if (room.closed || !rooms.containsKey(room.id)) { error(peer, "AUTH", "Room no longer exists"); return; }
                    if (room.started || room.participants.size() >= room.gameMode.maxPlayers()) { error(peer, "FULL", "Game mode participant limit reached"); return; }
                    if (!room.gameFingerprint.equals(game)) { error(peer, "ASSETS", "Default assets or game engine differ"); return; }
                    seat = room.gameMode.assign(room.occupiedSeats(), Protocol.string(o, "side", 16));
                }
                int id; do { id = random.nextInt(Integer.MAX_VALUE - 1) + 1; } while (idInUse(id));
                Participant p = new Participant(id, name, seat, peer);
                room.participants.put(id, p); membership.put(peer, room); participants.put(peer, p);
                if(create)room.hostId=id;
                room.changed();
                JsonObject joined = Protocol.message("joined");
                joined.addProperty("room", room.id); joined.addProperty("match", room.matchId);
                joined.addProperty("playerId", id); joined.addProperty("side", seat.name);
                joined.addProperty("passwordRequired", room.password.required());
                joined.addProperty("inputDelayTicks", room.inputDelayTicks); joined.addProperty("mode", room.gameMode.id());
                peer.send(joined);
                if (udp != null && (!o.has("udp") || o.get("udp").getAsBoolean())) {
                    final RoomSession target = room;
                    p.udp = udp.register(new UdpService.Listener() {
                        public void data(RealtimeData data) {
                            synchronized (RoomServerCore.this) {
                                if (target.closed || !target.started || p.transportState != Participant.TransportState.UDP) return;
                                try { accept(target, p, data); } catch (Exception e) { fail(p.control, e); }
                            }
                        }
                        public void failed(String reason) {
                            synchronized (RoomServerCore.this) { if (!target.closed) closeRoom(target, "UDP_PROTOCOL", reason); }
                        }
                    });
                    peer.send(p.udp.offer(config.udpHost));
                } else selectTransport(room, p, "WS");
                broadcast(room, roster(room, "room_state"));
            }
        } catch (Exception e) { fail(peer, e); }
        finally {
            if (verifier != null) verifier.close();
            synchronized (this) { authenticating.remove(peer); }
        }
    }
    private boolean idInUse(int id) { for (Participant p : participants.values()) if (p.id == id) return true; return false; }
    private static JsonObject roster(RoomSession room, String type) {
        JsonObject o = Protocol.message(type); o.addProperty("mode", room.gameMode.id());
        o.addProperty("inputDelayTicks", room.inputDelayTicks);
        o.addProperty("room",room.id);o.addProperty("revision",room.revision);o.addProperty("hostId",room.hostId);
        o.addProperty("phase",room.resultReady?"RESULT":room.started?"STARTED":room.prepared?"PREPARING":"EDITING");o.add("rules",room.rules.json());
        JsonArray players = new JsonArray();
        for (Participant p : room.participants.values()) {
            JsonObject item = new JsonObject(); item.addProperty("id", p.id); item.addProperty("name", p.displayName);
            item.addProperty("seat", p.seat.name); item.addProperty("team", p.seat.team);
            item.addProperty("lobbyReady",p.lobbyReady);item.addProperty("lineupName",p.lineupName);
            item.addProperty("castleHealthMultiplier",p.castleHealthMultiplier);players.add(item);
        }
        o.add("players", players); return o;
    }
    /** Editable lobby commands are nonfatal: a stale click must never kick another player. */
    private void editLobby(RoomSession room,Participant p,JsonObject o,String type) {
        if(room.prepared||room.started||room.resultReady){notice(p,"LOCKED","対戦処理中のため、編成と設定は固定されています");return;}
        try {
            if(type.equals("rules")) {
                if(p.id!=room.hostId){notice(p,"HOST_ONLY","ルールはホストだけが変更できます");return;}
                if(!currentRevision(room,p,o))return;
                RoomRules rules=RoomRules.read(o);
                if(!room.rules.equals(rules)){room.rules=rules;room.changed();}
            } else if(type.equals("lineup")) {
                p.lineupName=Protocol.string(o,"name",120);room.changed();
            } else if(type.equals("player_rules")) {
                if(!currentRevision(room,p,o))return;
                double multiplier=Protocol.real(o,"castleHealthMultiplier",0.1,1000.0);
                if(Double.compare(multiplier,p.castleHealthMultiplier)!=0){p.castleHealthMultiplier=multiplier;room.changed();}
            } else {
                if(!currentRevision(room,p,o))return;
                p.lobbyReady=Protocol.bool(o,"ready");room.progressed=System.nanoTime();
            }
            boolean all=room.participants.size()==room.gameMode.maxPlayers();
            for(Participant member:room.participants.values())all&=member.lobbyReady;
            if(all){room.prepare();room.progressed=System.nanoTime();broadcast(room,roster(room,"prepare"));}
            else broadcast(room,roster(room,"room_state"));
        } catch(IOException e){notice(p,"RULES",safe(e));}
    }
    private boolean currentRevision(RoomSession room,Participant p,JsonObject o)throws IOException {
        if(Protocol.number(o,"revision")==room.revision)return true;
        notice(p,"STALE","編成またはルールが更新されました。内容を確認してもう一度準備完了を押してください");
        p.control.send(roster(room,"room_state"));return false;
    }
    private static void notice(Participant p,String code,String text){JsonObject o=Protocol.message("notice");o.addProperty("code",code);o.addProperty("message",text);p.control.send(o);}
    private void selectTransport(RoomSession room, Participant p, String selected) throws IOException {
        if (room.started) throw new IOException("Cannot migrate transports during a match");
        Participant.TransportState next;
        if (selected.equals("UDP")) next = Participant.TransportState.UDP;
        else if (selected.equals("WS")) next = Participant.TransportState.WS;
        else throw new IOException("Unknown realtime transport");
        if (p.transportState != Participant.TransportState.PROBING && p.transportState != next)
            throw new IOException("Transport already selected");
        if (next == Participant.TransportState.UDP) {
            if (p.udp == null || !p.udp.isBound()) throw new IOException("UDP path is not authenticated");
            p.realtime = p.udp;
        } else {
            if (p.udp != null) { p.udp.close(); p.udp = null; }
            p.realtime = new WebSocketRealtimeTransport(p.control::send);
        }
        p.transportState = next;
        JsonObject o = Protocol.message("transport_selected"); o.addProperty("transport", selected); p.control.send(o);
        startIfReady(room);
    }
    private void offerBundle(RoomSession room, Participant p, JsonObject o) throws IOException {
        if (!room.prepared || room.started || p.bundleHash != null) throw new IOException("Unexpected bundle offer");
        String hash = Protocol.string(o, "hash", 64); long size = Protocol.number(o, "size");
        BundleStore.Offer response = room.bundles.offer(hash, size); p.bundleHash = hash;
        JsonObject answer;
        if (response == BundleStore.Offer.UPLOAD) { p.uploadingHash = hash; answer = Protocol.message("bundle_upload"); }
        else if (response == BundleStore.Offer.WAIT) answer = Protocol.message("bundle_wait");
        else { p.uploaded = true; answer = Protocol.message("bundle_ok"); }
        answer.addProperty("hash", hash); p.control.send(answer); room.progressed = System.nanoTime();
        publishManifest(room);
    }
    public synchronized void binary(ControlPeer peer, byte[] bytes) {
        try {
            RoomSession room = membership.get(peer); Participant p = participants.get(peer);
            if (room == null || room.closed || room.started || p == null || p.uploadingHash == null)
                throw new IOException("Unexpected asset upload");
            room.bundles.append(p.uploadingHash, bytes); room.progressed = System.nanoTime();
        } catch (Exception e) { fail(peer, e); }
    }
    private void finishBundle(RoomSession room, Participant p) throws IOException {
        if (room.started || p.uploadingHash == null) throw new IOException("Unexpected upload completion");
        String hash = p.uploadingHash; room.bundles.finish(hash); p.uploadingHash = null; completedUploads++;
        for (Participant member : room.participants.values()) {
            if (hash.equals(member.bundleHash)) {
                member.uploaded = true; JsonObject o = Protocol.message("bundle_ok"); o.addProperty("hash", hash); member.control.send(o);
            }
        }
        room.progressed = System.nanoTime(); publishManifest(room);
    }
    private void publishManifest(RoomSession room) throws IOException {
        if (!room.prepared || room.manifestSent) return;
        for (Participant p : room.participants.values()) if (!p.uploaded) return;
        JsonObject out = Protocol.message("bundle_manifest"); JsonArray bundles = new JsonArray();
        for (Participant p : room.participants.values()) {
            JsonObject b = new JsonObject(); b.addProperty("playerId", p.id); b.addProperty("hash", p.bundleHash);
            b.addProperty("size", room.bundles.size(p.bundleHash)); bundles.add(b);
        }
        room.manifestSent = true; out.add("bundles", bundles); broadcast(room, out);
    }
    private void download(RoomSession room, Participant p, String hash) throws IOException {
        if (room.started || !room.manifestSent || p.downloading || !room.bundles.contains(hash)) throw new IOException("Unexpected bundle download");
        p.downloading = true;
        try { downloads.execute(() -> streamBundle(room, p, hash)); }
        catch (RejectedExecutionException busy) { p.downloading = false; throw new IOException("Bundle download queue is full"); }
    }
    private void streamBundle(RoomSession room, Participant p, String hash) {
        Exception failure = null;
        try (InputStream in = room.bundles.open(hash)) {
            JsonObject start = Protocol.message("bundle_start"); start.addProperty("hash", hash); start.addProperty("size", room.bundles.size(hash));
            p.control.send(start); byte[] bytes = new byte[Protocol.CHUNK]; int n;
            while ((n = in.read(bytes)) != -1) {
                long deadline = System.nanoTime() + 30 * SECOND;
                while (p.control.hasBufferedData()) {
                    if (room.closed || !p.control.isOpen() || System.nanoTime() > deadline || Thread.currentThread().isInterrupted())
                        throw new IOException("Bundle receiver is too slow or disconnected");
                    Thread.sleep(5);
                }
                if (room.closed || !p.control.isOpen()) throw new IOException("Room closed during download");
                p.control.send(Arrays.copyOf(bytes, n));
                synchronized (this) { room.progressed = System.nanoTime(); }
            }
        } catch (Exception e) { failure = e; }
        synchronized (this) {
            p.downloading = false;
            if (!room.closed) {
                if (failure != null) closeRoom(room, "TRANSFER", safe(failure));
                else { JsonObject end = Protocol.message("bundle_end"); end.addProperty("hash", hash); p.control.send(end); }
            }
        }
    }
    private void ready(RoomSession room, Participant p, JsonObject o) throws IOException {
        if (Protocol.number(o,"revision")!=room.revision || room.started || room.resultReady || !room.manifestSent || !o.has("hashes") || !o.get("hashes").isJsonObject()) throw new IOException("Invalid readiness barrier");
        JsonObject hashes = o.getAsJsonObject("hashes");
        if (hashes.size() != room.participants.size()) throw new IOException("Incomplete bundle-hash roster");
        for (Participant member : room.participants.values())
            if (!member.bundleHash.equals(Protocol.string(hashes, Integer.toString(member.id), 64))) throw new IOException("Bundle roster mismatch");
        p.ready = true; JsonObject state = Protocol.message("ready_state"); state.addProperty("playerId", p.id); broadcast(room, state);
        startIfReady(room);
    }
    private void startIfReady(RoomSession room) {
        if (room.started || room.resultReady || !room.prepared || !room.manifestSent) return;
        for (Participant p : room.participants.values()) if (!p.ready || p.realtime == null) return;
        room.started = true; room.seed = random.nextLong(); room.progressed = System.nanoTime(); room.nextFrameAt = room.progressed;
        for (Participant p : room.participants.values()) p.lastFrameAckProgress = room.progressed;
        JsonObject o = roster(room, "start"); o.addProperty("seed", room.seed); broadcast(room, o);
    }
    private void accept(RoomSession room, Participant p, RealtimeData data) throws IOException {
        if (!room.started) return;
        if (!data.frames.isEmpty() || data.checkpointAck != 0 || data.nextExpected > room.lockstep.tick() || data.requestTick > room.lockstep.tick())
            throw new IOException("Invalid client realtime state");
        if (data.nextExpected > p.nextFrameExpected) { p.nextFrameExpected = data.nextExpected; p.lastFrameAckProgress = System.nanoTime(); }
        // A stale/reordered packet must not move an application acknowledgment backwards.
        p.requestedFrame = Math.max(p.nextFrameExpected, data.requestTick);
        for (Map.Entry<Long, Integer> input : data.inputs.entrySet()) room.lockstep.input(p.id, input.getKey(), input.getValue());
        for (Map.Entry<Long, String> checkpoint : data.checkpoints.entrySet()) room.lockstep.checkpoint(p.id, checkpoint.getKey(), checkpoint.getValue());
    }
    private RealtimeData outbound(RoomSession room, Participant p) throws IOException {
        RealtimeData out = new RealtimeData();
        if (!room.started) return out;
        out.nextExpected = room.lockstep.nextInput(p.id); out.checkpointAck = room.lockstep.hashAck(p.id);
        if (out.nextExpected <= room.lockstep.tick() + 1) out.requestTick = out.nextExpected;
        out.frames.addAll(room.lockstep.recent(p.requestedFrame)); return out;
    }
    private void result(RoomSession room, Participant p, JsonObject o) throws IOException {
        long tick = Protocol.number(o, "tick"); int winner = Protocol.integer(o, "winner"); String hash = Protocol.string(o, "hash", 64);
        if (!room.started || tick <= 0 || tick > room.lockstep.tick() || !room.gameMode.validWinner(winner) || !Hashes.valid(hash)) throw new IOException("Invalid battle result");
        String value = tick + ":" + winner + ":" + hash;
        if (p.result != null && !p.result.equals(value)) throw new IOException("Battle results differ"); p.result = value;
        for (Participant other : room.participants.values()) if (other.result == null) return;
        for (Participant other : room.participants.values()) if (!value.equals(other.result)) throw new IOException("Battle results differ");
        room.started=false;room.resultReady=true;room.progressed=System.nanoTime();
        JsonObject out = Protocol.message("result"); out.addProperty("winner", winner); out.addProperty("tick", tick); broadcast(room, out);
    }
    private void resultAck(RoomSession room,Participant p)throws IOException{
        if(!room.resultReady)throw new IOException("No battle result to acknowledge");
        p.resultAck=true;room.progressed=System.nanoTime();
        JsonObject state=Protocol.message("result_ack_state");state.addProperty("playerId",p.id);broadcast(room,state);
        for(Participant member:room.participants.values())if(!member.resultAck)return;
        room.resetMatch();broadcast(room,roster(room,"room_state"));
    }
    private void abortBattle(RoomSession room,Participant p)throws IOException{
        if(!room.prepared&&!room.started&&!room.resultReady)throw new IOException("No active battle to abort");
        JsonObject cancelled=Protocol.message("battle_cancelled");cancelled.addProperty("playerId",p.id);broadcast(room,cancelled);
        room.resetMatch();broadcast(room,roster(room,"room_state"));
    }
    public synchronized void pump(long now) {
        if (closed) return;
        for (RoomSession room : new ArrayList<>(rooms.values())) {
            try {
                if ((!room.prepared && now - room.progressed > config.roomIdleMinutes * 60L * SECOND) ||
                        (room.prepared && now - room.progressed > (room.started ? 30 : 120) * SECOND) ||
                        (room.resultReady && now - room.progressed > config.roomIdleMinutes * 60L * SECOND)) {
                    closeRoom(room, "TIMEOUT", "Room timed out"); continue;
                }
                if (room.started && now >= room.nextFrameAt) {
                    ResolvedFrame frame = room.lockstep.resolve();
                    if (frame != null) { room.progressed = now; room.nextFrameAt = Math.max(room.nextFrameAt + SECOND / Protocol.TPS, now - SECOND / Protocol.TPS); }
                }
                for (Participant p : room.participants.values()) {
                    if (p.transportState == Participant.TransportState.UDP && room.started && now - p.udp.lastReceived() >= 3 * SECOND) {
                        closeRoom(room, "UDP_TIMEOUT", "No authenticated UDP packets for 3 seconds; match aborted"); break;
                    }
                    RealtimeData data = outbound(room, p);
                    if (p.realtime != null) {
                        if (p.transportState == Participant.TransportState.UDP) p.realtime.send(data, now, room.started);
                        else if (room.started && now - p.lastSend >= SECOND / Protocol.TPS) { p.realtime.send(data, now, true); p.lastSend = now; }
                    } else if (p.udp != null && p.udp.isBound()) p.udp.send(new RealtimeData(), now, false);
                    if (room.started && p.transportState == Participant.TransportState.UDP && now - p.lastRescue >= RESCUE &&
                            (now - room.progressed >= RESCUE || (p.nextFrameExpected < room.lockstep.tick() && now - p.lastFrameAckProgress >= RESCUE))) {
                        p.control.send(data.control("rescue")); p.lastRescue = now; rescueCount++;
                    }
                }
            } catch (Exception e) { if (!room.closed) closeRoom(room, code(e), safe(e)); }
        }
        for (Map.Entry<ControlPeer, Long> item : new ArrayList<>(opened.entrySet())) {
            if (!membership.containsKey(item.getKey()) && !authenticating.contains(item.getKey()) && now - item.getValue() > 60 * SECOND) {
                item.getKey().closeLater(); opened.remove(item.getKey());
            }
        }
        attempts.entrySet().removeIf(e -> now - e.getValue()[0] > 60 * SECOND);
    }
    private static void broadcast(RoomSession room, JsonObject message) { for (Participant p : room.participants.values()) p.control.send(message); }
    private static void error(ControlPeer peer, String code, String message) {
        if (peer == null || !peer.isOpen()) return;
        JsonObject o = Protocol.message("error"); o.addProperty("code", code); o.addProperty("message", message); peer.send(o);
    }
    private static String safe(Exception e) { return e instanceof IOException ? e.getMessage() : "Invalid request"; }
    private static String code(Exception e) {
        String message = e.getMessage(); return message != null && (message.startsWith("Battle state mismatch") || message.startsWith("Battle results differ")) ? "DESYNC" : "PROTOCOL";
    }
    private synchronized void fail(ControlPeer peer, Exception failure) {
        RoomSession room = membership.get(peer);
        if (room != null) closeRoom(room, code(failure), safe(failure));
        else { error(peer, code(failure), safe(failure)); if (peer != null) peer.closeLater(); }
    }
    private void closeRoom(RoomSession room, String code, String message) {
        if (room.closed) return; for (Participant p : room.participants.values()) error(p.control, code, message); removeRoom(room);
    }
    private void removeRoom(RoomSession room) {
        room.closed = true; rooms.remove(room.id);
        for (Participant p : room.participants.values()) {
            membership.remove(p.control); participants.remove(p.control);
            if (p.realtime != null) p.realtime.close(); if (p.udp != null) p.udp.close();
        }
        try { room.close(); } catch (IOException e) { System.err.println("Room cache cleanup failed: " + e.getClass().getSimpleName()); }
    }
    public synchronized void disconnected(ControlPeer peer) {
        opened.remove(peer); RoomSession room = membership.get(peer); if (room != null) closeRoom(room, "DISCONNECTED", "A participant disconnected");
    }
    @Override public synchronized void close() {
        if (closed) return; closed = true;
        for (RoomSession room : new ArrayList<>(rooms.values())) closeRoom(room, "CLOSED", "Server stopped");
        for (ControlPeer peer : opened.keySet()) peer.closeLater(); opened.clear(); attempts.clear();
        authentication.shutdownNow(); downloads.shutdownNow();
    }
}
