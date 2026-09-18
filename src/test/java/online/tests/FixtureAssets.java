package online.tests;

import common.CommonStatic;
import common.pack.*;
import common.system.*;
import common.system.files.*;
import common.util.anim.*;
import common.util.pack.*;
import common.util.stage.*;
import java.io.*;
import java.lang.reflect.*;

/** Generates tiny valid sprites/animations for the REAL battle engine. */
public final class FixtureAssets {
    private static boolean loaded;
    public static void init() throws Exception {
        Fixture.init();if(loaded)return;loaded=true;
        VImg sprite=new VImg(common.system.fake.ImageBuilder.builder.build(2,2));
        ByteArrayOutputStream png=new ByteArrayOutputStream();common.system.fake.FakeImage.write(sprite.getImg(),"png",png);
        VFileRoot root=UserProfile.getBCData().root;
        root.build("./org/img/castle/castle000.png",new FDByte(png.toByteArray()));
        new CastleList.DefCasList("000000","castle");
        UserProfile.getRegister("DefMapColc_idmap",Integer.class).put("CH",3);
        common.util.unit.Trait.TRAITED=new java.util.ArrayList<>(UserProfile.getBCData().traits.getList().subList(0,8));
        Background bg=new Background(new Identifier<>("000000",Background.class,0),sprite);
        UserProfile.getBCData().bgs.set(0,bg);
        UserProfile.getBCData().musics.set(3,new Music(new Identifier<>(Identifier.DEF,Music.class,3),0,new FDByte(new byte[]{1,2,3})));
        for(Field f:EffAnim.EffAnimStore.class.getFields()) {
            if(f.getType()!=EffAnim.class)continue;
            Class<?> type=(Class<?>)((ParameterizedType)f.getGenericType()).getActualTypeArguments()[0];
            Object[] values=type.getEnumConstants();
            f.set(CommonStatic.getBCAssets().effas,effect(values,sprite));
        }
        byte[] model=text(p -> new MaModel().write(p));
        byte[] cut=text(p -> new ImgCut().write(p));
        byte[] anim=text(p -> new MaAnim().write(p));
        for(int i=0;i<8;i++) {
            String base="./org/castle/001/nyankoCastle_001_0"+i;
            root.build(base+".png",new FDByte(png.toByteArray()));
            // NyCastle attack animation uses nyankoCastle_001_0X_00.png.
            root.build(base+"_00.png",new FDByte(png.toByteArray()));
            root.build(base+"_00.imgcut",new FDByte(cut));
            for(int j=0;j<3;j++) {
                root.build(base+"_0"+j+".mamodel",new FDByte(model));
                root.build(base+"_0"+j+".maanim",new FDByte(anim));
            }
            for(int t:new int[]{0,2,3})root.build("./org/castle/00"+t+"/nyankoCastle_00"+t+"_0"+i+".png",new FDByte(png.toByteArray()));
        }
        NyCastle.read();
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static EffAnim effect(Object[] values,VImg img) {
        EffAnim.EffType[] types=new EffAnim.EffType[values.length];
        // Reuse the actual enum-array runtime type required by AnimD.
        EffAnim effect=new EffAnim("generated",img,new ImgCut(),new MaModel(),(Enum[])values) {
            @Override public void load(){loaded=true;parts=imgcut.cut(img.getImg());}
        };
        effect.anims=new MaAnim[values.length];
        for(int i=0;i<values.length;i++){MaAnim a=new MaAnim();a.max=30;a.len=30;effect.anims[i]=a;}
        return effect;
    }
    private static byte[] text(java.util.function.Consumer<PrintStream> writer) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();try(PrintStream p=new PrintStream(out,true,"UTF-8")){writer.accept(p);}return out.toByteArray();
    }
}
