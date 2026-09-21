package online.net.realtime;
import java.util.*;

/** Packet ACK telemetry, not tick delivery. Every retransmission has a fresh sequence. */
public final class ReliabilityWindow {
    private final LinkedHashMap<Long,Long> sent=new LinkedHashMap<>();
    private long total,acknowledged,lost,lastSequence;
    private double srtt,variance,loss;
    public void sent(long sequence,long now) {
        sent.put(sequence,now);lastSequence=sequence;total++;
        while(sent.size()>256){Iterator<Long> it=sent.keySet().iterator();it.next();it.remove();lost++;loss=.95*loss+.05;}
    }
    public void acknowledge(long ack,int bits,long now) {
        if(ack==0 || total==0 || Long.compareUnsigned(ack,lastSequence)>0)return;
        for(Iterator<Map.Entry<Long,Long>> it=sent.entrySet().iterator();it.hasNext();) {
            Map.Entry<Long,Long> e=it.next();long seq=e.getKey();
            if(Long.compareUnsigned(seq,ack)>0)continue;
            long delta=ack-seq;
            boolean received=delta==0 || (Long.compareUnsigned(delta,32)<=0 && (bits&(1<<(int)(delta-1)))!=0);
            if(received){
                double rtt=Math.max(0,now-e.getValue())/1_000_000.0;
                if(acknowledged==0){srtt=rtt;variance=rtt/2;}else{variance=.75*variance+.25*Math.abs(srtt-rtt);srtt=.875*srtt+.125*rtt;}
                acknowledged++;loss*=.95;it.remove();
            }else if(Long.compareUnsigned(delta,32)>0 && now-e.getValue()>retryNanos()){lost++;loss=.95*loss+.05;it.remove();}
        }
    }
    public long retryNanos(){return (long)(Math.max(100,Math.min(1000,srtt+4*variance))*1_000_000);}
    public Metrics metrics(){return new Metrics(srtt,variance,loss,total,acknowledged,lost);}
    public static final class Metrics {
        public final double smoothedRttMillis,jitterMillis,lossRate;
        public final long sentPackets,acknowledgedPackets,lostPackets;
        Metrics(double r,double j,double l,long s,long a,long lost){smoothedRttMillis=r;jitterMillis=j;lossRate=l;sentPackets=s;acknowledgedPackets=a;lostPackets=lost;}
    }
}
