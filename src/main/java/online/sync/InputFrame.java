package online.sync;

public final class InputFrame {
    public static final int WORKER=1<<10, SPECIAL=1<<11, CANNON=SPECIAL, DEBUG_ROULETTE_MAX=1<<22;
    /** Normal one-frame commands occupy bits 0..22. Slot-order swaps use the upper 9 bits. */
    public static final int ALLOWED=(1<<23)-1;
    private static final int SLOT_SWAP_FROM_SHIFT=23, SLOT_SWAP_TO_SHIFT=27, SLOT_SWAP_NIBBLE=0xF;
    public static final int SLOT_SWAP_FLAG=1<<31;
    public final long tick;
    public final int left,right;
    public InputFrame(long tick,int left,int right) {
        if(tick<0 || !valid(left) || !valid(right)) throw new IllegalArgumentException("Invalid input frame");
        this.tick=tick; this.left=left; this.right=right;
    }
    public static int slotSwap(int from,int to) {
        if(from<0||from>=10||to<0||to>=10||from==to)throw new IllegalArgumentException("Invalid slot swap");
        return SLOT_SWAP_FLAG|(from<<SLOT_SWAP_FROM_SHIFT)|(to<<SLOT_SWAP_TO_SHIFT);
    }
    public static boolean hasSlotSwap(int input){return (input&SLOT_SWAP_FLAG)!=0;}
    public static int slotSwapFrom(int input){return hasSlotSwap(input)?(input>>>SLOT_SWAP_FROM_SHIFT)&SLOT_SWAP_NIBBLE:-1;}
    public static int slotSwapTo(int input){return hasSlotSwap(input)?(input>>>SLOT_SWAP_TO_SHIFT)&SLOT_SWAP_NIBBLE:-1;}
    public static int baseCommands(int input){return input&ALLOWED;}
    public static boolean valid(int input) {
        int high=input&~ALLOWED;
        if(high==0)return true;
        if(!hasSlotSwap(input))return false;
        int from=slotSwapFrom(input),to=slotSwapTo(input);
        int encoded=SLOT_SWAP_FLAG|(from<<SLOT_SWAP_FROM_SHIFT)|(to<<SLOT_SWAP_TO_SHIFT);
        return high==encoded&&from<10&&to<10&&from!=to;
    }
}
