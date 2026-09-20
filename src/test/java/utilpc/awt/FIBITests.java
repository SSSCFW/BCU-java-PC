package utilpc.awt;

import online.tests.Check;

import java.awt.image.BufferedImage;

public final class FIBITests {
    public static void run() {
        FIBI missing=new FIBI(null);
        Check.that(!missing.isValid(),"null-backed FIBI reports invalid");
        Check.equal(1,missing.getWidth(),"null-backed FIBI width is safe");
        Check.equal(1,missing.getHeight(),"null-backed FIBI height is safe");
        Check.equal(0,missing.getRGB(0,0),"null-backed FIBI pixel lookup is safe");
        Check.equal(null,missing.getGraphics(),"null-backed FIBI has no graphics instead of throwing");
        Check.that(!missing.getSubimage(0,0,1,1).isValid(),"null-backed FIBI subimage remains safely invalid");

        BufferedImage target=new BufferedImage(8,8,BufferedImage.TYPE_INT_ARGB_PRE);
        FG2D graphics=new FG2D(target.getGraphics());
        graphics.drawImage(missing,0,0);
        graphics.drawImage(missing,0,0,8,8);
        graphics.dispose();
        Check.that(true,"Java2D skips null-backed images without throwing");
    }
    public static void main(String[] args){run();System.out.println("FIBI tests passed");}
}
