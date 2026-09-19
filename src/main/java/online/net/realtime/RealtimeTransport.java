package online.net.realtime;
import java.io.IOException;

/** Delivery policy belongs to a transport; deterministic room rules do not. */
public interface RealtimeTransport extends AutoCloseable {
    void send(RealtimeData data,long now,boolean active) throws IOException;
    @Override void close();
}
