package online.tests;

import online.net.lobby.PvpBattleMusic;

public final class PvpBattleMusicTests {
    public static void run() {
        int[] expected={3,4,6,30,31,32,33,34,47,48,49,58,66,67,68,69,80,81,82,87,141,142,147,148,149,150,153,154,155,156,157,161,163,166};
        String[] names={
                "日本侵略！","西表島の戦い","チャレンジバトル","スロウバトル","大地揺るがす猛者たち","民族大移動","なにわの恋人","神様降臨",
                "未来の侵略者","未知なる世界へ","アポロ決戦","道場の間","宇宙浪漫飛行","銀河の英雄","奇襲！未確認生物","ビッグバン組曲",
                "太古の力","古代の呪い","驚愕！古代生物","宇宙の危機！スターフィリーバスター","魔界侵略！","大決戦！破壊神ジャガンドー",
                "起源の覚醒","密林の異変","砂漠の怪異","火山の脅威","地底調査団、出動！","立ち向かえ！地底調査団","日本侵略！（0.ver）",
                "原住民大移動","ウルルブ島の戦い","激戦！世に来し超賢者","異空揺るがす強者たち","ゼロの侵略者"
        };
        PvpBattleMusic.Entry[] entries=PvpBattleMusic.entries();
        Check.equal(expected.length,entries.length,"PvP BGM catalogue contains only the requested tracks");
        for(int i=0;i<expected.length;i++){
            Check.equal(expected[i],entries[i].id,"PvP BGM id ordering "+i);
            Check.equal(names[i],entries[i].name,"PvP BGM Japanese name "+expected[i]);
            Check.that(PvpBattleMusic.isAllowed(expected[i]),"PvP BGM id is allowed "+expected[i]);
            Check.equal(String.format("%03d.ogg %s",expected[i],names[i]),entries[i].displayName(),"PvP BGM display label "+expected[i]);
        }
        Check.equal("魔界侵略！",PvpBattleMusic.find(141).name,"141.ogg mapping");
        Check.equal("大決戦！破壊神ジャガンドー",PvpBattleMusic.find(142).name,"142.ogg mapping");
        Check.that(!PvpBattleMusic.isAllowed(-1)&&!PvpBattleMusic.isAllowed(7),"non-catalogue BGM identifiers are rejected");
        Check.equal(-2,online.net.lobby.RoomRules.RANDOM_MUSIC,"random PvP BGM uses the reserved synchronized rule ID");
    }
    public static void main(String[] args){run();System.out.println("PvP battle music catalogue tests passed");}
}
