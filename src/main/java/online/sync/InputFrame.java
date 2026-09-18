package online.sync;

public final class InputFrame {
    public static final int WORKER=1<<10, SPECIAL=1<<11, CANNON=SPECIAL, DEBUG_ROULETTE_MAX=1<<22;
    public static final int ALLOWED=(1<<23)-1;
    public final long tick;
    public final int left,right;
    public InputFrame(long tick,int left,int right) {
        if(tick<0 || !valid(left) || !valid(right)) throw new IllegalArgumentException("Invalid input frame");
        this.tick=tick; this.left=left; this.right=right;
    }
    public static boolean valid(int input) { return (input & ~ALLOWED)==0; }
}
