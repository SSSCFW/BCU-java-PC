package online.tests;

import common.battle.*;
import common.battle.entity.EUnit;
import common.util.Data;
import common.util.unit.Trait;
import common.util.unit.Unit;
import online.net.lobby.PvpTraitRules;
import online.net.lobby.RoomRules;
import online.sync.InputFrame;

import java.util.Collections;

public final class PlayerTraitTests {
    public static void run() throws Exception {
        FixtureNativeUi.init();
        Unit leftUnit=FixtureNativeUi.unit("trait_left",0xffaa4433);
        Unit rightUnit=FixtureNativeUi.unit("trait_right",0xff3344aa);
        ((common.battle.data.CustomUnit)leftUnit.forms[0].du).price=1;
        ((common.battle.data.CustomUnit)rightUnit.forms[0].du).price=1;
        BasisLU left=Fixture.lineup(leftUnit),right=Fixture.lineup(rightUnit);

        RoomRules rules=new RoomRules(4400,0,-1,false,RoomRules.SpecialMode.ROULETTE,false,
                PvpTraitRules.NONE,PvpTraitRules.NONE,0,0,1);
        PvpStageBasis battle=new PvpStageBasis(left,right,9981,0,rules,1.0,1.0,Data.TRAIT_RED,Data.TRAIT_BLACK);
        battle.left().money=battle.right().money=999999;
        battle.step(new InputFrame(0,1,1));
        EUnit leftSpawn=(EUnit)battle.le.stream().filter(e->e instanceof EUnit&&e.dire==1).findFirst().orElseThrow(AssertionError::new);
        EUnit rightSpawn=(EUnit)battle.le.stream().filter(e->e instanceof EUnit&&e.dire==-1).findFirst().orElseThrow(AssertionError::new);
        Check.equal(Data.TRAIT_RED,leftSpawn.pvpAssignedTrait(),"left player's spawned unit receives selected red attribute");
        Check.equal(Data.TRAIT_BLACK,rightSpawn.pvpAssignedTrait(),"right player's spawned unit receives selected black attribute");
        Trait red=common.pack.UserProfile.getBCData().traits.get(Data.TRAIT_RED);
        Trait black=common.pack.UserProfile.getBCData().traits.get(Data.TRAIT_BLACK);
        Check.that(leftSpawn.traitCompatible(Collections.singletonList(red),rightSpawn,true),"red-target-only attack can target a red-assigned PvP unit");
        Check.that(!leftSpawn.traitCompatible(Collections.singletonList(black),rightSpawn,true),"black-target-only attack cannot target a red-assigned PvP unit");

        PvpStageBasis noTrait=new PvpStageBasis(left,right,9982,0,rules,1.0,1.0,PvpTraitRules.NONE,PvpTraitRules.NONE);
        noTrait.left().money=999999;noTrait.step(new InputFrame(0,1,0));
        EUnit neutral=(EUnit)noTrait.le.stream().filter(e->e instanceof EUnit&&e.dire==1).findFirst().orElseThrow(AssertionError::new);
        Check.equal(PvpTraitRules.NONE,neutral.pvpAssignedTrait(),"default PvP unit attribute is none");
        Check.that(!neutral.traitCompatible(Collections.singletonList(red),rightSpawn,true),"attribute-less unit is not treated as red");

        RoomRules randomRules=new RoomRules(4400,0,-1,false,RoomRules.SpecialMode.NONE,false,
                PvpTraitRules.RANDOM,PvpTraitRules.RANDOM,1<<PvpTraitRules.optionIndex(Data.TRAIT_RED),
                PvpTraitRules.ALL_EXCLUSIONS^(1<<PvpTraitRules.optionIndex(Data.TRAIT_BLACK)),15);
        Check.that(randomRules.resolvedHostTrait(12345L)!=Data.TRAIT_RED,"random host trait honors exclusions");
        Check.equal(Data.TRAIT_BLACK,randomRules.resolvedGuestTrait(12345L),"random guest trait can be constrained to one allowed attribute");

        int limit=1*60*PvpStageBasis.TPS;
        PvpStageBasis timed=new PvpStageBasis(left,right,9983,0,rules,1.0,1.0);
        timed.time=limit;timed.left().time=limit;
        timed.ebase.health=5000;timed.ubase.health=4000;
        Check.equal(0,timed.winner(),"time limit awards win to higher remaining left castle HP");
        timed.ebase.health=3000;timed.ubase.health=4000;
        Check.equal(1,timed.winner(),"time limit awards win to higher remaining right castle HP");
        timed.ebase.health=4000;timed.ubase.health=4000;
        Check.equal(-1,timed.winner(),"equal castle HP at time limit is a draw");

        RoomRules unlimitedRules=new RoomRules(4400,0,-1,false,RoomRules.SpecialMode.NONE,false,
                PvpTraitRules.NONE,PvpTraitRules.NONE,0,0,RoomRules.UNLIMITED_TIME);
        PvpStageBasis unlimited=new PvpStageBasis(left,right,9984,0,unlimitedRules,1.0,1.0);
        unlimited.time=99*60*PvpStageBasis.TPS;
        Check.equal(-2,unlimited.winner(),"unlimited room never ends from elapsed time alone");
    }
}
