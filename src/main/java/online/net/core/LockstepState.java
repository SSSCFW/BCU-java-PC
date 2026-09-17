package online.net.core;

import online.bundle.Hashes;
import online.net.Protocol;
import online.sync.*;
import java.io.IOException;
import java.util.*;

/** Bounded, participant-keyed deterministic barrier. Owned by the room-core monitor. */
public final class LockstepState {
    public static final int HISTORY = 128;
    private final Set<Integer> players;
    private final int maxAhead;
    private long tick;
    private final TreeMap<Long, Map<Integer, Integer>> pending = new TreeMap<>();
    private final TreeMap<Long, ResolvedFrame> history = new TreeMap<>();
    private final Map<Integer, Long> inputAck = new HashMap<>(), hashAck = new HashMap<>();
    private final Map<Integer, TreeSet<Long>> hashReceived = new HashMap<>();
    private final TreeMap<Long, Map<Integer, String>> checkpoints = new TreeMap<>();
    private final TreeMap<Long, String> checkedHashes = new TreeMap<>();

    public LockstepState(Collection<Integer> ids, int maxAhead) {
        TreeSet<Integer> copy = new TreeSet<>(ids);
        if (copy.isEmpty() || copy.size() > 8 || copy.size() != ids.size() || copy.first() <= 0 || maxAhead < 8 || maxAhead > 128)
            throw new IllegalArgumentException("Invalid lockstep participants/window");
        players = Collections.unmodifiableSet(copy); this.maxAhead = maxAhead;
        for (int id : players) { inputAck.put(id, 0L); hashAck.put(id, 0L); hashReceived.put(id, new TreeSet<>()); }
    }
    public Set<Integer> players() { return players; }
    public long tick() { return tick; }
    public long nextInput(int id) { return inputAck.get(id); }
    public long hashAck(int id) { return hashAck.get(id); }
    public void input(int id, long at, int mask) throws IOException {
        if (!players.contains(id) || at < 0 || at > tick + maxAhead || !InputFrame.valid(mask))
            throw new IOException("Input identity/tick/mask outside negotiated window");
        if (at < tick) {
            ResolvedFrame old = history.get(at);
            if (old != null && old.inputs.get(id) != mask) throw new IOException("Conflicting past input");
            return;
        }
        Map<Integer, Integer> frame = pending.computeIfAbsent(at, unused -> new TreeMap<>());
        Integer old = frame.putIfAbsent(id, mask);
        if (old != null && old != mask) throw new IOException("Conflicting duplicate input");
        long next = inputAck.get(id);
        while (pending.containsKey(next) && pending.get(next).containsKey(id)) next++;
        inputAck.put(id, next);
    }
    public boolean hasInput(int id, long at) {
        return at < tick || (pending.containsKey(at) && pending.get(at).containsKey(id));
    }
    public ResolvedFrame resolve() {
        if (tick >= Collections.min(hashAck.values()) + 2L * Protocol.HASH_INTERVAL) return null;
        Map<Integer, Integer> inputs = pending.get(tick);
        if (inputs == null || !inputs.keySet().equals(players)) return null;
        ResolvedFrame out = new ResolvedFrame(tick, inputs);
        pending.remove(tick); history.put(tick++, out);
        while (history.size() > HISTORY) history.pollFirstEntry();
        return out;
    }
    public java.util.List<ResolvedFrame> recent(long requested) throws IOException {
        TreeMap<Long, ResolvedFrame> out = new TreeMap<>();
        for (ResolvedFrame f : history.descendingMap().values()) {
            out.put(f.tick, f); if (out.size() == 4) break;
        }
        if (requested >= 0 && requested < tick) {
            ResolvedFrame f = history.get(requested);
            if (f == null) throw new IOException("Requested frame has expired from bounded history");
            out.put(f.tick, f);
        }
        return new ArrayList<>(out.values());
    }
    public void checkpoint(int id, long at, String hash) throws IOException {
        if (!players.contains(id) || at <= 0 || at > tick || at % Protocol.HASH_INTERVAL != 0 || !Hashes.valid(hash))
            throw new IOException("Invalid state checkpoint");
        String checked = checkedHashes.get(at);
        if (checked != null) {
            if (!checked.equals(hash)) throw new IOException("Battle state mismatch at tick " + at);
            return;
        }
        if (at <= hashAck.get(id)) return; // duplicate older than retained history
        Map<Integer, String> values = checkpoints.computeIfAbsent(at, unused -> new TreeMap<>());
        String old = values.putIfAbsent(id, hash);
        if (old != null && !old.equals(hash)) throw new IOException("Conflicting checkpoint");
        TreeSet<Long> received = hashReceived.get(id); received.add(at);
        long contiguous = hashAck.get(id);
        while (received.remove(contiguous + Protocol.HASH_INTERVAL)) contiguous += Protocol.HASH_INTERVAL;
        hashAck.put(id, contiguous);
        if (values.size() == players.size()) {
            if (new HashSet<>(values.values()).size() != 1) throw new IOException("Battle state mismatch at tick " + at);
            checkpoints.remove(at); checkedHashes.put(at, hash);
            while (checkedHashes.size() > 8) checkedHashes.pollFirstEntry();
        }
    }
}
