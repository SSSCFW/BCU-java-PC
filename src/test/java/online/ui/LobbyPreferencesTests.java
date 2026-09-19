package online.ui;

import online.net.lobby.PvpTraitRules;
import online.net.lobby.RoomRules;

import java.nio.file.Files;
import java.nio.file.Path;

public final class LobbyPreferencesTests {
    public static void run() throws Exception {
        Path dir=Files.createTempDirectory("bcu-pvp-pref-test-"),file=dir.resolve("online-client.properties");
        RoomRules rules=new RoomRules(8123,RoomRules.RANDOM_BACKGROUND,3,true,RoomRules.SpecialMode.ROULETTE,true,
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
        online.tests.Check.equal(3,loaded.musicId,"curated BGM preference roundtrip");
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

        java.util.Properties legacy=new java.util.Properties();
        legacy.setProperty("serverAddress","wss://legacy.invalid/pvp");
        legacy.setProperty("displayName","legacy");
        legacy.setProperty("castleDistance","8123");
        legacy.setProperty("backgroundId",Integer.toString(RoomRules.RANDOM_BACKGROUND));
        legacy.setProperty("musicId","-1");
        legacy.setProperty("force60Fps","true");
        legacy.setProperty("specialMode",RoomRules.SpecialMode.ROULETTE.name());
        legacy.setProperty("debugMode","true");
        legacy.setProperty("hostTraitChoice",Integer.toString(PvpTraitRules.NONE));
        legacy.setProperty("guestTraitChoice",Integer.toString(PvpTraitRules.NONE));
        legacy.setProperty("hostTraitExclusions","0");legacy.setProperty("guestTraitExclusions","0");
        legacy.setProperty("timeLimitMinutes","23");legacy.setProperty("castleHealthMultiplier","37.5");
        legacy.setProperty("creatorSideIndex","1");legacy.setProperty("lineupKind",Integer.toString(LobbyPreferences.LINEUP_RANDOM));
        legacy.setProperty("lineupSetIndex","-1");legacy.setProperty("lineupIndex","-1");
        try(java.io.Writer out=Files.newBufferedWriter(file)){legacy.store(out,"legacy");}
        LobbyPreferences migrated=LobbyPreferences.load(file,"fallback");
        online.tests.Check.equal(online.net.lobby.PvpBattleMusic.DEFAULT_ID,migrated.musicId,"legacy random/silent BGM migrates to the curated default");
        online.tests.Check.equal(8123,migrated.castleDistance,"legacy BGM migration preserves other room settings");
        online.tests.Check.equal(23,migrated.timeLimitMinutes,"legacy BGM migration preserves time limit");

        legacy.setProperty("musicId",Integer.toString(online.net.lobby.PvpBattleMusic.DEFAULT_ID));
        legacy.setProperty("hostTraitChoice",Integer.toString(common.util.Data.TRAIT_WHITE));
        legacy.setProperty("guestTraitChoice",Integer.toString(common.util.Data.TRAIT_WHITE));
        legacy.setProperty("hostTraitExclusions",Integer.toString((1<<10)|3));
        legacy.setProperty("guestTraitExclusions",Integer.toString(1<<10));
        try(java.io.Writer out=Files.newBufferedWriter(file)){legacy.store(out,"legacy white trait");}
        LobbyPreferences whiteMigrated=LobbyPreferences.load(file,"fallback");
        online.tests.Check.equal(PvpTraitRules.NONE,whiteMigrated.hostTraitChoice,"saved white host trait migrates to attribute-less");
        online.tests.Check.equal(PvpTraitRules.NONE,whiteMigrated.guestTraitChoice,"saved white guest trait migrates to attribute-less");
        online.tests.Check.equal(3,whiteMigrated.hostTraitExclusions,"removed white exclusion bit is stripped while valid exclusions remain");
        online.tests.Check.equal(0,whiteMigrated.guestTraitExclusions,"removed white-only exclusion bit is stripped");
    }
}
