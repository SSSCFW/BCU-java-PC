package online.ui;

import online.net.lobby.PvpTraitRules;
import online.net.lobby.RoomRules;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

/** Per-installation, non-secret lobby fields only. Never save passwords, room IDs or transport keys. */
final class LobbyPreferences {
    static final String DEFAULT_SERVER = "ws://127.0.0.1:8766";
    final String serverAddress, displayName;
    final int hostTraitChoice, guestTraitChoice, hostTraitExclusions, guestTraitExclusions, timeLimitMinutes;

    LobbyPreferences(String serverAddress, String displayName) {
        this(serverAddress,displayName,PvpTraitRules.NONE,PvpTraitRules.NONE,0,0,RoomRules.DEFAULT_TIME_LIMIT_MINUTES);
    }

    LobbyPreferences(String serverAddress,String displayName,int hostTraitChoice,int guestTraitChoice,
                     int hostTraitExclusions,int guestTraitExclusions,int timeLimitMinutes) {
        PvpTraitRules.validate(hostTraitChoice,hostTraitExclusions);
        PvpTraitRules.validate(guestTraitChoice,guestTraitExclusions);
        if(timeLimitMinutes!=RoomRules.UNLIMITED_TIME
                &&(timeLimitMinutes<RoomRules.MIN_TIME_LIMIT_MINUTES||timeLimitMinutes>RoomRules.MAX_TIME_LIMIT_MINUTES))
            throw new IllegalArgumentException("Invalid saved time limit");
        this.serverAddress=serverAddress;this.displayName=displayName;
        this.hostTraitChoice=hostTraitChoice;this.guestTraitChoice=guestTraitChoice;
        this.hostTraitExclusions=hostTraitExclusions;this.guestTraitExclusions=guestTraitExclusions;
        this.timeLimitMinutes=timeLimitMinutes;
    }

    LobbyPreferences withConnection(String server,String name){
        return new LobbyPreferences(server,name,hostTraitChoice,guestTraitChoice,hostTraitExclusions,guestTraitExclusions,timeLimitMinutes);
    }

    LobbyPreferences withHostRules(RoomRules rules){
        return new LobbyPreferences(serverAddress,displayName,rules.hostTraitChoice,rules.guestTraitChoice,
                rules.hostTraitExclusions,rules.guestTraitExclusions,rules.timeLimitMinutes);
    }

    static LobbyPreferences load(Path file, String fallbackName) throws IOException {
        Properties properties = new Properties();
        if (Files.exists(file)) {
            if (Files.size(file) > 32768) throw new IOException("Lobby preferences file is too large");
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { properties.load(in); }
            catch (IllegalArgumentException e) { throw new IOException("Invalid lobby preferences", e); }
        }
        String server=properties.getProperty("serverAddress",DEFAULT_SERVER);
        String name=properties.getProperty("displayName",fallbackName==null?"":fallbackName);
        try{
            int hostTrait=Integer.parseInt(properties.getProperty("hostTraitChoice",Integer.toString(PvpTraitRules.NONE)));
            int guestTrait=Integer.parseInt(properties.getProperty("guestTraitChoice",Integer.toString(PvpTraitRules.NONE)));
            int hostExclude=Integer.parseInt(properties.getProperty("hostTraitExclusions","0"));
            int guestExclude=Integer.parseInt(properties.getProperty("guestTraitExclusions","0"));
            int time=Integer.parseInt(properties.getProperty("timeLimitMinutes",Integer.toString(RoomRules.DEFAULT_TIME_LIMIT_MINUTES)));
            return new LobbyPreferences(server,name,hostTrait,guestTrait,hostExclude,guestExclude,time);
        }catch(IllegalArgumentException e){
            System.err.println("BCU online preferences: ignoring invalid saved PvP rule preferences: "+e.getMessage());
            return new LobbyPreferences(server,name);
        }
    }

    void save(Path file) throws IOException {
        Path target = file.toAbsolutePath(), parent = target.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".online-client-", ".tmp");
        try {
            Properties properties = new Properties();
            properties.setProperty("serverAddress", serverAddress);
            properties.setProperty("displayName", displayName);
            properties.setProperty("hostTraitChoice",Integer.toString(hostTraitChoice));
            properties.setProperty("guestTraitChoice",Integer.toString(guestTraitChoice));
            properties.setProperty("hostTraitExclusions",Integer.toString(hostTraitExclusions));
            properties.setProperty("guestTraitExclusions",Integer.toString(guestTraitExclusions));
            properties.setProperty("timeLimitMinutes",Integer.toString(timeLimitMinutes));
            try (Writer out = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(out, "BCU online lobby (non-secret preferences only)");
            }
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
