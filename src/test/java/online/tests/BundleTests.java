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
    }
}
