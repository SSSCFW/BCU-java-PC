package online.tests;

public final class ClockTests {
    public static void run() throws Exception {
        // Reflection lets the first run fail as an assertion, not a compiler error.
        Class<?> clock;
        try { clock = Class.forName("online.sync.FixedTickClock"); }
        catch (ClassNotFoundException e) { throw new AssertionError("Missing fixed 30 TPS clock", e); }
        Object c30 = clock.getConstructor(int.class).newInstance(30);
        Object c60 = clock.getConstructor(int.class).newInstance(30);
        java.lang.reflect.Method due = clock.getMethod("due", long.class);
        due.invoke(c30, 0L); due.invoke(c60, 0L);
        int n30=0, n60=0;
        for (int i=1; i<=300; i++) n30 += (Integer) due.invoke(c30, i*1_000_000_000L/30);
        for (int i=1; i<=600; i++) n60 += (Integer) due.invoke(c60, i*1_000_000_000L/60);
        Check.equal(300, n30, "30 FPS: ten seconds = 300 ticks");
        Check.equal(n30, n60, "display FPS cannot change logic count");
        Object slow = clock.getConstructor(int.class).newInstance(30);
        due.invoke(slow, 0L);
        int backlogged=(Integer) due.invoke(slow, 1_000_000_000L);
        Check.that(backlogged<=5, "bounded catch-up per UI callback");
        int total=backlogged;
        for(int i=0;i<10;i++) total+=(Integer)due.invoke(slow,1_000_000_000L);
        Check.equal(30,total,"catch-up must not discard ticks");

        Class<?> lobby=Class.forName("online.ui.OnlineLobbyPage");
        java.lang.reflect.Method advance=lobby.getDeclaredMethod("advanceDeadline",long.class,long.class,long.class);
        advance.setAccessible(true);
        for(int fps:new int[]{30,60}){
            long interval=1_000_000_000L/fps,deadline=0;
            int renders=0;
            for(long now=0;now<10_000_000_000L;now+=5_000_000L){
                if(now>=deadline){
                    renders++;
                    deadline=(Long)advance.invoke(null,deadline,now,interval);
                    Check.that(deadline>now,"presentation deadline always advances into the future");
                }
            }
            Check.that(Math.abs(renders-fps*10)<=1,"5 ms Swing pulse preserves "+fps+" FPS presentation cadence");
        }
        long interval=1_000_000_000L/60;
        long delayed=(Long)advance.invoke(null,interval,interval*5+1,interval);
        Check.that(delayed>interval*5+1&&delayed<=interval*6+1,
                "presentation pacing skips stale deadlines instead of burst-rendering catch-up frames");
    }
}
