package online.tests;
import java.net.*;
import java.io.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/** Real UDP forwarding with deterministic loss, duplicate and reorder injection in each direction. */
final class UdpLossProxy implements AutoCloseable {
    private final DatagramSocket socket;
    private final Thread reader;
    private volatile InetSocketAddress target,client;
    private volatile boolean closed;
    volatile boolean blocked;
    volatile long blockUntil;
    volatile boolean impair;
    final AtomicInteger dropped=new AtomicInteger(),duplicated=new AtomicInteger(),reordered=new AtomicInteger();
    private final int[] counts=new int[2];
    private final byte[][] held=new byte[2][];
    UdpLossProxy()throws IOException{
        socket=new DatagramSocket(new InetSocketAddress("127.0.0.1",0));socket.setSoTimeout(100);
        reader=new Thread(this::run,"pvp-test-loss-proxy");reader.setDaemon(true);reader.start();
    }
    InetSocketAddress route(InetSocketAddress target){this.target=target;return new InetSocketAddress("127.0.0.1",socket.getLocalPort());}
    private void run(){while(!closed){
        byte[] bytes=new byte[2048];DatagramPacket packet=new DatagramPacket(bytes,bytes.length);
        try{
            socket.receive(packet);InetSocketAddress source=(InetSocketAddress)packet.getSocketAddress();InetSocketAddress server=target;if(server==null)continue;
            boolean down=source.equals(server);if(!down)client=source;InetSocketAddress dest=down?client:server;if(dest==null)continue;
            byte[] data=Arrays.copyOf(bytes,packet.getLength());
            if(blocked||System.nanoTime()<blockUntil){dropped.incrementAndGet();continue;}
            int direction=down?1:0;boolean realtime=data.length>6&&data[6]==3;
            if(impair&&realtime){int n=++counts[direction];
                if(n%7==2){dropped.incrementAndGet();continue;}
                if(n%11==4){held[direction]=data;continue;}
                if(n%13==6){send(data,dest);duplicated.incrementAndGet();}
            }
            send(data,dest);
            if(held[direction]!=null){send(held[direction],dest);held[direction]=null;reordered.incrementAndGet();}
        }catch(SocketTimeoutException ignored){}catch(IOException e){if(!closed)throw new RuntimeException(e);}
    }}
    private void send(byte[] bytes,InetSocketAddress dest)throws IOException{socket.send(new DatagramPacket(bytes,bytes.length,dest));}
    public void close(){closed=true;socket.close();try{reader.join(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}
