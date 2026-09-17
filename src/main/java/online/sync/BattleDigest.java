package online.sync;

import common.battle.PvpStageBasis;
import common.pack.Identifier;
import common.util.BattleObj;
import common.util.BattleStatic;
import common.util.anim.EAnimD;
import common.util.anim.EAnimI;
import common.util.unit.Trait;
import online.bundle.Hashes;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Canonical hash of mutable simulation state, including private timers and RNG state. */
public final class BattleDigest {
    private static final Map<Class<?>,List<Field>> FIELDS=new ConcurrentHashMap<>();
    private static final Set<String> VIEW_FIELDS=new HashSet<>(Arrays.asList(
        "pos","siz","midH","battleHeight","shake","shakeOffset","shakeCoolDown","shakeDuration","bgEffect","bgEffectInitialized"));
    private final IdentityHashMap<Object,Integer> seen=new IdentityHashMap<>();
    private final MessageDigest digest=Hashes.digest();
    private final DataOutputStream out=new DataOutputStream(new DigestOutputStream(new OutputStream(){public void write(int b){} public void write(byte[] b,int o,int n){}},digest));
    private BattleDigest(){}
    public static String of(PvpStageBasis state) {
        BattleDigest writer=new BattleDigest();
        try {writer.text("BCU-PVP-STATE-1");writer.value(state);writer.out.flush();return Hashes.hex(writer.digest.digest());}
        catch(ReflectiveOperationException|IOException e){throw new IllegalStateException("Cannot hash battle state",e);}
    }
    private void text(String s)throws IOException{byte[] b=s.getBytes(StandardCharsets.UTF_8);out.writeInt(b.length);out.write(b);}
    private void value(Object v)throws IOException,ReflectiveOperationException {
        if(v==null){out.writeByte(0);return;}
        Class<?> type=v.getClass();
        if(v instanceof Number||v instanceof Boolean||v instanceof Character||v instanceof String){out.writeByte(1);text(type.getName());text(v.toString());return;}
        if(v instanceof Enum){out.writeByte(2);text(((Enum<?>)v).getDeclaringClass().getName());text(((Enum<?>)v).name());return;}
        if(v instanceof Identifier){Identifier<?> id=(Identifier<?>)v;out.writeByte(3);text(id.cls.getName());text(id.pack);out.writeInt(id.id);return;}
        if(v instanceof Trait){out.writeByte(4);value(((Trait)v).id);return;}
        if(v instanceof BattleStatic){out.writeByte(5);text(type.getName());return;} // verified by the bundle/game fingerprint handshake
        Integer reference=seen.get(v);
        if(reference!=null){out.writeByte(6);out.writeInt(reference);return;}
        if(seen.size()>200000)throw new IOException("Battle state graph exceeds safety limit");
        seen.put(v,seen.size());out.writeByte(7);
        if(type.isArray()){out.writeByte(8);text(type.getComponentType().getName());int n=Array.getLength(v);out.writeInt(n);for(int i=0;i<n;i++)value(Array.get(v,i));return;}
        if(v instanceof Map){out.writeByte(9);Map<?,?> m=(Map<?,?>)v;out.writeInt(m.size());List<Object> keys=new ArrayList<>(m.keySet());keys.sort(Comparator.comparing(BattleDigest::key));for(Object k:keys){value(k);value(m.get(k));}return;}
        if(v instanceof Collection){out.writeByte(10);Collection<?> c=(Collection<?>)v;out.writeInt(c.size());List<?> values=new ArrayList<>(c);if(v instanceof Set && !(v instanceof LinkedHashSet) && !(v instanceof SortedSet))values.sort(Comparator.comparing(BattleDigest::key));for(Object item:values)value(item);return;}
        if(v instanceof EAnimI){
            out.writeByte(11);text(type.getName());out.writeInt(Float.floatToIntBits(((EAnimI)v).ind()));
            if(v instanceof EAnimD)value(((EAnimD<?>)v).type);
            return; // model/parts are presentation; ind is also used by death/revival timing
        }
        if(!(v instanceof BattleObj))throw new IOException("Unsupported mutable battle type: "+type.getName());
        out.writeByte(12);text(type.getName());
        for(Field f:fields(type)) {
            if(f.getDeclaringClass()==common.battle.StageBasis.class && VIEW_FIELDS.contains(f.getName()))continue;
            text(f.getDeclaringClass().getName()+"."+f.getName());value(f.get(v));
        }
        out.writeByte(13);
    }
    private static String key(Object v){
        if(v instanceof Identifier){Identifier<?> id=(Identifier<?>)v;return id.cls.getName()+":"+id.pack+":"+id.id;}
        if(v instanceof common.battle.entity.Entity)return "entity:"+((common.battle.entity.Entity)v).pvpEntityId;
        if(v instanceof common.util.unit.EneRand)return "random:"+((common.util.unit.EneRand)v).id;
        if(v instanceof Number||v instanceof String||v instanceof Enum)return String.valueOf(v);
        throw new IllegalStateException("Unstable unordered state key: "+v.getClass().getName());
    }
    private static List<Field> fields(Class<?> type){return FIELDS.computeIfAbsent(type,c->{
        List<Field> result=new ArrayList<>();
        for(Class<?> k=c;k!=Object.class&&k!=null;k=k.getSuperclass())for(Field f:k.getDeclaredFields())
            if(!Modifier.isStatic(f.getModifiers())&&!f.isSynthetic()&&!f.getName().equals("copy")&&!f.getName().startsWith(BattleObj.NONC)){f.setAccessible(true);result.add(f);}
        result.sort(Comparator.comparing(f->f.getDeclaringClass().getName()+"."+f.getName()));return result;
    });}
}
