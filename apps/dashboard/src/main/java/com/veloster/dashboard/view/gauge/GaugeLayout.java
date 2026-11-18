package com.veloster.dashboard.view.gauge;

import com.veloster.dashboard.vehicle.*;
import com.veloster.dashboard.theme.ThemeSpec;
import com.veloster.dashboard.view.DashboardPage;
import com.veloster.dashboard.view.DashboardPage.Bounds;

import com.veloster.dashboard.vehicle.js.JsGearState;

/** Six or twelve display slots. Preferences never write vehicle settings. */
public final class GaugeLayout {
    public static final int SLOTS=12;
    private static final int[] DEFAULT_SIGNALS={0,7,9,1,2,10,11,12,15,16,17,13};
    private final int[] signals=new int[SLOTS], styles=new int[SLOTS];
    private boolean dense;
    public GaugeLayout(){reset();}
    private static void slot(int i){if(i<0||i>=SLOTS)throw new IllegalArgumentException("slot");}
    public int signal(int i){slot(i);return signals[i];}
    public int style(int i){slot(i);return styles[i];}
    public int count(){return dense?12:6;}
    public int columns(){return dense?4:3;}
    public boolean dense(){return dense;}
    public void dense(boolean value){dense=value;}
    private ThemeSpec theme=ThemeSpec.original();
    private Bounds area=new DashboardPage.Content(theme,"gauges").padded(theme);
    public void viewport(ThemeSpec theme,DashboardPage.Content content){this.theme=theme;area=content.padded(theme);}
    public Bounds toggle(){float w=Math.min(area.width*.36f,theme.spacing.buttonPadding*2+112);
        return new Bounds(area.right()-w,area.y,w,26);}
    public Bounds card(int i){
        slot(i);
        int cols=columns(),rows=count()/cols,gx=theme.spacing.gridGapX,gy=theme.spacing.gridGapY;
        float top=toggle().bottom()+gy,w=(area.width-(cols-1)*gx)/cols,h=(area.bottom()-top-(rows-1)*gy)/rows;
        return new Bounds(area.x+i%cols*(w+gx),top+i/cols*(h+gy),w,h);
    }
    public float left(int i){return card(i).x;}
    public float top(int i){return card(i).y;}
    public float width(){return card(0).width;}
    public float height(){return card(0).height;}
    public void set(int i,int signal,int style){
        slot(i);
        if(signal<0||signal>=Telemetry.LABELS.length||style<0||style>2)throw new IllegalArgumentException("gauge selection");
        signals[i]=signal;styles[i]=style;
    }
    public void swap(int a,int b){slot(a);slot(b);int n=signals[a];signals[a]=signals[b];signals[b]=n;n=styles[a];styles[a]=styles[b];styles[b]=n;}
    public void reset(){for(int i=0;i<SLOTS;i++){signals[i]=DEFAULT_SIGNALS[i];styles[i]=i<3?0:2;}}
    /** Card bounds in the current viewport. Gaps are not touch targets. */
    public int hit(float x,float y){
        for(int i=0;i<count();i++)if(card(i).contains(x,y))return i;
        return -1;
    }
}
