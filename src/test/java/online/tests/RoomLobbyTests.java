package online.tests;

import com.google.gson.*;
import online.net.*;
import online.bundle.Hashes;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

/** Actual control sockets; no client-side authority assumptions. */
public final class RoomLobbyTests {
    private static final class Peer extends WebSocketClient implements AutoCloseable {
        final BlockingQueue<JsonObject> events = new LinkedBlockingQueue<>();
        final List<JsonObject> pending = new ArrayList<>();
        Peer(int port) throws Exception { super(new URI("ws://127.0.0.1:"+port));Check.that(connectBlocking(3,TimeUnit.SECONDS),"lobby peer connects"); }
        public void onOpen(ServerHandshake h){} public void onClose(int c,String r,boolean remote){} public void onError(Exception e){}
        public void onMessage(String text){events.add(JsonParser.parseString(text).getAsJsonObject());}
        JsonObject take(String type) throws Exception {
            for(Iterator<JsonObject> i=pending.iterator();i.hasNext();){JsonObject o=i.next();if(type.equals(o.get("type").getAsString())){i.remove();return o;}}
            long until=System.nanoTime()+3_000_000_000L;
            while(System.nanoTime()<until){JsonObject o=events.poll(20,TimeUnit.MILLISECONDS);if(o==null)continue;if(type.equals(o.get("type").getAsString()))return o;pending.add(o);}
            throw new AssertionError("No "+type+": "+pending);
        }
        JsonObject state(int count) throws Exception { JsonObject s;do{s=take("room_state");}while(s.getAsJsonArray("players").size()!=count);return s; }
        void hello(boolean create,String room){JsonObject o=Protocol.message(create?"create":"join");o.addProperty("version",Protocol.VERSION);o.addProperty("engine",Protocol.ENGINE);o.addProperty("name",create?"Host":"Guest");o.addProperty("password","");o.addProperty("game",Hashes.sha256(new byte[]{1}));o.addProperty("room",room);o.addProperty("side","right");o.addProperty("udp",false);send(o.toString());}
        void rules(long revision,int distance){JsonObject o=Protocol.message("rules");o.addProperty("revision",revision);JsonObject r=new JsonObject();r.addProperty("castleDistance",distance);r.addProperty("backgroundId",4);r.addProperty("musicId",7);r.addProperty("force60Fps",true);o.add("rules",r);send(o.toString());}
        void ready(long revision,boolean value){JsonObject o=Protocol.message("lobby_ready");o.addProperty("revision",revision);o.addProperty("ready",value);send(o.toString());}
        void lineup(String name){JsonObject o=Protocol.message("lineup");o.addProperty("name",name);send(o.toString());}
    }
    public static void run() throws Exception {
        RoomServer server=new RoomServer(new InetSocketAddress("127.0.0.1",0));server.start();Check.that(server.awaitStarted(3,TimeUnit.SECONDS),"lobby server starts");
        try(Peer host=new Peer(server.getPort());Peer guest=new Peer(server.getPort())){
            host.hello(true,"");JsonObject joined=host.take("joined");host.state(1);
            guest.hello(false,joined.get("room").getAsString());guest.take("joined");
            Thread.sleep(120);
            Check.that(guest.events.stream().noneMatch(e->"prepare".equals(e.get("type").getAsString())),"joining must remain in editable lobby, not immediately export/freeze characters");
            JsonObject a=host.state(2),b=guest.state(2);long rev=a.get("revision").getAsLong();
            Check.equal("EDITING",a.get("phase").getAsString(),"both peers edit before ready");
            Check.equal(joined.get("playerId"),a.get("hostId"),"host identity assigned by server");
            guest.rules(rev,8000);Check.equal("HOST_ONLY",guest.take("notice").get("code").getAsString(),"guest cannot change host rules");
            host.rules(rev,0);Check.equal("RULES",host.take("notice").get("code").getAsString(),"invalid arena rejected without destroying room");
            host.rules(rev,8000);a=host.state(2);b=guest.state(2);long changed=a.get("revision").getAsLong();
            Check.that(changed>rev,"rules increment revision");Check.equal(a.get("rules"),b.get("rules"),"identical rules broadcast");
            Check.equal(8000,a.getAsJsonObject("rules").get("castleDistance").getAsInt(),"host rules accepted");
            host.ready(rev,true);Check.equal("STALE",host.take("notice").get("code").getAsString(),"stale readiness rejected nonfatally");host.state(2);
            host.ready(changed,true);a=host.state(2);guest.state(2);Check.that(a.getAsJsonArray("players").get(0).getAsJsonObject().get("lobbyReady").getAsBoolean(),"host readiness displayed");
            guest.lineup("Changed lineup");a=host.state(2);b=guest.state(2);long lineRev=a.get("revision").getAsLong();Check.that(lineRev>changed,"lineup edits increment revision");
            for(JsonElement p:a.getAsJsonArray("players"))Check.that(!p.getAsJsonObject().get("lobbyReady").getAsBoolean(),"lineup changes clear ALL readiness");
            host.ready(lineRev,true);host.state(2);guest.state(2);guest.ready(lineRev,true);
            JsonObject prepare=host.take("prepare");guest.take("prepare");Check.equal(lineRev,prepare.get("revision").getAsLong(),"prepare freezes current revision");Check.equal(a.get("rules"),prepare.get("rules"),"prepare freezes identical rules");
            host.rules(lineRev,9000);Check.equal("LOCKED",host.take("notice").get("code").getAsString(),"cannot change a bundle transfer in progress");
            Check.equal(1,server.roomCount(),"rejected lobby commands did not destroy room");
        }finally{server.stop(1000);}
    }
    public static void main(String[] args)throws Exception{run();System.out.println("Room lobby protocol tests passed");}
}
