package online.tests;

import common.battle.PvpStageBasis;
import common.battle.StageBasis;
import common.battle.entity.Entity;
import common.battle.data.CustomUnit;
import common.util.unit.Unit;
import online.net.lobby.PvpTraitRules;
import online.net.lobby.RoomRules;
import online.sync.BattleDigest;
import online.sync.InputFrame;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Dense 1v1 regression: optimized range lookup must stay bit-for-bit identical
 * to the legacy full scan while keeping a 500-entity battle bounded.
 */
public final class PvpStressTests {
    private static final int PER_SIDE=250;
    private static final int WARMUP=4;
    private static final int MEASURE=12;

    public static void run() throws Exception {
        FixtureAssets.init();
        PvpStageBasis optimized=crowded(0x5eed1234L,"stress_opt");
        PvpStageBasis reference=crowded(0x5eed1234L,"stress_ref");
        disableSpatialIndex(reference);

        Check.equal(PER_SIDE*2,optimized.le.size(),"stress fixture creates 500 live PvP entities");
        Check.equal(BattleDigest.of(reference),BattleDigest.of(optimized),"stress fixtures begin deterministic");

        List<Long> optimizedNanos=new ArrayList<>(),referenceNanos=new ArrayList<>();
        for(int tick=0;tick<WARMUP+MEASURE;tick++){
            long a,b;
            if((tick&1)==0){
                a=step(reference,tick);
                b=step(optimized,tick);
            }else{
                b=step(optimized,tick);
                a=step(reference,tick);
            }
            if(tick>=WARMUP){referenceNanos.add(a);optimizedNanos.add(b);}
            Check.equal(BattleDigest.of(reference),BattleDigest.of(optimized),
                    "spatial index preserves canonical PvP state at stress tick "+tick);
        }

        long optP95=p95(optimizedNanos),refP95=p95(referenceNanos);
        double optMs=optP95/1_000_000.0,refMs=refP95/1_000_000.0;
        System.out.printf(java.util.Locale.ROOT,
                "PVP_STRESS entities=%d optimized_p95=%.3fms fullscan_p95=%.3fms target=33.333ms%n",
                optimized.le.size(),optMs,refMs);
        // CI machines vary heavily; this guards catastrophic regressions while the
        // printed 33.333ms target remains visible for tuning.
        Check.that(optP95<TimeUnit.MILLISECONDS.toNanos(100),
                "500-entity optimized logic p95 remains bounded below 100ms");
        Check.that(optP95<=refP95*6/5,
                "spatial index must not regress 500-entity p95 by more than 20%");

        // Warm the reflection metadata cache once, then exercise the exact display-copy
        // path used by online presentation at maximum configured unit count.
        PvpStageBasis first=optimized.displayCopy();
        Check.equal(BattleDigest.of(optimized),BattleDigest.of(first),"display copy preserves stress state");
        List<Long> copies=new ArrayList<>();
        for(int i=0;i<3;i++){
            long start=System.nanoTime();
            PvpStageBasis copy=optimized.displayCopy();
            copies.add(System.nanoTime()-start);
            Check.equal(BattleDigest.of(optimized),BattleDigest.of(copy),"stress display copy remains canonical");
        }
        System.out.printf(java.util.Locale.ROOT,"PVP_STRESS display_copy_p95=%.3fms%n",p95(copies)/1_000_000.0);
    }

    private static long step(PvpStageBasis battle,int tick){
        long start=System.nanoTime();
        battle.step(new InputFrame(tick,0,0));
        return System.nanoTime()-start;
    }

    private static long p95(List<Long> values){
        ArrayList<Long> sorted=new ArrayList<>(values);Collections.sort(sorted);
        int index=Math.max(0,(int)Math.ceil(sorted.size()*0.95)-1);
        return sorted.get(index);
    }

    private static PvpStageBasis crowded(long seed,String packPrefix)throws Exception{
        Unit left=Fixture.unit(packPrefix+"_l",1_000_000);
        Unit right=Fixture.unit(packPrefix+"_r",1_000_000);
        for(Unit unit:new Unit[]{left,right}){
            CustomUnit data=(CustomUnit)unit.forms[0].du;
            data.range=250;data.speed=0;data.front=data.back=1;
            data.atks[0].atk=10;data.atks[0].pre=1;
        }
        RoomRules rules=new RoomRules(4400,0,3,true,RoomRules.SpecialMode.NONE,false,
                PvpTraitRules.NONE,PvpTraitRules.NONE,0,0,RoomRules.UNLIMITED_TIME,
                RoomRules.MAX_MAX_UNITS,false,RoomRules.DEFAULT_CASTLE_HIT_MONEY);
        PvpStageBasis battle=new PvpStageBasis(Fixture.lineup(left),Fixture.lineup(right),seed,0,rules);
        Method spawn=StageBasis.class.getDeclaredMethod("act_spawn",int.class,int.class,boolean.class);
        spawn.setAccessible(true);
        for(int i=0;i<PER_SIDE;i++){
            spawnOne(spawn,battle.left());
            spawnOne(spawn,battle.right());
        }
        int li=0,ri=0;
        for(Entity entity:battle.le){
            if(entity.dire==1)entity.pos=1000f+(li++*(1900f/(PER_SIDE-1)));
            else entity.pos=5000f-(ri++*(1900f/(PER_SIDE-1)));
            entity.lastPosition=entity.pos;
        }
        return battle;
    }

    private static void spawnOne(Method spawn,StageBasis owner)throws Exception{
        owner.unitRespawnTime=0;owner.elu.cool[0][0]=0;owner.money=Integer.MAX_VALUE/4;
        boolean ok=(Boolean)spawn.invoke(owner,0,0,true);
        if(!ok)throw new AssertionError("Unable to construct PvP stress entity "+owner.ownDirection());
    }

    private static void disableSpatialIndex(PvpStageBasis battle)throws Exception{
        Field field=StageBasis.class.getDeclaredField("NONC_disablePvpSpatialIndex");
        field.setAccessible(true);field.setBoolean(battle,true);
    }

    public static void main(String[] args)throws Exception{run();System.out.println("PvP stress tests passed");}
}
