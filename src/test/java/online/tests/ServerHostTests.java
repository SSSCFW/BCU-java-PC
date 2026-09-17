package online.tests;
import online.net.*;
import online.net.config.ServerConfig;
import online.ui.FriendServerPanel;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;

public final class ServerHostTests {
    public static void run() throws Exception {
        Properties p=new Properties();p.setProperty("bind","127.0.0.1");p.setProperty("controlPort","0");p.setProperty("udpPort","0");
        int udp;
        try(ServerHost host=ServerHost.start(ServerConfig.from(p))){
            udp=host.server().udpPort();Check.that(udp>0&&host.server().getPort()>0,"shared dedicated/embedded host binds both transports");
            Check.equal("ws://127.0.0.1:"+host.server().getPort(),host.localControlUrl(),"advertised local control address");
            Check.equal(1,host.candidateUrls().size(),"specific bind does not advertise inaccessible other adapters");
            Check.that(host.description().contains("DUEL_1V1"),"launcher identifies playable mode");
        }
        try(DatagramSocket rebound=new DatagramSocket(new InetSocketAddress("127.0.0.1",udp))){Check.equal(udp,rebound.getLocalPort(),"UDP port released on shared host close");}
        try(ServerSocket occupied=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){
            int free;try(DatagramSocket probe=new DatagramSocket(0)){free=probe.getLocalPort();}
            p.setProperty("controlPort",""+occupied.getLocalPort());p.setProperty("udpPort",""+free);
            Check.rejects(()->ServerHost.start(ServerConfig.from(p)),"TCP bind failure reported instead of false start success");
            try(DatagramSocket rebound=new DatagramSocket(new InetSocketAddress("127.0.0.1",free))){Check.equal(free,rebound.getLocalPort(),"startup failure also closes UDP socket");}
        }
        URI lan=new URI("ws://192.168.1.10:8766");
        Check.rejects(()->RoomClient.validateUri(lan,false),"LAN plaintext still requires explicit approval");
        Check.equal(lan,RoomClient.validateUri(lan,true),"explicit private LAN accepted");
        Check.equal("wss",RoomClient.validateUri(new URI("wss://example.com"),false).getScheme(),"public WSS accepted");
        Check.rejects(()->RoomClient.validateUri(new URI("ws://8.8.8.8:8766"),true),"private-network flag never permits public plaintext");
        Check.rejects(()->RoomClient.validateUri(new URI("ws://example.com:8766"),true),"no DNS-based private trust bypass");
        Check.equal("100.64.0.1",RoomClient.validateUri(new URI("ws://100.64.0.1:8766"),true).getHost(),"private VPN range allowed explicitly");
        Check.rejects(()->RoomClient.validateUri(new URI("ws://100.128.0.1:8766"),true),"CGNAT range boundary enforced");
        SwingUtilities.invokeAndWait(()->{FriendServerPanel panel=new FriendServerPanel(url->{});Check.that(panel.getComponentCount()>=2,"embedded server panel constructed headless");panel.close();});
    }
}
