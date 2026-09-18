package page.battle;

import common.CommonStatic;
import common.battle.*;
import common.battle.entity.AbEntity;
import common.battle.entity.Entity;
import common.util.Data;
import common.util.stage.Replay;
import common.util.stage.Stage;
import common.util.unit.Form;
import io.BCMusic;
import main.MainBCU;
import main.Opts;
import online.ui.OnlineBattleField;
import online.ui.AudioSettingsPanel;
import online.ui.PvpRouletteHud;
import online.net.lobby.PvpTraitRules;
import utilpc.UtilPC;
import java.util.function.IntConsumer;
import page.*;
import page.awt.BBBuilder;
import page.battle.BattleBox.OuterBox;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BattleInfoPage extends KeyHandler implements OuterBox {

	private static final long serialVersionUID = 1L;

	public static boolean DEF_LARGE = false;

	public static BattleInfoPage current = null;

	public static void redefine() {
		ComingTable.redefine();
		TotalDamageTable.redefine();
	}

	private final JBTN back = new JBTN(MainLocale.PAGE, "back");
	private final JBTN paus = new JBTN(MainLocale.PAGE, "pause");
	private final JBTN next = new JBTN(MainLocale.PAGE, "nextf");
	private final JBTN rply = new JBTN();
	private final JBTN row = new JBTN();
	private final EntityTable ut = new EntityTable(-1, false);
	private final EntityTable ust = new EntityTable(-1, true);
	private final ComingTable ct = new ComingTable(this);
	private final EntityTable et = new EntityTable(1, false);
	private final EntityTable est = new EntityTable(1, true);
	private final TotalDamageTable utd;
	private final JScrollPane eup = new JScrollPane(ut);
	private final JScrollPane eusp = new JScrollPane(ust);
	private final JScrollPane eep = new JScrollPane(et);
	private final JScrollPane eesp = new JScrollPane(est);
	private final JScrollPane ctp = new JScrollPane(ct);
	private final JScrollPane utdsp;
	private final JTG ustat = new JTG(MainLocale.INFO, "stat");
	private final JTG estat = new JTG(MainLocale.INFO, "stat");
	private final JLabel ebase = new JLabel();
	private final JLabel ubase = new JLabel();
    private final JLabel eTraitIcon=new JLabel("",SwingConstants.CENTER),uTraitIcon=new JLabel("",SwingConstants.CENTER);
	private final JLabel timer = new JLabel();
	private final JLabel ecount = new JLabel();
	private final JLabel ucount = new JLabel();
	private final JLabel stream = new JLabel();
	private final JLabel respawn = new JLabel();
	private final JTG jtb = new JTG(MainLocale.PAGE, "larges");
	private final JSlider jsl = new JSlider();
	private final BattleBox bb;
	private final BattleField basis;

	private OnlineBattleField online;
    private final JButton audio=new JButton("音量"),rouletteDebugMax=new JButton("ルーレットMAX");
    private final JLabel onlineTag=new JLabel("Online"),babyRushStatus=new JLabel(),rouletteNotice=new JLabel();
    private PvpRouletteHud onlineSpecial;
    private JDialog audioDialog;
    private final JPanel onlineResult=new JPanel(new BorderLayout(10,10));
    private final JLabel onlineResultTitle=new JLabel("",SwingConstants.CENTER),onlineResultDetail=new JLabel("",SwingConstants.CENTER);
    private final JButton onlineResultOk=new JButton("OK");
    private Runnable onlineResultAck;
    private boolean onlineResultAcked,opponentRouletteSpinning;
    private int rouletteNoticeUntil=-1;
	private Runnable onlineExit;
	private boolean onlineClosed;
	private String onlineLeftName, onlineRightName;

	private boolean pause = false, changedBG = false;
	private Replay recd;
	private boolean backClicked = false;

	private byte spe = 0;
	private int upd = 0;
	private boolean musicChanged = false, exPopupShown = false;

	/**
	 * Creates a new Battle Page
	 * @param p The previous page
	 * @param rec Will be null if this battle is not began from a replay
	 * @param conf If the value is 0, lineup will be randomized, if it's 1, lineup won't be changed
	 */
	public BattleInfoPage(Page p, Replay rec, int conf) {
		super(p);
		recd = rec;
		basis = new SBRply(rec);
		if ((conf & 1) == 0)
			bb = BBBuilder.def.getDef(this, basis);
		else
			bb = BBBuilder.def.getRply(this, basis, rec.rl.id, (conf & 4) != 0);
		jtb.setSelected((conf & 2) != 0);
		jtb.setEnabled((conf & 1) == 0);
		ct.setData(basis.sb.st);
		utd = new TotalDamageTable(basis.sb);
		utdsp = new JScrollPane(utd);

		if (recd.rl != null)
			jsl.setMaximum(((SBRply) basis).size());
		ini();
		rply.setText(0, recd.rl == null ? "save" : "start");
	}

	protected BattleInfoPage(Page p, SBRply rpl) {
		super(p);
		SBCtrl ctrl = rpl.transform(this);
		bb = BBBuilder.def.getCtrl(this, ctrl);
		pause = true;
		basis = ctrl;
		ct.setData(basis.sb.st);
		utd = new TotalDamageTable(rpl.sb);
		utdsp = new JScrollPane(utd);

		ini();
		rply.setText(0, "rply");
		current = this;
	}

	protected BattleInfoPage(Page p, Stage st, int star, BasisLU bl, int[] ints) {
		super(p);
		long seed = new Random().nextLong();

		BasisLU lu = bl.copy();
		lu.performRealisticLeveling(st.lim != null ? st.lim.stageLimit : null);

		SBCtrl sb = new SBCtrl(this, st, star, lu, ints, seed);
		bb = BBBuilder.def.getCtrl(this, sb);
		basis = sb;
		ct.setData(basis.sb.st);
		jtb.setSelected(DEF_LARGE);
		utd = new TotalDamageTable(sb.sb);
		utdsp = new JScrollPane(utd);

		ini();
		rply.setText(0, "rply");
		current = this;
	}

	/** Native battle page with a network-fed, display-only controller. */
	public BattleInfoPage(Page parent, PvpStageBasis displayCopy, int direction,
	                      IntConsumer commands, Runnable onExit, String leftName, String rightName) {
		super(parent);
		online = new OnlineBattleField(this, displayCopy, direction, commands);
		onlineExit = onExit;
		onlineLeftName = leftName;
		onlineRightName = rightName;
		basis = online;
		bb = BBBuilder.def.getCtrl(this, online);
		ct.setData(basis.sb.st);
		et.useUnitIcons();
		est.useUnitIcons();
		utd = new TotalDamageTable(online.playerState());
		utdsp = new JScrollPane(utd);
		jtb.setSelected(DEF_LARGE);
		ini();
		// These native single-player operations cannot be performed independently online.
		paus.setEnabled(false);paus.setVisible(false);
        onlineSpecial=new PvpRouletteHud(online);add(audio);add(onlineSpecial);add(onlineTag);add(babyRushStatus);add(rouletteNotice);add(rouletteDebugMax);
        configureTraitIcon(eTraitIcon,displayCopy.leftTrait());configureTraitIcon(uTraitIcon,displayCopy.rightTrait());add(eTraitIcon);add(uTraitIcon);
        onlineTag.setForeground(Color.WHITE);onlineTag.setFont(onlineTag.getFont().deriveFont(Font.BOLD,18f));
        babyRushStatus.setForeground(new Color(255,225,80));babyRushStatus.setFont(babyRushStatus.getFont().deriveFont(Font.BOLD,16f));babyRushStatus.setVisible(false);
        rouletteNotice.setForeground(new Color(255,245,180));rouletteNotice.setFont(rouletteNotice.getFont().deriveFont(Font.BOLD,16f));rouletteNotice.setVisible(false);
        rouletteDebugMax.setVisible(online.debugMode()&&online.rouletteMode());
        rouletteDebugMax.addActionListener(e->{getPress().clear();online.debugRouletteMax();});
        audio.addActionListener(e->{getPress().clear();if(audioDialog!=null&&audioDialog.isDisplayable()){audioDialog.toFront();return;}audioDialog=AudioSettingsPanel.open(this);});
        onlineResult.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.WHITE,2),BorderFactory.createEmptyBorder(18,24,18,24)));
        onlineResult.setBackground(new Color(20,20,20));onlineResultTitle.setForeground(Color.WHITE);onlineResultDetail.setForeground(Color.WHITE);
        onlineResultTitle.setFont(onlineResultTitle.getFont().deriveFont(Font.BOLD,30f));
        JPanel resultCenter=new JPanel(new GridLayout(2,1,4,4));resultCenter.setOpaque(false);resultCenter.add(onlineResultTitle);resultCenter.add(onlineResultDetail);
        onlineResult.add(resultCenter,BorderLayout.CENTER);onlineResult.add(onlineResultOk,BorderLayout.SOUTH);onlineResult.setVisible(false);add(onlineResult);setComponentZOrder(onlineResult,0);
        onlineResultOk.addActionListener(e->{if(onlineResultAcked||onlineResultAck==null)return;onlineResultAcked=true;onlineResultOk.setEnabled(false);onlineResultDetail.setText("相手のOKを待っています…");onlineResultAck.run();});
        if(MainBCU.loaded){BCMusic.stopAll();BCMusic.play(basis.sb.st.mus0);}
		next.setEnabled(false);
		rply.setEnabled(false);
		rply.setVisible(false);
		jsl.setEnabled(false);
		paus.setToolTipText("オンライン対戦では単独で一時停止できません");
		next.setToolTipText("オンライン対戦ではコマ送りできません");
		add(stream);
		current = this;
	}

    private void configureTraitIcon(JLabel label,int trait){
        label.setOpaque(false);label.setForeground(Color.WHITE);
        label.setToolTipText("キャラ属性: "+PvpTraitRules.label(trait));
        if(trait<0){
            label.setText("属性なし");label.setIcon(null);label.setFont(label.getFont().deriveFont(Font.BOLD,11f));return;
        }
        try{
            java.awt.image.BufferedImage image=UtilPC.getIcon(3,trait);
            if(image!=null){
                Image scaled=image.getScaledInstance(28,28,Image.SCALE_SMOOTH);
                label.setIcon(new ImageIcon(scaled));label.setText("");return;
            }
        }catch(RuntimeException ignored){}
        label.setText(PvpTraitRules.label(trait));label.setFont(label.getFont().deriveFont(Font.BOLD,10f));
    }

    public void force60Fps(boolean value){if(online!=null)online.force60Fps(value);}
    public int onlineFps(){return online==null?30:online.renderFps();}

    public void showOnlineResult(String title,String detail,Runnable acknowledge){
        if(online==null||onlineClosed)return;
        online.interactive(false);getPress().clear();onlineResultAck=acknowledge;onlineResultAcked=false;
        onlineResultTitle.setText(title);onlineResultDetail.setText(detail);onlineResultOk.setText("OK");onlineResultOk.setEnabled(true);onlineResult.setVisible(true);onlineResult.repaint();
    }
    public void onlineResultWaiting(String text){if(onlineResult.isVisible()&&onlineResultAcked)onlineResultDetail.setText(text);}

	public void publishOnline(PvpStageBasis displayCopy) {
		if (online != null && !onlineClosed) online.publish(displayCopy);
	}

	public void onlineStatus(String text, boolean interactive) {
		if (online == null || onlineClosed) return;
		online.interactive(interactive);
		stream.setText(text);
		stream.setToolTipText(text);
	}

	/** Called on the EDT by the lobby's fixed-network-tick pump, at the configured render rate. */
	public void renderOnlineFrame() {
		if (online == null || onlineClosed) return;
		online.update();
		updateKey();
		online.renderStep();
		StageBasis sb = online.sb;
		List<Entity> left = new ArrayList<>(), right = new ArrayList<>();
		for (Entity e : sb.le) (e.dire == 1 ? left : right).add(e);
		et.setList(left); est.setList(new ArrayList<>(left));
		ut.setList(right); ust.setList(new ArrayList<>(right));
		List<Form> lineup = new ArrayList<>();
		for (Form[] row : online.playerState().b.lu.fs) for (Form f : row) if (f != null) lineup.add(f);
		utd.setBasis(online.playerState()); utd.setList(lineup);
		ebase.setText(onlineLeftName + "  HP: " + sb.ebase.health + "/" + sb.ebase.maxH);
		ubase.setText(onlineRightName + "  HP: " + sb.ubase.health);
        int remaining=((PvpStageBasis)sb.world()).remainingTimeTicks();
        if(remaining<0)timer.setText("時間 ∞");
        else{int seconds=(remaining+PvpStageBasis.TPS-1)/PvpStageBasis.TPS;timer.setText(String.format(java.util.Locale.ROOT,"残り %02d:%02d",seconds/60,seconds%60));}
		ecount.setText(sb.entityCount(1) + "/" + sb.playerFor(1).maxNum);
		ucount.setText(sb.entityCount(-1) + "/" + sb.playerFor(-1).maxNum);
		if (bb.getPainter().dragging) bb.getPainter().dragFrame++;
		if (MainBCU.loaded) BCMusic.flush(sb.ebase.health > 0 && sb.ubase.health > 0);
        if(onlineSpecial!=null)onlineSpecial.refresh();
        StageBasis own=online.playerState();
        int rush=own.pvpRoulette==null?0:own.pvpRoulette.babyRushTicks;
        if(rush>0){
            babyRushStatus.setText(String.format(java.util.Locale.ROOT,"ぷちベビーラッシュ  残り %.1f秒",rush/(double)PvpStageBasis.TPS));
            babyRushStatus.setVisible(true);
        }else babyRushStatus.setVisible(false);
        rouletteDebugMax.setVisible(online.debugMode()&&online.rouletteMode());
        updateOpponentRouletteNotice();
		if (((Canvas) bb).isDisplayable()) bb.paint();
	}

    private void updateOpponentRouletteNotice(){
        if(online==null||!online.rouletteMode()){
            rouletteNotice.setVisible(false);opponentRouletteSpinning=false;return;
        }
        PvpRouletteState roulette=online.opponentState().pvpRoulette;
        if(roulette==null)return;
        int tick=online.sb.time;
        if(roulette.spinning&&!opponentRouletteSpinning){
            rouletteNotice.setText("相手がルーレットを開始しました！");
            rouletteNoticeUntil=tick+3*PvpStageBasis.TPS;
            rouletteNotice.setVisible(true);
        }else if(!roulette.spinning&&opponentRouletteSpinning&&roulette.lastResult>=0){
            int result=roulette.lastResult,lv=roulette.lastLevel;
            String level=lv<=0?"":lv>=4?" MAX":" Lv"+lv;
            rouletteNotice.setText("相手のルーレット結果: "+PvpRouletteState.NAMES[result]+level);
            rouletteNoticeUntil=tick+3*PvpStageBasis.TPS;
            rouletteNotice.setVisible(true);
        }
        opponentRouletteSpinning=roulette.spinning;
        if(rouletteNoticeUntil>=0&&tick>=rouletteNoticeUntil)rouletteNotice.setVisible(false);
    }

	/** Invalidate references before lobby unmounts temporary character packs. Idempotent. */
	public void detachOnline() {
		if (online == null || onlineClosed) return;
		onlineClosed = true;
        if(audioDialog!=null){audioDialog.dispose();audioDialog=null;}
		online.interactive(false);
		getPress().clear();
		if (current == this) current = null;
	}

	private void closeOnline() {
		if (online == null || onlineClosed) return;
		detachOnline();
		BCMusic.stopAll();
		onlineExit.run();
	}

	@Override protected void exit() { closeOnline(); }

	@Override protected void windowDeactivated() {
		if (online != null) getPress().clear();
		super.windowDeactivated();
	}

	@Override
	public void callBack(Object o) {
		if (online != null) return;
		BCMusic.stopAll();
		if(o instanceof Stage) {
			changePanel(new BattleInfoPage(getFront(), (Stage) o, 0, basis.sb.b, new int[1]));
		} else {
			changePanel(getFront());
		}
	}

	@Override
	public int getSpeed() {
		return spe;
	}

	@Override
	protected synchronized void keyTyped(KeyEvent e) {
		if (online != null) return;
		if (spe > -5 && e.getKeyChar() == ',') {
			spe--;
			bb.reset();
		}
		if (spe < 5 && e.getKeyChar() == '.') {
			spe++;
			bb.reset();
		}
	}

	@Override
	protected void mouseClicked(MouseEvent e) {
		if (e.getSource() == bb)
			bb.click(e.getPoint(), e.getButton());
	}

	@Override
	protected void mouseDragged(MouseEvent e) {
		if (e.getSource() == bb)
			bb.drag(e.getPoint(), e.getButton());
	}

	@Override
	protected void mousePressed(MouseEvent e) {
		if (e.getSource() == bb)
			bb.press(e.getPoint());
	}

	@Override
	protected void mouseReleased(MouseEvent e) {
		if (e.getSource() == bb)
			bb.release();
	}

	@Override
	protected void mouseWheel(MouseEvent e) {
		if (e.getSource() == bb)
			bb.wheeled(e.getPoint(), ((MouseWheelEvent) e).getWheelRotation());
	}

	@Override
	protected void renew() {
		if (online != null) return;
		backClicked = false;

		if (basis.sb.mus != null) {
			if(BCMusic.BG != null)
				BCMusic.BG.stop();

			BCMusic.play(basis.sb.mus);
			return;
		}

		if (basis.sb.getEBHP() < basis.sb.st.mush)
			if(basis.sb.st.mush == 0 || basis.sb.st.mush == 100)
				BCMusic.play(basis.sb.st.mus1);
			else {
				if(BCMusic.BG != null)
					BCMusic.BG.stop();

				BCMusic.play(basis.sb.st.mus1);
			}
		else
			BCMusic.play(basis.sb.st.mus0);
	}

	@Override
	protected synchronized void resized(int x, int y) {
		setBounds(0, 0, x, y);
		set(back, x, y, 0, 0, 200, 50);
		set(jtb, x, y, 2100, 0, 200, 50);
		if (jtb.isSelected()) {
			set(paus, x, y, 700, 0, 200, 50);
			set(rply, x, y, 900, 0, 200, 50);
			set(stream, x, y, 900, 0, online == null ? 400 : 200, 50);
			set(next, x, y, 1100, 0, 200, 50);
			set(row, x, y, 1300, 0, 200, 50);
            if(online!=null){
                set(eTraitIcon,x,y,240,0,600,24);set(ebase,x,y,240,24,600,26);
                set(uTraitIcon,x,y,1740,0,200,24);set(ubase,x,y,1740,24,200,26);
            }else{
                set(ebase, x, y, 240, 0, 600, 50);set(ubase, x, y, 1740, 0, 200, 50);
            }
			set(timer, x, y, 1500, 0, 200, 50);
			set((Canvas) bb, x, y, 190, 50, 1920, 1200);
			set(ctp, x, y, 0, 0, 0, 0);
			set(eep, x, y, 50, 100, 0, 0);
			set(eesp, x, y, 50, 100, 0, 0);
			set(eup, x, y, 50, 400, 0, 0);
			set(eusp, x, y, 50, 400, 0, 0);
			set(utdsp, x, y, 1650, 850, 0, 0);
			set(ecount, x, y, 50, 50, 0, 0);
			set(estat, x, y, 650, 50, 0, 0);
			set(ucount, x, y, 50, 350, 0, 0);
			set(ustat, x, y, 2100, 50, 0, 0);
			set(respawn, x, y, 0, 0, 0, 0);
			set(jsl, x, y, 0, 0, 0, 0);
		} else {
			set(ctp, x, y, 50, 850, 1450, 400);
			set(eep, x, y, 50, 100, 600, 700);
			set(eesp, x, y, 50, 100, 600, 700);
			set((Canvas) bb, x, y, 700, 300, 800, 500);
			set(row, x, y , 1300, 200, 200, 50);
			set(paus, x, y, 700, 200, 200, 50);
			set(rply, x, y, 900, 200, 200, 50);
			set(stream, x, y, 900, 200, online == null ? 400 : 200, 50);
			set(next, x, y, 1100, 200, 200, 50);
			set(eup, x, y, 1650, 100, 600, 700);
			set(eusp, x, y, 1650, 100, 600, 700);
			set(utdsp, x, y, 1650, 850, 600, 400);
            if(online!=null){
                set(eTraitIcon,x,y,700,210,400,34);set(ebase,x,y,700,244,400,50);
                set(uTraitIcon,x,y,1300,210,200,34);set(ubase,x,y,1300,244,200,50);
            }else{
                set(ebase, x, y, 700, 250, 400, 50);set(ubase, x, y, 1300, 250, 200, 50);
            }
			set(timer, x, y, 1100, 250, 200, 50);
			set(ecount, x, y, 50, 50, 450, 50);
			set(estat, x, y, 500, 50, 150, 50);
			set(ucount, x, y, 1650, 50, 450, 50);
			set(ustat, x, y, 2100, 50, 150, 50);
			set(respawn, x, y, 50, 800, 600, 50);
			set(jsl, x, y, 700, 800, 800, 50);
		}
        if(online!=null){
            audio.setBounds(paus.getBounds());
            if(onlineSpecial!=null){
                if(jtb.isSelected())set(onlineSpecial,x,y,1100,0,390,50);
                else set(onlineSpecial,x,y,1100,200,390,50);
            }
            if(jtb.isSelected()){
                set(onlineTag,x,y,220,60,300,34);
                set(babyRushStatus,x,y,220,92,520,38);
                set(rouletteNotice,x,y,220,130,720,38);
                set(rouletteDebugMax,x,y,220,172,300,50);
                set(onlineResult,x,y,650,430,1000,320);
            }else{
                set(onlineTag,x,y,720,310,300,34);
                set(babyRushStatus,x,y,720,342,520,38);
                set(rouletteNotice,x,y,720,380,720,38);
                set(rouletteDebugMax,x,y,720,422,300,50);
                set(onlineResult,x,y,750,390,700,320);
            }
        }
		ct.setRowHeight(size(x, y, 50));
		et.setRowHeight(size(x, y, 50));
		est.setRowHeight(size(x, y, 50));
		ut.setRowHeight(size(x, y, 50));
		ust.setRowHeight(size(x, y, 50));
		utd.setRowHeight(size(x, y, 50));
	}

	@Override
	public synchronized void onTimer(int t) {
		// Online simulation/render scheduling belongs to the EDT network pump, not Timer.p.
		if (online != null) return;
		super.onTimer(t);

		StageBasis sb = basis.sb;

		if (!pause) {
			upd++;

			if (spe < 0)
				if (upd % (1 - spe) != 0)
					return;

			basis.update();

			updateKey();

			if (spe > 0)
				for (int i = 0; i < Math.pow(2, spe); i++)
					basis.update();

			ct.update(sb.est);

			List<Entity> le = new ArrayList<>();
			List<Entity> les = new ArrayList<>();
			List<Entity> lu = new ArrayList<>();
			List<Entity> lus = new ArrayList<>();

			for (Entity e : sb.le) {
				(e.dire == 1 ? le : lu).add(e);
				(e.dire == 1 ? les : lus).add(e);
			}

			List<Form> lf = new ArrayList<>();

			for (Form[] fs : basis.sb.b.lu.fs) {
				for(Form f : fs) {
					if(f != null)
						lf.add(f);
				}
			}

			et.setList(le);
			est.setList(les);
			ut.setList(lu);
			ust.setList(lus);
			utd.setList(lf);

			BCMusic.flush(spe < 3 && sb.ebase.health > 0 && sb.ubase.health > 0);
		}

		if (basis instanceof SBRply && recd.rl != null)
			change((SBRply) basis, b -> jsl.setValue(b.prog()));

		bb.paint();

		AbEntity eba = sb.ebase;

		long h = eba.health;
		long mh = eba.maxH;

		if (!sb.st.trail)
			ebase.setText("HP: " + h + "/" + mh + ", " + 10000 * h / mh / 100.0 + "%");
		else {
			String score = "SCORE: " + sb.score;
			if (sb.est.lim != null && sb.est.lim.score > 0)
				score += "/" + sb.est.lim.score;
			ebase.setText(score);
		}
		ubase.setText("HP: " + sb.ubase.health);

		timer.setText(sb.time + "f");

		ecount.setText(sb.entityCount(1) + "/" + sb.st.max);
		ucount.setText(sb.entityCount(-1) + "/" + sb.maxNum);

		if (MainBCU.seconds)
			respawn.setText("respawn timer: " + MainBCU.toSeconds(sb.respawnTime));
		else
			respawn.setText("respawn timer: " + sb.respawnTime + "f");

		if (basis.sb.getEBHP() < basis.sb.st.bgh && basis.sb.st.bg1 != null) {
			if (!changedBG) {
				changedBG = true;

				basis.sb.changeBG(basis.sb.st.bg1);
			}
		} else if (changedBG) {
			changedBG = false;

			basis.sb.changeBG(basis.sb.st.bg);
		}

		if (!sb.isActive()) {
			if (sb.st.trail)
				BCMusic.endJingle(sb.st.lim != null && sb.score < sb.st.lim.score ? Data.SE_DEFEAT : Data.SE_DOJO);
			else
				BCMusic.endJingle(sb.ubase.health <= 0 ? Data.SE_DEFEAT : Data.SE_VICTORY);

			if (sb.st.trail || sb.ebase.health <= 0) {
				if(!exPopupShown && CommonStatic.getConfig().exContinuation && sb.st.info != null && (sb.st.info.hasExConnection() || sb.st.info.getExStages() != null)) {
					exPopupShown = true;

					Opts.showExStageSelection("EX stages found", "You can select one of these EX stages and continue the battle", sb.st, this);

					return;
				}
			}
		} else if (basis.sb.mus != null) {
			if (BCMusic.music != basis.sb.mus) {
				BCMusic.play(basis.sb.mus);

				musicChanged = sb.getEBHP() > sb.st.mush;
			}
		} else {
			if (sb.getEBHP() <= sb.st.mush && BCMusic.music != sb.st.mus1)
				if(basis.sb.st.mush == 0 || basis.sb.st.mush == 100)
					BCMusic.play(basis.sb.st.mus1);
				else {
					if(!musicChanged && !backClicked) {
						if(BCMusic.BG != null)
							BCMusic.BG.stop();

						new Thread(() -> {
							try {
								Thread.sleep(Data.MUSIC_DELAY);

								if(backClicked)
									return;

								BCMusic.play(basis.sb.st.mus1);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}).start();

						musicChanged = true;
					}
				}
			else if (BCMusic.music != sb.st.mus0 && sb.getEBHP() > sb.st.mush) {
				if(musicChanged && !backClicked) {
					if(BCMusic.BG != null)
						BCMusic.BG.stop();

					new Thread(() -> {
						try {
							Thread.sleep(Data.MUSIC_DELAY);

							if(backClicked)
								return;

							BCMusic.play(basis.sb.st.mus0);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}).start();

					musicChanged = false;
				}
			}
		}

		if (bb instanceof BBRecd) {
			BBRecd bbr = (BBRecd) bb;
			stream.setText("frame left: " + bbr.info());
		}

		if(bb.getPainter().dragging)
			bb.getPainter().dragFrame++;
	}

	@Override
	protected JButton getBackButton() {
		return back;
	}

	private void addListeners() {
		jtb.setLnr(x -> {
			remove((Canvas) bb);
			add((Canvas) bb);
			DEF_LARGE = jtb.isSelected();

			fireDimensionChanged();
		});

		back.setLnr(x -> {
			if (online != null) { closeOnline(); return; }
			backClicked = true;
			BCMusic.stopAll();
			if (bb instanceof BBRecd) {
				BBRecd bbr = (BBRecd) bb;
				if (Opts.conf("Do you want to save this video?")) {
					bbr.end();
					return;
				} else {
					bbr.quit();
				}
				bb.releaseData();
			}
			changePanel(getFront());
		});

		rply.setLnr(x -> {
			backClicked = true;
			if (basis instanceof SBCtrl)
				changePanel(new BattleInfoPage(getThis(), ((SBCtrl) basis).getData(), 0));
			if (basis instanceof SBRply)
				if (recd.rl == null)
					changePanel(new RecdSavePage(getThis(), recd));
				else
					changePanel(new BattleInfoPage(this, (SBRply) basis));
		});

		paus.addActionListener(arg0 -> {
			pause = !pause;
			jsl.setEnabled(pause);
		});

		next.addActionListener(arg0 -> {
			pause = false;
			timer(0);

			if (CommonStatic.getConfig().performanceModeBattle) {
				timer(0);
			}

			pause = true;
		});

		row.addActionListener(a -> {
			CommonStatic.getConfig().twoRow = !CommonStatic.getConfig().twoRow;
			row.setText(get(MainLocale.PAGE, CommonStatic.getConfig().twoRow ? "tworow" : "onerow"));
		});

		jsl.addChangeListener(e -> {
			if (jsl.getValueIsAdjusting() || isAdj() || !(basis instanceof SBRply))
				return;
			((SBRply) basis).restoreTo(jsl.getValue());
			bb.reset();
		});

		estat.addActionListener(a -> {
			eep.setVisible(!estat.isSelected());
			eesp.setVisible(estat.isSelected());
		});

		ustat.addActionListener(a -> {
			eup.setVisible(!ustat.isSelected());
			eusp.setVisible(ustat.isSelected());
		});
	}

	private void ini() {
		add(back);
		add(eup);
		add(eusp);
		add(eep);
		add(eesp);
		add(ctp);
		add(utdsp);
		add((Canvas) bb);
		add(paus);
		add(next);
		add(ebase);
		add(ubase);
		add(timer);
		add(ecount);
		add(estat);
		add(ucount);
		add(ustat);
		add(respawn);
		add(jtb);
		add(row);
		row.setText(get(MainLocale.PAGE, CommonStatic.getConfig().twoRow ? "tworow" : "onerow"));
		estat.setSelected(false);
		eep.setVisible(!estat.isSelected());
		eesp.setVisible(estat.isSelected());
		ustat.setSelected(false);
		eup.setVisible(!ustat.isSelected());
		eusp.setVisible(ustat.isSelected());
		if (bb instanceof BBRecd)
			add(stream);
		else {
			add(rply);
			if (recd != null && recd.rl != null) {
				add(jsl);
				jsl.setEnabled(pause);
			}
		}
		addListeners();
	}

}
