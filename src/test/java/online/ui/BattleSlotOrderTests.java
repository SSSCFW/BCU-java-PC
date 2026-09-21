package online.ui;

import common.CommonStatic;
import common.battle.BasisLU;
import common.battle.PvpStageBasis;
import common.battle.StageBasis;
import common.battle.entity.EUnit;
import common.util.unit.Unit;
import online.sync.BattleDigest;
import online.sync.InputFrame;
import online.tests.Check;
import online.tests.Fixture;
import online.tests.FixtureAssets;

import java.util.concurrent.atomic.AtomicInteger;

public final class BattleSlotOrderTests {
    public static void run() throws Exception {
        FixtureAssets.init();
        Unit a=Fixture.unit("slot_order_a",100000);
        Unit b=Fixture.unit("slot_order_b",100000);
        Unit opponent=Fixture.unit("slot_order_enemy",100000);
        BasisLU left=Fixture.lineup(a);
        left.lu.fs[0][1]=b.forms[0];left.lu.getLv(b.forms[0]);left.lu.renew();
        PvpStageBasis canonical=new PvpStageBasis(left,Fixture.lineup(opponent),77123,0);
        canonical.left().elu.price[0][0]=111;canonical.left().elu.price[0][1]=222;
        canonical.left().elu.cool[0][0]=11;canonical.left().elu.cool[0][1]=22;

        String before=BattleDigest.of(canonical);
        AtomicInteger sent=new AtomicInteger();
        CommonStatic.FakeKey keys=new CommonStatic.FakeKey(){
            public boolean pressed(int i,int j){return false;}
            public void remove(int i,int j){}
        };
        OnlineBattleField field=new OnlineBattleField(keys,canonical.displayCopy(),1,sent::set);

        Check.equal(a.forms[0],field.visibleForm(0),"slot order begins canonical");
        Check.equal(b.forms[0],field.visibleForm(1),"second slot begins canonical");
        Check.that(field.swapVisibleSlots(0,1),"middle-drag slot swap is accepted while interactive");
        Check.that(InputFrame.hasSlotSwap(sent.get()),"slot reorder emits a synchronized lockstep command");
        Check.equal(0,InputFrame.slotSwapFrom(sent.get()),"slot reorder command keeps the visible source index");
        Check.equal(1,InputFrame.slotSwapTo(sent.get()),"slot reorder command keeps the visible destination index");
        Check.equal(1,field.canonicalSlotForVisible(0),"visible slot zero maps to canonical slot one after swap");
        Check.equal(0,field.canonicalSlotForVisible(1),"visible slot one maps to canonical slot zero after swap");
        Check.equal(b.forms[0],field.visibleForm(0),"local display form lookup swaps without mutating shared BasisLU");
        Check.equal(a.forms[0],field.playerState().b.lu.fs[0][0],"shared canonical BasisLU remains immutable");
        Check.equal(222,field.playerState().elu.price[0][0],"slot HUD values swap with the form");
        Check.equal(before,BattleDigest.of(canonical),"local slot reorder never mutates canonical lockstep state");

        field.action.add(0);field.update();
        Check.equal(1<<1,sent.get(),"visible production command is translated to canonical slot bit");
        sent.set(0);field.action.add(0);field.action.add(10);field.update();
        Check.equal(1<<(12+1),sent.get(),"visible lock command is translated to canonical slot bit");

        canonical.left().elu.price[0][0]=333;canonical.left().elu.price[0][1]=444;
        canonical.left().elu.cool[0][0]=33;canonical.left().elu.cool[0][1]=44;
        field.publish(canonical.displayCopy());
        Check.equal(b.forms[0],field.visibleForm(0),"full authoritative snapshot preserves local slot order");
        Check.equal(444,field.playerState().elu.price[0][0],"full snapshot slot HUD is permuted into visible order");

        canonical.left().elu.cool[0][0]=55;canonical.left().elu.cool[0][1]=66;
        field.applyDelta(PvpPresentationDelta.capture(canonical));
        Check.equal(66,field.playerState().elu.cool[0][0],"30TPS presentation delta respects local slot permutation");
        Check.equal(55,field.playerState().elu.cool[0][1],"delta maps both swapped slots correctly");

        autoProductionFollowsVisibleOrder();

        field.setBattleUiHidden(true);
        Check.that(!field.swapVisibleSlots(0,1),"slot reorder is disabled after battle ending starts");
    }

    private static void autoProductionFollowsVisibleOrder() throws Exception {
        Unit first=Fixture.unit("slot_auto_first",100000);
        Unit second=Fixture.unit("slot_auto_second",100000);
        Unit enemy=Fixture.unit("slot_auto_enemy",100000);
        BasisLU left=Fixture.lineup(first);
        left.lu.fs[0][1]=second.forms[0];left.lu.getLv(second.forms[0]);left.lu.renew();
        PvpStageBasis battle=new PvpStageBasis(left,Fixture.lineup(enemy),77124,0);
        StageBasis own=battle.left();
        own.money=1_000_000;own.unitRespawnTime=0;
        own.elu.cool[0][0]=0;own.elu.cool[0][1]=0;
        int lockBoth=(1<<(12+0))|(1<<(12+1));
        int reordered=lockBoth|InputFrame.slotSwap(0,1);
        Check.that(InputFrame.valid(reordered),"slot reorder can share a frame with normal PvP commands");
        battle.step(new InputFrame(0,reordered,0));
        EUnit deployed=(EUnit)battle.le.stream()
                .filter(e->e instanceof EUnit&&e.dire==1)
                .findFirst().orElseThrow(()->new AssertionError("auto-production did not deploy a unit"));
        Check.that(deployed.data==second.forms[0].du,"auto-production prioritizes the character moved into the first visible slot");
        Check.equal(1,own.pvpAutoSlotOrder[0],"canonical auto order tracks the first visible slot");
        Check.equal(0,own.pvpAutoSlotOrder[1],"canonical auto order tracks the displaced slot");
    }
    public static void main(String[] args)throws Exception{run();System.out.println("Battle slot order tests passed");}
}
