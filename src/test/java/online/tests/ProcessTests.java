package online.tests;

import online.net.RoomServer;
import java.net.*;
import java.util.Arrays;
import java.nio.file.*;
import java.util.concurrent.*;

public final class ProcessTests {
    public static void run() throws Exception {
        Path dir=Files.createTempDirectory("bcu-pvp-e2e-");
        RoomServer server=new RoomServer(new InetSocketAddress("127.0.0.1",0));server.start();
        if(!server.awaitStarted(5,TimeUnit.SECONDS))throw new AssertionError("Server start");
        String java=Paths.get(System.getProperty("java.home"),"bin","java").toString();String cp=System.getProperty("java.class.path");
        Process host=null,guest=null;
        try {
            host=new ProcessBuilder(java,"-ea","-Djava.awt.headless=true","-cp",cp,"online.tests.HeadlessPeer","host",""+server.getPort(),dir.toString(),"240").redirectErrorStream(true).redirectOutput(dir.resolve("host.log").toFile()).start();
            guest=new ProcessBuilder(java,"-ea","-Djava.awt.headless=true","-cp",cp,"online.tests.HeadlessPeer","guest",""+server.getPort(),dir.toString(),"240").redirectErrorStream(true).redirectOutput(dir.resolve("guest.log").toFile()).start();
            Check.that(host.waitFor(30,TimeUnit.SECONDS),"host process completes");Check.that(guest.waitFor(5,TimeUnit.SECONDS),"guest process completes");
            if(host.exitValue()!=0||guest.exitValue()!=0)throw new AssertionError("End-to-end peers failed; logs: "+dir+"\nHOST\n"+new String(Files.readAllBytes(dir.resolve("host.log")))+"\nGUEST\n"+new String(Files.readAllBytes(dir.resolve("guest.log"))));
            for(int tick=60;tick<=240;tick+=60)Check.that(Arrays.equals(Files.readAllBytes(dir.resolve("host-"+tick)),Files.readAllBytes(dir.resolve("guest-"+tick))),"independent JVM state hash agrees at tick "+tick);
            System.out.println("Independent JVM integration logs: "+dir);
        }finally{if(host!=null&&host.isAlive())host.destroyForcibly();if(guest!=null&&guest.isAlive())guest.destroyForcibly();server.stop(1000);}
    }
    public static void main(String[] args)throws Exception{run();System.out.println("Process tests passed: "+Check.count);}
}
