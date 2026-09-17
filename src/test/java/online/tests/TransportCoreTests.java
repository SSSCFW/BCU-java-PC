package online.tests;
import online.net.config.ServerConfig;
import online.net.core.*;
import online.net.bundle.BundleStore;
import online.sync.*;
import online.bundle.Hashes;
import java.util.*;
import java.nio.file.*;
import java.io.*;

public final class TransportCoreTests {
    public static void run() throws Exception {
        List<Integer> ids=Arrays.asList(91,3,105,8,10,41,55,7);
        LockstepState lock=new LockstepState(ids,32);
        for(int id:ids) lock.input(id,1,2);
        Check.equal(null,lock.resolve(),"out-of-order input cannot skip a tick");
        for(int id:ids) lock.input(id,0,1);
        ResolvedFrame first=lock.resolve(); Check.equal(0L,first.tick,"tick zero resolved");
        Check.equal(8,first.inputs.size(),"eight participants supported without two-slot code");
        Check.equal(2L,lock.nextInput(91),"application ack is contiguous");
        Check.equal(1L,lock.resolve().tick,"buffered future tick resolves");
        lock.input(91,0,1);
        Check.rejects(()->lock.input(91,0,2),"conflicting old input rejected");
        Check.rejects(()->lock.input(999,2,0),"foreign participant rejected");
        Check.rejects(()->lock.input(91,99,0),"future inputs bounded");
        String hash=Hashes.sha256(new byte[]{1});
        for(int t=2;t<120;t++) { for(int id:ids)lock.input(id,t,0); Check.that(lock.resolve()!=null,"tick advances with complete input"); }
        for(int id:ids)lock.input(id,120,0);
        Check.equal(null,lock.resolve(),"checkpoint barrier cannot run away");
        for(int id:ids) {lock.checkpoint(id,120,hash);lock.checkpoint(id,60,hash);}
        Check.that(lock.resolve()!=null,"out-of-order checkpoints recover contiguously");
        Check.rejects(()->lock.checkpoint(91,60,Hashes.sha256(new byte[]{2})),"late checkpoint mismatch rejected");
        FrameBuffer buffer=new FrameBuffer(ids);
        ResolvedFrame second=new ResolvedFrame(1,first.inputs);
        Check.equal(0,buffer.accept(second).size(),"future frame buffered");
        Check.equal(2,buffer.accept(first).size(),"hole recovers contiguous frames");
        Check.equal(0,buffer.accept(first).size(),"duplicate frame idempotent");
        Map<Integer,Integer> changed=new TreeMap<>(first.inputs);changed.put(91,2);
        Check.rejects(()->buffer.accept(new ResolvedFrame(0,changed)),"conflicting frame rejected");
        Check.rejects(()->buffer.accept(new ResolvedFrame(500,first.inputs)),"frame buffer bounded");
        InputHistory history=new InputHistory();for(int i=0;i<20;i++)history.add(i,i);
        Check.equal(new TreeSet<>(Arrays.asList(16L,17L,18L,19L)),new TreeSet<>(history.batch(-1).keySet()),"four-tick redundancy");
        Check.that(history.batch(2).containsKey(2L),"explicit old hole appended");
        Check.equal(5,history.batch(2).size(),"history packet is bounded to five records");
        Check.rejects(()->history.add(19,999),"input immutable after scheduling");
        Properties settings=new Properties();settings.setProperty("inputDelayTicks","8");
        Check.equal(8,ServerConfig.from(settings).inputDelayTicks,"negotiated delay configurable");
        settings.setProperty("inputDelayTicks","9");Check.rejects(()->ServerConfig.from(settings),"delay bounded");
        settings.clear();settings.setProperty("maxParticipantsPerRoom","9");Check.rejects(()->ServerConfig.from(settings),"capacity bounded");
        settings.clear();settings.setProperty("unknown","1");Check.rejects(()->ServerConfig.from(settings),"unknown setting rejected");
        Duel1v1Mode duel=new Duel1v1Mode();GameMode.Seat left=duel.assign(Collections.emptyList(),"left");
        GameMode.Seat right=duel.assign(Collections.singletonList(left),"left");
        Check.equal("right",right.name,"duel seats are opposite");
        Check.rejects(()->duel.assign(Arrays.asList(left,right),"left"),"production mode still two participants");
        byte[] bytes="cache data".getBytes("UTF-8");String h=Hashes.sha256(bytes);Path root;
        try(BundleStore store=new BundleStore(1024,8)) {
            root=store.root();Check.equal(BundleStore.Offer.UPLOAD,store.offer(h,bytes.length),"first upload required");
            Check.equal(BundleStore.Offer.WAIT,store.offer(h,bytes.length),"concurrent same hash waits");
            store.append(h,bytes);store.finish(h);
            Check.equal(BundleStore.Offer.CACHED,store.offer(h,bytes.length),"verified cache deduplicated");
            Check.rejects(()->store.offer(h,bytes.length+1),"same hash inconsistent size rejected");
            try(InputStream in=store.open(h)) { Check.equal((int)bytes[0],in.read(),"cache bytes accessible"); }
            store.open(h); // close must close leaked reader before deleting files on Windows
        }
        Check.that(!Files.exists(root),"cache removed on room close");
    }
}
