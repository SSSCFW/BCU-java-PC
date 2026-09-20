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
import online.net.lobby.RoomRules;
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
        defeatRewardTests();
        castleHitMoneyTests();
        terminalPvpTickPreservesUnits();
        postDeploySlotRerollTests();
        rouletteAttackCastleTests();
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
        cannonVisualTests();
    }

    private static void cannonVisualTests() throws Exception {
        PvpStageBasis b=duel(false,false);
        for(StageBasis owner:new StageBasis[]{b.left(),b.right()}) {
            common.util.Data.Proc proc=common.util.Data.Proc.blank();
            proc.WAVE.lv=1;
            AttackCanon source=new AttackCanon(owner.canon,1,new ArrayList<>(),0,proc,0,0,1);
            float p=owner.ownBase().pos;
            ContWaveCanon wave=new ContWaveCanon(new AttackWave(source.attacker,source,p,common.util.Data.NYRAN[0],common.util.Data.WT_CANN|common.util.Data.WT_WAVE),p,0);
            Check.that(animation(wave).anim()==CommonStatic.getBCAssets().atks[0],
                    "basic cannon wave uses the normal pink cannon asset on direction "+owner.ownDirection());
        }
    }
    private static void defeatRewardTests() throws Exception {
    PvpStageBasis leftWins=duel(false,false);
    EUnit left=unit(leftWins,1),right=unit(leftWins,-1);
    StageBasis leftPlayer=leftWins.left(),rightPlayer=leftWins.right();
    leftPlayer.money=0;rightPlayer.money=0;
    int rightCost=rightPlayer.elu.price[right.index[0]][right.index[1]];
    right.lastKilledBy.add((AttackSimple)model(left).getAttack(0));
    right.kill(Entity.KillMode.NORMAL);
    Check.equal(rightCost/2,leftPlayer.money,"left player receives half of defeated unit cost");
    Check.equal(0,rightPlayer.money,"victim side receives no defeat reward");
    int once=leftPlayer.money;
    right.kill(Entity.KillMode.NORMAL);
    Check.equal(once,leftPlayer.money,"one deployed unit pays defeat reward only once");

    PvpStageBasis rightWins=duel(false,false);
    EUnit reverseLeft=unit(rightWins,1),reverseRight=unit(rightWins,-1);
    rightWins.right().money=0;
    int leftCost=rightWins.left().elu.price[reverseLeft.index[0]][reverseLeft.index[1]];
    reverseLeft.lastKilledBy.add((AttackSimple)model(reverseRight).getAttack(0));
    reverseLeft.kill(Entity.KillMode.NORMAL);
    Check.equal(leftCost/2,rightWins.right().money,"right player receives the same half-cost reward");

    PvpStageBasis capped=duel(false,false);
    EUnit cappedLeft=unit(capped,1),cappedRight=unit(capped,-1);
    int cappedCost=capped.left().elu.price[cappedLeft.index[0]][cappedLeft.index[1]];
    capped.right().money=Math.max(0,capped.right().maxMoney-Math.max(1,cappedCost/4));
    cappedLeft.lastKilledBy.add((AttackSimple)model(cappedRight).getAttack(0));
    cappedLeft.kill(Entity.KillMode.NORMAL);
    Check.equal(capped.right().maxMoney,capped.right().money,"defeat reward never exceeds max money");

    PvpStageBasis discounted=duel(false,false);
    EUnit discountedLeft=unit(discounted,1),discountedRight=unit(discounted,-1);
    StageBasis discountedVictim=discounted.right(),discountedWinner=discounted.left();
    int originalBountyCost=discountedVictim.elu.basePrice[discountedRight.index[0]][discountedRight.index[1]];
    for(int i=0;i<4;i++)discountedVictim.pvpRoulette.forceResult(discounted,discountedVictim,PvpRouletteState.COST_DOWN);
    Check.that(discountedVictim.elu.price[discountedRight.index[0]][discountedRight.index[1]]<originalBountyCost,
            "roulette cost-down really lowers future deployment cost");
    discountedWinner.money=0;
    discountedRight.lastKilledBy.add((AttackSimple)model(discountedLeft).getAttack(0));
    discountedRight.kill(Entity.KillMode.NORMAL);
    Check.equal(originalBountyCost/2,discountedWinner.money,
            "defeat reward uses pre-roulette cost and ignores cost-down");

    PvpStageBasis cleanup=duel(false,false);
    EUnit cleanupRight=unit(cleanup,-1);
    cleanup.left().money=0;
    cleanupRight.kill(Entity.KillMode.NORMAL);
    Check.equal(0,cleanup.left().money,"normal cleanup without an opposing attack pays no reward");

    PvpStageBasis selfDestruct=duel(false,false);
    EUnit selfLeft=unit(selfDestruct,1),selfRight=unit(selfDestruct,-1);
    selfDestruct.left().money=0;
    selfRight.lastKilledBy.add((AttackSimple)model(selfLeft).getAttack(0));
    selfRight.kill(Entity.KillMode.SELF_DESTRUCT);
    Check.equal(0,selfDestruct.left().money,"self-destruction pays no defeat reward");
}

