package online.net;

import com.google.gson.JsonObject;
import online.bundle.Hashes;
import online.sync.InputFrame;
import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

/** Socket callbacks never advance the battle. They deliver bounded, ordered frames to the UI. */
public final class RoomClient extends WebSocketClient implements AutoCloseable {
    public interface Listener {
        void event(JsonObject event);
        void bundle(Path verifiedArchive,String hash);
        void failed(String reason);
    }
    private final Listener listener;
    private final BlockingQueue<InputFrame> frames=new ArrayBlockingQueue<>(128);
    private final AtomicInteger commands=new AtomicInteger();
    private final ExecutorService transfer=Executors.newSingleThreadExecutor(r -> {Thread t=new Thread(r,"pvp-assets");t.setDaemon(true);return t;});
    private volatile boolean ended,started,reported;
    private long expectedTick;
    private Path receiving;
    private OutputStream output;
    private long expectedBytes,received;
    private MessageDigest digest;
    private String remoteHash;
    public RoomClient(URI uri,boolean allowDevelopmentWs,Listener listener) throws IOException {
        super(validateUri(uri,allowDevelopmentWs),Protocol.draft(),null,10000);
        this.listener=listener;setConnectionLostTimeout(20);
    }
    public static URI validateUri(URI uri,boolean development) throws IOException {
        if(uri==null || uri.getHost()==null || uri.getUserInfo()!=null || uri.getFragment()!=null)throw new IOException("Enter a ws(s) server URL without credentials");
        String host=uri.getHost();boolean local=host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]") || host.equals("::1");
        if(!"wss".equalsIgnoreCase(uri.getScheme()) && !("ws".equalsIgnoreCase(uri.getScheme()) && (local || development)))
            throw new IOException("Internet rooms require WSS; WS is allowed only for loopback or explicit LAN development");
        return uri;
    }
    @Override public void onOpen(ServerHandshake h){listener.event(Protocol.message("connected"));}
    public void enter(boolean create,String room,String password,String name,String side,String gameHash) {
        JsonObject o=Protocol.message(create?"create":"join");o.addProperty("version",Protocol.VERSION);o.addProperty("engine",Protocol.ENGINE);
        o.addProperty("game",gameHash);o.addProperty("name",name);o.addProperty("password",password);
        o.addProperty("room",room);o.addProperty("side",side);send(o.toString());
    }
    @Override public synchronized void onMessage(String text) {
        if(ended)return;
        try {
            JsonObject o=Protocol.parse(text);String type=Protocol.string(o,"type",32);
            switch(type) {
                case "bundle":
                    if(output!=null || receiving!=null || started)throw new IOException("Unexpected incoming bundle");
                    expectedBytes=Protocol.number(o,"size");remoteHash=Protocol.string(o,"hash",64);
                    if(expectedBytes<=0 || expectedBytes>Protocol.MAX_BUNDLE || !Hashes.valid(remoteHash))throw new IOException("Invalid remote bundle");
                    received=0;digest=Hashes.digest();receiving=Files.createTempFile("bcu-pvp-receive-",".zip");
                    output=Files.newOutputStream(receiving);break;
                case "bundle_end":
                    if(output==null || received!=expectedBytes)throw new IOException("Incomplete remote bundle");
                    output.close();output=null;
                    if(!Hashes.hex(digest.digest()).equals(remoteHash))throw new IOException("Remote bundle checksum mismatch");
                    Path complete=receiving;receiving=null;listener.bundle(complete,remoteHash);break;
                case "start":
                    if(started)throw new IOException("Duplicate start");started=true;expectedTick=0;
                    for(int t=0;t<Protocol.INPUT_DELAY;t++)sendInput(t,0);
                    listener.event(o);break;
                case "frame":
                    long tick=Protocol.number(o,"tick");
                    if(!started || tick!=expectedTick++)throw new IOException("Missing/out-of-order battle tick");
                    InputFrame frame=new InputFrame(tick,Protocol.integer(o,"left"),Protocol.integer(o,"right"));
                    if(!frames.offer(frame))throw new IOException("Simulation cannot keep up with the room");
                    // Sampling is tied to network logic ticks, NOT render frames.
                    sendInput(tick+Protocol.INPUT_DELAY,commands.getAndSet(0));break;
                case "error":fail(Protocol.string(o,"code",32)+": "+Protocol.string(o,"message",1024));break;
                default:listener.event(o);
            }
        }catch(Exception e){fail(e.getMessage()==null?"Invalid server response":e.getMessage());}
    }
    @Override public synchronized void onMessage(ByteBuffer bytes) {
        if(ended)return;
        try {
            int n=bytes.remaining();if(output==null || n<=0 || n>Protocol.CHUNK || received+n>expectedBytes)throw new IOException("Unexpected asset chunk");
            byte[] b=new byte[n];bytes.get(b);output.write(b);digest.update(b);received+=n;
        }catch(IOException e){fail(e.getMessage());}
    }
    public void sendBundle(Path file) {
        transfer.execute(() -> {
            try {
                long size=Files.size(file);if(size<=0 || size>Protocol.MAX_BUNDLE)throw new IOException("Bundle size limit");
                JsonObject begin=Protocol.message("bundle");begin.addProperty("size",size);begin.addProperty("hash",Hashes.sha256(file));send(begin.toString());
                try(InputStream in=Files.newInputStream(file)) {
                    byte[] bytes=new byte[Protocol.CHUNK];int n;
                    while(!ended && (n=in.read(bytes))!=-1) {
                        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
                        while(hasBufferedData() && !ended) {if(System.nanoTime()>deadline)throw new IOException("Asset upload timeout");Thread.sleep(5);}
                        if(ended)return;send(java.util.Arrays.copyOf(bytes,n));
                    }
                }
                if(!ended)send(Protocol.message("bundle_end").toString());
            }catch(Exception e){fail("Asset transfer failed: "+e.getMessage());}
        });
    }
    public void ready(String hostHash,String guestHash) {JsonObject o=Protocol.message("ready");o.addProperty("hostHash",hostHash);o.addProperty("guestHash",guestHash);send(o.toString());}
    public void queueCommand(int bit) {
        if(!started || ended || !InputFrame.valid(bit))return;
        commands.getAndUpdate(old -> (old | (bit&4095)) ^ (bit&~4095));
    }
    private void sendInput(long tick,int mask) {JsonObject o=Protocol.message("input");o.addProperty("tick",tick);o.addProperty("mask",mask);send(o.toString());}
    public InputFrame pollFrame(){return frames.poll();}
    public void checkpoint(long tick,String hash) {JsonObject o=Protocol.message("hash");o.addProperty("tick",tick);o.addProperty("hash",hash);send(o.toString());}
    public void result(long tick,int winner,String hash){JsonObject o=Protocol.message("result");o.addProperty("tick",tick);o.addProperty("winner",winner);o.addProperty("hash",hash);send(o.toString());}
    private synchronized void fail(String reason) {if(reported || ended)return;reported=true;listener.failed(reason);close();}
    @Override public void onClose(int code,String reason,boolean remote){if(!ended)fail("Connection closed ("+code+")");}
    @Override public void onError(Exception e){fail("Connection error: "+e.getClass().getSimpleName());}
    @Override public synchronized void close() {
        if(ended)return;ended=true;transfer.shutdownNow();frames.clear();
        try{if(output!=null)output.close();if(receiving!=null)Files.deleteIfExists(receiving);}catch(IOException ignored){}
        output=null;receiving=null;
        if(isOpen())try{send(Protocol.message("leave").toString());}catch(RuntimeException ignored){}
        super.close();
    }
}
