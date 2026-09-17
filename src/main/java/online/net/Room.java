package online.net;

import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.java_websocket.WebSocket;

/** All access is serialized by RoomServer's monitor. */
final class Room {
    final String id,match,game;
    final int leftSlot;
    final byte[] salt,passwordHash;
    final WebSocket[] peers=new WebSocket[2];
    final String[] names=new String[2],bundleHashes=new String[2];
    final MessageDigest[] uploadDigest=new MessageDigest[2];
    final long[] uploadSize=new long[2],received=new long[2],nextInput=new long[2],hashTick=new long[2];
    final boolean[] uploaded=new boolean[2],ready=new boolean[2];
    final Map<Long,String[]> checkpoints=new TreeMap<>();
    final Map<Long,int[]> frames=new TreeMap<>();
    final long created=System.nanoTime();
    long progressed=created,nextFrameAt,seed,tick;
    boolean started,closed;
    final String[] result=new String[2];
    Room(String id,String match,String game,String password,int leftSlot,SecureRandom random) throws GeneralSecurityException {
        this.id=id; this.match=match; this.game=game; this.leftSlot=leftSlot;
        salt=new byte[16]; random.nextBytes(salt); passwordHash=derive(password,salt);
    }
    boolean authenticate(String password) throws GeneralSecurityException { return MessageDigest.isEqual(passwordHash,derive(password,salt)); }
    private static byte[] derive(String password,byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt,210000,256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }
    int slot(WebSocket conn) { return peers[0]==conn ? 0 : peers[1]==conn ? 1 : -1; }
}
