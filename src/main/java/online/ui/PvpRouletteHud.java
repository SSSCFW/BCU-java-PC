package online.ui;

import common.battle.PvpRouletteState;
import common.battle.PvpStageBasis;
import common.battle.StageBasis;
import online.net.lobby.RoomRules;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Compact overlay that reuses the original Tobidasu multiplayer roulette art
 * while leaving BCU's native battle HUD and controls untouched.
 */
public final class PvpRouletteHud extends JComponent {
    private static final long serialVersionUID=1L;

    private static final String[] ICON={
            "アイコン：ふっとばし","アイコン：癒やし","アイコン：生産回復","アイコン：にゃんこ砲",
            "アイコン：生産短縮","アイコン：働き増加","アイコン：コストダウン","アイコン：お金マックス",
            "アイコン：スロウ","アイコン：ストップ","アイコン：攻撃力アップ","アイコン：体力アップ",
            "アイコン：移動アップ","アイコン：プチベビーラッシュ"
    };
    private static final String[] NAME={
            "効果名：ふっとばし","効果名：癒やし","効果名：生産回復","効果名：にゃんこ砲",
            "効果名：生産短縮","効果名：働き増加","効果名：コストダウン","効果名：お金マックス",
            "効果名：スロウ","効果名：ストップ","効果名：攻撃力アップ","効果名：体力アップ",
            "効果名：移動アップ","効果名：プチベビーラッシュ"
    };
    private static final String[] CUTIN={
            "ふっとばし発動!","にゃんこ回復ボーナス!","生産回復ボーナス!","にゃんこ砲発射!",
            "生産短縮ボーナス!","働きネコ仕事効率UPボーナス!","コストダウンボーナス!","お金MAXボーナス!!",
            "スロウ発動!","ストップ発動!","攻撃力UPボーナス!","体力UPボーナス!",
            "移動スピードUPボーナス!","ぷちベビーラッシュ発動!"
    };
    private static final String[] LEVEL={"","レベル１","レベル２","レベル３","レベルマックス"};

    private static final int CUTIN_TICKS=45;
    private final OnlineBattleField online;
    private boolean assetsReady,wasSpinning;
    private int seenResult=-1,cutinUntilTick=-1;

    public PvpRouletteHud(OnlineBattleField online){
        this.online=online;
        setOpaque(false);
        assetsReady=Pvp3dsAssets.available();
        refresh();
    }

    public void refresh(){
        PvpStageBasis world=(PvpStageBasis)online.sb;
        boolean roulette=world.specialMode()==RoomRules.SpecialMode.ROULETTE;
        setVisible(roulette);
        StageBasis own=online.playerState();
        PvpRouletteState state=own.pvpRoulette;
        if(state!=null){
            if(state.lastResult>=0 && (state.lastResult!=seenResult || (wasSpinning&&!state.spinning)))
                cutinUntilTick=world.time+CUTIN_TICKS;
            seenResult=state.lastResult;
            wasSpinning=state.spinning;
        }
        setToolTipText(online.specialStatus());
        repaint();
    }

    public boolean has3dsAssets(){return assetsReady;}

    public int displayedResult(){
        StageBasis own=online.playerState();
        if(own.pvpRoulette==null)return -1;
        if(own.pvpRoulette.spinning)return own.pvpRoulette.currentResult();
        return own.pvpRoulette.lastResult;
    }

    @Override protected void paintComponent(Graphics graphics){
        super.paintComponent(graphics);
        if(!isVisible())return;
        PvpStageBasis world=(PvpStageBasis)online.sb;
        StageBasis own=online.playerState();
        PvpRouletteState state=own.pvpRoulette;
        if(state==null)return;

        Graphics2D g=(Graphics2D)graphics.create();
        try{
            double scale=Math.min(getWidth()/390.0,getHeight()/50.0);
            if(scale<=0)return;
            g.translate((getWidth()-390*scale)/2.0,(getHeight()-50*scale)/2.0);
            g.scale(scale,scale);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

            if(assetsReady&&state.lastResult>=0&&world.time<cutinUntilTick){
                BufferedImage cut=Pvp3dsAssets.image("ui_battle_multi_cutin",CUTIN[state.lastResult]);
                g.drawImage(cut,(390-cut.getWidth())/2,(50-cut.getHeight())/2,null);
                return;
            }

            int result=state.spinning?state.currentResult():state.lastResult;
            if(assetsReady){
                boolean hi=((world.time/4)&1)==0;
                // Before the first result there is no effect icon/name yet; this is a
                // valid roulette state, not an asset-loading failure.
                if(result>=0&&result<ICON.length){
                    g.drawImage(Pvp3dsAssets.image("ui_battle_multi_icon",ICON[result]),4,5,null);
                    g.drawImage(Pvp3dsAssets.image("ui_battle_multi_reel",NAME[result]),48,5,null);
                    if(!state.spinning&&state.lastLevel>0){
                        int lv=Math.min(4,state.lastLevel);
                        g.drawImage(Pvp3dsAssets.image("ui_battle_multi_icon",LEVEL[lv]),226,14,null);
                    }
                }
                if(state.spinning){
                    // The 3DS sheet contains an R shoulder-button glyph, but PC R is a
                    // unit hotkey. Do not show a misleading control hint; reuse the
                    // authentic multiplayer roulette lamp instead.
                    g.drawImage(Pvp3dsAssets.image("ui_battle_multi","ルーレット点灯中ランプ"),264,13,null);
                    g.drawImage(Pvp3dsAssets.image("ui_battle_multi_reel",hi?"ルーレットランプ：ハイライト":"ルーレットランプ：点灯"),294,10,null);
                }else{
                    g.drawImage(Pvp3dsAssets.image("ui_battle_multi_reel","ルーレットランプ：点灯"),294,10,null);
                }
            }else{
                g.setColor(Color.WHITE);
                g.setFont(g.getFont().deriveFont(Font.BOLD,13f));
                g.drawString(online.specialStatus(),8,30);
            }
            int gauge=Math.max(0,Math.min(PvpRouletteState.MAX_GAUGE,state.gauge));
            g.setColor(new Color(0,0,0,180));g.fillRect(48,45,250,4);
            g.setColor(Color.WHITE);g.fillRect(48,45,250*gauge/PvpRouletteState.MAX_GAUGE,4);
        }catch(RuntimeException ex){
            assetsReady=false;
            g.setColor(Color.WHITE);
            g.drawString(online.specialStatus(),8,30);
        }finally{
            g.dispose();
        }
    }
}
