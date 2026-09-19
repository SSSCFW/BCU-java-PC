package online.ui;

import io.BCMusic;
import javax.sound.sampled.*;
import javax.swing.SwingUtilities;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Local presentation audio; device/decoder work never blocks Swing or the lockstep pump. */
public final class PvpSoundBank {
    private static final String ROOT="/online/pvp/audio/";
    public enum Sound {
        ROULETTE_CHARGE("roulette_charge.ogg",330),
        ROULETTE_MAX("roulette_max.ogg",850),
        ROULETTE_START("roulette_start.ogg",350),
        ROULETTE_SPIN("roulette_spin.ogg",560),
        ROULETTE_CONFIRM("roulette_confirm.ogg",1870),
        OPPONENT_ROULETTE_START("roulette_opponent_start.ogg",590),
        OPPONENT_ROULETTE_CONFIRM("roulette_opponent_confirm.ogg",1230),
        BATTLE_END("battle_end.ogg",1100);
        final String file;final int fallbackMillis;
        Sound(String file,int millis){this.file=file;fallbackMillis=millis;}
    }
    private static final ExecutorService AUDIO=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"pvp-audio");t.setDaemon(true);return t;});
    private static final Map<Sound,Pcm> CACHE=new EnumMap<>(Sound.class); // audio worker only
    private static final Set<Managed> ACTIVE=Collections.newSetFromMap(new IdentityHashMap<Managed,Boolean>());
    private static final Map<Sound,Managed> LOOPS=new EnumMap<>(Sound.class);
    private static long generation;
    private PvpSoundBank(){}
    public static String resourcePath(Sound sound){return ROOT+sound.file;}
    public static boolean resourceAvailable(Sound sound){return PvpSoundBank.class.getResource(resourcePath(sound))!=null;}
    public static void preload(){AUDIO.execute(()->{for(Sound sound:Sound.values())try{decode(sound);}catch(Exception e){diagnostic(sound,e);}});}
    public static void play(Sound sound){play(sound,null);}
    public static void play(Sound sound,Runnable after){
        Managed managed;
        synchronized(PvpSoundBank.class){managed=new Managed(sound,after,false,generation);ACTIVE.add(managed);}
        if(audible()){
            // Also bounds startup failure: a broken audio device cannot strand the result UI.
            managed.arm(sound.fallbackMillis+2000);managed.requestOpen();
        }else managed.arm(sound.fallbackMillis); // preserve the ending even with sound disabled
    }
    public static void startLoop(Sound sound){
        Managed managed;
        synchronized(PvpSoundBank.class){
            if(LOOPS.containsKey(sound))return;
            managed=new Managed(sound,null,true,generation);ACTIVE.add(managed);LOOPS.put(sound,managed);
        }
        if(audible())managed.requestOpen();
    }
    public static void stopLoop(Sound sound){
        Managed managed;synchronized(PvpSoundBank.class){managed=LOOPS.get(sound);}
        if(managed!=null)managed.cancel();
    }
    public static void refreshVolume(){
        Managed[] all;synchronized(PvpSoundBank.class){all=ACTIVE.toArray(new Managed[0]);}
        for(Managed m:all){
            if(m.loop&&m.clip==null&&audible())m.requestOpen();
            AUDIO.execute(()->{if(!m.cancelled&&!m.completed)setVolume(m.clip);});
        }
    }
    public static void stopAll(){
        Managed[] all;
        synchronized(PvpSoundBank.class){generation++;all=ACTIVE.toArray(new Managed[0]);}
        for(Managed m:all)m.cancel();
    }
    private static boolean audible(){return BCMusic.play&&BCMusic.VOL_SE>0;}
    private static void diagnostic(Sound sound,Exception e){System.err.println("BCU PvP sound: "+sound+" / "+e.getMessage());}
    private static Pcm decode(Sound sound)throws Exception{
        Pcm cached=CACHE.get(sound);if(cached!=null)return cached;
        InputStream resource=PvpSoundBank.class.getResourceAsStream(resourcePath(sound));
        if(resource==null)throw new IOException("Missing resource "+resourcePath(sound));
        try(BufferedInputStream in=new BufferedInputStream(resource);AudioInputStream raw=AudioSystem.getAudioInputStream(in)){
            AudioFormat rf=raw.getFormat();int channels=rf.getChannels();float rate=rf.getSampleRate();
            AudioFormat format=new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,rate,16,channels,channels*2,rate,false);
            try(AudioInputStream decoded=AudioSystem.getAudioInputStream(format,raw);ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int n;
                while((n=decoded.read(buffer))!=-1){if(n>0)out.write(buffer,0,n);}
                byte[] data=out.toByteArray();if(data.length==0)throw new IOException("Empty decoded sound "+sound);
                Pcm pcm=new Pcm(format,data);CACHE.put(sound,pcm);return pcm;
            }
        }
    }
    private static final class Pcm {
        final AudioFormat format;final byte[] data;final int millis;
        Pcm(AudioFormat format,byte[] data){this.format=format;this.data=data;millis=Math.max(1,(int)Math.ceil(1000.0*data.length/(format.getFrameSize()*format.getFrameRate())));}
    }
    private static void setVolume(Clip clip){
        if(clip==null||!clip.isOpen())return;
        int value=BCMusic.play?Math.max(0,Math.min(100,BCMusic.VOL_SE)):0;
        if(clip.isControlSupported(BooleanControl.Type.MUTE))((BooleanControl)clip.getControl(BooleanControl.Type.MUTE)).setValue(value==0);
        if(clip.isControlSupported(FloatControl.Type.MASTER_GAIN)){
            FloatControl gain=(FloatControl)clip.getControl(FloatControl.Type.MASTER_GAIN);
            float db=value==0?gain.getMinimum():20f*(float)Math.log10(value/100.0);
            gain.setValue(Math.max(gain.getMinimum(),Math.min(gain.getMaximum(),db)));
        }
    }
    private static void close(Clip clip){
        if(clip==null)return;
        try{clip.stop();}catch(Exception ignored){}
        try{clip.close();}catch(Exception ignored){}
    }
    private static final class Managed implements LineListener {
        final Sound sound;final Runnable after;final boolean loop;final long epoch;
        volatile Clip clip;volatile boolean cancelled,completed;boolean opening;
        volatile long startedNanos;volatile int durationMillis;
        private javax.swing.Timer timer; // EDT only
        Managed(Sound sound,Runnable after,boolean loop,long epoch){this.sound=sound;this.after=after;this.loop=loop;this.epoch=epoch;}
        void requestOpen(){
            synchronized(PvpSoundBank.class){if(cancelled||completed||opening||clip!=null)return;opening=true;}
            AUDIO.execute(this::openAndStart);
        }
        void openAndStart(){
            Clip opened=null;
            try{
                if(cancelled||completed)return;
                Pcm pcm=decode(sound);
                if(cancelled||completed)return;
                opened=(Clip)AudioSystem.getLine(new DataLine.Info(Clip.class,pcm.format));
                opened.open(pcm.format,pcm.data,0,pcm.data.length);
                if(cancelled||completed){close(opened);return;}
                clip=opened;durationMillis=pcm.millis;setVolume(opened);opened.addLineListener(this);
                startedNanos=System.nanoTime();
                if(loop)opened.loop(Clip.LOOP_CONTINUOUSLY);else opened.start();
                if(cancelled||completed){release();return;}
                if(!loop)arm(pcm.millis+250); // natural STOP wins; this only recovers missing notifications
            }catch(Exception e){
                if(opened!=null){opened.removeLineListener(this);close(opened);}clip=null;
                if(!cancelled&&!completed){diagnostic(sound,e);if(!loop)arm(sound.fallbackMillis);}
            }finally{synchronized(PvpSoundBank.class){opening=false;}}
        }
        void arm(int millis){
            SwingUtilities.invokeLater(()->{
                if(cancelled||completed)return;
                if(timer!=null)timer.stop();
                timer=new javax.swing.Timer(Math.max(1,millis),e->complete());timer.setRepeats(false);timer.start();
            });
        }
        private void forget(){
            ACTIVE.remove(this);if(LOOPS.get(sound)==this)LOOPS.remove(sound);
        }
        private void complete(){
            synchronized(PvpSoundBank.class){if(cancelled||completed)return;completed=true;forget();}
            if(timer!=null){timer.stop();timer=null;}
            AUDIO.execute(this::release);
            if(after!=null)SwingUtilities.invokeLater(()->{
                synchronized(PvpSoundBank.class){if(cancelled||epoch!=generation)return;}
                after.run();
            });
        }
        void cancel(){
            synchronized(PvpSoundBank.class){if(cancelled)return;cancelled=true;forget();}
            SwingUtilities.invokeLater(()->{if(timer!=null){timer.stop();timer=null;}});
            AUDIO.execute(this::release);
        }
        void release(){Clip current=clip;clip=null;if(current!=null){current.removeLineListener(this);close(current);}}
        @Override public void update(LineEvent event){
            if(event.getType()!=LineEvent.Type.STOP||loop||cancelled||completed)return;
            // Ignore a queued STOP from opening/rewinding a device, before real playback.
            if(event.getFramePosition()<=0&&System.nanoTime()-startedNanos<TimeUnit.MILLISECONDS.toNanos(durationMillis))return;
            SwingUtilities.invokeLater(this::complete);
        }
    }
}
