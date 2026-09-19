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
        System.out.println("Online pump timing tests passed");
    }
    public static void main(String[] args){run();}
}
