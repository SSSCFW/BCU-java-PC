package online.ui;

import com.google.gson.JsonObject;
import common.CommonStatic;
import common.battle.*;
import common.util.unit.Form;
import online.GameFingerprint;
import online.bundle.*;
import online.net.*;
import online.sync.*;
import page.Page;
import main.MainBCU;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
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
    private final JTextField server=new JTextField("wss://",32),name=new JTextField(MainBCU.author,24),room=new JTextField(24);
    private final JPasswordField password=new JPasswordField(24);
    private final JComboBox<String> side=new JComboBox<>(new String[]{"味方側（右・ピンク）","敵側（左・青）"});
    private final JComboBox<BasisLU> lineup=new JComboBox<>();
    private final JCheckBox share=new JCheckBox("参照Pack全体の自動共有に同意する（未編成キャラも含む）");
    private final JCheckBox development=new JCheckBox("LAN開発用の暗号化なしWSを許可（インターネットでは使用しない）");
    private final JTextArea status=new JTextArea(5,50);
    private final JPanel controls=new JPanel(new GridLayout(2,5,5,5));
    private final JButton[] units=new JButton[10];
    private final JButton worker=new JButton("働きネコ"),cannon=new JButton("にゃんこ砲");
    private final JComboBox<Integer> render=new JComboBox<>(new Integer[]{30,60});
    private final JLabel economy=new JLabel(" ");
    private final PvpCanvas canvas=new PvpCanvas();
    private final ExecutorService io=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"pvp-prepare");t.setDaemon(true);return t;});
    private final javax.swing.Timer pulse=new javax.swing.Timer(5,e->pump());
    private final MatchBundle.Mounted[] mounted=new MatchBundle.Mounted[2];
    private final String[] hashes=new String[2];
    private final java.util.List<Path> temporary=new ArrayList<>();
    private RoomClient client;private MatchBundle localBundle;private Path localArchive;private PvpStageBasis battle;
    private String match,hostName,guestName;private int slot=-1,leftSlot=-1;
    private boolean creating,uploaded,prepared,resultSent,busy;
    private volatile boolean disposed;
    private long nextPaint;

    public OnlineLobbyPage(Page parent){
        super(parent);content.setBorder(new EmptyBorder(16,18,16,18));add(content);
        JPanel header=new JPanel(new FlowLayout(FlowLayout.LEADING));header.add(back);header.add(new JLabel("オンライン対戦  /  2 PLAYERS  /  固定30TPS"));header.add(leave);
        content.add(header,BorderLayout.NORTH);content.add(setup,BorderLayout.CENTER);
        status.setEditable(false);status.setLineWrap(true);status.setWrapStyleWord(true);content.add(new JScrollPane(status),BorderLayout.SOUTH);
        for(BasisSet set:BasisSet.list())for(BasisLU b:set.lb)lineup.addItem(b);
        if(BasisSet.current()!=null)lineup.setSelectedItem(BasisSet.current().sele);
        row(0,"サーバー",server);row(1,"表示名",name);row(2,"編成",lineup);row(3,"城の位置（作成者）",side);row(4,"部屋ID（参加時）",room);row(5,"パスワード（8文字以上）",password);
        row(6,"",share);row(7,"",development);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEADING));actions.add(create);actions.add(join);actions.add(copyRoom);actions.add(ready);row(8,"",actions);
        ready.setEnabled(false);leave.setEnabled(false);copyRoom.setEnabled(false);
        render.setSelectedItem(CommonStatic.getConfig().performanceModeBattle?60:30);
        back.addActionListener(e->{cleanup();changePanel(front);});leave.addActionListener(e->{cleanup();changePanel(new OnlineLobbyPage(front));});
        create.addActionListener(e->connect(true));join.addActionListener(e->connect(false));
        copyRoom.addActionListener(e->Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(room.getText()),null));
        ready.addActionListener(e->{ready.setEnabled(false);client.ready(hashes[0],hashes[1]);message("準備完了。相手の準備を待っています。");});
        render.addActionListener(e->canvas.fps((Integer)render.getSelectedItem()));
        worker.addActionListener(e->command(InputFrame.WORKER));cannon.addActionListener(e->command(InputFrame.CANNON));
        for(int i=0;i<10;i++){final int index=i;units[i]=new JButton();units[i].addActionListener(e->command(1<<index));
            units[i].addMouseListener(new MouseAdapter(){@Override public void mousePressed(MouseEvent e){if(SwingUtilities.isRightMouseButton(e))command(1<<(12+index));}});controls.add(units[i]);}
        message("同じオンライン対応版と標準データが必要です。自作キャラは部屋内で一時共有し、元データは変更しません。\n部屋IDとパスワードを相手に伝えてください。");
    }
    private void row(int row,String text,Component field){GridBagConstraints c=new GridBagConstraints();c.gridy=row;c.insets=new Insets(7,8,7,8);c.anchor=GridBagConstraints.WEST;c.gridx=0;setup.add(new JLabel(text),c);c.gridx=1;c.fill=GridBagConstraints.HORIZONTAL;c.weightx=1;setup.add(field,c);}
    @Override protected JButton getBackButton(){return back;}
    @Override protected void resized(int w,int h){setBounds(0,0,w,h);content.setBounds(0,0,w,h);content.revalidate();}
    private void connect(boolean createRoom){
        if(busy||client!=null)return;
        if(!share.isSelected()){message("カスタムデータの自動共有への同意が必要です。");return;}
        char[] secret=password.getPassword();String pass=new String(secret);Arrays.fill(secret,'\0');
        if(pass.length()<8||pass.length()>128||name.getText().trim().isEmpty()||lineup.getSelectedItem()==null){message("表示名・編成・8〜128文字のパスワードを入力してください。");return;}
        boolean allowDevelopment=development.isSelected();
        URI uri;try{uri=RoomClient.validateUri(new URI(server.getText().trim()),allowDevelopment);}catch(Exception e){message(e.getMessage());return;}
        String playerName=name.getText().trim(),roomId=room.getText().trim(),castleSide=side.getSelectedIndex()==0?"right":"left";
        creating=createRoom;busy=true;setSetupEnabled(false);leave.setEnabled(true);message("編成とカスタムデータを固定しています…");
        // Run after the progress label paints. Export reads mutable editor objects on the EDT.
        SwingUtilities.invokeLater(()->{
            if(disposed)return;
            try{localArchive=MatchBundle.export((BasisLU)lineup.getSelectedItem());temporary.add(localArchive);localBundle=MatchBundle.read(localArchive);}
            catch(Exception e){failed("キャラクターの共有準備に失敗: "+e.getMessage());return;}
            io.execute(()->{
                try{
                    String game=GameFingerprint.compute(this::message);
                    if(disposed)return;
                    RoomClient connection=new RoomClient(uri,allowDevelopment,this);
                    SwingUtilities.invokeAndWait(()->{if(!disposed)client=connection;});
                    if(disposed){connection.close();return;}
                    if(!connection.connectBlocking(15,TimeUnit.SECONDS))throw new java.io.IOException("サーバーへ接続できません");
                    connection.enter(createRoom,roomId,pass,playerName,castleSide,game);
                }catch(Exception e){failed("接続失敗: "+e.getMessage());}
            });
        });
    }
    private void setSetupEnabled(boolean enabled){server.setEnabled(enabled);name.setEnabled(enabled);room.setEnabled(enabled);password.setEnabled(enabled);lineup.setEnabled(enabled);side.setEnabled(enabled);development.setEnabled(enabled);share.setEnabled(enabled);create.setEnabled(enabled);join.setEnabled(enabled);}
    @Override public void event(JsonObject event){SwingUtilities.invokeLater(()->{if(disposed)return;try{handle(event);}catch(Exception e){failed("対戦の準備に失敗: "+e.getMessage());}});}
    private void handle(JsonObject e)throws Exception{
        String type=Protocol.string(e,"type",32);
        switch(type){
            case "connected":message("サーバーに接続しました。");break;
            case "joined":
                match=Protocol.string(e,"match",32);slot=Protocol.integer(e,"slot");if(slot<0||slot>1)throw new java.io.IOException("Invalid seat");
                room.setText(Protocol.string(e,"room",32));copyRoom.setEnabled(true);password.setText("");
                hashes[slot]=Hashes.sha256(localArchive);mounted[slot]=localBundle.mount(match,slot);
                message("部屋ID: "+room.getText()+"\n"+(creating?"相手の参加を待っています。":"入室しました。"));break;
            case "prepare":
                if(slot<0)throw new java.io.IOException("No seat assignment");prepared=true;leftSlot=Protocol.integer(e,"leftSlot");
                hostName=Protocol.string(e,"host",40);guestName=Protocol.string(e,"guest",40);
                message("キャラクターのデータを自動共有しています…");client.sendBundle(localArchive);break;
            case "bundle_ok":uploaded=true;updateReady();break;
            case "start":
                if(!uploaded||mounted[0]==null||mounted[1]==null)throw new java.io.IOException("Character synchronization not complete");
                leftSlot=Protocol.integer(e,"leftSlot");if(leftSlot<0||leftSlot>1)throw new java.io.IOException("Invalid castle assignment");
                battle=PvpStageBasis.create(match,mounted[leftSlot].lineup,mounted[1-leftSlot].lineup,Protocol.number(e,"seed"),leftSlot);
                showBattle();break;
            case "result":
                pulse.stop();resultSent=true;int winner=Protocol.integer(e,"winner");
                message(winner==-1?"引き分けです。両者の結果が一致しました。":(winner==(slot==leftSlot?0:1)?"勝利！":"敗北")+" 両者の結果が一致しました。");break;
            default:break;
        }
    }
    @Override public void bundle(Path verified,String hash){
        io.execute(()->{try{MatchBundle incoming=MatchBundle.read(verified);SwingUtilities.invokeLater(()->{
            if(disposed){delete(verified);return;}temporary.add(verified);
            try{if(slot<0||mounted[1-slot]!=null)throw new java.io.IOException("Unexpected opponent bundle");
                mounted[1-slot]=incoming.mount(match,1-slot);hashes[1-slot]=hash;updateReady();}
            catch(Exception e){failed("受信キャラの読み込み失敗: "+e.getMessage());}
        });}catch(Exception e){delete(verified);failed("受信データを拒否: "+e.getMessage());}});
    }
    private void updateReady(){if(uploaded&&prepared&&mounted[0]!=null&&mounted[1]!=null){ready.setEnabled(true);message("両者のキャラクターを同期しました。\n相手: "+(slot==0?guestName:hostName)+"\n準備完了を押すと対戦を開始します。");}}
    private void showBattle(){
        JPanel matchPanel=new JPanel(new BorderLayout(8,8));matchPanel.add(canvas,BorderLayout.CENTER);
        JPanel bottom=new JPanel(new BorderLayout(8,8));bottom.add(controls,BorderLayout.CENTER);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEADING));actions.add(economy);actions.add(worker);actions.add(cannon);actions.add(new JLabel("描画FPS"));actions.add(render);bottom.add(actions,BorderLayout.SOUTH);matchPanel.add(bottom,BorderLayout.SOUTH);
        content.remove(setup);content.add(matchPanel,BorderLayout.CENTER);content.revalidate();
        canvas.names(leftSlot==0?hostName:guestName,leftSlot==0?guestName:hostName);canvas.fps((Integer)render.getSelectedItem());canvas.snapshot(battle.displayCopy());
        message("左＝青、右＝ピンク。数字1〜5／QWERTで出撃、右クリックで自動生産切替。\nF：働きネコ、C：にゃんこ砲。表示FPSは変更しても戦闘処理は30TPSのままです。");
        refreshHud();nextPaint=0;pulse.start();
    }
    private void pump(){
        if(disposed||battle==null)return;
        try{
            int advanced=0;InputFrame frame;
            while(!resultSent&&advanced<5&&(frame=client.pollFrame())!=null){
                battle.step(frame);advanced++;
                if(battle.time%Protocol.HASH_INTERVAL==0)client.checkpoint(battle.time,BattleDigest.of(battle));
                if(battle.winner()!=-2){resultSent=true;client.result(battle.time,battle.winner(),BattleDigest.of(battle));message("試合終了。両者の結果を照合しています…");}
            }
            if(advanced>0){canvas.snapshot(battle.displayCopy());refreshHud();}
            long now=System.nanoTime();if(now>=nextPaint){canvas.renderFrame();nextPaint=now+1_000_000_000L/(Integer)render.getSelectedItem();}
        }catch(Exception e){failed("同期処理を停止しました: "+e.getMessage());}
    }
    private void refreshHud(){StageBasis own=slot==leftSlot?battle.left():battle.right();
        economy.setText("お金 "+own.money/100+" / "+own.maxMoney/100+"　働きLv "+own.work_lv);
        worker.setEnabled(!resultSent&&own.work_lv<8&&own.money>own.upgradeCost);worker.setText("働きネコ ("+own.getUpgradeCost()+") [F]");
        cannon.setEnabled(!resultSent&&own.cannon==own.maxCannon);cannon.setText("にゃんこ砲 "+(100L*own.cannon/Math.max(1,own.maxCannon))+"% [C]");
        for(int i=0;i<10;i++){int row=i/5,col=i%5;Form f=own.b.lu.fs[row][col];String shortcut=i<5?""+(i+1):"QWERT".substring(i-5,i-4);
            units[i].setText(f==null?"—":"<html>"+escape(f.toString())+" ["+shortcut+"]<br>"+own.elu.price[row][col]/100+"円　"+(own.elu.cool[row][col]>0?own.elu.cool[row][col]+"f":"準備OK")+(own.locks[row][col]?"　自動":"")+"</html>");
            units[i].setEnabled(f!=null&&!resultSent);}
    }
    private void command(int bit){if(client!=null&&battle!=null&&!resultSent)client.queueCommand(bit);}
    @Override protected void keyPressed(KeyEvent e){if(battle==null||e.getComponent() instanceof javax.swing.text.JTextComponent)return;
        String key=KeyEvent.getKeyText(e.getKeyCode()).toUpperCase(Locale.ROOT);int index="12345QWERT".indexOf(key);
        if(key.length()==1&&index>=0)command(1<<index);else if(e.getKeyCode()==KeyEvent.VK_F)command(InputFrame.WORKER);else if(e.getKeyCode()==KeyEvent.VK_C)command(InputFrame.CANNON);}
    @Override public void failed(String reason){SwingUtilities.invokeLater(()->{if(disposed)return;message(reason+"\n退出してから再接続してください。");pulse.stop();ready.setEnabled(false);if(client!=null)client.close();resultSent=true;});}
    private void message(String text){if(SwingUtilities.isEventDispatchThread()){if(!disposed)status.setText(text);}else SwingUtilities.invokeLater(()->{if(!disposed)status.setText(text);});}
    private static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    private static void delete(Path p){try{Files.deleteIfExists(p);}catch(java.io.IOException ignored){}}
    private void cleanup(){if(disposed)return;disposed=true;pulse.stop();if(client!=null)client.close();io.shutdownNow();battle=null;
        for(MatchBundle.Mounted m:mounted)if(m!=null)m.close();for(Path p:temporary)delete(p);temporary.clear();}
    @Override protected void leave(){cleanup();}
    @Override protected void exit(){cleanup();}
}
