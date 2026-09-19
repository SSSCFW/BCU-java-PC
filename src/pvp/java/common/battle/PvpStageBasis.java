package common.battle;

import common.battle.entity.ECastle;
import common.battle.entity.EUnit;
import common.pack.Identifier;
import common.pack.UserProfile;
import common.util.stage.*;
import common.util.pack.Background;
import online.sync.InputFrame;
import online.net.lobby.RoomRules;
import online.net.lobby.PvpBattleMusic;
import java.util.*;

/** Two independently owned player states, a single simulation world, no native CPU spawner. */
public final class PvpStageBasis extends StageBasis {
    public static final int TPS = 30;
    public static final double DEFAULT_CASTLE_HEALTH_MULTIPLIER=20.0;
    public static final double MIN_CASTLE_HEALTH_MULTIPLIER=0.1, MAX_CASTLE_HEALTH_MULTIPLIER=1000.0;
    public final int pvpSpecialMode;
    public final boolean pvpDebugMode;
    private final int pvpLeftTrait,pvpRightTrait;
    private String matchScope;
    public static PvpStageBasis create(String match, BasisLU left, BasisLU right, long seed, int leftSeat) throws Exception {
        return create(match,left,right,seed,leftSeat,RoomRules.DEFAULT);
    }
    public static PvpStageBasis create(String match, BasisLU left, BasisLU right, long seed, int leftSeat, RoomRules rules) throws Exception {
        return create(match,left,right,seed,leftSeat,rules,DEFAULT_CASTLE_HEALTH_MULTIPLIER,DEFAULT_CASTLE_HEALTH_MULTIPLIER);
    }
    public static PvpStageBasis create(String match, BasisLU left, BasisLU right, long seed, int leftSeat, RoomRules rules,
                                       double leftCastleMultiplier,double rightCastleMultiplier) throws Exception {
        return create(match,left,right,seed,leftSeat,rules,leftCastleMultiplier,rightCastleMultiplier,
                online.net.lobby.PvpTraitRules.NONE,online.net.lobby.PvpTraitRules.NONE);
    }
    public static PvpStageBasis create(String match, BasisLU left, BasisLU right, long seed, int leftSeat, RoomRules rules,
                                       double leftCastleMultiplier,double rightCastleMultiplier,int leftTrait,int rightTrait) throws Exception {
        return PvpTiming.inMatch(match, () -> {PvpStageBasis b=new PvpStageBasis(left,right,seed,leftSeat,rules,leftCastleMultiplier,rightCastleMultiplier,leftTrait,rightTrait);b.matchScope=match;return b;});
    }
    public static final int ARENA_LENGTH = 6000;
    public static final int MAX_UNITS = 50;

