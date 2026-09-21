package online.net.core;

import java.io.IOException;
import java.util.Collection;

/** The only production mode for now. Multiplayer modes require a separate battle implementation. */
public final class Duel1v1Mode implements GameMode {
    public String id() { return "DUEL_1V1"; }
    public int minPlayers() { return 2; }
    public int maxPlayers() { return 2; }
    public Seat assign(Collection<Seat> occupied, String requested) throws IOException {
        if (occupied.size() >= 2) throw new IOException("Duel is full");
        if (!requested.equals("left") && !requested.equals("right")) throw new IOException("Invalid castle side");
        String side = requested;
        if (!occupied.isEmpty()) side = occupied.iterator().next().name.equals("left") ? "right" : "left";
        return new Seat(side, side.equals("left") ? 0 : 1);
    }
    public boolean validWinner(int team) { return team >= -1 && team <= 1; }
}
