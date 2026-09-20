package online.tests;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class BundleTests {
    private static byte[] zip(String path,byte[] value) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ZipOutputStream z=new ZipOutputStream(out)) { z.putNextEntry(new ZipEntry(path)); z.write(value); z.closeEntry(); }
        return out.toByteArray();
    }
    public static void run() throws Exception {
        try { Class.forName("online.bundle.SafeArchive"); } catch(ClassNotFoundException e) { throw new AssertionError("Missing bounded archive reader",e); }
        // Tests below use reflection until the production API is introduced.
        java.lang.reflect.Method read=Class.forName("online.bundle.SafeArchive").getMethod("read",Path.class);
        Path p=Files.createTempFile("pvp-test-", ".zip");
        try {
            Files.write(p,zip("../outside.txt",new byte[]{1}));
            boolean rejected=false;
            try {read.invoke(null,p);}catch(java.lang.reflect.InvocationTargetException e){rejected=e.getCause() instanceof IOException;}
            Check.that(rejected,"zip traversal rejected");
            Files.write(p,zip("packs/p0/pack.json","{}".getBytes("UTF-8")));
            @SuppressWarnings("unchecked") Map<String,byte[]> files=(Map<String,byte[]>)read.invoke(null,p);
            Check.equal(1,files.size(),"safe archive preserved");
            Files.write(p,zip("large.txt",new byte[online.net.Protocol.MAX_BUNDLE>0?17*1024*1024:0]));
            rejected=false;
            try {read.invoke(null,p);}catch(java.lang.reflect.InvocationTargetException e){rejected=e.getCause() instanceof IOException;}
            Check.that(rejected,"oversized expanded member rejected");
        } finally {Files.deleteIfExists(p);}

        FixtureNativeUi.init();
        common.util.unit.Unit broken=FixtureNativeUi.unit("bundle_pool_broken_icon",0xff333333);
        java.lang.reflect.Field iconImage=common.system.VImg.class.getDeclaredField("bimg");iconImage.setAccessible(true);
        iconImage.set(broken.forms[0].anim.getUni(),null);
        java.lang.reflect.Field iconLoaded=common.system.VImg.class.getDeclaredField("loaded");iconLoaded.setAccessible(true);
        iconLoaded.setBoolean(broken.forms[0].anim.getUni(),true);
        java.lang.reflect.Method best=online.bundle.MatchBundle.class.getDeclaredMethod("bestProductionForm",common.util.unit.Unit.class);best.setAccessible(true);
        Check.equal(null,best.invoke(null,broken),"reroll pool excludes units whose deploy icon cannot render");

        common.util.unit.Unit lineupUnit=FixtureNativeUi.unit("bundle_pool_lineup",0xff224466);
        common.util.unit.Unit extraUnit=FixtureNativeUi.unit("bundle_pool_extra",0xff662244);
        ((common.battle.data.CustomUnit)extraUnit.forms[0].du).price=7777;
        Path full=online.bundle.MatchBundle.export(Fixture.lineup(lineupUnit),true);
        try{
            online.bundle.MatchBundle bundle=online.bundle.MatchBundle.read(full);
            try(online.bundle.MatchBundle.Mounted mounted=bundle.mount("0123456789abcdef0123456789abcdef",0)){
                Check.that(mounted.productionPool.length>=2,"post-deploy reroll bundle contains more than the active lineup");
                Check.that(Arrays.stream(mounted.productionPool).anyMatch(form->form.du.getPrice()==7777),
                        "post-deploy reroll bundle includes eligible characters from packs outside the current lineup");
            }
        }finally{Files.deleteIfExists(full);}
    }
}
