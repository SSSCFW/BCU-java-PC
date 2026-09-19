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
        Check.equal(1.0,right.pvpRoulette.hpMultiplier(),"unboosted HP remains 1x");
        right.pvpRoulette.forceResult(b,right,PvpRouletteState.HP_UP);
        Check.equal(4.5,right.pvpRoulette.hpMultiplier(),"HP Lv1 is 4.5x");
        right.pvpRoulette.forceResult(b,right,PvpRouletteState.HP_UP);
        Check.equal(7.5,right.pvpRoulette.hpMultiplier(),"HP Lv2 is 7.5x");
        right.pvpRoulette.forceResult(b,right,PvpRouletteState.HP_UP);
        Check.equal(13.5,right.pvpRoulette.hpMultiplier(),"HP Lv3 is 13.5x");
        right.pvpRoulette.forceResult(b,right,PvpRouletteState.HP_UP);
        Check.equal(24.0,right.pvpRoulette.hpMultiplier(),"HP MAX is 24x");
        right.pvpRoulette.forceResult(b,right,PvpRouletteState.HP_UP);
        Check.equal(4,right.pvpRoulette.hpLevel,"HP boost caps at Level MAX");
        Check.equal(24.0,right.pvpRoulette.hpMultiplier(),"HP Level MAX is triple the former 8x multiplier");
        Check.equal(Math.round(oldMax*24.0),enemy.maxH,"HP Level MAX is now 24x");
        Check.equal(Math.round(oldHealth*24.0),enemy.health,"HP Level MAX immediately scales deployed unit current HP to 24x");

        int baseDisplayedAtk=enemy.getAtk();
        Check.equal(1.0,right.pvpRoulette.attackMultiplier(),"unboosted attack remains 1x");
        right.pvpRoulette.forceResult(b,right,PvpRouletteState.ATTACK_UP);
        Check.equal(2.7,right.pvpRoulette.attackMultiplier(),"attack Lv1 is 1.8x the former 1.5x multiplier");
        Check.equal((int)Math.round(baseDisplayedAtk*2.7),enemy.getAtk(),"attack-up immediately changes deployed-unit status attack to 2.7x");
        right.pvpRoulette.forceResult(b,right,PvpRouletteState.ATTACK_UP);
        Check.equal(4.5,right.pvpRoulette.attackMultiplier(),"attack Lv2 is 4.5x");
        right.pvpRoulette.forceResult(b,right,PvpRouletteState.ATTACK_UP);
        Check.equal(8.1,right.pvpRoulette.attackMultiplier(),"attack Lv3 is 8.1x");
        right.pvpRoulette.forceResult(b,right,PvpRouletteState.ATTACK_UP);
        Check.equal(14.4,right.pvpRoulette.attackMultiplier(),"attack MAX is 14.4x");
        right.pvpRoulette.forceResult(b,right,PvpRouletteState.ATTACK_UP);
        Check.equal(4,right.pvpRoulette.attackLevel,"attack boost caps at Level MAX");
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

        boostedCastleDamageEffects();

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
        Check.equal(390,castlePartialOwner.pvpRoulette.targetGauge,"castle-damage roulette charge is 1.3x the recovered native amount");
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
        Check.that(castleFilledOwner.pvpRoulette.castleDamageBoostedSpin,
                "shortened castle-damage reel is marked for boosted effects");
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

        // Charge continues at 100%, during the reel, and while the result card is visible.
        PvpStageBasis banked=duel(RoomRules.SpecialMode.ROULETTE);
        StageBasis bankedOwner=banked.right();
        bankedOwner.pvpRoulette.gauge=bankedOwner.pvpRoulette.targetGauge=PvpRouletteState.MAX_GAUGE;
        bankedOwner.pvpRoulette.chargeClock=PvpStageBasis.TPS-1;
        bankedOwner.pvpRoulette.initializeCharge(bankedOwner);
        bankedOwner.pvpRoulette.advance(banked,bankedOwner);
        Check.that(bankedOwner.pvpRoulette.targetGauge>PvpRouletteState.MAX_GAUGE,
                "roulette banks charge even while the visible gauge is already MAX");
        Check.that(bankedOwner.pvpRoulette.press(banked,bankedOwner),"banked full gauge starts normally");
        int duringSpin=bankedOwner.pvpRoulette.targetGauge;
        bankedOwner.pvpRoulette.chargeClock=PvpStageBasis.TPS-1;
        bankedOwner.pvpRoulette.advance(banked,bankedOwner);
        Check.that(bankedOwner.pvpRoulette.targetGauge>duringSpin,
                "roulette continues banking charge during the spinning presentation");

        PvpStageBasis capped=duel(RoomRules.SpecialMode.ROULETTE);
        StageBasis cappedOwner=capped.right();
        cappedOwner.pvpRoulette.gauge=0;
        cappedOwner.pvpRoulette.targetGauge=PvpRouletteState.MAX_STORED_GAUGE-5;
        cappedOwner.pvpRoulette.chargeClock=PvpStageBasis.TPS-1;
        cappedOwner.pvpRoulette.initializeCharge(cappedOwner);
        cappedOwner.pvpRoulette.advance(capped,cappedOwner);
        Check.equal(PvpRouletteState.MAX_STORED_GAUGE,cappedOwner.pvpRoulette.targetGauge,
                "roulette internal stock caps at exactly 500 percent");
        for(int i=0;i<PvpStageBasis.TPS;i++)cappedOwner.pvpRoulette.advance(capped,cappedOwner);
        Check.equal(PvpRouletteState.MAX_STORED_GAUGE,cappedOwner.pvpRoulette.targetGauge,
                "roulette cannot bank passive charge beyond 500 percent");

        PvpStageBasis instant=duel(RoomRules.SpecialMode.ROULETTE);
        StageBasis instantOwner=instant.right();
        instantOwner.pvpRoulette.gauge=PvpRouletteState.MAX_GAUGE;
        instantOwner.pvpRoulette.targetGauge=PvpRouletteState.MAX_GAUGE*2;
        Check.that(instantOwner.pvpRoulette.press(instant,instantOwner),"first spin starts with another full spin banked");
        instantOwner.pvpRoulette.spinDurationTicks=1;
        instantOwner.pvpRoulette.advance(instant,instantOwner);
        Check.equal(PvpRouletteState.MAX_GAUGE,instantOwner.pvpRoulette.gauge,
                "finishing a spin immediately exposes a banked full next gauge");
        Check.that(instantOwner.pvpRoulette.pendingResult>=0,"result card is still visible after reel stop");
        Check.that(!instantOwner.pvpRoulette.press(instant,instantOwner),
                "banked full gauge cannot interrupt the previous roulette result presentation");
        for(int i=0;i<PvpRouletteState.RESULT_DISPLAY_TICKS;i++)
            instantOwner.pvpRoulette.advance(instant,instantOwner);
        Check.equal(-1,instantOwner.pvpRoulette.pendingResult,"result presentation fully disappears before repeat delay");
        Check.equal(PvpRouletteState.REPEAT_DELAY_TICKS,instantOwner.pvpRoulette.repeatDelayTicks,
                "disappearing result starts a fixed 0.5-second repeat delay");
        Check.that(!instantOwner.pvpRoulette.press(instant,instantOwner),
                "next roulette cannot begin at the start of the repeat delay");
        for(int i=0;i<PvpRouletteState.REPEAT_DELAY_TICKS-1;i++)
            instantOwner.pvpRoulette.advance(instant,instantOwner);
        Check.equal(1,instantOwner.pvpRoulette.repeatDelayTicks,
                "repeat remains locked until the full half-second has elapsed");
        Check.that(!instantOwner.pvpRoulette.press(instant,instantOwner),
                "manual input cannot skip the final repeat-delay tick");
        instantOwner.pvpRoulette.advance(instant,instantOwner);
        Check.equal(0,instantOwner.pvpRoulette.repeatDelayTicks,
                "repeat delay expires after exactly 15 ticks at 30 TPS");
        Check.that(instantOwner.pvpRoulette.canPress(),
                "banked full gauge becomes ready only after result disappearance plus 0.5 seconds");
        Check.that(instantOwner.pvpRoulette.press(instant,instantOwner),
                "next roulette starts after the full repeat gap");

        PvpStageBasis manual=duel(RoomRules.SpecialMode.ROULETTE);
        StageBasis manualOwner=manual.right();
        manualOwner.pvpRoulette.gauge=PvpRouletteState.MAX_GAUGE;
        manualOwner.pvpRoulette.targetGauge=PvpRouletteState.MAX_GAUGE;
        for(int i=0;i<PvpStageBasis.TPS;i++)manualOwner.pvpRoulette.advance(manual,manualOwner);
        Check.that(manualOwner.pvpRoulette.targetGauge>PvpRouletteState.MAX_GAUGE,
                "passive charge is banked while waiting at MAX");
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
        Check.equal(0,manualOwner.pvpRoulette.attackLevel,"roulette effect stays unapplied while the result animation is visible");
        Check.equal(1,manualOwner.pvpRoulette.lastLevel,"result animation previews the level that will activate afterward");
        Check.equal(PvpRouletteState.RESULT_DISPLAY_TICKS,manualOwner.pvpRoulette.resultDelayTicks,"result card keeps its normal display window");
        int pendingTarget=manualOwner.pvpRoulette.targetGauge;
        manualOwner.pvpRoulette.chargeClock=PvpStageBasis.TPS-1;
        manualOwner.pvpRoulette.advance(manual,manualOwner);
        Check.that(manualOwner.pvpRoulette.targetGauge>pendingTarget,"gauge continues charging while result presentation is visible");
        for(int i=1;i<PvpRouletteState.RESULT_DISPLAY_TICKS-1;i++)manualOwner.pvpRoulette.advance(manual,manualOwner);
        Check.equal(0,manualOwner.pvpRoulette.attackLevel,"effect remains unapplied through the final visible result tick");
        Check.that(manualOwner.pvpRoulette.pendingResult>=0,"result is still pending immediately before the cut-in disappears");
        manualOwner.pvpRoulette.advance(manual,manualOwner);
        Check.equal(1,manualOwner.pvpRoulette.attackLevel,"roulette effect activates on the tick after the result animation disappears");
        Check.equal(-1,manualOwner.pvpRoulette.pendingResult,"pending result card clears when the delayed effect activates");
        Check.equal(PvpRouletteState.REPEAT_DELAY_TICKS,manualOwner.pvpRoulette.repeatDelayTicks,
                "normal result disappearance also starts the synchronized repeat gap");
        Check.that(manualOwner.pvpRoulette.gauge>0,"charge earned during spin/result is retained after consuming one full gauge");
    }
    private static void boostedCastleDamageEffects() throws Exception {
        // STOP: twice the normal five-second duration.
        PvpStageBasis stopBattle=duel(RoomRules.SpecialMode.ROULETTE);
        StageBasis stopOwner=stopBattle.left();
        EUnit stopTarget=unit(stopBattle,-1);
        stopOwner.pvpRoulette.forceResult(stopBattle,stopOwner,PvpRouletteState.STOP,true);
        Check.equal(PvpRouletteState.TEMP_TICKS*2,stopTarget.status[common.util.Data.P_STOP][0],
                "castle-damage STOP lasts twice as long");

        // SLOW: triple duration plus 50% attack for the exact same duration.
        PvpStageBasis slowBattle=duel(RoomRules.SpecialMode.ROULETTE);
        StageBasis slowOwner=slowBattle.left();
        EUnit slowTarget=unit(slowBattle,-1);
        int attackBefore=slowTarget.getAtk();
        slowOwner.pvpRoulette.forceResult(slowBattle,slowOwner,PvpRouletteState.SLOW,true);
        Check.equal(PvpRouletteState.TEMP_TICKS*3,slowTarget.status[common.util.Data.P_SLOW][0],
                "castle-damage SLOW lasts three times as long");
        Check.equal(PvpRouletteState.TEMP_TICKS*3,slowTarget.status[common.util.Data.P_WEAK][0],
                "castle-damage SLOW gives attack-down for the same duration");
        Check.equal(50,slowTarget.status[common.util.Data.P_WEAK][1],
                "castle-damage SLOW reduces attack to 50 percent");
        Check.equal(attackBefore/2,slowTarget.getAtk(),"castle-damage SLOW immediately halves displayed attack");

        // CANNON: the shot produced by this roulette activation carries a one-shot x10 attack multiplier.
        PvpStageBasis cannonBattle=duel(RoomRules.SpecialMode.ROULETTE);
        StageBasis cannonOwner=cannonBattle.left();
        cannonOwner.pvpRoulette.forceResult(cannonBattle,cannonOwner,PvpRouletteState.CANNON,true);
        Check.equal(10,cannonOwner.canon.pendingPvpAttackMultiplier(),
                "castle-damage cannon queues a ten-times attack multiplier");

        // KNOCKBACK: compare equal fixtures at midfield to avoid castle-edge clamping.
        PvpStageBasis normalKb=duel(RoomRules.SpecialMode.ROULETTE),boostedKb=duel(RoomRules.SpecialMode.ROULETTE);
        EUnit normalKbTarget=unit(normalKb,-1),boostedKbTarget=unit(boostedKb,-1);
        normalKbTarget.pos=normalKbTarget.lastPosition=normalKb.st.len/2f;
        boostedKbTarget.pos=boostedKbTarget.lastPosition=boostedKb.st.len/2f;
        float normalStart=normalKbTarget.pos,boostedStart=boostedKbTarget.pos;
        normalKb.left().pvpRoulette.forceResult(normalKb,normalKb.left(),PvpRouletteState.KNOCKBACK,false);
        boostedKb.left().pvpRoulette.forceResult(boostedKb,boostedKb.left(),PvpRouletteState.KNOCKBACK,true);
        normalKb.step(new InputFrame(normalKb.time,0,0));boostedKb.step(new InputFrame(boostedKb.time,0,0));
        float normalMove=Math.abs(normalKbTarget.pos-normalStart),boostedMove=Math.abs(boostedKbTarget.pos-boostedStart);
        Check.that(normalMove>0&&boostedMove>=normalMove*1.9f,
                "castle-damage knockback travels approximately twice the normal boss-shock distance");

        // Petit baby rush: keep the normal rush and immediately max worker wallet and cash.
        PvpStageBasis babyBattle=duel(RoomRules.SpecialMode.ROULETTE);
        StageBasis babyOwner=babyBattle.right();
        babyOwner.work_lv=1;babyOwner.money=0;
        babyOwner.pvpRoulette.forceResult(babyBattle,babyOwner,PvpRouletteState.BABY_RUSH,true);
        Check.equal(8,babyOwner.work_lv,"castle-damage petit baby rush maxes worker level");
        Check.equal(babyOwner.maxMoney,babyOwner.money,"castle-damage petit baby rush also fills the new max wallet");
        Check.equal(PvpRouletteState.BABY_RUSH_TICKS,babyOwner.pvpRoulette.babyRushTicks,
                "castle-damage petit baby rush keeps the normal ten-second production rush");

        // Production recovery: normal cooldown reset plus 4500 visible yen, capped normally.
        PvpStageBasis recoveryBattle=duel(RoomRules.SpecialMode.ROULETTE);
        StageBasis recoveryOwner=recoveryBattle.right();
        recoveryOwner.elu.cool[0][0]=77;recoveryOwner.money=0;
        recoveryOwner.pvpRoulette.forceResult(recoveryBattle,recoveryOwner,PvpRouletteState.PRODUCTION_RECOVERY,true);
        Check.equal(0,recoveryOwner.elu.cool[0][0],"castle-damage production recovery still clears cooldown");
        Check.equal((int)Math.min((long)recoveryOwner.maxMoney,4500L*100L),recoveryOwner.money,
                "castle-damage production recovery grants 4500 visible yen without exceeding wallet cap");

        // Cost down: price reduction plus 30% progress on every currently-running cooldown.
        PvpStageBasis costBattle=duel(RoomRules.SpecialMode.ROULETTE);
        StageBasis costOwner=costBattle.right();
        int priceBefore=costOwner.elu.price[0][0];
        costOwner.elu.cool[0][0]=100;
        costOwner.pvpRoulette.forceResult(costBattle,costOwner,PvpRouletteState.COST_DOWN,true);
        Check.equal(Math.max(1,priceBefore/2),costOwner.elu.price[0][0],"castle-damage cost-down keeps normal price halving");
        Check.equal(70,costOwner.elu.cool[0][0],"castle-damage cost-down advances current cooldown by 30 percent");

        // Heal: existing heal plus a five-second x2 max-HP window for newly deployed cats.
        PvpStageBasis healBattle=duel(RoomRules.SpecialMode.ROULETTE);
        StageBasis healOwner=healBattle.left();
        EUnit beforeSpawn=unit(healBattle,1);
        long baseSpawnHp=beforeSpawn.maxH;
        healOwner.pvpRoulette.forceResult(healBattle,healOwner,PvpRouletteState.HEAL,true);
        Check.equal(PvpRouletteState.BOOSTED_HEAL_SPAWN_TICKS,healOwner.pvpRoulette.healSpawnBoostTicks,
                "castle-damage heal opens a five-second spawn HP window");
        healOwner.unitRespawnTime=0;healOwner.elu.cool[0][0]=0;healOwner.money=healOwner.maxMoney;
        healBattle.step(new InputFrame(healBattle.time,1,0));
        Check.that(healBattle.le.stream().filter(e->e instanceof EUnit&&e.dire==1&&e!=beforeSpawn)
                        .map(e->(EUnit)e).anyMatch(e->e.maxH==baseSpawnHp*2),
                "cats deployed during boosted-heal window spawn with double HP");

        // Money MAX: add one full current wallet maximum and preserve only that one-time over-cap.
        PvpStageBasis moneyBattle=duel(RoomRules.SpecialMode.ROULETTE);
        StageBasis moneyOwner=moneyBattle.right();
        moneyOwner.money=Math.max(1,moneyOwner.maxMoney/3);
        int beforeMoney=moneyOwner.money,maximum=moneyOwner.maxMoney;
        moneyOwner.pvpRoulette.forceResult(moneyBattle,moneyOwner,PvpRouletteState.MONEY_MAX,true);
        Check.equal((int)Math.min((long)Integer.MAX_VALUE,(long)beforeMoney+maximum),moneyOwner.money,
                "castle-damage money MAX adds the current wallet maximum instead of merely filling it");
        Check.that(moneyOwner.money>maximum&&moneyOwner.pvpMoneyOvercapLimit==moneyOwner.money,
                "castle-damage money MAX may exceed the normal wallet limit");
        moneyOwner.clampMoney();
        Check.that(moneyOwner.money>maximum,"roulette over-cap survives the normal end-of-tick clamp");
        moneyOwner.money=maximum-1;moneyOwner.clampMoney();
        Check.equal(0,moneyOwner.pvpMoneyOvercapLimit,"over-cap privilege ends once money falls back under normal maximum");
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
