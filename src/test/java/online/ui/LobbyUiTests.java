package online.ui;

import common.battle.*;
import common.battle.data.CustomUnit;
import common.util.unit.Unit;
import main.MainBCU;
import online.net.*;
import online.tests.*;
import page.MainFrame;
import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Actual Swing pages and independent JVMs, not a mock RoomClient. Run under xvfb-run in CI. */
public final class LobbyUiTests {
    private static OnlineLobbyPage page;
    private static Path shared;
    private static <T> T edt(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work); SwingUtilities.invokeAndWait(task); return task.get();
    }
    private static Object field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }
    private static JTextField text(String name) throws Exception { return (JTextField) field(page, name); }
    private static JButton button(String name) throws Exception { return (JButton) field(page, name); }
    private static String status() throws Exception { return edt(() -> ((JTextArea)field(page,"status")).getText()); }
    private static void await(Callable<Boolean> condition, String reason) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < end) { if (edt(condition)) return; Thread.sleep(20); }
        throw new AssertionError(reason + "\n" + status());
    }
    private static void disposePage() throws Exception {
        edt(() -> { Method m = OnlineLobbyPage.class.getDeclaredMethod("cleanup"); m.setAccessible(true); m.invoke(page); return null; });
    }
    private static void newPage() throws Exception {
        edt(() -> { page = new OnlineLobbyPage(null); MainFrame.F.setContentPane(page); MainFrame.F.validate(); return null; });
    }
    private static void init(Path root) throws Exception {
        Fixture.init(); Fixture.root = root; Files.createDirectories(root); FixtureAssets.init();
        Unit u = Fixture.unit("same_local_pack", 1000000);
        CustomUnit d = (CustomUnit)u.forms[0].du; d.price=1;d.speed=500;d.range=250;
        BasisLU b = Fixture.lineup(u);BasisSet.current().lb.add(b);BasisSet.current().sele=b;
        edt(() -> { MainBCU.author=""; MainFrame.F=new MainFrame("lobby regression");MainFrame.F.setSize(1200,900); return null; });
        newPage();
    }
    private static void config(int port) throws Exception {
        byte[] bytes=("bind=127.0.0.1\ncontrolPort="+port+"\nudpPort=0\n").getBytes(StandardCharsets.UTF_8);
        Files.write(Fixture.root.resolve("pvp-server.properties"),bytes);
        // Deliberately different CWD: the GUI must use the BCU installation config.
        Files.write(Paths.get("pvp-server.properties"),"notTheBcuConfig=true\n".getBytes(StandardCharsets.UTF_8));
    }
    private static ServerHost startHost() throws Exception {
        config(0);
        edt(() -> { ((JButton)field(field(page,"friendServer"),"toggle")).doClick();return null; });
        await(() -> field(field(page,"friendServer"),"host")!=null,"embedded server did not start");
        return edt(() -> (ServerHost)field(field(page,"friendServer"),"host"));
    }
    private static void setup(String name, String password) throws Exception {
        edt(() -> { text("name").setText(name);text("password").setText(password);((JCheckBox)field(page,"share")).setSelected(true);return null; });
    }
    private static void create() throws Exception { edt(() -> { button("create").doClick();return null; }); }
    private static void test(String mode) throws Exception {
        switch(mode) {
            case "save":
                edt(() -> {text("name").setText("友人テスト名");text("server").setText("wss://example.invalid:443/bcu");text("password").setText("never-save-this");text("room").setText("never-save-room");return null;});
                disposePage();
                Path prefs=Fixture.root.resolve("user/online-client.properties");
                Check.that(Files.isRegularFile(prefs),"entered display name/server must be saved on exit without connecting");
                String content=new String(Files.readAllBytes(prefs),StandardCharsets.UTF_8);
                Check.that(!content.contains("never-save"),"password and room ID must never be persisted");break;
            case "load":
                Check.equal("友人テスト名",edt(()->text("name").getText()),"display name restored across JVM restart");
                Check.equal("wss://example.invalid:443/bcu",edt(()->text("server").getText()),"server address restored across JVM restart");
                Check.equal("",edt(()->text("password").getText()),"password not restored");
                Check.that(!edt(()->((JCheckBox)field(page,"development")).isSelected()),"private-network trust not silently restored");break;
            case "retry": {
                ServerHost host=startHost();String url=host.localControlUrl();
                int unused;try(ServerSocket socket=new ServerSocket(0)){unused=socket.getLocalPort();}
                setup("Retry","test-password");final int port=unused;
                edt(()->{text("server").setText("ws://127.0.0.1:"+port);return null;});create();
                await(()->button("create").isEnabled(),"failed connection must re-enable setup without exiting/destroying host");
                Check.that(edt(()->field(field(page,"friendServer"),"host"))==host,"failure retains the embedded host");
                edt(()->{text("server").setText(url);return null;});create();await(()->button("copyRoom").isEnabled(),"retry did not create a room");
                RoomClient old=edt(()->(RoomClient)field(page,"client"));
                Field listenerField=RoomClient.class.getDeclaredField("listener");listenerField.setAccessible(true);
                RoomClient.Listener oldListener=(RoomClient.Listener)listenerField.get(old);
                edt(()->{button("leave").doClick();return null;});
                Check.that(edt(()->field(field(page,"friendServer"),"host"))==host,"leaving the room must retain the embedded host");
                Check.that(edt(()->button("create").isEnabled()),"same lobby is reusable after leaving the room");
                create();await(()->button("copyRoom").isEnabled(),"new room after leaving");
                RoomClient fresh=edt(()->(RoomClient)field(page,"client"));
                oldListener.event(Protocol.message("joined"));oldListener.failed("stale previous socket");
                Path late=Files.createTempFile("bcu-late-bundle-",".zip");oldListener.bundle(1,late,"");Thread.sleep(100);
                Check.that(edt(()->field(page,"client"))==fresh&&fresh.isOpen(),"old callbacks cannot close or mutate new attempt");
                Check.that(!Files.exists(late),"late bundle callback cleans its temporary file");break;
            }
            case "room-info": {
                startHost();setup("Host","test-password");create();await(()->button("copyRoom").isEnabled(),"room creation");
                await(()->!((RoomClient)field(page,"client")).realtimeTransport().equals("PROBING"),"transport selection");
                Thread.sleep(150);
                Check.that(status().contains(edt(()->text("room").getText())),"transport status must retain the room ID");break;
            }
            case "local-button": {
                config(23456);
                edt(()->{
                    Object panel=field(page,"friendServer");JButton helper=null;
                    for(java.awt.Component c:((JPanel)panel).getComponents())if(c instanceof JPanel)for(java.awt.Component child:((JPanel)c).getComponents())
                        if(child instanceof JButton&&((JButton)child).getText().equals("このPCのサーバーを使う"))helper=(JButton)child;
                    Check.that(helper!=null,"same-PC helper must be available without starting a second server");helper.doClick();return null;
                });
                Check.equal("ws://127.0.0.1:23456",edt(()->text("server").getText()),"same-PC helper uses configured TCP port");
                Check.equal(null,edt(()->field(field(page,"friendServer"),"host")),"local helper does not bind a second server");break;
            }
            case "duel-host": case "duel-guest": {
                boolean host=mode.equals("duel-host");
                if(host){
                    ServerHost h=startHost();setup("同一PCホスト","");create();
                    await(()->button("copyRoom").isEnabled(),"blank-password room creation from real Swing page");
                    Properties p=new Properties();p.setProperty("url",h.localControlUrl());p.setProperty("room",edt(()->text("room").getText()));
                    Path tmp=shared.resolve("room.tmp");try(java.io.Writer out=Files.newBufferedWriter(tmp,StandardCharsets.UTF_8)){p.store(out,"");}
                    Files.move(tmp,shared.resolve("room.properties"));
                }else{
                    long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
                    while(!Files.exists(shared.resolve("room.properties"))){if(System.nanoTime()>end)throw new AssertionError("host room not published");Thread.sleep(20);}
                    Properties p=new Properties();try(java.io.Reader in=Files.newBufferedReader(shared.resolve("room.properties"),StandardCharsets.UTF_8)){p.load(in);}
                    setup("同一PCゲスト","");edt(()->{text("server").setText(p.getProperty("url"));text("room").setText(p.getProperty("room"));button("join").doClick();return null;});
                }
                await(()->button("ready").isEnabled(),"GUI asset synchronization/ready barrier");
                edt(()->{button("ready").doClick();return null;});
                await(()->field(page,"battle")!=null,"GUI battle starts");
                edt(()->{((JComboBox<?>)field(page,"render")).setSelectedItem(host?30:60);((JButton[])field(page,"units"))[0].doClick();return null;});
                await(()->((PvpStageBasis)field(page,"battle")).time>=150,"GUI battle must run 150 ticks without desync");
                Files.write(shared.resolve(host?"host-done":"guest-done"),new byte[]{1});
                long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                while(!Files.exists(shared.resolve(host?"guest-done":"host-done"))){if(System.nanoTime()>end)throw new AssertionError("other peer stalled");Thread.sleep(20);}
                System.out.println("GUI_DUEL_OK "+mode+" 150 ticks / 30-60 FPS / empty password");break;
            }
            default:throw new AssertionError("Unknown test "+mode);
        }
    }
    public static void main(String[] args) throws Exception {
        if(GraphicsEnvironment.isHeadless())throw new AssertionError("Run LobbyUiTests under xvfb-run (actual Swing required)");
        if(args.length>0){
            int result=0;
            try{shared=Paths.get(args[2]);init(Paths.get(args[1]));test(args[0]);System.out.println("GUI_OK "+args[0]);}
            catch(Throwable e){result=1;e.printStackTrace();if(page!=null)try{System.err.println(status());}catch(Exception ignored){}}
            finally{if(page!=null)try{disposePage();}catch(Exception ignored){}if(MainFrame.F!=null)edt(()->{MainFrame.F.dispose();return null;});}
            System.exit(result);return;
        }
        Path root=Files.createTempDirectory("bcu-lobby-ui-");Path logs=Paths.get("target/lobby-ui-logs").toAbsolutePath();Files.createDirectories(logs);
        for(String mode:Arrays.asList("save","load","retry","room-info","local-button")){
            Path user=root.resolve(mode.equals("save")||mode.equals("load")?"persist":mode);
            Process p=spawn(mode,user,root,logs);finish(p,mode,logs);
        }
        Process host=spawn("duel-host",root.resolve("host"),root,logs),guest=spawn("duel-guest",root.resolve("guest"),root,logs);
        try{finish(host,"duel-host",logs);finish(guest,"duel-guest",logs);}finally{host.destroyForcibly();guest.destroyForcibly();}
        System.out.println("Real Swing lobby regression tests passed; transcripts: "+logs);
    }
    private static Process spawn(String mode,Path user,Path shared,Path logs)throws Exception{
        Path cwd=Files.createDirectories(user.resolve("working-directory"));
        StringJoiner cp=new StringJoiner(java.io.File.pathSeparator);
        for(String item:System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)))cp.add(Paths.get(item).toAbsolutePath().toString());
        return new ProcessBuilder(Paths.get(System.getProperty("java.home"),"bin","java").toString(),"-ea","-Dfile.encoding=UTF-8","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","-cp",cp.toString(),LobbyUiTests.class.getName(),mode,user.toString(),shared.toString())
                .directory(cwd.toFile()).redirectErrorStream(true).redirectOutput(logs.resolve(mode+".log").toFile()).start();
    }
    private static void finish(Process p,String mode,Path logs)throws Exception{
        if(!p.waitFor(40,TimeUnit.SECONDS)){p.destroyForcibly();throw new AssertionError("GUI timeout: "+mode);}
        String transcript=new String(Files.readAllBytes(logs.resolve(mode+".log")),StandardCharsets.UTF_8);System.out.print(transcript);
        if(p.exitValue()!=0)throw new AssertionError("GUI test failed: "+mode);
    }
}
