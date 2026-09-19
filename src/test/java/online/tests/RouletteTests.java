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

        int shockEffects=b.lea.size();
        left.pvpRoulette.forceResult(b,left,PvpRouletteState.KNOCKBACK);
        Check.that(b.lea.size()>shockEffects,"roulette knockback creates the native boss A_SHOCKWAVE effect");

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
        Check.equal(Math.round(oldHealth*8.0),enemy.health,"HP Level MAX immediately scales deployed unit current HP");

        int baseDisplayedAtk=enemy.getAtk();
        right.pvpRoulette.forceResult(b,right,PvpRouletteState.ATTACK_UP);
        Check.equal((int)Math.round(baseDisplayedAtk*1.5),enemy.getAtk(),"attack-up immediately changes deployed-unit status attack");
        right.pvpRoulette.attackLevel=0;
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
        int rushMax=right.maxMoney,rushExpected=Math.min(rushMax,123+rushMax/2);
        right.pvpRoulette.forceResult(b,right,PvpRouletteState.BABY_RUSH);
        Check.equal(0,right.elu.cool[0][0],"petit baby rush immediately removes production cooldown");
        Check.equal(1,right.work_lv,"petit baby rush does not change worker level");
        Check.equal(rushExpected,right.money,"petit baby rush adds half of the current wallet limit");
        Check.equal(10*PvpStageBasis.TPS,right.pvpRoulette.babyRushTicks,"petit baby rush lasts exactly ten seconds at 30 TPS");
        right.money=Math.max(0,right.maxMoney-1);
        right.pvpRoulette.forceResult(b,right,PvpRouletteState.BABY_RUSH);
        Check.equal(right.maxMoney,right.money,"petit baby rush money bonus never exceeds wallet limit");
        right.elu.cool[0][0]=88;
        for(int i=0;i<10*PvpStageBasis.TPS-1;i++)right.pvpRoulette.advance(b,right);
        Check.equal(0,right.elu.cool[0][0],"petit baby rush keeps cooldown at zero during the ten-second window");
        right.pvpRoulette.advance(b,right);
        right.elu.cool[0][0]=77;
        right.pvpRoulette.advance(b,right);
        Check.equal(77,right.elu.cool[0][0],"petit baby rush stops forcing cooldown to zero after ten seconds");

        // Gauge follows the reverse-engineered castle-HP cadence and waits for explicit SPECIAL at 100%.
        // Native charge: once per second, full HP=1x, half HP=2x, near-zero HP approaches 5x.
        PvpStageBasis charge=duel(RoomRules.SpecialMode.ROULETTE);
        StageBasis chargeOwner=charge.right();
        // duel() advances one setup tick; isolate this timing assertion from that setup.
        chargeOwner.pvpRoulette.gauge=chargeOwner.pvpRoulette.targetGauge=chargeOwner.pvpRoulette.chargeClock=0;
        for(int i=0;i<PvpStageBasis.TPS-1;i++)chargeOwner.pvpRoulette.advance(charge,chargeOwner);
        Check.equal(0,chargeOwner.pvpRoulette.targetGauge,"roulette target does not charge before one second");
        chargeOwner.pvpRoulette.advance(charge,chargeOwner);
        Check.equal(10,chargeOwner.pvpRoulette.targetGauge,"full castle HP charges native base 10 points per second in the first half");
        Check.equal(10,chargeOwner.pvpRoulette.gauge,"visible gauge chases target by up to 50 points");
        chargeOwner.ownBase().health=chargeOwner.ownBase().maxH/2;
        // This assertion isolates the passive 1-second cadence. Re-baseline the
        // stored castle HP so the separate immediate castle-damage event is not
        // intentionally added to the same measurement.
        chargeOwner.pvpRoulette.initializeCharge(chargeOwner);
        for(int i=0;i<PvpStageBasis.TPS;i++)chargeOwner.pvpRoulette.advance(charge,chargeOwner);
        Check.equal(30,chargeOwner.pvpRoulette.targetGauge,"half castle HP uses native 2x passive roulette charge");
        Check.equal(1.0,PvpRouletteState.castleHealthFactor(fullHealth(chargeOwner)),"full HP comeback factor is 1x");
        chargeOwner.ownBase().health=chargeOwner.ownBase().maxH/2;
        Check.equal(2.0,PvpRouletteState.castleHealthFactor(chargeOwner),"half HP comeback factor is 2x");
        chargeOwner.ownBase().health=0;
        Check.equal(5.0,PvpRouletteState.castleHealthFactor(chargeOwner),"zero HP comeback factor is 5x");

        Check.equal(1.0,PvpRouletteState.matchTimeFactor(90*PvpStageBasis.TPS),"remaining 50 percent keeps 1x time factor");
        Check.equal(2.0,PvpRouletteState.matchTimeFactor(91*PvpStageBasis.TPS),"below 50 percent remaining uses 2x time factor");
        Check.equal(2.0,PvpRouletteState.matchTimeFactor(135*PvpStageBasis.TPS),"remaining 25 percent still uses 2x time factor");
        Check.equal(5.0,PvpRouletteState.matchTimeFactor(136*PvpStageBasis.TPS),"final quarter uses 5x time factor");

        PvpStageBasis normalFill=duel(RoomRules.SpecialMode.ROULETTE);
        StageBasis normalFillOwner=normalFill.right();
        normalFillOwner.pvpRoulette.gauge=0;normalFillOwner.pvpRoulette.targetGauge=300;normalFillOwner.pvpRoulette.chargeClock=0;
        normalFillOwner.pvpRoulette.initializeCharge(normalFillOwner);
        normalFillOwner.pvpRoulette.advance(normalFill,normalFillOwner);
        Check.equal(50,normalFillOwner.pvpRoulette.gauge,"ordinary roulette gauge presentation remains +50 per tick");

        PvpStageBasis castlePartial=duel(RoomRules.SpecialMode.ROULETTE);
        StageBasis castlePartialOwner=castlePartial.right();
        castlePartialOwner.pvpRoulette.gauge=castlePartialOwner.pvpRoulette.targetGauge=castlePartialOwner.pvpRoulette.chargeClock=0;
        castlePartialOwner.pvpRoulette.initializeCharge(castlePartialOwner);
        castlePartialOwner.ownBase().health-=100000;
        castlePartialOwner.pvpRoulette.advance(castlePartial,castlePartialOwner);
        Check.equal(300,castlePartialOwner.pvpRoulette.targetGauge,"taking 100000 castle damage adds floor(damage*3/1000) before comeback factors");
        Check.equal(50,castlePartialOwner.pvpRoulette.gauge,"castle damage does not change the native gauge-fill animation speed");
        Check.that(castlePartialOwner.pvpRoulette.castleDamageFastSpinReady,"partial castle charge arms the shortened next roulette");

        // Later charge can provide the final points. Preserve the castle-damage
        // contribution marker across that interval, then finish at 100% through the
        // real once-per-second passive-charge path.
        castlePartialOwner.pvpRoulette.gauge=castlePartialOwner.pvpRoulette.targetGauge=990;
        castlePartialOwner.pvpRoulette.chargeClock=PvpStageBasis.TPS-1;
        castlePartialOwner.pvpRoulette.initializeCharge(castlePartialOwner);
        castlePartialOwner.pvpRoulette.advance(castlePartial,castlePartialOwner);
        Check.equal(PvpRouletteState.MAX_GAUGE,castlePartialOwner.pvpRoulette.gauge,
                "passive charge can finish a gauge that previously received castle-damage charge");
        Check.that(castlePartialOwner.pvpRoulette.press(castlePartial,castlePartialOwner),"mixed castle/passive-filled gauge starts roulette");
        Check.equal(PvpRouletteState.AUTO_SPIN_TICKS/2,castlePartialOwner.pvpRoulette.spinDurationTicks,
                "partial castle contribution keeps the next reel at half duration");

        PvpStageBasis castleFilled=duel(RoomRules.SpecialMode.ROULETTE);
        StageBasis castleFilledOwner=castleFilled.right();
        castleFilledOwner.pvpRoulette.gauge=castleFilledOwner.pvpRoulette.targetGauge=950;
        castleFilledOwner.pvpRoulette.chargeClock=0;castleFilledOwner.pvpRoulette.initializeCharge(castleFilledOwner);
        castleFilledOwner.ownBase().health-=100000;
        castleFilledOwner.pvpRoulette.advance(castleFilled,castleFilledOwner);
        Check.equal(PvpRouletteState.MAX_GAUGE,castleFilledOwner.pvpRoulette.gauge,"castle hit can finish the visible roulette gauge");
        Check.that(castleFilledOwner.pvpRoulette.castleDamageFastSpinReady,"only castle damage that fills the gauge arms the short reel");
        Check.that(castleFilledOwner.pvpRoulette.press(castleFilled,castleFilledOwner),"castle-filled gauge starts roulette normally");
        Check.equal(PvpRouletteState.AUTO_SPIN_TICKS/2,castleFilledOwner.pvpRoulette.spinDurationTicks,"castle-filled roulette reel is exactly half duration");
        for(int i=0;i<PvpRouletteState.AUTO_SPIN_TICKS/2-1;i++)castleFilledOwner.pvpRoulette.advance(castleFilled,castleFilledOwner);
        Check.that(castleFilledOwner.pvpRoulette.spinning,"short castle-damage reel remains visible until its half-duration threshold");
        castleFilledOwner.pvpRoulette.advance(castleFilled,castleFilledOwner);
        Check.that(!castleFilledOwner.pvpRoulette.spinning,"castle-damage roulette resolves after one second at 30TPS");

        PvpStageBasis passiveFilled=duel(RoomRules.SpecialMode.ROULETTE);
        StageBasis passiveFilledOwner=passiveFilled.right();
        passiveFilledOwner.pvpRoulette.gauge=passiveFilledOwner.pvpRoulette.targetGauge=990;
        passiveFilledOwner.pvpRoulette.chargeClock=PvpStageBasis.TPS-1;passiveFilledOwner.pvpRoulette.initializeCharge(passiveFilledOwner);
        passiveFilledOwner.pvpRoulette.advance(passiveFilled,passiveFilledOwner);
        Check.equal(PvpRouletteState.MAX_GAUGE,passiveFilledOwner.pvpRoulette.gauge,"passive charge can finish the gauge");
        Check.that(!passiveFilledOwner.pvpRoulette.castleDamageFastSpinReady,"passive gauge completion never arms the short reel");
        Check.that(passiveFilledOwner.pvpRoulette.press(passiveFilled,passiveFilledOwner),"passive-filled gauge starts roulette");
        Check.equal(PvpRouletteState.AUTO_SPIN_TICKS,passiveFilledOwner.pvpRoulette.spinDurationTicks,"non-castle roulette keeps the normal two-second reel");

        Check.equal(1.5,PvpRouletteState.battlefieldFactor(castlePartialOwner,castlePartialOwner.ownBase().pos),"unit loss at own castle uses 1.5x position factor");
        float middle=(castlePartialOwner.ownBase().pos+castlePartialOwner.playerFor(-castlePartialOwner.ownDirection()).ownBase().pos)/2f;
        Check.equal(0.5,PvpRouletteState.battlefieldFactor(castlePartialOwner,middle),"unit loss at arena center uses 0.5x position factor");
        Check.equal(0.1,PvpRouletteState.battlefieldFactor(castlePartialOwner,castlePartialOwner.playerFor(-castlePartialOwner.ownDirection()).ownBase().pos),"unit loss at enemy castle uses 0.1x position factor");

        PvpStageBasis manual=duel(RoomRules.SpecialMode.ROULETTE);
        StageBasis manualOwner=manual.right();
        manualOwner.pvpRoulette.gauge=PvpRouletteState.MAX_GAUGE;
        manualOwner.pvpRoulette.targetGauge=PvpRouletteState.MAX_GAUGE;
        for(int i=0;i<PvpStageBasis.TPS;i++)manualOwner.pvpRoulette.advance(manual,manualOwner);
        Check.that(!manualOwner.pvpRoulette.spinning,"full roulette gauge never starts without player SPECIAL");
        Check.that(manualOwner.pvpRoulette.press(manual,manualOwner),"SPECIAL starts a full roulette gauge");
        Check.that(manualOwner.pvpRoulette.spinning,"manual SPECIAL starts the visible reel");
        for(int i=0;i<PvpRouletteState.AUTO_SPIN_TICKS-1;i++)manualOwner.pvpRoulette.advance(manual,manualOwner);
        Check.that(manualOwner.pvpRoulette.spinning,"roulette remains visible until the two-second threshold");
        Check.that(!manualOwner.pvpRoulette.press(manual,manualOwner),"repeated SPECIAL cannot skip an active reel");
        int[] manualReel=manualOwner.pvpRoulette.reelSnapshot();
        int attackSlot=0;
        while(manualReel[attackSlot]!=PvpRouletteState.ATTACK_UP)attackSlot++;
        manualOwner.pvpRoulette.reelIndex=(attackSlot-1+manualReel.length)%manualReel.length;
        manualOwner.pvpRoulette.advance(manual,manualOwner);
        Check.that(!manualOwner.pvpRoulette.spinning,"roulette reveals its result after roughly two seconds");
        Check.equal(PvpRouletteState.ATTACK_UP,manualOwner.pvpRoulette.lastResult,"test reel reveals attack-up");
        Check.equal(0,manualOwner.pvpRoulette.attackLevel,"revealed result does not apply while its message is visible");
        Check.equal(PvpRouletteState.RESULT_DISPLAY_TICKS,manualOwner.pvpRoulette.resultDelayTicks,"effect waits for the result-message window");
        for(int i=0;i<PvpRouletteState.RESULT_DISPLAY_TICKS-1;i++)manualOwner.pvpRoulette.advance(manual,manualOwner);
        Check.equal(0,manualOwner.pvpRoulette.attackLevel,"effect remains pending until result message disappears");
        manualOwner.pvpRoulette.advance(manual,manualOwner);
        Check.equal(1,manualOwner.pvpRoulette.attackLevel,"effect activates after the result message disappears");
        Check.equal(-1,manualOwner.pvpRoulette.pendingResult,"pending result clears after activation");
        Check.equal(0,manualOwner.pvpRoulette.gauge,"resolved roulette consumes the full gauge");
    }
    private static void modeTests() throws Exception {
        PvpStageBasis none=duel(RoomRules.SpecialMode.NONE);
        none.left().cannon=none.left().maxCannon;
        none.step(new InputFrame(1,InputFrame.SPECIAL,0));
        Check.equal(0,none.left().cannon,"NONE mode disables special meter/action");

        PvpStageBasis noDebug=duel(RoomRules.SpecialMode.ROULETTE);
        noDebug.left().pvpRoulette.gauge=noDebug.left().pvpRoulette.targetGauge=0;
        noDebug.step(new InputFrame(noDebug.time,InputFrame.DEBUG_ROULETTE_MAX,0));
        Check.equal(0,noDebug.left().pvpRoulette.gauge,"roulette debug command is ignored when host debug mode is disabled");

        Unit dl=Fixture.unit("roulette_debug_l",100000),dr=Fixture.unit("roulette_debug_r",100000);
        RoomRules debugRules=new RoomRules(4400,0,3,false,RoomRules.SpecialMode.ROULETTE,true);
        PvpStageBasis debug=new PvpStageBasis(Fixture.lineup(dl),Fixture.lineup(dr),77881,0,debugRules);
        debug.step(new InputFrame(debug.time,InputFrame.DEBUG_ROULETTE_MAX,0));
        Check.equal(PvpRouletteState.MAX_GAUGE,debug.left().pvpRoulette.gauge,"either participant can fill its own roulette gauge in host-enabled debug mode");

        PvpStageBasis roulette=duel(RoomRules.SpecialMode.ROULETTE);
        roulette.left().pvpRoulette.gauge=roulette.left().pvpRoulette.targetGauge=PvpRouletteState.MAX_GAUGE;
        roulette.step(new InputFrame(1,0,0));
        Check.that(!roulette.left().pvpRoulette.spinning,"ROULETTE does not auto-start at full gauge");
        roulette.step(new InputFrame(2,InputFrame.SPECIAL,0));
        Check.that(roulette.left().pvpRoulette.spinning,"ROULETTE starts only from synchronized SPECIAL input");
    }
    private static PvpStageBasis duel(RoomRules.SpecialMode mode)throws Exception {
        Unit l=Fixture.unit("roulette_l_"+mode,100000),r=Fixture.unit("roulette_r_"+mode,100000);
        RoomRules rules=new RoomRules(4400,0,3,false,mode);
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
