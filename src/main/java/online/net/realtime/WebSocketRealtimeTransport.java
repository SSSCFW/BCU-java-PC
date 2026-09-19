package online.net.realtime;
import com.google.gson.JsonObject;
import java.util.function.Consumer;

public final class WebSocketRealtimeTransport implements RealtimeTransport {
    private final Consumer<JsonObject> sender;
    private boolean closed;
    public WebSocketRealtimeTransport(Consumer<JsonObject> sender){this.sender=sender;}
    public void send(RealtimeData data,long now,boolean active){if(!closed)sender.accept(data.control("realtime"));}
    public void close(){closed=true;}
}
