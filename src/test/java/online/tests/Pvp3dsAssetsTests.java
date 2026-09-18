package online.tests;

import online.ui.Pvp3dsAssets;
import java.awt.image.BufferedImage;

/** Verifies original 3DS BCTEX resources decode and the imgcut metadata matches them. */
public final class Pvp3dsAssetsTests {
    public static void run() {
        Check.that(Pvp3dsAssets.available(),"3DS PvP BCTEX/imgcut resources load");
        assertPart("ui_battle_multi_icon","アイコン：ふっとばし",40,40);
        assertPart("ui_battle_multi_icon","アイコン：プチベビーラッシュ",40,40);
        assertPart("ui_battle_multi_reel","効果名：攻撃力アップ",173,40);
        assertPart("ui_battle_multi_icon","レベルマックス",30,21);
        assertPart("ui_battle_multi_cutin","ぷちベビーラッシュ発動!",336,45);
        assertPart("ui_battle_multi","Rボタン",24,23);
        assertPart("ui_battle_multi","ルーレット点灯中ランプ",26,25);
    }

    private static void assertPart(String sheet,String label,int w,int h) {
        BufferedImage image=Pvp3dsAssets.image(sheet,label);
        Check.equal(w,image.getWidth(),sheet+" / "+label+" width from imgcut");
        Check.equal(h,image.getHeight(),sheet+" / "+label+" height from imgcut");
        boolean visible=false;
        outer:for(int y=0;y<h;y++)for(int x=0;x<w;x++)
            if((image.getRGB(x,y)>>>24)!=0){visible=true;break outer;}
        Check.that(visible,sheet+" / "+label+" contains decoded visible pixels");
    }
}
