package online.net.realtime;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.io.IOException;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** AEAD codec only. Replay and sequence ownership belong to UdpLink. */
public final class UdpPacketCodec implements AutoCloseable {
    public static final int HELLO=1,HELLO_ACK=2,DATA=3,HEADER=44,OVERHEAD=HEADER+16,MAX_PACKET=1200;
    private static final int MAGIC=0x42435532;
    private final long session;
    private final int sendDirection,receiveDirection;
    private final byte[] sendKey,receiveKey;
    private boolean closed;
    public UdpPacketCodec(byte[] master,long session,boolean server) {
        if(master==null || master.length!=32 || session==0)throw new IllegalArgumentException("Invalid UDP session");
        this.session=session;sendDirection=server?1:0;receiveDirection=1-sendDirection;
        byte[] c2s=derive(master,"BCU-PVP-C2S-v2"),s2c=derive(master,"BCU-PVP-S2C-v2");
        sendKey=server?s2c:c2s;receiveKey=server?c2s:s2c;
    }
    private static byte[] derive(byte[] master,String label) {
        try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(master,"HmacSHA256"));return mac.doFinal(label.getBytes(StandardCharsets.US_ASCII));}
        catch(GeneralSecurityException e){throw new IllegalStateException("Required crypto unavailable",e);}
    }
    private byte[] crypt(int mode,byte[] key,int direction,long sequence,byte[] header,byte[] body)throws IOException {
        if(closed)throw new IOException("UDP keys destroyed");
        try{
            byte[] nonce=ByteBuffer.allocate(12).putInt(direction==0?0x43325332:0x53324332).putLong(sequence).array();
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
            cipher.updateAAD(header);return cipher.doFinal(body);
        }catch(GeneralSecurityException e){throw new IOException("Invalid UDP authentication",e);}
    }
    public byte[] encode(int type,long sequence,long ack,int ackBits,long latestTick,byte[] body)throws IOException {
        if(type<HELLO || type>DATA || sequence==0 || latestTick< -1 || body==null || body.length>MAX_PACKET-OVERHEAD)throw new IOException("Invalid UDP packet fields/MTU");
        byte[] header=ByteBuffer.allocate(HEADER).putInt(MAGIC).put((byte)2).put((byte)sendDirection).put((byte)type).put((byte)0)
            .putLong(session).putLong(sequence).putLong(ack).putInt(ackBits).putLong(latestTick).array();
        byte[] encrypted=crypt(Cipher.ENCRYPT_MODE,sendKey,sendDirection,sequence,header,body);
        return ByteBuffer.allocate(header.length+encrypted.length).put(header).put(encrypted).array();
    }
    public Packet decode(byte[] bytes)throws IOException {
        if(bytes==null || bytes.length<OVERHEAD || bytes.length>MAX_PACKET)throw new IOException("Invalid UDP size");
        ByteBuffer in=ByteBuffer.wrap(bytes);
        if(in.getInt()!=MAGIC || in.get()!=2 || in.get()!=receiveDirection)throw new IOException("Wrong UDP version/direction");
        int type=in.get()&255;
        if(in.get()!=0 || type<HELLO || type>DATA || in.getLong()!=session)throw new IOException("Wrong UDP session/type");
        long seq=in.getLong(),ack=in.getLong();int ackBits=in.getInt();long latestTick=in.getLong();
        if(seq==0 || latestTick< -1)throw new IOException("Invalid UDP sequence/tick");
        byte[] body=crypt(Cipher.DECRYPT_MODE,receiveKey,receiveDirection,seq,Arrays.copyOf(bytes,HEADER),Arrays.copyOfRange(bytes,HEADER,bytes.length));
        return new Packet(type,seq,ack,ackBits,latestTick,body);
    }
    public static long sessionId(byte[] bytes)throws IOException {
        if(bytes.length<OVERHEAD || bytes.length>MAX_PACKET || ByteBuffer.wrap(bytes).getInt()!=MAGIC)throw new IOException("Invalid UDP routing header");
        return ByteBuffer.wrap(bytes).getLong(8);
    }
    public static final class Packet {
        public final int type,ackBits;public final long sequence,ack,latestTick;public final byte[] body;
        Packet(int t,long s,long a,int b,long tick,byte[] data){type=t;sequence=s;ack=a;ackBits=b;latestTick=tick;body=data;}
    }
    @Override public void close(){closed=true;Arrays.fill(sendKey,(byte)0);Arrays.fill(receiveKey,(byte)0);}
}
