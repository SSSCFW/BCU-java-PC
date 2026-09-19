package online.ui;

import common.CommonStatic;
import online.net.ServerHost;
import online.net.config.ServerConfig;
import javax.swing.*;
import java.awt.*;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** GUI shell for the same ServerHost used by the dedicated launcher. No network auto-configuration. */
public final class FriendServerPanel extends JPanel implements AutoCloseable {
    private static final long serialVersionUID = 1L;
    @FunctionalInterface public interface LocalTargetConsumer { void accept(String url,int udpPort); }
    private final JButton toggle = new JButton("このPCで友人用サーバーを起動");
    private final JButton useLocal = new JButton("このPCのサーバーを使う");
    private final JSpinner tcpPort = new JSpinner(new SpinnerNumberModel(8766,0,65535,1));
    private final JSpinner udpPort = new JSpinner(new SpinnerNumberModel(8767,0,65535,1));
    private final JTextArea addresses = new JTextArea(5, 40);
    private final LocalTargetConsumer localTarget;
    private final AtomicBoolean closed = new AtomicBoolean();
    private ServerHost host;
    private boolean busy, connectionActive;
    public FriendServerPanel(Consumer<String> localUrl) { this((url,udp)->localUrl.accept(url)); }
    public FriendServerPanel(LocalTargetConsumer localTarget) {
        super(new BorderLayout(6,6)); this.localTarget = localTarget;
        try {
            ServerConfig saved=configured();
            tcpPort.setValue(saved.controlPort);udpPort.setValue(saved.udpPort);
        } catch (Exception ignored) { }
        addresses.setEditable(false); addresses.setLineWrap(true); addresses.setWrapStyleWord(true);
        addresses.setText("同一PCで試す場合、サーバー起動は片方のBCUだけです。TCP/UDPは別々に変更できます。0はOSによる自動割当です。\n既定: TCP 8766 / UDP 8767。ルーター等は自動変更しません。");
        JPanel ports=new JPanel(new FlowLayout(FlowLayout.LEADING,6,0));
        ports.add(new JLabel("TCPポート"));ports.add(tcpPort);ports.add(new JLabel("UDPポート"));ports.add(udpPort);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0)); actions.add(toggle); actions.add(useLocal);
        JPanel top=new JPanel();top.setLayout(new BoxLayout(top,BoxLayout.Y_AXIS));top.add(ports);top.add(actions);
        add(top, BorderLayout.NORTH); add(new JScrollPane(addresses), BorderLayout.CENTER);
        toggle.addActionListener(e -> toggle());
        useLocal.addActionListener(e -> useLocal());
    }
    private static Path configPath() {
        Path base = CommonStatic.ctx == null ? Paths.get(".") : CommonStatic.ctx.getBCUFolder().toPath();
        return base.resolve("pvp-server.properties");
    }
    private static Properties configProperties() throws java.io.IOException {
        Properties p=new Properties();Path path=configPath();
        if(Files.exists(path))try(Reader in=Files.newBufferedReader(path,StandardCharsets.UTF_8)){p.load(in);}
        return p;
    }
    private static ServerConfig configured() throws java.io.IOException {
        return ServerConfig.from(configProperties());
    }
    private ServerConfig config() throws java.io.IOException {
        Properties p=configProperties();
        p.setProperty("controlPort",Integer.toString(((Number)tcpPort.getValue()).intValue()));
        p.setProperty("udpPort",Integer.toString(((Number)udpPort.getValue()).intValue()));
        return ServerConfig.from(p);
    }
    private void useLocal() {
        if (busy || connectionActive || closed.get()) return;
        try {
            String url;
            if (host != null) url = host.localControlUrl();
            else {
                ServerConfig config = config();
                if (config.controlPort == 0) throw new java.io.IOException("自動割当ポートの場合は、起動済みサーバーに表示されたURLをコピーしてください。");
                java.net.InetAddress bind = config.controlAddress().getAddress();
                if (bind == null) throw new java.io.IOException("サーバーのbindアドレスを解決できません。");
                String address = bind.isAnyLocalAddress() ? "127.0.0.1" : bind.getHostAddress();
                if (address.indexOf(':') >= 0) address = "[" + address.replace("%", "%25") + "]";
                url = "ws://" + address + ":" + config.controlPort;
            }
            localTarget.accept(url,config.udpPort);
            addresses.setText("接続先を " + url + " / UDP " + config.udpPort + " に設定しました。\nサーバーは追加起動していません。表示名・編成を選び、共有に同意して部屋を作成、または部屋IDで参加してください。");
        } catch (Exception e) { addresses.setText("接続先の設定失敗: " + e.getMessage()); }
    }
    private void toggle() {
        if (busy || connectionActive || closed.get()) return;
        busy = true; updateButtons();
        final ServerHost stopping = host; host = null;
        new SwingWorker<ServerHost, Void>() {
            protected ServerHost doInBackground() throws Exception {
                if (stopping != null) { stopping.close(); return null; }
                return ServerHost.start(config());
            }
            protected void done() {
                busy = false;
                try {
                    ServerHost started = get();
                    if (closed.get()) { closeAsync(started); return; }
                    host = started;
                    if (host == null) addresses.setText("友人用サーバーを停止しました。");
                    else {
                        localTarget.accept(host.localControlUrl(),host.server().udpPort());
                        tcpPort.setValue(host.server().getPort());udpPort.setValue(host.server().udpPort());
                        StringBuilder text = new StringBuilder("接続先候補（到達確認は別途必要）:\n");
                        for (String url : host.candidateUrls()) text.append(url).append('\n');
                        text.append("TCP ").append(host.server().getPort()).append(" / UDP ").append(host.server().udpPort());
                        text.append("\n同じPCの2つ目のBCUは、サーバーを起動せず上のURLと部屋IDで参加してください。");
                        text.append("\n部屋の退出ではサーバーを維持します。「戻る」でオンライン画面を閉じると停止します。");
                        addresses.setText(text.toString());
                    }
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    addresses.setText("サーバー起動失敗: " + cause.getMessage() + "\nこのPCで既に起動している場合は「このPCのサーバーを使う」を選んでください。");
                } finally { updateButtons(); }
            }
        }.execute();
    }
    private void updateButtons() {
        toggle.setText(host == null ? "このPCで友人用サーバーを起動" : "友人用サーバーを停止");
        boolean editable=!connectionActive&&!busy&&!closed.get()&&host==null;
        tcpPort.setEnabled(editable);udpPort.setEnabled(editable);
        toggle.setEnabled(!connectionActive && !busy && !closed.get());
        useLocal.setEnabled(!connectionActive && !busy && !closed.get());
    }
    private static void closeAsync(ServerHost host) {
        if (host == null) return;
        Thread t = new Thread(host::close, "pvp-friend-server-stop"); t.setDaemon(true); t.start();
    }
    public void connectionActive(boolean active) { connectionActive = active; updateButtons(); }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        ServerHost stopping = host; host = null; closeAsync(stopping);
    }
}
