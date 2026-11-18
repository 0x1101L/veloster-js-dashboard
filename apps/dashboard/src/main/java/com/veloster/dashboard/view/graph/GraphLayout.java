package com.veloster.dashboard.view.graph;

import com.veloster.dashboard.vehicle.*;
import com.veloster.dashboard.theme.ThemeSpec;
import com.veloster.dashboard.view.DashboardPage;
import com.veloster.dashboard.view.DashboardPage.Bounds;

import com.veloster.dashboard.vehicle.js.JsGearState;

/** Three selectable time-series plots; preferences are independent of gauge cards. */
public final class GraphLayout {
    public static final int SLOTS=3;
    private final int[] signals={1,2,Telemetry.STEERING};
    private long window=60000;
    public int signal(int slot){check(slot);return signals[slot];}
    public void signal(int slot,int signal){check(slot);if(signal<0||signal>=Telemetry.LABELS.length)throw new IllegalArgumentException("graph signal");signals[slot]=signal;}
    public long window(){return window;}
    public void window(long value){if(value!=60000&&value!=300000)throw new IllegalArgumentException("graph window");window=value;}
    private ThemeSpec theme=ThemeSpec.original();
    private Bounds area=new DashboardPage.Content(theme,"graphs").padded(theme);
    public void viewport(ThemeSpec theme,DashboardPage.Content content){this.theme=theme;area=content.padded(theme);}
    public Bounds timeButton(int i){
        float w=Math.min(area.width*.18f,theme.spacing.buttonPadding*2+50),gap=theme.spacing.gridGapX;
        return new Bounds(area.right()-w-(1-i)*(w+gap),area.y,w,26);
    }
    public Bounds card(int slot){
        check(slot);
        int gap=theme.spacing.gridGapY;
        float top=timeButton(0).bottom()+gap,h=(area.bottom()-top-(SLOTS-1)*gap)/SLOTS;
        return new Bounds(area.x,top+slot*(h+gap),area.width,h);
    }
    private static void check(int slot){if(slot<0||slot>=SLOTS)throw new IllegalArgumentException("graph slot");}
    public int hit(float x,float y){for(int i=0;i<SLOTS;i++)if(card(i).contains(x,y))return i;return -1;}
}
