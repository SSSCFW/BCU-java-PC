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
        RoomLobbyTests.run();
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
        PvpStressTests.run();
        RouletteTests.run();
        Pvp3dsAssetsTests.run();
        PvpSoundBankTests.run();
        online.ui.PvpPlaybackTests.run();
        online.ui.PvpRouletteAudioTests.run();
        online.ui.OnlinePumpTimingTests.run();
        PvpBattleMusicTests.run();
        UiTests.run();
        RoomAudioTests.run();
        RoomRuleTests.run();
        RandomLineupTests.run();
        PlayerTraitTests.run();
        online.ui.LobbyPreferencesTests.run();
        io.BCJSONTests.run();
        io.AudioVolumeTests.run();
        ProcessTests.run();
        System.out.println("PvP tests passed: " + Check.count + " assertions");
    }
}
