package online.sync;
import java.io.IOException;
import java.util.*;

/** Bounded application reorder window. Later packets may arrive before a missing tick. */
public final class FrameBuffer {
    private final Set<Integer> ids;
    private final TreeMap<Long,ResolvedFrame> pending=new TreeMap<>(),history=new TreeMap<>();
    private long next;
    public FrameBuffer(Collection<Integer> players) {
        ids=Collections.unmodifiableSet(new TreeSet<>(players));
        if(ids.isEmpty() || ids.size()>8 || ids.size()!=players.size() || Collections.min(ids)<=0) throw new IllegalArgumentException("Invalid roster");
    }
    public long nextTick(){return next;}
    public List<ResolvedFrame> accept(ResolvedFrame frame) throws IOException {
        if(!frame.inputs.keySet().equals(ids) || frame.tick>next+128) throw new IOException("Frame outside roster/window");
        ResolvedFrame previous=frame.tick<next?history.get(frame.tick):pending.get(frame.tick);
        if(previous!=null && !previous.inputs.equals(frame.inputs)) throw new IOException("Conflicting resolved frame");
        if(frame.tick<next)return Collections.emptyList();
        pending.put(frame.tick,frame);
        List<ResolvedFrame> out=new ArrayList<>();
        while(pending.containsKey(next)) {ResolvedFrame f=pending.remove(next);history.put(next++,f);out.add(f);}
        while(history.size()>128)history.pollFirstEntry();
        return out;
    }
}
