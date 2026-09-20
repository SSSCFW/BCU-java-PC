package online.ui;

import common.pack.Identifier;
import common.util.unit.Trait;
import online.tests.Check;
import online.tests.FixtureNativeUi;

import javax.swing.*;
import java.lang.reflect.Method;

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
    }
    public static void main(String[] args)throws Exception{run();System.out.println("Trait overlay tests passed");}
}
