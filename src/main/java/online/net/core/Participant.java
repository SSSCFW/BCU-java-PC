package online.net.core;

import online.net.realtime.*;

/** Participant identity, game seat and network transport are deliberately separate. */
public final class Participant {
    public enum TransportState { PROBING, UDP, WS }
    public final int id;
    public final String displayName;
    public final GameMode.Seat seat;
    public final ControlPeer control;
    public TransportState transportState = TransportState.PROBING;
    public RealtimeTransport realtime;
    public UdpService.Connection udp;
    public String bundleHash, uploadingHash, result;
    public boolean uploaded, ready, downloading, lobbyReady, resultAck;
    public String lineupName="";
    public double castleHealthMultiplier=20.0;
    public long nextFrameExpected, requestedFrame = -1, lastRescue, lastSend, lastFrameAckProgress = System.nanoTime();
    Participant(int id, String name, GameMode.Seat seat, ControlPeer control) {
        this.id = id; displayName = name; this.seat = seat; this.control = control;
    }
    void resetMatchState() {
        bundleHash=null;uploadingHash=null;result=null;
        uploaded=false;ready=false;downloading=false;lobbyReady=false;resultAck=false;
        nextFrameExpected=0;requestedFrame=-1;lastRescue=0;lastSend=0;lastFrameAckProgress=System.nanoTime();
    }
}
