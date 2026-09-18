package online.ui;

import common.CommonStatic;
import common.battle.PvpStageBasis;
import common.battle.PvpRouletteState;
import common.battle.SBCtrl;
import common.battle.StageBasis;
import common.util.Data;
import common.util.stage.Replay;
import online.sync.InputFrame;
import page.battle.BattleBox;

import java.util.function.IntConsumer;

/**
 * Adapts the original BBCtrl input and BBPainter HUD to an online match.
 * sb is ALWAYS a disposable display copy, never the canonical simulation.
 * No native act_* or BattleField.update call may advance gameplay here.
 */
public final class OnlineBattleField extends SBCtrl implements BattleBox.PlayerView {
    private final CommonStatic.FakeKey keys;
    private final IntConsumer send;
    private final int direction;
    private int frontRow, changeFrame = -1;
    private boolean goingUp, interactive = true, halfAdvanced;
    private long published;
    private boolean force60;
    public void force60Fps(boolean value){force60=value;}
    public int renderFps(){return force60||CommonStatic.getConfig().performanceModeBattle?60:30;}

    public OnlineBattleField(CommonStatic.FakeKey keys, PvpStageBasis displayCopy,
                             int direction, IntConsumer send) {
        super(keys, displayCopy, new Replay(displayCopy.playerFor(direction).b,
                displayCopy.st.id, 0, new int[3], 0, false));
        if (direction != -1 && direction != 1) throw new IllegalArgumentException("Invalid player side");
        this.keys = keys;
        this.direction = direction;
        this.send = send;
        published = System.nanoTime();
    }

    @Override public StageBasis playerState() { return sb.playerFor(direction); }

    /** Preserve camera/row selection across new authoritative snapshots without sending them. */
    public void publish(PvpStageBasis displayCopy) {
        int elapsed = Math.max(0, displayCopy.time - sb.time);
        displayCopy.pos = sb.pos;
        displayCopy.siz = sb.siz;
        while (changeFrame >= 0 && elapsed-- > 0) {
            changeFrame--;
            if (changeFrame == Data.LINEUP_CHANGE_TIME / 2 - 1) frontRow = 1 - frontRow;
            if (changeFrame == 0) changeFrame = -1;
        }
        sb = displayCopy;
        syncSpecialHud();
        syncRow();
        published = System.nanoTime();
        halfAdvanced = false;
    }

    public void renderStep() {
        if (renderFps()==60 && !halfAdvanced
                && System.nanoTime() - published >= 16_666_667L) {
            ((PvpStageBasis) sb).advanceDisplay();
            halfAdvanced = true;
        }
    }

    public void interactive(boolean value) { interactive = value; if (!value) action.clear(); }
    public boolean debugMode(){return ((PvpStageBasis)sb).debugMode();}
    public boolean rouletteMode(){return ((PvpStageBasis)sb).specialMode()==online.net.lobby.RoomRules.SpecialMode.ROULETTE;}
    public void debugRouletteMax(){
        if(interactive&&debugMode()&&rouletteMode())send.accept(InputFrame.DEBUG_ROULETTE_MAX);
    }

    @Override public void update() { actions(); }

    @Override protected void actions() {
        if (!interactive) { action.clear(); return; }
        StageBasis own = playerState();
        if (own.ownBase().health <= 0 || ((PvpStageBasis) sb).winner() != -2) { action.clear(); return; }
        if (action.contains(-1) || (keys.pressed(-1, 0) && own.work_lv < 8 && own.money > own.upgradeCost)) {
            send.accept(InputFrame.WORKER); keys.remove(-1, 0);
        }
        boolean specialPressed=keys.pressed(-1,1);
        boolean specialReady=false;
        PvpStageBasis world=(PvpStageBasis)sb;
        switch(world.specialMode()) {
            case CANNON: specialReady=own.cannon==own.maxCannon; break;
            case ROULETTE: specialReady=own.pvpRoulette!=null&&!own.pvpRoulette.spinning&&own.pvpRoulette.gauge>=PvpRouletteState.MAX_GAUGE; break;
            default: break;
        }
        if ((action.contains(-2) || specialPressed) && world.specialMode()!=online.net.lobby.RoomRules.SpecialMode.NONE) {
            if(action.contains(-2)||specialReady)send.accept(InputFrame.SPECIAL);
            if(specialPressed)keys.remove(-1, 1);
        }
        boolean twoRows = CommonStatic.getConfig().twoRow;
        if (!twoRows && !own.isOneLineup && changeFrame < 0) {
            if (keys.pressed(-3, 0) || action.contains(-4)) { changeRow(true); keys.remove(-3, 0); }
            else if (action.contains(-5)) changeRow(false);
        }
        boolean lock = keys.pressed(-2, 0) || action.contains(10);
        for (int i = 0; i < 10; i++) {
            int row = i / 5, col = i % 5;
            boolean pressed = (twoRows || row == frontRow) && keys.pressed(row, col);
            boolean clicked = action.contains(i);
            if (own.b.lu.fs[row][col] == null || (!pressed && !clicked)) continue;
            if (lock) {
                send.accept(1 << (12 + i));
                if (pressed) keys.remove(row, col);
            } else send.accept(1 << i);
        }
        action.clear();
    }

    private void changeRow(boolean up) {
        changeFrame = Data.LINEUP_CHANGE_TIME; goingUp = up; syncRow();
    }
    public String specialStatus() {
        PvpStageBasis world=(PvpStageBasis)sb;
        StageBasis own=playerState();
        switch(world.specialMode()) {
            case NONE:return "特殊機能: なし";
            case CANNON:return "にゃんこ砲 "+Math.min(100,own.cannon*100/Math.max(1,own.maxCannon))+"%";
            case ROULETTE:
                if(own.pvpRoulette==null)return "対戦ルーレット";
                if(own.pvpRoulette.spinning) {
                    int remain=Math.max(0,PvpRouletteState.AUTO_SPIN_TICKS-own.pvpRoulette.spinTicks);
                    double seconds=remain/(double)PvpStageBasis.TPS;
                    return String.format(java.util.Locale.ROOT,"対戦ルーレット 回転中 / %.1f秒 / %s",seconds,
                            PvpRouletteState.NAMES[own.pvpRoulette.currentResult()]);
                }
                if(own.pvpRoulette.gauge>=PvpRouletteState.MAX_GAUGE)return "対戦ルーレット 100% / 発動可能";
                String last="";
                if(own.pvpRoulette.lastResult>=0) {
                    int lv=own.pvpRoulette.lastLevel;
                    String level=lv<=0?"":(lv>=4?" MAX":" Lv"+lv);
                    last=" / 前回: "+PvpRouletteState.NAMES[own.pvpRoulette.lastResult]+level;
                }
                return "対戦ルーレット "+own.pvpRoulette.gauge/10+"%"+last;
            default:return "";
        }
    }
    private void syncSpecialHud() {
        PvpStageBasis world=(PvpStageBasis)sb;
        StageBasis own=playerState();
        // Roulette owns a separate gauge; never reuse the native cannon charge meter.
        if(world.specialMode()!=online.net.lobby.RoomRules.SpecialMode.CANNON)own.cannon=0;
    }
    private void syncRow() {
        StageBasis own = playerState();
        own.frontLineup = frontRow;
        own.changeFrame = changeFrame;
        own.changeDivision = Data.LINEUP_CHANGE_TIME / 2;
        own.lineupChanging = changeFrame >= 0;
        own.goingUp = goingUp;
    }

    @Override public Replay getData() {
        throw new UnsupportedOperationException("Online battles cannot use the single-player replay format");
    }
}
