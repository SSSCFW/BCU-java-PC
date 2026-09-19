package online.ui;

import page.battle.BattleBox;
import utilpc.awt.FG2D;
import javax.swing.*;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicTextAreaUI;
import java.awt.*;
import java.awt.image.BufferedImage;

/** Lightweight frozen battlefield: no native Canvas can obscure the ending/OK UI. */
public final class PvpBattleOverlay extends JPanel {
    private static final long serialVersionUID=1L;
    private BufferedImage frame;
    public PvpBattleOverlay(){setOpaque(true);setBackground(new Color(18,20,26));setVisible(false);}
    public void capture(BattleBox box,int width,int height){
        if(width<=0||height<=0)return;
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();
        try{box.getPainter().draw(new FG2D(g));frame=image;}
        catch(RuntimeException e){System.err.println("BCU PvP ending snapshot: "+e.getMessage());frame=null;}
        finally{g.dispose();}
    }
    public void clear(){frame=null;}
    @Override protected void paintComponent(Graphics g){
        super.paintComponent(g);
        if(frame!=null)g.drawImage(frame,0,0,getWidth(),getHeight(),null);
    }
    /** Page.fontSetter must not replace an explicitly sized presentation font. */
    public static JLabel label(String text,float points){
        return new JLabel(text,SwingConstants.CENTER){
            @Override public void setFont(Font font){super.setFont((font==null?new Font("Dialog",Font.BOLD,12):font).deriveFont(Font.BOLD,points));}
        };
    }
    public static JTextArea detail(){
        JTextArea text=new JTextArea(){
            @Override public void setFont(Font font){super.setFont((font==null?new Font("Dialog",Font.PLAIN,12):font).deriveFont(Font.PLAIN,16f));}
        };
        text.setLineWrap(true);text.setWrapStyleWord(true);text.setEditable(false);text.setFocusable(false);
        text.setUI(new BasicTextAreaUI());text.setBorder(BorderFactory.createEmptyBorder(4,4,4,4));
        text.setOpaque(true);text.setBackground(new Color(20,20,20));text.setForeground(Color.WHITE);return text;
    }
    public static JButton okButton(){
        JButton button=new JButton("OK"){
            @Override public void setFont(Font font){super.setFont((font==null?new Font("Dialog",Font.BOLD,12):font).deriveFont(Font.BOLD,18f));}
        };
        button.setUI(new BasicButtonUI());button.setOpaque(true);button.setContentAreaFilled(true);
        button.setBackground(new Color(235,238,244));button.setForeground(new Color(15,20,30));
        button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(120,140,170)),BorderFactory.createEmptyBorder(10,18,10,18)));
        return button;
    }
}
