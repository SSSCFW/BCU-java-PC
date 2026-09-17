package online.sync;

public final class InputFrame {
    public static final int WORKER=1<<10, CANNON=1<<11;
    public static final int ALLOWED=(1<<22)-1;
    public final long tick;
    public final int left,right;
    public InputFrame(long tick,int left,int right) {
        if(tick<0 || !valid(left) || !valid(right)) throw new IllegalArgumentException("Invalid input frame");
        this.tick=tick; this.left=left; this.right=right;
    }
    public static boolean valid(int input) { return (input & ~ALLOWED)==0; }
}
