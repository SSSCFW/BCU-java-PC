package online.ui;

import com.google.gson.JsonObject;
import common.CommonStatic;
import common.battle.*;
import online.GameFingerprint;
import online.bundle.*;
import online.net.*;
import online.net.duel.DuelRoster;
import online.sync.*;
import page.Page;
import page.MainFrame;
import page.battle.BattleInfoPage;
import main.MainBCU;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** One match per page. Network I/O stays off the EDT; game registry/simulation stay on it. */
public final class OnlineLobbyPage extends Page implements RoomClient.Listener {
    private static final long serialVersionUID=1L;
    private final JPanel content=new JPanel(new BorderLayout(12,12));
    private final JPanel setup=new JPanel(new GridBagLayout());
    private final JButton back=new JButton("戻る"),create=new JButton("部屋を作成"),join=new JButton("部屋に参加"),ready=new JButton("準備完了"),leave=new JButton("退出"),copyRoom=new JButton("部屋IDをコピー");
    private final JTextField server=new JTextField(LobbyPreferences.DEFAULT_SERVER,32),name=new JTextField(MainBCU.author,24),room=new JTextField(24);
    private final JPasswordField password=new JPasswordField(24);
    private final JComboBox<String> side=new JComboBox<>(new String[]{"味方側（右・ピンク）","敵側（左・青）"});
    private final JComboBox<BasisLU> lineup=new JComboBox<>();
    private final JCheckBox development=new JCheckBox("信頼するLAN／暗号化VPNでWSを許可（公開回線はWSS）");
    private final JTextArea status=new JTextArea(5,50);
    private BattleInfoPage battlePage;
    private RoomLobbyPage roomLobby;
    private final ExecutorService io=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"pvp-prepare");t.setDaemon(true);return t;});
    private final javax.swing.Timer pulse=new javax.swing.Timer(5,e->pump());
    private final MatchBundle.Mounted[] mounted=new MatchBundle.Mounted[2];
    private final String[] hashes=new String[2];
    private final java.util.List<Path> temporary=new ArrayList<>();
    private RoomClient client;private MatchBundle localBundle;private Path localArchive;private PvpStageBasis battle;
    private String match,hostName,guestName;private int slot=-1,leftSlot=-1,playerId;
    private DuelRoster roster;
    private Future<?> connecting;
    private final FriendServerPanel friendServer=new FriendServerPanel(server::setText);
    private final Path preferencesPath=CommonStatic.ctx.getUserFile("online-client.properties").toPath();
    private final javax.swing.Timer saveTimer=new javax.swing.Timer(400,e->savePreferences());
    private boolean preferencesDirty,roomProtected;
    private volatile int generation;
    private boolean creating,uploaded,prepared,resultSent,busy;
    private volatile boolean disposed;
    private long nextPaint;

    public OnlineLobbyPage(Page parent){
        super(parent);loadPreferences();content.setBorder(new EmptyBorder(16,18,16,18));add(content);
        JPanel header=new JPanel(new FlowLayout(FlowLayout.LEADING));header.add(back);header.add(new JLabel("オンライン対戦  /  2 PLAYERS  /  固定30TPS"));header.add(leave);
        content.add(header,BorderLayout.NORTH);content.add(setup,BorderLayout.CENTER);
        status.setEditable(false);status.setLineWrap(true);status.setWrapStyleWord(true);content.add(new JScrollPane(status),BorderLayout.SOUTH);
        for(BasisSet set:BasisSet.list())for(BasisLU b:set.lb)lineup.addItem(b);
        if(BasisSet.current()!=null)lineup.setSelectedItem(BasisSet.current().sele);
        row(0,"サーバー",server);row(1,"表示名",name);row(3,"城の位置（作成者）",side);row(4,"部屋ID（参加時）",room);row(5,"パスワード（任意・設定時8文字以上）",password);
        row(7,"",development);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEADING));actions.add(create);actions.add(join);row(8,"",actions);row(9,"友人用サーバー",friendServer);
        ready.setEnabled(false);leave.setEnabled(false);copyRoom.setEnabled(false);
        back.addActionListener(e->{cleanup();changePanel(front);});leave.addActionListener(e->returnToConnection());
        create.addActionListener(e->connect(true));join.addActionListener(e->connect(false));
        copyRoom.addActionListener(e->Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(room.getText()),null));
        ready.addActionListener(e->{if(roomLobby!=null)roomLobby.toggleReady();});
        message("同じオンライン対応版と標準データが必要です。自作キャラは一時共有し、元データは変更しません。\nパスワードは空欄なら不要です。同一PCテストでは2つのBCUを起動し、同じ接続先と部屋IDで参加してください。");
        bindPreferences();
    }
    private void row(int row,String text,Component field){GridBagConstraints c=new GridBagConstraints();c.gridy=row;c.insets=new Insets(7,8,7,8);c.anchor=GridBagConstraints.WEST;c.gridx=0;setup.add(new JLabel(text),c);c.gridx=1;c.fill=GridBagConstraints.HORIZONTAL;c.weightx=1;setup.add(field,c);}
    @Override protected JButton getBackButton(){return back;}
    @Override protected void resized(int w,int h){setBounds(0,0,w,h);content.setBounds(0,0,w,h);content.revalidate();}
    private void connect(boolean createRoom){
        if(busy||client!=null||disposed)return;
        savePreferences();
        char[] secret=password.getPassword();String pass=new String(secret);Arrays.fill(secret,'\0');
        if(pass.length()>128||pass.indexOf('\0')>=0||(createRoom&&!pass.isEmpty()&&pass.length()<8)){
            message("パスワードなしで作る場合は空欄にしてください。設定する場合は8〜128文字です。");return;
        }
        String playerName=name.getText().trim(),roomId=room.getText().trim(),castleSide=side.getSelectedIndex()==0?"right":"left";
        if(playerName.isEmpty()||playerName.length()>40||playerName.indexOf('\0')>=0 ){message("1〜40文字の表示名を指定してください。");return;}
        if(!createRoom&&roomId.isEmpty()){message("起動済みサーバーへの接続だけでは入室できません。ホストが作成した部屋IDを入力してください。");return;}
        boolean allowDevelopment=development.isSelected();
        URI uri;try{uri=RoomClient.validateUri(new URI(server.getText().trim()),allowDevelopment);}catch(Exception e){message("接続先を確認してください: "+e.getMessage());return;}
        final int attempt=++generation;
        creating=createRoom;busy=true;setSetupEnabled(false);leave.setEnabled(true);message("サーバーへ接続しています…");
        // Export reads mutable editor objects on the EDT. Network callbacks belong to this attempt only.
        SwingUtilities.invokeLater(()->{
            if(!current(attempt))return;
            connecting=io.submit(()->{
                RoomClient connection=null;
                try{
                    if(!current(attempt))return;
                    String game=GameFingerprint.compute(text->message(attempt,text));
                    if(!current(attempt))return;
                    connection=new RoomClient(uri,allowDevelopment,listener(attempt));
                    final RoomClient candidate=connection;
                    SwingUtilities.invokeAndWait(()->{if(current(attempt))client=candidate;});
                    if(!current(attempt)){connection.close();return;}
                    if(!connection.connectBlocking(15,TimeUnit.SECONDS))throw new java.io.IOException("サーバーへ接続できません: "+uri);
                    if(!current(attempt)){connection.close();return;}
                    connection.enter(createRoom,roomId,pass,playerName,castleSide,game);
                }catch(Exception e){if(connection!=null)connection.close();failed(attempt,"接続失敗: "+e.getMessage());}
            });
        });
    }
    private boolean current(int attempt){return !disposed&&attempt==generation;}
    private RoomClient.Listener listener(int attempt){return new RoomClient.Listener(){
        public void event(JsonObject event){dispatch(attempt,event);}
        public void bundle(int id,Path file,String hash){receiveBundle(attempt,id,file,hash);}
        public void failed(String reason){OnlineLobbyPage.this.failed(attempt,reason);}
    };}
    private void setSetupEnabled(boolean enabled){friendServer.connectionActive(!enabled);server.setEnabled(enabled);name.setEnabled(enabled);room.setEnabled(enabled);password.setEnabled(enabled);lineup.setEnabled(enabled);side.setEnabled(enabled);development.setEnabled(enabled);create.setEnabled(enabled);join.setEnabled(enabled);}
    @Override public void event(JsonObject event){dispatch(generation,event);}
    private void dispatch(int attempt,JsonObject event){SwingUtilities.invokeLater(()->{if(!current(attempt))return;try{handle(event);}catch(Exception e){failed("対戦の準備に失敗: "+e.getMessage());}});}
    private void handle(JsonObject e)throws Exception{
        String type=Protocol.string(e,"type",32);
        switch(type){
            case "connected":message("サーバーに接続しました。");break;
            case "joined":
                roomProtected=!e.has("passwordRequired")||e.get("passwordRequired").getAsBoolean();
                match=Protocol.string(e,"match",32);playerId=Protocol.integer(e,"playerId");if(playerId<=0)throw new java.io.IOException("Invalid identity");
                room.setText(Protocol.string(e,"room",32));copyRoom.setEnabled(true);password.setText("");

                roomLobby=new RoomLobbyPage(this,client,playerId,room.getText(),roomProtected,lineup,ready);
                changePanel(roomLobby);roomLobby.componentResized(MainFrame.F.getRootPane().getWidth(),MainFrame.F.getRootPane().getHeight());
                message("入室しました。対戦ロビーで編成とルールを設定できます。");break;
            case "room_state":if(roomLobby!=null)roomLobby.state(e);break;
            case "notice":if(roomLobby!=null)roomLobby.notice(Protocol.string(e,"message",1024));break;
            case "prepare":
                if(playerId<=0)throw new java.io.IOException("No identity assignment");
                if(roomLobby!=null)roomLobby.state(e);
                localArchive=MatchBundle.export((BasisLU)lineup.getSelectedItem());temporary.add(localArchive);localBundle=MatchBundle.read(localArchive);
                roster=DuelRoster.read(e);slot=roster.indexOf(playerId);leftSlot=roster.leftIndex();prepared=true;
                hostName=roster.name(0);guestName=roster.name(1);
                hashes[slot]=Hashes.sha256(localArchive);mounted[slot]=localBundle.mount(match,playerId);
                message("キャラクターのデータを自動共有しています…");client.sendBundle(localArchive);break;
            case "bundle_ok":uploaded=true;updateReady();break;
            case "transport_selected":message("戦闘通信: "+Protocol.string(e,"transport",8)+(prepared?"。キャラデータを共有しています。":"。対戦ロビーで編成・ルールを確認してください。"));updateReady();break;
            case "start":
                if(!uploaded||mounted[0]==null||mounted[1]==null)throw new java.io.IOException("Character synchronization not complete");
                DuelRoster startedRoster=DuelRoster.read(e);
                if(startedRoster.playerId(0)!=roster.playerId(0)||startedRoster.playerId(1)!=roster.playerId(1)||startedRoster.leftIndex()!=leftSlot)
                    throw new java.io.IOException("Duel roster changed at start");
                battle=PvpStageBasis.create(match,mounted[leftSlot].lineup,mounted[1-leftSlot].lineup,Protocol.number(e,"seed"),leftSlot,client.roomRules());
                showBattle();break;
            case "result":
                resultSent=true;int winner=Protocol.integer(e,"winner");
                message(winner==-1?"引き分けです。両者の結果が一致しました。":(winner==(slot==leftSlot?0:1)?"勝利！":"敗北")+" 両者の結果が一致しました。");break;
            default:break;
        }
    }
    @Override public void bundle(int remoteId,Path verified,String hash){receiveBundle(generation,remoteId,verified,hash);}
    private void receiveBundle(int attempt,int remoteId,Path verified,String hash){
        if(!current(attempt)){delete(verified);return;}
        try{io.execute(()->{try{
            if(!current(attempt)){delete(verified);return;}
            MatchBundle incoming=MatchBundle.read(verified);SwingUtilities.invokeLater(()->{
                if(!current(attempt)){delete(verified);return;}temporary.add(verified);
                try{if(roster==null)throw new java.io.IOException("Roster not ready");int remote=roster.indexOf(remoteId);
                    if(remote==slot||mounted[remote]!=null)throw new java.io.IOException("Unexpected opponent bundle");
                    mounted[remote]=incoming.mount(match,remoteId);hashes[remote]=hash;updateReady();}
                catch(Exception e){failed(attempt,"受信キャラの読み込み失敗: "+e.getMessage());}
            });
        }catch(Exception e){delete(verified);failed(attempt,"受信データを拒否: "+e.getMessage());}});}
        catch(RejectedExecutionException e){delete(verified);}
    }
    private void updateReady(){if(uploaded&&prepared&&mounted[0]!=null&&mounted[1]!=null&&!client.realtimeTransport().equals("PROBING")){client.ready();message("両者のキャラクターを同期しました。対戦を開始しています…");}}
    private void showBattle(){
        battlePage=new BattleInfoPage(this,battle.displayCopy(),slot==leftSlot?1:-1,
                this::command,()->{resetMatch();message("部屋から退出しました。友人用サーバーは維持しています。");},
                leftSlot==0?hostName:guestName,leftSlot==0?guestName:hostName);
        battlePage.force60Fps(client.roomRules().force60Fps);
        battlePage.onlineStatus(transportLabel(),true);
        changePanel(battlePage);
        battlePage.componentResized(MainFrame.F.getRootPane().getWidth(),MainFrame.F.getRootPane().getHeight());
        nextPaint=0;pulse.start();
    }
    private void pump(){
        if(disposed||battle==null)return;
        try{
            int advanced=0;ResolvedFrame frame;
            while(!resultSent&&advanced<5&&(frame=client.pollResolvedFrame())!=null){
                final InputFrame tickFrame=roster.toDuel(frame);
                PvpAudio.forPlayer(slot==leftSlot?1:-1,()->battle.step(tickFrame));advanced++;
                if(battle.time%Protocol.HASH_INTERVAL==0)client.checkpoint(battle.time,BattleDigest.of(battle));
                if(battle.winner()!=-2){resultSent=true;client.result(battle.time,battle.winner(),BattleDigest.of(battle));message("試合終了。両者の結果を照合しています…");}
            }
            if(battlePage==null)return;
            if(advanced>0)battlePage.publishOnline(battle.displayCopy());
            long now=System.nanoTime();if(now>=nextPaint){
                if(!resultSent)battlePage.onlineStatus(transportLabel(),true);
                battlePage.renderOnlineFrame();
                nextPaint=now+1_000_000_000L/battlePage.onlineFps();
            }
        }catch(Exception e){failed("同期処理を停止しました: "+e.getMessage());}
    }
    private String transportLabel(){
        online.net.realtime.ReliabilityWindow.Metrics m=client.udpMetrics();
        return m==null?client.realtimeTransport():String.format(Locale.ROOT,"UDP RTT %.0fms / 揺れ %.0fms / 損失 %.1f%%",m.smoothedRttMillis,m.jitterMillis,m.lossRate*100);
    }
    private void command(int bit){if(client!=null&&battle!=null&&!resultSent)client.queueCommand(bit);}
    @Override public void failed(String reason){failed(generation,reason);}
    private void failed(int attempt,String reason){SwingUtilities.invokeLater(()->{
        if(!current(attempt))return;
        System.err.println("BCU online: "+reason);
        boolean child=isSessionChild();
        resetMatch();
        if(child)changePanel(this);
        message(reason+"\n入力を修正して、そのまま作成／参加を再試行できます。友人用サーバーは停止していません。");
    });}
    private void message(int attempt,String text){SwingUtilities.invokeLater(()->{if(current(attempt))message(text);});}
    private void message(String text){if(SwingUtilities.isEventDispatchThread()){
        if(battlePage!=null)battlePage.onlineStatus(text,!resultSent);
        if(roomLobby!=null)roomLobby.message(text);
        if(!disposed)status.setText((playerId>0?"部屋ID: "+room.getText()+" / "+(roomProtected?"パスワードあり":"パスワードなし")+"\n":"")+text);
    }else{int attempt=generation;message(attempt,text);}}
    private void loadPreferences(){
        try{LobbyPreferences saved=LobbyPreferences.load(preferencesPath,MainBCU.author);server.setText(saved.serverAddress);name.setText(saved.displayName);}
        catch(java.io.IOException e){System.err.println("BCU online preferences: "+e.getMessage());}
    }
    private void bindPreferences(){
        saveTimer.setRepeats(false);
        DocumentListener listener=new DocumentListener(){
            private void changed(){preferencesDirty=true;saveTimer.restart();}
            public void insertUpdate(DocumentEvent e){changed();}
            public void removeUpdate(DocumentEvent e){changed();}
            public void changedUpdate(DocumentEvent e){changed();}
        };
        server.getDocument().addDocumentListener(listener);name.getDocument().addDocumentListener(listener);
    }
    private void savePreferences(){
        saveTimer.stop();if(!preferencesDirty)return;
        try{new LobbyPreferences(server.getText(),name.getText()).save(preferencesPath);preferencesDirty=false;}
        catch(java.io.IOException e){System.err.println("BCU online preferences: "+e.getMessage());if(!disposed)status.append("\n表示名・サーバーの保存に失敗しました: "+e.getMessage());}
    }
    /** Leave/cancel a match, not the embedded server. Stale socket/asset callbacks are fenced out. */
    private boolean isSessionChild(){Page p=MainFrame.getPanel();if(p==this)return false;while(p!=null){if(p==this)return true;p=p.getFront();}return false;}
    void returnToConnection(){boolean child=isSessionChild();savePreferences();resetMatch();if(child)changePanel(this);message("部屋から退出しました。友人用サーバーは維持しています。");}
    private void resetMatch(){
        generation++;pulse.stop();if(connecting!=null){connecting.cancel(true);connecting=null;}
        RoomClient previous=client;client=null;
        if(previous!=null)previous.close();
        if(battlePage!=null){battlePage.detachOnline();battlePage=null;}
        battle=null;
        if(roomLobby!=null){roomLobby.closeLobby();roomLobby=null;}
        for(MatchBundle.Mounted m:mounted)if(m!=null)m.close();Arrays.fill(mounted,null);Arrays.fill(hashes,null);
        for(Path p:temporary)delete(p);temporary.clear();localBundle=null;localArchive=null;roster=null;
        match=null;hostName=null;guestName=null;slot=-1;leftSlot=-1;playerId=0;
        creating=false;uploaded=false;prepared=false;resultSent=false;busy=false;roomProtected=false;
        Component center=((BorderLayout)content.getLayout()).getLayoutComponent(BorderLayout.CENTER);
        if(center!=setup){if(center!=null)content.remove(center);content.add(setup,BorderLayout.CENTER);content.revalidate();content.repaint();}
        setSetupEnabled(true);ready.setEnabled(false);leave.setEnabled(false);copyRoom.setEnabled(false);
    }
    private static void delete(Path p){try{Files.deleteIfExists(p);}catch(java.io.IOException ignored){}}
    private void cleanup(){if(disposed)return;savePreferences();disposed=true;resetMatch();friendServer.close();io.shutdownNow();}
    // Opening the native child battle page must not tear down its sockets/server/packs.
    @Override protected void leave(){if(battlePage==null&&roomLobby==null)cleanup();}
    @Override protected void exit(){cleanup();}
}
