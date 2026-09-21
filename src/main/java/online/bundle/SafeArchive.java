package online.bundle;

import online.net.Protocol;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** No archive path is ever extracted to a user directory. Limits apply after decompression. */
public final class SafeArchive {
    public static final int MAX_FILES=4096, MAX_MEMBER=16*1024*1024, MAX_EXPANDED=64*1024*1024;
    private SafeArchive() {}
    public static String path(String name) throws IOException {
        if(name==null || name.isEmpty() || name.length()>240 || name.indexOf('\\')>=0 || name.indexOf(':')>=0 || name.startsWith("/"))
            throw new IOException("Invalid archive path");
        for(char c:name.toCharArray()) if(c<32 || c==127) throw new IOException("Control character in archive path");
        for(String part:name.split("/",-1)) if(part.isEmpty() || part.equals(".") || part.equals("..")) throw new IOException("Invalid archive path segment");
        return name;
    }
    public static Map<String,byte[]> read(Path file) throws IOException {
        if(Files.size(file)>Protocol.MAX_BUNDLE) throw new IOException("Compressed bundle exceeds 32 MiB");
        Map<String,byte[]> result=new TreeMap<>(); long total=0;
        try(ZipInputStream zip=new ZipInputStream(Files.newInputStream(file))) {
            ZipEntry entry;
            byte[] buffer=new byte[8192];
            while((entry=zip.getNextEntry())!=null) {
                if(entry.isDirectory()) throw new IOException("Directory entries are not part of the bundle format");
                String name=path(entry.getName());
                if(result.containsKey(name) || result.size()>=MAX_FILES) throw new IOException("Duplicate or excessive archive entries");
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                int n;
                while((n=zip.read(buffer))!=-1) {
                    total+=n;
                    if((long)out.size()+n>MAX_MEMBER || total>MAX_EXPANDED) throw new IOException("Expanded bundle exceeds safety limits");
                    out.write(buffer,0,n);
                }
                result.put(name,out.toByteArray());
                zip.closeEntry();
            }
        }
        if(result.isEmpty()) throw new IOException("Empty bundle");
        return result;
    }
    public static void write(Path file,Map<String,byte[]> data) throws IOException {
        if(data.isEmpty() || data.size()>MAX_FILES) throw new IOException("Too many bundle files");
        long total=0;
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(file))) {
            for(Map.Entry<String,byte[]> e:new TreeMap<>(data).entrySet()) {
                path(e.getKey()); total+=e.getValue().length;
                if(e.getValue().length>MAX_MEMBER || total>MAX_EXPANDED) throw new IOException("Custom assets exceed bundle limits");
                ZipEntry entry=new ZipEntry(e.getKey()); entry.setTime(0);
                zip.putNextEntry(entry); zip.write(e.getValue()); zip.closeEntry();
            }
        } catch(IOException e) { Files.deleteIfExists(file); throw e; }
        if(Files.size(file)>Protocol.MAX_BUNDLE) { Files.deleteIfExists(file); throw new IOException("Compressed custom assets exceed 32 MiB"); }
    }
}
