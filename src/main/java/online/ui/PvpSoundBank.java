package online.ui;

import io.BCMusic;

import javax.sound.sampled.*;
import javax.swing.SwingUtilities;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

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

        final String file;
        final int fallbackMillis;
        Sound(String file,int fallbackMillis){this.file=file;this.fallbackMillis=fallbackMillis;}
    }

    private static final Set<Managed> ACTIVE=Collections.newSetFromMap(new IdentityHashMap<Managed,Boolean>());
    private static final Map<Sound,Managed> LOOPS=new EnumMap<>(Sound.class);

    private PvpSoundBank(){}

    public static String resourcePath(Sound sound){return ROOT+sound.file;}
    public static boolean resourceAvailable(Sound sound){return PvpSoundBank.class.getResource(resourcePath(sound))!=null;}

    public static synchronized void play(Sound sound){play(sound,null);}

    public static synchronized void play(Sound sound,Runnable after){
        if(!BCMusic.play||BCMusic.VOL_SE<=0){
            if(after!=null)SwingUtilities.invokeLater(after);
            return;
        }
        try{
            Managed managed=new Managed(open(sound),after,false);
            ACTIVE.add(managed);managed.start(sound.fallbackMillis);
        }catch(Exception e){
            System.err.println("BCU PvP sound: "+sound+" / "+e.getMessage());
            if(after!=null){
                Managed managed=new Managed(null,after,false);
                ACTIVE.add(managed);managed.startFallback(sound.fallbackMillis);
            }
        }
    }

    public static synchronized void startLoop(Sound sound){
        Managed existing=LOOPS.get(sound);
        if(existing!=null&&!existing.cancelled)return;
        stopLoop(sound);
        if(!BCMusic.play||BCMusic.VOL_SE<=0)return;
        try{
            Managed managed=new Managed(open(sound),null,true);
            ACTIVE.add(managed);LOOPS.put(sound,managed);managed.start(sound.fallbackMillis);
        }catch(Exception e){System.err.println("BCU PvP loop sound: "+sound+" / "+e.getMessage());}
    }

    public static synchronized void stopLoop(Sound sound){
        Managed managed=LOOPS.remove(sound);
        if(managed!=null)managed.cancel();
    }

    public static synchronized void refreshVolume(){
        for(Managed managed:new ArrayList<>(ACTIVE))managed.refreshVolume();
    }

    public static synchronized void stopAll(){
        for(Managed managed:new ArrayList<>(ACTIVE))managed.cancel();
        ACTIVE.clear();LOOPS.clear();
    }

    private static Clip open(Sound sound)throws Exception{
        InputStream source=PvpSoundBank.class.getResourceAsStream(resourcePath(sound));
        if(source==null)throw new IllegalStateException("Missing resource "+resourcePath(sound));
        try(BufferedInputStream in=new BufferedInputStream(source);
            AudioInputStream raw=AudioSystem.getAudioInputStream(in)){
            AudioFormat rf=raw.getFormat();
            int channels=rf.getChannels();float rate=rf.getSampleRate();
            AudioFormat pcmFormat=new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,rate,16,channels,channels*2,rate,false);
            try(AudioInputStream pcm=AudioSystem.getAudioInputStream(pcmFormat,raw)){
                Clip clip=(Clip)AudioSystem.getLine(new DataLine.Info(Clip.class,pcmFormat));
                clip.open(pcm);setVolume(clip);return clip;
            }
        }
    }

    private static void setVolume(Clip clip){
        if(clip==null)return;
        int value=BCMusic.play?Math.max(0,Math.min(100,BCMusic.VOL_SE)):0;
        if(clip.isControlSupported(BooleanControl.Type.MUTE))
            ((BooleanControl)clip.getControl(BooleanControl.Type.MUTE)).setValue(value==0);
        if(clip.isControlSupported(FloatControl.Type.MASTER_GAIN)){
            FloatControl gain=(FloatControl)clip.getControl(FloatControl.Type.MASTER_GAIN);
            float db=value==0?gain.getMinimum():20f*((float)Math.log10(value)-2f);
            gain.setValue(Math.max(gain.getMinimum(),Math.min(gain.getMaximum(),db)));
        }
    }

    private static final class Managed implements LineListener{
        private Clip clip;
        private final Runnable after;
        private final boolean loop;
        private javax.swing.Timer fallbackTimer;
        private boolean cancelled,completed;

        Managed(Clip clip,Runnable after,boolean loop){this.clip=clip;this.after=after;this.loop=loop;}

        void start(int fallbackMillis){
            if(clip==null){startFallback(fallbackMillis);return;}
            clip.addLineListener(this);setVolume(clip);clip.setFramePosition(0);
            if(loop)clip.loop(Clip.LOOP_CONTINUOUSLY);else clip.start();
        }

        void startFallback(int millis){
            fallbackTimer=new javax.swing.Timer(Math.max(1,millis),e->complete());
            fallbackTimer.setRepeats(false);fallbackTimer.start();
        }

        private void complete(){
            Runnable callback;
            synchronized(PvpSoundBank.class){
                if(cancelled||completed)return;
                completed=true;closeClip();ACTIVE.remove(this);LOOPS.values().remove(this);callback=after;
            }
            if(callback!=null)SwingUtilities.invokeLater(callback);
        }

        void refreshVolume(){setVolume(clip);}

        void cancel(){
            if(cancelled)return;cancelled=true;
            if(fallbackTimer!=null){fallbackTimer.stop();fallbackTimer=null;}
            closeClip();ACTIVE.remove(this);LOOPS.values().remove(this);
        }

        private void closeClip(){
            if(clip==null)return;
            try{clip.removeLineListener(this);clip.stop();clip.close();}catch(Exception ignored){}
            clip=null;
        }

        @Override public void update(LineEvent event){
            if(event.getType()==LineEvent.Type.STOP&&!loop&&!cancelled)complete();
        }
    }
}
