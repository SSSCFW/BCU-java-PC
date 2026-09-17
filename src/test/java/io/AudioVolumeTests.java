package io;

import online.tests.Check;
import javax.sound.sampled.*;
import java.lang.reflect.*;

/** Real BCPlayer with an in-memory Clip/MASTER_GAIN; no physical audio device is needed. */
public final class AudioVolumeTests {
    private static final class Gain extends FloatControl {
        Gain(){super(FloatControl.Type.MASTER_GAIN,-80,6,0.1f,1,0,"dB");}
    }
    private static Clip clip(Gain gain){return (Clip)Proxy.newProxyInstance(Clip.class.getClassLoader(),new Class<?>[]{Clip.class},(p,m,a)->{
        switch(m.getName()){
            case "getControl":return gain;
            case "isControlSupported":return a[0]==FloatControl.Type.MASTER_GAIN;
            case "getMicrosecondLength":return 1_000_000L;
            case "hashCode":return System.identityHashCode(p);
            case "equals":return p==a[0];
            default:if(m.getReturnType()==boolean.class)return false;if(m.getReturnType()==int.class)return 0;if(m.getReturnType()==long.class)return 0L;return null;
        }
    });}
    public static void run()throws Exception {
        int bg=BCMusic.VOL_BG,se=BCMusic.VOL_SE,ui=BCMusic.VOL_UI;boolean play=BCMusic.play;
        Gain a=new Gain(),b=new Gain(),c=new Gain();BCPlayer effect=new BCPlayer(clip(a),20),operation=new BCPlayer(clip(b),19),music=new BCPlayer(clip(c),-1,0);
        try{
            BCMusic.play=true;effect.setVolume(20);operation.setVolume(20);music.setVolume(20);effect.start();operation.start();music.start();
            BCMusic.setSEVol(80);Check.that(Math.abs(a.getValue()-20*Math.log10(.8))<.01,"SE slider updates an already-playing hit sound, not just idle pool");
            Check.that(b.getValue() < -10,"SE slider must not change operation volume");
            BCMusic.setUIVol(60);Check.that(Math.abs(b.getValue()-20*Math.log10(.6))<.01,"UI slider updates already-playing notification");
            BCMusic.setBGVol(40);Check.that(Math.abs(c.getValue()-20*Math.log10(.4))<.01,"BGM slider updates active BGM");
            BCMusic.setUIVol(0);Check.equal(-80f,b.getValue(),"zero is finite minimum gain, never negative infinity");
            BCMusic.setUIVol(70);BCMusic.setSEVol(0);BCMusic.setSE(19);
            Field f=BCMusic.class.getDeclaredField("secall");f.setAccessible(true);Check.that(((boolean[])f.get(null))[19],"SE mute must not suppress separately-enabled UI notifications");
            BCMusic.class.getMethod("setSoundEnabled",boolean.class).invoke(null,false);
            Check.equal(-80f,b.getValue(),"master mute updates playing UI");Check.equal(-80f,c.getValue(),"master mute updates playing BGM");
            BCMusic.class.getMethod("setSoundEnabled",boolean.class).invoke(null,true);
            Check.that(b.getValue()>-10,"unmute restores configured UI volume");
            BCMusic.sounds.put(19,new java.util.ArrayDeque<>());
            operation.release();operation.update(new LineEvent(clip(b),LineEvent.Type.STOP,0));
            Check.that(BCMusic.sounds.get(19).isEmpty(),"late STOP callback must not return a released clip to the reusable pool");
            BCMusic.sounds.remove(19);
        }finally{effect.release();operation.release();music.release();BCMusic.play=play;BCMusic.setBGVol(bg);BCMusic.setSEVol(se);BCMusic.setUIVol(ui);}
    }
    public static void main(String[] args)throws Exception{run();System.out.println("Live audio volume tests passed");}
}
