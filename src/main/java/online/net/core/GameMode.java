package online.net.core;

import java.io.IOException;
import java.util.Collection;

/** Room capacity, seats and teams are game rules, never transport assumptions. */
public interface GameMode {
    String id();
    int minPlayers();
    int maxPlayers();
    Seat assign(Collection<Seat> occupied, String requested) throws IOException;
    boolean validWinner(int team);
    final class Seat {
        public final String name;
        public final int team;
        public Seat(String name, int team) { this.name = name; this.team = team; }
    }
}
