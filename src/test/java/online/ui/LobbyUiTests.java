package online.ui;

import common.battle.*;
import common.CommonStatic;
import common.battle.data.CustomUnit;
import common.util.unit.Unit;
import main.MainBCU;
import online.net.*;
import online.tests.*;
import page.MainFrame;
import page.KeyHandler;
import page.battle.BattleInfoPage;
import page.awt.BBBuilder;
import page.awt.AWTBBB;
import java.awt.event.KeyEvent;
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
    private static volatile Throwable uiFailure;
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
        while (System.nanoTime() < end) { if(uiFailure!=null)throw new AssertionError("EDT failed",uiFailure); if (edt(condition)) return; Thread.sleep(20); }
        throw new AssertionError(reason + "\n" + status());
    }
    private static void screenshot(java.awt.Component component,String name) throws Exception {
        java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(component.getWidth(),component.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g=image.createGraphics();try{component.paint(g);}finally{g.dispose();}
        javax.imageio.ImageIO.write(image,"png",shared.resolve("room-"+name+".png").toFile());
    }
    private static void disposePage() throws Exception {
        edt(() -> { Method m = OnlineLobbyPage.class.getDeclaredMethod("cleanup"); m.setAccessible(true); m.invoke(page); return null; });
    }
    private static void newPage() throws Exception {
        edt(() -> { page = new OnlineLobbyPage(null); MainFrame.changePanel(page); page.componentResized(MainFrame.F.getRootPane().getWidth(),MainFrame.F.getRootPane().getHeight()); MainFrame.F.validate(); return null; });
    }
    private static void init(Path root) throws Exception {
        Fixture.init(); Fixture.root = root; Files.createDirectories(root); FixtureNativeUi.init(); BBBuilder.def=AWTBBB.INS;
        Unit u = FixtureNativeUi.unit("same_local_pack", 0xffccaa55);
        CustomUnit d = (CustomUnit)u.forms[0].du; d.price=1;d.speed=500;d.range=250;
        BasisLU b = Fixture.lineup(u);BasisSet.current().lb.add(b);BasisSet.current().sele=b;
        Unit alternate=FixtureNativeUi.unit("edited_in_lobby",0xffaa77cc);CustomUnit ad=(CustomUnit)alternate.forms[0].du;ad.price=1;ad.speed=80;ad.range=250;
        BasisSet.current().lb.add(Fixture.lineup(alternate));
        common.pack.UserProfile.getBCData().bgs.set(4,new common.util.pack.Background(new common.pack.Identifier<>("000000",common.util.pack.Background.class,4),FixtureNativeUi.image(512,256,0xffaaccdd)));
        common.pack.UserProfile.getBCData().musics.set(6,new common.util.stage.Music(new common.pack.Identifier<>("000000",common.util.stage.Music.class,6),0,new common.system.files.FDByte(new byte[]{1,2,3})));
        edt(() -> { MainBCU.author=""; MainFrame.F=new MainFrame("lobby regression");MainFrame.F.setSize(1200,900); return null; });
        newPage();
        Check.that(Arrays.stream(OnlineLobbyPage.class.getDeclaredFields()).noneMatch(f->f.getName().equals("share")),"connection page must not keep Pack-sharing consent control");
        Check.that(Arrays.stream(RoomLobbyPage.class.getDeclaredFields()).noneMatch(f->f.getName().equals("share")),"room lobby must not keep Pack-sharing consent control");
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
        edt(() -> { text("name").setText(name);text("password").setText(password);return null; });
    }
    private static void create() throws Exception { edt(() -> { button("create").doClick();return null; }); }
    private static void test(String mode) throws Exception {
        switch(mode) {
            case "finish-host": case "finish-guest": case "timeout-host": case "timeout-guest":
                finishDuel(mode);break;
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
                ServerHost host=startHost();setup("Host","test-password");create();await(()->button("copyRoom").isEnabled(),"room creation");
                await(()->!((RoomClient)field(page,"client")).realtimeTransport().equals("PROBING"),"transport selection");
                Thread.sleep(150);
                Check.that(status().contains(edt(()->text("room").getText())),"transport status must retain the room ID");
                edt(()->{
                    @SuppressWarnings("unchecked") JComboBox<BasisLU> choices=(JComboBox<BasisLU>)field(page,"lineup");
                    BasisLU empty=new BasisLU(BasisSet.current());empty.name="empty regression lineup";
                    choices.addItem(empty);choices.setSelectedItem(empty);return null;
                });
                await(()->!((Boolean)field(field(page,"roomLobby"),"pending")),"empty lineup selection acknowledgement");
                Check.that(!edt(()->button("ready").isEnabled()),"zero-unit saved lineup cannot become ready");

                page.failed("synthetic prepare failure");
                await(()->MainFrame.getPanel()==page&&button("create").isEnabled(),"prepare failure returns to reusable connection page");
                Check.that(edt(()->field(field(page,"friendServer"),"host"))==host,"prepare failure must retain embedded friend server");
                create();await(()->button("copyRoom").isEnabled(),"room can be created again after prepare failure");
                Check.that(edt(()->field(field(page,"friendServer"),"host"))==host,"retry uses the same embedded friend server");
                break;
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
                await(()->field(page,"roomLobby")!=null&&field(field(page,"roomLobby"),"state")!=null&&((com.google.gson.JsonObject)field(field(page,"roomLobby"),"state")).getAsJsonArray("players").size()==2,"dedicated room lobby roster");
                edt(()->{
                    RoomLobbyPage lobby=(RoomLobbyPage)field(page,"roomLobby");
                    Check.that(MainFrame.getPanel()==lobby,"joining opens dedicated RoomLobbyPage");
                    Check.equal(null,field(page,"localArchive"),"no character export before every player confirms lobby");
                    JComboBox<?> lineupChoices=(JComboBox<?>)field(page,"lineup");
                    Check.equal("ランダム",String.valueOf(lineupChoices.getItemAt(0)),"random lineup is the first lineup choice");
                    Check.equal("ランダム(バニラ)",String.valueOf(lineupChoices.getItemAt(1)),"vanilla random lineup is the second lineup choice");
                    Check.equal("ランダム",String.valueOf(((JComboBox<?>)field(lobby,"background")).getItemAt(0)),"random background option is first");
                    JComboBox<?> battleMusic=(JComboBox<?>)field(lobby,"music");
                    Check.equal("003.ogg 日本侵略！",String.valueOf(battleMusic.getItemAt(0)),"curated BGM shows file id and Japanese name");
                    Check.equal("004.ogg 西表島の戦い",String.valueOf(battleMusic.getItemAt(1)),"curated BGM keeps requested ordering");
                    Check.equal("006.ogg チャレンジバトル",String.valueOf(battleMusic.getItemAt(2)),"curated BGM exposes only requested tracks");
                    Check.that(((JButton)field(lobby,"musicPreview")).isEnabled(),"BGM preview button is available");
                    Check.that(!((JButton)field(lobby,"musicStop")).isEnabled(),"BGM stop button is disabled until a preview starts");
                    Check.that(((JSpinner)field(lobby,"distance")).isEnabled()==host,"only host can edit distance");
                    Check.that(((JComboBox<?>)field(lobby,"special")).isEnabled()==host,"only host can edit cannon/roulette/none");
                    if(host){
                        ((JButton)field(lobby,"edit")).doClick();
                        Check.that(MainFrame.getPanel() instanceof page.basis.BasisPage,"lineup edit reuses original BasisPage");
                        ((JButton)field(MainFrame.getPanel(),"back")).doClick();
                        Check.that(MainFrame.getPanel()==lobby,"original editor returns to dedicated lobby");
                    }
                    return null;
                });
                await(()->!((Boolean)field(field(page,"roomLobby"),"pending")),"lineup editor acknowledgement");
                edt(()->{JComboBox<?> choices=(JComboBox<?>)field(page,"lineup");choices.setSelectedIndex(choices.getItemCount()-1);return null;});
                await(()->!((Boolean)field(field(page,"roomLobby"),"pending")),"changed lineup acknowledged");
                if(host){
                    edt(()->{
                        RoomLobbyPage lobby=(RoomLobbyPage)field(page,"roomLobby");
                        ((JSpinner)field(lobby,"distance")).setValue(8000);
                        ((JComboBox<?>)field(lobby,"background")).setSelectedIndex(2);
                        ((JComboBox<?>)field(lobby,"music")).setSelectedIndex(2);
                        ((JSpinner)field(lobby,"timeLimit")).setValue(1);
                        ((JComboBox<?>)field(lobby,"hostTrait")).setSelectedIndex(2); // red
                        ((JComboBox<?>)field(lobby,"guestTrait")).setSelectedIndex(1); // random
                        JCheckBox[] guestEx=(JCheckBox[])field(lobby,"guestTraitExclude");
                        int blackIndex=online.net.lobby.PvpTraitRules.optionIndex(common.util.Data.TRAIT_BLACK);
                        for(int i=0;i<guestEx.length;i++)if(i!=blackIndex)guestEx[i].doClick();
                        ((JComboBox<?>)field(lobby,"special")).setSelectedItem(online.net.lobby.RoomRules.SpecialMode.ROULETTE);
                        ((JCheckBox)field(lobby,"force60")).doClick();((JCheckBox)field(lobby,"debugMode")).doClick();
                        ((JButton)field(lobby,"apply")).doClick();return null;
                    });
                }
                await(()->((RoomClient)field(page,"client")).roomRules().castleDistance==8000,"host rules reach both room clients");
                Check.equal(online.net.lobby.RoomRules.SpecialMode.ROULETTE,((RoomClient)field(page,"client")).roomRules().specialMode,"host roulette rule reaches both clients");
                Check.that(((RoomClient)field(page,"client")).roomRules().debugMode,"host debug mode reaches both clients");
                Check.equal(1,((RoomClient)field(page,"client")).roomRules().timeLimitMinutes,"host time limit reaches both clients");
                Check.equal((int)common.util.Data.TRAIT_RED,((RoomClient)field(page,"client")).roomRules().hostTraitChoice,"host selected attribute reaches both clients");
                Check.equal(online.net.lobby.PvpTraitRules.RANDOM,((RoomClient)field(page,"client")).roomRules().guestTraitChoice,"guest random attribute reaches both clients");
                edt(()->{Object audio=field(field(page,"roomLobby"),"audio");((JSlider)field(audio,"bg")).setValue(host?23:81);((JSlider)field(audio,"se")).setValue(host?45:11);((JSlider)field(audio,"ui")).setValue(host?67:9);return null;});
                Files.write(shared.resolve(host?"host-lobby":"guest-lobby"),new byte[]{1});
                long readyDeadline=System.nanoTime()+10_000_000_000L;
                while(!Files.exists(shared.resolve(host?"guest-lobby":"host-lobby"))){if(System.nanoTime()>readyDeadline)throw new AssertionError("other lobby not edited");Thread.sleep(20);}
                await(()->button("ready").isEnabled(),"explicit editable-lobby readiness");
                edt(()->{BattleInfoPage.DEF_LARGE=true;screenshot(MainFrame.F.getRootPane(),host?"host":"guest");return null;});
                edt(()->{button("ready").doClick();return null;});
                await(()->field(page,"battle")!=null&&field(page,"battlePage")!=null&&MainFrame.getPanel()==field(page,"battlePage"),"GUI native battle page becomes current after 3DS asset load");
                edt(()->{
                    CommonStatic.getConfig().performanceModeBattle=!host;
                    CommonStatic.getConfig().performanceModeAnimation=!host;
                    BattleInfoPage nativePage=(BattleInfoPage)field(page,"battlePage");
                    Check.that(MainFrame.getPanel()==nativePage,"real native BattleInfoPage must be the current page");
                    Check.equal(60,nativePage.onlineFps(),"room force60 overrides a 30FPS preference locally");
                    Check.equal(!host,CommonStatic.getConfig().performanceModeBattle,"force60 must not rewrite saved client preference");
                    PvpStageBasis live=(PvpStageBasis)field(page,"battle");Check.equal(8000f,live.ubase.pos-live.ebase.pos,"exact castle separation from host rules");
                    Check.equal(4,live.st.bg.id,"host background selected");Check.equal(6,live.st.mus0.id,"host curated BGM selected");
                    Check.equal(online.net.lobby.RoomRules.SpecialMode.ROULETTE,live.specialMode(),"battle uses synchronized roulette special mode");
                    Check.equal(1,live.st.timeLimit,"battle uses host one-minute time limit");
                    Check.equal((int)common.util.Data.TRAIT_BLACK,live.leftTrait(),"guest random exclusions resolve left-side attribute to black");
                    Check.equal((int)common.util.Data.TRAIT_RED,live.rightTrait(),"host fixed attribute resolves right-side attribute to red");
                    Check.that(!((JLabel)field(nativePage,"timer")).isVisible(),"online page hides the oversized duplicate Swing timer; the smaller timer is rendered inside the battlefield");
                    PvpRouletteHud rouletteHud=(PvpRouletteHud)field(nativePage,"onlineSpecial");Check.that(rouletteHud.isVisible(),"native roulette HUD is visible in roulette mode");Check.that(rouletteHud.has3dsAssets(),"native battle page uses decoded 3DS roulette assets: "+Pvp3dsAssets.diagnostic());
                    JButton rouletteDebug=(JButton)field(nativePage,"rouletteDebugMax");
                    JLabel onlineLog=(JLabel)field(nativePage,"stream"),onlineMarker=(JLabel)field(nativePage,"onlineTag");
                    java.awt.Canvas battleCanvas=(java.awt.Canvas)field(nativePage,"bb");
                    Check.that(((AbstractButton)field(nativePage,"jtb")).isSelected(),"battle entered directly in large mode");
                    Check.that(rouletteDebug.isVisible()&&rouletteDebug.getWidth()>0&&rouletteDebug.getHeight()>0,"debug MAX button is laid out when entering directly in large mode");
                    Check.that(onlineLog.isVisible()&&onlineLog.getWidth()>0&&onlineLog.getHeight()>0,"online log is laid out when entering directly in large mode");
                    Check.that(onlineMarker.getY()+onlineMarker.getHeight()<=battleCanvas.getY(),
                            "Online marker stays above the battlefield instead of covering money/HUD");
                    Check.that(live.b.lu.fs[0][0].unit.id.pack.contains("pvp"),"edited lineup remains isolated by match");
                    Check.equal(host?23:81,io.BCMusic.VOL_BG,"individual lobby BGM gain retained");
                    ((JButton)field(nativePage,"audio")).doClick();JDialog dialog=(JDialog)field(nativePage,"audioDialog");
                    Check.that(dialog.isVisible()&&!dialog.isModal(),"battle audio settings are nonmodal and visible");
                    ((JSlider)field(dialog.getContentPane(),"bg")).setValue(host?31:72);
                    Check.equal(host?31:72,io.BCMusic.VOL_BG,"battle slider updates local volume immediately");
                    screenshot(dialog.getContentPane(),host?"battle-audio-host":"battle-audio-guest");
                    Check.that(!((JButton)field(nativePage,"paus")).isEnabled(),"native solo pause is disabled online");
                    Check.that(!((JButton)field(nativePage,"next")).isEnabled(),"native solo step is disabled online");
                    Check.that(!((JButton)field(nativePage,"rply")).isEnabled(),"native single-player replay is disabled online");
                    int before=((PvpStageBasis)field(page,"battle")).time;
                    nativePage.onTimer(1);
                    Check.equal(before,((PvpStageBasis)field(page,"battle")).time,"old page timer cannot advance online simulation");
                    ((AbstractButton)field(nativePage,"jtb")).doClick();
                    nativePage.componentResized(MainFrame.F.getRootPane().getWidth(),MainFrame.F.getRootPane().getHeight());
                    ((AbstractButton)field(nativePage,"row")).doClick();
                    Method press=KeyHandler.class.getDeclaredMethod("keyPressed",KeyEvent.class);press.setAccessible(true);
                    press.invoke(nativePage,new KeyEvent(nativePage,KeyEvent.KEY_PRESSED,System.currentTimeMillis(),0,KeyEvent.VK_Q,'q'));
                    nativePage.renderOnlineFrame();
                    Method release=KeyHandler.class.getDeclaredMethod("keyReleased",KeyEvent.class);release.setAccessible(true);
                    release.invoke(nativePage,new KeyEvent(nativePage,KeyEvent.KEY_RELEASED,System.currentTimeMillis(),0,KeyEvent.VK_Q,'q'));
                    nativePage.renderOnlineFrame();
                    return null;
                });
                await(()->((PvpStageBasis)field(page,"battle")).time>=150,"GUI battle must run 150 ticks without desync");
                Check.that(edt(()->((PvpStageBasis)field(page,"battle")).le.stream().anyMatch(e->e.dire==1)),"native host input spawned a left unit");
                Check.that(edt(()->((PvpStageBasis)field(page,"battle")).le.stream().anyMatch(e->e.dire==-1)),"native guest input spawned a right unit");
                // Retain the branch's synthetic result regression, alongside the natural
                // castle/timeout + real acknowledgement scenarios below.
                edt(()->{
                    BattleInfoPage nativePage=(BattleInfoPage)field(page,"battlePage");
                    nativePage.showOnlineResult(host?"勝利！":"敗北","両者の戦闘結果が一致しました。OKを押してください。",()->{});
                    Check.that(((JPanel)field(nativePage,"onlineBattleEnd")).isVisible(),"battle-end message is shown immediately on both clients");
                    Check.that(!((java.awt.Canvas)field(nativePage,"bb")).isVisible(),"heavyweight battle canvas is hidden behind end/result screens");return null;
                });
                await(()->((JPanel)field(field(page,"battlePage"),"onlineResult")).isVisible(),"result screen appears after battle-end sound completion");
                edt(()->{
                    BattleInfoPage nativePage=(BattleInfoPage)field(page,"battlePage");
                    JPanel result=(JPanel)field(nativePage,"onlineResult");
                    JLabel title=(JLabel)field(nativePage,"onlineResultTitle");
                    java.awt.Component detail=(java.awt.Component)field(nativePage,"onlineResultDetail");
                    JButton ok=(JButton)field(nativePage,"onlineResultOk");
                    Check.that(ok.isVisible()&&ok.isEnabled()&&ok.getWidth()>0&&ok.getHeight()>0,"OK button is visible and usable on both result screens");
                    Check.that(!title.getForeground().equals(result.getBackground()),"result title has readable foreground/background contrast");
                    Check.that(!detail.getForeground().equals(result.getBackground()),"result detail has readable foreground/background contrast");return null;
                });

                Files.write(shared.resolve(host?"host-done":"guest-done"),new byte[]{1});
                long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                while(!Files.exists(shared.resolve(host?"guest-done":"host-done"))){if(System.nanoTime()>end)throw new AssertionError("other peer stalled");Thread.sleep(20);}
                edt(()->{
                    BattleInfoPage nativePage=(BattleInfoPage)field(page,"battlePage");
                    if(nativePage!=null)((JButton)field(nativePage,"back")).doClick();
                    Check.that(MainFrame.getPanel()==field(page,"roomLobby"),"native Back returns to the existing room lobby, not connection setup");
                    if(host)Check.that(field(field(page,"friendServer"),"host")!=null,"native Back must not stop embedded friend server");
                    return null;
                });
                await(()->field(page,"battlePage")==null&&MainFrame.getPanel()==field(page,"roomLobby"),"both peers restore the editable room lobby after battle abort");
                edt(()->{
                    RoomLobbyPage lobby=(RoomLobbyPage)field(page,"roomLobby");
                    com.google.gson.JsonObject lobbyState=(com.google.gson.JsonObject)field(lobby,"state");
                    Check.equal("EDITING",lobbyState.get("phase").getAsString(),"returned room is editable for a rematch");
                    Check.that(((JButton)field(page,"ready")).isEnabled(),"returned room can ready for another battle");
                    Check.that(((JComboBox<?>)field(page,"lineup")).getActionListeners().length>0,"room lineup listener stays attached while remaining in the room");
                    return null;
                });
                // Keep both real clients connected until both independently observed the restored
                // room. Without this barrier the faster JVM may dispose its page and send LEAVE,
                // racing the slower peer's assertion even though battle-abort itself succeeded.
                Files.write(shared.resolve(host?"host-returned":"guest-returned"),new byte[]{1});
                long returnEnd=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                while(!Files.exists(shared.resolve(host?"guest-returned":"host-returned"))){
                    if(System.nanoTime()>returnEnd)throw new AssertionError("other peer did not observe restored room");
                    Thread.sleep(20);
                }
                System.out.println("GUI_DUEL_OK "+mode+" editable lobby / original editor / rules / live audio / forced60 / native BattleInfoPage / Back-to-room / 150 ticks");break;
            }
            default:throw new AssertionError("Unknown test "+mode);
        }
    }
    /** Natural engine result, real control/UDP clients, actual Swing OK buttons on BOTH peers. */
    private static void finishDuel(String mode)throws Exception{
        boolean host=mode.endsWith("-host"),timeout=mode.startsWith("timeout");
        RecordingMixerProvider.enable();RecordingMixerProvider.dropStop=!host;
        io.BCMusic.play=true;io.BCMusic.VOL_SE=60;MainBCU.loaded=false;
        byte[] ogg;
        try(java.io.InputStream in=PvpSoundBank.class.getResourceAsStream(PvpSoundBank.resourcePath(PvpSoundBank.Sound.ROULETTE_MAX));java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);ogg=out.toByteArray();
        }
        for(int id:new int[]{3,30})common.pack.UserProfile.getBCData().musics.set(id,new common.util.stage.Music(
                new common.pack.Identifier<>("000000",common.util.stage.Music.class,id),0,new common.system.files.FDByte(ogg)));
        edt(()->{
            BattleInfoPage.DEF_LARGE=true;
            BasisLU b=(BasisLU)((JComboBox<?>)field(page,"lineup")).getSelectedItem();
            CustomUnit u=(CustomUnit)b.lu.fs[0][0].du;
            u.speed=1200;u.range=800;u.atks[0].atk=10000000;u.atks[0].pre=1;
            return null;
        });
        if(host){
            ServerHost h=startHost();setup("終了テスト・ホスト","");create();await(()->button("copyRoom").isEnabled(),"ending test room creation");
            Properties p=new Properties();p.setProperty("url",h.localControlUrl());p.setProperty("room",edt(()->text("room").getText()));
            Path tmp=shared.resolve("room.tmp");try(java.io.Writer out=Files.newBufferedWriter(tmp,StandardCharsets.UTF_8)){p.store(out,"");}
            Files.move(tmp,shared.resolve("room.properties"));
        }else{
            barrier("room.properties");Properties p=new Properties();try(java.io.Reader in=Files.newBufferedReader(shared.resolve("room.properties"),StandardCharsets.UTF_8)){p.load(in);}
            setup("終了テスト・ゲスト","");edt(()->{text("server").setText(p.getProperty("url"));text("room").setText(p.getProperty("room"));button("join").doClick();return null;});
        }
        await(()->field(page,"roomLobby")!=null&&field(field(page,"roomLobby"),"state")!=null&&((com.google.gson.JsonObject)field(field(page,"roomLobby"),"state")).getAsJsonArray("players").size()==2,"ending roster");
        if(host)edt(()->{
            RoomLobbyPage lobby=(RoomLobbyPage)field(page,"roomLobby");
            ((JSpinner)field(lobby,"distance")).setValue(1000);((JSpinner)field(lobby,"timeLimit")).setValue(1);
            ((JComboBox<?>)field(lobby,"special")).setSelectedItem(online.net.lobby.RoomRules.SpecialMode.ROULETTE);
            ((JCheckBox)field(lobby,"debugMode")).doClick();((JButton)field(lobby,"apply")).doClick();return null;
        });
        await(()->((RoomClient)field(page,"client")).roomRules().castleDistance==1000,"ending rules synchronized");
        await(()->button("ready").isEnabled(),"ending ready control");
        edt(()->{button("ready").doClick();return null;});
        await(()->field(page,"battlePage")!=null&&MainFrame.getPanel()==field(page,"battlePage"),"ending native page");
        edt(()->{
            BattleInfoPage nativePage=(BattleInfoPage)field(page,"battlePage");
            Check.that(((AbstractButton)field(nativePage,"jtb")).isSelected(),"enters directly in large mode");
            java.awt.Canvas canvas=(java.awt.Canvas)field(nativePage,"bb");
            for(String name:new String[]{"rouletteDebugMax","rouletteNotice","onlineTag","stream"})
                Check.that(nativePage.getComponentZOrder((java.awt.Component)field(nativePage,name))<nativePage.getComponentZOrder(canvas),"initial large layering: "+name);
            io.BCMusic.play(new common.pack.Identifier<>("000000",common.util.stage.Music.class,3));return null;
        });
        Files.write(shared.resolve(host?"host-battle":"guest-battle"),new byte[]{1});barrier(host?"guest-battle":"host-battle");
        if(!timeout){
            edt(()->{((RoomClient)field(page,"client")).queueCommand(online.sync.InputFrame.DEBUG_ROULETTE_MAX);return null;});
            await(()->{PvpStageBasis b=(PvpStageBasis)field(page,"battle");return b.left().pvpRoulette.gauge==1000&&b.right().pvpRoulette.gauge==1000;},"both debug MAX commands synchronized");
            edt(()->{((RoomClient)field(page,"client")).queueCommand(online.sync.InputFrame.SPECIAL);return null;});
            await(()->{PvpStageBasis b=(PvpStageBasis)field(page,"battle");return b.left().pvpRoulette.pendingResult>=0&&b.right().pvpRoulette.pendingResult>=0;},"both real roulette results");
            await(()->RecordingMixerProvider.count(PvpSoundBank.Sound.ROULETTE_CONFIRM)==1&&RecordingMixerProvider.count(PvpSoundBank.Sound.OPPONENT_ROULETTE_CONFIRM)==1,"own and opponent confirmation PCM");
            for(PvpSoundBank.Sound sound:new PvpSoundBank.Sound[]{PvpSoundBank.Sound.ROULETTE_START,PvpSoundBank.Sound.ROULETTE_SPIN,PvpSoundBank.Sound.OPPONENT_ROULETTE_START})
                Check.equal(1L,RecordingMixerProvider.count(sound),"real duel plays "+sound);
            Check.equal(10L,RecordingMixerProvider.count(PvpSoundBank.Sound.ROULETTE_CHARGE),"one charge per synchronized gauge segment");
            Check.equal(1L,RecordingMixerProvider.count(PvpSoundBank.Sound.ROULETTE_MAX),"one synchronized MAX notification");
            Files.write(shared.resolve(host?"host-spun":"guest-spun"),new byte[]{1});barrier(host?"guest-spun":"host-spun");
            if(host)edt(()->{((RoomClient)field(page,"client")).queueCommand(1);return null;});
        }
        long endDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(85);
        while(!edt(()->field(page,"battlePage")!=null&&((javax.swing.JPanel)field(field(page,"battlePage"),"onlineBattleEnd")).isVisible())){
            if(uiFailure!=null)throw new AssertionError("EDT failed",uiFailure);
            if(System.nanoTime()>endDeadline)throw new AssertionError("natural battle result did not start ending: "+mode+" / "+status());Thread.sleep(10);
        }
        edt(()->{
            BattleInfoPage nativePage=(BattleInfoPage)field(page,"battlePage");
            Check.that(!((java.awt.Canvas)field(nativePage,"bb")).isVisible(),"ending removes the native Canvas that could obscure result controls");
            Check.that(!((JPanel)field(nativePage,"onlineResult")).isVisible(),"no result before ending sound finishes");
            Check.equal(null,io.BCMusic.BG,"battle BGM stops at ending start");
            for(String name:new String[]{"back","jtb","rouletteDebugMax","stream","onlineSpecial","onlineTag"})
                Check.that(!((java.awt.Component)field(nativePage,name)).isVisible(),"ending hides "+name);
            Check.that(((JLabel)field(nativePage,"onlineBattleEndLabel")).getFont().getSize()>=40,"large readable battle-end text");
            screenshot(MainFrame.F.getRootPane(),mode+"-ending");return null;
        });
        await(()->((JPanel)field(field(page,"battlePage"),"onlineResult")).isVisible(),"both peers must show results, including missing STOP guest");
        edt(()->{
            BattleInfoPage nativePage=(BattleInfoPage)field(page,"battlePage");
            Check.equal(timeout?"引き分け":host?"勝利！":"敗北",((JLabel)field(nativePage,"onlineResultTitle")).getText(),"correct per-peer result");
            JButton ok=(JButton)field(nativePage,"onlineResultOk");Check.that(ok.isShowing()&&ok.isEnabled()&&ok.getHeight()>=40,"readable actionable OK on both clients");
            Check.equal(30,io.BCMusic.music.id,"result BGM is standard 030.ogg");Check.that(io.BCMusic.BG!=null,"result BGM has a playing clip");
            Check.equal(1L,RecordingMixerProvider.count(PvpSoundBank.Sound.BATTLE_END),"exactly one battle-end jingle");
            screenshot(MainFrame.F.getRootPane(),mode+"-result");return null;
        });
        Files.write(shared.resolve(host?"host-result":"guest-result"),new byte[]{1});barrier(host?"guest-result":"host-result");
        if(!host)barrier("host-acked");
        edt(()->{
            BattleInfoPage nativePage=(BattleInfoPage)field(page,"battlePage");
            Check.that(MainFrame.getPanel()==nativePage,"one peer's OK cannot skip the other's result");
            ((JButton)field(nativePage,"onlineResultOk")).doClick();
            if(host){
                Object bg=io.BCMusic.BG;
                nativePage.showOnlineResult(timeout?"引き分け":"勝利！","duplicate result",()->{});
                Check.that((Boolean)field(nativePage,"onlineResultAcked"),"duplicate result cannot reset acknowledged OK");
                Check.that(io.BCMusic.BG==bg,"duplicate result cannot restart result BGM");
            }return null;
        });
        if(host)Files.write(shared.resolve("host-acked"),new byte[]{1});
        await(()->field(page,"battlePage")==null&&MainFrame.getPanel()==field(page,"roomLobby"),"both OK buttons restore existing room");
        edt(()->{
            Check.equal(null,io.BCMusic.BG,"result BGM stops on lobby return");Check.equal(null,io.BCMusic.music,"stopped result BGM has no resume request");
            io.BCMusic.setSoundEnabled(false);io.BCMusic.setSoundEnabled(true);
            Check.equal(null,io.BCMusic.BG,"muting/unmuting in lobby cannot resurrect result music");return null;
        });
        Files.write(shared.resolve(host?"host-returned":"guest-returned"),new byte[]{1});barrier(host?"guest-returned":"host-returned");
        System.out.println("GUI_END_OK "+mode+" / full-size / jingle / 030.ogg / both OK / same lobby");
    }
    private static void barrier(String file)throws Exception{
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);
        while(!Files.exists(shared.resolve(file))){if(System.nanoTime()>until)throw new AssertionError("peer barrier: "+file);Thread.sleep(10);}
    }

    public static void main(String[] args) throws Exception {
        if(GraphicsEnvironment.isHeadless())throw new AssertionError("Run LobbyUiTests under xvfb-run (actual Swing required)");
        if(args.length>0){
            Thread.setDefaultUncaughtExceptionHandler((thread,error)->{uiFailure=error;error.printStackTrace();});
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
        for(String image:Arrays.asList("host","guest","battle-audio-host","battle-audio-guest"))Files.copy(root.resolve("room-"+image+".png"),logs.resolve("room-"+image+".png"),StandardCopyOption.REPLACE_EXISTING);
        for(String scenario:Arrays.asList("finish","timeout")){
            Path pair=Files.createDirectories(root.resolve(scenario));
            Process a=spawn(scenario+"-host",pair.resolve("host"),pair,logs),b=spawn(scenario+"-guest",pair.resolve("guest"),pair,logs);
            try{finishPair(a,scenario+"-host",b,scenario+"-guest",logs);}finally{a.destroyForcibly();b.destroyForcibly();}
            for(String role:Arrays.asList("host","guest"))for(String phase:Arrays.asList("ending","result")){
                String name="room-"+scenario+"-"+role+"-"+phase+".png";
                Files.copy(pair.resolve(name),logs.resolve(name),StandardCopyOption.REPLACE_EXISTING);
            }
        }
        System.out.println("Real Swing lobby regression tests passed; transcripts: "+logs);
    }
    private static Process spawn(String mode,Path user,Path shared,Path logs)throws Exception{
        Path cwd=Files.createDirectories(user.resolve("working-directory"));
        StringJoiner cp=new StringJoiner(java.io.File.pathSeparator);
        for(String item:System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)))cp.add(Paths.get(item).toAbsolutePath().toString());
        return new ProcessBuilder(Paths.get(System.getProperty("java.home"),"bin","java").toString(),"-ea","-Dfile.encoding=UTF-8","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","-cp",cp.toString(),LobbyUiTests.class.getName(),mode,user.toString(),shared.toString())
                .directory(cwd.toFile()).redirectErrorStream(true).redirectOutput(logs.resolve(mode+".log").toFile()).start();
    }
    private static void finishPair(Process a,String amode,Process b,String bmode,Path logs)throws Exception{
        Throwable failure=null;
        try{finish(a,amode,logs);}catch(Throwable t){failure=t;}
        try{finish(b,bmode,logs);}catch(Throwable t){if(failure==null)failure=t;else failure.addSuppressed(t);}
        if(failure!=null){
            if(failure instanceof Exception)throw (Exception)failure;
            if(failure instanceof Error)throw (Error)failure;
            throw new RuntimeException(failure);
        }
    }
    private static void finish(Process p,String mode,Path logs)throws Exception{
        if(!p.waitFor(mode.startsWith("timeout")?110:40,TimeUnit.SECONDS)){p.destroyForcibly();throw new AssertionError("GUI timeout: "+mode);}
        String transcript=new String(Files.readAllBytes(logs.resolve(mode+".log")),StandardCharsets.UTF_8);System.out.print(transcript);
        if(p.exitValue()!=0)throw new AssertionError("GUI test failed: "+mode);
    }
}
