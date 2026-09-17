package online.net.realtime;
import com.google.gson.JsonObject;
import online.net.Protocol;
import online.bundle.Hashes;
import online.sync.*;
import java.io.*;
import java.util.*;

/** Identical bounded payload on UDP and WebSocket. Application ACKs are cumulative tick ACKs. */
public final class RealtimeData {
    public long nextExpected, requestTick=-1, checkpointAck;
    public final SortedMap<Long,Integer> inputs=new TreeMap<>();
    public final List<ResolvedFrame> frames=new ArrayList<>();
    public final SortedMap<Long,String> checkpoints=new TreeMap<>();
    public byte[] toBytes() throws IOException {
        if(nextExpected<0 || requestTick< -1 || checkpointAck<0 || inputs.size()>5 || frames.size()>5 || checkpoints.size()>2)throw new IOException("Realtime payload limits");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeLong(nextExpected);out.writeLong(requestTick);out.writeLong(checkpointAck);
        out.writeByte(inputs.size());
        for(Map.Entry<Long,Integer> e:inputs.entrySet()) {
            if(e.getKey()<0 || !InputFrame.valid(e.getValue()))throw new IOException("Invalid input record");
            out.writeLong(e.getKey());out.writeInt(e.getValue());
        }
        out.writeByte(frames.size());
        for(ResolvedFrame f:frames) {
            out.writeLong(f.tick);out.writeByte(f.inputs.size());
            for(Map.Entry<Integer,Integer> e:f.inputs.entrySet()){out.writeInt(e.getKey());out.writeInt(e.getValue());}
        }
        out.writeByte(checkpoints.size());
        for(Map.Entry<Long,String> e:checkpoints.entrySet()) {
            if(e.getKey()<=0 || !Hashes.valid(e.getValue()))throw new IOException("Invalid checkpoint record");
            out.writeLong(e.getKey());for(int i=0;i<64;i+=2)out.writeByte(Integer.parseInt(e.getValue().substring(i,i+2),16));
        }
        out.flush();byte[] result=bytes.toByteArray();
        if(result.length>1200-UdpPacketCodec.OVERHEAD)throw new IOException("Realtime payload exceeds UDP MTU");
        return result;
    }
    public static RealtimeData fromBytes(byte[] bytes) throws IOException {
        if(bytes==null || bytes.length>1200-UdpPacketCodec.OVERHEAD)throw new IOException("Realtime payload too large");
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes));RealtimeData d=new RealtimeData();
        try {
            d.nextExpected=in.readLong();d.requestTick=in.readLong();d.checkpointAck=in.readLong();
            int count=count(in,5);
            for(int i=0;i<count;i++){long t=in.readLong();int m=in.readInt();if(d.inputs.put(t,m)!=null)throw new IOException("Duplicate input record");}
            count=count(in,5);Set<Long> seen=new HashSet<>();
            for(int i=0;i<count;i++){
                long tick=in.readLong();int n=count(in,8);Map<Integer,Integer> inputs=new TreeMap<>();
                for(int j=0;j<n;j++)if(inputs.put(in.readInt(),in.readInt())!=null)throw new IOException("Duplicate participant");
                if(!seen.add(tick))throw new IOException("Duplicate frame record");d.frames.add(new ResolvedFrame(tick,inputs));
            }
            count=count(in,2);
            for(int i=0;i<count;i++){long t=in.readLong();byte[] hash=new byte[32];in.readFully(hash);if(d.checkpoints.put(t,Hashes.hex(hash))!=null)throw new IOException("Duplicate checkpoint");}
            if(in.available()!=0)throw new IOException("Trailing realtime bytes");d.toBytes();return d;
        }catch(IllegalArgumentException e){throw new IOException("Malformed realtime payload",e);}
    }
    private static int count(DataInputStream in,int max)throws IOException{int n=in.readUnsignedByte();if(n>max)throw new IOException("Too many realtime records");return n;}
    public JsonObject control(String type) {
        try{JsonObject out=Protocol.message(type);out.addProperty("payload",Base64.getEncoder().encodeToString(toBytes()));return out;}
        catch(IOException e){throw new IllegalArgumentException(e);}
    }
    public static RealtimeData fromControl(JsonObject object) throws IOException {
        try{return fromBytes(Base64.getDecoder().decode(Protocol.string(object,"payload",1600)));}
        catch(IllegalArgumentException e){throw new IOException("Malformed realtime envelope",e);}
    }
}
