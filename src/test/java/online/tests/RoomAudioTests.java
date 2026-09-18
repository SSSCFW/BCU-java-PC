package online.tests;

import common.CommonStatic;
import common.battle.*;
import common.util.Data;
import online.sync.*;
import java.lang.reflect.*;
import java.util.*;

/** Listener-local notifications must never affect the deterministic combat state. */
public final class RoomAudioTests {
    private static void heard(int dir,Runnable task) throws Exception {
        PvpAudio.forPlayer(dir,task);
    }
    public static void run() throws Exception {
        FixtureNativeUi.init(); CommonStatic.Itf old=CommonStatic.def;List<Integer> heard=new ArrayList<>();
        CommonStatic.def=(CommonStatic.Itf)Proxy.newProxyInstance(CommonStatic.Itf.class.getClassLoader(),new Class<?>[]{CommonStatic.Itf.class},(p,m,a)->{
            if(m.getName().equals("setSE")&&a[0] instanceof Integer){heard.add((Integer)a[0]);return null;}
            return m.invoke(old,a);
        });
        try {
            PvpStageBasis b=new PvpStageBasis(Fixture.lineup(FixtureNativeUi.unit("audio_left",0xff000000)),Fixture.lineup(FixtureNativeUi.unit("audio_right",0xff000000)),19,0);b.right().money=1000000;
            heard(1,()->b.step(new InputFrame(0,0,1)));
            Check.that(!heard.contains((int)Data.SE_SPEND_SUC),"opponent production sound must not be audible to local left player");
            heard.clear();b.left().money=1000000;heard(1,()->b.step(new InputFrame(1,1,0)));
            Check.that(heard.contains((int)Data.SE_SPEND_SUC),"local production sound retained");
            heard.clear();b.left().elu.cool[0][0]=0;b.right().elu.cool[0][0]=1;
            heard(1,()->b.step(new InputFrame(2,0,0)));
            Check.that(!heard.contains((int)Data.SE_SPEND_REF),"remote cooldown completion suppressed");
            heard.clear();b.left().elu.cool[0][0]=1;b.right().elu.cool[0][0]=0;
            heard(1,()->b.step(new InputFrame(3,0,0)));
            Check.that(heard.contains((int)Data.SE_SPEND_REF),"local cooldown completion audible");
            heard.clear();b.left().cannon=b.left().maxCannon-1;b.right().cannon=0;
            heard(1,()->b.step(new InputFrame(4,0,0)));
            Check.that(heard.contains((int)Data.SE_CANNON_CHARGE),"left player gets own cannon-ready sound");
            heard.clear();b.left().cannon=0;b.right().cannon=b.right().maxCannon-1;
            heard(1,()->b.step(new InputFrame(5,0,0)));
            Check.that(!heard.contains((int)Data.SE_CANNON_CHARGE),"opponent cannon-ready sound suppressed");
            PvpStageBasis c=b.displayCopy(),d=b.displayCopy();
            for(int i=0;i<20;i++){int tick=c.time;heard(1,()->c.step(new InputFrame(tick,1,1)));heard(-1,()->d.step(new InputFrame(tick,1,1)));}
            Check.equal(BattleDigest.of(c),BattleDigest.of(d),"different listeners produce identical simulation digest");
            heard.clear();heard(1,()->CommonStatic.setSE(Data.SE_HIT_0));Check.that(heard.contains((int)Data.SE_HIT_0),"shared combat sounds remain audible");
            heard.clear();b.right().money=1000000;b.right().elu.cool[0][0]=0;b.right().unitRespawnTime=0;b.step(new InputFrame(b.time,0,1));
            Check.that(heard.contains((int)Data.SE_SPEND_SUC),"sound context restored after scoped stepping");
            heard.clear();
            PvpStageBasis roulette=new PvpStageBasis(
                    Fixture.lineup(FixtureNativeUi.unit("audio_roulette_l",0xff111111)),
                    Fixture.lineup(FixtureNativeUi.unit("audio_roulette_r",0xff222222)),22,0,
                    new online.net.lobby.RoomRules(4400,0,-1,false,online.net.lobby.RoomRules.SpecialMode.ROULETTE));
            heard(1,()->roulette.left().pvpRoulette.forceResult(roulette,roulette.left(),PvpRouletteState.KNOCKBACK));
            Check.that(heard.contains((int)Data.SE_WAVE),"roulette knockback activation plays the shockwave sound effect");
        } finally {CommonStatic.def=old;}
    }
    public static void main(String[] args)throws Exception{run();System.out.println("Player-local audio tests passed");}
}
