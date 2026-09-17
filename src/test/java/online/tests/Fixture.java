package online.tests;

import common.CommonStatic;
import common.pack.*;
import common.pack.PackData.UserPack;
import common.pack.Source.*;
import common.system.VImg;
import common.system.fake.*;
import common.util.anim.*;
import common.util.unit.*;
import common.battle.*;
import common.battle.data.*;
import common.io.*;
import common.util.stage.Music;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Tiny generated data, no game assets or third-party game files required. */
public final class Fixture {
    public static Path root;
    public static void init() throws Exception {
        if(root!=null)return;
        root=Files.createTempDirectory("bcu-pvp-fixture-");
        CommonStatic.ctx=new Context() {
            public File getAssetFile(String s){return root.resolve("assets").resolve(s).toFile();}
            public File getAuxFile(String s){return root.resolve("aux").resolve(s).toFile();}
            public File getUserFile(String s){return root.resolve("user").resolve(s).toFile();}
            public File getWorkspaceFile(String s){return root.resolve("workspace").resolve(s).toFile();}
            public File getBackupFile(String s){return root.resolve("backup").resolve(s).toFile();}
            public File getBCUFolder(){return root.toFile();}
            public String getAuthor(){return "test";}
            public InputStream getLangFile(String s){return null;}
            public boolean confirmDelete(){return false;} public boolean confirmDelete(File f){return false;}
            public void initProfile(){} public void loadProg(String s){}
            public void noticeErr(Exception e,ErrType t,String s){throw new IllegalStateException(s,e);}
            public void printErr(ErrType t,String s){throw new IllegalStateException(s);}
            public boolean preload(PackLoader.ZipDesc.FileDesc d){return false;}
            public boolean restore(Backup b,Consumer<Double> c){return false;}
        };
        CommonStatic.def=new CommonStatic.Itf() {
            public void save(boolean a,boolean b){throw new AssertionError("Unexpected save");}
            public long getMusicLength(Music f){return 0;} public File route(String s){return root.resolve(s).toFile();}
            public void setSE(int n){} public void setSE(Identifier<Music> m){} public void setBGM(Identifier<Music> m){}
        };
        ImageBuilder.builder=new utilpc.awt.PCIB();
        VImg image=new VImg(ImageBuilder.builder.build(2,2));
        Arrays.fill(CommonStatic.getBCAssets().slot,image);
        CommonStatic.getBCAssets().dummyTrait=image;
        UnitLevel level=new UnitLevel(new int[20]); level.id=new Identifier<>(Identifier.DEF,UnitLevel.class,0);
        UserProfile.getBCData().unitLevels.set(0,level); CommonStatic.getBCAssets().defLv=level;
        for(int i=0;i<32;i++) UserProfile.getBCData().traits.set(i,new Trait(new Identifier<>(Identifier.DEF,Trait.class,i)));
        for(int i=1;i<common.util.Data.BASE_TOT;i++) {
            Map<Integer,int[][]> curve=new HashMap<>();
            for(int t=0;t<32;t++) curve.put(t,new int[][]{{30,0,0,0}});
            Treasure.curveData.put(i,new CannonLevelCurve(curve,CannonLevelCurve.PART.CANNON));
            Treasure.baseData.put(i,new CannonLevelCurve(curve,CannonLevelCurve.PART.BASE));
            Treasure.decorationData.put(i,new CannonLevelCurve(curve,CannonLevelCurve.PART.DECORATION));
        }
        CommonStatic.getConfig().ref=false;
        CommonStatic.getConfig().drawBGEffect=false;
    }
    public static AnimCI animation(String pack,String id) {
        final ResourceLocation location=new ResourceLocation(pack,id,BasePath.ANIM);
        final FakeImage img=ImageBuilder.builder.build(2,2);
        return new AnimCI(new AnimLoader() {
            public VImg getEdi(){return new VImg(img);} public VImg getUni(){return new VImg(img);}
            public ImgCut getIC(){return new ImgCut();} public MaModel getMM(){return new MaModel();}
            public MaAnim[] getMA(){MaAnim[] a=new MaAnim[7];Arrays.setAll(a,i -> new MaAnim());return a;}
            public ResourceLocation getName(){return location;} public FakeImage getNum(){return img;}
            public int getStatus(){return 1;} public boolean validate(AnimU.ImageKeeper.AnimationType t){return true;}
            public List<String> collectInvalidAnimation(AnimU.ImageKeeper.AnimationType t){return Collections.emptyList();}
        });
    }
    public static Unit unit(String pack,int hp) {
        UserPack p=UserProfile.getUserPack(pack);
        if(p==null) {p=new UserPack(pack); UserProfile.profile().packmap.put(pack,p);}
        Unit u=new Unit(new Identifier<>(pack,Unit.class,0)); u.rarity=0;u.max=50;
        u.lv=CommonStatic.getBCAssets().defLv;
        CustomUnit data=new CustomUnit();data.hp=hp;data.death=null;data.price=10;data.resp=30;
        data.atks[0].atk=100;data.atks[0].pre=1;
        u.forms=new Form[]{new Form(u,0,"同じキャラ名",animation(pack,"same"),data)};
        p.units.set(0,u);return u;
    }
    public static BasisLU lineup(Unit u) {
        BasisLU b=new BasisLU();b.name="test lineup";b.lu.fs[0][0]=u.forms[0];
        b.lu.map.put(u.id,new Level(1,0,new int[0])); b.lu.renew();return b;
    }
    public static void main(String[] args) throws Exception {
        init(); Unit u=unit("same_pack",1000); BasisLU b=lineup(u);
        System.out.println(common.io.json.JsonEncoder.encode(UserProfile.getUserPack("same_pack")));
        System.out.println(common.io.json.JsonEncoder.encode(b));
    }
}
