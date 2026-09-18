package online.tests;

import common.battle.*;
import common.battle.entity.*;
import common.util.CopRand;
import common.util.unit.Unit;
import online.net.lobby.RoomRules;
import online.sync.InputFrame;
import java.util.*;

public final class RouletteTests {
    public static void run() throws Exception {
        FixtureAssets.init();
        reelTests();
        effectTests();
        modeTests();
    }
    private static void reelTests() {
        PvpRouletteState a=new PvpRouletteState(new CopRand(12345));
        PvpRouletteState b=new PvpRouletteState(new CopRand(12345));
        Check.that(Arrays.equals(a.reelSnapshot(),b.reelSnapshot()),"same seed produces the same roulette reel");
        int[] count=new int[14];
        int[] reel=a.reelSnapshot();
        for(int v:reel){Check.that(v>=0&&v<14,"roulette result in range");count[v]++;}
        int[] expected={5,2,3,4,3,2,2,2,3,3,4,4,4,2};
        Check.that(Arrays.equals(expected,count),"43-slot 3DS roulette weights are exact");
        for(int i=1;i<reel.length;i++)Check.that(reel[i]!=reel[i-1],"native-style shuffled reel repairs adjacent duplicates");
    }
    private static void effectTests() throws Exception {
        PvpStageBasis b=duel(RoomRules.SpecialMode.ROULETTE);
        StageBasis left=b.left(),right=b.right();
        EUnit own=unit(b,1),enemy=unit(b,-1);

        enemy.health=enemy.maxH/4;
        right.pvpRoulette.forceResult(b,right,PvpRouletteState.HEAL);
        Check.equal(enemy.maxH*3/4,enemy.health,"heal restores half max HP");

        right.elu.cool[0][0]=77;
        right.pvpRoulette.forceResult(b,right,PvpRouletteState.PRODUCTION_RECOVERY);
        Check.equal(0,right.elu.cool[0][0],"production recovery clears cooldown");

        int originalPrice=right.elu.price[0][0];
        for(int i=0;i<5;i++)right.pvpRoulette.forceResult(b,right,PvpRouletteState.COST_DOWN);
        Check.equal(4,right.pvpRoulette.costLevel,"cost down reaches native MAX after four upgrades");
        Check.equal(Math.max(1,originalPrice/16),right.elu.price[0][0],"cost down caps at one-sixteenth");
        Check.equal(5,right.pvpRoulette.costLevel+1,"base plus four positive levels form five stock states");

        for(int i=0;i<5;i++)right.pvpRoulette.forceResult(b,right,PvpRouletteState.WORKER_UP);
        Check.equal(500,right.pvpRoulette.workerPercent(),"worker efficiency native progression caps at 500 percent");

        right.elu.cool[0][0]=80;
        for(int i=0;i<5;i++)right.pvpRoulette.forceResult(b,right,PvpRouletteState.PRODUCTION_SHORTEN);
        Check.equal(4,right.pvpRoulette.productionLevel,"production shortening caps at native MAX");
        Check.that(right.elu.cool[0][0]<=5,"four production-shortening levels repeatedly halve current cooldown");

        long oldMax=enemy.maxH,oldHealth=enemy.health;
        for(int i=0;i<5;i++)right.pvpRoulette.forceResult(b,right,PvpRouletteState.HP_UP);
        Check.equal(4,right.pvpRoulette.hpLevel,"HP boost caps at Level MAX");
        Check.equal(Math.round(oldMax*8.0),enemy.maxH,"HP Level MAX is 8x from 3DS table");
        Check.equal(oldHealth,enemy.health,"deployed unit current HP is unchanged by native HP-up effect");

        for(int i=0;i<5;i++)right.pvpRoulette.forceResult(b,right,PvpRouletteState.ATTACK_UP);
        Check.equal(8.0,right.pvpRoulette.attackMultiplier(),"attack Level MAX is 8x");
        for(int i=0;i<5;i++)right.pvpRoulette.forceResult(b,right,PvpRouletteState.MOVE_UP);
        Check.equal(8.0,right.pvpRoulette.moveMultiplier(),"move Level MAX is 8x");

        left.money=0;left.pvpRoulette.forceResult(b,left,PvpRouletteState.MONEY_MAX);
        Check.equal(left.maxMoney,left.money,"money max fills bank");

        left.pvpRoulette.forceResult(b,left,PvpRouletteState.SLOW);
        Check.equal(PvpRouletteState.TEMP_TICKS,enemy.status[common.util.Data.P_SLOW][0],"slow starts at native 150 ticks");
        left.pvpRoulette.forceResult(b,left,PvpRouletteState.STOP);
        Check.equal(PvpRouletteState.TEMP_TICKS,enemy.status[common.util.Data.P_STOP][0],"stop starts at native 150 ticks");

        right.work_lv=1;right.money=123;right.elu.cool[0][0]=99;
        right.pvpRoulette.forceResult(b,right,PvpRouletteState.BABY_RUSH);
        Check.equal(0,right.elu.cool[0][0],"petit baby rush immediately removes production cooldown");
        Check.equal(1,right.work_lv,"petit baby rush does not change worker level");
        Check.equal(123,right.money,"petit baby rush does not fill money");
        Check.equal(10*PvpStageBasis.TPS,right.pvpRoulette.babyRushTicks,"petit baby rush lasts exactly ten seconds at 30 TPS");
        right.elu.cool[0][0]=88;
        for(int i=0;i<10*PvpStageBasis.TPS-1;i++)right.pvpRoulette.advance(b,right);
        Check.equal(0,right.elu.cool[0][0],"petit baby rush keeps cooldown at zero during the ten-second window");
        right.pvpRoulette.advance(b,right);
        right.elu.cool[0][0]=77;
        right.pvpRoulette.advance(b,right);
        Check.equal(77,right.elu.cool[0][0],"petit baby rush stops forcing cooldown to zero after ten seconds");

        // Gauge fills from canonical frontline position and auto-starts; special action stops after intro.
        // Native charge: once per second, full HP=1x, half HP=2x, near-zero HP approaches 5x.
        PvpStageBasis charge=duel(RoomRules.SpecialMode.ROULETTE);
        StageBasis chargeOwner=charge.right();
        // duel() advances one setup tick; isolate this timing assertion from that setup.
        chargeOwner.pvpRoulette.gauge=chargeOwner.pvpRoulette.targetGauge=chargeOwner.pvpRoulette.chargeClock=0;
        for(int i=0;i<PvpStageBasis.TPS-1;i++)chargeOwner.pvpRoulette.advance(charge,chargeOwner);
        Check.equal(0,chargeOwner.pvpRoulette.targetGauge,"roulette target does not charge before one second");
        chargeOwner.pvpRoulette.advance(charge,chargeOwner);
        Check.equal(10,chargeOwner.pvpRoulette.targetGauge,"full castle HP charges native 10 points per second");
        Check.equal(10,chargeOwner.pvpRoulette.gauge,"visible gauge chases target by up to 50 points");
        chargeOwner.ownBase().health=chargeOwner.ownBase().maxH/2;
        for(int i=0;i<PvpStageBasis.TPS;i++)chargeOwner.pvpRoulette.advance(charge,chargeOwner);
        Check.equal(30,chargeOwner.pvpRoulette.targetGauge,"half castle HP uses native 2x roulette charge");
        Check.equal(1.0,PvpRouletteState.castleHealthFactor(fullHealth(chargeOwner)),"full HP comeback factor is 1x");
        chargeOwner.ownBase().health=chargeOwner.ownBase().maxH/2;
        Check.equal(2.0,PvpRouletteState.castleHealthFactor(chargeOwner),"half HP comeback factor is 2x");
        chargeOwner.ownBase().health=0;
        Check.equal(5.0,PvpRouletteState.castleHealthFactor(chargeOwner),"zero HP comeback factor is 5x");

        PvpStageBasis auto=duel(RoomRules.SpecialMode.ROULETTE);
        StageBasis autoOwner=auto.right();
        autoOwner.pvpRoulette.gauge=PvpRouletteState.MAX_GAUGE;
        autoOwner.pvpRoulette.targetGauge=PvpRouletteState.MAX_GAUGE;
        autoOwner.pvpRoulette.advance(auto,autoOwner);
        Check.that(autoOwner.pvpRoulette.spinning,"full roulette gauge starts reel automatically");
        for(int i=0;i<10;i++)autoOwner.pvpRoulette.advance(auto,autoOwner);
        Check.that(!autoOwner.pvpRoulette.press(auto,autoOwner),"special input cannot skip the two-second roulette animation");
        for(int i=10;i<PvpRouletteState.AUTO_SPIN_TICKS-1;i++)autoOwner.pvpRoulette.advance(auto,autoOwner);
        Check.that(autoOwner.pvpRoulette.spinning,"roulette remains visible until the two-second threshold");
        autoOwner.pvpRoulette.advance(auto,autoOwner);
        Check.that(!autoOwner.pvpRoulette.spinning,"roulette resolves automatically at roughly two seconds");
        Check.that(autoOwner.pvpRoulette.lastResult>=0,"automatic roulette resolution records the selected effect");
        Check.equal(0,autoOwner.pvpRoulette.gauge,"automatic roulette resolution consumes the full gauge");
    }
    private static void modeTests() throws Exception {
        PvpStageBasis none=duel(RoomRules.SpecialMode.NONE);
        none.left().cannon=none.left().maxCannon;
        none.step(new InputFrame(1,InputFrame.SPECIAL,0));
        Check.equal(0,none.left().cannon,"NONE mode disables special meter/action");

        PvpStageBasis roulette=duel(RoomRules.SpecialMode.ROULETTE);
        roulette.left().pvpRoulette.gauge=PvpRouletteState.MAX_GAUGE;
        roulette.step(new InputFrame(1,0,0));
        Check.that(roulette.left().pvpRoulette.spinning,"ROULETTE mode updates synchronized reel state");
    }
    private static PvpStageBasis duel(RoomRules.SpecialMode mode)throws Exception {
        Unit l=Fixture.unit("roulette_l_"+mode,100000),r=Fixture.unit("roulette_r_"+mode,100000);
        RoomRules rules=new RoomRules(4400,0,-1,false,mode);
        PvpStageBasis b=new PvpStageBasis(Fixture.lineup(l),Fixture.lineup(r),88123,0,rules);
        b.money=b.left().money=100000;
        b.step(new InputFrame(0,1,1));
        return b;
    }
    private static StageBasis fullHealth(StageBasis b){b.ownBase().health=b.ownBase().maxH;return b;}
    private static EUnit unit(PvpStageBasis b,int direction){
        return (EUnit)b.le.stream().filter(e->e instanceof EUnit&&e.dire==direction).findFirst().get();
    }
}
