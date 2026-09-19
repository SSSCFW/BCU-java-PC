package online.net.core;

import com.google.gson.JsonObject;

/** Nonblocking control adapter. closeLater must never close a socket while holding the core monitor. */
public interface ControlPeer {
    void send(JsonObject message);
    void send(byte[] bytes);
    boolean isOpen();
    boolean hasBufferedData();
    String address();
    void closeLater();
}
