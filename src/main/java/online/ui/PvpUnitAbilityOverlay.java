package online.ui;

import common.battle.StageBasis;
import common.battle.entity.EUnit;
import common.battle.data.MaskUnit;
import common.battle.data.PCoin;
import common.pack.Identifier;
import common.util.unit.Form;
import common.util.unit.EForm;
import common.util.unit.Level;
import common.util.unit.Trait;
import utilpc.Interpret;
import utilpc.UtilPC;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/** Hold-to-inspect overlay for the local PvP lineup. */
public final class PvpUnitAbilityOverlay extends JPanel {
    private static final long serialVersionUID=1L;
    private final JLabel title=new JLabel("",SwingConstants.CENTER);
    private final ScrollRows rows=new ScrollRows();
    private final JScrollPane scroll;
    public PvpUnitAbilityOverlay(){
        super(new BorderLayout(8,8));setOpaque(true);setBackground(new Color(28,32,36));
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(205,210,215),2),new EmptyBorder(10,14,10,14)));
        setMinimumSize(new Dimension(640,300));setPreferredSize(new Dimension(920,390));
        title.setForeground(Color.WHITE);title.setFont(title.getFont().deriveFont(Font.BOLD,18f));add(title,BorderLayout.NORTH);
        rows.setOpaque(false);rows.setLayout(new BoxLayout(rows,BoxLayout.Y_AXIS));
        scroll=new JScrollPane(rows,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);scroll.setOpaque(false);scroll.getViewport().setOpaque(false);
        scroll.setWheelScrollingEnabled(true);scroll.getVerticalScrollBar().setUnitIncrement(28);
        scroll.getHorizontalScrollBar().setUnitIncrement(0);
        add(scroll,BorderLayout.CENTER);setVisible(false);
    }
    public void show(Form form,StageBasis player){
        rows.removeAll();title.setText(form==null?"":form.toString());
        if(form==null||player==null){setVisible(false);return;}
        Level level=player.b.lu.getLv(form);
        MaskUnit du=form.du;
        PCoin pc=du.getPCoin();if(pc!=null)du=pc.improve(level.getTalents());

        EUnit preview=new EForm(form,level).invokeEntity(player,level.getLv()+level.getPlusLv(),0,0);
        JPanel stats=row("ステータス");
        stats.add(textChip("HP "+format(preview.maxH)));
        stats.add(textChip("攻撃力 "+format(preview.getAtk())));
        stats.add(textChip("移動速度 "+format(preview.displayMoveSpeed())));
        rows.add(stats);

        JPanel target=row("対象属性");
        java.util.List<Trait> traits=du.getTraits();
        if(traits.isEmpty())target.add(textChip("なし"));
        else for(Trait trait:traits)target.add(traitChip(trait));
        rows.add(target);

        List<Interpret.ProcDisplay> abi=Interpret.getAbi(du);
        JPanel effects=row("属性効果・特性");
        int abiCount=0;for(Interpret.ProcDisplay d:abi)if(d!=null&&!d.toString().trim().isEmpty()){effects.add(procChip(d));abiCount++;}
        if(abiCount==0)effects.add(textChip("なし"));rows.add(effects);

        double mul=form.unit.lv.getMult(level.getLv()+level.getPlusLv());
        List<Interpret.ProcDisplay> procs=Interpret.getProc(du,new double[]{mul,level.getLv()+level.getPlusLv()});
        JPanel abilities=row("その他能力");
        int procCount=0;for(Interpret.ProcDisplay d:procs)if(d!=null&&!d.toString().trim().isEmpty()){abilities.add(procChip(d));procCount++;}
        if(procCount==0)abilities.add(textChip("なし"));rows.add(abilities);

        revalidate();repaint();
        scroll.getVerticalScrollBar().setValue(0);
        setVisible(true);
    }
    public void close(){
        setVisible(false);
        scroll.getVerticalScrollBar().setValue(0);
    }
    public void scrollByWheel(int rotation){
        if(!isVisible()||rotation==0)return;
        JScrollBar bar=scroll.getVerticalScrollBar();
        int amount=Math.max(1,bar.getUnitIncrement(1))*3;
        bar.setValue(bar.getValue()+rotation*amount);
    }
    public JScrollPane scrollPane(){return scroll;}
    private static JPanel row(String name){
        JPanel p=new JPanel(new WrapLayout(FlowLayout.LEADING,6,3));p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);p.setMaximumSize(new Dimension(Integer.MAX_VALUE,Integer.MAX_VALUE));
        JLabel l=new JLabel(name);l.setForeground(new Color(255,232,150));l.setPreferredSize(new Dimension(110,38));p.add(l);return p;
    }
    private static JLabel traitChip(Trait trait){
        JLabel l=new JLabel();l.setOpaque(true);l.setBackground(new Color(48,52,58));l.setBorder(new EmptyBorder(3,5,3,5));
        ImageIcon icon=null;
        if(trait!=null&&trait.id!=null){
            if(Identifier.DEF.equals(trait.id.pack))icon=UtilPC.createIcon(3,trait.id.id);
            else if(trait.icon!=null)icon=UtilPC.getIcon(trait.icon);
        }
        if(icon!=null)l.setIcon(scale(icon,28,28));
        // Do not expose pack/id/debug names such as "000000/3 - new trait" in the
        // normal HUD. Keep the full name only as a hover tooltip for diagnostics.
        l.setText("");l.setToolTipText(trait==null?null:trait.toString());
        l.setPreferredSize(new Dimension(38,34));
        return l;
    }
    private static JLabel procChip(Interpret.ProcDisplay d){
        JLabel l=textChip(d.toString());if(d.getIcon()!=null)l.setIcon(scale(d.getIcon(),30,30));l.setToolTipText(d.toString());return l;
    }
    private static String format(long value){return String.format(java.util.Locale.ROOT,"%,d",value);}
    private static String format(double value){
        if(Math.abs(value-Math.rint(value))<1e-9)return format(Math.round(value));
        return String.format(java.util.Locale.ROOT,"%,.1f",value);
    }
    private static JLabel textChip(String text){
        JLabel l=new JLabel(text);l.setOpaque(true);l.setBackground(new Color(48,52,58));l.setForeground(Color.WHITE);
        l.setBorder(new EmptyBorder(3,7,3,7));return l;
    }
    private static ImageIcon scale(ImageIcon icon,int w,int h){
        return new ImageIcon(icon.getImage().getScaledInstance(w,h,Image.SCALE_SMOOTH));
    }

    private static final class ScrollRows extends JPanel implements Scrollable {
        private static final long serialVersionUID=1L;
        @Override public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
        @Override public int getScrollableUnitIncrement(Rectangle visibleRect,int orientation,int direction){return 28;}
        @Override public int getScrollableBlockIncrement(Rectangle visibleRect,int orientation,int direction){return Math.max(28,visibleRect.height-28);}
        @Override public boolean getScrollableTracksViewportWidth(){return true;}
        @Override public boolean getScrollableTracksViewportHeight(){return false;}
    }

    /** FlowLayout whose preferred height accounts for wrapped rows at the viewport width. */
    private static final class WrapLayout extends FlowLayout {
        private static final long serialVersionUID=1L;
        WrapLayout(int align,int hgap,int vgap){super(align,hgap,vgap);}
        @Override public Dimension preferredLayoutSize(Container target){return layoutSize(target,true);}
        @Override public Dimension minimumLayoutSize(Container target){
            Dimension d=layoutSize(target,false);d.width-=getHgap()+1;return d;
        }
        private Dimension layoutSize(Container target,boolean preferred){
            synchronized(target.getTreeLock()){
                int width=target.getWidth();
                if(width<=0&&target.getParent()!=null)width=target.getParent().getWidth();
                if(width<=0)width=920;
                Insets insets=target.getInsets();
                int horizontal=insets.left+insets.right+getHgap()*2;
                int maxWidth=Math.max(1,width-horizontal);
                Dimension out=new Dimension(0,0);
                int rowWidth=0,rowHeight=0,visible=0;
                for(Component c:target.getComponents()){
                    if(!c.isVisible())continue;
                    Dimension d=preferred?c.getPreferredSize():c.getMinimumSize();
                    if(rowWidth>0&&rowWidth+getHgap()+d.width>maxWidth){
                        out.width=Math.max(out.width,rowWidth);
                        out.height+=rowHeight+(visible>0?getVgap():0);
                        rowWidth=0;rowHeight=0;
                    }
                    if(rowWidth>0)rowWidth+=getHgap();
                    rowWidth+=d.width;rowHeight=Math.max(rowHeight,d.height);visible++;
                }
                if(rowWidth>0){
                    out.width=Math.max(out.width,rowWidth);
                    out.height+=rowHeight+(out.height>0?getVgap():0);
                }
                out.width=Math.min(width,Math.max(out.width+horizontal,width));
                out.height+=insets.top+insets.bottom+getVgap()*2;
                return out;
            }
        }
    }
}
