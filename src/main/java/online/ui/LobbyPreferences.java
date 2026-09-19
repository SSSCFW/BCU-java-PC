package online.ui;

import common.battle.PvpStageBasis;
import online.net.lobby.PvpTraitRules;
import online.net.lobby.PvpBattleMusic;
import online.net.lobby.RoomRules;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

/** Per-installation, non-secret lobby fields only. Never save passwords, room IDs or transport keys. */
final class LobbyPreferences {
    static final String DEFAULT_SERVER = "ws://127.0.0.1:8766";
    static final int LINEUP_SAVED=0, LINEUP_RANDOM=1, LINEUP_RANDOM_VANILLA=2;

    final String serverAddress, displayName;
    /** 0 means use the UDP port advertised by the server. */
    final int udpPortOverride;
    final int castleDistance, backgroundId, musicId;
    final boolean force60Fps, debugMode, castleHitMoneyEnabled;
    final RoomRules.SpecialMode specialMode;
    final int hostTraitChoice, guestTraitChoice, hostTraitExclusions, guestTraitExclusions, timeLimitMinutes;
    final int maxUnits, castleHitMoney;
    final double castleHealthMultiplier;
    final int creatorSideIndex, lineupKind, lineupSetIndex, lineupIndex;

    LobbyPreferences(String serverAddress, String displayName) {
        this(serverAddress,displayName,
                RoomRules.DEFAULT.castleDistance,RoomRules.DEFAULT.backgroundId,RoomRules.DEFAULT.musicId,
                RoomRules.DEFAULT.force60Fps,RoomRules.DEFAULT.specialMode,RoomRules.DEFAULT.debugMode,
                PvpTraitRules.NONE,PvpTraitRules.NONE,0,0,RoomRules.DEFAULT_TIME_LIMIT_MINUTES,
                RoomRules.DEFAULT_MAX_UNITS,false,RoomRules.DEFAULT_CASTLE_HIT_MONEY,
                PvpStageBasis.DEFAULT_CASTLE_HEALTH_MULTIPLIER,0,LINEUP_SAVED,-1,-1);
    }

    LobbyPreferences(String serverAddress,String displayName,
                     int castleDistance,int backgroundId,int musicId,boolean force60Fps,RoomRules.SpecialMode specialMode,boolean debugMode,
                     int hostTraitChoice,int guestTraitChoice,int hostTraitExclusions,int guestTraitExclusions,int timeLimitMinutes,
                     int maxUnits,boolean castleHitMoneyEnabled,int castleHitMoney,
                     double castleHealthMultiplier,int creatorSideIndex,int lineupKind,int lineupSetIndex,int lineupIndex) {
        this(serverAddress,displayName,castleDistance,backgroundId,musicId,force60Fps,specialMode,debugMode,
                hostTraitChoice,guestTraitChoice,hostTraitExclusions,guestTraitExclusions,timeLimitMinutes,
                maxUnits,castleHitMoneyEnabled,castleHitMoney,castleHealthMultiplier,creatorSideIndex,lineupKind,lineupSetIndex,lineupIndex,0);
    }

    LobbyPreferences(String serverAddress,String displayName,
                     int castleDistance,int backgroundId,int musicId,boolean force60Fps,RoomRules.SpecialMode specialMode,boolean debugMode,
                     int hostTraitChoice,int guestTraitChoice,int hostTraitExclusions,int guestTraitExclusions,int timeLimitMinutes,
                     int maxUnits,boolean castleHitMoneyEnabled,int castleHitMoney,
                     double castleHealthMultiplier,int creatorSideIndex,int lineupKind,int lineupSetIndex,int lineupIndex,int udpPortOverride) {
        RoomRules validated=new RoomRules(castleDistance,backgroundId,musicId,force60Fps,specialMode,debugMode,
                hostTraitChoice,guestTraitChoice,hostTraitExclusions,guestTraitExclusions,timeLimitMinutes,
                maxUnits,castleHitMoneyEnabled,castleHitMoney);
        PvpStageBasis.validateCastleHealthMultiplier(castleHealthMultiplier);
        if(creatorSideIndex<0||creatorSideIndex>1)throw new IllegalArgumentException("Invalid saved creator side");
        if(lineupKind<LINEUP_SAVED||lineupKind>LINEUP_RANDOM_VANILLA)throw new IllegalArgumentException("Invalid saved lineup kind");
        if(udpPortOverride<0||udpPortOverride>65535)throw new IllegalArgumentException("Invalid saved UDP port override");
        this.serverAddress=serverAddress;this.displayName=displayName;this.udpPortOverride=udpPortOverride;
        this.castleDistance=validated.castleDistance;this.backgroundId=validated.backgroundId;this.musicId=validated.musicId;
        this.force60Fps=validated.force60Fps;this.specialMode=validated.specialMode;this.debugMode=validated.debugMode;
        this.hostTraitChoice=validated.hostTraitChoice;this.guestTraitChoice=validated.guestTraitChoice;
        this.hostTraitExclusions=validated.hostTraitExclusions;this.guestTraitExclusions=validated.guestTraitExclusions;
        this.timeLimitMinutes=validated.timeLimitMinutes;this.maxUnits=validated.maxUnits;
        this.castleHitMoneyEnabled=validated.castleHitMoneyEnabled;this.castleHitMoney=validated.castleHitMoney;
        this.castleHealthMultiplier=castleHealthMultiplier;this.creatorSideIndex=creatorSideIndex;
        this.lineupKind=lineupKind;this.lineupSetIndex=lineupSetIndex;this.lineupIndex=lineupIndex;
    }

