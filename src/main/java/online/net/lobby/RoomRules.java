package online.net.lobby;

import com.google.gson.JsonObject;
import online.net.Protocol;
import java.io.IOException;
import java.util.Objects;

/** Immutable, server-validated match options. Assets refer to the fingerprinted default pack. */
public final class RoomRules {
    public enum SpecialMode {
        CANNON("にゃんこ砲"), ROULETTE("対戦ルーレット"), NONE("なし");
        public final String label;
        SpecialMode(String label){this.label=label;}
        @Override public String toString(){return label;}
        static SpecialMode read(String value) throws IOException {
            try{return valueOf(value);}
            catch(IllegalArgumentException e){throw new IOException("Invalid battle special mode",e);}
        }
    }
    public static final int MIN_DISTANCE=1000, MAX_DISTANCE=24000, RANDOM_BACKGROUND=-1, RANDOM_MUSIC=-2;
    public static final int DEFAULT_TIME_LIMIT_MINUTES=15, MIN_TIME_LIMIT_MINUTES=1, MAX_TIME_LIMIT_MINUTES=99, UNLIMITED_TIME=0;
    public static final int DEFAULT_MAX_UNITS=100, MIN_MAX_UNITS=1, MAX_MAX_UNITS=250;
    public static final int DEFAULT_CASTLE_HIT_MONEY=5, MIN_CASTLE_HIT_MONEY=0, MAX_CASTLE_HIT_MONEY=1_000_000;
    public static final RoomRules DEFAULT=new RoomRules(4400,0,PvpBattleMusic.DEFAULT_ID,true,SpecialMode.CANNON,false,
            PvpTraitRules.NONE,PvpTraitRules.NONE,0,0,DEFAULT_TIME_LIMIT_MINUTES,
            DEFAULT_MAX_UNITS,false,DEFAULT_CASTLE_HIT_MONEY,false);
    public final int castleDistance, backgroundId, musicId;
    public final boolean force60Fps, debugMode, castleHitMoneyEnabled, rerollSlotAfterDeploy;
    public final SpecialMode specialMode;
    public final int hostTraitChoice, guestTraitChoice, hostTraitExclusions, guestTraitExclusions, timeLimitMinutes;
    public final int maxUnits, castleHitMoney;
    public RoomRules(int distance,int background,int music,boolean force60) {
        this(distance,background,music,force60,SpecialMode.CANNON,false);
    }
    public RoomRules(int distance,int background,int music,boolean force60,SpecialMode special) {
        this(distance,background,music,force60,special,false);
    }
    public RoomRules(int distance,int background,int music,boolean force60,SpecialMode special,boolean debug) {
        this(distance,background,music,force60,special,debug,
                PvpTraitRules.NONE,PvpTraitRules.NONE,0,0,DEFAULT_TIME_LIMIT_MINUTES);
    }
    public RoomRules(int distance,int background,int music,boolean force60,SpecialMode special,boolean debug,
                     int hostTrait,int guestTrait,int hostExclusions,int guestExclusions,int timeLimit) {
        this(distance,background,music,force60,special,debug,hostTrait,guestTrait,hostExclusions,guestExclusions,timeLimit,
                DEFAULT_MAX_UNITS,false,DEFAULT_CASTLE_HIT_MONEY,false);
    }
    public RoomRules(int distance,int background,int music,boolean force60,SpecialMode special,boolean debug,
                     int hostTrait,int guestTrait,int hostExclusions,int guestExclusions,int timeLimit,
                     int maxUnits,boolean castleHitMoneyEnabled,int castleHitMoney) {
        this(distance,background,music,force60,special,debug,hostTrait,guestTrait,hostExclusions,guestExclusions,timeLimit,
                maxUnits,castleHitMoneyEnabled,castleHitMoney,false);
    }
    public RoomRules(int distance,int background,int music,boolean force60,SpecialMode special,boolean debug,
                     int hostTrait,int guestTrait,int hostExclusions,int guestExclusions,int timeLimit,
                     int maxUnits,boolean castleHitMoneyEnabled,int castleHitMoney,boolean rerollSlotAfterDeploy) {
        boolean backgroundValid=background==RANDOM_BACKGROUND||(background>=0&&background<=65535);
        boolean musicValid=music==RANDOM_MUSIC||PvpBattleMusic.isAllowed(music);
        boolean timeValid=timeLimit==UNLIMITED_TIME||(timeLimit>=MIN_TIME_LIMIT_MINUTES&&timeLimit<=MAX_TIME_LIMIT_MINUTES);
        boolean maxUnitsValid=maxUnits>=MIN_MAX_UNITS&&maxUnits<=MAX_MAX_UNITS;
        boolean hitMoneyValid=castleHitMoney>=MIN_CASTLE_HIT_MONEY&&castleHitMoney<=MAX_CASTLE_HIT_MONEY;
        PvpTraitRules.validate(hostTrait,hostExclusions);PvpTraitRules.validate(guestTrait,guestExclusions);
        if(distance<MIN_DISTANCE||distance>MAX_DISTANCE||!backgroundValid||!musicValid||special==null||!timeValid||!maxUnitsValid||!hitMoneyValid)
            throw new IllegalArgumentException("城間距離・背景/対戦BGM・特殊機能・時間制限・出撃上限・城被弾ボーナスの設定が無効です");
        castleDistance=distance;backgroundId=background;musicId=music;force60Fps=true;specialMode=special;debugMode=debug;
        hostTraitChoice=hostTrait;guestTraitChoice=guestTrait;hostTraitExclusions=hostExclusions;guestTraitExclusions=guestExclusions;
        timeLimitMinutes=timeLimit;this.maxUnits=maxUnits;this.castleHitMoneyEnabled=castleHitMoneyEnabled;this.castleHitMoney=castleHitMoney;
        this.rerollSlotAfterDeploy=rerollSlotAfterDeploy;
    }
    public JsonObject json(){
        JsonObject o=new JsonObject();
        o.addProperty("castleDistance",castleDistance);o.addProperty("backgroundId",backgroundId);
        o.addProperty("musicId",musicId);o.addProperty("force60Fps",force60Fps);
        o.addProperty("specialMode",specialMode.name());o.addProperty("debugMode",debugMode);
        o.addProperty("hostTraitChoice",hostTraitChoice);o.addProperty("guestTraitChoice",guestTraitChoice);
        o.addProperty("hostTraitExclusions",hostTraitExclusions);o.addProperty("guestTraitExclusions",guestTraitExclusions);
        o.addProperty("timeLimitMinutes",timeLimitMinutes);
        o.addProperty("maxUnits",maxUnits);
        o.addProperty("castleHitMoneyEnabled",castleHitMoneyEnabled);
        o.addProperty("castleHitMoney",castleHitMoney);
        o.addProperty("rerollSlotAfterDeploy",rerollSlotAfterDeploy);
        return o;
    }
    public static RoomRules read(JsonObject parent)throws IOException{
        if(!parent.has("rules")||!parent.get("rules").isJsonObject())throw new IOException("Missing room rules");
        JsonObject o=parent.getAsJsonObject("rules");
        try{
            return new RoomRules(Protocol.integer(o,"castleDistance"),Protocol.integer(o,"backgroundId"),
                    Protocol.integer(o,"musicId"),Protocol.bool(o,"force60Fps"),
                    SpecialMode.read(Protocol.string(o,"specialMode",16)),
                    o.has("debugMode")&&Protocol.bool(o,"debugMode"),
                    o.has("hostTraitChoice")?Protocol.integer(o,"hostTraitChoice"):PvpTraitRules.NONE,
                    o.has("guestTraitChoice")?Protocol.integer(o,"guestTraitChoice"):PvpTraitRules.NONE,
                    o.has("hostTraitExclusions")?Protocol.integer(o,"hostTraitExclusions"):0,
                    o.has("guestTraitExclusions")?Protocol.integer(o,"guestTraitExclusions"):0,
                    o.has("timeLimitMinutes")?Protocol.integer(o,"timeLimitMinutes"):DEFAULT_TIME_LIMIT_MINUTES,
                    o.has("maxUnits")?Protocol.integer(o,"maxUnits"):DEFAULT_MAX_UNITS,
                    o.has("castleHitMoneyEnabled")&&Protocol.bool(o,"castleHitMoneyEnabled"),
                    o.has("castleHitMoney")?Protocol.integer(o,"castleHitMoney"):DEFAULT_CASTLE_HIT_MONEY,
                    o.has("rerollSlotAfterDeploy")&&Protocol.bool(o,"rerollSlotAfterDeploy"));
        } catch(IllegalArgumentException e){throw new IOException(e.getMessage(),e);}
    }
    @Override public boolean equals(Object value){
        if(!(value instanceof RoomRules))return false;
        RoomRules r=(RoomRules)value;
        return castleDistance==r.castleDistance&&backgroundId==r.backgroundId&&musicId==r.musicId
                &&force60Fps==r.force60Fps&&specialMode==r.specialMode&&debugMode==r.debugMode
                &&hostTraitChoice==r.hostTraitChoice&&guestTraitChoice==r.guestTraitChoice
                &&hostTraitExclusions==r.hostTraitExclusions&&guestTraitExclusions==r.guestTraitExclusions
                &&timeLimitMinutes==r.timeLimitMinutes&&maxUnits==r.maxUnits
                &&castleHitMoneyEnabled==r.castleHitMoneyEnabled&&castleHitMoney==r.castleHitMoney
                &&rerollSlotAfterDeploy==r.rerollSlotAfterDeploy;
    }
    public int resolvedHostTrait(long seed){return PvpTraitRules.resolve(hostTraitChoice,hostTraitExclusions,seed,0x13579bdf2468ace0L);}
    public int resolvedGuestTrait(long seed){return PvpTraitRules.resolve(guestTraitChoice,guestTraitExclusions,seed,0x02468ace13579bdfL);}
    @Override public int hashCode(){return Objects.hash(castleDistance,backgroundId,musicId,force60Fps,specialMode,debugMode,
            hostTraitChoice,guestTraitChoice,hostTraitExclusions,guestTraitExclusions,timeLimitMinutes,maxUnits,castleHitMoneyEnabled,castleHitMoney,rerollSlotAfterDeploy);}
}
