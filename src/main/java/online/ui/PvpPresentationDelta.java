package online.ui;

import common.battle.PvpRouletteState;
import common.battle.PvpStageBasis;
import common.battle.StageBasis;
import common.battle.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Small immutable presentation update captured from the canonical PvP world.
 * It intentionally contains primitives only: simulation objects are never shared
 * with Swing/JOGL. Full displayCopy snapshots still carry structural changes.
 */
public final class PvpPresentationDelta {
    public final int tick;
    private final long[] ids;
    private final float[] positions;
    private final long[] health;
    private final int[] shield;
    private final PlayerDelta left,right;

    private PvpPresentationDelta(int tick,long[] ids,float[] positions,long[] health,int[] shield,
                                 PlayerDelta left,PlayerDelta right){
        this.tick=tick;this.ids=ids;this.positions=positions;this.health=health;this.shield=shield;
        this.left=left;this.right=right;
    }

    public static PvpPresentationDelta capture(PvpStageBasis world){
        int n=world.le.size();
        long[] ids=new long[n],health=new long[n];
        float[] positions=new float[n];
        int[] shield=new int[n];
        for(int i=0;i<n;i++){
            Entity e=world.le.get(i);
            ids[i]=e.pvpEntityId;positions[i]=e.pos;health[i]=e.health;shield[i]=e.currentShield;
        }
        return new PvpPresentationDelta(world.time,ids,positions,health,shield,
                new PlayerDelta(world.left()),new PlayerDelta(world.right()));
    }

    public void apply(PvpStageBasis display,Map<Long,Entity> entityIndex){
        if(display==null)return;
        applyPlayer(display.left(),left);applyPlayer(display.right(),right);
        display.time=tick;display.left().time=tick;
        for(int i=0;i<ids.length;i++){
            Entity e=entityIndex.get(ids[i]);if(e==null)continue;
            e.pos=positions[i];e.health=health[i];e.currentShield=shield[i];
        }
    }

    public static Map<Long,Entity> index(PvpStageBasis display){
        HashMap<Long,Entity> out=new HashMap<>(Math.max(16,display.le.size()*2));
        for(Entity e:display.le)out.put(e.pvpEntityId,e);
        return out;
    }

    private static void applyPlayer(StageBasis target,PlayerDelta source){
        target.money=source.money;target.maxMoney=source.maxMoney;
        target.cannon=source.cannon;target.maxCannon=source.maxCannon;
        target.work_lv=source.workLevel;target.unitRespawnTime=source.unitRespawnTime;
        target.ownBase().health=source.castleHealth;
        for(int row=0;row<2;row++)for(int col=0;col<5;col++){
            target.elu.cool[row][col]=source.cool[row][col];
            System.arraycopy(source.cdDelayVisual[row][col],0,target.cdDelayVisual[row][col],0,
                    Math.min(source.cdDelayVisual[row][col].length,target.cdDelayVisual[row][col].length));
        }
        if(target.pvpRoulette!=null&&source.roulette!=null)source.roulette.apply(target.pvpRoulette);
    }

    private static final class PlayerDelta {
        final int money,maxMoney,cannon,maxCannon,workLevel,unitRespawnTime;
        final long castleHealth;
        final int[][] cool=new int[2][5];
        final int[][][] cdDelayVisual=new int[2][5][];
        final RouletteDelta roulette;
        PlayerDelta(StageBasis s){
            money=s.money;maxMoney=s.maxMoney;cannon=s.cannon;maxCannon=s.maxCannon;
            workLevel=s.work_lv;unitRespawnTime=s.unitRespawnTime;castleHealth=s.ownBase().health;
            for(int row=0;row<2;row++)for(int col=0;col<5;col++){
                cool[row][col]=s.elu.cool[row][col];
                cdDelayVisual[row][col]=s.cdDelayVisual[row][col].clone();
            }
            roulette=s.pvpRoulette==null?null:new RouletteDelta(s.pvpRoulette);
        }
    }

    private static final class RouletteDelta {
        final int gauge,targetGauge,chargeClock,reelIndex,spinTicks,lastResult,lastLevel;
        final int pendingResult,resultDelayTicks,repeatDelayTicks;
        final long lastCastleHealth;
        final boolean spinning,castleDamageFastSpinReady,castleDamageBoostedSpin,lastResultCastleBoosted;
        final int productionLevel,workerLevel,costLevel,attackLevel,hpLevel,moveLevel;
        final int babyRushTicks,healSpawnBoostTicks,spinDurationTicks;
        RouletteDelta(PvpRouletteState r){
            gauge=r.gauge;targetGauge=r.targetGauge;chargeClock=r.chargeClock;reelIndex=r.reelIndex;
            spinTicks=r.spinTicks;lastResult=r.lastResult;lastLevel=r.lastLevel;pendingResult=r.pendingResult;
            resultDelayTicks=r.resultDelayTicks;repeatDelayTicks=r.repeatDelayTicks;lastCastleHealth=r.lastCastleHealth;
            spinning=r.spinning;castleDamageFastSpinReady=r.castleDamageFastSpinReady;
            castleDamageBoostedSpin=r.castleDamageBoostedSpin;lastResultCastleBoosted=r.lastResultCastleBoosted;
            productionLevel=r.productionLevel;workerLevel=r.workerLevel;costLevel=r.costLevel;
            attackLevel=r.attackLevel;hpLevel=r.hpLevel;moveLevel=r.moveLevel;
            babyRushTicks=r.babyRushTicks;healSpawnBoostTicks=r.healSpawnBoostTicks;spinDurationTicks=r.spinDurationTicks;
        }
        void apply(PvpRouletteState r){
            r.gauge=gauge;r.targetGauge=targetGauge;r.chargeClock=chargeClock;r.reelIndex=reelIndex;
            r.spinTicks=spinTicks;r.lastResult=lastResult;r.lastLevel=lastLevel;r.pendingResult=pendingResult;
            r.resultDelayTicks=resultDelayTicks;r.repeatDelayTicks=repeatDelayTicks;r.lastCastleHealth=lastCastleHealth;
            r.spinning=spinning;r.castleDamageFastSpinReady=castleDamageFastSpinReady;
            r.castleDamageBoostedSpin=castleDamageBoostedSpin;r.lastResultCastleBoosted=lastResultCastleBoosted;
            r.productionLevel=productionLevel;r.workerLevel=workerLevel;r.costLevel=costLevel;
            r.attackLevel=attackLevel;r.hpLevel=hpLevel;r.moveLevel=moveLevel;
            r.babyRushTicks=babyRushTicks;r.healSpawnBoostTicks=healSpawnBoostTicks;r.spinDurationTicks=spinDurationTicks;
        }
    }
}
