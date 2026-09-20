package online.ui;

import com.google.gson.JsonObject;
import common.CommonStatic;
import common.battle.*;
import common.util.unit.Form;
import online.GameFingerprint;
import online.bundle.*;
import online.net.*;
import online.net.duel.DuelRoster;
import online.sync.*;
import page.Page;
import page.MainFrame;
import page.battle.BattleInfoPage;
import main.MainBCU;
import io.BCMusic;
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
import java.util.concurrent.atomic.*;

/** One match per page. Network, deterministic simulation, and Swing presentation are isolated. */
public final class OnlineLobbyPage extends Page implements RoomClient.Listener {
    private static final long serialVersionUID=1L;
    private static final long BATTLE_STEP_NANOS=1_000_000_000L/Protocol.TPS;
    private static final int MAX_PRESENTATION_BACKLOG=1;
    private static final long SNAPSHOT_SOFT_NANOS=8_000_000L,SNAPSHOT_MEDIUM_NANOS=16_000_000L,
            SNAPSHOT_HARD_NANOS=33_000_000L,SNAPSHOT_EXTREME_NANOS=50_000_000L;
    private final JPanel content=new JPanel(new BorderLayout(12,12));
    private final JPanel setup=new JPanel(new GridBagLayout());
    private final JButton back=new JButton("戻る"),create=new JButton("部屋を作成"),join=new JButton("部屋に参加"),ready=new JButton("準備完了"),leave=new JButton("退出"),copyRoom=new JButton("部屋IDをコピー");
    private final JTextField server=new JTextField(LobbyPreferences.DEFAULT_SERVER,32),name=new JTextField(MainBCU.author,24),room=new JTextField(24);
    private final JSpinner udpPortOverride=new JSpinner(new SpinnerNumberModel(0,0,65535,1));
    private final JPasswordField password=new JPasswordField(24);
    private final JComboBox<String> side=new JComboBox<>(new String[]{"味方側（右・ピンク）","敵側（左・青）"});
    private final JComboBox<BasisLU> lineup=new JComboBox<>();
    private final BasisLU randomLineup=choice("ランダム"),randomVanillaLineup=choice("ランダム(バニラ)");
    private final JCheckBox development=new JCheckBox("平文WSを許可（外部サーバーの ws:// 接続を含む・通信は暗号化されません）");
    private final JTextArea status=new JTextArea(5,50);
    private BattleInfoPage battlePage;
    private RoomLobbyPage roomLobby;
    private final ExecutorService io=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"pvp-prepare");t.setDaemon(true);return t;});
    private final ScheduledExecutorService simulation=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"pvp-simulation");t.setDaemon(true);return t;});
    private final javax.swing.Timer pulse=new javax.swing.Timer(5,e->pump());
    private final Object simulationLock=new Object();
    private final AtomicLong battleEpoch=new AtomicLong();
    private final AtomicReference<PvpStageBasis> pendingSnapshot=new AtomicReference<>();
    private final AtomicReference<PvpPresentationDelta> pendingDelta=new AtomicReference<>();
    private final MatchBundle.Mounted[] mounted=new MatchBundle.Mounted[2];
    private final String[] hashes=new String[2];
    private final java.util.List<Path> temporary=new ArrayList<>();
    private RoomClient client;private MatchBundle localBundle;private Path localArchive;private volatile PvpStageBasis battle;
    private String match,hostName,guestName;private int slot=-1,leftSlot=-1,playerId;
    private DuelRoster roster;
    private Future<?> connecting;
    private ScheduledFuture<?> simulationTask;
    private PvpRouletteAudio simulationRouletteAudio;
    private final FriendServerPanel friendServer=new FriendServerPanel((url,udp)->{
        server.setText(url);udpPortOverride.setValue(Math.max(0,udp));
    });
    private final Path preferencesPath=CommonStatic.ctx.getUserFile("online-client.properties").toPath();
    private final javax.swing.Timer saveTimer=new javax.swing.Timer(400,e->savePreferences());
    private LobbyPreferences preferences;
    private boolean preferencesDirty,roomProtected;
    private volatile int generation;
    private boolean creating,uploaded,prepared,busy;
    private volatile boolean resultSent;
    private volatile boolean disposed;
    private long nextPaint,nextBattleStep,lastSnapshotNanos;
    private int snapshotStride=1;

    public OnlineLobbyPage(Page parent){
        super(parent);loadPreferences();content.setBorder(new EmptyBorder(16,18,16,18));add(content);
        JPanel header=new JPanel(new FlowLayout(FlowLayout.LEADING));header.add(back);header.add(new JLabel("オンライン対戦  /  2 PLAYERS  /  固定30TPS"));header.add(leave);
        content.add(header,BorderLayout.NORTH);content.add(setup,BorderLayout.CENTER);
        status.setEditable(false);status.setLineWrap(true);status.setWrapStyleWord(true);content.add(new JScrollPane(status),BorderLayout.SOUTH);
        populateLineupChoices(lineup,BasisSet.current()==null?null:BasisSet.current().sele);
        restoreLocalSetup();
        row(0,"サーバー（TCPはURLの :ポート）",server);
        row(1,"UDPポート（参加先・0=自動）",udpPortOverride);
        row(2,"表示名",name);row(3,"城の位置（作成者）",side);row(4,"部屋ID（参加時）",room);row(5,"パスワード（任意・設定時8文字以上）",password);
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
    private static BasisLU choice(String name){BasisLU b=new BasisLU();b.name=name;return b;}
    boolean isRandomLineupChoice(BasisLU value){return value==randomLineup||value==randomVanillaLineup;}
    void populateLineupChoices(JComboBox<BasisLU> box,BasisLU selected){
        box.removeAllItems();box.addItem(randomLineup);box.addItem(randomVanillaLineup);
        for(BasisSet set:BasisSet.list())for(BasisLU b:set.lb)box.addItem(b);
        if(selected!=null)box.setSelectedItem(selected);
        if(box.getSelectedItem()==null&&box.getItemCount()>0)box.setSelectedIndex(0);
    }
    private BasisLU battleLineup() throws java.io.IOException {
        BasisLU selected=(BasisLU)lineup.getSelectedItem();
        if(selected==null)throw new java.io.IOException("編成を選択してください");
        if(selected==randomLineup)return RandomLineupFactory.create(false);
        if(selected==randomVanillaLineup)return RandomLineupFactory.create(true);
        if(!canReadyLineup(selected))throw new java.io.IOException("編成には1体以上のキャラが必要です");
        return selected;
    }
    boolean canReadyLineup(BasisLU selected){
        if(selected==null)return false;
        if(isRandomLineupChoice(selected))return true;
        for(Form[] row:selected.lu.fs)for(Form form:row)if(form!=null)return true;
        return false;
    }
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
        final int udpOverride=((Number)udpPortOverride.getValue()).intValue();
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
                    connection=new RoomClient(uri,allowDevelopment,true,udpOverride,listener(attempt));
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
    private void setSetupEnabled(boolean enabled){friendServer.connectionActive(!enabled);server.setEnabled(enabled);udpPortOverride.setEnabled(enabled);name.setEnabled(enabled);room.setEnabled(enabled);password.setEnabled(enabled);lineup.setEnabled(enabled);side.setEnabled(enabled);development.setEnabled(enabled);create.setEnabled(enabled);join.setEnabled(enabled);}
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
            case "room_state":
                if(roomLobby!=null)roomLobby.state(e);
                if("EDITING".equals(Protocol.string(e,"phase",16))&&battlePage!=null)finishBattleToLobby("対戦ロビーに戻りました。");
                break;
            case "notice":if(roomLobby!=null)roomLobby.notice(Protocol.string(e,"message",1024));break;
            case "prepare":
                if(playerId<=0)throw new java.io.IOException("No identity assignment");
                if(roomLobby!=null)roomLobby.state(e);
                BasisLU selectedBattleLineup=battleLineup();
                localArchive=MatchBundle.export(selectedBattleLineup);temporary.add(localArchive);localBundle=MatchBundle.read(localArchive);
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
                long battleSeed=Protocol.number(e,"seed");
                online.net.lobby.RoomRules activeRules=client.roomRules();
                int roomHostId=Protocol.integer(e,"hostId");
                int resolvedHostTrait=activeRules.resolvedHostTrait(battleSeed),resolvedGuestTrait=activeRules.resolvedGuestTrait(battleSeed);
                int leftTrait=startedRoster.playerId(startedRoster.leftIndex())==roomHostId?resolvedHostTrait:resolvedGuestTrait;
                int rightTrait=startedRoster.playerId(startedRoster.rightIndex())==roomHostId?resolvedHostTrait:resolvedGuestTrait;
                battle=PvpStageBasis.create(match,mounted[leftSlot].lineup,mounted[1-leftSlot].lineup,battleSeed,leftSlot,activeRules,
                        startedRoster.castleHealthMultiplier(startedRoster.leftIndex()),startedRoster.castleHealthMultiplier(startedRoster.rightIndex()),
                        leftTrait,rightTrait);
                showBattle();break;
            case "result":
                resultSent=true;int winner=Protocol.integer(e,"winner");
                String title=winner==-1?"引き分け":(winner==(slot==leftSlot?0:1)?"勝利！":"敗北");
                message(title+" 両者の結果が一致しました。");
                if(battlePage!=null)battlePage.showOnlineResult(title,"両者の戦闘結果が一致しました。OKを押してください。",client::resultAck);
                break;
            case "result_ack_state":
                if(battlePage!=null)battlePage.onlineResultWaiting("相手のOKを待っています…");break;
            case "battle_cancelled":
                message("対戦が中断されました。ロビーへ戻ります…");break;
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
        // The native battle page is a direct child of the room lobby so Back/result
        // navigation returns to the room instead of unwinding the whole online session.
        int direction=slot==leftSlot?1:-1;
        battlePage=new BattleInfoPage(roomLobby,battle.displayCopy(),direction,
                this::command,this::requestBattleReturn,
                leftSlot==0?hostName:guestName,leftSlot==0?guestName:hostName);
        battlePage.force60Fps(client.roomRules().force60Fps);
        battlePage.onlineStatus(transportLabel(),true);
        changePanel(battlePage);
        battlePage.componentResized(MainFrame.F.getRootPane().getWidth(),MainFrame.F.getRootPane().getHeight());
        simulationRouletteAudio=new PvpRouletteAudio();simulationRouletteAudio.initialize(battle,direction);
        long now=System.nanoTime();nextPaint=now;nextBattleStep=now;lastSnapshotNanos=0;snapshotStride=1;
        long epoch=battleEpoch.incrementAndGet();
        simulationTask=simulation.scheduleWithFixedDelay(()->simulationPump(epoch),0,1,TimeUnit.MILLISECONDS);
        pulse.start();
    }
    /** EDT presentation pump. Authoritative gameplay never advances here. */
    private void pump(){
        if(disposed)return;
        try{
            PvpStageBasis snapshot=pendingSnapshot.getAndSet(null);
            if(snapshot!=null&&battlePage!=null&&!resultSent)battlePage.publishOnlineFromSimulation(snapshot);
            PvpPresentationDelta delta=pendingDelta.getAndSet(null);
            if(delta!=null&&battlePage!=null&&!resultSent)battlePage.applyOnlinePresentationDelta(delta);
            if(battlePage==null)return;
            long now=System.nanoTime();
            if(now>=nextPaint){
                long interval=1_000_000_000L/Math.max(1,battlePage.onlineFps());
                RoomClient connection=client;
                // If network frames accumulated, drawing stale frames only steals CPU
                // from the independent simulation thread. Keep the last frame visible
                // until the authoritative queue is back under control.
                if(connection==null||connection.resolvedFrameBacklog()<=MAX_PRESENTATION_BACKLOG||resultSent){
                    if(!resultSent)battlePage.onlineStatus(transportLabel(),true);
                    battlePage.renderOnlineFrame();
                }
                nextPaint=advanceDeadline(nextPaint,System.nanoTime(),interval);
            }
        }catch(Exception e){failed("表示処理を停止しました: "+e.getMessage());}
    }

    /** Single-threaded deterministic 30TPS simulation, isolated from Swing/Jogl stalls. */
    private void simulationPump(long epoch){
        if(epoch!=battleEpoch.get()||disposed||resultSent)return;
        PvpStageBasis world=battle;RoomClient connection=client;DuelRoster activeRoster=roster;
        if(world==null||connection==null||activeRoster==null)return;
        try{
            long now=System.nanoTime();int backlog=connection.resolvedFrameBacklog();
            boolean catchingUp=backlog>MAX_PRESENTATION_BACKLOG;
            if(!battleStepDue(now,nextBattleStep,backlog))return;
            ResolvedFrame frame=connection.pollResolvedFrame();if(frame==null)return;
            PvpStageBasis display=null;PvpPresentationDelta delta=null;String digest=null;int winner;long tick;
            synchronized(simulationLock){
                if(epoch!=battleEpoch.get()||world!=battle||resultSent)return;
                final InputFrame tickFrame=activeRoster.toDuel(frame);
                final int direction=slot==leftSlot?1:-1;
                PvpAudio.forPlayer(direction,()->world.step(tickFrame));
                if(simulationRouletteAudio!=null)simulationRouletteAudio.observe(world,direction);
                winner=world.winner();boolean terminal=winner!=-2;
                if(!terminal)delta=PvpPresentationDelta.capture(world);
                int effectiveStride=catchingUp?Math.max(3,snapshotStride):snapshotStride;
                if(terminal||world.time%effectiveStride==0){
                    long copyStart=System.nanoTime();display=world.displayCopy();
                    lastSnapshotNanos=System.nanoTime()-copyStart;
                    snapshotStride=presentationStride(world.le.size(),lastSnapshotNanos);
                }
                tick=world.time;
                if(tick%Protocol.HASH_INTERVAL==0||terminal)digest=BattleDigest.of(world);
                if(tick%Protocol.HASH_INTERVAL==0)connection.checkpoint(tick,digest);
                long after=System.nanoTime();
                nextBattleStep=catchingUp?after+BATTLE_STEP_NANOS:advanceDeadline(nextBattleStep,after,BATTLE_STEP_NANOS);
                if(terminal)resultSent=true;
            }
            if(winner!=-2){
                pendingSnapshot.set(null);
                final PvpStageBasis ending=display;final int finalWinner=winner;final long finalTick=tick;final String finalDigest=digest;
                SwingUtilities.invokeAndWait(()->{
                    if(epoch!=battleEpoch.get()||battlePage==null)return;
                    battlePage.publishOnlineFromSimulation(ending);
                    battlePage.beginOnlineBattleEnd();
                });
                if(epoch==battleEpoch.get()){
                    connection.result(finalTick,finalWinner,finalDigest);
                    message("試合終了。両者の結果を照合しています…");
                }
            }else{
                if(display!=null)pendingSnapshot.set(display);
                if(delta!=null)pendingDelta.set(delta);
            }
        }catch(Exception e){
            if(epoch!=battleEpoch.get())return;
            battleEpoch.incrementAndGet();
            failed("同期処理を停止しました: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));
        }
    }

    private void stopSimulation(){
        battleEpoch.incrementAndGet();pendingSnapshot.set(null);pendingDelta.set(null);simulationRouletteAudio=null;
        ScheduledFuture<?> task=simulationTask;simulationTask=null;if(task!=null)task.cancel(false);
    }

    static int presentationStride(int entities,long copyNanos){
        // Never skip simulation ticks. Only reduce how often the expensive full
        // presentation graph is cloned when a machine cannot clone it inside one
        // 33.3ms logic budget.
        int byCount=entities>=400?3:entities>=250?2:1;
        int byCost=copyNanos>=SNAPSHOT_EXTREME_NANOS?5:
                copyNanos>=SNAPSHOT_HARD_NANOS?4:
                copyNanos>=SNAPSHOT_MEDIUM_NANOS?3:
                copyNanos>=SNAPSHOT_SOFT_NANOS?2:1;
        return Math.max(byCount,byCost);
    }
    static boolean battleStepDue(long now,long deadline,int backlog){
        return backlog>MAX_PRESENTATION_BACKLOG||now>=deadline;
    }
    static long advanceDeadline(long deadline,long now,long interval){
        if(interval<=0)throw new IllegalArgumentException("interval");
        if(deadline<=0)return now+interval;
        if(deadline>now)return deadline;
        long periods=(now-deadline)/interval+1L;
        return deadline+periods*interval;
    }
    private void requestBattleReturn(){
        if(client==null||roomLobby==null)return;
        stopSimulation();
        client.abortBattle();roomLobby.message("対戦を中断してロビーへ戻っています…");
        if(MainFrame.getPanel()!=roomLobby){changePanel(roomLobby);roomLobby.componentResized(MainFrame.F.getRootPane().getWidth(),MainFrame.F.getRootPane().getHeight());}
    }
    private void finishBattleToLobby(String text){
        stopSimulation();pulse.stop();PvpSoundBank.stopAll();BCMusic.stopAll();BCMusic.music=null;
        if(battlePage!=null){battlePage.detachOnline();battlePage=null;}
        synchronized(simulationLock){
            battle=null;
            for(MatchBundle.Mounted m:mounted)if(m!=null)m.close();Arrays.fill(mounted,null);Arrays.fill(hashes,null);
        }
        for(Path p:temporary)delete(p);temporary.clear();localBundle=null;localArchive=null;roster=null;
        hostName=null;guestName=null;slot=-1;leftSlot=-1;uploaded=false;prepared=false;resultSent=false;
        if(roomLobby!=null){roomLobby.notice(text);if(MainFrame.getPanel()!=roomLobby){changePanel(roomLobby);roomLobby.componentResized(MainFrame.F.getRootPane().getWidth(),MainFrame.F.getRootPane().getHeight());}}
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
        try{preferences=LobbyPreferences.load(preferencesPath,MainBCU.author);server.setText(preferences.serverAddress);udpPortOverride.setValue(preferences.udpPortOverride);name.setText(preferences.displayName);}
        catch(java.io.IOException e){System.err.println("BCU online preferences: "+e.getMessage());preferences=new LobbyPreferences(LobbyPreferences.DEFAULT_SERVER,MainBCU.author);server.setText(preferences.serverAddress);udpPortOverride.setValue(preferences.udpPortOverride);name.setText(preferences.displayName);}
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
        udpPortOverride.addChangeListener(e->{preferencesDirty=true;saveTimer.restart();});
        side.addActionListener(e->{preferencesDirty=true;saveTimer.restart();});
        lineup.addActionListener(e->{preferencesDirty=true;saveTimer.restart();});
    }
    private void savePreferences(){
        saveTimer.stop();if(!preferencesDirty)return;
        try{
            int udpOverride=((Number)udpPortOverride.getValue()).intValue();
            if(preferences==null)preferences=new LobbyPreferences(server.getText(),name.getText()).withConnection(server.getText(),name.getText(),udpOverride);
            else preferences=preferences.withConnection(server.getText(),name.getText(),udpOverride);
            preferences=preferences.withLocalSetup(side.getSelectedIndex(),selectedLineupKind(),selectedLineupSet(),selectedLineupIndex());
            preferences.save(preferencesPath);preferencesDirty=false;
        }
        catch(java.io.IOException e){System.err.println("BCU online preferences: "+e.getMessage());if(!disposed)status.append("\n設定の保存に失敗しました: "+e.getMessage());}
    }
    boolean creatingRoom(){return creating;}
    LobbyPreferences preferences(){return preferences==null?new LobbyPreferences(server.getText(),name.getText()):preferences;}
    online.net.lobby.RoomRules savedHostRules(online.net.lobby.RoomRules base){return preferences().hostRules();}
    double savedCastleHealthMultiplier(){return preferences().castleHealthMultiplier;}
    void rememberHostRules(online.net.lobby.RoomRules rules){
        preferences=preferences().withHostRules(rules);preferencesDirty=true;saveTimer.restart();
    }
    void rememberCastleHealthMultiplier(double value){
        preferences=preferences().withCastleHealth(value);preferencesDirty=true;saveTimer.restart();
    }
    private int selectedLineupKind(){
        Object value=lineup.getSelectedItem();
        if(value==randomLineup)return LobbyPreferences.LINEUP_RANDOM;
        if(value==randomVanillaLineup)return LobbyPreferences.LINEUP_RANDOM_VANILLA;
        return LobbyPreferences.LINEUP_SAVED;
    }
    private int selectedLineupSet(){
        Object value=lineup.getSelectedItem();if(!(value instanceof BasisLU)||isRandomLineupChoice((BasisLU)value))return -1;
        java.util.List<BasisSet> sets=BasisSet.list();
        for(int i=0;i<sets.size();i++)if(sets.get(i).lb.contains(value))return i;
        return -1;
    }
    private int selectedLineupIndex(){
        Object value=lineup.getSelectedItem();if(!(value instanceof BasisLU)||isRandomLineupChoice((BasisLU)value))return -1;
        for(BasisSet set:BasisSet.list()){int i=set.lb.indexOf(value);if(i>=0)return i;}return -1;
    }
    private void restoreLocalSetup(){
        LobbyPreferences p=preferences();side.setSelectedIndex(Math.max(0,Math.min(1,p.creatorSideIndex)));
        if(p.lineupKind==LobbyPreferences.LINEUP_RANDOM){lineup.setSelectedItem(randomLineup);return;}
        if(p.lineupKind==LobbyPreferences.LINEUP_RANDOM_VANILLA){lineup.setSelectedItem(randomVanillaLineup);return;}
        java.util.List<BasisSet> sets=BasisSet.list();
        if(p.lineupSetIndex>=0&&p.lineupSetIndex<sets.size()){
            BasisSet set=sets.get(p.lineupSetIndex);
            if(p.lineupIndex>=0&&p.lineupIndex<set.lb.size())lineup.setSelectedItem(set.lb.get(p.lineupIndex));
        }
    }
    /** Leave/cancel a match, not the embedded server. Stale socket/asset callbacks are fenced out. */
    private boolean isSessionChild(){Page p=MainFrame.getPanel();if(p==this)return false;while(p!=null){if(p==this)return true;p=p.getFront();}return false;}
    void returnToConnection(){boolean child=isSessionChild();savePreferences();resetMatch();if(child)changePanel(this);message("部屋から退出しました。友人用サーバーは維持しています。");}
    private void resetMatch(){
        generation++;stopSimulation();pulse.stop();if(connecting!=null){connecting.cancel(true);connecting=null;}
        RoomClient previous=client;client=null;
        if(previous!=null)previous.close();
        if(battlePage!=null){battlePage.detachOnline();battlePage=null;}
        if(roomLobby!=null){roomLobby.closeLobby();roomLobby=null;}
        synchronized(simulationLock){
            battle=null;
            for(MatchBundle.Mounted m:mounted)if(m!=null)m.close();Arrays.fill(mounted,null);Arrays.fill(hashes,null);
        }
        for(Path p:temporary)delete(p);temporary.clear();localBundle=null;localArchive=null;roster=null;
        match=null;hostName=null;guestName=null;slot=-1;leftSlot=-1;playerId=0;
        creating=false;uploaded=false;prepared=false;resultSent=false;busy=false;roomProtected=false;
        Component center=((BorderLayout)content.getLayout()).getLayoutComponent(BorderLayout.CENTER);
        if(center!=setup){if(center!=null)content.remove(center);content.add(setup,BorderLayout.CENTER);content.revalidate();content.repaint();}
        setSetupEnabled(true);ready.setEnabled(false);leave.setEnabled(false);copyRoom.setEnabled(false);
    }
    private static void delete(Path p){try{Files.deleteIfExists(p);}catch(java.io.IOException ignored){}}
    private void cleanup(){if(disposed)return;savePreferences();disposed=true;resetMatch();friendServer.close();io.shutdownNow();simulation.shutdownNow();}
    // Opening the native child battle page must not tear down its sockets/server/packs.
    @Override protected void leave(){if(battlePage==null&&roomLobby==null)cleanup();}
    @Override protected void exit(){cleanup();}
}
