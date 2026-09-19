package online.tests;

import common.CommonStatic;
import common.battle.*;
import common.battle.entity.*;
import common.battle.data.*;
import common.util.anim.*;
import common.util.unit.*;
import online.sync.InputFrame;

public final class CoreTests {
    public static void run() throws Exception {
        FixtureAssets.init();
        Unit a=Fixture.unit("core_a",10000),b=Fixture.unit("core_b",10000);
        PvpStageBasis battle=new PvpStageBasis(Fixture.lineup(a),Fixture.lineup(b),123L,0);
        Check.equal(1,battle.left().ownDirection(),"left is enemy position");
        Check.equal(-1,battle.right().ownDirection(),"right is cat position");
        battle.left().money=battle.right().money=100000;
        battle.step(new InputFrame(0,1,1));
        Check.equal(2,battle.le.size(),"both players spawn units");
        Check.that(battle.le.stream().allMatch(e -> e instanceof EUnit),"both sides preserve player-unit class");
        Check.equal(battle.left().money,battle.right().money,"independent equal spending/income");
        Check.equal(battle.left().elu.cool[0][0],battle.right().elu.cool[0][0],"symmetric cooldowns");
        EUnit left=(EUnit)battle.le.stream().filter(e->e.dire==1).findFirst().get();
        EUnit right=(EUnit)battle.le.stream().filter(e->e.dire==-1).findFirst().get();
        Check.that(Math.abs(battle.st.len-left.pos-right.pos)<0.001,"mirrored spawn and movement");
        Check.that(left.basis==battle.left() && right.basis==battle.right(),"units reference their own economy");
        PvpStageBasis copy=battle.displayCopy();
        Check.that(copy!=battle && copy.pvpOther!=battle.pvpOther,"deep cloned both player states");
        Check.that(copy.le==copy.pvpOther.le && copy.le!=battle.le,"cloned world shares lists only internally");
        Check.that(copy.r==copy.pvpOther.r && copy.r!=battle.r,"cloned world RNG is isolated");
        Check.equal(online.sync.BattleDigest.of(battle),online.sync.BattleDigest.of(copy),"deep copy preserves canonical digest");
        long hp=left.health;copy.le.get(0).health=1;
        Check.equal(hp,left.health,"display cannot mutate canonical health");
        battle.left().deployDupe[0][0][0]=1;
        battle.right().deployDupe[0][0][0]=1;
        battle.step(new InputFrame(1,0,0));
        Check.equal(battle.entityCount(1),battle.entityCount(-1),"duplicate deployment is symmetric");
        // Fixed battle cadence independent from the legacy user FPS flags.
        PvpStageBasis p30=new PvpStageBasis(Fixture.lineup(a),Fixture.lineup(b),55,0);
        PvpStageBasis p60=new PvpStageBasis(Fixture.lineup(a),Fixture.lineup(b),55,0);
        for(int tick=0;tick<360;tick++) {
            int mask=tick%60==0?1:0;
            CommonStatic.getConfig().performanceModeAnimation=false;
            CommonStatic.getConfig().performanceModeBattle=false;
            p30.step(new InputFrame(tick,mask,mask));
            CommonStatic.getConfig().performanceModeAnimation=true;
            CommonStatic.getConfig().performanceModeBattle=true;
            p60.step(new InputFrame(tick,mask,mask));
            if(tick%30==0) {PvpStageBasis visual=p60.displayCopy();visual.advanceDisplay();}
        }
        Check.equal(online.sync.BattleDigest.of(p30),online.sync.BattleDigest.of(p60),"full state digest is FPS independent");
        Check.equal(summary(p30),summary(p60),"30 vs 60 render settings preserve simulation");
        Check.equal(p30.left().money,p30.right().money,"symmetric economy after 360 ticks");
        animationClonePreservesRuntimePose();
        CommonStatic.getConfig().performanceModeAnimation=false;
        CommonStatic.getConfig().performanceModeBattle=false;
    }
    private static void animationClonePreservesRuntimePose() {
        AnimCI animation=Fixture.animation("clone_pose","anim");
        animation.check();
        MaAnim walk=animation.getMaAnim(AnimU.UType.WALK);
        Part track=new Part(0,4);
        track.ints[2]=2;
        track.n=2;
        track.moves=new int[][]{{0,0,0,0},{10,100,0,0}};
        track.validate();
        walk.n=1;walk.parts=new Part[]{track};walk.validate();

        EAnimU live=animation.getEAnim(AnimU.UType.WALK);
        for(int i=0;i<26;i++)live.update(false);
        Check.equal(100f,live.ent[0].getValRaw(4),"finite walk track reaches and holds its final runtime pose");
        EAnimU copy=(EAnimU)live.clone();
        Check.equal(live.ind(),copy.ind(),"display clone preserves the unwrapped animation clock");
        Check.equal(live.ent[0].getValRaw(4),copy.ent[0].getValRaw(4),
                "display clone preserves the current finite-track pose instead of wrapping to frame zero");
    }

    public static String summary(PvpStageBasis b) {
        StringBuilder s=new StringBuilder();s.append(b.time).append(':').append(b.left().money).append(':').append(b.right().money);
        for(Entity e:b.le)s.append('|').append(e.dire).append(',').append(e.pos).append(',').append(e.health);
        return s.toString();
    }
}
