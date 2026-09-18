package online.tests;

import common.battle.BasisLU;
import common.pack.Identifier;
import common.pack.UserProfile;
import common.util.unit.Form;
import common.util.unit.Unit;
import online.ui.RandomLineupFactory;

import java.util.*;

public final class RandomLineupTests {
    public static void run() throws Exception {
        Fixture.init();
        for(int i=0;i<12;i++){
            Unit u=new Unit(new Identifier<>(Identifier.DEF,Unit.class,100+i));
            u.rarity=0;u.max=50;u.lv=common.CommonStatic.getBCAssets().defLv;
            common.battle.data.CustomUnit d=new common.battle.data.CustomUnit();
            d.hp=1000;d.death=null;d.price=10;d.resp=30;d.atks[0].atk=100;d.atks[0].pre=1;
            u.forms=new Form[]{new Form(u,0,"vanilla-"+i,Fixture.animation(Identifier.DEF,"random-"+i),d)};
            UserProfile.getBCData().units.set(100+i,u);
        }
        Unit custom=Fixture.unit("random_custom_pack",1000);
        Check.that(RandomLineupFactory.candidates(false).stream().anyMatch(u->u==custom),"all-pack random includes user-pack units");
        Check.that(RandomLineupFactory.candidates(true).stream().allMatch(u->Identifier.DEF.equals(u.id.pack)),"vanilla random excludes user packs");

        BasisLU vanilla=RandomLineupFactory.create(true,new Random(42));
        Set<String> ids=new HashSet<>();
        int count=0;
        for(Form[] row:vanilla.lu.fs)for(Form form:row){
            Check.that(form!=null,"random lineup fills every slot");
            Check.equal(Identifier.DEF,form.unit.id.pack,"vanilla random lineup contains only default-pack units");
            ids.add(form.unit.id.pack+":"+form.unit.id.id);count++;
        }
        Check.equal(10,count,"random lineup contains ten units");
        Check.equal(10,ids.size(),"random lineup does not duplicate units");
    }
    public static void main(String[] args)throws Exception{run();System.out.println("Random lineup tests passed");}
}
