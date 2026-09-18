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
    public static final int MIN_DISTANCE=1000, MAX_DISTANCE=24000;
    public static final RoomRules DEFAULT=new RoomRules(4400,0,-1,false,SpecialMode.CANNON,false);
    public final int castleDistance, backgroundId, musicId;
    public final boolean force60Fps, debugMode;
    public final SpecialMode specialMode;
    public RoomRules(int distance,int background,int music,boolean force60) {
        this(distance,background,music,force60,SpecialMode.CANNON,false);
    }
    public RoomRules(int distance,int background,int music,boolean force60,SpecialMode special) {
        this(distance,background,music,force60,special,false);
    }
    public RoomRules(int distance,int background,int music,boolean force60,SpecialMode special,boolean debug) {
        if(distance<MIN_DISTANCE||distance>MAX_DISTANCE||background<0||background>65535||music < -1||music>65535||special==null)
            throw new IllegalArgumentException("城間距離は1000〜24000、背景/BGM/特殊機能は有効な値を選択してください");
        castleDistance=distance;backgroundId=background;musicId=music;force60Fps=force60;specialMode=special;debugMode=debug;
    }
    public JsonObject json(){
        JsonObject o=new JsonObject();
        o.addProperty("castleDistance",castleDistance);o.addProperty("backgroundId",backgroundId);
        o.addProperty("musicId",musicId);o.addProperty("force60Fps",force60Fps);
        o.addProperty("specialMode",specialMode.name());o.addProperty("debugMode",debugMode);
        return o;
    }
    public static RoomRules read(JsonObject parent)throws IOException{
        if(!parent.has("rules")||!parent.get("rules").isJsonObject())throw new IOException("Missing room rules");
        JsonObject o=parent.getAsJsonObject("rules");
        try{
            return new RoomRules(Protocol.integer(o,"castleDistance"),Protocol.integer(o,"backgroundId"),
                    Protocol.integer(o,"musicId"),Protocol.bool(o,"force60Fps"),
                    SpecialMode.read(Protocol.string(o,"specialMode",16)),
                    o.has("debugMode")&&Protocol.bool(o,"debugMode"));
        } catch(IllegalArgumentException e){throw new IOException(e.getMessage(),e);}
    }
    @Override public boolean equals(Object value){
        if(!(value instanceof RoomRules))return false;
        RoomRules r=(RoomRules)value;
        return castleDistance==r.castleDistance&&backgroundId==r.backgroundId&&musicId==r.musicId
                &&force60Fps==r.force60Fps&&specialMode==r.specialMode&&debugMode==r.debugMode;
    }
    @Override public int hashCode(){return Objects.hash(castleDistance,backgroundId,musicId,force60Fps,specialMode,debugMode);}
}
