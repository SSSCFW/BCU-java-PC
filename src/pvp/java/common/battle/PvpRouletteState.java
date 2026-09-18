package common.battle;

import common.CommonStatic;
import common.battle.entity.Entity;
import common.battle.entity.EUnit;
import common.util.BattleObj;
import common.util.CopRand;
import common.util.Data;
import java.util.*;

/**
 * Deterministic reconstruction of the Tobidasu 3DS versus roulette.
 * The native binary exposes four positive permanent-bonus levels
 * (Level 1/2/3/MAX); together with the base state this is five states.
 */
public final class PvpRouletteState extends BattleObj {
    public static final int MAX_GAUGE=1000, TEMP_TICKS=150, BABY_RUSH_TICKS=10*PvpStageBasis.TPS,
            AUTO_SPIN_TICKS=2*PvpStageBasis.TPS, RESULT_DISPLAY_TICKS=2*PvpStageBasis.TPS,
            KNOCKBACK_SHOCK_TICKS=18, ORIGINAL_MATCH_SECONDS=180;
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
    public int pendingResult=-1, resultDelayTicks, knockbackShockTicks;
    public long lastCastleHealth=-1;
    public boolean spinning;
    public int productionLevel, workerLevel, costLevel, attackLevel, hpLevel, moveLevel;
    public int babyRushTicks;

