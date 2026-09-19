package online.net;

import online.net.config.ServerConfig;
import java.io.IOException;
import java.nio.file.*;

/** Properties-based launcher; does not require BCU assets, display server, or a database. */
public final class PvpServerMain {
    private PvpServerMain() { }
    public static void main(String[] args) {
        try {
            if (args.length == 1 && args[0].equals("--help")) {
                System.out.println("Usage: java -cp 'bcu-pvp.jar:lib/*' online.net.PvpServerMain [pvp-server.properties]\n"
                        + "       online.net.PvpServerMain --check-config [pvp-server.properties]\n"
                        + "Default file: pvp-server.properties. TCP 8766 + UDP 8767. Use ';' instead of ':' on Windows.\n"
                        + "Ctrl+C stops the server and deletes room caches. Plain WS is supported; use WSS when encryption is required.");
                return;
            }
            boolean check = args.length > 0 && args[0].equals("--check-config");
            int position = check ? 1 : 0;
            if (args.length > position + 1) throw new IOException("Too many arguments; use --help");
            Path file = Paths.get(args.length > position ? args[position] : "pvp-server.properties");
            ServerConfig config = ServerConfig.load(file);
            if (check) { System.out.println("Configuration OK: " + file + "; inputDelayTicks=" + config.inputDelayTicks); return; }
            ServerHost host = ServerHost.start(config);
            Runtime.getRuntime().addShutdownHook(new Thread(host::close, "pvp-server-shutdown"));
            System.out.println(host.description());
        } catch (Exception e) {
            System.err.println("BCU server could not start: " + e.getMessage());
            System.err.println("Check pvp-server.properties, Java 21, and whether TCP/UDP ports are already used.");
            System.exit(1);
        }
    }
}
