package online.tests;

import com.google.gson.JsonObject;
import common.CommonStatic;
import common.battle.*;
import common.battle.data.CustomUnit;
import common.pack.UserProfile;
import common.util.unit.Unit;
import online.bundle.*;
import online.net.*;
import online.sync.*;
import java.net.URI;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Independent JVM integration peer, sharing actual packs and running the real core. */
public final class HeadlessPeer implements RoomClient.Listener {
    private final BlockingQueue<Runnable> events=new LinkedBlockingQueue<>();
    private final MatchBundle.Mounted[] packs=new MatchBundle.Mounted[2];
    private final String[] hashes=new String[2];
    private final List<Path> temps=new ArrayList<>();
    private final Path directory;
    private final boolean host;
    private final RoomClient client;
    private final MatchBundle own;
    private final Path archive;
    private volatile Throwable failure;
    private int seat=-1,leftSeat=-1;
    private String match;
    private boolean uploaded,ready;
    private PvpStageBasis battle;
    private int targetTicks;
    private HeadlessPeer(boolean host,int port,Path directory,int targetTicks)throws Exception {
        this.host=host;this.directory=directory;this.targetTicks=targetTicks;
        FixtureAssets.init();
        CommonStatic.getConfig().performanceModeAnimation=!host;CommonStatic.getConfig().performanceModeBattle=!host;
        Unit u=Fixture.unit("identical_pack_id",host?100000:200000);
        CustomUnit d=(CustomUnit)u.forms[0].du;d.speed=500;d.range=250;d.price=1;d.rep.proc.WAVE.prob=100;d.rep.proc.WAVE.lv=2;
        archive=MatchBundle.export(Fixture.lineup(u));own=MatchBundle.read(archive);temps.add(archive);
        client=new RoomClient(new URI("ws://127.0.0.1:"+port),false,this);
    }
    public static void main(String[] args)throws Exception {
        HeadlessPeer peer=new HeadlessPeer(args[0].equals("host"),Integer.parseInt(args[1]),Paths.get(args[2]),Integer.parseInt(args[3]));
        try {peer.run();System.out.println("PEER_OK "+args[0]);}finally{peer.close();}
    }
    private void run()throws Exception {
        if(!client.connectBlocking(10,TimeUnit.SECONDS))throw new AssertionError("Connect failed");
        String room="";long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);
        if(!host){Path file=directory.resolve("room");while(!Files.exists(file)){if(System.nanoTime()>deadline)throw new AssertionError("No host room");Thread.sleep(10);}room=new String(Files.readAllBytes(file),StandardCharsets.UTF_8);}
        client.enter(host,room,"integration-password",host?"HOST":"GUEST","left",online.GameFingerprint.compute(s->{}));
        while(failure==null && (battle==null||battle.time<targetTicks)) {
            if(System.nanoTime()>deadline)throw new AssertionError("Peer timeout");
            Runnable task=events.poll(2,TimeUnit.MILLISECONDS);if(task!=null)task.run();
            if(battle!=null){InputFrame frame;int count=0;while(count++<5&&(frame=client.pollFrame())!=null){
                battle.step(frame);
                if(battle.time%60==0){String hash=BattleDigest.of(battle);client.checkpoint(battle.time,hash);Files.write(directory.resolve((host?"host":"guest")+"-"+battle.time),hash.getBytes(StandardCharsets.UTF_8));}
                if(!host){PvpStageBasis copy=battle.displayCopy();copy.advanceDisplay();}
                if(battle.time%40==0)client.queueCommand(1);
                if(battle.time>=targetTicks)break;
            }}
        }
        if(failure!=null)throw new AssertionError("Peer failed",failure);
        if(UserProfile.getUserPack("identical_pack_id").units.get(0).forms[0].du.getHp()!=(host?100000:200000))throw new AssertionError("Local pack overwritten");
        Files.write(directory.resolve(host?"host-done":"guest-done"),new byte[]{1});
        while(!Files.exists(directory.resolve(host?"guest-done":"host-done"))){if(System.nanoTime()>deadline)throw new AssertionError("Other peer did not finish");Thread.sleep(10);}
    }
    @Override public void event(JsonObject o){events.offer(()->{try {
        switch(o.get("type").getAsString()) {
            case "joined":seat=o.get("slot").getAsInt();match=o.get("match").getAsString();packs[seat]=own.mount(match,seat);hashes[seat]=Hashes.sha256(archive);
                if(host)Files.write(directory.resolve("room"),o.get("room").getAsString().getBytes(StandardCharsets.UTF_8));break;
            case "prepare":leftSeat=o.get("leftSlot").getAsInt();client.sendBundle(archive);break;
            case "bundle_ok":uploaded=true;ready();break;
            case "start":battle=PvpStageBasis.create(match,packs[leftSeat].lineup,packs[1-leftSeat].lineup,o.get("seed").getAsLong(),leftSeat);client.queueCommand(1);break;
            default:break;
        }
    }catch(Exception e){failure=e;}});}
    @Override public void bundle(Path file,String hash){events.offer(()->{try{temps.add(file);packs[1-seat]=MatchBundle.read(file).mount(match,1-seat);hashes[1-seat]=hash;ready();}catch(Exception e){failure=e;}});}
    private void ready(){if(!ready&&uploaded&&packs[0]!=null&&packs[1]!=null){ready=true;client.ready(hashes[0],hashes[1]);}}
    @Override public void failed(String message){failure=new IllegalStateException(message);}
    private void close()throws Exception{client.close();for(MatchBundle.Mounted p:packs)if(p!=null)p.close();for(Path f:temps)Files.deleteIfExists(f);}
}
