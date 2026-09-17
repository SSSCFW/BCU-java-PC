package online.tests;

import common.battle.BasisLU;
import common.pack.UserProfile;
import common.util.unit.Unit;
import java.nio.file.*;

public final class PackTests {
    public static void run() throws Exception {
        Fixture.init();
        Unit original=Fixture.unit("collision_pack",1000);
        BasisLU lineup=Fixture.lineup(original);
        try {Class.forName("online.bundle.MatchBundle");} catch(ClassNotFoundException e){throw new AssertionError("Missing isolated pack exchange",e);}
        Class<?> type=Class.forName("online.bundle.MatchBundle");
        Path archive=(Path)type.getMethod("export",BasisLU.class).invoke(null,lineup);
        Path altered=Files.createTempFile("pvp-foreign-ref-", ".zip");
        try {
            java.util.Map<String,byte[]> entries=online.bundle.SafeArchive.read(archive);
            com.google.gson.JsonObject m=com.google.gson.JsonParser.parseString(new String(entries.get("manifest.json"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            com.google.gson.JsonObject ref=new com.google.gson.JsonObject();ref.addProperty("cls","common.util.unit.Unit");ref.addProperty("pack","abcdef");ref.addProperty("id",0);
            m.add("foreignLocalPack",ref);entries.put("manifest.json",m.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            online.bundle.SafeArchive.write(altered,entries);
            Check.rejects(()->online.bundle.MatchBundle.read(altered),"network references cannot escape into a local six-hex-digit pack");
        } finally {Files.deleteIfExists(altered);}
        Object bundle=type.getMethod("read",Path.class).invoke(null,archive);
        java.lang.reflect.Method mount=type.getMethod("mount",String.class,int.class);
        Object a=mount.invoke(bundle,"0123456789abcdef0123456789abcdef",0);
        Object b=mount.invoke(bundle,"0123456789abcdef0123456789abcdef",1);
        try {
            BasisLU la=(BasisLU)a.getClass().getField("lineup").get(a);
            BasisLU lb=(BasisLU)b.getClass().getField("lineup").get(b);
            Check.that(!la.lu.fs[0][0].uid.pack.equals(lb.lu.fs[0][0].uid.pack),"same pack ID separated by player");
            Check.equal(1000,la.lu.fs[0][0].du.getHp(),"unit stats round trip");
            Check.equal(original.forms[0].names.toString(),la.lu.fs[0][0].names.toString(),"display name preserved");
            Check.equal(original,UserProfile.getUserPack("collision_pack").units.getRaw(0),"local pack never overwritten");
            Check.that(la.lu.fs[0][0].anim.getNum().getWidth()==2,"sprite bytes round trip");
        }finally {
            ((AutoCloseable)a).close();((AutoCloseable)b).close();Files.deleteIfExists(archive);
        }
        Check.that(UserProfile.getUserPack("collision_pack")!=null,"cleanup keeps user's original pack");
    }
}
