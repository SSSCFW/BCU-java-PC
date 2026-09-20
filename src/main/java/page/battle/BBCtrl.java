package page.battle;

import common.CommonStatic;
import common.CommonStatic.BCAuxAssets;
import common.battle.SBCtrl;
import common.system.P;
import common.system.fake.FakeImage;
import common.util.unit.Form;
import main.MainBCU;
import online.ui.OnlineBattleField;
import page.battle.BattleBox.BBPainter;
import page.battle.BattleBox.OuterBox;
import utilpc.PP;

import java.awt.*;
import java.awt.event.MouseEvent;

public class BBCtrl extends BBPainter {

	private final SBCtrl sbc;

	//This section is for lineup changing, detecting dragging up
	/**
	 * Initial point where drag started and ended
	 */
	private Point dragInit, dragEnd;

	/**
	 * The mouse button used when dragging (e.g. left click)
	 */
	private int dragButton;

	/**
	 * Boolean which tells dragging up/down is performed or not
	 */
	protected boolean performed = false;

	public BBCtrl(OuterBox bip, SBCtrl bas, BattleBox bb) {
		super(bip, bas, bb);
		sbc = bas;
	}

	public synchronized Form formAt(Point p) {
        int slot=slotAt(p);return slot<0?null:formForSlot(slot);
    }

    private Form formForSlot(int slot){
        if(sbc instanceof OnlineBattleField)return ((OnlineBattleField)sbc).visibleForm(slot);
        return controlState().b.lu.fs[slot/5][slot%5];
    }

    private FakeImage imageForSlot(int slot){
        BCAuxAssets aux=CommonStatic.getBCAssets();Form form=formForSlot(slot);
        FakeImage fallback=aux.slot[0].getImg();
        if(form==null||form.anim==null)return fallback;
        try{
            if(form.anim.getUni()==null)return fallback;
            FakeImage image=form.anim.getUni().getImg();
            if(image==null||!image.isValid()||image.getWidth()<=1||image.getHeight()<=1)return fallback;
            return image;
        }catch(RuntimeException e){return fallback;}
    }

    /** Visible lineup slot under the pointer, including empty slots. */
    public synchronized int slotAt(Point p) {
		BCAuxAssets aux=CommonStatic.getBCAssets();int w=box.getWidth(),h=box.getHeight();double hr=unir;
		if(p==null||hr<=0)return -1;
		double term=hr*aux.slot[0].getImg().getWidth()*0.2;
		if(CommonStatic.getConfig().twoRow){
			double termh=hr*aux.slot[0].getImg().getHeight()*0.1;
			for(int row=0;row<2;row++)for(int col=0;col<5;col++){
				Form f=formForSlot(row*5+col);
				FakeImage img=imageForSlot(row*5+col);int iw=(int)(hr*img.getWidth()),ih=(int)(hr*img.getHeight());
				int x=(w-iw*5)/2+iw*col+(int)(term*(col-2)),y=(int)(h-(2-row)*(ih+termh));
				if(!new PP(p).out(new P(x,y),new P(x+iw,y+ih),0))return row*5+col;
			}
		}else{
			int row=controlState().frontLineup;
			for(int col=0;col<5;col++){
				Form f=formForSlot(row*5+col);
				FakeImage img=imageForSlot(row*5+col);int iw=(int)(hr*img.getWidth()),ih=(int)(hr*img.getHeight());
				int x=(w-iw*5)/2+iw*col+(int)(term*(col-2)+(row==0?0:term/2)),y=h-(int)(ih*1.1);
				if(!new PP(p).out(new P(x,y),new P(x+iw,y+ih),0))return row*5+col;
			}
		}
		return -1;
	}