    LobbyPreferences withConnection(String server,String name){return withConnection(server,name,udpPortOverride);}
    LobbyPreferences withConnection(String server,String name,int udpPortOverride){
        return new LobbyPreferences(server,name,castleDistance,backgroundId,musicId,force60Fps,specialMode,debugMode,
                hostTraitChoice,guestTraitChoice,hostTraitExclusions,guestTraitExclusions,timeLimitMinutes,
                maxUnits,castleHitMoneyEnabled,castleHitMoney,
                castleHealthMultiplier,creatorSideIndex,lineupKind,lineupSetIndex,lineupIndex,udpPortOverride);
    }

    LobbyPreferences withHostRules(RoomRules rules){
        return copy(serverAddress,displayName,rules.castleDistance,rules.backgroundId,rules.musicId,rules.force60Fps,rules.specialMode,rules.debugMode,
                rules.hostTraitChoice,rules.guestTraitChoice,rules.hostTraitExclusions,rules.guestTraitExclusions,rules.timeLimitMinutes,
                rules.maxUnits,rules.castleHitMoneyEnabled,rules.castleHitMoney,
                castleHealthMultiplier,creatorSideIndex,lineupKind,lineupSetIndex,lineupIndex);
    }

    LobbyPreferences withCastleHealth(double value){
        return copy(serverAddress,displayName,castleDistance,backgroundId,musicId,force60Fps,specialMode,debugMode,
                hostTraitChoice,guestTraitChoice,hostTraitExclusions,guestTraitExclusions,timeLimitMinutes,
                maxUnits,castleHitMoneyEnabled,castleHitMoney,
                value,creatorSideIndex,lineupKind,lineupSetIndex,lineupIndex);
    }

    LobbyPreferences withLocalSetup(int side,int kind,int setIndex,int lineupIndex){
        return copy(serverAddress,displayName,castleDistance,backgroundId,musicId,force60Fps,specialMode,debugMode,
                hostTraitChoice,guestTraitChoice,hostTraitExclusions,guestTraitExclusions,timeLimitMinutes,
                maxUnits,castleHitMoneyEnabled,castleHitMoney,
                castleHealthMultiplier,side,kind,setIndex,lineupIndex);
    }

    RoomRules hostRules(){
        return new RoomRules(castleDistance,backgroundId,musicId,force60Fps,specialMode,debugMode,
                hostTraitChoice,guestTraitChoice,hostTraitExclusions,guestTraitExclusions,timeLimitMinutes,
                maxUnits,castleHitMoneyEnabled,castleHitMoney);
    }

    private LobbyPreferences copy(String server,String name,
                                         int distance,int background,int music,boolean force60,RoomRules.SpecialMode special,boolean debug,
                                         int hostTrait,int guestTrait,int hostExclude,int guestExclude,int time,
                                         int maxUnits,boolean castleHitMoneyEnabled,int castleHitMoney,
                                         double castle,int side,int lineupKind,int setIndex,int lineupIndex){
        return new LobbyPreferences(server,name,distance,background,music,force60,special,debug,
                hostTrait,guestTrait,hostExclude,guestExclude,time,maxUnits,castleHitMoneyEnabled,castleHitMoney,
                castle,side,lineupKind,setIndex,lineupIndex,udpPortOverride);
    }

