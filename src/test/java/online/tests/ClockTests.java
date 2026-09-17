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
    }
}
