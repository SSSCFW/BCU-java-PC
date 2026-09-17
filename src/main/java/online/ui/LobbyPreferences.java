package online.ui;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

/** Per-installation, non-secret lobby fields only. Never save passwords, room IDs or transport keys. */
final class LobbyPreferences {
    static final String DEFAULT_SERVER = "ws://127.0.0.1:8766";
    final String serverAddress, displayName;
    LobbyPreferences(String serverAddress, String displayName) {
        this.serverAddress = serverAddress;
        this.displayName = displayName;
    }
    static LobbyPreferences load(Path file, String fallbackName) throws IOException {
        Properties properties = new Properties();
        if (Files.exists(file)) {
            if (Files.size(file) > 32768) throw new IOException("Lobby preferences file is too large");
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { properties.load(in); }
            catch (IllegalArgumentException e) { throw new IOException("Invalid lobby preferences", e); }
        }
        return new LobbyPreferences(properties.getProperty("serverAddress", DEFAULT_SERVER),
                properties.getProperty("displayName", fallbackName == null ? "" : fallbackName));
    }
    void save(Path file) throws IOException {
        Path target = file.toAbsolutePath(), parent = target.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".online-client-", ".tmp");
        try {
            Properties properties = new Properties();
            properties.setProperty("serverAddress", serverAddress);
            properties.setProperty("displayName", displayName);
            try (Writer out = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(out, "BCU online lobby (display name and server address only)");
            }
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