    static LobbyPreferences load(Path file, String fallbackName) throws IOException {
        Properties p=new Properties();
        if(Files.exists(file)){
            if(Files.size(file)>32768)throw new IOException("Lobby preferences file is too large");
            try(Reader in=Files.newBufferedReader(file,StandardCharsets.UTF_8)){p.load(in);}
            catch(IllegalArgumentException e){throw new IOException("Invalid lobby preferences",e);}
        }
        String server=p.getProperty("serverAddress",DEFAULT_SERVER),name=p.getProperty("displayName",fallbackName==null?"":fallbackName);
        try{
            int music=integer(p,"musicId",RoomRules.DEFAULT.musicId);
            if(!PvpBattleMusic.isAllowed(music))music=PvpBattleMusic.DEFAULT_ID;
            return new LobbyPreferences(server,name,
                    integer(p,"castleDistance",RoomRules.DEFAULT.castleDistance),
                    integer(p,"backgroundId",RoomRules.DEFAULT.backgroundId),
                    music,
                    bool(p,"force60Fps",RoomRules.DEFAULT.force60Fps),
                    RoomRules.SpecialMode.valueOf(p.getProperty("specialMode",RoomRules.DEFAULT.specialMode.name())),
                    bool(p,"debugMode",RoomRules.DEFAULT.debugMode),
                    integer(p,"hostTraitChoice",PvpTraitRules.NONE),integer(p,"guestTraitChoice",PvpTraitRules.NONE),
                    integer(p,"hostTraitExclusions",0),integer(p,"guestTraitExclusions",0),
                    integer(p,"timeLimitMinutes",RoomRules.DEFAULT_TIME_LIMIT_MINUTES),
                    integer(p,"maxUnits",RoomRules.DEFAULT_MAX_UNITS),
                    bool(p,"castleHitMoneyEnabled",false),
                    integer(p,"castleHitMoney",RoomRules.DEFAULT_CASTLE_HIT_MONEY),
                    decimal(p,"castleHealthMultiplier",PvpStageBasis.DEFAULT_CASTLE_HEALTH_MULTIPLIER),
                    integer(p,"creatorSideIndex",0),integer(p,"lineupKind",LINEUP_SAVED),
                    integer(p,"lineupSetIndex",-1),integer(p,"lineupIndex",-1),
                    integer(p,"udpPortOverride",0));
        }catch(IllegalArgumentException e){
            System.err.println("BCU online preferences: ignoring invalid saved PvP preferences: "+e.getMessage());
            return new LobbyPreferences(server,name);
        }
    }

    void save(Path file)throws IOException{
        Path target=file.toAbsolutePath(),parent=target.getParent();Files.createDirectories(parent);
        Path temporary=Files.createTempFile(parent,".online-client-",".tmp");
        try{
            Properties p=new Properties();
            p.setProperty("serverAddress",serverAddress);p.setProperty("displayName",displayName);
            p.setProperty("udpPortOverride",Integer.toString(udpPortOverride));
            p.setProperty("castleDistance",Integer.toString(castleDistance));p.setProperty("backgroundId",Integer.toString(backgroundId));
            p.setProperty("musicId",Integer.toString(musicId));p.setProperty("force60Fps",Boolean.toString(force60Fps));
            p.setProperty("specialMode",specialMode.name());p.setProperty("debugMode",Boolean.toString(debugMode));
            p.setProperty("hostTraitChoice",Integer.toString(hostTraitChoice));p.setProperty("guestTraitChoice",Integer.toString(guestTraitChoice));
            p.setProperty("hostTraitExclusions",Integer.toString(hostTraitExclusions));p.setProperty("guestTraitExclusions",Integer.toString(guestTraitExclusions));
            p.setProperty("timeLimitMinutes",Integer.toString(timeLimitMinutes));
            p.setProperty("maxUnits",Integer.toString(maxUnits));
            p.setProperty("castleHitMoneyEnabled",Boolean.toString(castleHitMoneyEnabled));
            p.setProperty("castleHitMoney",Integer.toString(castleHitMoney));
            p.setProperty("castleHealthMultiplier",Double.toString(castleHealthMultiplier));
            p.setProperty("creatorSideIndex",Integer.toString(creatorSideIndex));
            p.setProperty("lineupKind",Integer.toString(lineupKind));p.setProperty("lineupSetIndex",Integer.toString(lineupSetIndex));p.setProperty("lineupIndex",Integer.toString(lineupIndex));
            try(Writer out=Files.newBufferedWriter(temporary,StandardCharsets.UTF_8)){p.store(out,"BCU online lobby (non-secret preferences only)");}
            try{Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temporary);}
    }

    private static int integer(Properties p,String key,int fallback){return Integer.parseInt(p.getProperty(key,Integer.toString(fallback)));}
    private static double decimal(Properties p,String key,double fallback){return Double.parseDouble(p.getProperty(key,Double.toString(fallback)));}
    private static boolean bool(Properties p,String key,boolean fallback){return Boolean.parseBoolean(p.getProperty(key,Boolean.toString(fallback)));}
}
