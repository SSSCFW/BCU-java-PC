package online.sync;
import java.util.*;

/** Immutable inputs keyed by participant identity, not castle side or join order. */
public final class ResolvedFrame {
    public final long tick;
    public final SortedMap<Integer,Integer> inputs;
    public ResolvedFrame(long tick, Map<Integer,Integer> inputs) {
        if(tick<0 || inputs==null || inputs.isEmpty() || inputs.size()>8) throw new IllegalArgumentException("Invalid resolved frame");
        TreeMap<Integer,Integer> copy=new TreeMap<>();
        for(Map.Entry<Integer,Integer> e:inputs.entrySet()) {
            if(e.getKey()==null || e.getKey()<=0 || e.getValue()==null || !InputFrame.valid(e.getValue())) throw new IllegalArgumentException("Invalid frame participant/input");
            copy.put(e.getKey(),e.getValue());
        }
        this.tick=tick;this.inputs=Collections.unmodifiableSortedMap(copy);
    }
}
