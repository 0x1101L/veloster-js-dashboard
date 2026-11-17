package com.veloster.dashboard.view.overview;

import com.veloster.dashboard.vehicle.*;
import com.veloster.dashboard.vehicle.js.JsGearState;
import com.veloster.dashboard.theme.ThemeSpec;

/** Overview signal choices and page geometry; signal preferences remain keyed by signal ID. */
public final class OverviewLayout {
    private static final int[] ORDER={7,0,8,9,10,1,2,29,14,13,15,16,17,11,12,18,22,26,25,27};
    private final int[] styles=new int[Telemetry.LABELS.length];
    private boolean pd,js;
    private ThemeSpec theme=ThemeSpec.original();
    private ThemeLayout layout=new ThemeLayout(theme);
    private int page;
    private static void check(int i){if(i<0||i>=Telemetry.LABELS.length)throw new IllegalArgumentException("overview signal");}
    public OverviewLayout(){for(int i=0;i<styles.length;i++)styles[i]=2;}
    public void pdProfile(boolean value){pd=value;}
    public void jsProfile(boolean value){js=value;}
    public void theme(ThemeSpec value){theme=value==null?ThemeSpec.original():value;layout=new ThemeLayout(theme);page=Math.min(page,pageCount()-1);}
    public ThemeSpec theme(){return theme;}
    public int page(){return page;}
    public int pageCount(){return layout.pages(ORDER.length);}
    public boolean page(int value){if(value<0||value>=pageCount()||page==value)return false;page=value;return true;}
    public int count(){return Math.min(layout.pageSize(),ORDER.length-page*layout.pageSize());}
    private int absoluteSlot(int slot){if(slot<0||slot>=count())throw new IllegalArgumentException("overview slot");return page*layout.pageSize()+slot;}
    public int signalAt(int slot){int absolute=absoluteSlot(slot);return js&&absolute==18?Telemetry.CLUSTER_FUEL:pd&&absolute==3?Telemetry.ACCEL_PEDAL:pd&&absolute==10?9:pd&&absolute==16?Telemetry.PD_TCS_ENABLED:ORDER[absolute];}
    private int slot(int signal){check(signal);for(int i=0;i<count();i++)if(signalAt(i)==signal)return i;throw new IllegalArgumentException("overview signal is not on current page");}
    public int style(int signal){check(signal);return styles[signal];}
    public void style(int signal,int value){check(signal);if(value<0||value>2)throw new IllegalArgumentException("overview style");styles[signal]=value;}
    /** JS reuses the oil card's geometry, while oil telemetry and preferences keep their own ID. */
    public static boolean dctCard(int signal,boolean js){return js&&signal==1;}
    public boolean grouped(int signal){return signal==Telemetry.CLUSTER_FUEL||signal==Telemetry.TPMS_FL||signal==Telemetry.TCS||signal==Telemetry.PD_TCS_ENABLED;}
    public void cycle(int signal){if(grouped(signal))return;style(signal,style(signal)==2?1:style(signal)==1?0:2);}
    public boolean headline(int signal){return page==0&&slot(signal)<5;}
    /** Returns the target overview page for the visible Focus pager buttons, or -1. */
    public int pageControlTarget(float x,float y){
        if(theme.isOriginal()||pageCount()<2||y<448||y>480)return -1;
        int target=x>=330&&x<384?page-1:x>416&&x<=470?page+1:-1;
        return target>=0&&target<pageCount()?target:-1;
    }
    public float left(int signal){return layout.left(slot(signal));}
    public float top(int signal){return layout.top(slot(signal));}
    public float width(int signal){slot(signal);return layout.width();}
    public float height(int signal){slot(signal);return layout.height();}
    public int hit(float x,float y){
        for(int slot=0;slot<count();slot++){int signal=signalAt(slot);float l=left(signal),t=top(signal);if(x>=l&&x<l+width(signal)&&y>=t&&y<t+height(signal))return signal;}
        return -1;
    }
}
