package online.ui;

import javax.sound.sampled.*;
import javax.sound.sampled.spi.MixerProvider;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in Java Sound device: decodes real PCM but records output instead of requiring speakers. */
public final class RecordingMixerProvider extends MixerProvider {
    private static final Mixer.Info INFO=new Mixer.Info("BCU regression PCM","BCU","Test-only PCM output","1"){};
    public static volatile boolean enabled,dropStop,failOpen;
    public static volatile int openDelayMillis;
    public static final List<Recording> opened=new CopyOnWriteArrayList<>();
    public static final List<Recording> started=new CopyOnWriteArrayList<>();
    private static final ScheduledExecutorService TIMER=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"test-audio-clock");t.setDaemon(true);return t;});
    public static void enable(){enabled=true;System.setProperty("javax.sound.sampled.Clip",RecordingMixerProvider.class.getName()+"#"+INFO.getName());}
    public static void reset(){for(Recording r:opened)r.close();opened.clear();started.clear();dropStop=false;failOpen=false;openDelayMillis=0;}
    public static byte[] pcm(PvpSoundBank.Sound sound)throws Exception{
        try(InputStream source=PvpSoundBank.class.getResourceAsStream(PvpSoundBank.resourcePath(sound));
            AudioInputStream raw=AudioSystem.getAudioInputStream(new BufferedInputStream(source))){
            AudioFormat f=raw.getFormat();
            AudioFormat pcm=new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,f.getSampleRate(),16,f.getChannels(),f.getChannels()*2,f.getSampleRate(),false);
            try(AudioInputStream in=AudioSystem.getAudioInputStream(pcm,raw);ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)if(n>0)out.write(b,0,n);return out.toByteArray();
            }
        }
    }
    public static long count(PvpSoundBank.Sound sound)throws Exception{
        byte[] data=pcm(sound);return started.stream().filter(r->r.loop==(sound==PvpSoundBank.Sound.ROULETTE_SPIN)&&Arrays.equals(r.pcm,data)).count();
    }
    public Mixer.Info[] getMixerInfo(){return enabled?new Mixer.Info[]{INFO}:new Mixer.Info[0];}
    public Mixer getMixer(Mixer.Info info){
        if(!enabled||(info!=null&&info!=INFO))throw new IllegalArgumentException("Test mixer disabled");
        return (Mixer)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Mixer.class},(p,m,a)->{
            switch(m.getName()){
                case "getMixerInfo":return INFO;
                case "isLineSupported":return a[0] instanceof DataLine.Info&&((DataLine.Info)a[0]).getLineClass()==Clip.class;
                case "getLine":return new Recording().clip;
                case "getMaxLines":return AudioSystem.NOT_SPECIFIED;
                case "getSourceLineInfo":return new Line.Info[]{new DataLine.Info(Clip.class,(AudioFormat)null)};
                case "getTargetLineInfo":return new Line.Info[0];
                case "getSourceLines":case "getTargetLines":return new Line[0];
                case "getControls":return new Control[0];
                case "getLineInfo":return new Line.Info(Mixer.class);
                case "isOpen":return true;
                default:return zero(m.getReturnType());
            }
        });
    }
    private static Object zero(Class<?> c){if(c==boolean.class)return false;if(c==int.class)return 0;if(c==long.class)return 0L;if(c==float.class)return 0f;return null;}
    public static final class Recording implements InvocationHandler {
        public final Clip clip=(Clip)Proxy.newProxyInstance(Clip.class.getClassLoader(),new Class<?>[]{Clip.class},this);
        private final List<LineListener> listeners=new CopyOnWriteArrayList<>();
        private final FloatControl gain=new FloatControl(FloatControl.Type.MASTER_GAIN,-80f,6f,.1f,1,0f,"dB"){};
        public AudioFormat format;public byte[] pcm=new byte[0];public volatile boolean running,closed,loop;
        public long startNanos;private ScheduledFuture<?> stop;
        public long durationMillis(){return Math.max(1L,(long)Math.ceil(pcm.length*1000.0/(format.getFrameSize()*format.getSampleRate())));}
        public float gain(){return gain.getValue();}
        public Object invoke(Object p,Method m,Object[] a)throws Exception{
            switch(m.getName()){
                case "open":
                    if(openDelayMillis>0)Thread.sleep(openDelayMillis);
                    if(failOpen)throw new LineUnavailableException("test device unavailable");
                    if(a[0] instanceof AudioInputStream){AudioInputStream in=(AudioInputStream)a[0];format=in.getFormat();ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)if(n>0)out.write(b,0,n);pcm=out.toByteArray();}
                    else {format=(AudioFormat)a[0];pcm=Arrays.copyOfRange((byte[])a[1],(Integer)a[2],(Integer)a[2]+(Integer)a[3]);}
                    opened.add(this);return null;
                case "getFormat":return format;
                case "getFrameLength":return pcm.length/format.getFrameSize();
                case "getMicrosecondLength":return (long)(pcm.length*1000000.0/(format.getFrameSize()*format.getSampleRate()));
                case "getFramePosition":return (int)position();
                case "getLongFramePosition":return position();
                case "getMicrosecondPosition":return (long)(position()*1000000.0/format.getSampleRate());
                case "setFramePosition":case "setMicrosecondPosition":return null;
                case "getControl":return gain;
                case "isControlSupported":return a[0]==FloatControl.Type.MASTER_GAIN;
                case "getControls":return new Control[]{gain};
                case "getLineInfo":return new DataLine.Info(Clip.class,format);
                case "addLineListener":listeners.add((LineListener)a[0]);return null;
                case "removeLineListener":listeners.remove(a[0]);return null;
                case "isOpen":return !closed;
                case "isRunning":case "isActive":return running;
                case "loop":loop=(Integer)a[0]==Clip.LOOP_CONTINUOUSLY;start();return null;
                case "start":start();return null;
                case "stop":boolean wasRunning=running;running=false;if(stop!=null)stop.cancel(false);if(wasRunning)event(LineEvent.Type.STOP);return null;
                case "close":close();return null;
                case "hashCode":return System.identityHashCode(p);
                case "equals":return p==a[0];
                case "toString":return "PCM["+pcm.length+"]";
                default:return zero(m.getReturnType());
            }
        }
        private long position(){if(format==null)return 0L;long f=pcm.length/format.getFrameSize();return startNanos==0?0:Math.min(f,(System.nanoTime()-startNanos)*(long)format.getSampleRate()/1000000000L);}
        private void start(){if(running)return;running=true;startNanos=System.nanoTime();started.add(this);event(LineEvent.Type.START);if(!loop){boolean omit=dropStop;stop=TIMER.schedule(()->{running=false;if(!omit)event(LineEvent.Type.STOP);},durationMillis(),TimeUnit.MILLISECONDS);}}
        private void event(LineEvent.Type type){for(LineListener l:listeners)l.update(new LineEvent(clip,type,position()));}
        public void close(){closed=true;running=false;if(stop!=null)stop.cancel(false);}
    }
}