    public PvpStageBasis(BasisLU left, BasisLU right, long seed, int leftSeat) {
        this(left,right,seed,leftSeat,RoomRules.DEFAULT);
    }
    public PvpStageBasis(BasisLU left, BasisLU right, long seed, int leftSeat, RoomRules rules) {
        this(left,right,seed,leftSeat,rules,DEFAULT_CASTLE_HEALTH_MULTIPLIER,DEFAULT_CASTLE_HEALTH_MULTIPLIER);
    }
    public PvpStageBasis(BasisLU left, BasisLU right, long seed, int leftSeat, RoomRules rules,
                         double leftCastleMultiplier,double rightCastleMultiplier) {
        this(left,right,seed,leftSeat,rules,leftCastleMultiplier,rightCastleMultiplier,
                online.net.lobby.PvpTraitRules.NONE,online.net.lobby.PvpTraitRules.NONE);
    }
    public PvpStageBasis(BasisLU left, BasisLU right, long seed, int leftSeat, RoomRules rules,
                         double leftCastleMultiplier,double rightCastleMultiplier,int leftTrait,int rightTrait) {
        this(arena(resolveRandomRules(rules,seed)),left,right,seed,leftSeat,resolveRandomRules(rules,seed),
                leftCastleMultiplier,rightCastleMultiplier,leftTrait,rightTrait);
    }
    private PvpStageBasis(Stage arena, BasisLU left, BasisLU right, long seed, int leftSeat, RoomRules rules,
                          double leftCastleMultiplier,double rightCastleMultiplier,int leftTrait,int rightTrait) {
        super(null, new EStage(arena, 0), right, new int[3], seed, false);
        this.pvpSpecialMode=rules.specialMode.ordinal();
        this.pvpDebugMode=rules.debugMode;
        online.net.lobby.PvpTraitRules.validate(leftTrait,0);online.net.lobby.PvpTraitRules.validate(rightTrait,0);
        if(leftTrait==online.net.lobby.PvpTraitRules.RANDOM||rightTrait==online.net.lobby.PvpTraitRules.RANDOM)
            throw new IllegalArgumentException("戦闘開始時の属性は解決済みである必要があります");
        this.pvpLeftTrait=leftTrait;this.pvpRightTrait=rightTrait;
        if (leftSeat != 0 && leftSeat != 1) throw new IllegalArgumentException("Invalid player seat");
        pvpRoot = this; pvpDirection = -1; pvpSeat = 1-leftSeat;
        StageBasis other = new StageBasis(null, new EStage(arena,0), left, new int[3], seed, false);
        pvpOther = other; other.pvpRoot = this; other.pvpOther = this;
        other.pvpDirection = 1; other.pvpSeat = leftSeat;
        other.r = r; r.deterministicVisuals = true;
        pvpRoulette = new PvpRouletteState(r);
        other.pvpRoulette = new PvpRouletteState(r);
        other.le = le; other.tempe = tempe; other.lw = lw; other.tlw = tlw; other.lea = lea; other.la = la;
        other.ebaseSmoke = ebaseSmoke; other.ubaseSmoke = ubaseSmoke;
        ebase = new ECastle(other, left); ebase.added(1,800);
        other.ebase = ebase; other.ubase = ubase;
        applyCastleHealthMultiplier(other,leftCastleMultiplier);
        applyCastleHealthMultiplier(this,rightCastleMultiplier);
        other.pvpRoulette.initializeCharge(other);
        pvpRoulette.initializeCharge(this);
        bgEffect = other.bgEffect = null;
        maxNum = other.maxNum = MAX_UNITS;
        maxMoney = b.t().getMaxMon(work_lv, false);
        other.maxMoney = other.b.t().getMaxMon(other.work_lv, false);
    }
    public RoomRules.SpecialMode specialMode() { return RoomRules.SpecialMode.values()[pvpSpecialMode]; }
    public boolean debugMode() { return pvpDebugMode; }
    public int traitForDirection(int direction){return direction==1?pvpLeftTrait:direction==-1?pvpRightTrait:online.net.lobby.PvpTraitRules.NONE;}
    public int leftTrait(){return pvpLeftTrait;}
    public int rightTrait(){return pvpRightTrait;}
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
            if(specialMode()==RoomRules.SpecialMode.ROULETTE) {
                left().pvpRoulette.advance(this,left());
                right().pvpRoulette.advance(this,right());
            }
            if(le.size()+tempe.size()>512 || lw.size()+tlw.size()>8192 || la.size()>8192)
                throw new IllegalStateException("Custom battle exceeds entity/effect safety limit");
        }); return null; }); } catch(RuntimeException e){throw e;} catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void input(StageBasis player, int mask) {
        PvpStageBasis world=(PvpStageBasis)player.world();
        if ((mask & InputFrame.WORKER) != 0) player.act_mon();
        if ((mask & InputFrame.DEBUG_ROULETTE_MAX) != 0
                && world.debugMode() && world.specialMode()==RoomRules.SpecialMode.ROULETTE
                && player.pvpRoulette!=null && !player.pvpRoulette.spinning) {
            player.pvpRoulette.gauge=PvpRouletteState.MAX_GAUGE;
            player.pvpRoulette.targetGauge=PvpRouletteState.MAX_GAUGE;
            player.pvpRoulette.castleDamageFastGauge=0;
            player.pvpRoulette.chargeClock=0;
        }
        if ((mask & InputFrame.SPECIAL) != 0) {
            if(world.specialMode()==RoomRules.SpecialMode.CANNON) player.act_can();
            else if(world.specialMode()==RoomRules.SpecialMode.ROULETTE) player.pvpRoulette.press(world,player);
        }
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
        boolean cannonMode=specialMode()==RoomRules.SpecialMode.CANNON;
        if(cannonMode && p.cannon==p.maxCannon-1)PvpAudio.notification(p,SE_CANNON_CHARGE);
        if (active) {
            if(cannonMode)p.cannon++;
            else p.cannon=0;
            p.maxMoney=p.b.t().getMaxMon(p.work_lv,StageLimit.isComboBanned(p.est.lim,C_M_MAX));
            int income=p.b.t().getMonInc(p.work_lv);
            if(!StageLimit.isComboBanned(p.est.lim,C_M_INC))income*=p.b.getInc(C_M_INC)/100+1;
            if(p.pvpRoulette!=null)income=income*p.pvpRoulette.workerPercent()/100;
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
        if(ebase.health<=0||ubase.health<=0){
            if(ebase.health<=0&&ubase.health<=0)return -1;
            return ebase.health>0?0:1;
        }
        int limit=timeLimitTicks();
        if(limit>0&&time>=limit){
            if(ebase.health==ubase.health)return -1;
            return ebase.health>ubase.health?0:1;
        }
        return -2;
    }
    public int timeLimitTicks(){return st.timeLimit<=0?0:st.timeLimit*60*TPS;}
    public int remainingTimeTicks(){int limit=timeLimitTicks();return limit<=0?-1:Math.max(0,limit-time);}
    public PvpStageBasis displayCopy() { return (PvpStageBasis)clone(); }
    /** Only call on a displayCopy; never on the canonical battle state. */
    public void advanceDisplay() {
        PvpTiming.halfStep(() -> {updateAnimation();pvpOther.canon.updateAnimation();});
    }
    public static double validateCastleHealthMultiplier(double value) {
        if(!Double.isFinite(value)||value<MIN_CASTLE_HEALTH_MULTIPLIER||value>MAX_CASTLE_HEALTH_MULTIPLIER)
            throw new IllegalArgumentException("城体力倍率は0.1〜1000の有限値にしてください");
        return value;
    }
    private static void applyCastleHealthMultiplier(StageBasis player,double multiplier) {
        multiplier=validateCastleHealthMultiplier(multiplier);
        long base=Math.max(1L,player.ownBase().maxH);
        long scaled=Math.max(1L,Math.round(base*multiplier));
        player.ownBase().maxH=scaled;player.ownBase().health=scaled;
    }
    public static RoomRules resolveRandomRules(RoomRules rules,long seed) {
        int background=rules.backgroundId==RoomRules.RANDOM_BACKGROUND?randomBackgroundId(seed):rules.backgroundId;
        RoomRules resolved=new RoomRules(rules.castleDistance,background,rules.musicId,rules.force60Fps,rules.specialMode,rules.debugMode,
                rules.hostTraitChoice,rules.guestTraitChoice,rules.hostTraitExclusions,rules.guestTraitExclusions,rules.timeLimitMinutes);
        validateRulesAssets(resolved);
        return resolved;
    }
    private static int randomBackgroundId(long seed) {
        List<Integer> ids=new ArrayList<>();
        for(Background bg:UserProfile.getBCData().bgs.getList())if(bg!=null&&bg.id!=null)ids.add(bg.id.id);
        Collections.sort(ids);
        if(ids.isEmpty())throw new IllegalArgumentException("ランダム背景に使える標準背景がありません");
        return ids.get(new Random(seed^0x4d5f0b17913a2c6dL).nextInt(ids.size()));
    }
    public static void validateRulesAssets(RoomRules rules) {
        if(rules.backgroundId==RoomRules.RANDOM_BACKGROUND) {
            randomBackgroundId(0);
        } else if(Identifier.get(new Identifier<>(Identifier.DEF,Background.class,rules.backgroundId))==null)
            throw new IllegalArgumentException("背景データがありません: "+rules.backgroundId);
        if(!PvpBattleMusic.isAllowed(rules.musicId))
            throw new IllegalArgumentException("対戦BGMとして許可されていません: "+rules.musicId);
        Music music=Identifier.get(new Identifier<>(Identifier.DEF,Music.class,rules.musicId));
        if(music==null||music.data==null)throw new IllegalArgumentException("BGMデータがありません: "+rules.musicId);
    }
    private static Stage arena(RoomRules rules) {
        validateRulesAssets(rules);
        ArenaMap map=new ArenaMap(); ArenaStage stage=new ArenaStage(map);
        stage.names.put("Online PvP");stage.len=rules.castleDistance+1600;stage.max=MAX_UNITS;
        stage.non_con=true;stage.drop=false;stage.health=60000;stage.timeLimit=rules.timeLimitMinutes;stage.data=new SCDef(0);
        stage.bg=new Identifier<>(Identifier.DEF,Background.class,rules.backgroundId);
        stage.mus0=stage.mus1=new Identifier<>(Identifier.DEF,Music.class,rules.musicId);
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
