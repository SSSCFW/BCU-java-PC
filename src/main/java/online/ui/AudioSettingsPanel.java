package online.ui;

import io.BCMusic;
import javax.swing.*;
import java.awt.*;

/** Local-only controls shared by the room and the nonmodal battle dialog. */
public final class AudioSettingsPanel extends JPanel {
    private static final long serialVersionUID=1L;
    private final JSlider bg=new JSlider(0,100),se=new JSlider(0,100),ui=new JSlider(0,100);
    private boolean refreshing;
    private final JCheckBox enabled=new JCheckBox("音声を有効にする");
    public AudioSettingsPanel(){
        super(new GridBagLayout());setBorder(BorderFactory.createTitledBorder("自分の音量（相手には影響しません）"));
        refresh();row(0,"",enabled);row(1,"BGM",bg);row(2,"戦闘効果音",se);row(3,"操作・生産通知",ui);
        enabled.addActionListener(e->BCMusic.setSoundEnabled(enabled.isSelected()));
        bg.addChangeListener(e->{if(!refreshing)BCMusic.setBGVol(bg.getValue());});se.addChangeListener(e->{if(!refreshing){BCMusic.setSEVol(se.getValue());PvpSoundBank.refreshVolume();}});ui.addChangeListener(e->{if(!refreshing)BCMusic.setUIVol(ui.getValue());});
    }
    public void refresh(){refreshing=true;try{bg.setValue(BCMusic.VOL_BG);se.setValue(BCMusic.VOL_SE);ui.setValue(BCMusic.VOL_UI);enabled.setSelected(BCMusic.play);}finally{refreshing=false;}}
    private void row(int y,String text,JComponent component){
        GridBagConstraints c=new GridBagConstraints();c.gridy=y;c.insets=new Insets(4,8,4,8);c.anchor=GridBagConstraints.WEST;c.gridx=0;add(new JLabel(text),c);c.gridx=1;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;add(component,c);
        if(component instanceof JSlider){JSlider slider=(JSlider)component;JLabel value=new JLabel(slider.getValue()+"%",SwingConstants.RIGHT);c.gridx=2;c.weightx=0;add(value,c);slider.addChangeListener(e->value.setText(slider.getValue()+"%"));}
    }
    /** MODELESS deliberately: closing/changing volume must not stall synchronized gameplay. */
    public static JDialog open(Component parent){
        JDialog dialog=new JDialog(SwingUtilities.getWindowAncestor(parent),"音量設定",Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);dialog.setContentPane(new AudioSettingsPanel());dialog.pack();dialog.setMinimumSize(new Dimension(380,220));dialog.setSize(Math.max(380,dialog.getWidth()),Math.max(220,dialog.getHeight()));dialog.setLocationRelativeTo(parent);dialog.setVisible(true);return dialog;
    }
}
