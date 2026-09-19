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
        Trait red=common.pack.UserProfile.getBCData().traits.get(Data.TRAIT_RED);
        Trait black=common.pack.UserProfile.getBCData().traits.get(Data.TRAIT_BLACK);
        Trait white=common.pack.UserProfile.getBCData().traits.get(Data.TRAIT_WHITE);
        common.battle.data.CustomUnit leftData=(common.battle.data.CustomUnit)leftUnit.forms[0].du;
        common.battle.data.CustomUnit rightData=(common.battle.data.CustomUnit)rightUnit.forms[0].du;
        leftData.price=1;rightData.price=1;
        // Deliberately give the source units native traits. PvP's selected player
        // attribute must replace these, never combine with them.
        leftData.traits.add(white);leftData.traits.add(black);
        rightData.traits.add(white);rightData.traits.add(red);
        BasisLU left=Fixture.lineup(leftUnit),right=Fixture.lineup(rightUnit);

        RoomRules rules=new RoomRules(4400,0,3,false,RoomRules.SpecialMode.ROULETTE,false,
                PvpTraitRules.NONE,PvpTraitRules.NONE,0,0,1);
        PvpStageBasis battle=new PvpStageBasis(left,right,9981,0,rules,1.0,1.0,Data.TRAIT_RED,Data.TRAIT_BLACK);
        battle.left().money=battle.right().money=999999;
        battle.step(new InputFrame(0,1,1));
        EUnit leftSpawn=(EUnit)battle.le.stream().filter(e->e instanceof EUnit&&e.dire==1).findFirst().orElseThrow(AssertionError::new);
        EUnit rightSpawn=(EUnit)battle.le.stream().filter(e->e instanceof EUnit&&e.dire==-1).findFirst().orElseThrow(AssertionError::new);
        Check.equal((int)Data.TRAIT_RED,leftSpawn.pvpAssignedTrait(),"left player's spawned unit receives selected red attribute");
        Check.equal((int)Data.TRAIT_BLACK,rightSpawn.pvpAssignedTrait(),"right player's spawned unit receives selected black attribute");
        Check.equal(1,leftSpawn.traits.size(),"selected PvP attribute replaces all source-unit traits");
        Check.that(leftSpawn.traits.contains(red),"red-selected unit exposes only red in its runtime trait list");
        Check.that(!leftSpawn.traits.contains(white),"red-selected unit does not remain mixed with untraited/white");
        Check.that(!leftSpawn.traits.contains(black),"red-selected unit does not retain another native source trait");
        Check.equal(1,rightSpawn.traits.size(),"right player's selected attribute is also exclusive");
        Check.that(rightSpawn.traits.contains(black),"black-selected unit exposes only black");
        Check.that(!rightSpawn.traits.contains(white)&&!rightSpawn.traits.contains(red),"black-selected unit drops white and source red traits");
        Check.that(leftSpawn.traitCompatible(Collections.singletonList(red),rightSpawn,true),"red-target-only attack can target a red-assigned PvP unit");
        Check.that(!leftSpawn.traitCompatible(Collections.singletonList(black),rightSpawn,true),"black-target-only attack cannot target a red-assigned PvP unit");
        Check.that(!leftSpawn.traitCompatible(Collections.singletonList(white),rightSpawn,true),"white-target-only attack cannot target a red-assigned PvP unit");

        PvpStageBasis noTrait=new PvpStageBasis(left,right,9982,0,rules,1.0,1.0,PvpTraitRules.NONE,PvpTraitRules.NONE);
        noTrait.left().money=999999;noTrait.step(new InputFrame(0,1,0));
        EUnit neutral=(EUnit)noTrait.le.stream().filter(e->e instanceof EUnit&&e.dire==1).findFirst().orElseThrow(AssertionError::new);
        Check.equal(PvpTraitRules.NONE,neutral.pvpAssignedTrait(),"default PvP unit attribute is none");
        Check.that(neutral.traits.isEmpty(),"NONE PvP attribute clears native white/other source traits");
        Check.that(!neutral.traitCompatible(Collections.singletonList(red),rightSpawn,true),"attribute-less unit is not treated as red");
        Check.that(!neutral.traitCompatible(Collections.singletonList(white),rightSpawn,true),"NONE is distinct from explicitly selected white");

        PvpStageBasis explicitWhite=new PvpStageBasis(left,right,9985,0,rules,1.0,1.0,Data.TRAIT_WHITE,Data.TRAIT_BLACK);
        explicitWhite.left().money=999999;explicitWhite.step(new InputFrame(0,1,0));
        EUnit whiteSpawn=(EUnit)explicitWhite.le.stream().filter(e->e instanceof EUnit&&e.dire==1).findFirst().orElseThrow(AssertionError::new);
        Check.equal(1,whiteSpawn.traits.size(),"explicit white selection still has exactly one runtime trait");
        Check.that(whiteSpawn.traits.contains(white),"white is present only when explicitly selected");
        Check.that(whiteSpawn.traitCompatible(Collections.singletonList(white),rightSpawn,true),"white-target attack matches explicit white selection");
        Check.that(!whiteSpawn.traitCompatible(Collections.singletonList(red),rightSpawn,true),"explicit white selection is not mixed with red");

        RoomRules randomRules=new RoomRules(4400,0,3,false,RoomRules.SpecialMode.NONE,false,
                PvpTraitRules.RANDOM,PvpTraitRules.RANDOM,1<<PvpTraitRules.optionIndex(Data.TRAIT_RED),
                PvpTraitRules.ALL_EXCLUSIONS^(1<<PvpTraitRules.optionIndex(Data.TRAIT_BLACK)),15);
        Check.that(randomRules.resolvedHostTrait(12345L)!=Data.TRAIT_RED,"random host trait honors exclusions");
        Check.equal((int)Data.TRAIT_BLACK,randomRules.resolvedGuestTrait(12345L),"random guest trait can be constrained to one allowed attribute");

        int limit=1*60*PvpStageBasis.TPS;
        PvpStageBasis timed=new PvpStageBasis(left,right,9983,0,rules,1.0,1.0);
        timed.time=limit;timed.left().time=limit;
        timed.ebase.health=5000;timed.ubase.health=4000;
        Check.equal(0,timed.winner(),"time limit awards win to higher remaining left castle HP");
        timed.ebase.health=3000;timed.ubase.health=4000;
        Check.equal(1,timed.winner(),"time limit awards win to higher remaining right castle HP");
        timed.ebase.health=4000;timed.ubase.health=4000;
        Check.equal(-1,timed.winner(),"equal castle HP at time limit is a draw");

        RoomRules unlimitedRules=new RoomRules(4400,0,3,false,RoomRules.SpecialMode.NONE,false,
                PvpTraitRules.NONE,PvpTraitRules.NONE,0,0,RoomRules.UNLIMITED_TIME);
        PvpStageBasis unlimited=new PvpStageBasis(left,right,9984,0,unlimitedRules,1.0,1.0);
        unlimited.time=99*60*PvpStageBasis.TPS;
        Check.equal(-2,unlimited.winner(),"unlimited room never ends from elapsed time alone");
    }
}
