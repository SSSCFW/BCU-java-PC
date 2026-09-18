package online.ui;

import common.battle.PvpRouletteState;
import common.battle.PvpStageBasis;
import online.net.lobby.RoomRules;
import online.ui.PvpSoundBank.Sound;

/** Read-only, listener-local edge detector. Observe EVERY logic tick, never just rendered frames. */
public final class PvpRouletteAudio {
    interface Sink {void play(Sound sound);void startLoop(Sound sound);void stopLoop(Sound sound);}
    private final Sink sink;
    private boolean initialized,ownSpinning,opponentSpinning;
    private int segment,ownPending,opponentPending;
    public PvpRouletteAudio(){this(new Sink(){
        public void play(Sound s){PvpSoundBank.play(s);}
        public void startLoop(Sound s){PvpSoundBank.startLoop(s);}
        public void stopLoop(Sound s){PvpSoundBank.stopLoop(s);}
    });}
    PvpRouletteAudio(Sink sink){this.sink=sink;}
    public void initialize(PvpStageBasis world,int direction){initialized=false;observe(world,direction);}
    public void observe(PvpStageBasis world,int direction){
        if(world==null||world.specialMode()!=RoomRules.SpecialMode.ROULETTE){
            if(initialized)sink.stopLoop(Sound.ROULETTE_SPIN);initialized=false;return;
        }
        PvpRouletteState own=world.playerFor(direction).pvpRoulette,other=world.playerFor(-direction).pvpRoulette;
        if(own==null||other==null)return;
        int next=Math.max(0,Math.min(10,own.gauge/(PvpRouletteState.MAX_GAUGE/10)));
        if(!initialized){
            initialized=true;
            if(own.spinning)sink.startLoop(Sound.ROULETTE_SPIN);
        }else{
            for(int i=segment+1;i<=next;i++)sink.play(Sound.ROULETTE_CHARGE);
            if(segment<10&&next==10)sink.play(Sound.ROULETTE_MAX);
            if(own.spinning&&!ownSpinning){sink.play(Sound.ROULETTE_START);sink.startLoop(Sound.ROULETTE_SPIN);}
            if(!own.spinning&&ownSpinning)sink.stopLoop(Sound.ROULETTE_SPIN);
            if(own.pendingResult>=0&&ownPending<0)sink.play(Sound.ROULETTE_CONFIRM);
            if(other.spinning&&!opponentSpinning)sink.play(Sound.OPPONENT_ROULETTE_START);
            if(other.pendingResult>=0&&opponentPending<0)sink.play(Sound.OPPONENT_ROULETTE_CONFIRM);
        }
        segment=next;ownSpinning=own.spinning;opponentSpinning=other.spinning;
        ownPending=own.pendingResult;opponentPending=other.pendingResult;
    }
}
