package online.net;

import com.google.gson.JsonObject;
import online.net.config.ServerConfig;
import online.net.core.*;
import online.net.realtime.UdpRealtimeServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;

/** WebSocket adapter and lifecycle owner. Room rules live in RoomServerCore, not socket callbacks. */
public final class RoomServer extends WebSocketServer {
    private final RoomServerCore core;
    private final UdpRealtimeServer udp;
    private final Map<WebSocket, Bridge> peers = new ConcurrentHashMap<>();
    private final Queue<WebSocket> closures = new ConcurrentLinkedQueue<>();
    private final CountDownLatch started = new CountDownLatch(1);
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "pvp-room-clock"); t.setDaemon(true); return t;
    });
    private volatile boolean stopped;
    private volatile Exception startupError;

    public RoomServer(InetSocketAddress address) throws IOException { this(ServerConfig.local(address)); }
    public RoomServer(ServerConfig config) throws IOException {
        this(config, Collections.singletonMap("DUEL_1V1", new Duel1v1Mode()));
    }
    public RoomServer(ServerConfig config, Map<String, GameMode> modes) throws IOException {
        super(config.controlAddress(), 2, Collections.singletonList(Protocol.draft()));
        udp = config.udpEnabled ? new UdpRealtimeServer(config.udpAddress()) : null;
        core = new RoomServerCore(config, udp, modes);
        setConnectionLostTimeout(20); setReuseAddr(true); setTcpNoDelay(true);
    }
    public boolean awaitStarted(long time, TimeUnit unit) throws InterruptedException { return started.await(time, unit) && startupError == null; }
    public int roomCount() { return core.roomCount(); }
    public int udpPort() { return udp == null ? -1 : udp.port(); }
    public RoomServerCore core() { return core; }
    @Override public void onStart() {
        timer.scheduleWithFixedDelay(() -> {
            try { core.pump(System.nanoTime()); }
            catch (RuntimeException e) { System.err.println("PvP room clock failed: " + e.getClass().getSimpleName()); }
            finally { drainClosures(); }
        }, 0, 5, TimeUnit.MILLISECONDS);
        started.countDown();
    }
    @Override public void onOpen(WebSocket socket, ClientHandshake handshake) {
        Bridge peer = new Bridge(socket); peers.put(socket, peer); core.opened(peer);
    }
    @Override public void onMessage(WebSocket socket, String text) { core.text(peers.computeIfAbsent(socket, Bridge::new), text); }
    @Override public void onMessage(WebSocket socket, ByteBuffer bytes) {
        Bridge peer = peers.get(socket);
        if (peer == null || bytes.remaining() <= 0 || bytes.remaining() > Protocol.CHUNK) { closures.add(socket); return; }
        byte[] copy = new byte[bytes.remaining()]; bytes.get(copy); core.binary(peer, copy);
    }
    @Override public void onClose(WebSocket socket, int code, String reason, boolean remote) {
        Bridge peer = peers.remove(socket); if (peer != null) core.disconnected(peer);
    }
    @Override public void onError(WebSocket socket, Exception error) {
        if (socket != null) closures.add(socket);
        else { startupError = error; started.countDown(); System.err.println("PvP server: " + error.getClass().getSimpleName()); }
    }
    private void drainClosures() {
        WebSocket socket;
        while ((socket = closures.poll()) != null) try { socket.close(); } catch (RuntimeException ignored) { }
    }
    @Override public void stop(int timeout) throws InterruptedException {
        if (stopped) return; stopped = true; timer.shutdownNow(); core.close();
        if (udp != null) udp.close(); drainClosures(); super.stop(timeout);
    }
    private final class Bridge implements ControlPeer {
        final WebSocket socket;
        Bridge(WebSocket socket) { this.socket = socket; }
        public void send(JsonObject message) {
            if (isOpen()) try { socket.send(message.toString()); } catch (RuntimeException e) { closeLater(); }
        }
        public void send(byte[] bytes) {
            if (isOpen()) try { socket.send(bytes); } catch (RuntimeException e) { closeLater(); }
        }
        public boolean isOpen() { return socket.isOpen(); }
        public boolean hasBufferedData() { return socket.hasBufferedData(); }
        public String address() {
            InetSocketAddress address = socket.getRemoteSocketAddress();
            return address == null || address.getAddress() == null ? "unknown" : address.getAddress().getHostAddress();
        }
        public void closeLater() { closures.add(socket); }
    }
    /** Compatibility launcher; the properties-based launcher is PvpServerMain. */
    public static void main(String[] args) throws Exception {
        Properties p = new Properties(); p.setProperty("bind", args.length > 0 ? args[0] : "127.0.0.1");
        p.setProperty("controlPort", args.length > 1 ? args[1] : "8766"); p.setProperty("udpPort", args.length > 2 ? args[2] : "8767");
        RoomServer server = new RoomServer(ServerConfig.from(p)); server.start();
        if (!server.awaitStarted(10, TimeUnit.SECONDS)) { server.stop(1000); throw new IOException("Server could not bind/start"); }
        System.out.println("BCU PvP: control " + server.getAddress() + " / UDP " + server.udpPort());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { try { server.stop(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }));
    }
}
