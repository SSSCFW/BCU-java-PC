package online.ui;

import common.battle.*;
import io.BCMusic;
import main.MainBCU;
import online.net.lobby.RoomRules;
import online.tests.*;
import page.MainFrame;
import page.awt.AWTBBB;
import page.awt.BBBuilder;
import page.battle.BattleInfoPage;
import page.battle.BattleBox;
import page.battle.BBCtrl;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.concurrent.*;
import javax.swing.*;

/** Real AWT Canvas/Swing presentation regressions. Run with xvfb-run. */
public final class BattlePresentationTests {
    private static BattleInfoPage page;
    private static <T> T edt(Callable<T> work)throws Exception{
        FutureTask<T> task=new FutureTask<>(work);SwingUtilities.invokeAndWait(task);return task.get();
    }
    private static Object field(Object object,String name)throws Exception{
        Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
    }
    private static void init(boolean large)throws Exception{
        FixtureNativeUi.init();BBBuilder.def=AWTBBB.INS;BCMusic.play=false;MainBCU.loaded=false;
        edt(()->{
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
            MainFrame.F=new MainFrame("battle presentation regression");MainFrame.F.setSize(1200,900);
            BattleInfoPage.DEF_LARGE=large;
            PvpStageBasis world=new PvpStageBasis(Fixture.lineup(FixtureNativeUi.unit("presentation_l",0xff557799)),
                    Fixture.lineup(FixtureNativeUi.unit("presentation_r",0xff995577)),19,0,
                    new RoomRules(4400,0,3,false,RoomRules.SpecialMode.ROULETTE,true));
            page=new BattleInfoPage(null,world.displayCopy(),1,i->{},()->{},"ホスト","参加者");
            MainFrame.changePanel(page);
            page.componentResized(MainFrame.F.getRootPane().getWidth(),MainFrame.F.getRootPane().getHeight());
            MainFrame.F.validate();page.renderOnlineFrame();
            for(String name:new String[]{"onlineBattleEndLabel","onlineResultTitle","onlineResultDetail"}){
                Component label=(Component)field(page,name);
                Check.that(label.getFont().canDisplayUpTo("戦闘終了 勝利！ 敗北 引き分け 両者の結果が一致しました。")<0,
                        "Japanese result glyphs must render, not missing-font boxes: "+name);
            }
            return null;
        });
    }
    private static Point slotPoint(BBCtrl painter,Canvas canvas,int slot){
        for(int y=0;y<canvas.getHeight();y+=4)for(int x=0;x<canvas.getWidth();x+=4){
            Point p=new Point(x,y);if(painter.slotAt(p)==slot)return p;
        }
        throw new AssertionError("No visible point for lineup slot "+slot);
    }
    private static void pageMouse(String method,MouseEvent event)throws Exception{
        Method m=BattleInfoPage.class.getDeclaredMethod(method,MouseEvent.class);m.setAccessible(true);m.invoke(page,event);
    }
    private static void screenshot(String name)throws Exception{
        Path dir=Paths.get("target/presentation-regression");Files.createDirectories(dir);
        Rectangle bounds=edt(()->{Point p=MainFrame.F.getLocationOnScreen();return new Rectangle(p,MainFrame.F.getSize());});
        javax.imageio.ImageIO.write(new Robot().createScreenCapture(bounds),"png",dir.resolve(name+".png").toFile());
    }
    public static void main(String[] args)throws Exception{
        int exit=0;
        try{
            if(args[0].equals("muted")){
                BCMusic.play=false;CountDownLatch done=new CountDownLatch(1);
                PvpSoundBank.play(PvpSoundBank.Sound.BATTLE_END,done::countDown);
                Check.that(!done.await(200,TimeUnit.MILLISECONDS),"muting audio must not skip the battle-end presentation duration");
                Check.that(done.await(3,TimeUnit.SECONDS),"muted battle-end still completes");
            }else{
                init(!args[0].startsWith("small"));screenshot("initial-"+args[0]);
                if(args[0].equals("publication")){
                    RecordingMixerProvider.enable();BCMusic.play=true;
                    edt(()->{
                        OnlineBattleField view=(OnlineBattleField)field(page,"online");
                        PvpStageBasis copy=((PvpStageBasis)view.sb).displayCopy();
                        copy.left().pvpRoulette.gauge=100;
                        page.publishOnline(copy);return null;
                    });
                    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
                    while(RecordingMixerProvider.started.isEmpty()&&System.nanoTime()<deadline)Thread.sleep(10);
                    Check.equal(1,RecordingMixerProvider.started.size(),"a completed gauge segment must emit sound without waiting for a render frame");
                }
                if(args[0].equals("layers"))edt(()->{
                    Canvas canvas=(Canvas)field(page,"bb");
                    for(String name:new String[]{"rouletteDebugMax","rouletteNotice","onlineTag","stream"}){
                        Component c=(Component)field(page,name);
                        System.out.println(name+" z="+page.getComponentZOrder(c)+" canvas="+page.getComponentZOrder(canvas)+" bounds="+c.getBounds());
                        Check.that(page.getComponentZOrder(c)<page.getComponentZOrder(canvas),"initial-large "+name+" must be above the Canvas without toggling size");
                    }
                    Component details=(Component)field(page,"unitAbilityOverlay");
                    Check.that(details.getWidth()>=canvas.getWidth()*0.70,"large-mode unit details are wide enough for long ability rows");
                    Check.that(details.getHeight()>=canvas.getHeight()*0.30,"large-mode unit details are tall enough to avoid premature clipping");
                    return null;
                });
                if(args[0].equals("slot-drag"))edt(()->{
                    BattleBox box=(BattleBox)field(page,"bb");Canvas canvas=(Canvas)box;
                    BBCtrl painter=(BBCtrl)box.getPainter();OnlineBattleField view=(OnlineBattleField)field(page,"online");
                    Check.that(view.visibleForm(0)!=null&&view.visibleForm(1)==null,"slot-drag fixture begins with one unit in slot zero");
                    Point from=slotPoint(painter,canvas,0),to=slotPoint(painter,canvas,1);long now=System.currentTimeMillis();
                    pageMouse("mousePressed",new MouseEvent(canvas,MouseEvent.MOUSE_PRESSED,now,MouseEvent.BUTTON2_DOWN_MASK,from.x,from.y,1,false,MouseEvent.BUTTON2));
                    pageMouse("mouseDragged",new MouseEvent(canvas,MouseEvent.MOUSE_DRAGGED,now+1,MouseEvent.BUTTON2_DOWN_MASK,to.x,to.y,0,false,MouseEvent.NOBUTTON));
                    pageMouse("mouseReleased",new MouseEvent(canvas,MouseEvent.MOUSE_RELEASED,now+2,0,to.x,to.y,1,false,MouseEvent.BUTTON2));
                    Check.equal(1,view.canonicalSlotForVisible(0),"middle-button drag swaps visible slot mapping");
                    Check.equal(0,view.canonicalSlotForVisible(1),"middle-button drag preserves inverse mapping");
                    Check.that(view.visibleForm(0)==null&&view.visibleForm(1)!=null,"middle-button drag visibly moves the unit to the destination slot");
                    return null;
                });
                if(args[0].equals("hold-wheel"))edt(()->{
                    BattleBox box=(BattleBox)field(page,"bb");Canvas canvas=(Canvas)box;
                    BBCtrl painter=(BBCtrl)box.getPainter();OnlineBattleField view=(OnlineBattleField)field(page,"online");
                    Point slot=slotPoint(painter,canvas,0);long now=System.currentTimeMillis();
                    pageMouse("mousePressed",new MouseEvent(canvas,MouseEvent.MOUSE_PRESSED,now,MouseEvent.BUTTON1_DOWN_MASK,slot.x,slot.y,1,false,MouseEvent.BUTTON1));
                    PvpUnitAbilityOverlay overlay=(PvpUnitAbilityOverlay)field(page,"unitAbilityOverlay");
                    overlay.show(view.visibleForm(0),view.playerState());
                    overlay.setBounds(100,100,700,130);overlay.doLayout();overlay.scrollPane().doLayout();overlay.scrollPane().getViewport().doLayout();
                    JScrollBar bar=overlay.scrollPane().getVerticalScrollBar();
                    Check.that(bar.getMaximum()>bar.getVisibleAmount(),"hold-wheel fixture has vertical overflow");
                    int before=bar.getValue();
                    pageMouse("mouseWheel",new MouseWheelEvent(canvas,MouseEvent.MOUSE_WHEEL,now+1,MouseEvent.BUTTON1_DOWN_MASK,
                            slot.x,slot.y,0,false,MouseWheelEvent.WHEEL_UNIT_SCROLL,3,3));
                    Check.that(bar.getValue()>before,"Canvas-origin mouse wheel scrolls unit details while left button remains held");
                    Check.equal(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,overlay.scrollPane().getHorizontalScrollBarPolicy(),
                            "held unit details never expose horizontal scrolling");
                    pageMouse("mouseReleased",new MouseEvent(canvas,MouseEvent.MOUSE_RELEASED,now+2,0,slot.x,slot.y,1,false,MouseEvent.BUTTON1));
                    return null;
                });
                if(args[0].endsWith("result")){
                    if(args[0].startsWith("late")){
                        edt(()->{page.beginOnlineBattleEnd();return null;});Thread.sleep(1500);
                        edt(()->{
                            JPanel ending=(JPanel)field(page,"onlineBattleEnd");
                            JPanel backdrop=(JPanel)field(page,"onlineBackdrop");
                            Check.that(ending.isVisible()&&!((JPanel)field(page,"onlineResult")).isVisible(),"finished sound waits for the verified server result");
                            Check.that(backdrop.isVisible(),"frozen battlefield remains visible behind the ending card");
                            Check.that(ending.getWidth()<backdrop.getWidth()/2&&ending.getHeight()<backdrop.getHeight()/3,
                                    "battle-end card stays compact instead of covering the battlefield");
                            return null;
                        });
                    }
                    edt(()->{page.showOnlineResult("勝利！","両者の結果が一致しました。OKを押してください。",()->{});return null;});
                    if(args[0].startsWith("resize"))edt(()->{
                        MainFrame.F.setSize(1000,760);
                        page.componentResized(MainFrame.F.getRootPane().getWidth(),MainFrame.F.getRootPane().getHeight());return null;
                    });
                    Thread.sleep(1500);screenshot(args[0]);
                    Rectangle detailBounds=edt(()->{Component c=(Component)field(page,"onlineResultDetail");return new Rectangle(c.getLocationOnScreen(),c.getSize());});
                    java.awt.image.BufferedImage detailImage=new Robot().createScreenCapture(detailBounds);
                    int dark=0,light=0;
                    for(int y=0;y<detailImage.getHeight();y++)for(int x=0;x<detailImage.getWidth();x++){
                        Color pixel=new Color(detailImage.getRGB(x,y));
                        if(pixel.getRed()<60&&pixel.getGreen()<60&&pixel.getBlue()<60)dark++;
                        if(pixel.getRed()>180&&pixel.getGreen()>180&&pixel.getBlue()>180)light++;
                    }
                    Check.that(dark>detailImage.getWidth()*detailImage.getHeight()/2&&light>10,"result detail must render light text on dark background, not Nimbus white-on-white");
                    edt(()->{
                        JPanel result=(JPanel)field(page,"onlineResult");JButton ok=(JButton)field(page,"onlineResultOk");
                        JLabel label=(JLabel)field(page,"onlineResultTitle");
                        System.out.println("result="+result.getBounds()+" valid="+result.isValid()+" title="+label.getBounds()+" font="+label.getFont()+" bg="+result.getBackground()+" fg="+label.getForeground()+" OK="+ok.getBounds());
                        JPanel backdrop=(JPanel)field(page,"onlineBackdrop");
                        Check.that(result.isShowing()&&ok.isShowing()&&ok.getWidth()>0&&ok.getHeight()>0,"result OK must be laid out and visible on first battle ending");
                        Check.that(backdrop.isShowing(),"battlefield snapshot remains visible behind the result card");
                        Check.that(result.getWidth()<backdrop.getWidth()*3/5&&result.getHeight()<backdrop.getHeight()/2,
                                "result card stays compact and leaves most of the battlefield visible");
                        Check.that(label.getFont().getSize()>=24,"result heading must remain legible after page-wide resizing");return null;
                    });
                }
            }
            System.out.println("PRESENTATION_OK "+args[0]);
        }catch(Throwable t){exit=1;t.printStackTrace();}
        finally{PvpSoundBank.stopAll();BCMusic.stopAll();if(MainFrame.F!=null)edt(()->{if(page!=null)page.detachOnline();MainFrame.F.dispose();return null;});}
        System.exit(exit);
    }
}
