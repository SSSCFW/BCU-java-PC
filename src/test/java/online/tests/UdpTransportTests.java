package online.tests;
import online.net.realtime.*;
import online.sync.ResolvedFrame;
import java.util.*;
import java.io.IOException;

public final class UdpTransportTests {
    public static void run() throws Exception {
        ReplayWindow w=new ReplayWindow();
        Check.that(w.accept(100),"first sequence accepted");Check.that(w.accept(102),"ahead accepted");
        Check.that(w.accept(101),"reordered accepted");Check.that(!w.accept(101),"duplicate rejected");
        Check.equal(3,w.ackBits(),"selective ACK bits refer to preceding packets");
        Check.that(w.accept(39),"63 behind accepted");Check.that(!w.accept(38),"64 behind rejected");
        ReplayWindow unsigned=new ReplayWindow();Check.that(unsigned.accept(Long.MAX_VALUE),"signed boundary before");Check.that(unsigned.accept(Long.MIN_VALUE),"unsigned sequence boundary");
        byte[] key=new byte[32];Arrays.fill(key,(byte)7);
        UdpPacketCodec client=new UdpPacketCodec(key,123,false),server=new UdpPacketCodec(key,123,true);
        byte[] packet=client.encode(UdpPacketCodec.DATA,10,0,0,0,new byte[]{1,2,3});
        Check.equal(3,server.decode(packet).body.length,"AEAD round trip");
        byte[] corrupt=packet.clone();corrupt[corrupt.length-1]^=1;
        Check.rejects(()->server.decode(corrupt),"tampered tag rejected");
        Check.rejects(()->client.decode(packet),"wrong direction rejected");
        UdpPacketCodec another=new UdpPacketCodec(key,124,true);
        Check.rejects(()->another.decode(packet),"wrong session rejected");
        Check.that(!Arrays.equals(packet,server.encode(UdpPacketCodec.DATA,10,0,0,0,new byte[]{1,2,3})),"direction keys and nonces separate");
        Check.rejects(()->client.encode(UdpPacketCodec.DATA,11,0,0,0,new byte[1200]),"MTU enforced");
        RealtimeData data=new RealtimeData();data.nextExpected=23;data.requestTick=10;data.checkpointAck=60;
        for(long t=20;t<25;t++)data.inputs.put(t,(int)t);
        Map<Integer,Integer> players=new TreeMap<>();for(int i=1;i<=8;i++)players.put(i,i);
        for(long t=18;t<23;t++)data.frames.add(new ResolvedFrame(t,players));
        data.checkpoints.put(60L,String.join("",Collections.nCopies(64,"a")));
        data.checkpoints.put(120L,String.join("",Collections.nCopies(64,"b")));
        byte[] payload=data.toBytes();Check.that(payload.length+UdpPacketCodec.OVERHEAD<=1200,"eight-player packet within MTU");
        RealtimeData out=RealtimeData.fromBytes(payload);Check.equal(data.inputs,out.inputs,"input serialization");
        Check.equal(8,out.frames.get(0).inputs.size(),"N-player serialization");
        Check.equal(data.checkpoints,out.checkpoints,"checkpoint serialization");
        Check.equal(23L,RealtimeData.fromControl(data.control("realtime")).nextExpected,"WS and UDP have identical payload");
        Check.rejects(()->RealtimeData.fromBytes(Arrays.copyOf(payload,payload.length-1)),"truncated body rejected");
        ReliabilityWindow rel=new ReliabilityWindow();rel.sent(100,1_000_000_000L);rel.sent(101,1_030_000_000L);
        rel.acknowledge(101,1,1_080_000_000L);
        Check.equal(2L,rel.metrics().acknowledgedPackets,"packet SACK accounting");
        Check.that(rel.metrics().smoothedRttMillis>0,"RTT measured");
        rel.acknowledge(101,1,1_200_000_000L);Check.equal(2L,rel.metrics().acknowledgedPackets,"duplicate ACK cannot double-count");
        UdpLink exhausted=new UdpLink(123,key,false,-1L);
        exhausted.encode(UdpPacketCodec.DATA,new RealtimeData(),1);
        Check.rejects(()->exhausted.encode(UdpPacketCodec.DATA,new RealtimeData(),2),"sequence wrap cannot reuse nonce");
        UdpLink receiver=new UdpLink(123,key,true,99);
        Check.that(receiver.receive(packet,10)!=null,"authenticated packet accepted");
        Check.equal(null,receiver.receive(packet,11),"authenticated replay discarded");
        exhausted.close();receiver.close();client.close();server.close();another.close();
    }
}
