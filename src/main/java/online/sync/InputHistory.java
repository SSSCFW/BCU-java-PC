package online.sync;
import java.io.IOException;
import java.util.*;

/** Once sampled, commands for a tick are immutable across all retransmissions/transports. */
public final class InputHistory {
    private final TreeMap<Long,Integer> history=new TreeMap<>();
    private long next;
    public long nextTick(){return next;}
    public void add(long tick,int mask) throws IOException {
        if(!InputFrame.valid(mask) || tick<0 || tick>next)throw new IOException("Invalid input schedule");
        if(tick<next) {if(!Objects.equals(history.get(tick),mask))throw new IOException("Conflicting input schedule");return;}
        history.put(tick,mask);next++;
        while(history.size()>128)history.pollFirstEntry();
    }
    public SortedMap<Long,Integer> batch(long requested) throws IOException {
        TreeMap<Long,Integer> out=new TreeMap<>();
        for(Map.Entry<Long,Integer> e:history.descendingMap().entrySet()) {out.put(e.getKey(),e.getValue());if(out.size()==4)break;}
        if(requested>=0 && requested<next) {
            Integer value=history.get(requested);if(value==null)throw new IOException("Requested input expired from bounded history");
            out.put(requested,value);
        }
        return out;
    }
}
