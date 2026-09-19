package io;

import javax.sound.sampled.Clip;
import javax.sound.sampled.BooleanControl;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineEvent.Type;
import javax.sound.sampled.LineListener;
import java.util.ArrayDeque;

public class BCPlayer implements LineListener {
	private static final int FACTOR = 20;
    static final int BACKGROUND=0, EFFECT=1, OPERATION=2;
    private static final Set<BCPlayer> LIVE=ConcurrentHashMap.newKeySet();
    private final int category;
    static void refreshVolumes(){for(BCPlayer p:LIVE)p.setVolume(!BCMusic.play?0:p.category==BACKGROUND?BCMusic.VOL_BG:p.category==OPERATION?BCMusic.VOL_UI:BCMusic.VOL_SE);}
    static void releaseAll(){for(BCPlayer p:LIVE.toArray(new BCPlayer[0]))p.release();}

	private static float getVol(int vol) {
		return FACTOR * ((float) Math.log10(vol) - 2);
	}

	private final int ind;
	private Clip c;
	private FloatControl master;

	private volatile boolean released;
	private boolean rewinding = false;
	private boolean playing = false;

	protected BCPlayer(Clip c, int ind) {
		this.ind = ind;
        this.category=BCMusic.isUiSound(ind)?OPERATION:EFFECT;
		this.c = c;
		this.c.addLineListener(this);
		this.master = c.isControlSupported(FloatControl.Type.MASTER_GAIN) ? (FloatControl)c.getControl(FloatControl.Type.MASTER_GAIN) : null;
        LIVE.add(this);
	}

	protected BCPlayer(Clip c, int ind, long loop) {
		this.ind = ind;
        this.category=BACKGROUND;
		this.c = c;
		this.c.addLineListener(this);
		this.master = c.isControlSupported(FloatControl.Type.MASTER_GAIN) ? (FloatControl)c.getControl(FloatControl.Type.MASTER_GAIN) : null;
        LIVE.add(this);

		if (loop > 0 && loop * 1000 < c.getMicrosecondLength()) {
			c.setLoopPoints(milliToFrame(loop), -1);
		} else if (loop * 1000 >= c.getMicrosecondLength()) {
			c.loop(0);
		}
	}

	public boolean isPlaying() {
		return playing;
	}

	public void stop() {
		playing = false;
		if (c != null) {
			c.stop();
		}
	}

	@Override
	public void update(LineEvent event) {
		if (event.getType() == Type.STOP && !released) {
			playing = false;
			stop();

			if (ind >= 0 && ind != 8 && ind != 9 && ind != 20 && ind != 21 && ind != 22) {
				synchronized (BCMusic.class) {
					ArrayDeque<BCPlayer> players = BCMusic.sounds.get(ind);

					if (players != null && !released) {
						players.push(this);
					}
				}
			}
		}
	}

	protected void release() {
        if(released)return;released=true;LIVE.remove(this);
        if(c!=null)c.removeLineListener(this);
		if (playing) {
			stop();
		}
		playing = false;
		rewinding = false;
		if (c != null) {
			c.close();
			c = null;
		}
		master = null;
	}

	protected void rewind() {
		if (rewinding) {
			return;
		}

		rewinding = true;
		c.setFramePosition(0);
		rewinding = false;
	}

	protected void setLineListener(LineListener l) {
		c.addLineListener(l);
	}

	protected void setVolume(int vol) {
        if(c==null)return;
        int value=Math.max(0,Math.min(100,vol));
        if(c.isControlSupported(BooleanControl.Type.MUTE))((BooleanControl)c.getControl(BooleanControl.Type.MUTE)).setValue(value==0);
        if(master!=null)master.setValue(value==0?master.getMinimum():Math.max(master.getMinimum(),Math.min(master.getMaximum(),getVol(value))));
	}

	protected void start() {
		if (playing) {
			return;
		}

		playing = true;
		c.start();
	}

	private int milliToFrame(long milli) {
		return (int) (c.getFormat().getFrameRate() * milli / 1000.0);
	}
}
