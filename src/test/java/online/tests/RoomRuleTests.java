package online.tests;

import com.google.gson.JsonObject;
import common.CommonStatic;
import common.battle.*;
import common.pack.*;
import common.system.files.FDByte;
import common.util.stage.Music;
import online.net.Protocol;
import online.net.lobby.RoomRules;
import online.sync.*;
import online.ui.OnlineBattleField;

/** Immutable rule roundtrip, real arena geometry and display-only frame-rate override. */
public final class RoomRuleTests {
    public static void run() throws Exception {
        FixtureNativeUi.init();
        Check.rejects(()->new RoomRules(999,0,-1,false),"too-short distance rejected");
        Check.rejects(()->new RoomRules(24001,0,-1,false),"too-long distance rejected");
        Check.rejects(()->new RoomRules(4400,-1,-1,false),"negative background rejected");
        Check.rejects(()->new RoomRules(4400,0,-2,false),"invalid silence identifier rejected");
        Check.rejects(()->PvpStageBasis.validateCastleHealthMultiplier(Double.NaN),"NaN castle multiplier rejected");
        Check.rejects(()->PvpStageBasis.validateCastleHealthMultiplier(0.0),"non-positive castle multiplier rejected");
        for(int distance:new int[]{1000,4400,24000}) {
            RoomRules rule=new RoomRules(distance,0,-1,true,RoomRules.SpecialMode.ROULETTE);
            JsonObject message=Protocol.message("rules");message.add("rules",rule.json());
            Check.equal(rule,RoomRules.read(message),"all rules roundtrip without client-local settings");
            Check.equal(RoomRules.SpecialMode.ROULETTE,RoomRules.read(message).specialMode,"special mode is synchronized in room rules");
            BasisLU l=Fixture.lineup(FixtureNativeUi.unit("rule_l"+distance,0xff0055aa));
            BasisLU r=Fixture.lineup(FixtureNativeUi.unit("rule_r"+distance,0xffaa5500));
            PvpStageBasis a=new PvpStageBasis(l,r,134,0,rule),b=new PvpStageBasis(l,r,134,0,rule);
            Check.equal(1200000L,a.left().ownBase().maxH,"default left castle HP is 20x");
            Check.equal(1200000L,a.right().ownBase().maxH,"default right castle HP is 20x");
            PvpStageBasis scaled=new PvpStageBasis(l,r,135,0,rule,2.5,7.25);
            Check.equal(150000L,scaled.left().ownBase().maxH,"left player controls its own double castle HP multiplier");
            Check.equal(435000L,scaled.right().ownBase().maxH,"right player controls its own double castle HP multiplier");
            Check.equal((float)distance,a.ubase.pos-a.ebase.pos,"configured distance is exact castle separation");
            Check.equal(distance+1600,a.st.len,"stage includes consistent castle margins");
            Check.equal(null,a.st.mus0,"no-BGM option contains no music identifier");
            for(int tick=0;tick<5;tick++){a.step(new InputFrame(tick,0,0));b.step(new InputFrame(tick,0,0));}
            Check.equal(BattleDigest.of(a),BattleDigest.of(b),"same rules produce same simulation");
            boolean old=CommonStatic.getConfig().performanceModeBattle;
            try {
                CommonStatic.getConfig().performanceModeBattle=false;
                OnlineBattleField view=new OnlineBattleField(new CommonStatic.FakeKey(){public boolean pressed(int r,int c){return false;}public void remove(int r,int c){}},a.displayCopy(),1,i->{});
                Check.equal(30,view.renderFps(),"unforced room respects 30FPS preference");
                view.force60Fps(true);Check.equal(60,view.renderFps(),"host can force display60");
                int time=view.sb.time;Thread.sleep(18);view.renderStep();
                Check.equal(time,view.sb.time,"forced display60 cannot advance logic tick");
                Check.that(!CommonStatic.getConfig().performanceModeBattle,"force60 is not a global preference mutation");
                view.force60Fps(false);Check.equal(30,view.renderFps(),"turning off override restores local preference");
            } finally {CommonStatic.getConfig().performanceModeBattle=old;}
        }
        for(RoomRules.SpecialMode mode:RoomRules.SpecialMode.values()){
            RoomRules special=new RoomRules(4400,0,-1,false,mode);
            JsonObject msg=Protocol.message("rules");msg.add("rules",special.json());
            Check.equal(mode,RoomRules.read(msg).specialMode,"host special mode roundtrip: "+mode);
        }
        JsonObject invalidMode=Protocol.message("rules");invalidMode.add("rules",RoomRules.DEFAULT.json());
        invalidMode.getAsJsonObject("rules").addProperty("specialMode","NOT_A_MODE");
        Check.rejects(()->RoomRules.read(invalidMode),"unknown special mode rejected");
        JsonObject bad=Protocol.message("rules");bad.add("rules",RoomRules.DEFAULT.json());
        bad.getAsJsonObject("rules").addProperty("force60Fps","true");
        Check.rejects(()->RoomRules.read(bad),"boolean option must not accept a string");
        Check.rejects(()->PvpStageBasis.validateRulesAssets(new RoomRules(4400,65534,-1,false)),"unavailable background rejected before confirmation");
        Identifier<Music> id=new Identifier<>(Identifier.DEF,Music.class,65534);
        Music previous=UserProfile.getBCData().musics.get(id.id);
        try {
            UserProfile.getBCData().musics.set(id.id,new Music(id,0,null));
            Check.rejects(()->PvpStageBasis.validateRulesAssets(new RoomRules(4400,0,id.id,false)),"music entry without bytes must be rejected before ready");
            UserProfile.getBCData().musics.set(id.id,new Music(id,0,new FDByte(new byte[]{1,2,3})));
            PvpStageBasis.validateRulesAssets(new RoomRules(4400,0,id.id,false));
        } finally {if(previous==null)UserProfile.getBCData().musics.remove(UserProfile.getBCData().musics.get(id.id));else UserProfile.getBCData().musics.set(id.id,previous);}
    }
    public static void main(String[] args)throws Exception{run();System.out.println("Room rule tests passed");}
}
