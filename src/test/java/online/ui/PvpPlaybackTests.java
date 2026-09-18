package online.ui;

import io.BCMusic;
import online.tests.Check;
import javax.swing.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Exercise the actual sound bank and actual Vorbis decoder with an opt-in PCM output device. */
public final class PvpPlaybackTests {
    private static void edt(Runnable work)throws Exception{SwingUtilities.invokeAndWait(work);}
    private static void await(java.util.function.BooleanSupplier condition,String reason)throws Exception{
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(!condition.getAsBoolean()&&System.nanoTime()<until)Thread.sleep(10);
        Check.that(condition.getAsBoolean(),reason);
    }
    public static void run()throws Exception{for(String mode:new String[]{"decode","loop","missing-stop","mute","slow-open","cancel","unavailable","initial-mute","zero-volume"})test(mode);}
    private static void test(String mode)throws Exception{
        boolean enabled=BCMusic.play;int volume=BCMusic.VOL_SE;
        RecordingMixerProvider.enable();RecordingMixerProvider.reset();BCMusic.music=null;BCMusic.play=true;BCMusic.VOL_SE=70;
        try{
            switch(mode){
                case "decode":
                    for(PvpSoundBank.Sound sound:PvpSoundBank.Sound.values()){
                        int count=RecordingMixerProvider.started.size();
                        PvpSoundBank.play(sound);
                        await(()->RecordingMixerProvider.started.size()==count+1,"starts supplied OGG "+sound);
                        RecordingMixerProvider.Recording clip=RecordingMixerProvider.started.get(count);
                        Check.that(clip.pcm.length>1000,"decodes complete nonempty PCM "+sound);
                        int peak=0;for(int i=0;i+1<clip.pcm.length;i+=2)peak=Math.max(peak,Math.abs((short)((clip.pcm[i]&255)|(clip.pcm[i+1]<<8))));
                        Check.that(peak>500,"non-silent PCM reaches output "+sound);
                        PvpSoundBank.stopAll();await(()->clip.closed,"release "+sound);
                    }break;
                case "loop":
                    PvpSoundBank.startLoop(PvpSoundBank.Sound.ROULETTE_SPIN);PvpSoundBank.startLoop(PvpSoundBank.Sound.ROULETTE_SPIN);
                    await(()->RecordingMixerProvider.started.size()==1,"one spin loop only");
                    RecordingMixerProvider.Recording spinning=RecordingMixerProvider.started.get(0);
                    Thread.sleep(700);Check.that(spinning.loop&&spinning.running,"spin repeats beyond the short source duration");
                    PvpSoundBank.stopLoop(PvpSoundBank.Sound.ROULETTE_SPIN);await(()->spinning.closed,"confirm stops and releases spin");break;
                case "missing-stop":
                    RecordingMixerProvider.dropStop=true;AtomicInteger count=new AtomicInteger();
                    PvpSoundBank.play(PvpSoundBank.Sound.BATTLE_END,count::incrementAndGet);
                    await(()->count.get()==1,"missing native STOP must still finish the battle ending");
                    Thread.sleep(200);Check.equal(1,count.get(),"end callback occurs exactly once");break;
                case "mute":
                    PvpSoundBank.startLoop(PvpSoundBank.Sound.ROULETTE_SPIN);
                    await(()->RecordingMixerProvider.started.size()==1,"spin starts");
                    RecordingMixerProvider.Recording live=RecordingMixerProvider.started.get(0);
                    BCMusic.setSoundEnabled(false);await(()->live.gain()==-80f,"master mute affects roulette clips, not only BCPlayer");
                    BCMusic.setSoundEnabled(true);await(()->live.gain()>-10f,"unmute resumes live loop gain");break;
                case "slow-open":
                    RecordingMixerProvider.openDelayMillis=300;long start=System.nanoTime();
                    edt(()->PvpSoundBank.play(PvpSoundBank.Sound.ROULETTE_START));
                    Check.that(System.nanoTime()-start<TimeUnit.MILLISECONDS.toNanos(180),"opening an audio device must not block Swing/lockstep");
                    await(()->RecordingMixerProvider.started.size()==1,"slow device eventually starts sound");break;
                case "cancel":
                    RecordingMixerProvider.openDelayMillis=300;AtomicInteger callback=new AtomicInteger();
                    PvpSoundBank.play(PvpSoundBank.Sound.BATTLE_END,callback::incrementAndGet);PvpSoundBank.stopAll();
                    Thread.sleep(1700);Check.equal(0,callback.get(),"leaving cannot show a stale result or restart its BGM");
                    Check.that(RecordingMixerProvider.opened.stream().allMatch(r->r.closed),"cancelled open releases its device");break;
                case "unavailable":
                    RecordingMixerProvider.failOpen=true;AtomicInteger failures=new AtomicInteger();
                    PvpSoundBank.play(PvpSoundBank.Sound.BATTLE_END,failures::incrementAndGet);
                    Thread.sleep(200);Check.equal(0,failures.get(),"unavailable device still presents the ending");
                    await(()->failures.get()==1,"unavailable device cannot strand result");break;
                case "initial-mute":
                    BCMusic.setSoundEnabled(false);PvpSoundBank.startLoop(PvpSoundBank.Sound.ROULETTE_SPIN);
                    Thread.sleep(100);Check.equal(0,RecordingMixerProvider.started.size(),"muted loop has no device output");
                    BCMusic.setSoundEnabled(true);await(()->RecordingMixerProvider.started.size()==1,"unmuting while spinning starts the missing loop");break;
                case "zero-volume":
                    BCMusic.VOL_SE=0;AtomicInteger muted=new AtomicInteger();
                    PvpSoundBank.play(PvpSoundBank.Sound.BATTLE_END,muted::incrementAndGet);
                    Thread.sleep(200);Check.equal(0,muted.get(),"SE zero preserves ending duration");
                    await(()->muted.get()==1,"SE zero still completes ending");
                    Check.equal(0,RecordingMixerProvider.started.size(),"SE zero is silent");break;
                default:throw new AssertionError(mode);
            }
            System.out.println("PLAYBACK_OK "+mode);
        }finally{PvpSoundBank.stopAll();Thread.sleep(50);RecordingMixerProvider.reset();RecordingMixerProvider.enabled=false;BCMusic.play=enabled;BCMusic.VOL_SE=volume;System.clearProperty("javax.sound.sampled.Clip");}
    }
    public static void main(String[] args)throws Exception{if(args.length==0)run();else test(args[0]);}
}
