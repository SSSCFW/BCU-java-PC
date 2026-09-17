package common.battle;

/** Marks fixed simulation updates without modifying the user's global display setting. */
public final class PvpTiming {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<String> MATCH = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> HALF = ThreadLocal.withInitial(() -> false);
    public static boolean isDisplayHalfStep(){return HALF.get();}
    public static void halfStep(Runnable action){boolean previous=HALF.get();HALF.set(true);try{action.run();}finally{if(previous)HALF.set(true);else HALF.remove();}}
    private PvpTiming() {}
    public static String currentMatch(){return MATCH.get();}
    public static <T> T inMatch(String match, java.util.concurrent.Callable<T> task) throws Exception {
        String previous=MATCH.get();MATCH.set(match);
        try{return task.call();}finally{if(previous==null)MATCH.remove();else MATCH.set(previous);}
    }
    public static boolean isLogicStep() { return DEPTH.get() > 0; }
    public static void logic(Runnable update) {
        int depth = DEPTH.get(); DEPTH.set(depth + 1);
        try { update.run(); } finally { if (depth == 0) DEPTH.remove(); else DEPTH.set(depth); }
    }
}
