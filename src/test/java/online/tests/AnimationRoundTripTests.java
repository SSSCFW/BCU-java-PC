package online.tests;

import common.pack.UserProfile;
import common.system.files.FDByte;
import common.util.anim.*;
import common.util.unit.Unit;
import online.bundle.MatchBundle;
import java.io.*;
import java.nio.file.*;
import java.util.function.Consumer;

public final class AnimationRoundTripTests {
    public static void run() throws Exception {
        FixtureAssets.init();Unit original=Fixture.unit("borrowed_standard_animation",12500);
        AnimU<?> custom=original.forms[0].anim;custom.check();
        String prefix="./org/pvp_fixture/sample";
        ByteArrayOutputStream png=new ByteArrayOutputStream();common.system.fake.FakeImage.write(custom.getNum(),"png",png);
        put(prefix+".png",png.toByteArray());put(prefix+".imgcut",text(custom.imgcut::write));put(prefix+".mamodel",text(custom.mamodel::write));
        for(int i=0;i<4;i++)put(prefix+"0"+i+".maanim",text(custom.anims[i]::write));
        MaAnim entry=new MaAnim(); Part track=new Part(0,5);
        track.n=2;track.moves=new int[][]{{0,0,0,0},{12,30,0,0}};track.validate();
        entry.n=1;entry.parts=new Part[]{track};entry.validate();
        put(prefix+"_entry.maanim",text(entry::write));
        AnimUD nativeAnimation=new AnimUD("./org/pvp_fixture/","sample",null,null);original.forms[0].anim=nativeAnimation;
        nativeAnimation.check();Check.equal(5,nativeAnimation.anims.length,"fixture contains a real entry animation");
        Path archive=MatchBundle.export(Fixture.lineup(original));
        try(MatchBundle.Mounted mounted=MatchBundle.read(archive).mount("33333333333333333333333333333333",0)) {
            AnimU<?> received=mounted.lineup.lu.fs[0][0].anim;received.check();
            Check.equal(5,received.anims.length,"entry animation type count survives sharing");
            Check.that(received.getMaAnim(AnimU.UType.ENTER)!=null,"entry animation is not mistaken for burrowing");
            Check.that(original.forms[0].anim==nativeAnimation,"export does not replace the original animation");
        }finally{Files.deleteIfExists(archive);}
    }
    private static void put(String name,byte[] bytes){UserProfile.getBCData().root.build(name,new FDByte(bytes));}
    private static byte[] text(Consumer<PrintStream> writer)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();try(PrintStream p=new PrintStream(b,true,"UTF-8")){writer.accept(p);}return b.toByteArray();}
    public static void main(String[] args)throws Exception{run();}
}
