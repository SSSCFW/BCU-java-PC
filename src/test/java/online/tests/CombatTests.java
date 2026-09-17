package online.tests;

import common.CommonStatic;
import common.battle.*;
import common.battle.data.*;
import common.battle.entity.*;
import common.battle.attack.*;
import common.util.Data;
import common.util.anim.EAnimD;
import common.util.pack.EffAnim;
import common.util.unit.*;
import online.sync.*;
import java.lang.reflect.Field;
import java.util.*;

public final class CombatTests {
    public static void run() throws Exception {
        FixtureAssets.init();
        PvpStageBasis duel=duel(false,false);
        long initial=duel.le.get(0).health;
        for(int tick=1;tick<100;tick++)duel.step(new InputFrame(tick,0,0));
        Check.that(duel.le.get(0).health<initial,"real opposing units inflict damage");
        Check.equal(duel.le.get(0).health,duel.le.get(1).health,"same-unit combat is symmetric");
        for(boolean mini:new boolean[]{false,true}) {
            PvpStageBasis b=duel(!mini,mini);
            EUnit left=(EUnit)b.le.stream().filter(e->e.dire==1).findFirst().get();
            EUnit right=(EUnit)b.le.stream().filter(e->e.dire==-1).findFirst().get();
            AttackSimple la=(AttackSimple)model(left).getAttack(0),ra=(AttackSimple)model(right).getAttack(0);
            la.capture();ra.capture();la.excuse();ra.excuse();
            List<ContAb> waves=new ArrayList<>(b.tlw);
            Check.equal(2,waves.size(),"both units create "+(mini?"mini":"normal")+" waves");
            ContWaveAb lw=(ContWaveAb)waves.get(0),rw=(ContWaveAb)waves.get(1);
            AttackWave l=attack(lw),r=attack(rw);
            Check.equal(400f,Math.abs(l.end-l.sta),"enemy-side wave width uses pink geometry");
            Check.equal(400f,Math.abs(r.end-r.sta),"cat-side wave width unchanged");
            Check.equal(6000f,l.sta+r.end,"first wave leading/trailing edge mirrored");
            Check.equal(6000f,l.end+r.sta,"first wave opposite edge mirrored");
            Check.that(animation(lw).anim()==(mini?EffAnim.effas().A_E_MINIWAVE:EffAnim.effas().A_E_WAVE),"left uses blue effect assets");
            for(int i=0;i<8;i++){lw.update();rw.update();}
            Check.that(b.tlw.size()>=4,"wave propagates to following segment");
            for(ContAb w:b.tlw)Check.equal(400f,Math.abs(attack((ContWaveAb)w).end-attack((ContWaveAb)w).sta),"all following wave segments symmetric");
        }
        // A live combat replay runs identically with drawing/intermediate animation on only one copy.
        PvpStageBasis a=duel(true,false),b=(PvpStageBasis)a.clone();
        for(int tick=1;tick<180 && a.winner()==-2;tick++) {
            CommonStatic.getConfig().performanceModeAnimation=false;a.step(new InputFrame(tick,0,0));
            CommonStatic.getConfig().performanceModeAnimation=true;b.step(new InputFrame(tick,0,0));
            PvpStageBasis view=b.displayCopy();view.advanceDisplay();
            Check.equal(BattleDigest.of(a),BattleDigest.of(b),"wave/combat digest agrees at "+tick);
        }
        CommonStatic.getConfig().performanceModeAnimation=false;
    }
    public static PvpStageBasis duel(boolean wave,boolean mini)throws Exception {
        Unit u=Fixture.unit("duel_a",100000),v=Fixture.unit("duel_b",100000);
        for(Unit f:new Unit[]{u,v}) {
            CustomUnit d=(CustomUnit)f.forms[0].du;d.range=350;d.speed=40;d.front=d.back=1;
            d.rep.proc.WAVE.prob=wave?100:0;d.rep.proc.WAVE.lv=3;
            d.rep.proc.MINIWAVE.prob=mini?100:0;d.rep.proc.MINIWAVE.lv=3;d.rep.proc.MINIWAVE.multi=20;
        }
        PvpStageBasis b=new PvpStageBasis(Fixture.lineup(u),Fixture.lineup(v),912,0);
        b.money=b.left().money=100000;b.step(new InputFrame(0,1,1));
        for(Entity e:b.le)e.pos=e.dire==1?2900:3100;
        return b;
    }
    private static AtkModelEntity model(Entity entity)throws Exception{Field f=Entity.class.getDeclaredField("aam");f.setAccessible(true);return (AtkModelEntity)f.get(entity);}
    private static AttackWave attack(ContWaveAb wave)throws Exception{Field f=ContWaveAb.class.getDeclaredField("atk");f.setAccessible(true);return (AttackWave)f.get(wave);}
    private static EAnimD<?> animation(ContWaveAb wave)throws Exception{Field f=ContWaveAb.class.getDeclaredField("anim");f.setAccessible(true);return (EAnimD<?>)f.get(wave);}
}
