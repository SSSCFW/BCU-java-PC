package online.net.lobby;

import java.util.Arrays;

/** The curated standard-pack BGM catalogue exposed by online PvP. */
public final class PvpBattleMusic {
    public static final class Entry {
        public final int id;
        public final String name;
        private Entry(int id,String name){this.id=id;this.name=name;}
        public String displayName(){return String.format("%03d.ogg %s",id,name);}
        @Override public String toString(){return displayName();}
    }

    private static final Entry[] ENTRIES={
            new Entry(3,"日本侵略！"),
            new Entry(4,"西表島の戦い"),
            new Entry(6,"チャレンジバトル"),
            new Entry(30,"スロウバトル"),
            new Entry(31,"大地揺るがす猛者たち"),
            new Entry(32,"民族大移動"),
            new Entry(33,"なにわの恋人"),
            new Entry(34,"神様降臨"),
            new Entry(47,"未来の侵略者"),
            new Entry(48,"未知なる世界へ"),
            new Entry(49,"アポロ決戦"),
            new Entry(58,"道場の間"),
            new Entry(66,"宇宙浪漫飛行"),
            new Entry(67,"銀河の英雄"),
            new Entry(68,"奇襲！未確認生物"),
            new Entry(69,"ビッグバン組曲"),
            new Entry(80,"太古の力"),
            new Entry(81,"古代の呪い"),
            new Entry(82,"驚愕！古代生物"),
            new Entry(87,"宇宙の危機！スターフィリーバスター"),
            new Entry(141,"魔界侵略！"),
            new Entry(142,"大決戦！破壊神ジャガンドー"),
            new Entry(147,"起源の覚醒"),
            new Entry(148,"密林の異変"),
            new Entry(149,"砂漠の怪異"),
            new Entry(150,"火山の脅威"),
            new Entry(153,"地底調査団、出動！"),
            new Entry(154,"立ち向かえ！地底調査団"),
            new Entry(155,"日本侵略！（0.ver）"),
            new Entry(156,"原住民大移動"),
            new Entry(157,"ウルルブ島の戦い"),
            new Entry(161,"激戦！世に来し超賢者"),
            new Entry(163,"異空揺るがす強者たち"),
            new Entry(166,"ゼロの侵略者")
    };

    public static final int DEFAULT_ID=3;

    private PvpBattleMusic(){}

    public static Entry[] entries(){return ENTRIES.clone();}

    public static boolean isAllowed(int id){
        for(Entry entry:ENTRIES)if(entry.id==id)return true;
        return false;
    }

    public static Entry find(int id){
        for(Entry entry:ENTRIES)if(entry.id==id)return entry;
        return null;
    }

    public static int[] ids(){
        int[] ids=new int[ENTRIES.length];
        for(int i=0;i<ENTRIES.length;i++)ids[i]=ENTRIES[i].id;
        return ids;
    }

    static {
        int[] ids=ids(),sorted=ids.clone();
        Arrays.sort(sorted);
        for(int i=1;i<sorted.length;i++)if(sorted[i-1]==sorted[i])
            throw new ExceptionInInitializerError("Duplicate PvP BGM id: "+sorted[i]);
    }
}
