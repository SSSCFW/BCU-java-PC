package online.net.core;
import online.net.bundle.BundleStore;
import online.net.Protocol;
import online.net.lobby.RoomRules;
import java.io.IOException;
import java.util.*;

/** Match-scoped state: one participant registry, disk cache and deterministic input barrier. */
public final class RoomSession implements AutoCloseable {
    public final String id,matchId,gameFingerprint;
    public final GameMode gameMode;
    public final int inputDelayTicks;
    final PasswordVerifier password;
    public final LinkedHashMap<Integer,Participant> participants=new LinkedHashMap<>();
    public final BundleStore bundles;
    public LockstepState lockstep;
    public RoomRules rules=RoomRules.DEFAULT;
    public int hostId;
    public long revision;
    public final long created=System.nanoTime();
    public long progressed=created,nextFrameAt,seed;
    public boolean prepared,manifestSent,started,resultReady;
    public volatile boolean closed;
    RoomSession(String id,String match,String game,GameMode mode,int delay,PasswordVerifier verifier,long limit,int capacity)throws IOException {
        this.id=id;matchId=match;gameFingerprint=game;gameMode=mode;inputDelayTicks=delay;password=verifier;bundles=new BundleStore(limit,capacity);
    }
    public List<GameMode.Seat> occupiedSeats(){List<GameMode.Seat> seats=new ArrayList<>();for(Participant p:participants.values())seats.add(p.seat);return seats;}
    public void changed(){revision++;progressed=System.nanoTime();for(Participant p:participants.values())p.lobbyReady=false;}
    public void prepare(){lockstep=new LockstepState(participants.keySet(),Protocol.MAX_AHEAD);prepared=true;}
    public void resetMatch(){
        lockstep=null;prepared=false;manifestSent=false;started=false;resultReady=false;seed=0;nextFrameAt=0;
        for(Participant p:participants.values())p.resetMatchState();
        changed();
    }
    public void close()throws IOException{closed=true;password.close();bundles.close();}
}
