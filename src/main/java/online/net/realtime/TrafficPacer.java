package online.net.realtime;

/** Bounded low-rate UDP, including retransmits. No response-driven ACK storm. */
public final class TrafficPacer {
    private long last;
    public boolean permit(long now,boolean active,ReliabilityWindow.Metrics metrics,long silenceNanos) {
        long interval=active?33_333_333L:250_000_000L;
        if(active && (metrics.lossRate>.1 || silenceNanos>250_000_000L))interval=Math.max(interval,66_666_666L);
        if(silenceNanos>1_000_000_000L)interval=Math.max(interval,250_000_000L);
        if(last!=0 && now-last<interval)return false;last=now;return true;
    }
}
