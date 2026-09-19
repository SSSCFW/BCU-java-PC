package online.net.lobby;

import common.util.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Shared validation/labels for per-player PvP unit attributes. */
public final class PvpTraitRules {
    public static final int NONE=-1, RANDOM=-2;
    public static final int[] OPTIONS={
            NONE,
            Data.TRAIT_RED,Data.TRAIT_FLOAT,Data.TRAIT_BLACK,Data.TRAIT_METAL,Data.TRAIT_ANGEL,
            Data.TRAIT_ALIEN,Data.TRAIT_ZOMBIE,Data.TRAIT_DEMON,Data.TRAIT_RELIC
    };
    public static final String[] LABELS={
            "属性なし",
            "赤い敵","浮いてる敵","黒い敵","メタルな敵","天使",
            "エイリアン","ゾンビ","悪魔","古代種"
    };
    public static final int ALL_EXCLUSIONS=(1<<OPTIONS.length)-1;

    private PvpTraitRules(){}

    public static void validate(int choice,int exclusionMask){
        // White/untraited is intentionally not a PvP-selectable attribute.
        // A player is either attribute-less (NONE) or exactly one explicit trait.
        boolean valid=choice==RANDOM;
        for(int option:OPTIONS)valid|=choice==option;
        if(!valid)throw new IllegalArgumentException("無効なプレイヤー属性です");
        if((exclusionMask&~ALL_EXCLUSIONS)!=0)throw new IllegalArgumentException("無効な属性除外設定です");
        if(choice==RANDOM){
            boolean available=false;
            for(int i=0;i<OPTIONS.length;i++)
                if(OPTIONS[i]!=NONE&&(exclusionMask&(1<<i))==0){available=true;break;}
            if(!available)throw new IllegalArgumentException("ランダム属性では最低1つは実属性を残してください");
        }
    }

    public static int resolve(int choice,int exclusionMask,long seed,long salt){
        validate(choice,exclusionMask);
        if(choice!=RANDOM)return choice;
        List<Integer> candidates=new ArrayList<>();
        for(int i=0;i<OPTIONS.length;i++)
            if(OPTIONS[i]!=NONE&&(exclusionMask&(1<<i))==0)candidates.add(OPTIONS[i]);
        if(candidates.isEmpty())throw new IllegalArgumentException("ランダム属性の候補がありません");
        return candidates.get(new Random(seed^salt).nextInt(candidates.size()));
    }

    public static String label(int choice){
        if(choice==RANDOM)return "ランダム";
        for(int i=0;i<OPTIONS.length;i++)if(OPTIONS[i]==choice)return LABELS[i];
        return "不明";
    }

    public static int optionIndex(int choice){
        for(int i=0;i<OPTIONS.length;i++)if(OPTIONS[i]==choice)return i;
        return -1;
    }
}
