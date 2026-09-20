package online.bundle;

import com.google.gson.*;
import common.battle.*;
import common.io.assets.AssetLoader;
import common.io.json.*;
import common.pack.*;
import common.pack.PackData.*;
import common.pack.Source.*;
import common.system.fake.FakeImage;
import common.util.anim.*;
import common.util.pack.Soul;
import common.util.stage.*;
import common.util.unit.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Immutable snapshot of lineup + transitive user packs. Names are display data, never identity. */
public final class MatchBundle {
    private final JsonObject manifest;
    private final Map<String,byte[]> files;
    private MatchBundle(JsonObject manifest,Map<String,byte[]> files){this.manifest=manifest;this.files=files;}
    private static JsonElement encode(Object value) throws IOException {
        JsonElement e=JsonEncoder.encode(value);if(e==null || e.isJsonNull())throw new IOException("BCU could not serialize match data");return e;
    }
    public static Path export(BasisLU lineup) throws Exception {return export(lineup,false);}
    public static Path export(BasisLU lineup,boolean includeProductionPool) throws Exception {
        if(lineup==null)throw new IOException("Choose a lineup first");
        BasisLU exported=lineup.copy();
        List<Form> productionForms=includeProductionPool?productionCandidates():Collections.emptyList();
        for(Form form:productionForms)exported.lu.getLv(form);
        JsonObject manifest=new JsonObject();manifest.addProperty("format",1);
        JsonElement basis=encode(exported),treasure=encode(lineup.t());
        JsonArray productionPool=new JsonArray();
        for(Form form:productionForms){
            JsonObject p=new JsonObject();p.addProperty("pack",form.unit.id.pack);p.addProperty("id",form.unit.id.id);p.addProperty("form",form.fid);
            productionPool.add(p);
        }
        SortedSet<String> required=new TreeSet<>(); collectRefs(basis,required);collectRefs(productionPool,required);
        JsonArray combos=new JsonArray();for(Combo c:lineup.lu.coms) {combos.add(encode(c.id));required.add(c.id.pack);}
        Map<String,UserPack> packs=new TreeMap<>();Map<String,JsonObject> jsons=new TreeMap<>();
        Map<Object,Object> animationAliases=new IdentityHashMap<>();
        List<AnimationEntry> animations=new ArrayList<>();
        // Snapshot the actual pack graph, including references made by summons, souls and traits.
        while(!required.isEmpty()) {
            String id=required.first();required.remove(id);
            if(id.equals(Identifier.DEF) || id.equals(ResourceLocation.LOCAL) || packs.containsKey(id))continue;
            UserPack pack=UserProfile.getUserPack(id);
            if(pack==null) {
                if(CastleList.getList(id)!=null)continue;
                throw new IOException("Missing custom pack dependency: "+id);
            }
            if(pack.source instanceof MatchSource)throw new IOException("A match cannot re-export temporary opponent data");
            if(packs.size()>=32)throw new IOException("Too many dependent packs");
            for(Unit u:pack.units)for(Form f:u.forms)collectAnimation(f.anim,id,BasePath.ANIM,animationAliases,animations);
            for(Enemy enemy:pack.enemies)collectAnimation(enemy.anim,id,BasePath.ANIM,animationAliases,animations);
            for(Soul soul:pack.souls)collectAnimation(soul.anim,id,BasePath.SOUL,animationAliases,animations);
            JsonElement snapshot=JsonEncoder.encodeWithAliases(pack,animationAliases);
            if(snapshot==null || !snapshot.isJsonObject())throw new IOException("Cannot serialize shared pack");
            JsonObject data=snapshot.getAsJsonObject();
            // Stage replays are unrelated personal data. No replays/music/stage graphs are shared.
            data.remove("mc"); data.remove("musics");
            JsonObject desc=data.getAsJsonObject("desc");desc.remove("parentPassword");
            desc.addProperty("BCU_VERSION",AssetLoader.CORE_VER);
            packs.put(id,pack);jsons.put(id,data);collectRefs(data,required);
            for(String dependency:pack.desc.dependency)required.add(dependency);
        }
        Map<String,String> tokens=new TreeMap<>();int index=0;
        for(String id:packs.keySet())tokens.put(id,String.format(Locale.ROOT,"p%03d",index++));
        // _local animations get an independent asset-only pack. Originals are not renamed.
        tokens.put(ResourceLocation.LOCAL,"local");
        Map<String,byte[]> files=new TreeMap<>();
        JsonArray list=new JsonArray();
        for(Map.Entry<String,UserPack> entry:packs.entrySet()) {
            String id=entry.getKey(),token=tokens.get(id);UserPack p=entry.getValue();JsonObject data=jsons.get(id);
            // All images used by custom traits/backgrounds/castles/random enemy groups.
            for(BasePath folder:new BasePath[]{BasePath.BG,BasePath.CASTLE,BasePath.TRAIT,BasePath.ENERAND})
                copyImages(p.source,folder.toString(),"packs/"+token+"/",files,0);
            rewrite(data,tokens);descId(data,token);
            SortedSet<String> dependencies=new TreeSet<>(p.desc.dependency);collectRefs(jsons.get(id),dependencies);
            JsonArray deps=new JsonArray();for(String dependency:dependencies) {
                // data has already been tokenized above; preserve either representation.
                String t=tokens.get(dependency);if(t==null && tokens.containsValue(dependency))t=dependency;
                if(t!=null && !t.equals(token))deps.add(t);
            }
            data.getAsJsonObject("desc").add("dependency",deps);
            JsonObject record=new JsonObject();record.addProperty("token",token);record.add("data",data);list.add(record);
        }
        for(AnimationEntry animation:animations) {
            ResourceLocation location=animation.location;
            String owner=tokens.get(location.pack);
            if(owner==null)throw new IOException("Undeclared animation dependency: "+location.pack);
            saveAnimation(files,"packs/"+owner+"/"+location.base+"/"+location.id+"/",animation.animation,location.base);
        }
        JsonObject local=new JsonObject(),desc=new JsonObject();desc.addProperty("id","local");desc.addProperty("BCU_VERSION",AssetLoader.CORE_VER);
        desc.add("dependency",new JsonArray());local.add("desc",desc);
        JsonObject record=new JsonObject();record.addProperty("token","local");record.add("data",local);list.add(record);
        rewrite(basis,tokens);rewrite(combos,tokens);rewrite(productionPool,tokens);
        manifest.add("lineup",basis);manifest.add("treasure",treasure);manifest.add("combos",combos);manifest.add("productionPool",productionPool);manifest.add("packs",list);
        JsonObject checksums=new JsonObject();for(Map.Entry<String,byte[]> e:files.entrySet())checksums.addProperty(e.getKey(),Hashes.sha256(e.getValue()));
        manifest.add("assets",checksums);files.put("manifest.json",manifest.toString().getBytes(StandardCharsets.UTF_8));
        Path out=Files.createTempFile("bcu-pvp-send-",".zip");
        try{SafeArchive.write(out,files);read(out);return out;}catch(Exception e){Files.deleteIfExists(out);throw e;}
    }
    private static final class AnimationEntry {
        final AnimU<?> animation; final ResourceLocation location;
        AnimationEntry(AnimU<?> animation,ResourceLocation location){this.animation=animation;this.location=location;}
    }
    private static void collectAnimation(AnimU<?> animation,String pack,BasePath base,Map<Object,Object> aliases,List<AnimationEntry> entries) throws IOException {
        if(animation==null)throw new IOException("Missing animation in shared pack");
        ResourceLocation location;
        if(animation instanceof AnimCI)location=((AnimCI)animation).id;
        else {
            if(aliases.containsKey(animation))return;
            location=new ResourceLocation(pack,"__pvp_snapshot_"+UUID.randomUUID().toString().replace("-",""),base);
            aliases.put(animation,location);
        }
        entries.add(new AnimationEntry(animation,location));
    }
    static String[] animationNames(int count,BasePath base) {
        if(base==BasePath.SOUL && count==1)return SourceAnimLoader.MA_SOUL;
        if(base!=BasePath.SOUL && count==4)return Arrays.copyOf(SourceAnimLoader.MA_ENTITY,4);
        if(base!=BasePath.SOUL && count==5)return new String[]{"maanim_walk.txt","maanim_idle.txt","maanim_attack.txt","maanim_kb.txt","maanim_entry.txt"};
        if(base!=BasePath.SOUL && count==7)return SourceAnimLoader.MA_ENTITY;
        throw new IllegalArgumentException("Unsupported animation layout");
    }
    private static void saveAnimation(Map<String,byte[]> files,String prefix,AnimU<?> a,BasePath base) throws IOException {
        a.check();if(a.imgcut==null || a.mamodel==null || a.anims==null)throw new IOException("Invalid custom animation");
        putText(files,prefix+"imgcut.txt",a.imgcut::write);putText(files,prefix+"mamodel.txt",a.mamodel::write);
        String[] names=animationNames(a.anims.length,base);
        put(files,prefix+"animation_count.txt",Integer.toString(names.length).getBytes(StandardCharsets.UTF_8));
        for(int i=0;i<names.length;i++){MaAnim ma=a.anims[i];if(ma==null)throw new IOException("Missing animation track");putText(files,prefix+names[i],ma::write);}
        putImage(files,prefix+"sprite.png",a.getNum());
        if(a.getUni()!=null && base!=BasePath.SOUL)putImage(files,prefix+"icon_deploy.png",a.getUni().getImg());
        if(a.getEdi()!=null)putImage(files,prefix+"icon_display.png",a.getEdi().getImg());
    }
    private static void putText(Map<String,byte[]> files,String name,Consumer<PrintStream> writer) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();try(PrintStream ps=new PrintStream(out,false,"UTF-8")){writer.accept(ps);}
        put(files,name,out.toByteArray());
    }
    private static void putImage(Map<String,byte[]> files,String name,FakeImage image) throws IOException {
        if(image==null)throw new IOException("Missing sprite");ByteArrayOutputStream out=new ByteArrayOutputStream();
        if(!FakeImage.write(image,"PNG",out))throw new IOException("Image export failed");put(files,name,out.toByteArray());
    }
    private static void put(Map<String,byte[]> files,String name,byte[] value) throws IOException {
        SafeArchive.path(name);if(value.length>SafeArchive.MAX_MEMBER || files.size()>SafeArchive.MAX_FILES)throw new IOException("Custom asset limit");
        byte[] old=files.put(name,value);if(old!=null && !Arrays.equals(old,value))throw new IOException("Conflicting animation paths inside a local pack");
    }
    private static void copyImages(Source source,String path,String prefix,Map<String,byte[]> out,int depth) throws Exception {
        if(depth>3)throw new IOException("Unexpected asset directory nesting");String[] names=source.listFile("./"+path);if(names==null)return;
        Arrays.sort(names);
        for(String name:names) {
            SafeArchive.path(name);if(name.contains("/"))throw new IOException("Invalid source entry");
            String p=path+"/"+name;
            if(name.endsWith(".png")) {
                try(InputStream in=source.streamFile("./"+p)) {ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;
                    while((n=in.read(b))!=-1){if(bytes.size()+n>SafeArchive.MAX_MEMBER)throw new IOException("Image file limit");bytes.write(b,0,n);}put(out,prefix+p,bytes.toByteArray());}
            }
        }
    }
    private static List<Form> productionCandidates(){
        Map<String,Form> out=new TreeMap<>();
        Set<String> spiritTargets=new HashSet<>();
        for(Unit unit:UserProfile.getBCData().units.getList())collectSpiritTargets(spiritTargets,unit);
        for(PackData pack:UserProfile.getAllPacks()){
            if(pack instanceof UserPack&&((UserPack)pack).source instanceof MatchSource)continue;
            for(Unit unit:pack.units)collectSpiritTargets(spiritTargets,unit);
        }
        for(Unit unit:UserProfile.getBCData().units.getList())addProductionCandidate(out,unit,spiritTargets);
        for(PackData pack:UserProfile.getAllPacks()){
            if(pack instanceof UserPack&&((UserPack)pack).source instanceof MatchSource)continue;
            for(Unit unit:pack.units)addProductionCandidate(out,unit,spiritTargets);
        }
        return new ArrayList<>(out.values());
    }
    private static void addProductionCandidate(Map<String,Form> out,Unit unit,Set<String> spiritTargets){
        if(unit==null||unit.id==null||unit.lv==null||unit.forms==null)return;
        Form form=bestProductionForm(unit);if(form==null)return;
        String key=unit.id.pack+":"+unit.id.id;
        if(form.du.getPrice()==0&&(spiritTargets.contains(key)||(Identifier.DEF.equals(unit.id.pack)&&unit.id.id==339)))return;
        out.put(key,form);
    }
    private static Form bestProductionForm(Unit unit){
        for(int i=unit.forms.length-1;i>=0;i--){Form form=unit.forms[i];if(form!=null&&form.du!=null&&form.anim!=null)return form;}
        return null;
    }
    private static void collectSpiritTargets(Set<String> targets,Unit unit){
        if(unit==null||unit.forms==null)return;
        for(Form form:unit.forms)if(form!=null&&form.du!=null&&form.du.getProc()!=null&&form.du.getProc().SPIRIT.exists()&&form.du.getProc().SPIRIT.id!=null)
            targets.add(form.du.getProc().SPIRIT.id.pack+":"+form.du.getProc().SPIRIT.id.id);
    }

    public static MatchBundle read(Path file) throws IOException {
        Map<String,byte[]> files=SafeArchive.read(file);byte[] bytes=files.remove("manifest.json");if(bytes==null)throw new IOException("Missing bundle manifest");
        JsonObject m=SafeJson.object(bytes);
        try {
            if(m.get("format").getAsInt()!=1)throw new IOException("Unsupported bundle format");
            JsonArray packs=m.getAsJsonArray("packs");if(packs.size()>33)throw new IOException("Pack count limit");
            Set<String> tokens=new HashSet<>();
            for(JsonElement e:packs) {
                JsonObject p=e.getAsJsonObject();String token=p.get("token").getAsString();
                if(!token.matches("p[0-9]{3}|local") || !tokens.add(token))throw new IOException("Invalid/duplicate pack token");
                if(!token.equals(p.getAsJsonObject("data").getAsJsonObject("desc").get("id").getAsString()))throw new IOException("Pack token/ID mismatch");
                if(p.getAsJsonObject("data").has("mc") || p.getAsJsonObject("data").has("musics"))throw new IOException("Stage/music payloads are not part of a match");
            }
            validateReferences(m,tokens);
            JsonObject hashes=m.getAsJsonObject("assets");if(hashes.size()!=files.size())throw new IOException("Asset manifest size mismatch");
            long pixels=0;
            for(Map.Entry<String,byte[]> e:files.entrySet()) {
                String name=e.getKey();String[] parts=name.split("/");
                if(parts.length<3 || !parts[0].equals("packs") || !tokens.contains(parts[1]) || !(name.endsWith(".png") || name.endsWith(".txt")))throw new IOException("Unexpected bundle member");
                if(!hashes.has(name) || !Hashes.sha256(e.getValue()).equals(hashes.get(name).getAsString()))throw new IOException("Asset checksum mismatch");
                pixels+=AssetChecks.check(name,e.getValue());if(pixels>33_554_432L)throw new IOException("Total decoded image limit");
            }
            m.getAsJsonObject("lineup");m.getAsJsonObject("treasure");m.getAsJsonArray("combos");
            JsonArray productionPool=m.has("productionPool")?m.getAsJsonArray("productionPool"):new JsonArray();
            if(productionPool.size()>10000)throw new IOException("Production pool size limit");
            validateReferences(productionPool,tokens);
            return new MatchBundle(m,files);
        }catch(RuntimeException e){throw new IOException("Malformed match manifest",e);}
    }
    private static void validateReferences(JsonElement value,Set<String> tokens) throws IOException {
        if(value.isJsonArray()){for(JsonElement e:value.getAsJsonArray())validateReferences(e,tokens);return;}
        if(!value.isJsonObject())return;
        JsonObject object=value.getAsJsonObject();
        if(object.has("pack") && object.get("pack").isJsonPrimitive()) {
            String pack=object.get("pack").getAsString();String root=pack.split("/",2)[0];
            boolean defaultCastle=object.has("cls") && "common.util.stage.CastleImg".equals(object.get("cls").getAsString())
                && pack.equals(root) && CastleList.getList(root) instanceof CastleList.DefCasList;
            if(!tokens.contains(root) && !root.equals(Identifier.DEF) && !defaultCastle)
                throw new IOException("Undeclared asset dependency: "+root);
        }
        for(Map.Entry<String,JsonElement> e:object.entrySet())validateReferences(e.getValue(),tokens);
    }
    private static void collectRefs(JsonElement e,Set<String> refs) {
        if(e==null || e.isJsonNull())return;
        if(e.isJsonArray()){for(JsonElement c:e.getAsJsonArray())collectRefs(c,refs);return;}
        if(!e.isJsonObject())return;
        JsonObject o=e.getAsJsonObject();if(o.has("pack") && o.get("pack").isJsonPrimitive())refs.add(o.get("pack").getAsString().split("/",2)[0]);
        for(Map.Entry<String,JsonElement> f:o.entrySet())collectRefs(f.getValue(),refs);
    }
    private static void rewrite(JsonElement e,Map<String,String> ids) {
        if(e.isJsonArray()){for(JsonElement c:e.getAsJsonArray())rewrite(c,ids);return;}
        if(!e.isJsonObject())return;
        JsonObject o=e.getAsJsonObject();
        if(o.has("pack") && o.get("pack").isJsonPrimitive()) {
            String pack=o.get("pack").getAsString();String[] split=pack.split("/",2);
            if(ids.containsKey(split[0]))o.addProperty("pack",ids.get(split[0])+(split.length==2?"/"+split[1]:""));
        }
        for(Map.Entry<String,JsonElement> f:o.entrySet())rewrite(f.getValue(),ids);
    }
    private static void descId(JsonObject data,String id){data.getAsJsonObject("desc").addProperty("id",id);}
    public Mounted mount(String match,int slot) throws Exception {
        return common.battle.PvpTiming.inMatch(match, () -> mountScoped(match,slot));
    }
    private Mounted mountScoped(String match,int slot) throws Exception {
        if(!match.matches("[0-9a-f]{32}") || slot<0)throw new IOException("Invalid match namespace");
        return new Mounted(match,slot);
    }
    public final class Mounted implements AutoCloseable {
        public final BasisLU lineup;
        public final Form[] productionPool;
        private final List<UserPack> owned=new ArrayList<>();private boolean closed;
        private Mounted(String match,int slot) throws Exception {
            Map<String,String> ids=new TreeMap<>();
            JsonObject m=manifest.deepCopy();JsonArray entries=m.getAsJsonArray("packs");
            for(JsonElement e:entries) {String token=e.getAsJsonObject().get("token").getAsString();
                String id="pvp_"+match+"_s"+slot+"_"+token;
                if(UserProfile.getPack(id)!=null || CastleList.getList(id)!=null || MapColc.get(id)!=null)throw new IOException("Match namespace already in use");ids.put(token,id);}
            try {
                rewrite(m,ids);
                for(JsonElement e:entries) {
                    JsonObject entry=e.getAsJsonObject();String token=entry.get("token").getAsString(),id=ids.get(token);
                    JsonObject data=entry.getAsJsonObject("data");descId(data,id);
                    JsonArray dependencies=new JsonArray();for(JsonElement d:data.getAsJsonObject("desc").getAsJsonArray("dependency")) {
                        String mapped=ids.get(d.getAsString());if(mapped==null)throw new IOException("Missing declared pack dependency");dependencies.add(mapped);}
                    data.getAsJsonObject("desc").add("dependency",dependencies);
                    Map<String,byte[]> assets=new TreeMap<>();String prefix="packs/"+token+"/";
                    for(Map.Entry<String,byte[]> f:files.entrySet())if(f.getKey().startsWith(prefix))assets.put(f.getKey().substring(prefix.length()),f.getValue());
                    PackDesc desc=JsonDecoder.decode(data.get("desc"),PackDesc.class);if(desc==null)throw new IOException("Invalid pack descriptor");
                    UserPack p=new UserPack(new MatchSource(id,assets),desc,data);p.useCombos=false;owned.add(p);UserProfile.profile().packmap.put(id,p);
                }
                // Reference targets (levels/traits/units) are pre-registered; load dependencies first.
                Set<String> loaded=new HashSet<>();int remaining=owned.size();
                while(remaining>0) {boolean progress=false;
                    for(UserPack p:owned)if(!loaded.contains(p.getSID()) && loaded.containsAll(p.desc.dependency)) {
                        p.load();loaded.add(p.getSID());remaining--;progress=true;
                    }
                    if(!progress)throw new IOException("Cyclic pack dependency; make the pack graph acyclic before online play");
                }
                for(UserPack p:owned)if(!p.validate())throw new IOException("Invalid animation in shared pack");
                // Rebuild active combos from the mounted lineup instead of resolving
                // serialized combo IDs one-by-one. The temporary packs normally keep
                // useCombos=false so one player's custom combos cannot leak into the
                // other player's lineup. Enable ONLY this mounted bundle's packs while
                // renewing, then immediately disable them again.
                for(UserPack p:owned)p.useCombos=true;
                BasisLU result;
                try {
                    result=new BasisLU();JsonDecoder.inject(m.get("lineup"),BasisLU.class,result);
                    JsonDecoder.inject(m.get("treasure"),Treasure.class,result.t());
                    result.lu.renew();
                } finally {
                    for(UserPack p:owned)p.useCombos=false;
                }
                validateLineup(result);lineup=result;
                JsonArray pool=m.has("productionPool")?m.getAsJsonArray("productionPool"):new JsonArray();
                ArrayList<Form> forms=new ArrayList<>(pool.size());
                for(JsonElement element:pool){
                    JsonObject entry=element.getAsJsonObject();
                    String pack=entry.get("pack").getAsString();int unitId=entry.get("id").getAsInt(),formId=entry.get("form").getAsInt();
                    Unit unit=Identifier.get(new Identifier<>(pack,Unit.class,unitId));
                    if(unit==null||unit.forms==null||formId<0||formId>=unit.forms.length||unit.forms[formId]==null)
                        throw new IOException("Invalid production-pool unit");
                    Form form=unit.forms[formId];
                    if(!lineup.lu.map.containsKey(unit.id))throw new IOException("Missing synchronized production-pool level");
                    lineup.lu.getLv(form);
                    forms.add(form);
                }
                productionPool=forms.toArray(new Form[0]);
            }catch(Exception e){close();throw e;}
            finally{UserProfile.setStatic("_current_pack",null);}
        }
        @Override public void close() {
            if(closed)return;closed=true;
            for(UserPack p:owned) {
                for(Unit u:p.units)if(u.lv!=null)u.lv.units.remove(u);
                ((MatchSource)p.source).release();
                if(UserProfile.getPack(p.getSID())==p)UserProfile.unloadPack(p);
                if(CastleList.getList(p.getSID())==p.castles)CastleList.map().remove(p.getSID());
                if(MapColc.get(p.getSID())==p.mc)UserProfile.getRegister("MapColc",MapColc.class).remove(p.getSID());
            }
            owned.clear();
        }
    }
    private static void validateLineup(BasisLU b) throws IOException {
        int units=0;
        if(b.nyc.length!=3)throw new IOException("Invalid castle configuration");
        for(int type:b.nyc)if(type<0 || type>=common.util.Data.BASE_TOT)throw new IOException("Invalid cannon/base type");
        for(Form[] row:b.lu.fs)for(Form f:row)if(f!=null) {
            units++;if(f.du==null || f.anim==null || f.unit.lv==null || f.du.getHp()<=0 || f.du.getHb()<=0)throw new IOException("Invalid unit stats");
            f.anim.check();if(f.anim.mamodel==null || f.anim.anims==null)throw new IOException("Missing animation");
        }
        if(units==0)throw new IOException("Lineup is empty");
    }
}