    public PvpRouletteState(CopRand random) {
        // FUN_0025502c initializes all 43 slots to 0x0e (empty), then inserts each
        // weighted source value at a random slot, preferring the nearest free slot
        // while walking backwards before falling forward.
        Arrays.fill(reel,14);
        for(int value:SOURCE_REEL) {
            int pick=index(random),slot=pick;
            if(reel[slot]!=14) {
                slot=-1;
                for(int j=pick;j>=0;j--)if(reel[j]==14){slot=j;break;}
                if(slot<0)for(int j=pick;j<reel.length;j++)if(reel[j]==14){slot=j;break;}
            }
            if(slot<0)throw new IllegalStateException("Roulette reel fill failed");
            reel[slot]=value;
        }
        // Native constructor then performs exactly 300 random swaps.
        for(int i=0;i<300;i++) {
            int a=index(random), b=index(random), v=reel[a]; reel[a]=reel[b]; reel[b]=v;
        }
        // Finally repair adjacent equal outcomes. The native pass covers pairs 0..41.
        for(int i=0;i<reel.length-1;i++) if(reel[i]==reel[i+1]) {
            int repeated=reel[i],swap=-1;
            for(int j=0;j<reel.length;j++) {
                int candidate=reel[j];
                boolean candidateIsolated=(j==0||reel[j-1]!=repeated)&&(j==reel.length-1||reel[j+1]!=repeated);
                boolean targetAccepts=(i==0||reel[i-1]!=candidate)&&(i==reel.length-1||reel[i+1]!=candidate);
                if(candidateIsolated&&targetAccepts){swap=j;break;}
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

    public void initializeCharge(StageBasis owner) {
        lastCastleHealth=Math.max(0L,owner.ownBase().health);
    }

    public void advance(PvpStageBasis world, StageBasis owner) {
        if(babyRushTicks>0) {
            babyRushTicks--;
            clearCooldowns(owner);
        }
        if(knockbackShockTicks>0)knockbackShockTicks--;

        // FUN_0023bcc0 charges from castle HP actually lost since the previous update.
        // FUN_001954d0 is called before the stored castle HP is replaced, therefore
        // this event uses the PRE-DAMAGE castle ratio.
        long currentCastle=Math.max(0L,owner.ownBase().health);
        if(lastCastleHealth<0)lastCastleHealth=currentCastle;
        long previousCastle=lastCastleHealth;
        long castleDamage=Math.max(0L,previousCastle-currentCastle);
        lastCastleHealth=currentCastle;

        if(pendingResult>=0) {
            if(resultDelayTicks>0)resultDelayTicks--;
            if(resultDelayTicks<=0) {
                int result=pendingResult;pendingResult=-1;
                apply(world,owner,result);
                lastLevel=stockState(result);
            }
            return;
        }

        if(spinning) {
            spinTicks++;
            reelIndex=(reelIndex+1)%reel.length;
            if(spinTicks>=AUTO_SPIN_TICKS)revealResult();
            return;
        }

        // Once the visible gauge reaches 1000 the native state machine leaves the
        // charge state. Keep observing castle HP, but do not bank charge for later.
        if(gauge>=MAX_GAUGE) {
            gauge=targetGauge=MAX_GAUGE;return;
        }

        if(castleDamage>0)
            addGauge(castleDamageGain(owner,previousCastle,castleDamage,world));

        // FUN_002559d4: passive charge every 60 native frames. At 30TPS that is
        // once per 30 logic ticks. Base=10, then castle-HP and remaining-time factors.
        if(++chargeClock>=PvpStageBasis.TPS) {
            chargeClock-=PvpStageBasis.TPS;
            addGauge((int)(10.0*castleHealthFactor(owner)*matchTimeFactor(world)));
        }

        // Native visible gauge follows target gauge by +50 per update.
        if(gauge<targetGauge)gauge=Math.min(targetGauge,gauge+50);
    }

    private void addGauge(int amount) {
        if(amount>0)targetGauge=Math.min(MAX_GAUGE,targetGauge+amount);
    }

    /**
     * FUN_0019dbc4: castle damage contribution.
     * base=floor(damage*3/1000), then castle comeback and remaining-time factors.
     */
    public static int castleDamageGain(StageBasis owner,long previousHealth,long damage,int battleTick) {
        return castleDamageGain(owner,previousHealth,damage,matchTimeFactor(battleTick));
    }
    private static int castleDamageGain(StageBasis owner,long previousHealth,long damage,PvpStageBasis world) {
        return castleDamageGain(owner,previousHealth,damage,matchTimeFactor(world));
    }
    private static int castleDamageGain(StageBasis owner,long previousHealth,long damage,double timeFactor) {
        if(damage<=0)return 0;
        long base=damage*3L/1000L;
        if(base<=0)return 0;
        double hp=castleHealthFactor(owner.ownBase().maxH,previousHealth);
        return Math.max(0,(int)(base*hp*timeFactor));
    }

    /**
     * FUN_002557d8: a deployed unit leaving battle also contributes to the local
     * roulette. Own-unit loss uses 10/10000 of its internal production price;
     * opponent loss uses 5/10000, capped at 250 before dynamic multipliers.
     * Position factor is 1.5x at own castle, 0.5x at center, 0.1x at enemy castle.
     */
    public void unitDefeated(PvpStageBasis world,StageBasis owner,int deadDirection,float position,int internalPrice) {
        if(spinning||gauge>=MAX_GAUGE||internalPrice<=0)return;
        boolean ownDeath=deadDirection==owner.ownDirection();
        int base=(int)Math.min(250L,(long)internalPrice*(ownDeath?10L:5L)/10000L);
        if(base<=0)return;
        double value=base*battlefieldFactor(owner,position)*castleHealthFactor(owner)*matchTimeFactor(world);
        addGauge(Math.max(0,(int)value));
    }

    public static double battlefieldFactor(StageBasis owner,float position) {
        float own=owner.ownBase().pos;
        StageBasis opponent=owner.playerFor(-owner.ownDirection());
        float enemy=opponent.ownBase().pos;
        double half=Math.abs(own-enemy)/2.0;
        if(half<=0.0)return 0.5;
        double dOwn=Math.abs(position-own),dEnemy=Math.abs(position-enemy);
        if(dOwn==dEnemy)return 0.5;
        if(dOwn<dEnemy) {
            double t=dOwn/half;
            return 1.5*(1.0-t)+0.5*t;
        }
        double t=dEnemy/half;
        return 0.1*(1.0-t)+0.5*t;
    }

    /**
     * FUN_001953d0. The original selectable limits are 180/300/420/600 seconds.
     * PvP now exposes its own 1-99 minute limit, so the same remaining-time
     * thresholds are applied proportionally to the configured match duration.
     * Unlimited matches have no final-quarter comeback window and stay at 1x.
     */
    public static double matchTimeFactor(PvpStageBasis world) {
        if(world==null||world.st.timeLimit<=0)return 1.0;
        return matchTimeFactor(world.time,world.st.timeLimit*60);
    }
    public static double matchTimeFactor(int battleTick) {
        return matchTimeFactor(battleTick,ORIGINAL_MATCH_SECONDS);
    }
    public static double matchTimeFactor(int battleTick,int matchSeconds) {
        if(matchSeconds<=0)return 1.0;
        int elapsed=Math.max(0,battleTick/PvpStageBasis.TPS);
        int remaining=Math.max(0,matchSeconds-elapsed);
        int percent=remaining*100/matchSeconds;
        if(percent>=50)return 1.0;
        if(percent>=25)return 2.0;
        return 5.0;
    }

    /**
     * Reverse engineered FUN_001954d0.
     * At full castle HP the charge multiplier is 1x, at 50% it is 2x,
     * and at 0% it is 5x, with linear interpolation on either side of 50%.
     */
    public static double castleHealthFactor(StageBasis owner) {
        return castleHealthFactor(owner.ownBase().maxH,owner.ownBase().health);
    }
    public static double castleHealthFactor(long maxHealth,long health) {
        long max=Math.max(1L,maxHealth);
        double ratio=Math.max(0.0,Math.min(1.0,(double)health/max));
        if(ratio>=0.5)return 3.0-2.0*ratio;
        return 5.0-6.0*ratio;
    }

    /**
     * SPECIAL starts a full roulette gauge. The reel cannot be manually skipped;
     * it resolves deterministically after roughly two seconds on both peers.
     */
    public boolean press(PvpStageBasis world, StageBasis owner) {
        if(spinning || pendingResult>=0 || gauge<MAX_GAUGE)return false;
        gauge=targetGauge=MAX_GAUGE;spinning=true;spinTicks=0;
        return true;
    }

    private void revealResult() {
        int result=reel[reelIndex];
        spinning=false;spinTicks=0;gauge=targetGauge=0;chargeClock=0;
        lastResult=result;pendingResult=result;resultDelayTicks=RESULT_DISPLAY_TICKS;
        lastLevel=previewLevel(result);
    }

    private int previewLevel(int effect) {
        switch(effect){
            case PRODUCTION_SHORTEN:case WORKER_UP:case COST_DOWN:
            case ATTACK_UP:case HP_UP:case MOVE_UP:
                return Math.min(4,stockState(effect)+1);
            default:return 0;
        }
    }

    public void forceResult(PvpStageBasis world,StageBasis owner,int result) {
        if(result<0||result>=NAMES.length)throw new IllegalArgumentException("Invalid roulette result");
        pendingResult=-1;resultDelayTicks=0;lastResult=result;apply(world,owner,result);lastLevel=stockState(result);
    }

    private void apply(PvpStageBasis world, StageBasis owner, int result) {
        StageBasis opponent=owner.playerFor(-owner.ownDirection());
        switch(result) {
            case KNOCKBACK:
                knockbackShockTicks=KNOCKBACK_SHOCK_TICKS;
                CommonStatic.setSE(Data.SE_WAVE);
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
                    for(Entity e:world.le)if(e instanceof EUnit&&e.dire==owner.ownDirection()&&!e.dead&&!((EUnit)e).isSpirit) {
                        e.maxH=Math.max(1,Math.round(e.maxH*ratio));
                        e.health=Math.min(e.maxH,Math.max(1,Math.round(e.health*ratio)));
                    }
                }
                break;
            case MOVE_UP: if(moveLevel<4)moveLevel++;break;
            case BABY_RUSH:
                // Tobidasu versus roulette: 10 seconds of zero production cooldown.
                // This is intentionally NOT the full God "Baby Rush" miracle, so it does
                // not alter worker level or money.
                babyRushTicks=BABY_RUSH_TICKS;
                clearCooldowns(owner);
                break;
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
