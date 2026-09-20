package online.bundle;

import online.tests.Check;
import online.tests.Fixture;
import common.battle.entity.EAnimCont;
import common.pack.Source;
import java.util.Collections;

public final class AnimationSafetyTests {
    public static void run() throws Exception {
        Fixture.init();
        MatchSource source=new MatchSource("test-network-source",Collections.emptyMap());
        Check.rejects(()->source.loadAnimation("missing",Source.BasePath.ANIM).check(),"missing network animation rejects without native save/error recovery");

        EAnimCont missingEffect=new EAnimCont(0f,0,null);
        missingEffect.update();
        Check.that(missingEffect.done(),"missing optional battle effect is discarded instead of stopping PvP sync");
    }
    public static void main(String[] args)throws Exception{run();}
}
