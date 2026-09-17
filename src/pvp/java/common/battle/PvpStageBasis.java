package common.battle;

import common.battle.entity.ECastle;
import common.battle.entity.EUnit;
import common.pack.Identifier;
import common.util.stage.*;
import common.util.pack.Background;
import online.sync.InputFrame;
import java.util.*;

/** Two independently owned player states, a single simulation world, no native CPU spawner. */
public final class PvpStageBasis extends StageBasis {
    public static final int TPS = 30;
    private String matchScope;
    public static PvpStageBasis create(String match, BasisLU left, BasisLU right, long seed, int leftSeat) throws Exception {
        return PvpTiming.inMatch(match, () -> {PvpStageBasis b=new PvpStageBasis(left,right,seed,leftSeat);b.matchScope=match;return b;});
    }
    public static final int ARENA_LENGTH = 6000;
    public static final int MAX_UNITS = 50;

    public PvpStageBasis(BasisLU left, BasisLU right, long seed, int leftSeat) {
        this(arena(), left, right, seed, leftSeat);
    }
    private PvpStageBasis(Stage arena, BasisLU left, BasisLU right, long seed, int leftSeat) {
        super(null, new EStage(arena, 0), right, new int[3], seed, false);
        if (leftSeat != 0 && leftSeat != 1) throw new IllegalArgumentException("Invalid player seat");
        pvpRoot = this; pvpDirection = -1; pvpSeat = 1-leftSeat;
        StageBasis other = new StageBasis(null, new EStage(arena,0), left, new int[3], seed, false);
        pvpOther = other; other.pvpRoot = this; other.pvpOther = this;
        other.pvpDirection = 1; other.pvpSeat = leftSeat;
        other.r = r; r.deterministicVisuals = true;
        other.le = le; other.tempe = tempe; other.lw = lw; other.tlw = tlw; other.lea = lea; other.la = la;
        other.ebaseSmoke = ebaseSmoke; other.ubaseSmoke = ubaseSmoke;
        ebase = new ECastle(other, left); ebase.added(1,800);
        other.ebase = ebase; other.ubase = ubase;
        bgEffect = other.bgEffect = null;
        maxNum = other.maxNum = MAX_UNITS;
        maxMoney = b.t().getMaxMon(work_lv, false);
        other.maxMoney = other.b.t().getMaxMon(other.work_lv, false);
    }
    public StageBasis left() { return pvpOther; }
    public StageBasis right() { return this; }
    /** tick starts at zero, time counts the number of COMPLETED simulation ticks. */
    public void step(InputFrame frame) {
        if (frame.tick != time) throw new IllegalArgumentException("Non-sequential simulation tick");
        if (winner() != -2) throw new IllegalStateException("Battle already finished");
        try { PvpTiming.inMatch(matchScope, () -> { PvpTiming.logic(() -> {
            time++; pvpOther.time = time;
            // Stable player identity, not physical side, determines simultaneous command order.
            if (pvpSeat == 0) { input(this,frame.right); input(pvpOther,frame.left); }
            else { input(pvpOther,frame.left); input(this,frame.right); }
            update();
            if(le.size()+tempe.size()>512 || lw.size()+tlw.size()>8192 || la.size()>8192)
                throw new IllegalStateException("Custom battle exceeds entity/effect safety limit");
        }); return null; }); } catch(RuntimeException e){throw e;} catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void input(StageBasis player, int mask) {
        if ((mask & InputFrame.WORKER) != 0) player.act_mon();
        if ((mask & InputFrame.CANNON) != 0) player.act_can();
        for (int i=0;i<10;i++) {
            if ((mask & (1<<(12+i))) != 0) player.act_lock(i/5,i%5);
            // Calls without a pressed bit intentionally retain native auto-spawn locks.
            player.act_spawn(i/5,i%5,(mask & (1<<i)) != 0);
        }
    }
    /** Called by StageBasis at the SAME phase as the right-player economy. */
    protected void updateOtherEconomy(boolean active) {
        StageBasis p=pvpOther;
        if (p.unitRespawnTime > 0 && active) p.unitRespawnTime--;
        p.elu.update();
        for(int i=0;i<2;i++)for(int j=0;j<5;j++) {
            if (p.spiritEmphasizeCount[i][j]>0 && (time-p.spiritEmphasizeStartTime[i][j])%4==0) p.spiritEmphasizeCount[i][j]--;
            if(p.spiritCooldown[i][j]>0 && --p.spiritCooldown[i][j]==0) {
                p.spiritEmphasizeStartTime[i][j]=time; p.spiritEmphasizeCount[i][j]=10;
            }
        }
        if (active) {
            p.cannon++;
            p.maxMoney=p.b.t().getMaxMon(p.work_lv,StageLimit.isComboBanned(p.est.lim,C_M_MAX));
            int income=p.b.t().getMonInc(p.work_lv);
            if(!StageLimit.isComboBanned(p.est.lim,C_M_INC))income*=p.b.getInc(C_M_INC)/100+1;
            p.money+=income;
        }
    }
    protected void finishOtherEconomy() {
        StageBasis p=pvpOther;
        for(int i=0;i<2;i++)for(int j=0;j<5;j++) {
            if(Arrays.stream(p.cdDelay[i][j]).anyMatch(v -> v!=0)) {
                p.elu.delay(i,j,p.cdDelay[i][j]);p.cdDelay[i][j]=new int[3];
            }
        }
        p.cannon=Math.min(p.maxCannon,Math.max(0,p.cannon));
        p.money=Math.min(p.maxMoney,Math.max(0,p.money));
    }
    /** -2 ongoing; -1 draw; 0 physical left wins; 1 physical right wins. */
    public int winner() {
        if(ebase.health>0 && ubase.health>0)return -2;
        if(ebase.health<=0 && ubase.health<=0)return -1;
        return ebase.health>0?0:1;
    }
    public PvpStageBasis displayCopy() { return (PvpStageBasis)clone(); }
    /** Only call on a displayCopy; never on the canonical battle state. */
    public void advanceDisplay() {
        PvpTiming.halfStep(() -> {updateAnimation();pvpOther.canon.updateAnimation();});
    }
    private static Stage arena() {
        ArenaMap map=new ArenaMap(); ArenaStage stage=new ArenaStage(map);
        stage.names.put("Online PvP");stage.len=ARENA_LENGTH;stage.max=MAX_UNITS;
        stage.non_con=true;stage.drop=false;stage.health=60000;stage.data=new SCDef(0);
        stage.bg=new Identifier<>(Identifier.DEF,Background.class,0);
        stage.castle=CastleList.defset().stream().sorted(Comparator.comparing(CastleList::getSID))
                .map(c -> c.getRaw(0)).filter(Objects::nonNull).map(c -> c.id).findFirst()
                .orElseThrow(() -> new IllegalStateException("Default castle assets are not loaded"));
        return stage;
    }
    private static final class ArenaMap extends StageMap {
        ArenaMap(){super(new Identifier<>("pvp_rules",StageMap.class,0));}
        @Override public MapColc getCont(){return Stage.CLIPMC;}
    }
    private static final class ArenaStage extends Stage {
        private final StageMap map;
        ArenaStage(StageMap map){super(map);this.map=map;}
        @Override public StageMap getCont(){return map;}
    }
}
