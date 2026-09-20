package online.ui;

import common.CommonStatic;
import common.battle.PvpStageBasis;
import common.battle.PvpRouletteState;
import common.battle.SBCtrl;
import common.battle.entity.Entity;
import common.battle.StageBasis;
import common.util.Data;
import common.util.stage.Replay;
import online.sync.InputFrame;
import page.battle.BattleBox;

import java.util.Map;
import java.util.function.IntConsumer;

/**
 * Adapts the original BBCtrl input and BBPainter HUD to an online match.
 * sb is ALWAYS a disposable display copy, never the canonical simulation.
 * No native act_* or BattleField.update call may advance gameplay here.
 */
@SuppressWarnings("unchecked")
public final class OnlineBattleField extends SBCtrl implements BattleBox.PlayerView {
    private final CommonStatic.FakeKey keys;
    private final IntConsumer send;
    private final int direction;
    private int frontRow, changeFrame = -1;
    private boolean goingUp, interactive = true, halfAdvanced, renderedSincePublish, battleUiHidden;
    private boolean autoRoulette, autoRouletteQueued;
    private int authoritativeTick;
    private Map<Long,Entity> entityIndex;
    /** Local presentation order only: visible slot -> canonical lockstep slot. */
    private final int[] visibleToCanonical={0,1,2,3,4,5,6,7,8,9};
    /** Online PvP presentation is always 60 FPS; simulation remains fixed at 30 TPS. */
    public void force60Fps(boolean value){/* retained for protocol/UI compatibility */}
    public int renderFps(){return 60;}

    public OnlineBattleField(CommonStatic.FakeKey keys, PvpStageBasis displayCopy,
                             int direction, IntConsumer send) {
        super(keys, displayCopy, new Replay(displayCopy.playerFor(direction).b,
                displayCopy.st.id, 0, new int[3], 0, false));
        if (direction != -1 && direction != 1) throw new IllegalArgumentException("Invalid player side");
        this.keys = keys;
        this.direction = direction;
        this.send = send;
        authoritativeTick=displayCopy.time;
        entityIndex=PvpPresentationDelta.index(displayCopy);
    }

    @Override public StageBasis playerState() { return sb.playerFor(direction); }
    public StageBasis opponentState(){return sb.playerFor(-direction);}
    public int playerDirection(){return direction;}

    /** Preserve camera/row selection across new authoritative snapshots without sending them. */
    public void publish(PvpStageBasis displayCopy) {
        advancePresentationClock(displayCopy.time);
        displayCopy.pos = sb.pos;
        displayCopy.siz = sb.siz;
        applyLocalSlotOrder(displayCopy.playerFor(direction));
        sb = displayCopy;
        entityIndex=PvpPresentationDelta.index(displayCopy);
        syncSpecialHud();
        syncRow();
        halfAdvanced = false;
        renderedSincePublish = false;
    }

    /** Apply primitive-only 30TPS motion/HUD state between expensive full snapshots. */
    public void applyDelta(PvpPresentationDelta delta) {
        if(delta==null||delta.tick<authoritativeTick)return;
        advancePresentationClock(delta.tick);
        delta.apply((PvpStageBasis)sb,entityIndex,direction,visibleToCanonical);
        syncSpecialHud();
        syncRow();
        halfAdvanced=false;
        renderedSincePublish=false;
    }

    private void advancePresentationClock(int tick){
        int elapsed=Math.max(0,tick-authoritativeTick);authoritativeTick=Math.max(authoritativeTick,tick);
        while(changeFrame>=0&&elapsed-->0){
            changeFrame--;
            if(changeFrame==Data.LINEUP_CHANGE_TIME/2-1)frontRow=1-frontRow;
            if(changeFrame==0)changeFrame=-1;
        }
    }

    public void renderStep() {
        if (renderFps()!=60) return;
        // A publication is the authoritative 30 TPS frame. Draw it once unchanged,
        // then draw exactly one visual half-step on the next 60 FPS presentation.
        // Wall-clock thresholds made this half-step disappear whenever EDT/network
        // jitter moved a render just below 16.7 ms.
        if (!renderedSincePublish) {
            renderedSincePublish = true;
            return;
        }
        if (!halfAdvanced) {
            ((PvpStageBasis) sb).advanceDisplay();
            halfAdvanced = true;
        }
    }

