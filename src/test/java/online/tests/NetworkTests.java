package online.tests;

import com.google.gson.*;
import online.net.*;
import online.net.realtime.RealtimeData;
import online.net.duel.DuelRoster;
import online.sync.*;
import online.bundle.Hashes;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

/** Raw v2 peers test protocol barriers, not the convenience client's automatic input generation. */
public final class NetworkTests {
    static final String GAME=String.join("",Collections.nCopies(64,"a"));
    private static final class Peer extends WebSocketClient {
        final BlockingQueue<JsonObject> messages=new LinkedBlockingQueue<>();
        final List<JsonObject> pending=new ArrayList<>();
        volatile int id;volatile long revision;
        Peer(int port)throws Exception{super(new URI("ws://127.0.0.1:"+port));Check.that(connectBlocking(3,TimeUnit.SECONDS),"raw peer connects");}
        public void onOpen(ServerHandshake h){} public void onClose(int c,String r,boolean remote){} public void onError(Exception e){}
        public void onMessage(ByteBuffer b){} public void onMessage(String s){JsonObject o=JsonParser.parseString(s).getAsJsonObject();String type=o.get("type").getAsString();
            if(type.equals("joined"))id=o.get("playerId").getAsInt();
            if(type.equals("room_state")){revision=o.get("revision").getAsLong();if(o.getAsJsonArray("players").size()==2)for(JsonElement value:o.getAsJsonArray("players")){JsonObject p=value.getAsJsonObject();if(p.get("id").getAsInt()==id&&!p.get("lobbyReady").getAsBoolean()){JsonObject r=Protocol.message("lobby_ready");r.addProperty("revision",revision);r.addProperty("ready",true);send(r.toString());}}}
            messages.add(o);}
        JsonObject take(String type)throws Exception{
            for(Iterator<JsonObject> i=pending.iterator();i.hasNext();){JsonObject o=i.next();if(type.equals(o.get("type").getAsString())){i.remove();return o;}}
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(System.nanoTime()<end){JsonObject o=messages.poll(50,TimeUnit.MILLISECONDS);if(o!=null){if(type.equals(o.get("type").getAsString()))return o;pending.add(o);}}
            throw new AssertionError("No "+type+"; pending="+pending);
        }
        ResolvedFrame frame(long tick)throws Exception{
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(System.nanoTime()<end){JsonObject o=take("realtime");for(ResolvedFrame f:RealtimeData.fromControl(o).frames)if(f.tick==tick)return f;}
            throw new AssertionError("Missing tick "+tick);
        }
        void hello(String type,String room,String password,String side){
            JsonObject o=Protocol.message(type);o.addProperty("version",Protocol.VERSION);o.addProperty("engine",Protocol.ENGINE);
            o.addProperty("mode","DUEL_1V1");o.addProperty("udp",false);o.addProperty("game",GAME);o.addProperty("name","Tester");
            o.addProperty("password",password);o.addProperty("room",room);o.addProperty("side",side);send(o.toString());
        }
        void input(long tick,int mask)throws Exception{RealtimeData d=new RealtimeData();d.nextExpected=tick;d.requestTick=tick;d.inputs.put(tick,mask);send(d.control("realtime").toString());}
        void bundle(byte[] bytes)throws Exception{JsonObject o=Protocol.message("bundle");o.addProperty("size",bytes.length);o.addProperty("hash",Hashes.sha256(bytes));send(o.toString());take("bundle_upload");send(bytes);send(Protocol.message("bundle_end").toString());take("bundle_ok");}
        void ready(Map<Integer,String> hashes){JsonObject o=Protocol.message("ready"),all=new JsonObject();for(Map.Entry<Integer,String> e:hashes.entrySet())all.addProperty(e.getKey().toString(),e.getValue());o.add("hashes",all);o.addProperty("revision",revision);send(o.toString());}
    }
    public static void run()throws Exception{
        RoomServer server=new RoomServer(new InetSocketAddress("127.0.0.1",0));server.start();Check.that(server.awaitStarted(5,TimeUnit.SECONDS),"raw relay started");List<Peer> all=new ArrayList<>();
        try{
            Peer host=new Peer(server.getPort());all.add(host);host.hello("create","","test-password","right");JsonObject j=host.take("joined");String room=j.get("room").getAsString();int hostId=j.get("playerId").getAsInt();Check.that(hostId>0,"server issues positive opaque identity");
            Peer wrong=new Peer(server.getPort());all.add(wrong);wrong.hello("join",room,"wrong-password","left");Check.equal("AUTH",wrong.take("error").get("code").getAsString(),"wrong password rejected");
            Peer guest=new Peer(server.getPort());all.add(guest);guest.hello("join",room,"test-password","left");JsonObject g=guest.take("joined");int guestId=g.get("playerId").getAsInt();Check.equal("left",g.get("side").getAsString(),"guest opposite side");Check.that(hostId!=guestId,"IDs unique");DuelRoster roster=DuelRoster.read(host.take("prepare"));guest.take("prepare");
            Peer third=new Peer(server.getPort());all.add(third);third.hello("join",room,"test-password","left");Check.equal("FULL",third.take("error").get("code").getAsString(),"duel third player rejected");
            byte[] a="host-bundle".getBytes(StandardCharsets.UTF_8),b="guest-bundle".getBytes(StandardCharsets.UTF_8);host.bundle(a);guest.bundle(b);host.take("bundle_manifest");guest.take("bundle_manifest");
            Map<Integer,String> hashes=new TreeMap<>();hashes.put(hostId,Hashes.sha256(a));hashes.put(guestId,Hashes.sha256(b));host.ready(hashes);guest.ready(hashes);Check.equal(host.take("start").get("seed"),guest.take("start").get("seed"),"shared seed");
            host.input(0,2);Thread.sleep(120);JsonObject o;boolean advanced=false;while((o=host.messages.poll())!=null){if(o.get("type").getAsString().equals("realtime")&&!RealtimeData.fromControl(o).frames.isEmpty())advanced=true;}Check.that(!advanced,"missing input stops lockstep");
            guest.input(0,1);InputFrame f=roster.toDuel(host.frame(0));guest.frame(0);Check.equal(1,f.left,"left guest input");Check.equal(2,f.right,"right host input");
            for(long t=1;t<60;t++){host.input(t,0);guest.input(t,0);host.frame(t);guest.frame(t);}
            RealtimeData ca=new RealtimeData();ca.nextExpected=60;ca.checkpoints.put(60L,hashes.get(hostId));host.send(ca.control("realtime").toString());RealtimeData cb=new RealtimeData();cb.nextExpected=60;cb.checkpoints.put(60L,hashes.get(guestId));guest.send(cb.control("realtime").toString());
            Check.equal("DESYNC",host.take("error").get("code").getAsString(),"hash disagreement aborts");Check.equal("DESYNC",guest.take("error").get("code").getAsString(),"both informed");Check.equal(0,server.roomCount(),"failed room removed");
        }finally{for(Peer p:all)p.closeBlocking();server.stop(1000);}
    }
}
