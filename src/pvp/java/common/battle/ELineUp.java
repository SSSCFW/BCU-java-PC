package common.battle;

import common.CommonStatic;
import common.util.BattleObj;
import common.util.stage.Limit;
import common.util.stage.StageLimit;
import common.util.unit.Form;

import java.util.Arrays;

public class ELineUp extends BattleObj {

	public final int[][] price, basePrice, cool, maxC, tick, cdDownOrb, priceDownOrb;
    /** Next rerolled character's cooldown; active cooldown keeps the deployed character's maxC until it reaches zero. */
    public final int[][] pvpNextMaxC;
    public final boolean[][] pvpNextMaxCPending;
	private final StageBasis b;

	protected ELineUp(LineUp lu, StageBasis sb) {
		b = sb;
		price = new int[2][5];
		basePrice = new int[2][5];
		cool = new int[2][5];
		maxC = new int[2][5];
		tick = new int[2][5];
		cdDownOrb = new int[2][5];
		priceDownOrb = new int[2][5];
        pvpNextMaxC = new int[2][5];
        pvpNextMaxCPending = new boolean[2][5];
		Limit lim = sb.est.lim;
		for (int i = 0; i < 2; i++)
			for (int j = 0; j < 5; j++) {
				Form form = lu.fs[i][j];
				if (form == null) {
					price[i][j] = -1;
					continue;
				}
				if (lim != null && ((lim.line == 1 && i == 1) || lim.unusable(lu.efs[i][j].du, sb.st.getCont().price)))
					price[i][j] = -1;
				else
					price[i][j] = 100 * (sb.globalCost() > -1 ? sb.globalCost() : (int) (lu.efs[i][j].getPrice(sb.st.getCont().price)));
				if (!StageLimit.isComboBanned(lim, C_DISCOUNT))
					price[i][j] -= price[i][j] * b.b.getInc(C_DISCOUNT, form.du.getPack().unit) / 100;
				maxC[i][j] = sb.globalCdLimit() > 0
						? sb.b.t().getFinResGlobal(sb.globalCdLimit(), StageLimit.isComboBanned(sb.est.lim, C_RESP) ? 0 : sb.b.getInc(C_RESP, lu.efs[i][j].du.getPack().unit))
						: sb.b.t().getFinRes(lu.efs[i][j].du.getRespawn(), StageLimit.isComboBanned(sb.est.lim, C_RESP) ? 0 : sb.b.getInc(C_RESP, lu.efs[i][j].du.getPack().unit));
				if (lim != null && lim.stageLimit != null) {
					if (price[i][j] != -1)
						price[i][j] = price[i][j] * lim.stageLimit.costMultiplier[form.unit.rarity] / 100;
					maxC[i][j] = maxC[i][j] * lim.stageLimit.cooldownMultiplier[form.unit.rarity] / 100;
				}
				int[][] orbs = lu.efs[i][j].getLevel().getOrbs();
				boolean hasEveryOther = false;
				if (orbs != null) {
					for (int[] orb : orbs) {
						if (orb.length != ORB_INTS)
							continue;
						int orbId = orb[0];
						hasEveryOther |= Arrays.stream(ORB_EVERY_OTHER).anyMatch(v -> v == orbId);
						if (orbId == ORB_COOLDOWN)
							cdDownOrb[i][j] = ORB_COOLDOWN_MULT[orb[2]];
						else if (orbId == ORB_COST_DOWN)
							priceDownOrb[i][j] = ORB_COST_DOWN_MULT[orb[2]];
					}
				}
				if (!hasEveryOther)
					tick[i][j] = -1;
				// Preserve the fully calculated pre-roulette production cost.
				// PvP COST_DOWN mutates price[][] in place, but defeat rewards must
				// continue to use this original per-battle cost.
				basePrice[i][j] = price[i][j];
			}
	}

