package online.tests;

import common.CommonStatic;
import common.battle.*;
import common.system.fake.FakeImage;
import common.util.unit.Unit;
import online.sync.*;
import online.ui.*;
import page.battle.*;
import utilpc.awt.FG2D;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** Real native BBPainter/BBCtrl: no replacement battlefield, no simulated button widget. */
public final class NativeBattleUiTests {
    private static final class Keys implements CommonStatic.FakeKey {
        private final Set<String> pressed = new HashSet<>();
        void set(int row, int col) { pressed.add(row+":"+col); }
        public boolean pressed(int row, int col) { return pressed.contains(row+":"+col); }
        public void remove(int row, int col) { pressed.remove(row+":"+col); }
    }
    private static final class Box implements BattleBox, BattleBox.OuterBox {
        private final BBCtrl painter;
        Box(SBCtrl field) { painter = new BBCtrl(this, field, this); }
        public int getWidth() { return 1100; }
        public int getHeight() { return 680; }
        public BBCtrl getPainter() { return painter; }
        public void paint() { }
        public void reset() { }
        public void releaseData() { }
        public int getSpeed() { return 0; }
        public void callBack(Object value) { }
    }
    private static final class Trace extends FG2D {
        final IdentityHashMap<FakeImage,List<Rectangle>> images = new IdentityHashMap<>();
        int axes, flips;
        Trace(Graphics2D graphics) { super(graphics); }
        @Override public void drawImage(FakeImage image, float x, float y, float w, float h) {
            images.computeIfAbsent(image, key -> new ArrayList<>()).add(new Rectangle((int)x, (int)y, (int)w, (int)h));
            super.drawImage(image,x,y,w,h);
        }
        @Override public void drawLine(float x,float y,float w,float h) { axes++; super.drawLine(x,y,w,h); }
        @Override public void drawRect(float x,float y,float w,float h) { axes++; super.drawRect(x,y,w,h); }
        @Override public void scale(float x,float y) { if(x<0)flips++;super.scale(x,y); }
    }
    public static void run() throws Exception {
        boolean nativePage = false;
        for (Field field : OnlineLobbyPage.class.getDeclaredFields()) {
            Check.that(!field.getType().getName().equals("online.ui.PvpCanvas"),"Remove the custom PvpCanvas battlefield");
            Check.that(!field.getName().equals("units") && !field.getName().equals("controls"),"Native icon HUD replaces text-only Swing buttons");
            nativePage |= field.getType() == BattleInfoPage.class;
        }
        Check.that(nativePage,"Online lobby must open the existing BattleInfoPage");
        FixtureNativeUi.init();
        Unit left = FixtureNativeUi.unit("native_left",0xff4499ee), right = FixtureNativeUi.unit("native_right",0xffee7799);
        BasisLU leftLu = Fixture.lineup(left), rightLu = Fixture.lineup(right);
        for(int i=1;i<6;i++){leftLu.lu.fs[i/5][i%5]=left.forms[0];rightLu.lu.fs[i/5][i%5]=right.forms[0];}leftLu.lu.renew();rightLu.lu.renew();
        PvpStageBasis live = new PvpStageBasis(leftLu,rightLu,781,0);
        live.left().money=100000;live.right().money=200000;live.step(new InputFrame(0,1,1));
        boolean ref=CommonStatic.getConfig().ref, rows=CommonStatic.getConfig().twoRow, fps=CommonStatic.getConfig().performanceModeBattle;
        try {
            CommonStatic.getConfig().ref=true;
            for (int dir : new int[]{1,-1}) for(boolean twoRows : new boolean[]{false,true}) {
                CommonStatic.getConfig().twoRow=twoRows;
                Keys keys=new Keys();List<Integer> sent=new ArrayList<>();
                OnlineBattleField field=new OnlineBattleField(keys,live.displayCopy(),dir,sent::add);
                Box box=new Box(field);
                String before=BattleDigest.of(live);
                BufferedImage image=new BufferedImage(box.getWidth(),box.getHeight(),BufferedImage.TYPE_INT_ARGB);
                Graphics2D g=image.createGraphics();Trace trace=new Trace(g);
                try { box.painter.draw(trace); } finally { g.dispose(); }
                Check.equal(0,trace.axes,"normal PvP rendering must hide editor axes even when global ref=true");
                Check.that(CommonStatic.getConfig().ref,"rendering must restore the user's editor debug setting");
                Check.that(trace.flips>=3,"left castle, units and cannons are mirrored with native coordinates");
                FakeImage own=(dir==1?left:right).forms[0].anim.getUni().getImg();
                FakeImage other=(dir==1?right:left).forms[0].anim.getUni().getImg();
                Check.that(trace.images.containsKey(own)&&!trace.images.containsKey(other),"HUD renders only the local lineup, including left-side players");
                Check.equal(live.playerFor(dir).money,field.playerState().money,"HUD owns local money");
                Rectangle target=trace.images.get(own).get(twoRows?0:1);
                box.click(new Point(target.x+target.width/2,target.y+target.height/2),java.awt.event.MouseEvent.BUTTON1);
                field.update();Check.that(sent.contains(1),"native icon click queues local slot zero");sent.clear();
                box.click(new Point(target.x+target.width/2,target.y+target.height/2),java.awt.event.MouseEvent.BUTTON3);
                field.update();Check.that(sent.contains(1<<12)&&!sent.contains(1),"native right click queues auto-production, not a spawn");sent.clear();
                box.click(new Point(8,box.getHeight()-10),java.awt.event.MouseEvent.BUTTON1);field.update();
                Check.that(sent.contains(InputFrame.WORKER),"native worker hit target queues network command");sent.clear();
                box.click(new Point(box.getWidth()-8,box.getHeight()-10),java.awt.event.MouseEvent.BUTTON1);field.update();
                Check.that(sent.contains(InputFrame.CANNON),"native cannon hit target queues network command");sent.clear();
                keys.set(0,0);field.update();Check.that(sent.contains(1),"original KeyHandler slot mapping feeds commands");keys.remove(0,0);sent.clear();
                keys.set(-2,0);keys.set(0,0);field.update();Check.that(sent.contains(1<<12),"original Shift modifier retained");keys.remove(-2,0);sent.clear();
                float scale=field.sb.siz;box.wheeled(new Point(500,300),-1);float zoomed=field.sb.siz;
                Check.that(zoomed>scale,"native mouse-wheel camera zoom is active");
                field.publish(live.displayCopy());Check.equal(zoomed,field.sb.siz,"new snapshots retain local camera zoom");
                int oldPan=field.sb.pos;
                box.press(new Point(500,300));box.drag(new Point(450,300),java.awt.event.MouseEvent.BUTTON1);box.release();
                Check.equal(oldPan-50,field.sb.pos,"native camera pan after a new snapshot must update the current display state");
                if(!twoRows) {
                    field.action.add(-4);field.update();
                    Check.that(sent.isEmpty(),"lineup switching is presentation-only and not a simulation command");
                    for(int i=0;i<common.util.Data.LINEUP_CHANGE_TIME;i++){
                        PvpStageBasis copy=live.displayCopy();copy.time=live.time+i+1;field.publish(copy);
                    }
                    Check.equal(1,field.playerState().frontLineup,"native single-row lineup switching preserved");
                    Check.equal(0,live.playerFor(dir).frontLineup,"row selection never mutates the canonical battle");
                }
                CommonStatic.getConfig().performanceModeBattle=true;
                int displayTime=field.sb.time;Thread.sleep(18);field.renderStep();
                Check.equal(displayTime,field.sb.time,"60 FPS half-step must not add a simulation tick");
                CommonStatic.getConfig().performanceModeBattle=fps;
                field.interactive(false);field.action.add(0);field.update();Check.that(sent.isEmpty(),"ended match ignores controls");
                Check.equal(before,BattleDigest.of(live),"rendering, zoom and native input cannot advance canonical simulation");
                Path path=Paths.get("target/native-ui-"+dir+"-"+twoRows+".png");Files.createDirectories(path.getParent());ImageIO.write(image,"png",path.toFile());
            }
            roulettePresentationTests(leftLu,rightLu);
            SBCtrl offline=new SBCtrl(new Keys(),live.st,0,Fixture.lineup(right),new int[3],983);
            for(int i=0;i<40;i++){offline.sb.money=100000;offline.action.add(0);offline.update();}
            Check.that(!offline.sb.le.isEmpty(),"offline fixture must spawn a real unit before debug comparison");
            Box original=new Box(offline);
            BufferedImage image=new BufferedImage(1100,680,BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics=image.createGraphics();Trace trace=new Trace(graphics);
            try { original.painter.draw(trace); } finally { graphics.dispose(); }
            Check.that(trace.axes>0,"single-player renderer still honors the original debug setting");
            Check.that(CommonStatic.getConfig().ref,"single-player debug preference is preserved");
        } finally {CommonStatic.getConfig().ref=ref;CommonStatic.getConfig().twoRow=rows;CommonStatic.getConfig().performanceModeBattle=fps;}
    }
    private static void roulettePresentationTests(BasisLU leftLu,BasisLU rightLu) throws Exception {
        online.net.lobby.RoomRules rules=new online.net.lobby.RoomRules(4400,0,-1,false,online.net.lobby.RoomRules.SpecialMode.ROULETTE);

        PvpStageBasis charging=new PvpStageBasis(leftLu,rightLu,780,0,rules);
        for(int i=0;i<PvpStageBasis.TPS;i++)charging.step(new InputFrame(charging.time,0,0));
        Check.equal(10,charging.left().pvpRoulette.gauge,
                "real 30TPS battle steps visibly charge roulette by the reverse-engineered 1% full-HP step");

        PvpStageBasis meter=new PvpStageBasis(leftLu,rightLu,781,0,rules);
        OnlineBattleField meterField=new OnlineBattleField(new Keys(),meter.displayCopy(),1,value->{});
        Box meterBox=new Box(meterField);
        BufferedImage emptyMeter=new BufferedImage(meterBox.getWidth(),meterBox.getHeight(),BufferedImage.TYPE_INT_ARGB);
        Graphics2D eg=emptyMeter.createGraphics();Trace emptyTrace=new Trace(eg);
        try{meterBox.painter.draw(emptyTrace);}finally{eg.dispose();}
        Check.that(!emptyTrace.images.containsKey(CommonStatic.getBCAssets().battle[1][0].getImg()),
                "roulette mode removes the native cannon icon from the bottom-right control");
        Check.that(emptyTrace.images.containsKey(Pvp3dsAssets.fakeImage("ui_battle_multi","ルーレットアイコン蓋（上部）")),
                "roulette mode draws original 3DS roulette framing in the cannon control position");

        meter.left().pvpRoulette.gauge=meter.left().pvpRoulette.targetGauge=PvpRouletteState.MAX_GAUGE/2;
        meterField.publish(meter.displayCopy());
        BufferedImage halfMeter=new BufferedImage(meterBox.getWidth(),meterBox.getHeight(),BufferedImage.TYPE_INT_ARGB);
        Graphics2D hg=halfMeter.createGraphics();
        try{meterBox.painter.draw(new Trace(hg));}finally{hg.dispose();}
        Check.that(pixelDifference(emptyMeter,halfMeter,700,430,1100,680)>100,
                "roulette bottom-right gauge visibly changes between 0% and 50%");

        PvpStageBasis live=new PvpStageBasis(leftLu,rightLu,782,0,rules);
        StageBasis own=live.left();
        own.pvpRoulette.gauge=own.pvpRoulette.targetGauge=PvpRouletteState.MAX_GAUGE;
        live.step(new InputFrame(live.time,InputFrame.SPECIAL,0));
        Check.that(own.pvpRoulette.spinning,"roulette presentation fixture starts from explicit SPECIAL input");

        OnlineBattleField field=new OnlineBattleField(new Keys(),live.displayCopy(),1,value->{});
        Box box=new Box(field);
        int current=field.playerState().pvpRoulette.currentResult();

        BufferedImage spinning=new BufferedImage(box.getWidth(),box.getHeight(),BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg=spinning.createGraphics();Trace spinTrace=new Trace(sg);
        try{box.painter.draw(spinTrace);}finally{sg.dispose();}
        Check.that(spinTrace.images.containsKey(Pvp3dsAssets.fakeImage("ui_battle_multi_reel",rouletteIcon(current))),
                "on-field roulette animation renders the current 3DS icon");
        Check.that(spinTrace.images.containsKey(Pvp3dsAssets.fakeImage("ui_battle_multi_reel",rouletteName(current))),
                "on-field roulette animation renders the current 3DS effect name");

        for(int i=0;i<PvpRouletteState.AUTO_SPIN_TICKS;i++)live.step(new InputFrame(live.time,0,0));
        Check.that(!live.left().pvpRoulette.spinning,"roulette fixture auto-resolves after two seconds");
        int result=live.left().pvpRoulette.lastResult;
        field.publish(live.displayCopy());

        BufferedImage resultImage=new BufferedImage(box.getWidth(),box.getHeight(),BufferedImage.TYPE_INT_ARGB);
        Graphics2D rg=resultImage.createGraphics();Trace resultTrace=new Trace(rg);
        try{box.painter.draw(resultTrace);}finally{rg.dispose();}
        Check.that(resultTrace.images.containsKey(Pvp3dsAssets.fakeImage("ui_battle_multi_cutin",rouletteCutin(result))),
                "resolved effect shows the original 3DS cut-in on the battlefield");
        Check.that(resultTrace.images.containsKey(Pvp3dsAssets.fakeImage("ui_battle_multi_reel",rouletteEffect(result))),
                "resolved effect shows the original 3DS activation icon on the battlefield");

        Path spinPath=Paths.get("target/native-ui-roulette-spin.png");
        Path resultPath=Paths.get("target/native-ui-roulette-result.png");
        Files.createDirectories(spinPath.getParent());
        ImageIO.write(spinning,"png",spinPath.toFile());
        ImageIO.write(resultImage,"png",resultPath.toFile());
    }

    private static int pixelDifference(BufferedImage a,BufferedImage b,int x0,int y0,int x1,int y1){
        int changed=0;
        int minX=Math.max(0,x0),minY=Math.max(0,y0),maxX=Math.min(a.getWidth(),x1),maxY=Math.min(a.getHeight(),y1);
        for(int y=minY;y<maxY;y++)for(int x=minX;x<maxX;x++)if(a.getRGB(x,y)!=b.getRGB(x,y))changed++;
        return changed;
    }

    private static String rouletteIcon(int result){return new String[]{
            "アイコン：ふっとばし","アイコン：癒やし","アイコン：生産回復","アイコン：にゃんこ砲",
            "アイコン：生産短縮","アイコン：働き増加","アイコン：コストダウン","アイコン：お金マックス",
            "アイコン：スロウ","アイコン：ストップ","アイコン：攻撃力アップ","アイコン：体力アップ",
            "アイコン：移動アップ","アイコン：プチベビーラッシュ"}[result];}
    private static String rouletteName(int result){return new String[]{
            "効果名：ふっとばし","効果名：癒やし","効果名：生産回復","効果名：にゃんこ砲",
            "効果名：生産短縮","効果名：働き増加","効果名：コストダウン","効果名：お金マックス",
            "効果名：スロウ","効果名：ストップ","効果名：攻撃力アップ","効果名：体力アップ",
            "効果名：移動アップ","効果名：プチベビーラッシュ"}[result];}
    private static String rouletteEffect(int result){return new String[]{
            "発動エフェクト：ふっとばし","発動エフェクト：癒やし","発動エフェクト：生産回復","発動エフェクト：にゃんこ砲",
            "発動エフェクト：生産短縮","発動エフェクト：働き増加","発動エフェクト：コストダウン","発動エフェクト：お金マックス",
            "発動エフェクト：スロウ","発動エフェクト：ストップ","発動エフェクト：攻撃力アップ","発動エフェクト：体力アップ",
            "発動エフェクト：移動アップ","発動エフェクト：プチベビーラッシュ"}[result];}
    private static String rouletteCutin(int result){return new String[]{
            "ふっとばし発動!","にゃんこ回復ボーナス!","生産回復ボーナス!","にゃんこ砲発射!",
            "生産短縮ボーナス!","働きネコ仕事効率UPボーナス!","コストダウンボーナス!","お金MAXボーナス!!",
            "スロウ発動!","ストップ発動!","攻撃力UPボーナス!","体力UPボーナス!",
            "移動スピードUPボーナス!","ぷちベビーラッシュ発動!"}[result];}

    public static void main(String[] args) throws Exception { run();System.out.println("Native BBPainter/BBCtrl regression passed"); }
}
