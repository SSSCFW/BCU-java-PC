package online.ui;

import online.net.ServerHost;
import online.net.config.ServerConfig;
import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** GUI shell for the same ServerHost used by the dedicated launcher. No network auto-configuration. */
public final class FriendServerPanel extends JPanel implements AutoCloseable {
    private static final long serialVersionUID = 1L;
    private final JButton toggle = new JButton("このPCで友人用サーバーを起動");
    private final JTextArea addresses = new JTextArea(4, 40);
    private final Consumer<String> localUrl;
    private final AtomicBoolean closed = new AtomicBoolean();
    private ServerHost host;
    private boolean busy;
    public FriendServerPanel(Consumer<String> localUrl) {
        super(new BorderLayout(6,6)); this.localUrl = localUrl;
        addresses.setEditable(false); addresses.setLineWrap(true); addresses.setWrapStyleWord(true);
        addresses.setText("既定: TCP 8766 / UDP 8767。設定は pvp-server.properties。ルーター等は自動変更しません。");
        add(toggle, BorderLayout.NORTH); add(new JScrollPane(addresses), BorderLayout.CENTER);
        toggle.addActionListener(e -> toggle());
    }
    private void toggle() {
        if (busy || closed.get()) return;
        busy = true; toggle.setEnabled(false);
        final ServerHost stopping = host; host = null;
        new SwingWorker<ServerHost, Void>() {
            protected ServerHost doInBackground() throws Exception {
                if (stopping != null) { stopping.close(); return null; }
                Path path = Paths.get("pvp-server.properties");
                return ServerHost.start(Files.exists(path) ? ServerConfig.load(path) : ServerConfig.from(new Properties()));
            }
            protected void done() {
                busy = false;
                try {
                    ServerHost started = get();
                    if (closed.get()) { closeAsync(started); return; }
                    host = started;
                    if (host == null) addresses.setText("友人用サーバーを停止しました。");
                    else {
                        localUrl.accept(host.localControlUrl());
                        StringBuilder text = new StringBuilder("接続先候補（到達確認は別途必要）:\n");
                        for (String url : host.candidateUrls()) text.append(url).append('\n');
                        text.append("TCP ").append(host.server().getPort()).append(" / UDP ").append(host.server().udpPort());
                        text.append("\nこの画面から退出するとサーバーも停止します。常設には専用起動スクリプトを使用してください。");
                        addresses.setText(text.toString());
                    }
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    addresses.setText("サーバー起動失敗: " + cause.getMessage());
                } finally {
                    toggle.setText(host == null ? "このPCで友人用サーバーを起動" : "友人用サーバーを停止");
                    toggle.setEnabled(!closed.get());
                }
            }
        }.execute();
    }
    private static void closeAsync(ServerHost host) {
        if (host == null) return;
        Thread t = new Thread(host::close, "pvp-friend-server-stop"); t.setDaemon(true); t.start();
    }
    public void connectionActive(boolean active) { toggle.setEnabled(!active && !busy && !closed.get()); }
    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        ServerHost stopping = host; host = null; closeAsync(stopping);
    }
}
