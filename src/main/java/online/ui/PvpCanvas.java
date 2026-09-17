package online.ui;

import common.battle.*;
import common.battle.attack.ContAb;
import common.battle.entity.*;
import common.CommonStatic;
import common.system.P;
import common.system.fake.FakeGraphics;
import common.system.fake.FakeTransform;
import page.battle.BattleBox;
import utilpc.awt.FG2D;
import javax.swing.*;
import java.awt.*;
import java.util.*;

/** Renders only disposable display snapshots; never advances the canonical simulation. */
public final class PvpCanvas extends JPanel {
    private static final long serialVersionUID=1L;
    private PvpStageBasis view;
    private final Map<Long,Float> previous=new HashMap<>();
    private long published;
    private boolean halfAdvanced;
    private int fps=60;
    private String leftName="LEFT",rightName="RIGHT";
    public PvpCanvas(){setOpaque(true);setPreferredSize(new Dimension(1000,540));}
    public void names(String left,String right){leftName=left;rightName=right;}
    public void fps(int value){fps=value;}
    public void snapshot(PvpStageBasis copy){
        previous.clear();if(view!=null)for(Entity e:view.le)previous.put(e.pvpEntityId,e.pos);
        view=copy;published=System.nanoTime();halfAdvanced=false;
    }
    public void renderFrame(){
        if(view!=null && fps==60 && !halfAdvanced && System.nanoTime()-published>=16_666_667L){view.advanceDisplay();halfAdvanced=true;}
        repaint();
    }
    @Override protected void paintComponent(Graphics graphics){
        super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();
        try {
            int w=getWidth(),h=getHeight();
            g.setPaint(new GradientPaint(0,0,new Color(119,180,221),0,h,new Color(238,245,250)));g.fillRect(0,0,w,h);
            if(view==null){g.setColor(Color.DARK_GRAY);g.drawString("キャラクターの同期を待っています",24,32);return;}
            float scale=Math.max(0.01f,(w-40f)/(view.st.len*CommonStatic.BattleConst.ratio));
            float unit=scale*0.8f,ground=h*0.77f;
            FG2D fg=new FG2D(g);FakeTransform base=fg.getTransform();
            try{view.bg.draw(fg,new P(w,h),0,0,scale,(int)ground);}catch(RuntimeException ignored){/* retain fallback background */}
            fg.setTransform(base);g.setColor(new Color(112,119,82));g.fillRect(0,(int)ground,w,h-(int)ground);
            castle(g,fg,view.left(),true,scale,ground);castle(g,fg,view.right(),false,scale,ground);
            float alpha=fps==60?Math.min(1f,(System.nanoTime()-published)/33_333_333f):1f;
            ArrayList<ContAb> effects=new ArrayList<>(view.lw);effects.addAll(view.tlw);effects.sort(Comparator.comparingInt(e->e.layer));
            int effect=0;
            for(Entity e:view.le){
                while(effect<effects.size()&&effects.get(effect).layer<=e.currentLayer)drawEffect(fg,base,effects.get(effect++),scale,ground);
                if(e.dead)continue;
                float prev=previous.getOrDefault(e.pvpEntityId,e.pos),x=20+(prev+(e.pos-prev)*alpha)*CommonStatic.BattleConst.ratio*scale;
                float y=ground+e.currentLayer*4*scale;
                fg.setTransform(base);
                boolean flip=e instanceof EUnit?e.dire==1:e.dire==-1;
                if(flip){fg.translate(x*2,0);fg.scale(-1,1);}
                e.anim.draw(fg,new P(x,y),unit);fg.setTransform(base);
                e.anim.drawEff(fg,new P(x,y),scale);fg.setTransform(base);
            }
            while(effect<effects.size())drawEffect(fg,base,effects.get(effect++),scale,ground);
            for(EAnimCont e:view.lea){fg.setTransform(base);e.draw(fg,new P(20+e.pos*CommonStatic.BattleConst.ratio*scale,ground+e.layer*4*scale),unit);}
            for(StageBasis side:new StageBasis[]{view.left(),view.right()}){
                fg.setTransform(base);float x=20+side.ownBase().pos*CommonStatic.BattleConst.ratio*scale;
                if(side.ownDirection()==1){fg.translate(2*x,0);fg.scale(-1,1);}
                side.canon.drawBase(fg,new P(x,ground-134*scale),unit);fg.setTransform(base);
                float attackX=20+side.canon.pos*CommonStatic.BattleConst.ratio*scale;
                if(side.ownDirection()==1){fg.translate(2*attackX,0);fg.scale(-1,1);}
                side.canon.drawAtk(fg,new P(attackX,ground),unit);fg.setTransform(base);
            }
            g.setColor(new Color(15,23,42,210));g.fillRoundRect(12,12,w-24,58,14,14);
            g.setColor(new Color(103,201,255));g.drawString(leftName+"   "+view.ebase.health+" / "+view.ebase.maxH,28,36);
            g.setColor(new Color(255,173,206));String right=rightName+"   "+view.ubase.health+" / "+view.ubase.maxH;
            g.drawString(right,w-28-g.getFontMetrics().stringWidth(right),36);
            g.setColor(Color.WHITE);g.drawString("30 TPS  |  "+fps+" FPS  |  Tick "+view.time,w/2-100,56);
        } finally {g.dispose();}
    }
    private void castle(Graphics2D g,FG2D fg,StageBasis side,boolean flip,float scale,float y){
        FakeTransform base=fg.getTransform();float x=20+side.ownBase().pos*CommonStatic.BattleConst.ratio*scale;
        if(flip){fg.translate(x*2,0);fg.scale(-1,1);}
        BattleBox.BBPainter.drawNyCast(fg,(int)y,(int)x,scale,side.nyc);fg.setTransform(base);
    }
    private static void drawEffect(FG2D fg,FakeTransform base,ContAb effect,float scale,float y){
        fg.setTransform(base);effect.draw(fg,new P(20+effect.pos*CommonStatic.BattleConst.ratio*scale,y+effect.layer*4*scale),scale*0.8f);fg.setTransform(base);
    }
}