    /** Recalculate a rerolled slot without replacing the cooldown already started by the deployed character. */
    protected void pvpReplace(int i,int j,common.util.unit.EForm f) {
        int activeCool=cool[i][j],activeMax=maxC[i][j];
        price[i][j]=-1;basePrice[i][j]=-1;tick[i][j]=0;cdDownOrb[i][j]=0;priceDownOrb[i][j]=0;
        if(f==null){
            pvpNextMaxC[i][j]=0;pvpNextMaxCPending[i][j]=false;
            if(activeCool<=0)maxC[i][j]=0;
            return;
        }
        Form form=f.du.getPack();
        Limit lim=b.est.lim;
        if(lim!=null&&((lim.line==1&&i==1)||lim.unusable(f.du,b.st.getCont().price)))price[i][j]=-1;
        else price[i][j]=100*(b.globalCost()>-1?b.globalCost():(int)f.getPrice(b.st.getCont().price));
        if(!StageLimit.isComboBanned(lim,C_DISCOUNT))
            price[i][j]-=price[i][j]*b.b.getInc(C_DISCOUNT,form.du.getPack().unit)/100;

        int nextMax=b.globalCdLimit()>0
                ?b.b.t().getFinResGlobal(b.globalCdLimit(),StageLimit.isComboBanned(b.est.lim,C_RESP)?0:b.b.getInc(C_RESP,f.du.getPack().unit))
                :b.b.t().getFinRes(f.du.getRespawn(),StageLimit.isComboBanned(b.est.lim,C_RESP)?0:b.b.getInc(C_RESP,f.du.getPack().unit));
        if(lim!=null&&lim.stageLimit!=null){
            if(price[i][j]!=-1)price[i][j]=price[i][j]*lim.stageLimit.costMultiplier[form.unit.rarity]/100;
            nextMax=nextMax*lim.stageLimit.cooldownMultiplier[form.unit.rarity]/100;
        }

        int[][] orbs=f.getLevel().getOrbs();boolean hasEveryOther=false;
        if(orbs!=null)for(int[] orb:orbs){
            if(orb.length!=ORB_INTS)continue;
            int orbId=orb[0];hasEveryOther|=Arrays.stream(ORB_EVERY_OTHER).anyMatch(v->v==orbId);
            if(orbId==ORB_COOLDOWN)cdDownOrb[i][j]=ORB_COOLDOWN_MULT[orb[2]];
            else if(orbId==ORB_COST_DOWN)priceDownOrb[i][j]=ORB_COST_DOWN_MULT[orb[2]];
        }
        if(!hasEveryOther)tick[i][j]=-1;
        basePrice[i][j]=price[i][j];
        if(b.pvpRoulette!=null&&b.pvpRoulette.costLevel>0&&price[i][j]>0)
            for(int level=0;level<b.pvpRoulette.costLevel;level++)price[i][j]=Math.max(1,price[i][j]/2);

        pvpNextMaxC[i][j]=nextMax;
        pvpNextMaxCPending[i][j]=activeCool>0;
        if(activeCool>0){
            cool[i][j]=activeCool;
            maxC[i][j]=activeMax;
        }else{
            maxC[i][j]=nextMax;
            pvpNextMaxCPending[i][j]=false;
        }
    }

    private void applyPendingPvpMaxC(int i,int j){
        if(!pvpNextMaxCPending[i][j])return;
        maxC[i][j]=pvpNextMaxC[i][j];
        pvpNextMaxCPending[i][j]=false;
    }

	/**
	 * reset cooldown of a unit
	 */
	protected void get(int i, int j) {
        if(cool[i][j]<=0)applyPendingPvpMaxC(i,j);
		cool[i][j] = maxC[i][j];
		if (b.pvpRoulette != null && b.pvpRoulette.productionLevel > 0)
			cool[i][j] = Math.max(0, cool[i][j] / b.pvpRoulette.productionDivisor());
		if (cdDownOrb[i][j] > 0 && tick[i][j] == 0)
			cool[i][j] -= cool[i][j] * cdDownOrb[i][j] / 100;
		b.cdDelayVisual[i][j] = StageBasis.DELAY_BASE.clone();
	}

	protected void delay(int i, int j, int[] delay) {
		if (cool[i][j] == 0)
			return;

		int inc = b.getDelayStrength(cool[i][j], maxC[i][j], delay);
		if (inc > 0) {
			b.cdDelayVisual[i][j][0] = Math.max(b.cdDelayVisual[i][j][0], cool[i][j]);
		} else {
			b.cdDelayVisual[i][j][2] += inc;
		}
		cool[i][j] += inc;
		if (cool[i][j] > maxC[i][j])
			cool[i][j] = maxC[i][j];
		if (inc < 0) {
			if (cool[i][j] <= 0) {
				cool[i][j] = 0;
                applyPendingPvpMaxC(i,j);
				PvpAudio.notification(b, SE_SPEND_REF);
				b.frameOffCd[i][j] = b.time;
			} else {
				b.cdDelayVisual[i][j][3] = 10;
			}
			PvpAudio.notification(b, SE_DELAY_COOLDOWN);
		} else {
			b.cdDelayVisual[i][j][1] = 10;
			PvpAudio.notification(b, SE_DELAY_COOLDOWN);
		}
	}

	/**
	 * count down the cooldown
	 */
	protected void update() {
		for (int i = 0; i < 2; i++)
			for (int j = 0; j < 5; j++) {
				if (cool[i][j] > 0) {
					cool[i][j]--;

//					if (cool[i][j] == 30)
//						delay(i, j, -30, 0);

					if (cool[i][j] == 0) {
                        applyPendingPvpMaxC(i,j);
						PvpAudio.notification(b, SE_SPEND_REF);
						b.frameOffCd[i][j] = b.time;
					}
				}
				if (b.cdDelayVisual[i][j][1] > 0 && --b.cdDelayVisual[i][j][1] == 0) {
					b.cdDelayVisual[i][j][0] = 0;
				}
				if (b.cdDelayVisual[i][j][3] > 0 && --b.cdDelayVisual[i][j][3] == 0) {
					b.cdDelayVisual[i][j][2] = 0;
				}
			}
	}

}
