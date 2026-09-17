package online.tests;

public final class Check {
    public static int count;
    private Check() {}
    public static void that(boolean condition, String message) {
        count++;
        if (!condition) throw new AssertionError(message);
    }
    public static void equal(Object expected, Object actual, String message) {
        that(java.util.Objects.equals(expected, actual), message + ": expected=" + expected + ", actual=" + actual);
    }
    public interface Checked { void run() throws Exception; }
    public static void rejects(Checked action, String message) throws Exception {
        count++;
        try { action.run(); } catch (IllegalArgumentException | java.io.IOException expected) { return; }
        throw new AssertionError(message);
    }
}
