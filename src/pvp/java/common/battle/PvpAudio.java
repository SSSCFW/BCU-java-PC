package common.battle;

import common.CommonStatic;

/** Presentation-only listener context; it is never a field in hashed/cloned battle state. */
public final class PvpAudio {
    private static final ThreadLocal<Integer> LISTENER=new ThreadLocal<>();
    private PvpAudio(){}
    public static void forPlayer(int direction,Runnable task){
        if(direction!=1&&direction!=-1)throw new IllegalArgumentException("Invalid audio listener side");
        Integer previous=LISTENER.get();LISTENER.set(direction);
        try{task.run();}finally{if(previous==null)LISTENER.remove();else LISTENER.set(previous);}
    }
    public static void notification(StageBasis owner,int sound){
        Integer local=LISTENER.get();
        if(!owner.isPvp()||local==null||local==owner.ownDirection())CommonStatic.setSE(sound);
    }
}
