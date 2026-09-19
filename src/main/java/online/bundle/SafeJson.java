package online.bundle;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** Strict parser in front of BCU's reflective decoder. Never accept arbitrary class tags. */
public final class SafeJson {
    private static final Set<String> TAGS=new HashSet<>(Arrays.asList(
        "common.battle.data.CustomUnit", "common.battle.data.CustomEnemy",
        "common.pack.FixIndexList$FixIndexMap", "common.pack.PackData$UserPack",
        "common.util.stage.MapColc$PackMapColc", "common.util.stage.CastleList$PackCasList",
        "common.util.stage.CustomStageInfo", "common.util.stage.Limit$DefLimit"));
    private static final Set<String> IDENTIFIERS=new HashSet<>(Arrays.asList(
        "common.util.unit.Unit", "common.util.unit.UnitLevel", "common.util.unit.Trait",
        "common.util.unit.Enemy", "common.util.unit.AbEnemy", "common.util.unit.EneRand",
        "common.util.unit.Combo", "common.util.pack.Soul", "common.util.pack.DemonSoul",
        "common.util.pack.Background", "common.util.stage.CastleImg", "common.util.stage.Stage",
        "common.util.stage.StageMap", "common.util.stage.Music", "common.util.stage.CharaGroup",
        "common.util.stage.LvRestrict", "common.util.stage.SCGroup"));
    private SafeJson() {}
    public static JsonObject object(byte[] utf8) throws IOException {
        try {
            String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(utf8)).toString();
            JsonReader r=new JsonReader(new StringReader(text)); r.setLenient(false);
            JsonElement result=read(r,0,new int[]{0});
            if(r.peek()!=JsonToken.END_DOCUMENT || !result.isJsonObject()) throw new IOException("Expected one JSON object");
            validate(result,0); return result.getAsJsonObject();
        }catch(IllegalStateException | NumberFormatException e){throw new IOException("Invalid JSON",e);}
    }
    private static JsonElement read(JsonReader r,int depth,int[] count) throws IOException {
        if(depth>64 || ++count[0]>1_000_000) throw new IOException("JSON complexity limit");
        switch(r.peek()) {
            case BEGIN_OBJECT:
                JsonObject o=new JsonObject();r.beginObject();
                while(r.hasNext()) {String n=r.nextName();if(o.has(n))throw new IOException("Duplicate JSON key");o.add(n,read(r,depth+1,count));}
                r.endObject();return o;
            case BEGIN_ARRAY:
                JsonArray a=new JsonArray();r.beginArray();
                while(r.hasNext()){if(a.size()>=32768)throw new IOException("Oversized JSON array");a.add(read(r,depth+1,count));}
                r.endArray();return a;
            case STRING: String s=r.nextString();if(s.length()>65536)throw new IOException("Oversized JSON string");return new JsonPrimitive(s);
            case NUMBER:
                String n=r.nextString();if(n.length()>48)throw new IOException("Oversized number");
                java.math.BigDecimal v=new java.math.BigDecimal(n);if(v.abs().compareTo(new java.math.BigDecimal("9223372036854775807"))>0)throw new IOException("Numeric limit");
                return new JsonPrimitive(v);
            case BOOLEAN:return new JsonPrimitive(r.nextBoolean());
            case NULL:r.nextNull();return JsonNull.INSTANCE;
            default:throw new IOException("Unexpected JSON token");
        }
    }
    private static void validate(JsonElement e,int depth) throws IOException {
        if(e.isJsonArray()){for(JsonElement c:e.getAsJsonArray())validate(c,depth+1);return;}
        if(!e.isJsonObject())return;
        JsonObject o=e.getAsJsonObject();
        if(o.has("_class") && !TAGS.contains(o.get("_class").getAsString())) throw new IOException("Unsupported class tag in network data");
        if(o.has("cls") && !IDENTIFIERS.contains(o.get("cls").getAsString())) throw new IOException("Unsupported identifier type");
        if(o.has("ind") && o.get("ind").isJsonPrimitive()) {
            int n=o.get("ind").getAsInt();if(n<0 || n>16383)throw new IOException("Pack index limit");
        }
        for(Map.Entry<String,JsonElement> f:o.entrySet())validate(f.getValue(),depth+1);
    }
}
