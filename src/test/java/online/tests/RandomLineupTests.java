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
            d.hp=1000;d.death=null;d.price=10+i*7;d.resp=30;d.atks[0].atk=100;d.atks[0].pre=1;
            u.forms=new Form[]{new Form(u,0,"vanilla-"+i,Fixture.animation(Identifier.DEF,"random-"+i),d)};
            UserProfile.getBCData().units.set(100+i,u);
        }
        Unit custom=Fixture.unit("random_custom_pack",1000);
        Check.that(RandomLineupFactory.candidates(false).stream().anyMatch(u->u==custom),"all-pack random includes user-pack units");
        Check.that(RandomLineupFactory.candidates(true).stream().allMatch(u->Identifier.DEF.equals(u.id.pack)),"vanilla random excludes user packs");

        Unit spirit=new Unit(new Identifier<>(Identifier.DEF,Unit.class,212));
        spirit.rarity=0;spirit.max=50;spirit.lv=common.CommonStatic.getBCAssets().defLv;
        common.battle.data.CustomUnit spiritData=new common.battle.data.CustomUnit();
        spiritData.hp=1000;spiritData.death=null;spiritData.price=0;spiritData.resp=30;spiritData.atks[0].atk=100;spiritData.atks[0].pre=1;
        spirit.forms=new Form[]{new Form(spirit,0,"zero-spirit",Fixture.animation(Identifier.DEF,"zero-spirit"),spiritData)};
        UserProfile.getBCData().units.set(212,spirit);

        Unit summoner=new Unit(new Identifier<>(Identifier.DEF,Unit.class,213));
        summoner.rarity=0;summoner.max=50;summoner.lv=common.CommonStatic.getBCAssets().defLv;
        common.battle.data.CustomUnit summonerData=new common.battle.data.CustomUnit();
        summonerData.hp=1000;summonerData.death=null;summonerData.price=100;summonerData.resp=30;summonerData.atks[0].atk=100;summonerData.atks[0].pre=1;
        summonerData.rep.proc.SPIRIT.id=spirit.id;
        summoner.forms=new Form[]{new Form(summoner,0,"summoner",Fixture.animation(Identifier.DEF,"summoner"),summonerData)};
        UserProfile.getBCData().units.set(213,summoner);

        Unit freeNormal=new Unit(new Identifier<>(Identifier.DEF,Unit.class,214));
        freeNormal.rarity=0;freeNormal.max=50;freeNormal.lv=common.CommonStatic.getBCAssets().defLv;
        common.battle.data.CustomUnit freeData=new common.battle.data.CustomUnit();
        freeData.hp=1000;freeData.death=null;freeData.price=0;freeData.resp=30;freeData.atks[0].atk=100;freeData.atks[0].pre=1;
        freeNormal.forms=new Form[]{new Form(freeNormal,0,"free-normal",Fixture.animation(Identifier.DEF,"free-normal"),freeData)};
        UserProfile.getBCData().units.set(214,freeNormal);

        Unit ironWall=new Unit(new Identifier<>(Identifier.DEF,Unit.class,339));
        ironWall.rarity=0;ironWall.max=50;ironWall.lv=common.CommonStatic.getBCAssets().defLv;
        common.battle.data.CustomUnit wallData=new common.battle.data.CustomUnit();
        wallData.hp=1000;wallData.death=null;wallData.price=0;wallData.resp=30;wallData.atks[0].atk=100;wallData.atks[0].pre=1;
        ironWall.forms=new Form[]{new Form(ironWall,0,"iron-wall-cannon",Fixture.animation(Identifier.DEF,"iron-wall-cannon"),wallData)};
        UserProfile.getBCData().units.set(339,ironWall);

        List<Unit> vanillaCandidates=RandomLineupFactory.candidates(true);
        Check.that(vanillaCandidates.stream().noneMatch(u->u==spirit),"zero-cost spirit target is excluded from vanilla random");
        Check.that(vanillaCandidates.stream().anyMatch(u->u==summoner),"spirit summoner remains eligible");
        Check.that(vanillaCandidates.stream().anyMatch(u->u==freeNormal),"ordinary zero-cost unit remains eligible");
        Check.that(vanillaCandidates.stream().noneMatch(u->u==ironWall),"zero-cost Iron Wall cannon unit is excluded from vanilla random");
        List<Unit> allCandidates=RandomLineupFactory.candidates(false);
        Check.that(allCandidates.stream().noneMatch(u->u==spirit),"zero-cost spirit target is excluded from all-pack random");
        Check.that(allCandidates.stream().noneMatch(u->u==ironWall),"zero-cost Iron Wall cannon unit is excluded from all-pack random");

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

        BasisLU cheap=RandomLineupFactory.create(true,new Random(42),RandomLineupFactory.SortOrder.PRICE_ASC);
        BasisLU expensive=RandomLineupFactory.create(true,new Random(42),RandomLineupFactory.SortOrder.PRICE_DESC);
        int previous=Integer.MIN_VALUE;
        for(Form[] row:cheap.lu.fs)for(Form form:row){int price=form.du.getPrice();Check.that(price>=previous,"random lineup can sort selected units by ascending price");previous=price;}
        previous=Integer.MAX_VALUE;
        for(Form[] row:expensive.lu.fs)for(Form form:row){int price=form.du.getPrice();Check.that(price<=previous,"random lineup can sort selected units by descending price");previous=price;}
    }
    public static void main(String[] args)throws Exception{run();System.out.println("Random lineup tests passed");}
}
