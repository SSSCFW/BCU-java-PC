package online.tests;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public final class NetworkTests {
    private static final String GAME = String.join("", java.util.Collections.nCopies(64, "a"));
    private static final class Peer extends WebSocketClient {
        final BlockingQueue<JsonObject> messages = new LinkedBlockingQueue<>();
        Peer(int port) throws Exception { super(new URI("ws://127.0.0.1:"+port)); Check.that(connectBlocking(3,TimeUnit.SECONDS),"connect"); }
        public void onOpen(ServerHandshake h) {}
        public void onMessage(String s) { messages.add(JsonParser.parseString(s).getAsJsonObject()); }
        public void onMessage(ByteBuffer b) {}
        public void onClose(int c,String r,boolean remote) {}
        public void onError(Exception e) {}
        final java.util.List<JsonObject> pending=new java.util.ArrayList<>();
        JsonObject take(String type) throws Exception {
            for(java.util.Iterator<JsonObject> i=pending.iterator();i.hasNext();) {
                JsonObject o=i.next();
                if(type.equals(o.get("type").getAsString())) { i.remove(); return o; }
            }
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(System.nanoTime()<end) {
                JsonObject o=messages.poll(100,TimeUnit.MILLISECONDS);
                if(o!=null) {
                    if(type.equals(o.get("type").getAsString())) return o;
                    pending.add(o);
                }
            }
            throw new AssertionError("No "+type+" response");
        }
        void hello(String type,String room,String pass,String side) {
            JsonObject o=online.net.Protocol.message(type);
            o.addProperty("version", online.net.Protocol.VERSION);
            o.addProperty("engine", online.net.Protocol.ENGINE);
            o.addProperty("game",GAME); o.addProperty("name","Tester");
            o.addProperty("password",pass); o.addProperty("room",room); o.addProperty("side",side);
            send(o.toString());
        }
        void input(long tick,int mask) { JsonObject o=online.net.Protocol.message("input"); o.addProperty("tick",tick); o.addProperty("mask",mask); send(o.toString()); }
        void bundle(byte[] bytes) throws Exception {
            JsonObject o=online.net.Protocol.message("bundle"); o.addProperty("size",bytes.length);
            o.addProperty("hash",online.bundle.Hashes.sha256(bytes)); send(o.toString()); send(bytes);
            send(online.net.Protocol.message("bundle_end").toString());
        }
        void ready(String a,String b) { JsonObject o=online.net.Protocol.message("ready"); o.addProperty("hostHash",a); o.addProperty("guestHash",b); send(o.toString()); }
    }
    public static void run() throws Exception {
        online.net.RoomServer server=new online.net.RoomServer(new InetSocketAddress("127.0.0.1",0));
        server.start(); Check.that(server.awaitStarted(5,TimeUnit.SECONDS),"server started");
        java.util.List<Peer> all=new java.util.ArrayList<>();
        try {
            Peer host=new Peer(server.getPort()); all.add(host);
            host.hello("create","","test-password","right");
            JsonObject joined=host.take("joined"); String room=joined.get("room").getAsString();
            Check.equal(0,joined.get("slot").getAsInt(),"host identity");
            Peer wrong=new Peer(server.getPort()); all.add(wrong);
            wrong.hello("join",room,"wrong-password","left");
            Check.equal("AUTH",wrong.take("error").get("code").getAsString(),"wrong password rejected");
            Peer guest=new Peer(server.getPort()); all.add(guest);
            guest.hello("join",room,"test-password","left");
            Check.equal("left",guest.take("joined").get("side").getAsString(),"guest receives opposite side");
            host.take("prepare"); guest.take("prepare");
            Peer third=new Peer(server.getPort()); all.add(third);
            third.hello("join",room,"test-password","left");
            Check.equal("FULL",third.take("error").get("code").getAsString(),"third player rejected");
            byte[] a="host-bundle".getBytes(StandardCharsets.UTF_8), b="guest-bundle".getBytes(StandardCharsets.UTF_8);
            host.bundle(a); guest.bundle(b);
            host.take("bundle_ok"); guest.take("bundle_ok");
            host.take("bundle_end"); guest.take("bundle_end");
            String ah=online.bundle.Hashes.sha256(a), bh=online.bundle.Hashes.sha256(b);
            host.ready(ah,bh); guest.ready(ah,bh);
            JsonObject startA=host.take("start"),startB=guest.take("start");
            Check.equal(startA.get("seed"),startB.get("seed"),"same simulation seed");
            host.input(0,2); // guest input not present: no simulation frame is allowed.
            Check.that(host.messages.poll(100,TimeUnit.MILLISECONDS)==null,"missing input stalls tick");
            guest.input(0,1);
            JsonObject f=host.take("frame"); guest.take("frame");
            Check.equal(0L,f.get("tick").getAsLong(),"first tick");
            Check.equal(1,f.get("left").getAsInt(),"physical left input belongs to guest");
            Check.equal(2,f.get("right").getAsInt(),"physical right input belongs to host");
            for(long t=1;t<60;t++) { host.input(t,0); guest.input(t,0); host.take("frame"); guest.take("frame"); }
            JsonObject ha=online.net.Protocol.message("hash"); ha.addProperty("tick",60); ha.addProperty("hash",ah); host.send(ha.toString());
            ha.addProperty("hash",bh); guest.send(ha.toString());
            Check.equal("DESYNC",host.take("error").get("code").getAsString(),"state mismatch aborts");
            Check.equal("DESYNC",guest.take("error").get("code").getAsString(),"both peers informed");
            Check.that(server.roomCount()==0,"failed room cleaned up");
        } finally { for(Peer p:all) p.closeBlocking(); server.stop(1000); }
    }
}
