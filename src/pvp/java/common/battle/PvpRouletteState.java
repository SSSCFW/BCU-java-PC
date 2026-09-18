package common.battle;

import common.battle.entity.Entity;
import common.battle.entity.EUnit;
import common.util.BattleObj;
import common.util.CopRand;
import java.util.*;

/**
 * Deterministic reconstruction of the Tobidasu 3DS versus roulette.
 * The native binary exposes four positive permanent-bonus levels
 * (Level 1/2/3/MAX); together with the base state this is five states.
 */
public final class PvpRouletteState extends BattleObj {
    public static final int MAX_GAUGE=1000, TEMP_TICKS=150, BABY_RUSH_TICKS=14*PvpStageBasis.TPS;
    public static final int KNOCKBACK=0, HEAL=1, PRODUCTION_RECOVERY=2, CANNON=3,
            PRODUCTION_SHORTEN=4, WORKER_UP=5, COST_DOWN=6, MONEY_MAX=7,
            SLOW=8, STOP=9, ATTACK_UP=10, HP_UP=11, MOVE_UP=12, BABY_RUSH=13;
    public static final String[] NAMES={
            "ふっとばし","いやし","生産回復","にゃんこ砲","生産短縮","働きネコ仕事効率UP",
            "コストダウン","お金MAX","スロウ","ストップ","攻撃力UP","体力UP","移動UP","ぷちベビーラッシュ"
    };
    /** Exact 43-slot source distribution recovered from the 3DS executable. */
    public static final int[] SOURCE_REEL={
            0,0,0,0,0, 1,1, 2,2,2, 3,3,3,3, 4,4,4, 5,5, 6,6, 7,7,
            8,8,8, 9,9,9, 10,10,10,10, 11,11,11,11, 12,12,12,12, 13,13
    };
    private static final double[] PERMANENT={1.0,1.5,2.5,4.5,8.0};
    private static final int[] WORKER={100,150,200,300,500};

    private final int[] reel=new int[SOURCE_REEL.length];
    /** Native roulette keeps a target gauge and lets the visible gauge chase it by 50. */
    public int gauge, targetGauge, chargeClock, reelIndex, spinTicks, lastResult=-1, lastLevel;
    public boolean spinning;
    public int productionLevel, workerLevel, costLevel, attackLevel, hpLevel, moveLevel;
    public int babyRushTicks;

    public PvpRouletteState(CopRand random) {
        System.arraycopy(SOURCE_REEL,0,reel,0,reel.length);
        // Native constructor performs 300 random swaps.
        for(int i=0;i<300;i++) {
            int a=index(random), b=index(random), v=reel[a]; reel[a]=reel[b]; reel[b]=v;
        }
        // Native code repairs adjacent equal outcomes after shuffling.
        for(int i=1;i<reel.length;i++) if(reel[i]==reel[i-1]) {
            int swap=-1;
            for(int d=1;d<reel.length;d++) {
                int j=(i+d)%reel.length;
                if(reel[j]!=reel[i] && (j+1>=reel.length || reel[j+1]!=reel[i])
                        && (i+1>=reel.length || reel[i+1]!=reel[j])) {swap=j;break;}
            }
            if(swap>=0){int v=reel[i];reel[i]=reel[swap];reel[swap]=v;}
        }
    }
    private static int index(CopRand random){return Math.min(SOURCE_REEL.length-1,(int)(random.nextFloat()*SOURCE_REEL.length));}
    public int[] reelSnapshot(){return reel.clone();}
    public int currentResult(){return reel[reelIndex];}
    public static double multiplier(int level){return PERMANENT[Math.max(0,Math.min(4,level))];}
    public int workerPercent(){return WORKER[Math.max(0,Math.min(4,workerLevel))];}
    public int productionDivisor(){return 1<<Math.max(0,Math.min(4,productionLevel));}
    public double attackMultiplier(){return multiplier(attackLevel);}
    public double hpMultiplier(){return multiplier(hpLevel);}
    public double moveMultiplier(){return multiplier(moveLevel);}
    public int stockState(int effect) {
        switch(effect){
            case PRODUCTION_SHORTEN:return productionLevel;
            case WORKER_UP:return workerLevel;
            case COST_DOWN:return costLevel;
            case ATTACK_UP:return attackLevel;
            case HP_UP:return hpLevel;
            case MOVE_UP:return moveLevel;
            default:return 0;
        }
    }

    public void advance(PvpStageBasis world, StageBasis owner) {
        if(babyRushTicks>0) {
            babyRushTicks--;
            clearCooldowns(owner);
        }
        if(spinning) {
            spinTicks++;
            reelIndex=(reelIndex+1)%reel.length;
            return;
        }

        // The native state machine tests the displayed gauge before the catch-up step.
        if(gauge>=MAX_GAUGE) {
            gauge=targetGauge=MAX_GAUGE;spinning=true;spinTicks=0;return;
        }

        // 3DS accumulates roulette charge once per 60 native frames (one second).
        // BCU PvP logic is fixed at 30 TPS, so one native charge step is 30 logic ticks.
        if(++chargeClock>=PvpStageBasis.TPS) {
            chargeClock-=PvpStageBasis.TPS;
            int add=(int)(10.0 * castleHealthFactor(owner));
            targetGauge=Math.min(MAX_GAUGE,targetGauge+Math.max(0,add));
        }

        if(gauge<targetGauge)gauge=Math.min(targetGauge,gauge+50);
    }

