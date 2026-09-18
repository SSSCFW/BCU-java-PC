package online.tests;

import common.CommonStatic;
import common.pack.Source;
import common.pack.Source.AnimLoader;
import common.system.VImg;
import common.system.fake.*;
import common.util.anim.*;
import common.util.unit.*;
import java.util.*;

/** Original renderer test textures only; no copyrighted game artwork. */
public final class FixtureNativeUi {
    public static void init() throws Exception {
        FixtureAssets.init();
        CommonStatic.BCAuxAssets a = CommonStatic.getBCAssets();
        // Native BasisPage expects the default combo-category catalogue to be loaded.
        a.filter = new int[32][0];
        ImgCut backgroundCut = new ImgCut(); backgroundCut.cuts[0] = new int[]{0, 0, 512, 256};
        while (a.iclist.size() < 2) a.iclist.add(backgroundCut);
        common.util.pack.Background bg = new common.util.pack.Background(
                new common.pack.Identifier<>("000000", common.util.pack.Background.class, 0), image(512, 256, 0xffaaccee));
        common.pack.UserProfile.getBCData().bgs.set(0, bg);
        for (int row = 0; row < a.num.length; row++) for (int i = 0; i < a.num[row].length; i++)
            a.num[row][i] = image(14, 20, 0xffdddd00 + i * 80);
        a.battle[0] = new VImg[4];
        a.battle[1] = new VImg[23];
        a.battle[2] = new VImg[12];
        for (int row = 0; row < a.battle.length; row++) for (int i = 0; i < a.battle[row].length; i++)
            a.battle[row][i] = image(190, row == 1 && i >= 2 && i <= 11 ? 20 : 210,
                    row == 0 ? 0xff7777aa : 0xff99aa77);
        for (VImg[] signs : a.moneySign) Arrays.fill(signs, image(18, 20, 0xffeedd66));
        for(int i=0;i<a.timer.length;i++)if(a.timer[i]==null)a.timer[i]=image(i==10?8:16,24,0xffeeeecc+i*13);
        if(a.icon[3]==null||a.icon[3].length<common.util.Data.TRAIT_TOT)a.icon[3]=new VImg[common.util.Data.TRAIT_TOT];
        for(int i=0;i<common.util.Data.TRAIT_TOT;i++)if(a.icon[3][i]==null)a.icon[3][i]=image(32,32,0xffcc8844+i*31);
        Arrays.fill(a.slot, image(120, 90, 0xff777777));
        Arrays.fill(a.spiritSummon, image(90, 24, 0xff55ffff));
        for (VImg[] parts : a.main) Arrays.fill(parts, image(110, 130, 0xffaaaaee));
    }
    public static VImg image(int w, int h, int rgb) {
        FakeImage image = ImageBuilder.builder.build(w, h);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) image.setRGB(x, y, rgb);
        return new VImg(image);
    }
    public static Unit unit(String pack, int rgb) {
        Unit unit = Fixture.unit(pack, 1000000);
        final VImg icon = image(120, 90, rgb);
        final FakeImage sprite = image(2, 2, rgb).getImg();
        AnimCI animation = new AnimCI(new AnimLoader() {
            public VImg getEdi() { return icon; }
            public VImg getUni() { return icon; }
            public ImgCut getIC() { return new ImgCut(); }
            public MaModel getMM() { return new MaModel(); }
            public MaAnim[] getMA() { MaAnim[] a = new MaAnim[7]; Arrays.setAll(a, i -> new MaAnim()); return a; }
            public Source.ResourceLocation getName() { return new Source.ResourceLocation(pack, "native", Source.BasePath.ANIM); }
            public FakeImage getNum() { return sprite; }
            public int getStatus() { return 1; }
            public boolean validate(AnimU.ImageKeeper.AnimationType t) { return true; }
            public List<String> collectInvalidAnimation(AnimU.ImageKeeper.AnimationType t) { return Collections.emptyList(); }
        });
        unit.forms[0] = new Form(unit, 0, "native " + pack, animation, (common.battle.data.CustomUnit) unit.forms[0].du);
        return unit;
    }
}
