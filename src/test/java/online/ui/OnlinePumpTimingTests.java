package online.ui;

import online.tests.Check;

public final class OnlinePumpTimingTests {
    public static void run() {
        long now=1_000_000_000L,step=33_333_333L;
        Check.that(!OnlineLobbyPage.battleStepDue(now,now+step,1),
                "single buffered frame keeps the normal 30TPS presentation cadence");
        Check.that(OnlineLobbyPage.battleStepDue(now,now+step,2),
                "two-frame backlog immediately authorizes catch-up instead of preserving accumulated input lag");
        Check.that(OnlineLobbyPage.battleStepDue(now,now,0),
                "ordinary deadline still advances battle with no backlog");
        long next=OnlineLobbyPage.advanceDeadline(now-step*30,now,step);
        Check.that(next>now&&next<=now+step,
                "stale battle deadline advances to the next future 30TPS slot");
        Check.equal(1,OnlineLobbyPage.presentationStride(199,7_000_000L),"small/cheap battles publish every tick");
        Check.equal(3,OnlineLobbyPage.presentationStride(400,7_000_000L),"400-entity battles protect logic with 10Hz snapshots");
        Check.equal(2,OnlineLobbyPage.presentationStride(100,9_000_000L),"expensive copies reduce presentation rate");
        Check.equal(3,OnlineLobbyPage.presentationStride(100,17_000_000L),"16ms copies use 10Hz snapshots");
        Check.equal(4,OnlineLobbyPage.presentationStride(100,40_000_000L),"over-budget copies use 7.5Hz snapshots");
        Check.equal(5,OnlineLobbyPage.presentationStride(100,60_000_000L),"extreme copies use 6Hz snapshots");
        Check.that(OnlineLobbyPage.fullSnapshotDue(true,true,true,7,5),"terminal tick always publishes a full snapshot");
        Check.that(!OnlineLobbyPage.fullSnapshotDue(false,true,false,10,5),"catch-up suppresses presentation clones");
        Check.that(!OnlineLobbyPage.fullSnapshotDue(false,false,true,10,5),"unconsumed snapshot applies backpressure");
        Check.that(OnlineLobbyPage.fullSnapshotDue(false,false,false,10,5),"ordinary due tick publishes a snapshot");
        System.out.println("Online pump timing tests passed");
    }
    public static void main(String[] args){run();}
}
