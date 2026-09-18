package online.ui;

import common.battle.*;
import online.net.lobby.RoomRules;
import online.sync.BattleDigest;
import online.tests.*;
import java.util.*;
import online.ui.PvpSoundBank.Sound;

public final class PvpRouletteAudioTests {
    public static void run()throws Exception{
        FixtureNativeUi.init();
        for(int direction:new int[]{1,-1}){
            PvpStageBasis world=new PvpStageBasis(Fixture.lineup(FixtureNativeUi.unit("sound_l"+direction,0xff112233)),
                    Fixture.lineup(FixtureNativeUi.unit("sound_r"+direction,0xff445566)),234,0,
                    new RoomRules(4400,0,3,false,RoomRules.SpecialMode.ROULETTE));
            List<Sound> events=new ArrayList<>();int[] loops=new int[2];
            PvpRouletteAudio detector=new PvpRouletteAudio(new PvpRouletteAudio.Sink(){
                public void play(Sound sound){events.add(sound);}
                public void startLoop(Sound sound){Check.equal(Sound.ROULETTE_SPIN,sound,"only own spin loops");loops[0]++;}
                public void stopLoop(Sound sound){loops[1]++;}
            });
            detector.observe(world,direction);Check.that(events.isEmpty(),"initial snapshot does not replay old events");
            PvpRouletteState own=world.playerFor(direction).pvpRoulette,other=world.playerFor(-direction).pvpRoulette;
            for(int i=1;i<=10;i++){
                own.gauge=i*100;world.time++;
                String before=BattleDigest.of(world);detector.observe(world,direction);detector.observe(world,direction);
                Check.equal(before,BattleDigest.of(world),"audio observation cannot mutate synchronized state");
            }
            Check.equal(10,Collections.frequency(events,Sound.ROULETTE_CHARGE),"one charge per segment without rendering");
            Check.equal(1,Collections.frequency(events,Sound.ROULETTE_MAX),"MAX once");
            own.spinning=true;other.spinning=true;detector.observe(world,direction);
            for(int i=0;i<60;i++){world.time++;detector.observe(world,direction);}
            Check.equal(1,Collections.frequency(events,Sound.ROULETTE_START),"own start once for either player side");
            Check.equal(1,Collections.frequency(events,Sound.OPPONENT_ROULETTE_START),"opponent start once");
            Check.equal(1,loops[0],"continuous spin does not reopen/restart every tick");
            own.spinning=other.spinning=false;own.gauge=0;own.pendingResult=2;other.pendingResult=4;
            detector.observe(world,direction);detector.observe(world,direction);
            Check.equal(1,loops[1],"confirmation stops spin loop");
            Check.equal(1,Collections.frequency(events,Sound.ROULETTE_CONFIRM),"own result once");
            Check.equal(1,Collections.frequency(events,Sound.OPPONENT_ROULETTE_CONFIRM),"opponent result once");
            own.pendingResult=-1;other.pendingResult=-1;detector.observe(world,direction);
            own.gauge=100;detector.observe(world,direction);
            Check.equal(11,Collections.frequency(events,Sound.ROULETTE_CHARGE),"new gauge cycle charges again");
        }
        System.out.println("Roulette audio edge tests passed for both player sides");
    }
    public static void main(String[] args)throws Exception{run();}
}
