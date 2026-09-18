package online.ui;

import online.net.lobby.PvpTraitRules;
import online.net.lobby.RoomRules;

import java.nio.file.Files;
import java.nio.file.Path;

public final class LobbyPreferencesTests {
    public static void run() throws Exception {
        Path dir=Files.createTempDirectory("bcu-pvp-pref-test-"),file=dir.resolve("online-client.properties");
        RoomRules rules=new RoomRules(8123,RoomRules.RANDOM_BACKGROUND,RoomRules.RANDOM_MUSIC,true,RoomRules.SpecialMode.ROULETTE,true,
                PvpTraitRules.RANDOM,common.util.Data.TRAIT_RED,5,2,RoomRules.UNLIMITED_TIME);
        LobbyPreferences original=new LobbyPreferences("wss://example.invalid/pvp","tester")
                .withHostRules(rules).withCastleHealth(37.5)
                .withLocalSetup(1,LobbyPreferences.LINEUP_RANDOM_VANILLA,-1,-1);
        original.save(file);
        LobbyPreferences loaded=LobbyPreferences.load(file,"fallback");
        online.tests.Check.equal("wss://example.invalid/pvp",loaded.serverAddress,"preference server roundtrip");
        online.tests.Check.equal("tester",loaded.displayName,"preference display name roundtrip");
        online.tests.Check.equal(8123,loaded.castleDistance,"castle distance preference roundtrip");
        online.tests.Check.equal(RoomRules.RANDOM_BACKGROUND,loaded.backgroundId,"random background preference roundtrip");
        online.tests.Check.equal(RoomRules.RANDOM_MUSIC,loaded.musicId,"random BGM preference roundtrip");
        online.tests.Check.that(loaded.force60Fps&&loaded.debugMode,"boolean PvP room preferences roundtrip");
        online.tests.Check.equal(RoomRules.SpecialMode.ROULETTE,loaded.specialMode,"special mode preference roundtrip");
        online.tests.Check.equal(PvpTraitRules.RANDOM,loaded.hostTraitChoice,"host trait preference roundtrip");
        online.tests.Check.equal((int)common.util.Data.TRAIT_RED,loaded.guestTraitChoice,"guest trait preference roundtrip");
        online.tests.Check.equal(5,loaded.hostTraitExclusions,"host random exclusions roundtrip");
        online.tests.Check.equal(2,loaded.guestTraitExclusions,"guest random exclusions roundtrip");
        online.tests.Check.equal(RoomRules.UNLIMITED_TIME,loaded.timeLimitMinutes,"unlimited time preference roundtrip");
        online.tests.Check.equal(37.5,loaded.castleHealthMultiplier,"local castle multiplier preference roundtrip");
        online.tests.Check.equal(1,loaded.creatorSideIndex,"creator side preference roundtrip");
        online.tests.Check.equal(LobbyPreferences.LINEUP_RANDOM_VANILLA,loaded.lineupKind,"lineup choice preference roundtrip");
        online.tests.Check.equal(rules,loaded.hostRules(),"all host PvP room rules roundtrip");
    }
}
