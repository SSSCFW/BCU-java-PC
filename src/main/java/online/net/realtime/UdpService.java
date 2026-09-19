package online.net.realtime;
import com.google.gson.JsonObject;
import java.io.IOException;

/** Minimal bootstrap/transport service consumed by the room core. */
public interface UdpService extends AutoCloseable {
    interface Listener { void data(RealtimeData data); void failed(String reason); }
    interface Connection extends RealtimeTransport {
        JsonObject offer(String advertisedHost);
        boolean isBound();
        long lastReceived();
        ReliabilityWindow.Metrics metrics();
    }
    Connection register(Listener listener) throws IOException;
    int port();
    @Override void close();
}