    /**
     * Reverse engineered FUN_001954d0.
     * At full castle HP the charge multiplier is 1x, at 50% it is 2x,
     * and at 0% it is 5x, with linear interpolation on either side of 50%.
     */
    public static double castleHealthFactor(StageBasis owner) {
        long max=Math.max(1L,owner.ownBase().maxH);
        double ratio=Math.max(0.0,Math.min(1.0,(double)owner.ownBase().health/max));
        if(ratio>=0.5)return 3.0-2.0*ratio; // 50% -> 2.0, 100% -> 1.0
        return 5.0-6.0*ratio;               // 0% -> 5.0, 50% -> 2.0
    }

    /** The 3DS reel auto-starts at full gauge; the special button stops it after the intro frames. */
    public boolean press(PvpStageBasis world, StageBasis owner) {
        if(!spinning || spinTicks<10)return false;
        int result=reel[reelIndex];
        spinning=false;spinTicks=0;gauge=targetGauge=0;chargeClock=0;lastResult=result;
        apply(world,owner,result);
        lastLevel=stockState(result);
        return true;
    }

    public void forceResult(PvpStageBasis world,StageBasis owner,int result) {
        if(result<0||result>=NAMES.length)throw new IllegalArgumentException("Invalid roulette result");
        lastResult=result;apply(world,owner,result);lastLevel=stockState(result);
    }

    private void apply(PvpStageBasis world, StageBasis owner, int result) {
        StageBasis opponent=owner.playerFor(-owner.ownDirection());
        switch(result) {
            case KNOCKBACK:
                for(Entity e:new ArrayList<>(world.le)) if(e instanceof EUnit && e.dire==opponent.ownDirection()&&!e.dead&&!((EUnit)e).isSpirit)
                    e.interrupt(INT_KB,KB_DIS[INT_KB]);
                break;
            case HEAL:
                for(Entity e:world.le) if(e instanceof EUnit&&e.dire==owner.ownDirection()&&!e.dead) {
                    e.health=Math.min(e.maxH,e.health+e.maxH/2);
                    e.anim.getEff(common.util.Data.HEAL);
                }
                break;
            case PRODUCTION_RECOVERY: clearCooldowns(owner); break;
            case CANNON:
                owner.cannon=owner.maxCannon;owner.act_can();break;
            case PRODUCTION_SHORTEN:
                if(productionLevel<4) {
                    productionLevel++;
                    for(int i=0;i<2;i++)for(int j=0;j<5;j++)owner.elu.cool[i][j]/=2;
                }
                break;
            case WORKER_UP: if(workerLevel<4)workerLevel++;break;
            case COST_DOWN:
                if(costLevel<4) {
                    costLevel++;
                    for(int i=0;i<2;i++)for(int j=0;j<5;j++)if(owner.elu.price[i][j]>0)
                        owner.elu.price[i][j]=Math.max(1,owner.elu.price[i][j]/2);
                }
                break;
            case MONEY_MAX: owner.money=owner.maxMoney;break;
            case SLOW: timed(opponent,P_SLOW);break;
            case STOP: timed(opponent,P_STOP);break;
            case ATTACK_UP: if(attackLevel<4)attackLevel++;break;
            case HP_UP:
                if(hpLevel<4) {
                    double old=hpMultiplier();hpLevel++;double now=hpMultiplier(),ratio=now/old;
                    // Native FUN_00233ce0 rewrites max HP for deployed units but leaves current HP unchanged.
                    for(Entity e:world.le)if(e instanceof EUnit&&e.dire==owner.ownDirection()&&!((EUnit)e).isSpirit)
                        e.maxH=Math.max(1,Math.round(e.maxH*ratio));
                }
                break;
            case MOVE_UP: if(moveLevel<4)moveLevel++;break;
            case BABY_RUSH: babyRushTicks=BABY_RUSH_TICKS;clearCooldowns(owner);break;
            default: throw new IllegalArgumentException("Unknown roulette result");
        }
    }
    private static void clearCooldowns(StageBasis owner){
        for(int i=0;i<2;i++)Arrays.fill(owner.elu.cool[i],0);
    }
    private static void timed(StageBasis player,int proc){
        for(Entity e:player.world().le)if(e instanceof EUnit&&e.dire==player.ownDirection()&&!e.dead&&!((EUnit)e).isSpirit) {
            e.status[proc][0]=Math.max(e.status[proc][0],TEMP_TICKS);
            e.anim.getEff(proc);
        }
    }
}
