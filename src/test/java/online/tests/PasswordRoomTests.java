package online.tests;

import com.google.gson.JsonObject;
import online.bundle.Hashes;
import online.net.*;
import java.net.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** Real loopback admission: an empty password is a room policy, not invalid protocol. */
public final class PasswordRoomTests {
    private static final String GAME = Hashes.sha256(new byte[]{4, 2});
    private static final class Peer implements RoomClient.Listener, AutoCloseable {
        final BlockingQueue<JsonObject> events = new LinkedBlockingQueue<>();
        final RoomClient client;
        volatile String failure;
        Peer(int port) throws Exception {
            client = new RoomClient(new URI("ws://127.0.0.1:" + port), false, false, this);
            Check.that(client.connectBlocking(3, TimeUnit.SECONDS), "password test connects on loopback");
        }
        public void event(JsonObject o) { events.add(o); }
        public void bundle(int id, Path file, String hash) { throw new AssertionError("No bundles during admission test"); }
        public void failed(String reason) { failure = reason; }
        JsonObject take(String type) throws Exception {
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < end) {
                if (failure != null) throw new AssertionError("Expected " + type + ", got " + failure);
                JsonObject o = events.poll(20, TimeUnit.MILLISECONDS);
                if (o != null && type.equals(o.get("type").getAsString())) return o;
            }
            throw new AssertionError("No " + type);
        }
        void enter(boolean create, String room, String password) { client.enter(create, room, password, "Tester", "right", GAME); }
        void rejected(String code) throws Exception {
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (failure == null && System.nanoTime() < end) Thread.sleep(10);
            Check.that(failure != null && failure.startsWith(code + ":"), "admission error " + code + ": " + failure);
        }
        public void close() { client.close(); }
    }
    public static void run() throws Exception {
        RoomServer server = new RoomServer(new InetSocketAddress("127.0.0.1", 0));
        server.start(); Check.that(server.awaitStarted(5, TimeUnit.SECONDS), "password test server starts");
        try {
            try (Peer host = new Peer(server.getPort())) {
                host.enter(true, "my-room_01", "");JsonObject joined=host.take("joined");
                Check.equal("MY-ROOM_01",joined.get("room").getAsString(),"host-selected room ID is normalized and preserved");
                try(Peer duplicate=new Peer(server.getPort())){duplicate.enter(true,"MY-ROOM_01","");duplicate.rejected("ROOM_EXISTS");}
                try(Peer guest=new Peer(server.getPort())){guest.enter(false,"my-room_01","");Check.equal("MY-ROOM_01",guest.take("joined").get("room").getAsString(),"custom room ID joins case-insensitively");}
            }
            try (Peer host = new Peer(server.getPort()); Peer guest = new Peer(server.getPort())) {
                host.enter(true, "", ""); JsonObject joined = host.take("joined");
                Check.equal(false, joined.get("passwordRequired").getAsBoolean(), "blank password creates open room");
                guest.enter(false, joined.get("room").getAsString(), "");
                Check.equal(false, guest.take("joined").get("passwordRequired").getAsBoolean(), "blank password joins open room");
                host.take("room_state"); guest.take("room_state");
                try (Peer third = new Peer(server.getPort())) {
                    third.enter(false, joined.get("room").getAsString(), ""); third.rejected("FULL");
                }
            }
            try (Peer host = new Peer(server.getPort())) {
                host.enter(true, "", "test-password"); JsonObject joined = host.take("joined");
                String room = joined.get("room").getAsString();
                Check.equal(true, joined.get("passwordRequired").getAsBoolean(), "protected room metadata");
                for (String wrong : Arrays.asList("", "x", "wrong-password")) {
                    try (Peer guest = new Peer(server.getPort())) { guest.enter(false, room, wrong); guest.rejected("AUTH"); }
                }
                try (Peer guest = new Peer(server.getPort())) {
                    guest.enter(false, room, "test-password"); guest.take("joined");
                    Check.equal(1, server.roomCount(), "wrong passwords did not destroy the host's room");
                }
            }
            try (Peer host = new Peer(server.getPort()); Peer guest = new Peer(server.getPort())) {
                host.enter(true, "", ""); String room = host.take("joined").get("room").getAsString();
                guest.enter(false, room, "unused-value"); guest.take("joined");
                Check.that(true, "open room ignores an accidentally supplied password");
            }
        } finally { server.stop(1000); }
    }
    public static void main(String[] args) throws Exception { run(); System.out.println("Optional password tests passed"); }
}
