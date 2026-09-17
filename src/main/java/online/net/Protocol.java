package online.net;

import com.google.gson.*;
import org.java_websocket.drafts.Draft_6455;
import java.io.IOException;
import java.util.Collections;

public final class Protocol {
    public static final int VERSION=2, TPS=30, INPUT_DELAY=3, HASH_INTERVAL=60, MAX_AHEAD=32;
    public static final int MAX_TEXT=4096, CHUNK=65536, MAX_FRAME=CHUNK+1024;
    public static final long MAX_BUNDLE=32L*1024*1024;
    public static final String ENGINE="bcu-pvp-2-core-8920447";
    private Protocol() {}
    public static Draft_6455 draft() { return new Draft_6455(Collections.emptyList(),MAX_FRAME); }
    public static JsonObject message(String type) { JsonObject o=new JsonObject(); o.addProperty("type",type); return o; }
    public static JsonObject parse(String text) throws IOException {
        if(text==null || text.length()>MAX_TEXT) throw new IOException("Control message too large");
        try {
            // Bound nesting before Gson parses participant rosters and hash maps.
            int depth=0; boolean quoted=false,escape=false;
            for(char c:text.toCharArray()) {
                if(quoted) { if(escape) escape=false; else if(c=='\\') escape=true; else if(c=='"') quoted=false; }
                else if(c=='"') quoted=true;
                else if(c=='{' || c=='[') { if(++depth>8) throw new IOException("JSON nesting"); }
                else if(c=='}' || c==']') depth--;
            }
            JsonObject o=JsonParser.parseString(text).getAsJsonObject(); string(o,"type",32); return o;
        } catch(RuntimeException e) { throw new IOException("Malformed control message",e); }
    }
    public static String string(JsonObject o,String key,int max) throws IOException {
        JsonElement v=o.get(key);
        if(v==null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString()) throw new IOException("Missing string: "+key);
        String s=v.getAsString();
        if(s.length()>max || s.indexOf('\0')>=0) throw new IOException("Invalid string: "+key);
        return s;
    }
    public static long number(JsonObject o,String key) throws IOException {
        JsonElement v=o.get(key);
        if(v==null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber()) throw new IOException("Missing number: "+key);
        try { return new java.math.BigDecimal(v.getAsString()).longValueExact(); }
        catch(ArithmeticException | NumberFormatException e) { throw new IOException("Invalid number: "+key,e); }
    }
    public static int integer(JsonObject o,String key) throws IOException {
        long n=number(o,key); if(n<Integer.MIN_VALUE || n>Integer.MAX_VALUE) throw new IOException("Number out of range"); return (int)n;
    }
}
