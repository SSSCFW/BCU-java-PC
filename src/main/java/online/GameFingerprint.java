package online;

import common.io.assets.AssetLoader;
import common.system.files.VFile;
import online.bundle.Hashes;
import online.net.Protocol;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;

/** Verifies actual default asset bytes, not just a user-visible version string. */
public final class GameFingerprint {
    private GameFingerprint(){}
    public static String compute(Consumer<String> progress) throws IOException {
        List<VFile> files=new ArrayList<>();collect(VFile.getBCFileTree(),files);
        files.sort(Comparator.comparing(VFile::getPath));
        if(files.isEmpty())throw new IOException("Default BCU assets are not loaded");
        MessageDigest digest=Hashes.digest();text(digest,Protocol.ENGINE);text(digest,AssetLoader.CORE_VER);
        byte[] buffer=new byte[65536];int index=0;
        for(VFile file:files) {
            if(Thread.currentThread().isInterrupted())throw new IOException("Asset verification cancelled");
            text(digest,file.getPath());text(digest,Integer.toString(file.getData().size()));
            try(InputStream in=file.getData().getStream()){if(in==null)throw new IOException("Missing default asset: "+file.getPath());int n;while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);}
            if(++index%200==0)progress.accept("標準データを照合中: "+index+" / "+files.size());
        }
        return Hashes.hex(digest.digest());
    }
    private static void text(MessageDigest d,String s){byte[] bytes=s.getBytes(StandardCharsets.UTF_8);d.update((byte)(bytes.length>>>24));d.update((byte)(bytes.length>>>16));d.update((byte)(bytes.length>>>8));d.update((byte)bytes.length);d.update(bytes);}
    private static void collect(VFile file,List<VFile> list){if(file.getData()!=null)list.add(file);if(file.list()!=null)for(VFile child:file.list())collect(child,list);}
}
