package online.net;

import online.net.config.ServerConfig;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared embedded/dedicated lifecycle. Startup failure always closes both bound transports. */
public final class ServerHost implements AutoCloseable {
    private final RoomServer server;
    private final ServerConfig config;
    private final AtomicBoolean closed = new AtomicBoolean();
    private ServerHost(RoomServer server, ServerConfig config) { this.server = server; this.config = config; }
    public static ServerHost start(ServerConfig config) throws IOException {
        RoomServer server = new RoomServer(config);
        ServerHost host = new ServerHost(server, config);
        try {
            server.start();
            if (!server.awaitStarted(10, TimeUnit.SECONDS)) throw new IOException("Server could not start: check TCP/UDP ports and bind address");
            return host;
        } catch (InterruptedException e) {
            host.close(); Thread.currentThread().interrupt(); throw new IOException("Server startup interrupted", e);
        } catch (IOException | RuntimeException e) {
            host.close();
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Server could not start", e);
        }
    }
    public RoomServer server() { return server; }
    public String localControlUrl() {
        InetAddress bind = server.getAddress().getAddress();
        String host = bind == null || bind.isAnyLocalAddress() ? "127.0.0.1" : bind.getHostAddress();
        return wsUrl(host, server.getPort());
    }
    /** These are candidates, not a claim that NAT/firewall/VPN connectivity has been configured. */
    public List<String> candidateUrls() {
        LinkedHashSet<String> urls = new LinkedHashSet<>(); urls.add(localControlUrl());
        InetAddress bind = server.getAddress().getAddress();
        if (bind != null && !bind.isAnyLocalAddress()) return new ArrayList<>(urls);
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) for (NetworkInterface nic : Collections.list(interfaces)) {
                if (!nic.isUp() || nic.isLoopback()) continue;
                for (InetAddress address : Collections.list(nic.getInetAddresses())) {
                    if (address instanceof Inet4Address && privateAddress(address)) urls.add(wsUrl(address.getHostAddress(), server.getPort()));
                }
            }
        } catch (SocketException ignored) { }
        return new ArrayList<>(urls);
    }
    private static boolean privateAddress(InetAddress address) {
        byte[] b = address.getAddress();
        return address.isSiteLocalAddress() || (b.length == 4 && (b[0] & 255) == 100 && (b[1] & 255) >= 64 && (b[1] & 255) <= 127);
    }
    private static String wsUrl(String host, int port) {
        if (host.indexOf(':') >= 0 && !host.startsWith("[")) host = "[" + host.replace("%", "%25") + "]";
        return "ws://" + host + ":" + port;
    }
    public String description() {
        StringBuilder text = new StringBuilder("BCU PvP server / TCP ").append(server.getPort());
        text.append(server.udpPort() >= 0 ? " / UDP " + server.udpPort() : " / UDP disabled (WS fallback)");
        text.append("\nMode: DUEL_1V1 (2 players); technical capacity: ").append(config.maxParticipantsPerRoom);
        text.append("; input delay: ").append(config.inputDelayTicks).append(" ticks\n");
        for (String url : candidateUrls()) text.append(url).append('\n');
        text.append(config.friendsMode ? "Friends mode: WS only on loopback, trusted LAN or an encrypted private VPN.\n" : "Public mode: configure a TLS/WSS reverse proxy before accepting Internet clients.\n");
        text.append("Private IP alone does not encrypt WS. Public Internet requires WSS; forward UDP separately.\n");
        text.append("No router, firewall, TLS or VPN settings were changed.");
        return text.toString();
    }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { server.stop(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
