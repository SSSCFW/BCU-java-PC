package common.battle;

import common.battle.attack.*;
import common.battle.entity.*;
import common.battle.data.MaskUnit;
import common.pack.UserProfile;
import common.util.Data;
import common.util.unit.*;
import java.util.ArrayList;

/** Player offensive modifiers formerly implemented only in EEnemy.getDamage. */
public final class PvpDamage extends Data {
    private PvpDamage() {}
    public static int offense(EUnit target, AttackAb atk, int damage) {
        Entity source=atk.attacker;
        if(source instanceof EUnit && atk.model instanceof AtkModelUnit) {
            EUnit unit=(EUnit)source;
            StageBasis owner=unit.basis;
            if(source.status[P_CURSE][0]==0) {
                java.util.List<Trait> targetTraits=target.pvpAttributeTraits();
                ArrayList<Trait> shared=new ArrayList<>(atk.trait);shared.retainAll(targetTraits);
                boolean anti=Trait.isTargetTraited(atk.trait);
                for(Trait t:targetTraits)if(!t.id.pack.equals(IdentifierDefault.VALUE) && !shared.contains(t)
                        && (t.targetType && anti || t.targetForms.contains(((MaskUnit)unit.data).getPack())))shared.add(t);
                if(!shared.isEmpty()) {
                    if((atk.abi&AB_GOOD)!=0)damage=(int)(damage*EUnit.OrbHandler.getOrbGood(atk,shared,owner.b.t()));
                    if((atk.abi&AB_MASSIVE)!=0)damage=(int)(damage*EUnit.OrbHandler.getOrbMassive(atk,shared,owner.b.t()));
                    if((atk.abi&AB_MASSIVES)!=0)damage=(int)(damage*owner.b.t().getMASSIVESATK(shared));
                }
            }
            Unit data=((MaskUnit)unit.data).getPack().unit;
            if(has(target,TRAIT_WITCH)&&(atk.abi&AB_WKILL)!=0)damage=(int)(damage*owner.b.t().getWKAtk(owner.b.getInc(C_WKILL,data)));
            if(has(target,TRAIT_EVA)&&(atk.abi&AB_EKILL)!=0)damage=(int)(damage*owner.b.t().getEKAtk(owner.b.getInc(C_EKILL,data)));
            if(has(target,TRAIT_BARON)) {
                if((atk.abi&AB_BAKILL)!=0)damage=(int)(damage*1.6);
                if(unit.coloGrade!=-1)damage=damage*ORB_BARON_DAMAGE[unit.coloGrade]/100;
            }
        }
        if(has(target,TRAIT_BEAST)&&atk.getProc().BSTHUNT.active==1)damage=(int)(damage*2.5);
        if(has(target,TRAIT_SAGE)&&(atk.abi&AB_SKILL)!=0)damage=(int)(damage*SUPER_SAGE_HUNTER_ATTACK);
        if(has(target,TRAIT_VILLAIN)&&(atk.abi&AB_VKILL)!=0)damage=(int)(damage*VILLAIN_KILLER_ATTACK);
        return damage;
    }
    private static boolean has(Entity e,int trait){
        if(e instanceof EUnit&&e.basis.isPvp())return ((EUnit)e).pvpAttributeTraits().contains(UserProfile.getBCData().traits.get(trait));
        return e.traits.contains(UserProfile.getBCData().traits.get(trait));
    }
    private static final class IdentifierDefault {static final String VALUE="000000";}
}
