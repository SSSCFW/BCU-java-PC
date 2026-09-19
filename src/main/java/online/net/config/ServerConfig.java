package online.net.config;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Validated, immutable configuration shared by the embedded and dedicated servers. */
public final class ServerConfig {
    public final String bind, udpHost;
    public final int controlPort, udpPort, maxRooms, maxConnections, maxParticipantsPerRoom;
    public final int roomIdleMinutes, bundleMaxMiB, inputDelayTicks;
    public final boolean friendsMode, udpEnabled;
    private static final Set<String> KEYS = new HashSet<>(Arrays.asList(
            "bind", "controlPort", "udpPort", "udpHost", "maxRooms", "maxConnections",
            "maxParticipantsPerRoom", "roomIdleMinutes", "bundleMaxMiB", "friendsMode",
            "inputDelayTicks", "udpEnabled"));

    private ServerConfig(Properties p) throws IOException {
        for (String key : p.stringPropertyNames())
            if (!KEYS.contains(key)) throw new IOException("Unknown server setting: " + key);
        bind = host(p.getProperty("bind", "0.0.0.0"), false);
        udpHost = host(p.getProperty("udpHost", ""), true);
        controlPort = number(p, "controlPort", 8766, 0, 65535);
        udpPort = number(p, "udpPort", 8767, 0, 65535);
        maxRooms = number(p, "maxRooms", 32, 1, 128);
        maxConnections = number(p, "maxConnections", 128, 2, 1024);
        maxParticipantsPerRoom = number(p, "maxParticipantsPerRoom", 8, 2, 8);
        roomIdleMinutes = number(p, "roomIdleMinutes", 10, 1, 60);
        bundleMaxMiB = number(p, "bundleMaxMiB", 32, 1, 32);
        inputDelayTicks = number(p, "inputDelayTicks", 3, 3, 8);
        friendsMode = bool(p, "friendsMode", true);
        udpEnabled = bool(p, "udpEnabled", true);
    }
    public static ServerConfig from(Properties p) throws IOException { return new ServerConfig(p); }
    public static ServerConfig load(Path path) throws IOException {
        Properties p = new Properties();
        try (Reader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { p.load(in); }
        return from(p);
    }
    /** Ephemeral UDP port keeps independent in-process test servers isolated. */
    public static ServerConfig local(InetSocketAddress address) throws IOException {
        Properties p = new Properties();
        p.setProperty("bind", address.getHostString());
        p.setProperty("controlPort", Integer.toString(address.getPort()));
        p.setProperty("udpPort", "0");
        return from(p);
    }
    public InetSocketAddress controlAddress() { return new InetSocketAddress(bind, controlPort); }
    public InetSocketAddress udpAddress() { return new InetSocketAddress(bind, udpPort); }
    private static String host(String s, boolean empty) throws IOException {
        s = s.trim();
        if (empty && s.isEmpty()) return s;
        if (s.isEmpty() || s.length() > 253 || !s.matches("[A-Za-z0-9._:%\\[\\]-]+"))
            throw new IOException("Invalid bind/UDP hostname");
        return s;
    }
    private static int number(Properties p, String key, int fallback, int low, int high) throws IOException {
        try {
            int value = Integer.parseInt(p.getProperty(key, Integer.toString(fallback)).trim());
            if (value < low || value > high) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) { throw new IOException(key + " must be " + low + ".." + high); }
    }
    private static boolean bool(Properties p, String key, boolean fallback) throws IOException {
        String value = p.getProperty(key, Boolean.toString(fallback)).trim();
        if (!value.equals("true") && !value.equals("false")) throw new IOException(key + " must be true/false");
        return Boolean.parseBoolean(value);
    }
}
