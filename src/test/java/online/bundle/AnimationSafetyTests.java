package online.bundle;

import online.tests.Check;
import online.tests.Fixture;
import common.pack.Source;
import java.util.Collections;

public final class AnimationSafetyTests {
    public static void run() throws Exception {
        Fixture.init();
        MatchSource source=new MatchSource("test-network-source",Collections.emptyMap());
        Check.rejects(()->source.loadAnimation("missing",Source.BasePath.ANIM).check(),"missing network animation rejects without native save/error recovery");
    }
    public static void main(String[] args)throws Exception{run();}
}
