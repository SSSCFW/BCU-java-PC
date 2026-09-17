package online.tests;

import common.battle.PvpStageBasis;
import online.sync.BattleDigest;
import online.ui.PvpCanvas;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

public final class UiTests {
    public static void run() throws Exception {
        PvpStageBasis battle=CombatTests.duel(true,false);
        String before=BattleDigest.of(battle);
        BufferedImage image=new BufferedImage(1100,560,BufferedImage.TYPE_INT_ARGB);
        SwingUtilities.invokeAndWait(() -> {
            PvpCanvas canvas=new PvpCanvas();canvas.setSize(1100,560);
            canvas.names("Left / blue", "Right / pink");canvas.fps(60);
            canvas.snapshot(battle.displayCopy());canvas.renderFrame();
            Graphics2D g=image.createGraphics();try{canvas.paint(g);}finally{g.dispose();}
        });
        Check.equal(before,BattleDigest.of(battle),"painting a snapshot cannot mutate the live battle");
        Check.that(image.getRGB(10,10)!=image.getRGB(10,550),"canvas paints a complete battlefield");
        Path path=Paths.get("target","pvp-canvas-test.png");Files.createDirectories(path.getParent());
        ImageIO.write(image,"png",path.toFile());
    }
}
