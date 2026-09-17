package online.net;

import com.google.gson.JsonObject;
import online.tests.Check;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.security.SecureRandom;

public final class CheckpointTests {
    public static void run() throws Exception {
        RoomServer server=new RoomServer(new InetSocketAddress("127.0.0.1",0));
        Room room=new Room("test","test","test","test-password",0,new SecureRandom());room.started=true;room.tick=120;
        Method hash=RoomServer.class.getDeclaredMethod("hash",Room.class,int.class,JsonObject.class);hash.setAccessible(true);
        hash.invoke(server,room,0,message(60,'a'));hash.invoke(server,room,0,message(120,'a'));
        hash.invoke(server,room,1,message(60,'b'));
        Check.that(room.closed,"late checkpoint mismatch cannot be hidden by a newer checkpoint");
        server.stop(1000);
    }
    private static JsonObject message(int tick,char c){JsonObject o=new JsonObject();o.addProperty("tick",tick);char[] h=new char[64];java.util.Arrays.fill(h,c);o.addProperty("hash",new String(h));return o;}
    public static void main(String[] args)throws Exception{run();}
}
