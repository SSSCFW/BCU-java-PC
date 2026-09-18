package online.ui;

import common.battle.StageBasis;
import common.battle.data.MaskUnit;
import common.battle.data.PCoin;
import common.pack.Identifier;
import common.util.unit.Form;
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
    private final JPanel rows=new JPanel();
    public PvpUnitAbilityOverlay(){
        super(new BorderLayout(8,8));setOpaque(true);setBackground(new Color(28,32,36));
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(205,210,215),2),new EmptyBorder(8,12,8,12)));
        title.setForeground(Color.WHITE);title.setFont(title.getFont().deriveFont(Font.BOLD,18f));add(title,BorderLayout.NORTH);
        rows.setOpaque(false);rows.setLayout(new BoxLayout(rows,BoxLayout.Y_AXIS));add(rows,BorderLayout.CENTER);setVisible(false);
    }
    public void show(Form form,StageBasis player){
        rows.removeAll();title.setText(form==null?"":form.toString());
        if(form==null||player==null){setVisible(false);return;}
        Level level=player.b.lu.getLv(form);
        MaskUnit du=form.du;
        PCoin pc=du.getPCoin();if(pc!=null)du=pc.improve(level.getTalents());

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

        revalidate();repaint();setVisible(true);
    }
    public void close(){setVisible(false);}
    private static JPanel row(String name){
        JPanel p=new JPanel(new FlowLayout(FlowLayout.LEADING,6,3));p.setOpaque(false);
        JLabel l=new JLabel(name);l.setForeground(new Color(255,232,150));l.setPreferredSize(new Dimension(110,38));p.add(l);return p;
    }
    private static JLabel traitChip(Trait trait){
        JLabel l=new JLabel();l.setOpaque(true);l.setBackground(new Color(48,52,58));l.setForeground(Color.WHITE);l.setBorder(new EmptyBorder(3,5,3,7));
        ImageIcon icon=null;
        if(trait!=null&&trait.id!=null){
            if(Identifier.DEF.equals(trait.id.pack))icon=UtilPC.createIcon(3,trait.id.id);
            else if(trait.icon!=null)icon=UtilPC.getIcon(trait.icon);
        }
        if(icon!=null)l.setIcon(scale(icon,28,28));
        l.setText(trait==null?"?":trait.toString());return l;
    }
    private static JLabel procChip(Interpret.ProcDisplay d){
        JLabel l=textChip(d.toString());if(d.getIcon()!=null)l.setIcon(scale(d.getIcon(),30,30));l.setToolTipText(d.toString());return l;
    }
    private static JLabel textChip(String text){
        JLabel l=new JLabel(text);l.setOpaque(true);l.setBackground(new Color(48,52,58));l.setForeground(Color.WHITE);
        l.setBorder(new EmptyBorder(3,7,3,7));return l;
    }
    private static ImageIcon scale(ImageIcon icon,int w,int h){
        return new ImageIcon(icon.getImage().getScaledInstance(w,h,Image.SCALE_SMOOTH));
    }
}
