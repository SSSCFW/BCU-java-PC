package online.tests;

import com.google.gson.JsonObject;
import common.CommonStatic;
import common.battle.*;
import common.pack.*;
import common.system.files.FDByte;
import common.util.stage.Music;
import online.net.Protocol;
import online.net.lobby.RoomRules;
import online.net.lobby.PvpBattleMusic;
import online.net.lobby.PvpTraitRules;
import online.sync.*;
import online.ui.OnlineBattleField;

/** Immutable rule roundtrip, real arena geometry and display-only frame-rate override. */
public final class RoomRuleTests {
    public static void run() throws Exception {
        FixtureNativeUi.init();
        Check.rejects(()->new RoomRules(999,0,3,false),"too-short distance rejected");
        Check.rejects(()->new RoomRules(24001,0,3,false),"too-long distance rejected");
        Check.rejects(()->new RoomRules(4400,-2,3,false),"unsupported negative background rejected");
        Check.rejects(()->new RoomRules(4400,0,-3,false),"unsupported negative music identifier rejected");
        Check.rejects(()->new RoomRules(4400,0,3,false,RoomRules.SpecialMode.NONE,false,
                PvpTraitRules.NONE,PvpTraitRules.NONE,0,0,100),"time limit above 99 minutes rejected");
        Check.rejects(()->new RoomRules(4400,0,3,false,RoomRules.SpecialMode.NONE,false,
                PvpTraitRules.RANDOM,PvpTraitRules.NONE,PvpTraitRules.ALL_EXCLUSIONS,0,15),"random trait cannot exclude every option");
        Check.rejects(()->new RoomRules(4400,0,3,true,RoomRules.SpecialMode.NONE,false,
                PvpTraitRules.NONE,PvpTraitRules.NONE,0,0,15,0,false,5),"zero maximum deployed units rejected");
        Check.rejects(()->new RoomRules(4400,0,3,true,RoomRules.SpecialMode.NONE,false,
                PvpTraitRules.NONE,PvpTraitRules.NONE,0,0,15,RoomRules.DEFAULT_MAX_UNITS,true,-1),"negative castle-hit money rejected");
        Check.rejects(()->PvpStageBasis.validateCastleHealthMultiplier(Double.NaN),"NaN castle multiplier rejected");
        Check.rejects(()->PvpStageBasis.validateCastleHealthMultiplier(0.0),"non-positive castle multiplier rejected");
        for(int distance:new int[]{1000,4400,24000}) {
            RoomRules rule=new RoomRules(distance,0,3,true,RoomRules.SpecialMode.ROULETTE,true,
                    PvpTraitRules.RANDOM,common.util.Data.TRAIT_RED,1<<PvpTraitRules.optionIndex(common.util.Data.TRAIT_BLACK),0,15,
                    123,true,7);
            JsonObject message=Protocol.message("rules");message.add("rules",rule.json());
            Check.equal(rule,RoomRules.read(message),"all rules roundtrip without client-local settings");
            Check.equal(RoomRules.SpecialMode.ROULETTE,RoomRules.read(message).specialMode,"special mode is synchronized in room rules");
            Check.that(RoomRules.read(message).debugMode,"host debug mode is synchronized in room rules");
            Check.equal(15,RoomRules.read(message).timeLimitMinutes,"time limit is synchronized in room rules");
            Check.equal(PvpTraitRules.RANDOM,RoomRules.read(message).hostTraitChoice,"host random trait is synchronized");
            Check.equal((int)common.util.Data.TRAIT_RED,RoomRules.read(message).guestTraitChoice,"guest fixed trait is synchronized");
            Check.equal(123,RoomRules.read(message).maxUnits,"maximum deployed unit count is synchronized");
            Check.that(RoomRules.read(message).castleHitMoneyEnabled,"castle-hit money mode is synchronized");
            Check.equal(7,RoomRules.read(message).castleHitMoney,"castle-hit money amount is synchronized");
            BasisLU l=Fixture.lineup(FixtureNativeUi.unit("rule_l"+distance,0xff0055aa));
            BasisLU r=Fixture.lineup(FixtureNativeUi.unit("rule_r"+distance,0xffaa5500));
            PvpStageBasis baseline=new PvpStageBasis(l,r,133,0,rule,1.0,1.0);
            long leftBase=baseline.left().ownBase().maxH,rightBase=baseline.right().ownBase().maxH;
            PvpStageBasis a=new PvpStageBasis(l,r,134,0,rule),b=new PvpStageBasis(l,r,134,0,rule);
            Check.equal(Math.round(leftBase*40.0),a.left().ownBase().maxH,"default left castle HP is 40x its native battle value");
            Check.equal(Math.round(rightBase*40.0),a.right().ownBase().maxH,"default right castle HP is 40x its native battle value");
            PvpStageBasis scaled=new PvpStageBasis(l,r,135,0,rule,2.5,7.25);
            Check.equal(Math.round(leftBase*2.5),scaled.left().ownBase().maxH,"left player controls its own double castle HP multiplier");
            Check.equal(Math.round(rightBase*7.25),scaled.right().ownBase().maxH,"right player controls its own double castle HP multiplier");
            Check.equal((float)distance,a.ubase.pos-a.ebase.pos,"configured distance is exact castle separation");
            Check.equal(distance+1600,a.st.len,"stage includes consistent castle margins");
            Check.equal(123,a.left().maxNum,"host unit cap applies to left participant");
            Check.equal(123,a.right().maxNum,"host unit cap applies to right participant");
            Check.equal(123,a.st.max,"arena stage max follows host unit cap");
            Check.equal(3,a.st.mus0.id,"curated battle BGM is installed into the arena");
            for(int tick=0;tick<5;tick++){a.step(new InputFrame(tick,0,0));b.step(new InputFrame(tick,0,0));}
            Check.equal(BattleDigest.of(a),BattleDigest.of(b),"same rules produce same simulation");
            boolean old=CommonStatic.getConfig().performanceModeBattle;
            try {
                CommonStatic.getConfig().performanceModeBattle=false;
                OnlineBattleField view=new OnlineBattleField(new CommonStatic.FakeKey(){public boolean pressed(int r,int c){return false;}public void remove(int r,int c){}},a.displayCopy(),1,i->{});
                Check.equal(60,view.renderFps(),"online PvP presentation is fixed at 60FPS");
                view.force60Fps(false);Check.equal(60,view.renderFps(),"legacy room override cannot lower fixed 60FPS");
                int time=view.sb.time;view.renderStep();view.renderStep();
                Check.equal(time,view.sb.time,"60FPS presentation cannot advance logic tick");
                Check.that(!CommonStatic.getConfig().performanceModeBattle,"fixed online 60FPS is not a global preference mutation");
            } finally {CommonStatic.getConfig().performanceModeBattle=old;}
        }
        for(RoomRules.SpecialMode mode:RoomRules.SpecialMode.values()){
            RoomRules special=new RoomRules(4400,0,3,false,mode);
            JsonObject msg=Protocol.message("rules");msg.add("rules",special.json());
            Check.equal(mode,RoomRules.read(msg).specialMode,"host special mode roundtrip: "+mode);
        }
        common.util.pack.Background bg4=new common.util.pack.Background(new Identifier<>(Identifier.DEF,common.util.pack.Background.class,4),FixtureNativeUi.image(64,64,0xff556677));
        UserProfile.getBCData().bgs.set(4,bg4);
        UserProfile.getBCData().musics.set(3,new Music(new Identifier<>(Identifier.DEF,Music.class,3),0,new FDByte(new byte[]{4,5,6})));
        RoomRules randomRules=new RoomRules(4400,RoomRules.RANDOM_BACKGROUND,RoomRules.RANDOM_MUSIC,false,RoomRules.SpecialMode.ROULETTE,true);
        PvpStageBasis.validateRulesAssets(randomRules);
        RoomRules resolvedA=PvpStageBasis.resolveRandomRules(randomRules,123456789L),resolvedB=PvpStageBasis.resolveRandomRules(randomRules,123456789L);
        Check.equal(resolvedA,resolvedB,"random background/BGM resolve deterministically from the shared match seed");
        Check.that(resolvedA.backgroundId>=0,"random background resolves to a concrete standard background ID");
        Check.that(PvpBattleMusic.isAllowed(resolvedA.musicId),"random BGM resolves to a curated concrete BGM");
        Check.that(resolvedA.musicId!=RoomRules.RANDOM_MUSIC,"random BGM sentinel is gone before arena creation");
        BasisLU randomLeft=Fixture.lineup(FixtureNativeUi.unit("random_music_l",0xff225588));
        BasisLU randomRight=Fixture.lineup(FixtureNativeUi.unit("random_music_r",0xff882255));
        PvpStageBasis randomBattleA=new PvpStageBasis(randomLeft,randomRight,24680,0,randomRules),
                randomBattleB=new PvpStageBasis(randomLeft,randomRight,24680,0,randomRules);
        Check.equal(randomBattleA.st.mus0.id,randomBattleB.st.mus0.id,"both peers resolve the same random BGM for the same battle seed");
        Check.rejects(()->new RoomRules(4400,0,7,false),"non-curated BGM identifier rejected");

        JsonObject invalidMode=Protocol.message("rules");invalidMode.add("rules",RoomRules.DEFAULT.json());
        invalidMode.getAsJsonObject("rules").addProperty("specialMode","NOT_A_MODE");
        Check.rejects(()->RoomRules.read(invalidMode),"unknown special mode rejected");
        JsonObject bad=Protocol.message("rules");bad.add("rules",RoomRules.DEFAULT.json());
        bad.getAsJsonObject("rules").addProperty("force60Fps","true");
        Check.rejects(()->RoomRules.read(bad),"boolean option must not accept a string");
        Check.rejects(()->PvpStageBasis.validateRulesAssets(new RoomRules(4400,65534,3,false)),"unavailable background rejected before confirmation");
        Identifier<Music> id=new Identifier<>(Identifier.DEF,Music.class,166);
        Music previous=UserProfile.getBCData().musics.get(id.id);
        try {
            UserProfile.getBCData().musics.set(id.id,new Music(id,0,null));
            Check.rejects(()->PvpStageBasis.validateRulesAssets(new RoomRules(4400,0,id.id,false)),"allowed music entry without bytes must be rejected before ready");
            UserProfile.getBCData().musics.set(id.id,new Music(id,0,new FDByte(new byte[]{1,2,3})));
            PvpStageBasis.validateRulesAssets(new RoomRules(4400,0,id.id,false));
        } finally {if(previous==null)UserProfile.getBCData().musics.remove(UserProfile.getBCData().musics.get(id.id));else UserProfile.getBCData().musics.set(id.id,previous);}
    }
    public static void main(String[] args)throws Exception{run();System.out.println("Room rule tests passed");}
}
