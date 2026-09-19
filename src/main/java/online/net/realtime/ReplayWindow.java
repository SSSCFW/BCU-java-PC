package online.net.realtime;

/** Unsigned 64-bit sequences; 64 packets of authenticated reorder/anti-replay history. */
public final class ReplayWindow {
    private boolean initialized;
    private long highest,bits;
    public boolean accept(long sequence) {
        if(sequence==0)return false;
        if(!initialized){initialized=true;highest=sequence;bits=1;return true;}
        if(Long.compareUnsigned(sequence,highest)>0){long delta=sequence-highest;bits=Long.compareUnsigned(delta,64)>=0?1:(bits<<(int)delta)|1;highest=sequence;return true;}
        long behind=highest-sequence;
        if(Long.compareUnsigned(behind,64)>=0 || (bits&(1L<<(int)behind))!=0)return false;
        bits|=1L<<(int)behind;return true;
    }
    public long highest(){return initialized?highest:0;}
    public int ackBits(){return (int)(bits>>>1);}
}
