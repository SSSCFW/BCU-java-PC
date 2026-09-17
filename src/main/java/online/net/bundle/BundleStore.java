package online.net.bundle;

import online.bundle.Hashes;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Room-scoped content-addressed cache. Never deserializes or extracts an uploaded game pack. */
public final class BundleStore implements AutoCloseable {
    public enum Offer { UPLOAD, WAIT, CACHED }
    private static final class Entry {
        final long size; final Path path; final MessageDigest digest = Hashes.digest();
        OutputStream output; long received; boolean verified;
        Entry(long size, Path path) throws IOException { this.size = size; this.path = path; output = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW); }
    }
    private final Path root;
    private final long limit;
    private final int capacity;
    private final Map<String, Entry> entries = new HashMap<>();
    private final Set<InputStream> readers = new HashSet<>();
    private boolean closed;
    public BundleStore(long perBundleLimit, int maxBundles) throws IOException {
        if (perBundleLimit <= 0 || maxBundles < 1 || maxBundles > 8) throw new IllegalArgumentException("Invalid cache limits");
        root = Files.createTempDirectory("bcu-room-bundles-"); limit = perBundleLimit; capacity = maxBundles;
    }
    public Path root() { return root; }
    public synchronized Offer offer(String hash, long size) throws IOException {
        checkOpen();
        if (!Hashes.valid(hash) || size <= 0 || size > limit) throw new IOException("Invalid bundle size/hash");
        Entry old = entries.get(hash);
        if (old != null) {
            if (old.size != size) throw new IOException("Contradictory size for the same bundle hash");
            return old.verified ? Offer.CACHED : Offer.WAIT;
        }
        if (entries.size() >= capacity) throw new IOException("Room cache capacity exceeded");
        entries.put(hash, new Entry(size, root.resolve(hash + ".zip")));
        return Offer.UPLOAD;
    }
    public synchronized void append(String hash, byte[] bytes) throws IOException {
        checkOpen(); Entry e = entries.get(hash);
        if (e == null || e.output == null || e.verified || bytes.length == 0 || bytes.length > 65536 || e.received + bytes.length > e.size)
            throw new IOException("Unexpected or oversized bundle chunk");
        e.output.write(bytes); e.digest.update(bytes); e.received += bytes.length;
    }
    public synchronized void finish(String hash) throws IOException {
        checkOpen(); Entry e = entries.get(hash);
        if (e == null || e.output == null) throw new IOException("Unexpected upload completion");
        e.output.close(); e.output = null;
        if (e.received != e.size || !Hashes.hex(e.digest.digest()).equals(hash)) {
            Files.deleteIfExists(e.path); entries.remove(hash); throw new IOException("Incomplete bundle or checksum mismatch");
        }
        e.verified = true;
    }
    public synchronized boolean contains(String hash) { Entry e = entries.get(hash); return !closed && e != null && e.verified; }
    public synchronized long size(String hash) throws IOException {
        checkOpen(); Entry e = entries.get(hash);
        if (e == null || !e.verified) throw new IOException("Bundle not verified");
        return e.size;
    }
    public synchronized InputStream open(String hash) throws IOException {
        size(hash);
        InputStream in = new FilterInputStream(Files.newInputStream(entries.get(hash).path)) {
            @Override public void close() throws IOException { try { super.close(); } finally { synchronized (BundleStore.this) { readers.remove(this); } } }
        };
        readers.add(in); return in;
    }
    private void checkOpen() throws IOException { if (closed) throw new IOException("Room cache closed"); }
    @Override public synchronized void close() throws IOException {
        if (closed) return; closed = true; IOException error = null;
        for (InputStream in : new ArrayList<>(readers)) try { in.close(); } catch (IOException e) { error = e; }
        for (Entry entry : entries.values()) {
            try { if (entry.output != null) entry.output.close(); } catch (IOException e) { error = e; }
            try { Files.deleteIfExists(entry.path); } catch (IOException e) { error = e; }
        }
        entries.clear(); readers.clear();
        try { Files.deleteIfExists(root); } catch (IOException e) { error = e; }
        if (error != null) throw error;
    }
}