	@Override
	public synchronized void click(Point p, int button) {
		BCAuxAssets aux = CommonStatic.getBCAssets();
		int w = box.getWidth();
		int h = box.getHeight();
		double hr = unir;
		double term = hr * aux.slot[0].getImg().getWidth() * 0.2;
		if(CommonStatic.getConfig().twoRow) {
			double termh = hr * aux.slot[0].getImg().getHeight() * 0.1;

			for (int i = 0; i < 2; i++) {
				for(int j = 0; j < 5; j++) {
					Form f = formForSlot(i*5+j);
					FakeImage img = imageForSlot(i*5+j);
					int iw = (int) (hr * img.getWidth());
					int ih = (int) (hr * img.getHeight());
					int x = (w - iw * 5) / 2 + iw * j + (int) (term * (j -2));
					int y = (int) (h - (2 - i) * (ih + termh));
					if (!new PP(p).out(new P(x, y), new P(x + iw, y + ih), 0))
						sbc.action.add(j + i * 5);
					if (button != MouseEvent.BUTTON1)
						sbc.action.add(10);
				}
			}
		} else {
			for (int i = 0; i < 5; i++) {
				Form f = formForSlot(controlState().frontLineup*5+i);
				FakeImage img = imageForSlot(controlState().frontLineup*5+i);
				int iw = (int) (hr * img.getWidth());
				int ih = (int) (hr * img.getHeight());
				int x = (w - iw * 5) / 2 + iw * i + (int) (term * (i -2) + (controlState().frontLineup == 0 ? 0 : term/2));
				int y = h - (int) (ih * 1.1);
				if (!new PP(p).out(new P(x, y), new P(x + iw, y + ih), 0))
					sbc.action.add(i+controlState().frontLineup*5);
				if (button != MouseEvent.BUTTON1)
					sbc.action.add(10);
			}
		}
		hr = corr;
		FakeImage left = aux.battle[0][0].getImg();
		FakeImage right = aux.battle[1][0].getImg();
		int ih = (int) (hr * left.getHeight());
		int iw = (int) (hr * left.getWidth());
		if (!new PP(p).out(new P(0, h - ih), new P(iw, h), 0))
			sbc.action.add(-1);
		iw = (int) (hr * right.getWidth());
		ih = (int) (hr * right.getHeight());
		if (!new PP(p).out(new P(w - iw, h - ih), new P(w, h), 0)) {
			if (button == MouseEvent.BUTTON3 && sbc instanceof OnlineBattleField && ((OnlineBattleField) sbc).rouletteMode())
				((OnlineBattleField) sbc).toggleRouletteAuto();
			else
				sbc.action.add(-2);
		}

		if ((controlState().conf[0] & 2) > 0) {
			FakeImage bimg = aux.battle[2][1].getImg();
			int cw = bimg.getWidth();
			int ch = bimg.getHeight();
			int mh = aux.num[0][0].getImg().getHeight();
			if (!new PP(p).out(new P(w - cw, mh), new P(w, mh + ch), 0))
				sbc.action.add(-3);
		}
		reset();
	}

	@Override
	protected synchronized void release() {
		if (dragging && MainBCU.clickWithinDragWindow) {
			int totalDrag = Math.abs(dragInit.x - dragEnd.x) + Math.abs(dragInit.y - dragEnd.y);
			if (totalDrag < 5)
				click(dragEnd, dragButton);
		}
		super.release();
		performed = false;
		dragInit = null;
		dragEnd = null;
	}

	@Override
	protected synchronized void drag(Point p, int button) {
		if(!dragging) {
			dragInit = p;
			dragButton = button;
		}

		dragging = true;
		dragEnd = p;

		if(!CommonStatic.getConfig().twoRow)
			checkDragUpDown();

		super.drag(p, button);
	}

	private void checkDragUpDown() {
		if(controlState().isOneLineup || controlState().ubase.health == 0 || dragInit == null || dragEnd == null || dragFrame == 0 || performed)
			return;

		final double MINIMUM_DISTANCE = box.getHeight() * 0.2;
		final double MINIMUM_VELOCITY = MINIMUM_DISTANCE / 30; //px/f cursor must be dragged in 1 sec

		if(isInDragRange(MINIMUM_DISTANCE)) {
			double dy = dragEnd.y - dragInit.y;
			double velocity = dy / dragFrame;

			if(Math.abs(velocity) >= MINIMUM_VELOCITY && Math.abs(dy) >= MINIMUM_DISTANCE) {
				//Notice program dragging up/down is already performed
				//Won't process dragging up/down until drag is reset (mouse released)
				performed = true;

				if(velocity < 0)
					sbc.action.add(-4);
				else
					sbc.action.add(-5);
			}
		}
	}

	private boolean isInDragRange(double minD) {
		double dx = dragEnd.x - dragInit.x;

		//Drag up down, dx shouldn't exceed minimum off path
		return minD >= Math.abs(dx);
	}
}
