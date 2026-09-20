package online.ui;

import com.google.gson.*;
import common.battle.*;
import common.pack.UserProfile;
import io.BCMusic;
import common.util.pack.Background;
import common.util.stage.Music;
import common.util.unit.Form;
import online.net.*;
import online.net.lobby.PvpTraitRules;
import online.net.lobby.PvpBattleMusic;
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
    private final JButton back=new JButton("部屋から退出"),edit=new JButton("編成を編集"),apply=new JButton("ルールを適用"),
            applyPlayer=new JButton("自分設定を適用"),ready;
    private final JComboBox<BasisLU> lineup;
    private final JComboBox<RandomLineupFactory.SortOrder> randomSort=new JComboBox<>(RandomLineupFactory.SortOrder.values());
    private final JCheckBox force60=new JCheckBox("表示: 60FPS固定（戦闘処理は30TPS）"),
            debugMode=new JCheckBox("デバッグモード（全員にルーレットMAXボタンを表示）"),
            unlimitedTime=new JCheckBox("無制限"),
            castleHitMoneyEnabled=new JCheckBox("自城を攻撃されるたびにお金を加算");
    private final JComboBox<RoomRules.SpecialMode> special=new JComboBox<>(RoomRules.SpecialMode.values());
    private final JComboBox<TraitChoice> hostTrait=new JComboBox<>(),guestTrait=new JComboBox<>();
    private final JCheckBox[] hostTraitExclude=new JCheckBox[PvpTraitRules.OPTIONS.length],
            guestTraitExclude=new JCheckBox[PvpTraitRules.OPTIONS.length];
    private final JPanel hostTraitExcludePanel=new JPanel(new FlowLayout(FlowLayout.LEADING,4,2)),
            guestTraitExcludePanel=new JPanel(new FlowLayout(FlowLayout.LEADING,4,2));
    private final JSpinner timeLimit=new JSpinner(new SpinnerNumberModel(RoomRules.DEFAULT_TIME_LIMIT_MINUTES,
            RoomRules.MIN_TIME_LIMIT_MINUTES,RoomRules.MAX_TIME_LIMIT_MINUTES,1));
    private final JSpinner maxUnits=new JSpinner(new SpinnerNumberModel(RoomRules.DEFAULT_MAX_UNITS,
            RoomRules.MIN_MAX_UNITS,RoomRules.MAX_MAX_UNITS,1));
    private final JSpinner castleHitMoney=new JSpinner(new SpinnerNumberModel(RoomRules.DEFAULT_CASTLE_HIT_MONEY,
            RoomRules.MIN_CASTLE_HIT_MONEY,RoomRules.MAX_CASTLE_HIT_MONEY,1));
    private final JSpinner castleHealthMultiplier=new JSpinner(new SpinnerNumberModel(PvpStageBasis.DEFAULT_CASTLE_HEALTH_MULTIPLIER,
            PvpStageBasis.MIN_CASTLE_HEALTH_MULTIPLIER,PvpStageBasis.MAX_CASTLE_HEALTH_MULTIPLIER,0.5));
    private final JSpinner distance=new JSpinner(new SpinnerNumberModel(4400,RoomRules.MIN_DISTANCE,RoomRules.MAX_DISTANCE,100));
    private final JComboBox<BackgroundChoice> background=new JComboBox<>();
    private final JComboBox<MusicChoice> music=new JComboBox<>();
    private final JButton musicPreview=new JButton("試聴"),musicStop=new JButton("停止");
    private final JLabel musicPreviewStatus=new JLabel("停止中");
    private final JTextArea participants=new JTextArea(3,35),status=new JTextArea(3,50);
    private final JLabel ruleNote=new JLabel("ホストのルールを受信しています…");
    private final JLabel[] icons=new JLabel[10];
    private final AudioSettingsPanel audio=new AudioSettingsPanel();
    private JsonObject state;
    private boolean loading,editing,dirty,playerDirty,pending,closed,restoredHostPreferences,restoredPlayerPreferences,musicPreviewing;
    private int musicPreviewId=-1;
    private final ActionListener lineupListener;

    RoomLobbyPage(OnlineLobbyPage owner,RoomClient client,int id,String room,boolean protectedRoom,JComboBox<BasisLU> lineup,JButton ready){
        super(owner);this.owner=owner;this.client=client;playerId=id;this.lineup=lineup;this.ready=ready;
        content.setBorder(BorderFactory.createEmptyBorder(12,16,12,16));add(content);
        JPanel header=new JPanel(new FlowLayout(FlowLayout.LEADING));header.add(back);header.add(new JLabel("対戦ロビー  /  部屋ID: "+room+"  /  "+(protectedRoom?"パスワードあり":"パスワードなし")));
        JButton copy=new JButton("部屋IDをコピー");copy.addActionListener(e->Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(room),null));header.add(copy);content.add(header,BorderLayout.NORTH);
        JPanel sections=new JPanel();sections.setLayout(new BoxLayout(sections,BoxLayout.Y_AXIS));
        participants.setEditable(false);participants.setBorder(BorderFactory.createTitledBorder("参加者・準備状態"));sections.add(participants);

        JPanel own=new JPanel(new BorderLayout(6,6));own.setBorder(BorderFactory.createTitledBorder("自分の編成・設定"));
        JPanel ownTop=new JPanel();ownTop.setLayout(new BoxLayout(ownTop,BoxLayout.Y_AXIS));
        JPanel choose=new JPanel(new BorderLayout(8,0));choose.add(lineup,BorderLayout.CENTER);choose.add(edit,BorderLayout.EAST);ownTop.add(choose);
        JPanel randomSortRow=new JPanel(new FlowLayout(FlowLayout.LEADING));randomSortRow.add(new JLabel("ランダム編成の並び"));randomSortRow.add(randomSort);ownTop.add(randomSortRow);
        JPanel castleRow=new JPanel(new FlowLayout(FlowLayout.LEADING));castleRow.add(new JLabel("自分の城体力倍率"));castleRow.add(castleHealthMultiplier);castleRow.add(new JLabel("倍"));castleRow.add(applyPlayer);ownTop.add(castleRow);
        own.add(ownTop,BorderLayout.NORTH);
        JPanel slots=new JPanel(new GridLayout(2,5,6,6));for(int i=0;i<10;i++){icons[i]=new JLabel("—",SwingConstants.CENTER);icons[i].setVerticalTextPosition(SwingConstants.BOTTOM);icons[i].setHorizontalTextPosition(SwingConstants.CENTER);slots.add(icons[i]);}own.add(slots,BorderLayout.CENTER);sections.add(own);

        JPanel rules=new JPanel(new GridBagLayout());rules.setBorder(BorderFactory.createTitledBorder("対戦ルール（ホストのみ変更可能）"));
        background.addItem(new BackgroundChoice(null,true));
        for(Background b:UserProfile.getBCData().bgs.getList())if(b!=null)background.addItem(new BackgroundChoice(b,false));
        for(PvpBattleMusic.Entry entry:PvpBattleMusic.entries())music.addItem(new MusicChoice(entry));
        populateTraits(hostTrait);populateTraits(guestTrait);buildExclusions(hostTraitExclude,hostTraitExcludePanel);buildExclusions(guestTraitExclude,guestTraitExcludePanel);
        JPanel timeRow=new JPanel(new FlowLayout(FlowLayout.LEADING,6,0));timeRow.add(timeLimit);timeRow.add(new JLabel("分"));timeRow.add(unlimitedTime);
        JPanel musicRow=new JPanel(new BorderLayout(6,0));musicRow.add(music,BorderLayout.CENTER);
        JPanel musicActions=new JPanel(new FlowLayout(FlowLayout.LEADING,4,0));musicActions.add(musicPreview);musicActions.add(musicStop);musicActions.add(musicPreviewStatus);musicRow.add(musicActions,BorderLayout.EAST);

        row(rules,0,"城と城の距離",distance);
        row(rules,1,"背景（標準データ）",background);
        row(rules,2,"対戦BGM",musicRow);
        row(rules,3,"戦闘時間",timeRow);
        row(rules,4,"ホスト側キャラ属性",hostTrait);
        row(rules,5,"ホスト側ランダム除外",hostTraitExcludePanel);
        row(rules,6,"参加者側キャラ属性",guestTrait);
        row(rules,7,"参加者側ランダム除外",guestTraitExcludePanel);
        row(rules,8,"戦闘特殊機能",special);
        row(rules,9,"最大出撃キャラ数",maxUnits);
        JPanel castleHitMoneyRow=new JPanel(new FlowLayout(FlowLayout.LEADING,6,0));
        castleHitMoneyRow.add(castleHitMoneyEnabled);castleHitMoneyRow.add(castleHitMoney);castleHitMoneyRow.add(new JLabel("円 / 1ヒット"));
        row(rules,10,"城被弾ボーナス",castleHitMoneyRow);
        row(rules,11,"",force60);
        row(rules,12,"",debugMode);
        row(rules,13,"",apply);
        row(rules,14,"",ruleNote);
        sections.add(rules);sections.add(audio);

        content.add(new JScrollPane(sections),BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout(8,8));footer.add(ready,BorderLayout.EAST);status.setEditable(false);status.setLineWrap(true);status.setWrapStyleWord(true);footer.add(new JScrollPane(status),BorderLayout.CENTER);content.add(footer,BorderLayout.SOUTH);
        back.addActionListener(e->owner.returnToConnection());edit.addActionListener(e->editLineup());apply.addActionListener(e->applyRules());applyPlayer.addActionListener(e->applyPlayerRules());
        lineupListener=e->{if(!loading&&!closed&&!editing){preview();pending=true;client.setLineupName(summary());refreshControls();}};
        lineup.addActionListener(lineupListener);
        randomSort.setSelectedItem(owner.savedRandomLineupSort());
        randomSort.addActionListener(e->{
            if(loading||closed||editing)return;
            RandomLineupFactory.SortOrder value=(RandomLineupFactory.SortOrder)randomSort.getSelectedItem();
            if(value==null)return;
            owner.rememberRandomLineupSort(value);
            if(editable()&&!ownReady()){
                pending=true;client.setLineupName(summary());
            }
            refreshControls();
        });

        distance.addChangeListener(e->rulesChanged());background.addActionListener(e->rulesChanged());
        music.addActionListener(e->{if(!loading)stopMusicPreview();rulesChanged();});
        musicPreview.addActionListener(e->previewMusic());musicStop.addActionListener(e->stopMusicPreview());
        force60.setSelected(true);force60.setEnabled(false);
        special.addActionListener(e->rulesChanged());debugMode.addActionListener(e->rulesChanged());
        timeLimit.addChangeListener(e->rulesChanged());unlimitedTime.addActionListener(e->{rulesChanged();refreshControls();});
        maxUnits.addChangeListener(e->rulesChanged());
        castleHitMoneyEnabled.addActionListener(e->{rulesChanged();refreshControls();});castleHitMoney.addChangeListener(e->rulesChanged());
        hostTrait.addActionListener(e->{rulesChanged();refreshControls();});guestTrait.addActionListener(e->{rulesChanged();refreshControls();});
        for(JCheckBox box:hostTraitExclude)box.addActionListener(e->rulesChanged());
        for(JCheckBox box:guestTraitExclude)box.addActionListener(e->rulesChanged());
        castleHealthMultiplier.addChangeListener(e->{if(!loading&&!closed){playerDirty=true;refreshControls();}});
        preview();refreshControls();message("編成とルールを確認してください。全員が準備完了するとキャラを自動共有し、対戦を開始します。");
    }

    private static void row(JPanel panel,int y,String title,Component component){GridBagConstraints c=new GridBagConstraints();c.gridy=y;c.gridx=0;c.anchor=GridBagConstraints.WEST;c.insets=new Insets(4,8,4,8);panel.add(new JLabel(title),c);c.gridx=1;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;panel.add(component,c);}
    private static void populateTraits(JComboBox<TraitChoice> box){
        box.addItem(new TraitChoice(PvpTraitRules.NONE));box.addItem(new TraitChoice(PvpTraitRules.RANDOM));
        for(int option:PvpTraitRules.OPTIONS)if(option!=PvpTraitRules.NONE)box.addItem(new TraitChoice(option));
    }
    private static void buildExclusions(JCheckBox[] boxes,JPanel panel){
        for(int i=0;i<boxes.length;i++){boxes[i]=new JCheckBox(PvpTraitRules.LABELS[i]);panel.add(boxes[i]);}
    }
    private static int selectedTrait(JComboBox<TraitChoice> box){TraitChoice value=(TraitChoice)box.getSelectedItem();return value==null?PvpTraitRules.NONE:value.value;}
    private static void selectTrait(JComboBox<TraitChoice> box,int trait){for(int i=0;i<box.getItemCount();i++)if(box.getItemAt(i).value==trait){box.setSelectedIndex(i);return;}box.setSelectedIndex(0);}
    private static int exclusionMask(JCheckBox[] boxes){int mask=0;for(int i=0;i<boxes.length;i++)if(boxes[i].isSelected())mask|=1<<i;return mask;}
    private static void setExclusionMask(JCheckBox[] boxes,int mask){for(int i=0;i<boxes.length;i++)boxes[i].setSelected((mask&(1<<i))!=0);}
    private static void setExclusionEnabled(JCheckBox[] boxes,boolean enabled){for(JCheckBox box:boxes)box.setEnabled(enabled);}

    private boolean editable(){return state!=null&&"EDITING".equals(state.get("phase").getAsString());}
    private boolean host(){return state!=null&&state.get("hostId").getAsInt()==playerId;}
    private boolean ownReady(){if(state!=null)for(JsonElement e:state.getAsJsonArray("players")){JsonObject p=e.getAsJsonObject();if(p.get("id").getAsInt()==playerId)return p.get("lobbyReady").getAsBoolean();}return false;}

    void state(JsonObject value)throws java.io.IOException{
        state=value;pending=false;
        RoomRules rules=RoomRules.read(value);
        if(!restoredPlayerPreferences&&editable()){
            restoredPlayerPreferences=true;
            double current=PvpStageBasis.DEFAULT_CASTLE_HEALTH_MULTIPLIER;
            for(JsonElement e:value.getAsJsonArray("players")){
                JsonObject p=e.getAsJsonObject();if(p.get("id").getAsInt()==playerId){current=p.get("castleHealthMultiplier").getAsDouble();break;}
            }
            double saved=owner.savedCastleHealthMultiplier();
            if(Math.abs(saved-current)>1e-9){
                pending=true;client.setCastleHealthMultiplier(saved,value.get("revision").getAsLong());
                message("保存済みの城体力倍率を復元しています…");refreshControls();return;
            }
        }
        if(!restoredHostPreferences&&host()&&editable()&&owner.creatingRoom()){
            restoredHostPreferences=true;
            RoomRules saved=owner.savedHostRules(rules);
            if(!saved.equals(rules)){
                pending=true;client.setRoomRules(saved,value.get("revision").getAsLong());
                message("保存済みの属性・時間制限設定を復元しています…");refreshControls();return;
            }
        }
        loading=true;
        try{
            if(!dirty||!host()||!editable()){
                distance.setValue(rules.castleDistance);force60.setSelected(true);special.setSelectedItem(rules.specialMode);debugMode.setSelected(rules.debugMode);
                for(int i=0;i<background.getItemCount();i++)if(background.getItemAt(i).id()==rules.backgroundId)background.setSelectedIndex(i);
                for(int i=0;i<music.getItemCount();i++)if(music.getItemAt(i).id()==rules.musicId)music.setSelectedIndex(i);
                selectTrait(hostTrait,rules.hostTraitChoice);selectTrait(guestTrait,rules.guestTraitChoice);
                setExclusionMask(hostTraitExclude,rules.hostTraitExclusions);setExclusionMask(guestTraitExclude,rules.guestTraitExclusions);
                unlimitedTime.setSelected(rules.timeLimitMinutes==RoomRules.UNLIMITED_TIME);
                timeLimit.setValue(rules.timeLimitMinutes==RoomRules.UNLIMITED_TIME?RoomRules.DEFAULT_TIME_LIMIT_MINUTES:rules.timeLimitMinutes);
                maxUnits.setValue(rules.maxUnits);
                castleHitMoneyEnabled.setSelected(rules.castleHitMoneyEnabled);castleHitMoney.setValue(rules.castleHitMoney);
                dirty=false;
            }
        }finally{loading=false;}
        MusicChoice selectedMusic=(MusicChoice)music.getSelectedItem();
        if(musicPreviewing&&(selectedMusic==null||selectedMusic.id()!=musicPreviewId))stopMusicPreview();

        StringBuilder names=new StringBuilder();
        boolean previousLoading=loading;loading=true;
        try{for(JsonElement e:value.getAsJsonArray("players")){JsonObject p=e.getAsJsonObject();
            if(p.get("id").getAsInt()==playerId&&!playerDirty)castleHealthMultiplier.setValue(p.get("castleHealthMultiplier").getAsDouble());
            names.append(p.get("id").getAsInt()==value.get("hostId").getAsInt()?"[ホスト] ":"[参加者] ").append(p.get("name").getAsString()).append("  /  ").append(p.get("seat").getAsString().equals("left")?"左・青":"右・ピンク").append("  / 城HP x").append(String.format(java.util.Locale.ROOT,"%.2f",p.get("castleHealthMultiplier").getAsDouble())).append("  /  ").append(p.get("lobbyReady").getAsBoolean()?"準備完了":"編集中");String label=p.get("lineupName").getAsString();if(!label.isEmpty())names.append("  /  ").append(label);names.append('\n');}}
        finally{loading=previousLoading;}
        participants.setText(names.toString());
        ruleNote.setText("設定バージョン "+value.get("revision").getAsLong()+"。変更すると全員の準備完了を解除します。");refreshControls();
    }

    private void refreshControls(){
        boolean can=editable()&&!ownReady()&&!pending&&!closed,hostCan=can&&host();
        lineup.setEnabled(can);edit.setEnabled(can&&lineup.getSelectedItem()!=null&&!owner.isRandomLineupChoice((BasisLU)lineup.getSelectedItem()));castleHealthMultiplier.setEnabled(can);applyPlayer.setEnabled(can&&playerDirty);
        distance.setEnabled(hostCan);background.setEnabled(hostCan);music.setEnabled(hostCan);special.setEnabled(hostCan);force60.setEnabled(false);debugMode.setEnabled(hostCan);
        musicPreview.setEnabled(!closed&&music.getSelectedItem()!=null);musicStop.setEnabled(!closed&&musicPreviewing);
        hostTrait.setEnabled(hostCan);guestTrait.setEnabled(hostCan);unlimitedTime.setEnabled(hostCan);timeLimit.setEnabled(hostCan&&!unlimitedTime.isSelected());
        maxUnits.setEnabled(hostCan);castleHitMoneyEnabled.setEnabled(hostCan);castleHitMoney.setEnabled(hostCan&&castleHitMoneyEnabled.isSelected());
        setExclusionEnabled(hostTraitExclude,hostCan&&selectedTrait(hostTrait)==PvpTraitRules.RANDOM);
        setExclusionEnabled(guestTraitExclude,hostCan&&selectedTrait(guestTrait)==PvpTraitRules.RANDOM);
        apply.setEnabled(hostCan&&dirty);
        BasisLU selected=(BasisLU)lineup.getSelectedItem();
        randomSort.setEnabled(can&&selected!=null&&owner.isRandomLineupChoice(selected));
        ready.setText(ownReady()?"準備を解除":"準備完了");
        ready.setEnabled(editable()&&!pending&&!closed&&(ownReady()||(!dirty&&!playerDirty&&owner.canReadyLineup(selected))));
        if(!editable()&&state!=null){ready.setText("共有・開始待ち…");edit.setEnabled(false);}
    }

    private void rulesChanged(){if(loading||closed)return;dirty=true;refreshControls();}

    private void previewMusic(){
        if(closed)return;
        MusicChoice choice=(MusicChoice)music.getSelectedItem();
        if(choice==null)return;
        Music track=UserProfile.getBCData().musics.get(choice.id());
        if(track==null||track.data==null){message("試聴できるBGMデータがありません: "+choice);return;}
        BCMusic.stopAll();BCMusic.music=null;
        BCMusic.play(track.id);
        musicPreviewing=true;musicPreviewId=choice.id();musicPreviewStatus.setText("試聴中: "+choice);
        refreshControls();
    }

    private void stopMusicPreview(){
        if(musicPreviewing){BCMusic.stopAll();BCMusic.music=null;}
        musicPreviewing=false;musicPreviewId=-1;musicPreviewStatus.setText("停止中");
        if(!closed)refreshControls();
    }

    private void applyRules(){
        try{
            distance.commitEdit();timeLimit.commitEdit();maxUnits.commitEdit();castleHitMoney.commitEdit();
            BackgroundChoice b=(BackgroundChoice)background.getSelectedItem();MusicChoice m=(MusicChoice)music.getSelectedItem();
            if(b==null||m==null)throw new IllegalArgumentException("背景とBGMを選択してください");
            int hostChoice=selectedTrait(hostTrait),guestChoice=selectedTrait(guestTrait);
            int hostMask=exclusionMask(hostTraitExclude),guestMask=exclusionMask(guestTraitExclude);
            int limit=unlimitedTime.isSelected()?RoomRules.UNLIMITED_TIME:((Number)timeLimit.getValue()).intValue();
            RoomRules r=new RoomRules((Integer)distance.getValue(),b.id(),m.id(),true,(RoomRules.SpecialMode)special.getSelectedItem(),debugMode.isSelected(),
                    hostChoice,guestChoice,hostMask,guestMask,limit,
                    ((Number)maxUnits.getValue()).intValue(),castleHitMoneyEnabled.isSelected(),((Number)castleHitMoney.getValue()).intValue());
            PvpStageBasis.validateRulesAssets(r);owner.rememberHostRules(r);
            pending=true;dirty=false;client.setRoomRules(r,state.get("revision").getAsLong());refreshControls();
        }catch(Exception e){message(e.getMessage());}
    }

    private void applyPlayerRules(){try{castleHealthMultiplier.commitEdit();double value=((Number)castleHealthMultiplier.getValue()).doubleValue();PvpStageBasis.validateCastleHealthMultiplier(value);owner.rememberCastleHealthMultiplier(value);pending=true;playerDirty=false;client.setCastleHealthMultiplier(value,state.get("revision").getAsLong());refreshControls();}catch(Exception e){message(e.getMessage());}}

    void toggleReady(){
        if(closed||!editable()||pending)return;
        stopMusicPreview();
        if(!ownReady())try{
            distance.commitEdit();
            if(dirty)throw new IllegalArgumentException("変更したルールを先に適用してください");
            if(playerDirty)throw new IllegalArgumentException("城体力倍率を先に適用してください");
            BasisLU selected=(BasisLU)lineup.getSelectedItem();
            if(selected==null)throw new IllegalArgumentException("編成を選択してください");
            if(!owner.canReadyLineup(selected))throw new IllegalArgumentException("編成には1体以上のキャラが必要です");
            PvpStageBasis.validateRulesAssets(client.roomRules());
        }catch(Exception e){message(e.getMessage());return;}
        pending=true;client.lobbyReady(!ownReady(),state.get("revision").getAsLong());refreshControls();
    }

    private String summary(){
        BasisLU selected=(BasisLU)lineup.getSelectedItem();
        String s=String.valueOf(selected);
        if(selected!=null&&owner.isRandomLineupChoice(selected)){
            RandomLineupFactory.SortOrder sort=(RandomLineupFactory.SortOrder)randomSort.getSelectedItem();
            if(sort!=null&&sort!=RandomLineupFactory.SortOrder.SHUFFLED)s+=" / "+sort;
        }
        return s.length()>120?s.substring(0,120):s;
    }
    private void preview(){BasisLU b=(BasisLU)lineup.getSelectedItem();boolean random=b!=null&&owner.isRandomLineupChoice(b);for(int i=0;i<10;i++){Form f=b==null||random?null:b.lu.fs[i/5][i%5];icons[i].setIcon(null);icons[i].setText(random?"?":f==null?"—":f.toString());if(f!=null&&f.anim!=null)try{icons[i].setIcon(UtilPC.getIcon(f.anim.getUni()));}catch(Exception ignored){}}}
    private void editLineup(){
        if(!editable()||ownReady()||pending)return;
        stopMusicPreview();
        BasisLU b=(BasisLU)lineup.getSelectedItem();if(b==null||owner.isRandomLineupChoice(b))return;
        for(BasisSet set:BasisSet.list())if(set.lb.contains(b)){BasisSet.setCurrent(set);set.sele=b;break;}
        editing=true;client.setLineupName(summary());
        try {changePanel(new BasisPage(this));}
        catch(RuntimeException e){editing=false;notice("編成画面を開けませんでした: "+e.getMessage());}
    }
    @Override protected void renew(){
        audio.refresh();if(!editing)return;editing=false;loading=true;
        try{owner.populateLineupChoices(lineup,BasisSet.current().sele);}finally{loading=false;}
        preview();pending=true;client.setLineupName(summary());refreshControls();
    }
    void message(String text){status.setText(text==null?"":text);}
    void notice(String text){pending=false;message(text);refreshControls();}
    void closeLobby(){if(closed)return;stopMusicPreview();closed=true;lineup.removeActionListener(lineupListener);refreshControls();}
    @Override protected JButton getBackButton(){return back;}
    @Override protected void resized(int w,int h){setBounds(0,0,w,h);content.setBounds(0,0,w,h);content.revalidate();}

    private static final class TraitChoice{
        final int value;TraitChoice(int value){this.value=value;}
        public String toString(){return PvpTraitRules.label(value);}
    }
    private static final class BackgroundChoice{
        final Background background;final boolean random;
        BackgroundChoice(Background value,boolean random){background=value;this.random=random;}
        int id(){return random?RoomRules.RANDOM_BACKGROUND:background.id.id;}
        public String toString(){return random?"ランダム":background.toString();}
    }
    private static final class MusicChoice{
        final PvpBattleMusic.Entry entry;
        MusicChoice(PvpBattleMusic.Entry value){entry=value;}
        int id(){return entry.id;}
        public String toString(){return entry.displayName();}
    }
}
