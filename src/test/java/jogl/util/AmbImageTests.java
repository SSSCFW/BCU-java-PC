package jogl.util;

import common.system.fake.FakeImage;
import online.tests.Check;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

public final class AmbImageTests {
    public static void run() {
        AmbImage missing=new AmbImage((BufferedImage)null);
        missing.mark(FakeImage.Marker.UNI);
        Check.equal(null,missing.gl(),"missing forced BI returns no GL image instead of throwing");
        Check.equal(1,missing.getWidth(),"missing forced BI has safe fallback width");
        Check.equal(1,missing.getHeight(),"missing forced BI has safe fallback height");

        AmbImage invalid=new AmbImage(()->new ByteArrayInputStream(new byte[]{1,2,3,4,5}));
        invalid.mark(FakeImage.Marker.UNI);
        Check.equal(null,invalid.gl(),"invalid deploy icon cannot crash GL conversion");
        Check.equal(null,invalid.bimg(),"invalid image exposes no AWT backing image");
        Check.equal(1,invalid.getWidth(),"invalid image remains safe for layout width queries");
        Check.equal(1,invalid.getHeight(),"invalid image remains safe for layout height queries");
    }
    public static void main(String[] args){run();System.out.println("AmbImage tests passed");}
}
