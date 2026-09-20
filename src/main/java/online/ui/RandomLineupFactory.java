package online.ui;

import common.battle.BasisLU;
import common.battle.BasisSet;
import common.pack.Identifier;
import common.pack.PackData;
import common.pack.UserProfile;
import common.util.unit.Form;
import common.util.unit.Unit;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Creates a fresh ten-unit PvP lineup every time a random choice enters a match. */
public final class RandomLineupFactory {
    public enum SortOrder {
        SHUFFLED("シャッフル順"), PRICE_ASC("価格の安い順"), PRICE_DESC("価格の高い順");
        private final String label;
        SortOrder(String label){this.label=label;}
        @Override public String toString(){return label;}
    }
    private RandomLineupFactory(){}

    public static BasisLU create(boolean vanilla) throws IOException {
        return create(vanilla,ThreadLocalRandom.current(),SortOrder.SHUFFLED);
    }

    public static BasisLU create(boolean vanilla,SortOrder order) throws IOException {
        return create(vanilla,ThreadLocalRandom.current(),order);
    }

    public static BasisLU create(boolean vanilla,Random random) throws IOException {
        return create(vanilla,random,SortOrder.SHUFFLED);
    }

    public static BasisLU create(boolean vanilla,Random random,SortOrder order) throws IOException {
        if(random==null)throw new IllegalArgumentException("random");
        if(order==null)throw new IllegalArgumentException("order");
        List<Unit> candidates=candidates(vanilla);
        if(candidates.size()<10)throw new IOException((vanilla?"バニラ":"全パック")+"の有効キャラが10体未満です");
        Collections.shuffle(candidates,random);
        BasisSet set=BasisSet.current();
        if(set==null)throw new IOException("編成データがありません");
        BasisLU result=new BasisLU(set);
        result.name=vanilla?"ランダム(バニラ)":"ランダム";
        BasisLU base=set.sele;
        if(base!=null&&base.nyc!=null)result.nyc=base.nyc.clone();
        List<Form> chosen=new ArrayList<>(10);
        for(int i=0;i<10;i++)chosen.add(bestForm(candidates.get(i)));
        Comparator<Form> price=Comparator.comparingInt(f->f.du.getPrice());
        if(order==SortOrder.PRICE_ASC)chosen.sort(price);
        else if(order==SortOrder.PRICE_DESC)chosen.sort(price.reversed());
        for(int i=0;i<10;i++){
            Form form=chosen.get(i);
            result.lu.fs[i/5][i%5]=form;
            result.lu.getLv(form);
        }
        result.lu.renew();
        return result;
    }

    public static List<Unit> candidates(boolean vanilla) {
        Map<String,Unit> unique=new TreeMap<>();
        Set<String> spiritTargets=spiritTargets();
        if(vanilla){
            for(Unit unit:UserProfile.getBCData().units.getList())add(unique,unit,true,spiritTargets);
        }else{
            for(PackData pack:UserProfile.getAllPacks())for(Unit unit:pack.units)add(unique,unit,false,spiritTargets);
        }
        return new ArrayList<>(unique.values());
    }

    private static void add(Map<String,Unit> out,Unit unit,boolean vanilla,Set<String> spiritTargets){
        if(unit==null||unit.id==null||unit.lv==null||unit.forms==null)return;
        if(vanilla&&!Identifier.DEF.equals(unit.id.pack))return;
        Form form=bestForm(unit);
        if(form==null)return;
        // Zero-cost support entities are not normal lineup choices. Keep ordinary
        // zero-cost units eligible, but exclude SPIRIT summon targets and the
        // Iron Wall cannon entity (the engine spawns default Unit 339 directly).
        if(form.du.getPrice()==0&&(spiritTargets.contains(key(unit.id))||isIronWallCannonUnit(unit.id)))return;
        out.put(key(unit.id),unit);
    }

    private static Set<String> spiritTargets(){
        Set<String> targets=new HashSet<>();
        for(Unit unit:UserProfile.getBCData().units.getList())collectSpiritTargets(targets,unit);
        for(PackData pack:UserProfile.getAllPacks())for(Unit unit:pack.units)collectSpiritTargets(targets,unit);
        return targets;
    }

    private static void collectSpiritTargets(Set<String> targets,Unit unit){
        if(unit==null||unit.forms==null)return;
        for(Form form:unit.forms){
            if(form==null||form.du==null||form.du.getProc()==null||!form.du.getProc().SPIRIT.exists()||form.du.getProc().SPIRIT.id==null)continue;
            targets.add(key(form.du.getProc().SPIRIT.id));
        }
    }

    private static boolean isIronWallCannonUnit(Identifier<?> id){
        return id!=null&&Identifier.DEF.equals(id.pack)&&id.id==339;
    }

    private static String key(Identifier<?> id){return id.pack+":"+id.id;}

    private static Form bestForm(Unit unit){
        if(unit==null||unit.forms==null)return null;
        for(int i=unit.forms.length-1;i>=0;i--){
            Form form=unit.forms[i];
            if(form!=null&&form.du!=null&&form.anim!=null)return form;
        }
        return null;
    }
}
