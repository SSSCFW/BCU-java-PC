package online.net.duel;

import com.google.gson.*;
import online.net.Protocol;
import online.sync.*;
import java.io.IOException;
import java.util.*;

/** The only network-to-battle adapter that assumes two players and two castles. */
public final class DuelRoster {
    private final int[] ids = new int[2];
    private final String[] names = new String[2];
    private final double[] castleHealthMultiplier = new double[2];
    private int left;
    private DuelRoster() { }
    public static DuelRoster read(JsonObject message) throws IOException {
        if (!"DUEL_1V1".equals(Protocol.string(message, "mode", 32)) || !message.has("players") ||
                !message.get("players").isJsonArray() || message.getAsJsonArray("players").size() != 2)
            throw new IOException("This battle renderer supports DUEL_1V1 only");
        DuelRoster result = new DuelRoster(); Set<String> seats = new HashSet<>();
        for (int i = 0; i < 2; i++) {
            JsonObject p = message.getAsJsonArray("players").get(i).getAsJsonObject();
            result.ids[i] = Protocol.integer(p, "id"); result.names[i] = Protocol.string(p, "name", 40);
            result.castleHealthMultiplier[i]=p.has("castleHealthMultiplier")?Protocol.real(p,"castleHealthMultiplier",0.1,1000.0):20.0;
            String seat = Protocol.string(p, "seat", 16); int team = Protocol.integer(p, "team");
            if (result.ids[i] <= 0 || !seats.add(seat) ||
                    !(seat.equals("left") && team == 0 || seat.equals("right") && team == 1))
                throw new IOException("Invalid duel roster/seat");
            if (seat.equals("left")) result.left = i;
        }
        if (result.ids[0] == result.ids[1]) throw new IOException("Duplicate duel identity");
        return result;
    }
    public int indexOf(int id) throws IOException {
        for (int i = 0; i < ids.length; i++) if (ids[i] == id) return i;
        throw new IOException("Unknown duel participant");
    }
    public int playerId(int index) { return ids[index]; }
    public String name(int index) { return names[index]; }
    public int leftIndex() { return left; }
    public int rightIndex() { return 1 - left; }
    public double castleHealthMultiplier(int index){return castleHealthMultiplier[index];}
    public InputFrame toDuel(ResolvedFrame frame) throws IOException {
        if (frame.inputs.size() != 2 || !frame.inputs.containsKey(ids[0]) || !frame.inputs.containsKey(ids[1]))
            throw new IOException("Frame participants differ from duel roster");
        return new InputFrame(frame.tick, frame.inputs.get(ids[left]), frame.inputs.get(ids[1-left]));
    }
}
