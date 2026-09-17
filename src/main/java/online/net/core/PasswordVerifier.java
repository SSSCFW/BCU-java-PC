package online.net.core;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.*;
import java.util.Arrays;

/** Salted, in-memory verifier. Expensive derivation is invoked outside the room-core monitor. */
final class PasswordVerifier implements AutoCloseable {
    private final byte[] salt = new byte[16], hash;
    private final boolean required;
    private volatile boolean closed;
    PasswordVerifier(String password, SecureRandom random) throws GeneralSecurityException {
        required = !password.isEmpty();
        random.nextBytes(salt); hash = required ? derive(password, salt) : new byte[0];
    }
    boolean matches(String password) throws GeneralSecurityException {
        if (!required) return !closed;
        byte[] candidate = derive(password, salt);
        try { return !closed && MessageDigest.isEqual(hash, candidate); }
        finally { Arrays.fill(candidate, (byte) 0); }
    }
    boolean required() { return required; }
    private static byte[] derive(String password, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 210000, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }
    public void close() { closed = true; Arrays.fill(hash, (byte) 0); Arrays.fill(salt, (byte) 0); }
}
