package online.tests;

import online.net.RoomServer;
import org.java_websocket.WebSocket;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LifecycleTests {
    public static void run() throws Exception {
        RoomServer server=new RoomServer(new InetSocketAddress("127.0.0.1",0));
        CountDownLatch closed=new CountDownLatch(1);AtomicBoolean heldRoomLock=new AtomicBoolean();
        WebSocket socket=(WebSocket)Proxy.newProxyInstance(WebSocket.class.getClassLoader(),new Class[]{WebSocket.class},(proxy,method,args)->{
            switch(method.getName()) {
                case "close":heldRoomLock.set(Thread.holdsLock(server)||Thread.holdsLock(server.core()));closed.countDown();return null;
                case "isOpen":return true;
                case "hashCode":return System.identityHashCode(proxy);
                case "equals":return proxy==args[0];
                case "toString":return "test-socket";
                default:return null;
            }
        });
        server.start();Check.that(server.awaitStarted(5,TimeUnit.SECONDS),"lifecycle relay starts");
        try {
            server.onMessage(socket,"{}");
            Check.that(closed.await(2,TimeUnit.SECONDS),"malformed peer is closed");
            Check.that(!heldRoomLock.get(),"socket close never holds room monitor (selector callback lock inversion)");
        }finally{server.stop(1000);}
    }
    public static void main(String[] args)throws Exception{run();}
}
