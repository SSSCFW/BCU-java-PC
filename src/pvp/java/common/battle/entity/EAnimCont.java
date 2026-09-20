package common.battle.entity;

import common.system.P;
import common.system.fake.FakeGraphics;
import common.util.BattleObj;
import common.util.anim.EAnimD;

/**
 * PvP-safe animation container.
 *
 * Some optional effect assets can legitimately be unavailable for a synced/custom
 * unit.  The upstream container accepts a null EAnimD but dereferences it
 * unconditionally on the next simulation tick, which aborts the entire 30 TPS
 * lockstep loop.  A missing presentation effect must not affect battle logic.
 */
public class EAnimCont extends BattleObj {

    public final float pos;
    public final int layer;
    private final EAnimD<?> anim;
    public final float offsetY;

    public EAnimCont(float p, int lay, EAnimD<?> ead) {
        pos = p;
        layer = lay;
        anim = ead;
        offsetY = 0f;
    }

    public EAnimCont(float p, int lay, EAnimD<?> ead, float offsetY) {
        pos = p;
        layer = lay;
        anim = ead;
        this.offsetY = offsetY;
    }

    /**
     * Missing visual effects are treated as already finished so StageBasis removes
     * the container normally instead of stopping synchronized gameplay.
     */
    public boolean done() {
        return anim == null || anim.done();
    }

    public void draw(FakeGraphics gra, P p, float psiz) {
        if (anim == null)
            return;
        p.y += offsetY * psiz;
        anim.draw(gra, p, psiz);
    }

    public void update() {
        if (anim != null)
            anim.update(false);
    }
}
