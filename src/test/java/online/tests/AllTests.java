package online.tests;

public final class AllTests {
    public static void main(String[] args) throws Exception {
        ClockTests.run();
        TransportCoreTests.run();
        UdpTransportTests.run();
        DuelAdapterTests.run();
        HybridNetworkTests.run();
        NetworkTests.run();
        PasswordRoomTests.run();
        LifecycleTests.run();
        ServerHostTests.run();
        online.net.CheckpointTests.run();
        BundleTests.run();
        PackTests.run();
        online.bundle.AnimationSafetyTests.run();
        AnimationRoundTripTests.run();
        ClientTests.run();
        CoreTests.run();
        CombatTests.run();
        UiTests.run();
        ProcessTests.run();
        System.out.println("PvP tests passed: " + Check.count + " assertions");
    }
}
