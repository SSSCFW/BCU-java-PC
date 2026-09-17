package online.net.realtime;
import java.io.IOException;
import java.net.*;
import java.util.Arrays;

/** Per-participant UDP path: bounded probes, pinned server, paced redundancy, no silent migration. */
public final class UdpRealtimeClient implements RealtimeTransport {
    public interface Listener { void available(); void fallback(); void data(RealtimeData data); void failed(String reason); }
    private final DatagramSocket socket;
    private final UdpLink link;
    private final Listener listener;
    private final TrafficPacer pacer=new TrafficPacer();
    private final long created=System.nanoTime();
    private final Thread reader;
    private volatile boolean closed,available,fallback;
    private int probes;
    private long nextProbe=created;
    public UdpRealtimeClient(InetSocketAddress address,long session,byte[] master,Listener listener)throws IOException {
        if(address.isUnresolved())throw new IOException("Unresolved UDP server");
        this.listener=listener;socket=new DatagramSocket();link=new UdpLink(session,master,false);
        try{socket.connect(address);socket.setSoTimeout(250);}catch(SocketException e){socket.close();link.close();throw e;}
        reader=new Thread(this::readLoop,"pvp-udp-client");reader.setDaemon(true);reader.start();
    }
    private void readLoop(){
        while(!closed){
            byte[] buffer=new byte[UdpPacketCodec.MAX_PACKET+1];DatagramPacket packet=new DatagramPacket(buffer,buffer.length);
            try{
                socket.receive(packet);if(packet.getLength()>UdpPacketCodec.MAX_PACKET)continue;
                UdpPacketCodec.Packet decoded;
                try{decoded=link.receive(Arrays.copyOf(buffer,packet.getLength()),System.nanoTime());}catch(IOException invalid){continue;}
                if(decoded==null)continue;
                if(decoded.type==UdpPacketCodec.HELLO_ACK){
                    boolean notify=false;synchronized(this){if(!available && !fallback && !closed){available=true;notify=true;}}
                    if(notify)listener.available();
                }else if(decoded.type==UdpPacketCodec.DATA && available){
                    try{RealtimeData data=RealtimeData.fromBytes(decoded.body);listener.data(data);}
                    catch(IOException invalid){listener.failed("Malformed authenticated UDP response");}
                }
            }catch(SocketTimeoutException ignored){}
            catch(PortUnreachableException ignored){} // bounded probe timeout chooses WS; active timeout aborts
            catch(IOException e){if(!closed && available)listener.failed("UDP receive failed");}
        }
    }
    public void pump(RealtimeData data,long now,boolean active)throws IOException {
        boolean notifyFallback=false;
        synchronized(this){
            if(closed || fallback)return;
            if(!available){
                if(now-created>=1_500_000_000L){fallback=true;notifyFallback=true;}
                else if(probes<4 && now>=nextProbe){
                    transmit(link.encode(UdpPacketCodec.HELLO,new RealtimeData(),now));probes++;nextProbe=created+probes*250_000_000L;
                }
            }else if(pacer.permit(now,active,link.metrics(),now-link.lastReceived()))transmit(link.encode(UdpPacketCodec.DATA,data,now));
        }
        if(notifyFallback)listener.fallback();
    }
    private void transmit(byte[] bytes)throws IOException{
        if(!closed)try{socket.send(new DatagramPacket(bytes,bytes.length));}catch(PortUnreachableException ignored){}
    }
    public void send(RealtimeData data,long now,boolean active)throws IOException{pump(data,now,active);}
    public long lastReceived(){return link.lastReceived();}
    public ReliabilityWindow.Metrics metrics(){return link.metrics();}
    @Override public synchronized void close(){if(closed)return;closed=true;socket.close();link.close();}
}