private static void castleHitMoneyTests() throws Exception {
    Unit l=Fixture.unit("castle_hit_l",100000),r=Fixture.unit("castle_hit_r",100000);
    RoomRules rules=new RoomRules(4400,0,3,true,RoomRules.SpecialMode.NONE,false,
            online.net.lobby.PvpTraitRules.NONE,online.net.lobby.PvpTraitRules.NONE,0,0,
            RoomRules.DEFAULT_TIME_LIMIT_MINUTES,RoomRules.DEFAULT_MAX_UNITS,true,5);
    PvpStageBasis b=new PvpStageBasis(Fixture.lineup(l),Fixture.lineup(r),9901,0,rules);
    b.left().money=b.right().money=100000;
    b.step(new InputFrame(0,1,1));
    EUnit attacker=unit(b,1);
    StageBasis victim=b.right();
    victim.money=0;
    AttackSimple hit=(AttackSimple)model(attacker).getAttack(0);
    victim.ownBase().damaged(hit);
    Check.equal(500,victim.money,"one successful castle hit grants configured 5 displayed yen");
    Check.equal(5,victim.getMoney(),"castle-hit reward uses the same visible-yen units as the HUD");
    victim.ownBase().damaged((AttackSimple)model(attacker).getAttack(0));
    Check.equal(1000,victim.money,"each separate castle hit grants another configured 5 yen");
    victim.money=Math.max(0,victim.maxMoney-2);
    victim.ownBase().damaged((AttackSimple)model(attacker).getAttack(0));
    Check.equal(victim.maxMoney,victim.money,"castle-hit money never exceeds wallet limit");
}

private static void terminalPvpTickPreservesUnits() throws Exception {
    PvpStageBasis b=duel(false,false);
    List<Entity> before=new ArrayList<>(b.le);
    b.right().ownBase().health=0;
    java.lang.reflect.Method update=StageBasis.class.getDeclaredMethod("update");
    update.setAccessible(true);update.invoke(b);
    for(Entity entity:before)
        Check.that(entity.anim.dead<0,"PvP terminal tick freezes the field instead of mass-killing every unit");
}

