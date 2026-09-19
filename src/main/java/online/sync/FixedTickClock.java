package online.sync;

/** Monotonic rational clock: display cadence never determines simulation time. */
public final class FixedTickClock {
    private final int ticksPerSecond;
    private boolean started;
    private long previous, credit;
    public FixedTickClock(int ticksPerSecond) {
        if (ticksPerSecond < 1 || ticksPerSecond > 240) throw new IllegalArgumentException("tick rate");
        this.ticksPerSecond = ticksPerSecond;
    }
    public int due(long now) {
        if (!started) { previous = now; started = true; return 0; }
        long elapsed = now - previous;
        if (elapsed < 0 || elapsed > Long.MAX_VALUE / ticksPerSecond)
            throw new IllegalArgumentException("non-monotonic or excessive interval");
        previous = now;
        credit = Math.addExact(credit, elapsed * ticksPerSecond);
        int ticks = (int) Math.min(5L, credit / 1_000_000_000L);
        credit -= ticks * 1_000_000_000L;
        return ticks;
    }
    public void reset(long now) { started = true; previous = now; credit = 0; }
}
