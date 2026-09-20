package online.ui;

import common.battle.*;
import common.battle.data.CustomUnit;
import common.battle.entity.EUnit;
import common.pack.Identifier;
import common.util.unit.*;
import online.net.lobby.RoomRules;
import online.tests.Check;
import online.tests.Fixture;
import online.tests.FixtureNativeUi;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class TraitOverlayTests {
    public static void run() throws Exception {
        FixtureNativeUi.init();
        Trait trait=new Trait(new Identifier<>("custom_trait_pack",Trait.class,3));
        trait.name="new trait";
        trait.icon=FixtureNativeUi.image(32,32,0xff44aaee);

        Method chip=PvpUnitAbilityOverlay.class.getDeclaredMethod("traitChip",Trait.class);
        chip.setAccessible(true);
        JLabel label=(JLabel)chip.invoke(null,trait);

        Check.equal("",label.getText(),"target trait chip exposes no pack/id/debug text");
        Check.that(label.getIcon()!=null,"target trait chip keeps the trait icon");
        Check.equal("custom_trait_pack/3 - new trait",label.getToolTipText(),"full trait name is diagnostic tooltip only");

        Unit left=Fixture.unit("detail_stats_left",1200),right=Fixture.unit("detail_stats_right",1200);
        CustomUnit data=(CustomUnit)left.forms[0].du;
        data.speed=12;data.atks[0].atk=200;
        BasisLU leftLu=Fixture.lineup(left),rightLu=Fixture.lineup(right);
        PvpStageBasis battle=new PvpStageBasis(leftLu,rightLu,77111,0,
                new RoomRules(4400,0,3,false,RoomRules.SpecialMode.ROULETTE));
        StageBasis owner=battle.left();Form form=left.forms[0];Level level=owner.b.lu.getLv(form);

        EUnit base=new EForm(form,level).invokeEntity(owner,level.getLv()+level.getPlusLv(),0,0);
        long baseHp=base.maxH;int baseAtk=base.getAtk();double baseSpeed=base.displayMoveSpeed();
        PvpUnitAbilityOverlay overlay=new PvpUnitAbilityOverlay();
        overlay.show(form,owner);
        List<String> before=texts(overlay);
        Check.that(before.contains("HP "+format(baseHp)),"hold details show current HP stat");
        Check.that(before.contains("攻撃力 "+format(baseAtk)),"hold details show current attack stat");
        Check.that(before.contains("移動速度 "+format(baseSpeed)),"hold details show current movement stat");

        owner.pvpRoulette.forceResult(battle,owner,PvpRouletteState.HP_UP);
        owner.pvpRoulette.forceResult(battle,owner,PvpRouletteState.ATTACK_UP);
        owner.pvpRoulette.forceResult(battle,owner,PvpRouletteState.MOVE_UP);
        EUnit boosted=new EForm(form,level).invokeEntity(owner,level.getLv()+level.getPlusLv(),0,0);
        overlay.show(form,owner);
        List<String> after=texts(overlay);
        Check.equal(Math.round(baseHp*4.5),boosted.maxH,"preview HP includes roulette Lv1 multiplier");
        Check.equal((int)Math.round(baseAtk*2.7),boosted.getAtk(),"preview attack includes roulette Lv1 multiplier");
        Check.equal(baseSpeed*1.5,boosted.displayMoveSpeed(),"preview speed includes roulette Lv1 multiplier");
        Check.that(after.contains("HP "+format(boosted.maxH)),"hold details refresh roulette-adjusted HP");
        Check.that(after.contains("攻撃力 "+format(boosted.getAtk())),"hold details refresh roulette-adjusted attack");
        Check.that(after.contains("移動速度 "+format(boosted.displayMoveSpeed())),"hold details refresh roulette-adjusted movement");
    }
    private static List<String> texts(Component root){
        List<String> out=new ArrayList<>();
        if(root instanceof JLabel)out.add(((JLabel)root).getText());
        if(root instanceof Container)for(Component child:((Container)root).getComponents())out.addAll(texts(child));
        return out;
    }
    private static String format(long value){return String.format(java.util.Locale.ROOT,"%,d",value);}
    private static String format(double value){
        if(Math.abs(value-Math.rint(value))<1e-9)return format(Math.round(value));
        return String.format(java.util.Locale.ROOT,"%,.1f",value);
    }
    public static void main(String[] args)throws Exception{run();System.out.println("Trait overlay tests passed");}
}
