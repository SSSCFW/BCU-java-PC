package online.ui;

import com.google.gson.*;
import common.battle.*;
import common.pack.UserProfile;
import common.util.pack.Background;
import common.util.stage.Music;
import common.util.unit.Form;
import online.net.*;
import online.net.lobby.RoomRules;
import page.Page;
import page.MainFrame;
import page.basis.BasisPage;
import utilpc.UtilPC;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionListener;
import java.util.*;

/** Dedicated editable in-room page. It owns no sockets; parent manages the session lifetime. */
public final class RoomLobbyPage extends Page {
    private static final long serialVersionUID=1L;
    private final OnlineLobbyPage owner;
    private final RoomClient client;
    private final int playerId;
    private final JPanel content=new JPanel(new BorderLayout(12,12));
    private final JButton back=new JButton("部屋から退出"),edit=new JButton("編成を編集"),apply=new JButton("ルールを適用"),ready;
    private final JComboBox<BasisLU> lineup;
    private final JCheckBox share,force60=new JCheckBox("全員の表示を60FPSに揃える（戦闘処理は30TPS）");
    private final JSpinner distance=new JSpinner(new SpinnerNumberModel(4400,RoomRules.MIN_DISTANCE,RoomRules.MAX_DISTANCE,100));
    private final JComboBox<Background> background=new JComboBox<>();
    private final JComboBox<MusicChoice> music=new JComboBox<>();
    private final JTextArea participants=new JTextArea(3,35),status=new JTextArea(3,50);
    private final JLabel ruleNote=new JLabel("ホストのルールを受信しています…");
    private final JLabel[] icons=new JLabel[10];
    private final AudioSettingsPanel audio=new AudioSettingsPanel();
    private JsonObject state;
    private boolean loading,editing,dirty,pending,closed;
    private final ActionListener lineupListener, shareListener;

