package online.net;

import com.google.gson.JsonObject;
import online.bundle.Hashes;
import online.sync.InputFrame;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import org.java_websocket.WebSocket;
import org.java_websocket.WebSocketImpl;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

/** Passworded, two-player, bounded lockstep relay. Combat itself runs in clients. */
public final class RoomServer extends WebSocketServer {
    private final Map<String,Room> rooms=new HashMap<>();
    private final Map<WebSocket,Room> membership=new HashMap<>();
    private final Map<WebSocket,Long> opened=new HashMap<>();
    private final Map<String,long[]> authAttempts=new HashMap<>();
    // Never acquire a socket monitor while holding the room monitor.
    private final Queue<WebSocket> closing=new ConcurrentLinkedQueue<>();
    private final SecureRandom random=new SecureRandom();
    private final CountDownLatch started=new CountDownLatch(1);
    private final ScheduledExecutorService pulse=Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t=new Thread(r,"pvp-room-clock"); t.setDaemon(true); return t;
    });
    private static final long SECOND=1_000_000_000L;

    public RoomServer(InetSocketAddress address) {
        super(address,2,Collections.singletonList(Protocol.draft()));
        setConnectionLostTimeout(20);
        setReuseAddr(true);
    }
    public boolean awaitStarted(long time,TimeUnit unit) throws InterruptedException { return started.await(time,unit); }
    public synchronized int roomCount() { return rooms.size(); }
    @Override public void onStart() {
        started.countDown();
        pulse.scheduleWithFixedDelay(this::pump,0,5,TimeUnit.MILLISECONDS);
    }
    @Override public synchronized void onOpen(WebSocket conn,ClientHandshake h) {
        if(opened.size()>=128) { error(conn,"BUSY","Server connection limit"); deferClose(conn); return; }
        opened.put(conn,System.nanoTime());
    }
    @Override public synchronized void onMessage(WebSocket conn,String text) {
        try {
            JsonObject o=Protocol.parse(text); String type=Protocol.string(o,"type",32);
            if(type.equals("create") || type.equals("join")) { enter(conn,o,type.equals("create")); return; }
            Room room=membership.get(conn);
            if(room==null || room.closed) throw new IOException("Not in a room");
            int slot=room.slot(conn);
            switch(type) {
                case "bundle": beginBundle(room,slot,o); break;
                case "bundle_end": endBundle(room,slot); break;
                case "ready": ready(room,slot,o); break;
                case "input": input(room,slot,o); break;
                case "hash": hash(room,slot,o); break;
                case "result": result(room,slot,o); break;
                case "leave": closeRoom(room,"CLOSED","Player left the room"); break;
                default: throw new IOException("Unknown request");
            }
        } catch(Exception e) {
            Room room=membership.get(conn);
            if(room!=null) closeRoom(room,"PROTOCOL",safe(e));
            else { error(conn,"PROTOCOL",safe(e)); deferClose(conn); }
        }
    }
    private static String safe(Exception e) { return e instanceof IOException ? e.getMessage() : "Invalid request"; }
    private void enter(WebSocket conn,JsonObject o,boolean create) throws Exception {
        if(membership.containsKey(conn)) throw new IOException("Already in a room");
        if(!opened.containsKey(conn)) throw new IOException("Connection not admitted");
        if(Protocol.integer(o,"version")!=Protocol.VERSION || !Protocol.ENGINE.equals(Protocol.string(o,"engine",128))) {
            error(conn,"VERSION","PvP versions differ"); return;
        }
        String ip=conn.getRemoteSocketAddress().getAddress().getHostAddress();
        long now=System.nanoTime(); long[] attempts=authAttempts.get(ip);
        if(attempts==null || now-attempts[0]>60*SECOND) { attempts=new long[]{now,0}; authAttempts.put(ip,attempts); }
        if(++attempts[1]>12) { error(conn,"RATE","Too many room/password attempts; wait one minute"); return; }
        String name=Protocol.string(o,"name",40).trim(),password=Protocol.string(o,"password",128),game=Protocol.string(o,"game",64);
        if(name.isEmpty() || password.length()<8 || !Hashes.valid(game)) throw new IOException("Name, 8+ character password and game fingerprint required");
        Room room; int slot;
        if(create) {
            if(rooms.size()>=32) { error(conn,"BUSY","Room limit reached"); return; }
            String side=Protocol.string(o,"side",5);
            if(!side.equals("left") && !side.equals("right")) throw new IOException("Invalid side");
            String id;
            do { byte[] b=new byte[9]; random.nextBytes(b); id=Hashes.hex(b).toUpperCase(Locale.ROOT); } while(rooms.containsKey(id));
            room=new Room(id,UUID.randomUUID().toString().replace("-",""),game,password,side.equals("left")?0:1,random);
            rooms.put(id,room); slot=0;
        } else {
            String id=Protocol.string(o,"room",32).trim().toUpperCase(Locale.ROOT);
            room=rooms.get(id);
            if(room==null || !room.authenticate(password)) { error(conn,"AUTH","Room or password is incorrect"); return; }
            if(room.peers[1]!=null || room.started) { error(conn,"FULL","Room already has two players"); return; }
            if(!room.game.equals(game)) { error(conn,"ASSETS","Default assets or engine differ; use the same BCU data"); return; }
            slot=1;
        }
        room.peers[slot]=conn; room.names[slot]=name; membership.put(conn,room);
        JsonObject joined=Protocol.message("joined"); joined.addProperty("room",room.id); joined.addProperty("match",room.match);
        joined.addProperty("slot",slot); joined.addProperty("side",slot==room.leftSlot?"left":"right"); conn.send(joined.toString());
        if(room.peers[1]!=null) {
            JsonObject prepare=Protocol.message("prepare"); prepare.addProperty("leftSlot",room.leftSlot);
            prepare.addProperty("host",room.names[0]); prepare.addProperty("guest",room.names[1]); broadcast(room,prepare);
            room.progressed=now;
        }
    }
    private void beginBundle(Room r,int s,JsonObject o) throws IOException {
        if(r.started || r.peers[1]==null || r.uploadDigest[s]!=null || r.uploaded[s]) throw new IOException("Unexpected bundle");
        long size=Protocol.number(o,"size"); String hash=Protocol.string(o,"hash",64);
        if(size<=0 || size>Protocol.MAX_BUNDLE || !Hashes.valid(hash)) throw new IOException("Invalid bundle size/hash");
        r.uploadSize[s]=size; r.bundleHashes[s]=hash; r.uploadDigest[s]=Hashes.digest();
        JsonObject out=Protocol.message("bundle"); out.addProperty("slot",s); out.addProperty("size",size); out.addProperty("hash",hash);
        r.peers[1-s].send(out.toString());
    }
    @Override public synchronized void onMessage(WebSocket conn,ByteBuffer message) {
        Room r=membership.get(conn);
        try {
            if(r==null || r.closed) throw new IOException("Not in a room");
            int s=r.slot(conn),n=message.remaining();
            if(r.started || r.uploadDigest[s]==null || r.uploaded[s] || n<=0 || n>Protocol.CHUNK || r.received[s]+n>r.uploadSize[s])
                throw new IOException("Unexpected or oversized bundle chunk");
            WebSocket peer=r.peers[1-s];
            if(peer instanceof WebSocketImpl && ((WebSocketImpl)peer).outQueue.size()>64) throw new IOException("Peer is too slow to receive assets");
            byte[] bytes=new byte[n]; message.get(bytes); r.uploadDigest[s].update(bytes); r.received[s]+=n;
            peer.send(bytes); r.progressed=System.nanoTime();
        } catch(Exception e) { if(r!=null) closeRoom(r,"TRANSFER",safe(e)); else deferClose(conn); }
    }
    private void endBundle(Room r,int s) throws IOException {
        if(r.uploadDigest[s]==null || r.uploaded[s] || r.received[s]!=r.uploadSize[s]) throw new IOException("Incomplete bundle");
        if(!Hashes.hex(r.uploadDigest[s].digest()).equals(r.bundleHashes[s])) throw new IOException("Bundle checksum mismatch");
        r.uploaded[s]=true;
        r.peers[s].send(Protocol.message("bundle_ok").toString());
        r.peers[1-s].send(Protocol.message("bundle_end").toString());
    }
    private void ready(Room r,int s,JsonObject o) throws IOException {
        if(r.started || !r.uploaded[0] || !r.uploaded[1]) throw new IOException("Both bundles must finish before ready");
        if(!r.bundleHashes[0].equals(Protocol.string(o,"hostHash",64)) || !r.bundleHashes[1].equals(Protocol.string(o,"guestHash",64)))
            throw new IOException("Bundle-pair mismatch");
        r.ready[s]=true;
        JsonObject state=Protocol.message("ready_state"); state.addProperty("slot",s); broadcast(r,state);
        if(r.ready[0] && r.ready[1]) {
            r.started=true; r.seed=random.nextLong(); r.progressed=System.nanoTime(); r.nextFrameAt=r.progressed;
            JsonObject start=Protocol.message("start"); start.addProperty("seed",r.seed); start.addProperty("leftSlot",r.leftSlot);
            broadcast(r,start);
        }
    }
    private void input(Room r,int s,JsonObject o) throws IOException {
        long tick=Protocol.number(o,"tick"); int mask=Protocol.integer(o,"mask");
        if(!r.started || tick!=r.nextInput[s] || tick<r.tick || tick>r.tick+Protocol.MAX_AHEAD || !InputFrame.valid(mask))
            throw new IOException("Duplicate, out-of-order or invalid input");
        int[] f=r.frames.computeIfAbsent(tick,t -> new int[]{-1,-1}); f[s]=mask; r.nextInput[s]++;
    }
    private void hash(Room r,int s,JsonObject o) throws IOException {
        long tick=Protocol.number(o,"tick"); String hash=Protocol.string(o,"hash",64);
        if(!r.started || tick!=r.hashTick[s]+Protocol.HASH_INTERVAL || tick>r.tick || !Hashes.valid(hash)) throw new IOException("Invalid hash checkpoint");
        r.hashTick[s]=tick;
        String[] pair=r.checkpoints.computeIfAbsent(tick,t -> new String[2]);pair[s]=hash;
        if(pair[0]!=null && pair[1]!=null) {
            r.checkpoints.remove(tick);
            if(!pair[0].equals(pair[1]))closeRoom(r,"DESYNC","Battle state mismatch at tick "+tick);
        }
    }
    private void result(Room r,int s,JsonObject o) throws IOException {
        long tick=Protocol.number(o,"tick"); int winner=Protocol.integer(o,"winner"); String hash=Protocol.string(o,"hash",64);
        if(!r.started || tick<1 || tick>r.tick || winner < -1 || winner>1 || !Hashes.valid(hash)) throw new IOException("Invalid result");
        r.result[s]=tick+":"+winner+":"+hash;
        if(r.result[0]!=null && r.result[1]!=null) {
            if(!r.result[0].equals(r.result[1])) closeRoom(r,"DESYNC","Battle results differ");
            else { JsonObject result=Protocol.message("result"); result.addProperty("winner",winner); result.addProperty("tick",tick); broadcast(r,result); removeRoom(r); }
        }
    }
    private void deferClose(WebSocket socket) { closing.offer(socket); }
    private void drainClosures() {
        WebSocket socket;
        while((socket=closing.poll())!=null)try{socket.close();}catch(RuntimeException ignored){}
    }
    private void pump() {
        try { pumpRooms(); } finally { drainClosures(); }
    }
    private synchronized void pumpRooms() {
        long now=System.nanoTime();
        for(Room r:new ArrayList<>(rooms.values())) {
            if(r.closed) continue;
            if(now-r.created>10*60*SECOND && r.peers[1]==null || now-r.progressed>(r.started?30:120)*SECOND && r.peers[1]!=null) {
                closeRoom(r,"TIMEOUT","Room timed out"); continue;
            }
            if(!r.started || now<r.nextFrameAt) continue;
            int[] f=r.frames.get(r.tick);
            if(f==null || f[0]<0 || f[1]<0) continue;
            // Require periodic state acknowledgments instead of allowing a dead client to run away.
            if(r.tick>=Math.min(r.hashTick[0],r.hashTick[1])+2*Protocol.HASH_INTERVAL) continue;
            JsonObject out=Protocol.message("frame"); out.addProperty("tick",r.tick);
            out.addProperty("left",f[r.leftSlot]); out.addProperty("right",f[1-r.leftSlot]); broadcast(r,out);
            r.frames.remove(r.tick++); r.progressed=now;
            r.nextFrameAt=Math.max(r.nextFrameAt+SECOND/Protocol.TPS,now-SECOND/Protocol.TPS);
        }
        for(Map.Entry<WebSocket,Long> e:new ArrayList<>(opened.entrySet())) {
            if(!membership.containsKey(e.getKey()) && now-e.getValue()>60*SECOND) { deferClose(e.getKey()); opened.remove(e.getKey()); }
        }
        authAttempts.entrySet().removeIf(e -> now-e.getValue()[0]>60*SECOND);
    }
    private static void broadcast(Room r,JsonObject o) { for(WebSocket p:r.peers) if(p!=null && p.isOpen()) p.send(o.toString()); }
    private static void error(WebSocket p,String code,String text) { if(!p.isOpen())return; JsonObject o=Protocol.message("error"); o.addProperty("code",code); o.addProperty("message",text); p.send(o.toString()); }
    private void closeRoom(Room r,String code,String message) {
        if(r.closed) return;
        for(WebSocket p:r.peers) if(p!=null) error(p,code,message);
        removeRoom(r);
    }
    private void removeRoom(Room r) {
        r.closed=true; rooms.remove(r.id); r.frames.clear(); r.checkpoints.clear();
        for(WebSocket p:r.peers) if(p!=null) membership.remove(p);
        Arrays.fill(r.passwordHash,(byte)0);
    }
    @Override public synchronized void onClose(WebSocket conn,int code,String reason,boolean remote) {
        opened.remove(conn); Room r=membership.get(conn); if(r!=null) closeRoom(r,"DISCONNECTED","The other player disconnected");
    }
    @Override public void onError(WebSocket conn,Exception e) { if(conn!=null) deferClose(conn); else System.err.println("PvP server: "+e.getClass().getSimpleName()); }
    @Override public void stop(int timeout) throws InterruptedException { pulse.shutdownNow(); drainClosures(); super.stop(timeout); }
    public static void main(String[] args) throws Exception {
        String host=args.length>0?args[0]:"127.0.0.1"; int port=args.length>1?Integer.parseInt(args[1]):8766;
        RoomServer server=new RoomServer(new InetSocketAddress(host,port)); server.start();
        if(!server.awaitStarted(10,TimeUnit.SECONDS)) throw new IOException("Server failed to start");
        System.out.println("BCU PvP relay listening on "+host+":"+server.getPort()+"; put TLS/WSS in front for internet use.");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { try { server.stop(1000); } catch(InterruptedException e) { Thread.currentThread().interrupt(); } }));
    }
}
