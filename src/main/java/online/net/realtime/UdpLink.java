package online.net.realtime;
import java.io.IOException;
import java.security.SecureRandom;

/** Thread-safe directional sequence/nonce ownership. Never calls application code under its lock. */
public final class UdpLink implements AutoCloseable {
    private final UdpPacketCodec codec;
    private final ReplayWindow receive=new ReplayWindow();
    private final ReliabilityWindow reliability=new ReliabilityWindow();
    private long sequence,lastReceived;
    private boolean exhausted,closed;
    public UdpLink(long session,byte[] master,boolean server){this(session,master,server,fresh());}
    public UdpLink(long session,byte[] master,boolean server,long initial){
        if(initial==0)throw new IllegalArgumentException("Zero sequence reserved");sequence=initial;codec=new UdpPacketCodec(master,session,server);
    }
    private static long fresh(){long n;SecureRandom r=new SecureRandom();do{n=r.nextLong();}while(n==0);return n;}
    public synchronized byte[] encode(int type,RealtimeData data,long now)throws IOException {
        if(exhausted || closed)throw new IOException("UDP sequence exhausted or closed; new session required");
        byte[] bytes=codec.encode(type,sequence,receive.highest(),receive.ackBits(),data.nextExpected,data.toBytes());
        reliability.sent(sequence,now);if(sequence== -1L)exhausted=true;else sequence++;return bytes;
    }
    public synchronized UdpPacketCodec.Packet receive(byte[] bytes,long now)throws IOException {
        if(closed)return null;
        UdpPacketCodec.Packet packet=codec.decode(bytes);
        if(!receive.accept(packet.sequence))return null;
        reliability.acknowledge(packet.ack,packet.ackBits,now);lastReceived=now;return packet;
    }
    public synchronized long lastReceived(){return lastReceived;}
    public synchronized ReliabilityWindow.Metrics metrics(){return reliability.metrics();}
    @Override public synchronized void close(){closed=true;codec.close();}
}
