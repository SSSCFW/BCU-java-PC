package online.net;

import com.google.gson.*;
import online.bundle.Hashes;
import online.net.lobby.RoomRules;
import online.net.realtime.*;
import online.sync.*;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Participant-keyed client. Control, transport timers, and display/simulation are independent. */
public class RoomClient extends WebSocketClient implements AutoCloseable {
    public interface Listener {
        void event(JsonObject event);
        /** The listener owns this verified temporary copy and must delete it when finished. */
        void bundle(int playerId, Path verifiedArchive, String hash);
        void failed(String reason);
    }
    private final Object state = new Object();
    private final Listener listener;
    private final boolean preferUdp;
    private final BlockingQueue<ResolvedFrame> frames = new ArrayBlockingQueue<>(128);
    private final AtomicInteger commands = new AtomicInteger();
    private final ExecutorService transfer = Executors.newSingleThreadExecutor(r -> daemon(r, "pvp-assets"));
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "pvp-client-network"));
    private final LinkedHashSet<Integer> roster = new LinkedHashSet<>();
    private final Map<Integer, String> hashes = new LinkedHashMap<>();
    private final Map<String, Path> cached = new HashMap<>();
    private final Map<String, List<Integer>> recipients = new LinkedHashMap<>();
    private final Map<String, Long> manifestSizes = new HashMap<>();
    private final Set<Integer> delivered = new HashSet<>();
    private final List<Path> cacheTemps = new ArrayList<>();
    private final ArrayDeque<String> downloadQueue = new ArrayDeque<>();
    private final TreeMap<Long, String> checkpoints = new TreeMap<>();
    private final InputHistory inputHistory = new InputHistory();
    private FrameBuffer frameBuffer;
    private UdpRealtimeClient udp;
    private WebSocketRealtimeTransport wsRealtime;
    private String selected, requestedTransport, ownHash, downloadingHash;
    private Path ownArchive, receiving;
    private OutputStream output;
    private MessageDigest digest;
    private long expectedBytes, received, lastFrameProgress, lastRescue, serverExpected, serverRequest = -1, maxCheckpoint, lastWsSend;
    private long rescueCount, retransmitRequests;
    private int playerId, delay = Protocol.INPUT_DELAY;
    private long roomRevision=-1;
    private RoomRules rules=RoomRules.DEFAULT;
    private boolean started, completed, ended, reported, manifestSeen, readyRequested, readySent;

    public RoomClient(URI uri, boolean allowPrivateWs, Listener listener) throws IOException { this(uri, allowPrivateWs, true, listener); }
    public RoomClient(URI uri, boolean allowPrivateWs, boolean preferUdp, Listener listener) throws IOException {
        super(validateUri(uri, allowPrivateWs), Protocol.draft(), null, 10000);
        this.listener = listener; this.preferUdp = preferUdp; setConnectionLostTimeout(20); setTcpNoDelay(true);
    }
    private static Thread daemon(Runnable task, String name) { Thread t = new Thread(task, name); t.setDaemon(true); return t; }
    public static URI validateUri(URI uri, boolean privateWs) throws IOException {
        if (uri == null || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null)
            throw new IOException("Enter a ws(s) server URL without credentials/fragments");
        String host = uri.getHost();
        boolean local = host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("[::1]") || host.equals("::1");
        if ("wss".equalsIgnoreCase(uri.getScheme())) return uri;
        if ("ws".equalsIgnoreCase(uri.getScheme()) && (local || (privateWs && privateLiteral(host)))) return uri;
        throw new IOException("公開回線はWSSを使用してください。WSはlocalhost、または明示許可したLAN/VPNのIPアドレスのみです。");
    }
    private static boolean privateLiteral(String host) {
        try {
            if (host.matches("[0-9.]+")) {
                String[] parts = host.split("\\."); if (parts.length != 4) return false;
                int[] b = new int[4]; for (int i = 0; i < 4; i++) { b[i] = Integer.parseInt(parts[i]); if (b[i] < 0 || b[i] > 255) return false; }
                return b[0] == 10 || b[0] == 127 || (b[0] == 172 && b[1] >= 16 && b[1] <= 31) ||
                        (b[0] == 192 && b[1] == 168) || (b[0] == 100 && b[1] >= 64 && b[1] <= 127);
            }
            if (host.contains(":")) {
                byte[] a = InetAddress.getByName(host).getAddress();
                return a.length == 16 && ((a[0] & 0xfe) == 0xfc || InetAddress.getByAddress(a).isLoopbackAddress());
            }
        } catch (Exception ignored) { }
        return false;
    }
    @Override public void onOpen(ServerHandshake handshake) {
        wsRealtime = new WebSocketRealtimeTransport(o -> send(o.toString()));
        timer.scheduleWithFixedDelay(this::pump, 0, 5, TimeUnit.MILLISECONDS);
        listener.event(Protocol.message("connected"));
    }
    public void enter(boolean create, String room, String password, String name, String side, String gameHash) {
        enter(create, room, password, name, side, gameHash, "DUEL_1V1");
    }
    public void enter(boolean create, String room, String password, String name, String side, String gameHash, String mode) {
        JsonObject o = Protocol.message(create ? "create" : "join"); o.addProperty("version", Protocol.VERSION); o.addProperty("engine", Protocol.ENGINE);
        o.addProperty("game", gameHash); o.addProperty("name", name); o.addProperty("password", password); o.addProperty("room", room);
        o.addProperty("side", side); o.addProperty("mode", mode); o.addProperty("udp", preferUdp); send(o.toString());
    }
    @Override public void onMessage(String text) {
        try {
            synchronized (state) {
                if (ended) return;
                JsonObject o = Protocol.parse(text); String type = Protocol.string(o, "type", 32);
                switch (type) {
                    case "joined":
                        if (playerId != 0) throw new IOException("Duplicate identity assignment");
                        playerId = Protocol.integer(o, "playerId"); if (playerId <= 0) throw new IOException("Invalid player identity");
                        delay = readDelay(o); listener.event(o); break;
                    case "room_state":
                        roomRevision=Protocol.number(o,"revision");rules=RoomRules.read(o);listener.event(o);break;
                    case "prepare":
                        long revision=Protocol.number(o,"revision");
                        RoomRules frozen=RoomRules.read(o);
                        if(revision!=roomRevision||!frozen.equals(rules))throw new IOException("Room rules changed without confirmation");
                        if (!roster.isEmpty()) throw new IOException("Duplicate roster");
                        readRoster(o); listener.event(o); break;
                    case "udp_offer": offerUdp(o); break;
                    case "transport_selected":
                        String transport = Protocol.string(o, "transport", 8);
                        if (!transport.equals("UDP") && !transport.equals("WS")) throw new IOException("Unknown transport");
                        if (started || (selected != null && !selected.equals(transport))) throw new IOException("Unexpected transport change");
                        selected = transport;
                        if (selected.equals("WS") && udp != null) { udp.close(); udp = null; }
                        listener.event(o); tryReady(); break;
                    case "bundle_upload": upload(Protocol.string(o, "hash", 64)); break;
                    case "bundle_ok": listener.event(o); tryReady(); break;
                    case "bundle_wait": break;
                    case "bundle_manifest": manifest(o); break;
                    case "bundle_start": beginDownload(o); break;
                    case "bundle_end": endDownload(o); break;
                    case "start": start(o); break;
                    case "realtime":
                        if (!"WS".equals(selected)) throw new IOException("Unexpected WS realtime packet");
                        accept(RealtimeData.fromControl(o)); break;
                    case "rescue":
                        if ("UDP".equals(selected) && started) { accept(RealtimeData.fromControl(o)); rescueCount++; send(outbound().control("rescue").toString()); }
                        break;
                    case "result":
                        completed = true; if (udp != null) udp.close(); timer.shutdownNow(); listener.event(o); break;
                    case "error": throw new IOException(Protocol.string(o, "code", 32) + ": " + Protocol.string(o, "message", 1024));
                    default: listener.event(o); break;
                }
            }
        } catch (Exception e) { fail(e.getMessage() == null ? "Invalid server response" : e.getMessage()); }
    }
    private int readDelay(JsonObject o) throws IOException {
        int value = Protocol.integer(o, "inputDelayTicks"); if (value < 3 || value > 8) throw new IOException("Invalid input delay"); return value;
    }
    private void readRoster(JsonObject o) throws IOException {
        if (!o.has("players") || !o.get("players").isJsonArray()) throw new IOException("Missing participant roster");
        JsonArray players = o.getAsJsonArray("players");
        if (players.size() < 2 || players.size() > 8 || readDelay(o) != delay) throw new IOException("Invalid participant count/configuration");
        for (JsonElement value : players) {
            int id = Protocol.integer(value.getAsJsonObject(), "id");
            if (id <= 0 || !roster.add(id)) throw new IOException("Invalid or duplicate participant");
        }
        if (!roster.contains(playerId)) throw new IOException("Local identity not in roster");
    }
    private void offerUdp(JsonObject o) throws IOException {
        if (udp != null || selected != null || playerId == 0) throw new IOException("Unexpected UDP bootstrap");
        if (!preferUdp) { select("WS"); return; }
        byte[] master;
        try { master = Base64.getDecoder().decode(Protocol.string(o, "master", 64)); }
        catch (IllegalArgumentException e) { throw new IOException("Invalid UDP bootstrap key"); }
        try {
            long session = Long.parseUnsignedLong(Protocol.string(o, "session", 20));
            int port = Protocol.integer(o, "port"); if (port < 1 || port > 65535 || master.length != 32 || session == 0) throw new IOException("Invalid UDP bootstrap");
            String host = Protocol.string(o, "host", 253);
            try {
                udp = new UdpRealtimeClient(udpEndpoint(host, port), session, master, new UdpRealtimeClient.Listener() {
                    public void available() { synchronized (state) { if (!ended) select("UDP"); } }
                    public void fallback() { synchronized (state) { if (!ended) select("WS"); } }
                    public void data(RealtimeData data) { try { synchronized (state) { if (!ended && !completed && started) accept(data); } } catch (Exception e) { fail(e.getMessage()); } }
                    public void failed(String reason) { fail(reason); }
                });
            } catch (IOException unavailable) { select("WS"); }
        } catch (NumberFormatException invalid) { throw new IOException("Invalid UDP session number"); }
        finally { Arrays.fill(master, (byte) 0); }
    }
    protected InetSocketAddress udpEndpoint(String host, int offeredPort) throws IOException {
        InetSocketAddress address = new InetSocketAddress(host.isEmpty() ? getURI().getHost() : host, offeredPort);
        if (address.isUnresolved()) throw new IOException("UDP endpoint not resolved"); return address;
    }
    private void select(String transport) {
        if (requestedTransport != null || ended) return;
        requestedTransport = transport;
        JsonObject out = Protocol.message("transport_select"); out.addProperty("transport", transport); send(out.toString());
    }
    private void start(JsonObject o) throws IOException {
        if (started || selected == null || readDelay(o) != delay || !manifestSeen || !hashes.keySet().equals(roster)) throw new IOException("Start barrier not complete");
        LinkedHashSet<Integer> startIds = new LinkedHashSet<>();
        for (JsonElement p : o.getAsJsonArray("players")) startIds.add(Protocol.integer(p.getAsJsonObject(), "id"));
        if (!startIds.equals(roster)||Protocol.number(o,"revision")!=roomRevision||!RoomRules.read(o).equals(rules)) throw new IOException("Roster/rules changed at start");
        frameBuffer = new FrameBuffer(roster); started = true; lastFrameProgress = System.nanoTime();
        for (int tick = 0; tick < delay; tick++) inputHistory.add(tick, 0);
        listener.event(o);
    }
    private void accept(RealtimeData data) throws IOException {
        if (!started || completed) return;
        if (!data.inputs.isEmpty() || !data.checkpoints.isEmpty() || data.nextExpected > inputHistory.nextTick() || data.checkpointAck > maxCheckpoint)
            throw new IOException("Invalid server realtime state");
        serverExpected = Math.max(serverExpected, data.nextExpected);
        if (data.requestTick >= 0 && data.requestTick < inputHistory.nextTick() - 3) retransmitRequests++;
        serverRequest = Math.max(serverExpected, data.requestTick);
        checkpoints.headMap(data.checkpointAck, true).clear();
        for (ResolvedFrame received : data.frames) for (ResolvedFrame frame : frameBuffer.accept(received)) {
            if (!frames.offer(frame)) throw new IOException("Simulation cannot keep up with the room");
            inputHistory.add(frame.tick + delay, commands.getAndSet(0));
            lastFrameProgress = System.nanoTime();
        }
    }
    private RealtimeData outbound() throws IOException {
        RealtimeData data = new RealtimeData();
        if (!started) return data;
        data.nextExpected = frameBuffer.nextTick(); data.requestTick = frameBuffer.nextTick();
        data.inputs.putAll(inputHistory.batch(serverRequest));
        for (Map.Entry<Long, String> e : checkpoints.entrySet()) { data.checkpoints.put(e.getKey(), e.getValue()); if (data.checkpoints.size() == 2) break; }
        return data;
    }
    private void pump() {
        try {
            synchronized (state) {
                if (ended || completed || !isOpen()) return;
                long now = System.nanoTime(); RealtimeData data = outbound();
                if (udp != null) udp.pump(data, now, started && "UDP".equals(selected));
                if (!started) return;
                if ("UDP".equals(selected)) {
                    if (udp == null || now - udp.lastReceived() > 3_000_000_000L) throw new IOException("UDP_TIMEOUT: 3秒間UDP通信が届かないため試合を停止しました");
                    if (now - lastFrameProgress >= 250_000_000L && now - lastRescue >= 250_000_000L) {
                        send(data.control("rescue").toString()); lastRescue = now; rescueCount++;
                    }
                } else if ("WS".equals(selected) && now - lastWsSend >= 33_333_333L) {
                    wsRealtime.send(data, now, true); lastWsSend = now;
                }
            }
        } catch (Exception e) { fail(e.getMessage()); }
    }
    public void sendBundle(Path archive) {
        transfer.execute(() -> {
            try {
                long size = Files.size(archive); String hash = Hashes.sha256(archive);
                if (size <= 0 || size > Protocol.MAX_BUNDLE) throw new IOException("Bundle size limit");
                synchronized (state) {
                    if (ended || ownArchive != null) return;
                    ownArchive = archive; ownHash = hash; cached.put(hash, archive); hashes.put(playerId, hash);
                    JsonObject offer = Protocol.message("bundle"); offer.addProperty("size", size); offer.addProperty("hash", hash); send(offer.toString());
                }
            } catch (Exception e) { fail("Asset offer failed: " + e.getMessage()); }
        });
    }
    private void upload(String hash) throws IOException {
        if (ownArchive == null || !hash.equals(ownHash)) throw new IOException("Unexpected upload request");
        final Path file = ownArchive;
        transfer.execute(() -> {
            try (InputStream in = Files.newInputStream(file)) {
                byte[] bytes = new byte[Protocol.CHUNK]; int n;
                while ((n = in.read(bytes)) != -1) {
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
                    while (hasBufferedData()) { if (!isOpen() || System.nanoTime() > deadline || Thread.currentThread().isInterrupted()) throw new IOException("Asset upload timeout"); Thread.sleep(5); }
                    synchronized (state) { if (ended) return; }
                    send(Arrays.copyOf(bytes, n));
                }
                if (isOpen()) send(Protocol.message("bundle_end").toString());
            } catch (Exception e) { fail("Asset upload failed: " + e.getMessage()); }
        });
    }
    private void manifest(JsonObject o) throws IOException {
        if (manifestSeen || started || ownArchive == null || !o.has("bundles") || !o.get("bundles").isJsonArray()) throw new IOException("Unexpected bundle manifest");
        Set<Integer> seen = new HashSet<>();
        for (JsonElement value : o.getAsJsonArray("bundles")) {
            JsonObject item = value.getAsJsonObject(); int id = Protocol.integer(item, "playerId");
            String hash = Protocol.string(item, "hash", 64); long size = Protocol.number(item, "size");
            if (!roster.contains(id) || !seen.add(id) || !Hashes.valid(hash) || size <= 0 || size > Protocol.MAX_BUNDLE)
                throw new IOException("Invalid bundle manifest entry");
            Long previous = manifestSizes.put(hash, size); if (previous != null && previous != size) throw new IOException("Contradictory bundle size");
            if (id == playerId && !hash.equals(ownHash)) throw new IOException("Local bundle hash changed");
            recipients.computeIfAbsent(hash, unused -> new ArrayList<>()).add(id);
        }
        if (!seen.equals(roster)) throw new IOException("Incomplete bundle roster");
        manifestSeen = true;
        for (String hash : recipients.keySet()) {
            if (cached.containsKey(hash)) deliver(hash); else downloadQueue.add(hash);
        }
        nextDownload(); tryReady();
    }
    private void nextDownload() {
        if (downloadingHash != null || downloadQueue.isEmpty() || ended) return;
        downloadingHash = downloadQueue.remove(); JsonObject get = Protocol.message("bundle_get"); get.addProperty("hash", downloadingHash); send(get.toString());
    }
    private void beginDownload(JsonObject o) throws IOException {
        String hash = Protocol.string(o, "hash", 64); long size = Protocol.number(o, "size");
        if (output != null || started || !hash.equals(downloadingHash) || !Objects.equals(manifestSizes.get(hash), size)) throw new IOException("Unexpected download stream");
        expectedBytes = size; received = 0; digest = Hashes.digest(); receiving = Files.createTempFile("bcu-pvp-cache-", ".zip"); output = Files.newOutputStream(receiving);
    }
    @Override public void onMessage(ByteBuffer bytes) {
        try {
            synchronized (state) {
                if (ended) return;
                int n = bytes.remaining();
                if (output == null || n <= 0 || n > Protocol.CHUNK || received + n > expectedBytes) throw new IOException("Unexpected asset chunk");
                byte[] b = new byte[n]; bytes.get(b); output.write(b); digest.update(b); received += n;
            }
        } catch (IOException e) { fail(e.getMessage()); }
    }
    private void endDownload(JsonObject o) throws IOException {
        String hash = Protocol.string(o, "hash", 64);
        if (output == null || !hash.equals(downloadingHash) || received != expectedBytes) throw new IOException("Incomplete download");
        output.close(); output = null;
        if (!Hashes.hex(digest.digest()).equals(hash)) throw new IOException("Remote bundle checksum mismatch");
        cached.put(hash, receiving); cacheTemps.add(receiving); receiving = null; downloadingHash = null;
        deliver(hash); nextDownload();
    }
    private void deliver(String hash) {
        Path source = cached.get(hash);
        for (int id : recipients.get(hash)) {
            if (id == playerId || !delivered.add(id)) continue;
            transfer.execute(() -> {
                Path copy = null;
                try {
                    copy = Files.createTempFile("bcu-pvp-peer-", ".zip"); Files.copy(source, copy, StandardCopyOption.REPLACE_EXISTING);
                    synchronized (state) { if (ended) { Files.deleteIfExists(copy); return; } hashes.put(id, hash); }
                    listener.bundle(id, copy, hash); copy = null;
                    synchronized (state) { tryReady(); }
                } catch (Exception e) { if (copy != null) try { Files.deleteIfExists(copy); } catch (IOException ignored) { } fail("Bundle delivery failed: " + e.getMessage()); }
            });
        }
    }
    public void ready() { synchronized (state) { readyRequested = true; tryReady(); } }
    private void tryReady() {
        if (ended || readySent || !readyRequested || selected == null || !manifestSeen || !hashes.keySet().equals(roster)) return;
        JsonObject o = Protocol.message("ready"), all = new JsonObject();
        for (int id : roster) all.addProperty(Integer.toString(id), hashes.get(id));
        o.add("hashes", all); o.addProperty("revision",roomRevision); readySent = true; send(o.toString());
    }
    public RoomRules roomRules(){synchronized(state){return rules;}}
    public long roomRevision(){synchronized(state){return roomRevision;}}
    public void lobbyReady(boolean ready){synchronized(state){lobbyReady(ready,roomRevision);}}
    /** Confirm the revision actually displayed on the EDT, not a newer network-thread revision. */
    public void lobbyReady(boolean ready,long displayedRevision){synchronized(state){JsonObject o=Protocol.message("lobby_ready");o.addProperty("revision",displayedRevision);o.addProperty("ready",ready);send(o.toString());}}
    public void setRoomRules(RoomRules value){synchronized(state){setRoomRules(value,roomRevision);}}
    public void setRoomRules(RoomRules value,long displayedRevision){synchronized(state){JsonObject o=Protocol.message("rules");o.addProperty("revision",displayedRevision);o.add("rules",value.json());send(o.toString());}}
    public void setLineupName(String value){JsonObject o=Protocol.message("lineup");o.addProperty("name",value);send(o.toString());}
    public void queueCommand(int bit) {
        synchronized (state) { if (!started || ended || completed || !InputFrame.valid(bit)) return; }
        commands.getAndUpdate(old -> (old | (bit & 4095)) ^ (bit & ~4095));
    }
    public ResolvedFrame pollResolvedFrame() { return frames.poll(); }
    public int playerId() { synchronized (state) { return playerId; } }
    public String realtimeTransport() { synchronized (state) { return selected == null ? "PROBING" : selected; } }
    public ReliabilityWindow.Metrics udpMetrics() { synchronized (state) { return udp == null ? null : udp.metrics(); } }
    public long rescueCount() { synchronized (state) { return rescueCount; } }
    public long retransmitRequests() { synchronized (state) { return retransmitRequests; } }
    public void checkpoint(long tick, String hash) {
        synchronized (state) {
            if (!started || ended || completed) return;
            if (tick <= 0 || tick % Protocol.HASH_INTERVAL != 0 || !Hashes.valid(hash) || tick > frameBuffer.nextTick()) throw new IllegalArgumentException("Invalid checkpoint");
            String old = checkpoints.putIfAbsent(tick, hash); if (old != null && !old.equals(hash)) throw new IllegalArgumentException("Conflicting checkpoint");
            maxCheckpoint = Math.max(maxCheckpoint, tick);
        }
    }
    public void result(long tick, int winner, String hash) {
        JsonObject o = Protocol.message("result"); o.addProperty("tick", tick); o.addProperty("winner", winner); o.addProperty("hash", hash); send(o.toString());
    }
    private void fail(String reason) {
        synchronized (state) { if (reported || ended || completed) return; reported = true; }
        try { listener.failed(reason == null ? "Network error" : reason); } finally { close(); }
    }
    @Override public void onClose(int code, String reason, boolean remote) { fail("Connection closed (" + code + ")"); }
    @Override public void onError(Exception e) { fail("Connection error: " + e.getClass().getSimpleName()); }
    @Override public void close() {
        synchronized (state) {
            if (ended) return; ended = true; timer.shutdownNow(); transfer.shutdownNow(); frames.clear();
            if (udp != null) udp.close(); if (wsRealtime != null) wsRealtime.close();
            try { if (output != null) output.close(); if (receiving != null) Files.deleteIfExists(receiving); } catch (IOException ignored) { }
            for (Path path : cacheTemps) try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            cacheTemps.clear(); cached.clear(); checkpoints.clear(); output = null; receiving = null;
        }
        if (isOpen()) try { send(Protocol.message("leave").toString()); } catch (RuntimeException ignored) { }
        super.close();
    }
}
