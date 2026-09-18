package online.ui;

import online.net.lobby.PvpTraitRules;
import online.net.lobby.RoomRules;

import java.nio.file.Files;
import java.nio.file.Path;

public final class LobbyPreferencesTests {
    public static void run() throws Exception {
        Path dir=Files.createTempDirectory("bcu-pvp-pref-test-"),file=dir.resolve("online-client.properties");
        RoomRules rules=new RoomRules(4400,0,-1,false,RoomRules.SpecialMode.ROULETTE,false,
                PvpTraitRules.RANDOM,common.util.Data.TRAIT_RED,5,2,RoomRules.UNLIMITED_TIME);
        LobbyPreferences original=new LobbyPreferences("wss://example.invalid/pvp","tester").withHostRules(rules);
        original.save(file);
        LobbyPreferences loaded=LobbyPreferences.load(file,"fallback");
        online.tests.Check.equal("wss://example.invalid/pvp",loaded.serverAddress,"preference server roundtrip");
        online.tests.Check.equal("tester",loaded.displayName,"preference display name roundtrip");
        online.tests.Check.equal(PvpTraitRules.RANDOM,loaded.hostTraitChoice,"host trait preference roundtrip");
        online.tests.Check.equal((int)common.util.Data.TRAIT_RED,loaded.guestTraitChoice,"guest trait preference roundtrip");
        online.tests.Check.equal(5,loaded.hostTraitExclusions,"host random exclusions roundtrip");
        online.tests.Check.equal(2,loaded.guestTraitExclusions,"guest random exclusions roundtrip");
        online.tests.Check.equal(RoomRules.UNLIMITED_TIME,loaded.timeLimitMinutes,"unlimited time preference roundtrip");
    }
}
