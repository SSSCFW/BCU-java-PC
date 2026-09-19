package online.bundle;

import common.pack.Source;
import common.system.VImg;
import common.system.files.*;
import common.util.Data;
import common.util.anim.AnimCI;
import java.io.*;
import java.util.*;

/** Read-only in-memory pack; never routes a remote path into workspace or packs/. */
final class MatchSource extends Source {
    private final Map<String,byte[]> files;
    private final Map<String,AnimCI> animations=new HashMap<>();
    MatchSource(String id,Map<String,byte[]> files){super(id);this.files=files;}
    private static String clean(String path) {
        if(path.startsWith("./"))path=path.substring(2);
        try{return SafeArchive.path(path);}catch(IOException e){throw new IllegalArgumentException(e);}
    }
    public void delete(){throw new UnsupportedOperationException("Match assets are read-only");}
    public FileData getFileData(String path){byte[] b=files.get(clean(path));return b==null?null:new FDByte(b);}
    public String[] listFile(String path) {
        String prefix=path.equals(".") || path.equals("./")?"":clean(path.replaceAll("/$",""))+"/";
        SortedSet<String> names=new TreeSet<>();
        for(String name:files.keySet())if(name.startsWith(prefix))names.add(name.substring(prefix.length()).split("/",2)[0]);
        return names.toArray(new String[0]);
    }
    public AnimCI loadAnimation(String name,BasePath base) {
        String key=clean(base.toString()+"/"+name);
        return animations.computeIfAbsent(key,k -> new ReadOnlyAnimation(new SourceAnimLoader(new ResourceLocation(id,name,base),
            (b,location,file) -> getFileData(b+"/"+location.id+"/"+file)) {
                @Override public common.util.anim.MaAnim[] getMA() {
                    byte[] metadata=files.get(key+"/animation_count.txt");
                    if(metadata==null)throw new IllegalArgumentException("Missing animation layout");
                    int count=Integer.parseInt(new String(metadata,java.nio.charset.StandardCharsets.UTF_8));
                    String[] names=MatchBundle.animationNames(count,base);
                    common.util.anim.MaAnim[] result=new common.util.anim.MaAnim[count];
                    for(int i=0;i<count;i++) {
                        FileData file=getFileData(key+"/"+names[i]);
                        if(file==null)throw new IllegalArgumentException("Missing animation track");
                        result[i]=common.util.anim.MaAnim.newIns(file,false);
                    }
                    return result;
                }
            }));
    }
    /** Legacy AnimCI retries errors and may invoke the application's save handler. Network data must not. */
    private static final class ReadOnlyAnimation extends AnimCI {
        ReadOnlyAnimation(Source.AnimLoader loader){super(loader);}
        @Override public void load() {
            if(loaded)return;
            try {
                common.system.fake.FakeImage sprite=loader.getNum();
                if(sprite==null || !sprite.isValid())throw new IllegalArgumentException("Missing network sprite: "+id);
                imgcut=loader.getIC();mamodel=loader.getMM();anims=loader.getMA();
                if(imgcut==null || mamodel==null || anims==null)throw new IllegalArgumentException("Missing network animation: "+id);
                if(anims.length!=1 && anims.length!=4 && anims.length!=5 && anims.length!=7)throw new IllegalArgumentException("Invalid animation count");
                types=anims.length==1?SOUL:anims.length==4?TYPE4:anims.length==5?TYPE5:TYPE7;
                parts=imgcut.cut(sprite);partial=true;loaded=true;
                validate();
            } catch(Exception e) {
                loaded=false;partial=false;
                throw new IllegalArgumentException("Cannot load shared animation: "+id,e);
            }
        }
        @Override public void partial(){if(!partial)load();}
    }
    public VImg readImage(String path,int ind){FileData f=getFileData(path+"/"+Data.trio(ind)+".png");return f==null?null:new VImg(f.getImg());}
    public InputStream streamFile(String path) throws IOException {FileData f=getFileData(path);if(f==null)throw new FileNotFoundException(path);return f.getStream();}
    void release(){for(AnimCI a:animations.values())a.unload();animations.clear();}
}
