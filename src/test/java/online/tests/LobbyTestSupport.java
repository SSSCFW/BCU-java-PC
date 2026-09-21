package online.tests;
import com.google.gson.*;
import online.net.RoomClient;
/** Transport-only fixtures explicitly consent to defaults; production UI never auto-readies. */
public final class LobbyTestSupport {
    private LobbyTestSupport(){}
    public static void acceptDefaults(RoomClient client,JsonObject event){
        if(!"room_state".equals(event.get("type").getAsString())||event.getAsJsonArray("players").size()<2)return;
        for(JsonElement p:event.getAsJsonArray("players"))if(p.getAsJsonObject().get("id").getAsInt()==client.playerId()&&!p.getAsJsonObject().get("lobbyReady").getAsBoolean())client.lobbyReady(true);
    }
}