    RoomLobbyPage(OnlineLobbyPage owner,RoomClient client,int id,String room,boolean protectedRoom,JComboBox<BasisLU> lineup,JCheckBox share,JButton ready){
        super(owner);this.owner=owner;this.client=client;playerId=id;this.lineup=lineup;this.share=share;this.ready=ready;
        content.setBorder(BorderFactory.createEmptyBorder(12,16,12,16));add(content);
        JPanel header=new JPanel(new FlowLayout(FlowLayout.LEADING));header.add(back);header.add(new JLabel("対戦ロビー  /  部屋ID: "+room+"  /  "+(protectedRoom?"パスワードあり":"パスワードなし")));
        JButton copy=new JButton("部屋IDをコピー");copy.addActionListener(e->Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(room),null));header.add(copy);content.add(header,BorderLayout.NORTH);
        JPanel sections=new JPanel();sections.setLayout(new BoxLayout(sections,BoxLayout.Y_AXIS));
        participants.setEditable(false);participants.setBorder(BorderFactory.createTitledBorder("参加者・準備状態"));sections.add(participants);
        JPanel own=new JPanel(new BorderLayout(6,6));own.setBorder(BorderFactory.createTitledBorder("自分の編成"));JPanel choose=new JPanel(new BorderLayout(8,0));choose.add(lineup,BorderLayout.CENTER);choose.add(edit,BorderLayout.EAST);own.add(choose,BorderLayout.NORTH);
        JPanel slots=new JPanel(new GridLayout(2,5,6,6));for(int i=0;i<10;i++){icons[i]=new JLabel("—",SwingConstants.CENTER);icons[i].setVerticalTextPosition(SwingConstants.BOTTOM);icons[i].setHorizontalTextPosition(SwingConstants.CENTER);slots.add(icons[i]);}own.add(slots,BorderLayout.CENTER);own.add(share,BorderLayout.SOUTH);sections.add(own);
        JPanel rules=new JPanel(new GridBagLayout());rules.setBorder(BorderFactory.createTitledBorder("対戦ルール（ホストのみ変更可能）"));
        for(Background b:UserProfile.getBCData().bgs.getList())if(b!=null)background.addItem(b);
        music.addItem(new MusicChoice(null));for(Music m:UserProfile.getBCData().musics.getList())if(m!=null&&m.data!=null)music.addItem(new MusicChoice(m));
        row(rules,0,"城と城の距離",distance);row(rules,1,"背景（標準データ）",background);row(rules,2,"BGM（標準データ）",music);row(rules,3,"",force60);row(rules,4,"",apply);row(rules,5,"",ruleNote);sections.add(rules);sections.add(audio);
        content.add(new JScrollPane(sections),BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout(8,8));footer.add(ready,BorderLayout.EAST);status.setEditable(false);status.setLineWrap(true);status.setWrapStyleWord(true);footer.add(new JScrollPane(status),BorderLayout.CENTER);content.add(footer,BorderLayout.SOUTH);
        back.addActionListener(e->owner.returnToConnection());edit.addActionListener(e->editLineup());apply.addActionListener(e->applyRules());
        lineupListener=e->{if(!loading&&!closed&&!editing){preview();pending=true;client.setLineupName(summary());refreshControls();}};
        shareListener=e->refreshControls();
        lineup.addActionListener(lineupListener);
        distance.addChangeListener(e->rulesChanged());background.addActionListener(e->rulesChanged());music.addActionListener(e->rulesChanged());force60.addActionListener(e->rulesChanged());share.addActionListener(shareListener);
        preview();refreshControls();message("編成とルールを確認してください。全員が準備完了するとキャラを自動共有し、対戦を開始します。");
    }
    private static void row(JPanel panel,int y,String title,Component component){GridBagConstraints c=new GridBagConstraints();c.gridy=y;c.gridx=0;c.anchor=GridBagConstraints.WEST;c.insets=new Insets(4,8,4,8);panel.add(new JLabel(title),c);c.gridx=1;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;panel.add(component,c);}
    private boolean editable(){return state!=null&&"EDITING".equals(state.get("phase").getAsString());}
    private boolean host(){return state!=null&&state.get("hostId").getAsInt()==playerId;}
    private boolean ownReady(){if(state!=null)for(JsonElement e:state.getAsJsonArray("players")){JsonObject p=e.getAsJsonObject();if(p.get("id").getAsInt()==playerId)return p.get("lobbyReady").getAsBoolean();}return false;}
    void state(JsonObject value)throws java.io.IOException{
        state=value;pending=false;
        RoomRules rules=RoomRules.read(value);loading=true;
        try{
            if(!dirty||!host()||!editable()){
                distance.setValue(rules.castleDistance);force60.setSelected(rules.force60Fps);
                for(int i=0;i<background.getItemCount();i++)if(background.getItemAt(i).id.id==rules.backgroundId)background.setSelectedIndex(i);
                for(int i=0;i<music.getItemCount();i++)if(music.getItemAt(i).id()==rules.musicId)music.setSelectedIndex(i);
                dirty=false;
            }
        }finally{loading=false;}
        StringBuilder names=new StringBuilder();for(JsonElement e:value.getAsJsonArray("players")){JsonObject p=e.getAsJsonObject();names.append(p.get("id").getAsInt()==value.get("hostId").getAsInt()?"[ホスト] ":"[参加者] ").append(p.get("name").getAsString()).append("  /  ").append(p.get("seat").getAsString().equals("left")?"左・青":"右・ピンク").append("  /  ").append(p.get("lobbyReady").getAsBoolean()?"準備完了":"編集中");String label=p.get("lineupName").getAsString();if(!label.isEmpty())names.append("  /  ").append(label);names.append('\n');}participants.setText(names.toString());
        ruleNote.setText("設定バージョン "+value.get("revision").getAsLong()+"。変更すると全員の準備完了を解除します。");refreshControls();
    }
    private void refreshControls(){
        boolean can=editable()&&!ownReady()&&!pending&&!closed;
        lineup.setEnabled(can);edit.setEnabled(can);share.setEnabled(can);
        distance.setEnabled(can&&host());background.setEnabled(can&&host());music.setEnabled(can&&host());force60.setEnabled(can&&host());apply.setEnabled(can&&host()&&dirty);
        ready.setText(ownReady()?"準備を解除":"準備完了");ready.setEnabled(editable()&&!pending&&!closed&&(ownReady()||(!dirty&&share.isSelected()&&lineup.getSelectedItem()!=null)));
        if(!editable()&&state!=null){ready.setText("共有・開始待ち…");edit.setEnabled(false);}
    }
    private void rulesChanged(){if(loading||closed)return;dirty=true;refreshControls();}
    private void applyRules(){try{distance.commitEdit();Background b=(Background)background.getSelectedItem();MusicChoice m=(MusicChoice)music.getSelectedItem();if(b==null||m==null)throw new IllegalArgumentException("背景とBGMを選択してください");RoomRules r=new RoomRules((Integer)distance.getValue(),b.id.id,m.id(),force60.isSelected());PvpStageBasis.validateRulesAssets(r);pending=true;dirty=false;client.setRoomRules(r,state.get("revision").getAsLong());refreshControls();}catch(Exception e){message(e.getMessage());}}
    void toggleReady(){
        if(closed||!editable()||pending)return;
        if(!ownReady())try{distance.commitEdit();if(dirty)throw new IllegalArgumentException("変更したルールを先に適用してください");if(!share.isSelected()||lineup.getSelectedItem()==null)throw new IllegalArgumentException("編成と自動共有への同意が必要です");PvpStageBasis.validateRulesAssets(client.roomRules());}catch(Exception e){message(e.getMessage());return;}
        pending=true;client.lobbyReady(!ownReady(),state.get("revision").getAsLong());refreshControls();
    }
    private String summary(){String s=String.valueOf(lineup.getSelectedItem());return s.length()>120?s.substring(0,120):s;}
    private void preview(){BasisLU b=(BasisLU)lineup.getSelectedItem();for(int i=0;i<10;i++){Form f=b==null?null:b.lu.fs[i/5][i%5];icons[i].setIcon(null);icons[i].setText(f==null?"—":f.toString());if(f!=null&&f.anim!=null)try{icons[i].setIcon(UtilPC.getIcon(f.anim.getUni()));}catch(Exception ignored){}}}
    private void editLineup(){
        if(!editable()||ownReady()||pending)return;
        BasisLU b=(BasisLU)lineup.getSelectedItem();if(b==null)return;
        for(BasisSet set:BasisSet.list())if(set.lb.contains(b)){BasisSet.setCurrent(set);set.sele=b;break;}
        editing=true;client.setLineupName(summary());
        try {changePanel(new BasisPage(this));}
        catch(RuntimeException e){editing=false;notice("編成画面を開けませんでした: "+e.getMessage());}
    }
    @Override protected void renew(){
        audio.refresh();if(!editing)return;editing=false;loading=true;
        try{lineup.removeAllItems();for(BasisSet set:BasisSet.list())for(BasisLU b:set.lb)lineup.addItem(b);lineup.setSelectedItem(BasisSet.current().sele);}finally{loading=false;}
        preview();pending=true;client.setLineupName(summary());refreshControls();
    }
    void message(String text){status.setText(text==null?"":text);}
    void notice(String text){pending=false;message(text);refreshControls();}
    void closeLobby(){if(closed)return;closed=true;lineup.removeActionListener(lineupListener);share.removeActionListener(shareListener);refreshControls();}
    @Override protected JButton getBackButton(){return back;}
    @Override protected void resized(int w,int h){setBounds(0,0,w,h);content.setBounds(0,0,w,h);content.revalidate();}
    private static final class MusicChoice{final Music music;MusicChoice(Music m){music=m;}int id(){return music==null?-1:music.id.id;}public String toString(){return music==null?"BGMなし":music.toString();}}
}
