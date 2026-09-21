package online.net.realtime;
import com.google.gson.JsonObject;
import online.net.Protocol;
import java.io.IOException;
import java.net.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Authenticated relay endpoint. Socket thread never holds a crypto/session lock during a core callback. */
public final class UdpRealtimeServer implements UdpService {
    private final DatagramSocket socket;
    private final ConcurrentMap<Long,Session> sessions=new ConcurrentHashMap<>();
    private final SecureRandom random=new SecureRandom();
    private final Thread reader;
    private final AtomicBoolean closed=new AtomicBoolean();
    public UdpRealtimeServer(InetSocketAddress address)throws IOException {
        socket=new DatagramSocket(null);
        try{socket.setReuseAddress(false);socket.bind(address);socket.setSoTimeout(250);}
        catch(IOException e){socket.close();throw e;}
        reader=new Thread(this::readLoop,"pvp-udp-server");reader.setDaemon(true);reader.start();
    }
    public int port(){return socket.getLocalPort();}
    public Connection register(Listener listener)throws IOException {
        if(closed.get())throw new IOException("UDP server closed");
        for(;;){long id=random.nextLong();if(id==0)continue;byte[] master=new byte[32];random.nextBytes(master);
            Session s=new Session(id,master,listener);Arrays.fill(master,(byte)0);
            if(sessions.putIfAbsent(id,s)==null){if(closed.get()){s.close();throw new IOException("UDP server closed");}return s;}s.closeKeys();}
    }
    private void readLoop(){
        while(!closed.get()){
            byte[] buffer=new byte[UdpPacketCodec.MAX_PACKET+1];DatagramPacket packet=new DatagramPacket(buffer,buffer.length);
            try{
                socket.receive(packet);if(packet.getLength()>UdpPacketCodec.MAX_PACKET)continue;
                byte[] bytes=Arrays.copyOf(buffer,packet.getLength());Session s=sessions.get(UdpPacketCodec.sessionId(bytes));
                if(s!=null)s.receive(bytes,packet.getSocketAddress());
            }catch(SocketTimeoutException ignored){}
            catch(IOException ignored){if(closed.get())break;}
            catch(RuntimeException e){System.err.println("UDP packet dropped: "+e.getClass().getSimpleName());}
        }
    }
    private final class Session implements Connection {
        final long id;final Listener listener;final UdpLink link;final TrafficPacer pacer=new TrafficPacer();
        final AtomicReference<SocketAddress> endpoint=new AtomicReference<>();
        final AtomicBoolean ended=new AtomicBoolean();
        byte[] master;
        Session(long id,byte[] key,Listener listener){this.id=id;this.master=key.clone();this.listener=listener;link=new UdpLink(id,key,true);}
        public synchronized JsonObject offer(String host){
            if(master==null || ended.get())throw new IllegalStateException("UDP bootstrap already issued");
            JsonObject o=Protocol.message("udp_offer");o.addProperty("session",Long.toUnsignedString(id));
            o.addProperty("master",Base64.getEncoder().encodeToString(master));o.addProperty("port",port());o.addProperty("host",host);
            Arrays.fill(master,(byte)0);master=null;return o;
        }
        void receive(byte[] bytes,SocketAddress source){
            if(ended.get())return;
            SocketAddress bound=endpoint.get();if(bound!=null && !bound.equals(source))return;
            UdpPacketCodec.Packet packet;
            try{packet=link.receive(bytes,System.nanoTime());}catch(IOException invalid){return;}
            if(packet==null)return;
            if(packet.type==UdpPacketCodec.HELLO){
                if(!endpoint.compareAndSet(null,source) && !source.equals(endpoint.get()))return;
                try{transmit(link.encode(UdpPacketCodec.HELLO_ACK,new RealtimeData(),System.nanoTime()));}
                catch(IOException e){listener.failed("UDP hello failed");}return;
            }
            if(packet.type!=UdpPacketCodec.DATA || endpoint.get()==null)return;
            try{RealtimeData data=RealtimeData.fromBytes(packet.body);listener.data(data);}
            catch(IOException invalid){listener.failed("Authenticated UDP payload was malformed");}
        }
        private void transmit(byte[] bytes)throws IOException {SocketAddress to=endpoint.get();if(!ended.get() && to!=null)socket.send(new DatagramPacket(bytes,bytes.length,to));}
        public void send(RealtimeData data,long now,boolean active)throws IOException {
            if(ended.get() || endpoint.get()==null)return;
            if(pacer.permit(now,active,link.metrics(),now-link.lastReceived()))transmit(link.encode(UdpPacketCodec.DATA,data,now));
        }
        public boolean isBound(){return !ended.get() && endpoint.get()!=null;}
        public long lastReceived(){return link.lastReceived();}
        public ReliabilityWindow.Metrics metrics(){return link.metrics();}
        private synchronized void closeKeys(){if(master!=null){Arrays.fill(master,(byte)0);master=null;}link.close();}
        public void close(){if(ended.compareAndSet(false,true)){sessions.remove(id,this);closeKeys();endpoint.set(null);}}
    }
    public int sessionCount(){return sessions.size();}
    @Override public void close(){
        if(!closed.compareAndSet(false,true))return;
        socket.close();for(Session s:sessions.values())s.close();sessions.clear();
        if(Thread.currentThread()!=reader)try{reader.join(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
}
