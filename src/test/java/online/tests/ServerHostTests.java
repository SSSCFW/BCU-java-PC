package online.tests;
import online.net.*;
import online.net.config.ServerConfig;
import online.ui.FriendServerPanel;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;

public final class ServerHostTests {
    private static final RoomClient.Listener NOOP=new RoomClient.Listener(){
        public void event(com.google.gson.JsonObject event){}
        public void bundle(int playerId,Path verifiedArchive,String hash){try{Files.deleteIfExists(verifiedArchive);}catch(Exception ignored){}}
        public void failed(String reason){}
    };
    private static final class PortProbeClient extends RoomClient {
        PortProbeClient(int udpOverride)throws Exception{super(new URI("ws://127.0.0.1:8766"),true,true,udpOverride,NOOP);}
        InetSocketAddress endpoint(int offered)throws Exception{return udpEndpoint("",offered);}
    }
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

        int customTcp,customUdp;
        try(ServerSocket probe=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){customTcp=probe.getLocalPort();}
        try(DatagramSocket probe=new DatagramSocket(new InetSocketAddress("127.0.0.1",0))){customUdp=probe.getLocalPort();}
        Properties custom=new Properties();custom.setProperty("bind","127.0.0.1");
        custom.setProperty("controlPort",Integer.toString(customTcp));custom.setProperty("udpPort",Integer.toString(customUdp));
        try(ServerHost host=ServerHost.start(ServerConfig.from(custom))){
            Check.equal(customTcp,host.server().getPort(),"custom TCP port is used by the PvP host");
            Check.equal(customUdp,host.server().udpPort(),"custom UDP port is used by the PvP host");
            Check.equal("ws://127.0.0.1:"+customTcp,host.localControlUrl(),"custom TCP port is advertised to participants");
        }

        try(PortProbeClient automatic=new PortProbeClient(0);PortProbeClient overridden=new PortProbeClient(19001)){
            Check.equal(8767,automatic.endpoint(8767).getPort(),"UDP override 0 keeps server-advertised port");
            Check.equal(19001,overridden.endpoint(8767).getPort(),"participant UDP override replaces advertised port");
        }
        Check.rejects(()->new PortProbeClient(65536),"participant rejects invalid UDP override above 65535");
        try(ServerSocket occupied=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){
            int free;try(DatagramSocket probe=new DatagramSocket(0)){free=probe.getLocalPort();}
            p.setProperty("controlPort",""+occupied.getLocalPort());p.setProperty("udpPort",""+free);
            Check.rejects(()->ServerHost.start(ServerConfig.from(p)),"TCP bind failure reported instead of false start success");
            try(DatagramSocket rebound=new DatagramSocket(new InetSocketAddress("127.0.0.1",free))){Check.equal(free,rebound.getLocalPort(),"startup failure also closes UDP socket");}
        }
        URI lan=new URI("ws://192.168.1.10:8766");
        Check.rejects(()->RoomClient.validateUri(lan,false),"non-loopback plaintext still requires explicit approval");
        Check.equal(lan,RoomClient.validateUri(lan,true),"explicit plain WS accepts private LAN");
        Check.equal("wss",RoomClient.validateUri(new URI("wss://example.com"),false).getScheme(),"public WSS accepted without opt-in");
        Check.equal("8.8.8.8",RoomClient.validateUri(new URI("ws://8.8.8.8:8766"),true).getHost(),"explicit plain WS accepts public IPv4");
        Check.equal("example.com",RoomClient.validateUri(new URI("ws://example.com:8766"),true).getHost(),"explicit plain WS accepts public DNS host");
        Check.rejects(()->RoomClient.validateUri(new URI("ws://8.8.8.8:8766"),false),"public plaintext remains blocked without opt-in");
        Check.equal("100.64.0.1",RoomClient.validateUri(new URI("ws://100.64.0.1:8766"),true).getHost(),"explicit plain WS accepts VPN range");
        SwingUtilities.invokeAndWait(()->{FriendServerPanel panel=new FriendServerPanel(url->{});Check.that(panel.getComponentCount()>=2,"embedded server panel constructed headless");panel.close();});
    }
}
