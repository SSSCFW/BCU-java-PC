package common.util;

import java.util.Random;

public class CopRand extends BattleObj {

	private long seed;
    private long visualSeed;
    public boolean deterministicVisuals;

	public CopRand(long s) {
		seed = s;
        visualSeed = s ^ 0x9e3779b97f4a7c15L;
	}

	public double irDouble() {
		if (!deterministicVisuals) return Math.random();
        visualSeed += 0x9e3779b97f4a7c15L;
        long x = visualSeed;
        x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
        x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
        return ((x ^ (x >>> 31)) >>> 11) * 0x1.0p-53;
	}

	public double nextDouble() {
		Random r = new Random(seed);
		seed = r.nextLong();
		return r.nextFloat();
	}

	public float nextFloat() {
		Random r = new Random(seed);
		seed = r.nextLong();
		return r.nextFloat();
	}

}