private static void postDeploySlotRerollTests() throws Exception {
    Unit a=Fixture.unit("reroll_a",100000),b=Fixture.unit("reroll_b",100000),c=Fixture.unit("reroll_c",100000);
    Unit ra=Fixture.unit("reroll_ra",100000),rb=Fixture.unit("reroll_rb",100000),rc=Fixture.unit("reroll_rc",100000);
    ((CustomUnit)a.forms[0].du).price=10;((CustomUnit)b.forms[0].du).price=25;((CustomUnit)c.forms[0].du).price=40;
    ((CustomUnit)ra.forms[0].du).price=11;((CustomUnit)rb.forms[0].du).price=26;((CustomUnit)rc.forms[0].du).price=41;
    BasisLU left=Fixture.lineup(a),right=Fixture.lineup(ra);
    left.lu.getLv(b.forms[0]);left.lu.getLv(c.forms[0]);right.lu.getLv(rb.forms[0]);right.lu.getLv(rc.forms[0]);
    RoomRules rules=new RoomRules(4400,0,3,false,RoomRules.SpecialMode.NONE,false,
            online.net.lobby.PvpTraitRules.NONE,online.net.lobby.PvpTraitRules.NONE,0,0,
            RoomRules.DEFAULT_TIME_LIMIT_MINUTES,RoomRules.DEFAULT_MAX_UNITS,false,RoomRules.DEFAULT_CASTLE_HIT_MONEY,true);
    PvpStageBasis first=new PvpStageBasis(left,right,445566,0,rules),second=new PvpStageBasis(left,right,445566,0,rules);
    Form[] leftPool={a.forms[0],b.forms[0],c.forms[0]},rightPool={ra.forms[0],rb.forms[0],rc.forms[0]};
    first.configureProductionPools(leftPool,rightPool);second.configureProductionPools(leftPool,rightPool);
    for(PvpStageBasis battle:new PvpStageBasis[]{first,second}){
        battle.left().money=1_000_000;battle.right().money=1_000_000;
        battle.left().unitRespawnTime=0;battle.right().unitRespawnTime=0;
    }
    Form original=first.left().pvpSlotForm(0,0);
    first.step(new InputFrame(0,1,0));second.step(new InputFrame(0,1,0));
    EUnit deployed=(EUnit)first.le.stream().filter(e->e instanceof EUnit&&e.dire==1).findFirst()
            .orElseThrow(()->new AssertionError("reroll fixture did not deploy the original unit"));
    Check.that(deployed.data==original.du,"pressed slot deploys its current character before reroll");
    Form next=first.left().pvpSlotForm(0,0);
    Check.that(!next.unit.id.equals(original.unit.id),"successful production replaces the slot with a different character");
    Check.equal(next.unit.id,second.left().pvpSlotForm(0,0).unit.id,"same seed and input reroll to the same next character");
    EForm nextEForm=new EForm(next,first.left().b.lu.getLv(next));
    int expectedNextPrice=100*(int)nextEForm.getPrice(first.left().st.getCont().price);
    Check.equal(expectedNextPrice,first.left().elu.basePrice[0][0],"rerolled slot recalculates the next character battle price");
    Check.that(first.left().elu.cool[0][0]>0,"rerolled slot begins the next character's production cooldown");
    Check.equal(BattleDigest.of(first),BattleDigest.of(second),"slot reroll state remains deterministic");

    for(PvpStageBasis battle:new PvpStageBasis[]{first,second}){
        battle.left().unitRespawnTime=0;battle.left().elu.cool[0][0]=0;battle.left().money=1_000_000;
    }
    first.step(new InputFrame(1,1,0));second.step(new InputFrame(1,1,0));
    long matching=first.le.stream().filter(e->e instanceof EUnit&&e.dire==1&&e.data==next.du).count();
    Check.equal(1L,matching,"the next press deploys the character that the slot rerolled into");
    Check.that(!first.left().pvpSlotForm(0,0).unit.id.equals(next.unit.id),"the slot rerolls again after every successful production");
    Check.equal(BattleDigest.of(first),BattleDigest.of(second),"repeated slot rerolls remain deterministic");
}

private static void rouletteAttackCastleTests() throws Exception {
    PvpStageBasis normal=duel(false,false),boosted=duel(false,false);
    EUnit normalAttacker=unit(normal,1),boostedAttacker=unit(boosted,1);
    EUnit normalTarget=unit(normal,-1),boostedTarget=unit(boosted,-1);
    StageBasis boostedOwner=boosted.left();
    boostedOwner.pvpRoulette.forceResult(boosted,boostedOwner,PvpRouletteState.ATTACK_UP);

    AttackSimple normalPacket=(AttackSimple)model(normalAttacker).getAttack(0);
    AttackSimple boostedPacket=(AttackSimple)model(boostedAttacker).getAttack(0);
    Check.equal(normalPacket.atk,boostedPacket.atk,
            "roulette attack-up no longer changes the raw attack packet used by castles");

    long normalCastleBefore=normal.right().ownBase().health;
    long boostedCastleBefore=boosted.right().ownBase().health;
    normal.right().ownBase().damaged((AttackSimple)model(normalAttacker).getAttack(0));
    boosted.right().ownBase().damaged((AttackSimple)model(boostedAttacker).getAttack(0));
    long normalCastleDamage=normalCastleBefore-normal.right().ownBase().health;
    long boostedCastleDamage=boostedCastleBefore-boosted.right().ownBase().health;
    Check.equal(normalCastleDamage,boostedCastleDamage,
            "roulette attack-up does not increase castle damage");

    long normalUnitBefore=normalTarget.health,boostedUnitBefore=boostedTarget.health;
    normalTarget.damaged((AttackSimple)model(normalAttacker).getAttack(0));normalTarget.postUpdate();
    boostedTarget.damaged((AttackSimple)model(boostedAttacker).getAttack(0));boostedTarget.postUpdate();
    long normalUnitDamage=normalUnitBefore-normalTarget.health;
    long boostedUnitDamage=boostedUnitBefore-boostedTarget.health;
    Check.that(normalUnitDamage>0&&boostedUnitDamage>normalUnitDamage*2.5,
            "roulette attack-up still boosts damage dealt to opposing units");
}

private static EUnit unit(PvpStageBasis b,int direction){return (EUnit)b.le.stream().filter(e->e.dire==direction).findFirst().get();}

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
