package online.tests;

import com.google.gson.*;
import online.net.*;
import online.net.core.*;
import online.net.config.ServerConfig;
import online.sync.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Real sockets: all transport combinations, loss recovery, opaque N-player inputs and cleanup. */
public final class HybridNetworkTests {
    static final String HASH=String.join("",Collections.nCopies(64,"a"));
    static final class Peer implements RoomClient.Listener,AutoCloseable{
        final RoomClient client;final Path archive;final BlockingQueue<JsonObject> events=new LinkedBlockingQueue<>();
        volatile String failure;volatile int bundleCount;volatile long firstTickNanos;
        final List<ResolvedFrame> received=new ArrayList<>();
        Peer(int port,boolean udp,UdpLossProxy proxy,Path archive)throws Exception{
            this.archive=archive;client=new RoomClient(new URI("ws://127.0.0.1:"+port),false,udp,this){
                protected InetSocketAddress udpEndpoint(String host,int offered)throws IOException{
                    InetSocketAddress actual=super.udpEndpoint(host,offered);return proxy==null?actual:proxy.route(actual);
                }
            };
            Check.that(client.connectBlocking(5,TimeUnit.SECONDS),"hybrid peer connects");
        }
        public void event(JsonObject o){events.offer(o);if(o.get("type").getAsString().equals("prepare")){client.sendBundle(archive);client.ready();}}
        public void bundle(int id,Path path,String hash){try{if(!Arrays.equals(Files.readAllBytes(archive),Files.readAllBytes(path)))throw new IOException("Wrong bundle bytes");Files.delete(path);bundleCount++;}catch(IOException e){failure=e.toString();}}
        public void failed(String reason){failure=reason;}
        JsonObject await(String type)throws Exception{
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);
            while(System.nanoTime()<end){healthy();JsonObject e=events.poll(20,TimeUnit.MILLISECONDS);if(e!=null&&e.get("type").getAsString().equals(type))return e;}
            throw new AssertionError("No "+type+" response; transport="+client.realtimeTransport());
        }
        void healthy(){if(failure!=null)throw new AssertionError("Peer error: "+failure);}
        void drain(){ResolvedFrame frame;while((frame=client.pollResolvedFrame())!=null){if(firstTickNanos==0)firstTickNanos=System.nanoTime();received.add(frame);if((frame.tick+1)%60==0)client.checkpoint(frame.tick+1,HASH);if(frame.tick%7==0)client.queueCommand(1);}}
        public void close(){client.close();}
    }
    static RoomServer server(int delay,Map<String,GameMode> modes)throws Exception{
        Properties props=new Properties();props.setProperty("bind","127.0.0.1");props.setProperty("controlPort","0");props.setProperty("udpPort","0");props.setProperty("inputDelayTicks",""+delay);
        RoomServer server=modes==null?new RoomServer(ServerConfig.from(props)):new RoomServer(ServerConfig.from(props),modes);server.start();Check.that(server.awaitStarted(5,TimeUnit.SECONDS),"hybrid server starts");return server;
    }
    static Path archive()throws Exception{Path p=Files.createTempFile("pvp-network-fixture-",".zip");byte[] bytes=new byte[180000];new Random(42).nextBytes(bytes);Files.write(p,bytes);return p;}
    static void enter(List<Peer> peers,String mode)throws Exception{
        Peer host=peers.get(0);host.client.enter(true,"","hybrid-password","Host","left",HASH,mode);String room=host.await("joined").get("room").getAsString();
        for(int i=1;i<peers.size();i++){Peer p=peers.get(i);p.client.enter(false,room,"hybrid-password","Guest "+i,"right",HASH,mode);p.await("joined");}
        for(Peer p:peers)p.await("start");
    }
    static void advance(List<Peer> peers,int ticks)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
        while(true){boolean done=true;for(Peer p:peers){p.healthy();p.drain();if(p.received.size()<ticks)done=false;}if(done)break;if(System.nanoTime()>deadline)throw new AssertionError("Hybrid frames timed out "+peers.get(0).received.size()+"/"+ticks);Thread.sleep(3);}
        for(int tick=0;tick<ticks;tick++)for(Peer p:peers){ResolvedFrame f=p.received.get(tick);Check.equal((long)tick,f.tick,"ordered frame under loss");Check.equal(peers.get(0).received.get(tick).inputs,f.inputs,"all participants agree on inputs");}
    }
    static void pair(boolean aUdp,boolean bUdp,boolean impair,int delay)throws Exception{
        RoomServer server=server(delay,null);Path archive=archive();List<Peer> peers=new ArrayList<>();UdpLossProxy proxy=new UdpLossProxy();proxy.impair=impair;
        try{
            peers.add(new Peer(server.getPort(),aUdp,proxy,archive));peers.add(new Peer(server.getPort(),bUdp,null,archive));enter(peers,"DUEL_1V1");
            Check.equal(aUdp?"UDP":"WS",peers.get(0).client.realtimeTransport(),"first transport negotiated");Check.equal(bUdp?"UDP":"WS",peers.get(1).client.realtimeTransport(),"second transport negotiated");
            advance(peers,70);Check.equal(1L,server.core().completedUploads(),"identical bundle uploaded once in room");
            for(Peer p:peers)Check.equal(1,p.bundleCount,"one private bundle copy for other identity");
            if(impair){Check.that(proxy.dropped.get()>0&&proxy.duplicated.get()>0&&proxy.reordered.get()>0,"fault injector exercised loss/duplicates/reorder");Check.that(peers.get(0).client.udpMetrics().acknowledgedPackets>0,"UDP packet acknowledgments tracked");}
            if(delay==8)Check.that(peers.get(0).client.retransmitRequests()>0,"old missing startup inputs explicitly requested beyond recent four");
            System.out.println("Hybrid "+aUdp+"/"+bUdp+" loss="+impair+" delay="+delay+": 70 ticks consistent");
        }finally{for(Peer p:peers)p.close();proxy.close();server.stop(1000);Files.deleteIfExists(archive);}
    }
    static void fallback()throws Exception{
        RoomServer server=server(3,null);Path archive=archive();UdpLossProxy proxy=new UdpLossProxy();proxy.blocked=true;List<Peer> peers=new ArrayList<>();
        try{peers.add(new Peer(server.getPort(),true,proxy,archive));peers.add(new Peer(server.getPort(),true,null,archive));long start=System.nanoTime();enter(peers,"DUEL_1V1");Check.equal("WS",peers.get(0).client.realtimeTransport(),"blocked UDP automatically falls back before start");Check.that(System.nanoTime()-start>=1_400_000_000L,"fallback did not skip probe deadline");Check.equal(4,proxy.dropped.get(),"at most four UDP hello probes");advance(peers,10);}
        finally{for(Peer p:peers)p.close();proxy.close();server.stop(1000);Files.deleteIfExists(archive);}
    }
    static void rescueAndTimeout()throws Exception{
        RoomServer server=server(3,null);Path archive=archive();UdpLossProxy proxy=new UdpLossProxy();List<Peer> peers=new ArrayList<>();
        try{
            peers.add(new Peer(server.getPort(),true,proxy,archive));peers.add(new Peer(server.getPort(),true,null,archive));enter(peers,"DUEL_1V1");advance(peers,10);
            proxy.blockUntil=System.nanoTime()+650_000_000L;advance(peers,45);
            Check.that(server.core().rescueCount()+peers.get(0).client.rescueCount()>0,"250ms stall uses bounded reliable rescue");Check.equal("UDP",peers.get(0).client.realtimeTransport(),"emergency recovery does not migrate active transport");
            proxy.blocked=true;long deadline=System.nanoTime()+5_000_000_000L;
            while(peers.get(0).failure==null&&peers.get(1).failure==null&&System.nanoTime()<deadline){for(Peer p:peers)p.drain();Thread.sleep(10);}
            Check.that((peers.get(0).failure!=null&&peers.get(0).failure.contains("UDP_TIMEOUT"))||(peers.get(1).failure!=null&&peers.get(1).failure.contains("UDP_TIMEOUT")),"3s authenticated UDP silence aborts instead of silent migration");
            long stop=System.nanoTime()+1_000_000_000L;while(server.roomCount()!=0&&System.nanoTime()<stop)Thread.sleep(10);Check.equal(0,server.roomCount(),"timed out room cleaned");
        }finally{for(Peer p:peers)p.close();proxy.close();server.stop(1000);Files.deleteIfExists(archive);}
    }
    static void eightParticipants()throws Exception{
        GameMode mode=new GameMode(){public String id(){return "TEST_8";}public int minPlayers(){return 8;}public int maxPlayers(){return 8;}public Seat assign(Collection<Seat> occupied,String request)throws IOException{if(occupied.size()>=8)throw new IOException("Full");return new Seat("seat"+occupied.size(),occupied.size()%2);}public boolean validWinner(int team){return team>=-1&&team<2;}};
        RoomServer server=server(3,Collections.singletonMap(mode.id(),mode));Path archive=archive();List<Peer> peers=new ArrayList<>();
        try{for(int i=0;i<8;i++)peers.add(new Peer(server.getPort(),i%2==0,null,archive));enter(peers,mode.id());advance(peers,65);Set<Integer> ids=new HashSet<>();for(Peer p:peers){ids.add(p.client.playerId());Check.equal(7,p.bundleCount,"eight-player cache distributes seven isolated copies");}Check.equal(8,ids.size(),"eight opaque identities");Check.equal(1L,server.core().completedUploads(),"eight-player bundle hash dedup");}
        finally{for(Peer p:peers)p.close();server.stop(1000);Files.deleteIfExists(archive);}
    }
    static void concurrentRooms()throws Exception{
        RoomServer server=server(3,null);Path archive=archive();List<Peer> a=new ArrayList<>(),b=new ArrayList<>();
        try{
            for(int i=0;i<2;i++){a.add(new Peer(server.getPort(),true,null,archive));b.add(new Peer(server.getPort(),false,null,archive));}
            enter(a,"DUEL_1V1");enter(b,"DUEL_1V1");Check.equal(2,server.roomCount(),"two simultaneous rooms admitted");
            advance(a,10);advance(b,10);Check.equal(2L,server.core().completedUploads(),"bundle cache scope is per room, never global");
            Set<Integer> ids=new HashSet<>();for(Peer peer:a)ids.add(peer.client.playerId());for(Peer peer:b)Check.that(!ids.contains(peer.client.playerId()),"other room cannot share participant identity");
            for(Peer peer:a)peer.close();long deadline=System.nanoTime()+2_000_000_000L;while(server.roomCount()!=1&&System.nanoTime()<deadline)Thread.sleep(10);
            Check.equal(1,server.roomCount(),"closing one room does not close another");advance(b,25);
        }finally{for(Peer p:a)p.close();for(Peer p:b)p.close();server.stop(1000);Files.deleteIfExists(archive);}
    }
    public static void run()throws Exception{pair(true,true,true,3);pair(true,false,false,3);pair(false,true,false,3);pair(false,false,false,3);pair(true,true,true,8);fallback();rescueAndTimeout();eightParticipants();concurrentRooms();}
    public static void main(String[] args)throws Exception{run();System.out.println("Hybrid assertions: "+Check.count);}
}