    public void interactive(boolean value) { interactive = value; if (!value) action.clear(); }
    public void setBattleUiHidden(boolean value){battleUiHidden=value;if(value)interactive(false);}
    public boolean battleUiHidden(){return battleUiHidden;}
    public boolean debugMode(){return ((PvpStageBasis)sb).debugMode();}
    public boolean rouletteMode(){return ((PvpStageBasis)sb).specialMode()==online.net.lobby.RoomRules.SpecialMode.ROULETTE;}
    public boolean autoRoulette(){return autoRoulette;}
    public void toggleRouletteAuto(){if(rouletteMode()){autoRoulette=!autoRoulette;autoRouletteQueued=false;}}
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
            case ROULETTE: specialReady=own.pvpRoulette!=null&&own.pvpRoulette.canPress(); break;
            default: break;
        }
        if ((action.contains(-2) || specialPressed) && world.specialMode()!=online.net.lobby.RoomRules.SpecialMode.NONE) {
            boolean allowed=world.specialMode()!=online.net.lobby.RoomRules.SpecialMode.ROULETTE||specialReady;
            if(allowed){
                send.accept(InputFrame.SPECIAL);
                if(world.specialMode()==online.net.lobby.RoomRules.SpecialMode.ROULETTE)autoRouletteQueued=true;
            }
            if(specialPressed)keys.remove(-1, 1);
        }
        if(world.specialMode()==online.net.lobby.RoomRules.SpecialMode.ROULETTE){
            if(!specialReady)autoRouletteQueued=false;
            else if(autoRoulette&&!autoRouletteQueued){
                send.accept(InputFrame.SPECIAL);
                autoRouletteQueued=true;
            }
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
            if (visibleForm(i) == null || (!pressed && !clicked)) continue;
            int canonical=visibleToCanonical[i];
            if (lock) {
                send.accept(1 << (12 + canonical));
                if (pressed) keys.remove(row, col);
            } else send.accept(1 << canonical);
        }
        action.clear();
    }

    public int canonicalSlotForVisible(int visible){
        return visible<0||visible>=visibleToCanonical.length?-1:visibleToCanonical[visible];
    }

    public common.util.unit.Form visibleForm(int visible){
        int canonical=canonicalSlotForVisible(visible);
        return canonical<0?null:playerState().pvpSlotForm(canonical/5,canonical%5);
    }

    public boolean swapVisibleSlots(int from,int to){
        if(!interactive||battleUiHidden||from<0||from>=10||to<0||to>=10||from==to)return false;
        int tmp=visibleToCanonical[from];visibleToCanonical[from]=visibleToCanonical[to];visibleToCanonical[to]=tmp;
        swapSlotState(playerState(),from,to);
        return true;
    }

    private void applyLocalSlotOrder(StageBasis own){
        if(identitySlotOrder())return;
        permute(own.elu.price);permute(own.elu.basePrice);permute(own.elu.cool);permute(own.elu.maxC);
        permute(own.elu.tick);permute(own.elu.cdDownOrb);permute(own.elu.priceDownOrb);
        permute(own.locks);
        permute(own.spiritCooldown);permute(own.frameOffCd);permute(own.cdDelay);permute(own.cdDelayVisual);
        permute(own.summonerSummoned);permute(own.spiritSummoned);permute(own.spiritEmphasizeCount);permute(own.spiritEmphasizeStartTime);permute(own.deployDupe);
        if(own.selectedUnit[0]>=0&&own.selectedUnit[1]>=0){
            int canonical=own.selectedUnit[0]*5+own.selectedUnit[1],visible=visibleForCanonical(canonical);
            own.selectedUnit[0]=visible<0?-1:visible/5;own.selectedUnit[1]=visible<0?-1:visible%5;
        }
    }

    private boolean identitySlotOrder(){for(int i=0;i<10;i++)if(visibleToCanonical[i]!=i)return false;return true;}
    private int visibleForCanonical(int canonical){for(int i=0;i<10;i++)if(visibleToCanonical[i]==canonical)return i;return -1;}

    private void swapSlotState(StageBasis own,int a,int b){
        swap(own.elu.price,a,b);swap(own.elu.basePrice,a,b);swap(own.elu.cool,a,b);swap(own.elu.maxC,a,b);
        swap(own.elu.tick,a,b);swap(own.elu.cdDownOrb,a,b);swap(own.elu.priceDownOrb,a,b);
        swap(own.locks,a,b);
        swap(own.spiritCooldown,a,b);swap(own.frameOffCd,a,b);swap(own.cdDelay,a,b);swap(own.cdDelayVisual,a,b);
        swap(own.summonerSummoned,a,b);swap(own.spiritSummoned,a,b);swap(own.spiritEmphasizeCount,a,b);swap(own.spiritEmphasizeStartTime,a,b);swap(own.deployDupe,a,b);
    }

    private <T> void permute(T[][] values){Object[] old=new Object[10];for(int i=0;i<10;i++)old[i]=values[i/5][i%5];for(int i=0;i<10;i++)values[i/5][i%5]=(T)old[visibleToCanonical[i]];}
    private void permute(int[][] values){int[] old=new int[10];for(int i=0;i<10;i++)old[i]=values[i/5][i%5];for(int i=0;i<10;i++)values[i/5][i%5]=old[visibleToCanonical[i]];}
    private void permute(long[][] values){long[] old=new long[10];for(int i=0;i<10;i++)old[i]=values[i/5][i%5];for(int i=0;i<10;i++)values[i/5][i%5]=old[visibleToCanonical[i]];}
    private void permute(boolean[][] values){boolean[] old=new boolean[10];for(int i=0;i<10;i++)old[i]=values[i/5][i%5];for(int i=0;i<10;i++)values[i/5][i%5]=old[visibleToCanonical[i]];}
    private void permute(int[][][] values){int[][] old=new int[10][];for(int i=0;i<10;i++)old[i]=values[i/5][i%5];for(int i=0;i<10;i++)values[i/5][i%5]=old[visibleToCanonical[i]];}

    private static <T> void swap(T[][] values,int a,int b){T t=values[a/5][a%5];values[a/5][a%5]=values[b/5][b%5];values[b/5][b%5]=t;}
    private static void swap(int[][] values,int a,int b){int t=values[a/5][a%5];values[a/5][a%5]=values[b/5][b%5];values[b/5][b%5]=t;}
    private static void swap(long[][] values,int a,int b){long t=values[a/5][a%5];values[a/5][a%5]=values[b/5][b%5];values[b/5][b%5]=t;}
    private static void swap(boolean[][] values,int a,int b){boolean t=values[a/5][a%5];values[a/5][a%5]=values[b/5][b%5];values[b/5][b%5]=t;}
    private static void swap(int[][][] values,int a,int b){int[] t=values[a/5][a%5];values[a/5][a%5]=values[b/5][b%5];values[b/5][b%5]=t;}

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
                    int remain=Math.max(0,own.pvpRoulette.spinDurationTicks-own.pvpRoulette.spinTicks);
                    double seconds=remain/(double)PvpStageBasis.TPS;
                    return String.format(java.util.Locale.ROOT,"対戦ルーレット 回転中 / %.1f秒 / %s%s",seconds,
                            PvpRouletteState.NAMES[own.pvpRoulette.currentResult()],rouletteAutoText());
                }
                if(own.pvpRoulette.pendingResult>=0)
                    return "対戦ルーレット "+own.pvpRoulette.gauge/10+"% / 結果演出中"+rouletteAutoText();
                if(own.pvpRoulette.repeatDelayTicks>0&&own.pvpRoulette.gauge>=PvpRouletteState.MAX_GAUGE) {
                    double seconds=own.pvpRoulette.repeatDelayTicks/(double)PvpStageBasis.TPS;
                    return String.format(java.util.Locale.ROOT,"対戦ルーレット 100%% / 次回まで %.1f秒%s",seconds,rouletteAutoText());
                }
                if(own.pvpRoulette.gauge>=PvpRouletteState.MAX_GAUGE)return "対戦ルーレット 100% / 発動可能"+rouletteAutoText();
                String last="";
                if(own.pvpRoulette.lastResult>=0) {
                    int lv=own.pvpRoulette.lastLevel;
                    String level=lv<=0?"":(lv>=4?" MAX":" Lv"+lv);
                    last=" / 前回: "+PvpRouletteState.NAMES[own.pvpRoulette.lastResult]+level;
                }
                return "対戦ルーレット "+own.pvpRoulette.gauge/10+"%"+last+rouletteAutoText();
            default:return "";
        }
    }
    private String rouletteAutoText(){return autoRoulette?" / 自動ON":"";}
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
