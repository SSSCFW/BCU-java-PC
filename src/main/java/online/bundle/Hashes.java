package online.bundle;

import java.io.*;
import java.nio.file.*;
import java.security.*;

public final class Hashes {
    private Hashes() {}
    public static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static String hex(byte[] data) {
        StringBuilder out=new StringBuilder(data.length*2);
        for(byte b:data) out.append(Character.forDigit((b>>>4)&15,16)).append(Character.forDigit(b&15,16));
        return out.toString();
    }
    public static String sha256(byte[] data) { return hex(digest().digest(data)); }
    public static String sha256(Path path) throws IOException {
        MessageDigest md=digest();
        try(InputStream in=Files.newInputStream(path)) { byte[] b=new byte[65536]; int n; while((n=in.read(b))!=-1) md.update(b,0,n); }
        return hex(md.digest());
    }
    public static boolean valid(String value) { return value!=null && value.matches("[0-9a-f]{64}"); }
}
