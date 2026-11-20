package com.veloster.dashboard.view.overview;

import com.veloster.dashboard.vehicle.*;
import com.veloster.dashboard.vehicle.js.JsGearState;
import com.veloster.dashboard.theme.ThemeSpec;
import com.veloster.dashboard.view.DashboardPage;

/** Overview signal choices and fixed 5x4 geometry; preferences remain keyed by signal ID. */
public final class OverviewLayout {
    private static final int[] ORDER={7,0,8,9,10,1,2,29,14,13,15,16,17,11,12,18,22,26,25,27};
    private final int[] styles=new int[Telemetry.LABELS.length];
    private boolean pd,js;
    private boolean jsn;
    private int[] configuredSignals;
    private ThemeSpec theme=ThemeSpec.original();
    private ThemeLayout layout=new ThemeLayout(theme);
    private float viewportWidth=800,viewportHeight=480;
    private boolean sideMedia;
    private static void check(int i){if(i<0||i>=Telemetry.LABELS.length)throw new IllegalArgumentException("overview signal");}
    public OverviewLayout(){for(int i=0;i<styles.length;i++)styles[i]=2;}
    public void pdProfile(boolean value){pd=value;}
    public void jsProfile(boolean value){js=value;}
    public void jsnProfile(boolean value){jsn=value;}
    public void profile(boolean pd,boolean js,boolean jsn){this.pd=pd;this.js=js;this.jsn=jsn;}
    public void theme(ThemeSpec value){theme=value==null?ThemeSpec.original():value;layout=new ThemeLayout(theme);}
    public void viewport(float width,float height){viewport(width,height,false);}
    public void viewport(float width,float height,boolean sideMedia){viewportWidth=Math.max(1,width);viewportHeight=Math.max(1,height);this.sideMedia=sideMedia;}
    public ThemeSpec theme(){return theme;}
    public int slotCapacity(){return theme.overview()==null?ORDER.length:theme.overview().count();}
    public int count(){return configuredSignals==null?slotCapacity():Math.min(configuredSignals.length,slotCapacity());}
    private ThemeSpec.Card cardAt(int slot){
        if(slot<0)throw new IllegalArgumentException("overview slot");
        for(int i=0;i<theme.overview().count();i++)if(slot--==0)return theme.overview().card(i);
        throw new IllegalArgumentException("overview slot");
    }
    private int absoluteSlot(int slot){if(slot<0||slot>=count())throw new IllegalArgumentException("overview slot");return slot;}
    public int signalAt(int slot){
        if(configuredSignals!=null){if(slot<0||slot>=count())throw new IllegalArgumentException("overview slot");return configuredSignals[slot];}
        if(theme.overview()!=null)return cardAt(slot).source;
        int absolute=absoluteSlot(slot);return js&&absolute==18?Telemetry.CLUSTER_FUEL:pd&&absolute==3?Telemetry.ACCEL_PEDAL:pd&&absolute==10?9:pd&&absolute==16?Telemetry.PD_TCS_ENABLED:ORDER[absolute];
    }
    private int slot(int signal){check(signal);for(int i=0;i<count();i++)if(signalAt(i)==signal)return i;throw new IllegalArgumentException("overview signal is not in the fixed grid");}
    public int style(int signal){check(signal);return styles[signal];}
    public void style(int signal,int value){check(signal);if(value<0||value>2)throw new IllegalArgumentException("overview style");styles[signal]=value;}
    /** JS reuses the oil card's geometry, while oil telemetry and preferences keep their own ID. */
    public static boolean dctCard(int signal,boolean js){return js&&signal==1;}
    public boolean grouped(int signal){return (signal==Telemetry.CLUSTER_FUEL&&js)||signal==Telemetry.TPMS_FL||signal==Telemetry.TCS||signal==Telemetry.PD_TCS_ENABLED;}
    public void cycle(int signal){if(grouped(signal))return;style(signal,style(signal)==2?1:style(signal)==1?0:2);}
    public boolean headline(int signal){return configuredSignals==null&&theme.overview()==null&&slot(signal)<5;}
    public int[] defaultSignals(){
        if(theme.overview()!=null){int[] result=new int[theme.overview().count()];for(int i=0;i<result.length;i++)result[i]=cardAt(i).source;return result;}
        int[] result=ORDER.clone();if(js)result[18]=Telemetry.CLUSTER_FUEL;if(pd)result[3]=Telemetry.ACCEL_PEDAL;
        if(pd)result[10]=9;if(pd)result[16]=Telemetry.PD_TCS_ENABLED;return result;
    }
    public int[] configuredSignals(){return configuredSignals==null?null:configuredSignals.clone();}
    public void configuredSignals(int[] signals){
        if(signals==null){configuredSignals=null;return;}
        if(signals.length>Telemetry.LABELS.length)throw new IllegalArgumentException("too many overview cards");
        boolean[] seen=new boolean[Telemetry.LABELS.length];
        for(int signal:signals){check(signal);if(seen[signal]||!available(signal))throw new IllegalArgumentException("unsupported overview signal");seen[signal]=true;}
        configuredSignals=signals.clone();
    }
    public void moveUp(int slot){
        if(slot<1||slot>=count())return;
        if(configuredSignals==null)configuredSignals=defaultSignals();
        int value=configuredSignals[slot];configuredSignals[slot]=configuredSignals[slot-1];configuredSignals[slot-1]=value;
    }
    public boolean available(int signal){
        if(signal<0||signal>=Telemetry.LABELS.length)return false;
        if(jsn)return signal==3||signal==4||signal==5||signal==6||signal==22||signal==23||signal==24||signal==25||signal==26||signal==27||signal==29;
        if(!(pd||js)){
            if(theme.overview()!=null)for(int i=0;i<theme.overview().count();i++)if(theme.overview().card(i).source==signal)return true;
            return false;
        }
        if(signal==28||signal==3||signal==4||signal==5||signal==6)return js;
        if(signal>=34)return js&&signal<=35;
        return signal<=27||signal>=29&&signal<=33;
    }
    public int[] availableSignals(){
        int[] result=new int[Telemetry.LABELS.length];int at=0;
        for(int signal:defaultSignals())if(available(signal)&&!resultContains(result,at,signal))result[at++]=signal;
        for(int i=0;i<Telemetry.LABELS.length;i++)if(available(i)&&!resultContains(result,at,i))result[at++]=i;
        return java.util.Arrays.copyOf(result,at);
    }
    private static boolean resultContains(int[] values,int length,int value){for(int i=0;i<length;i++)if(values[i]==value)return true;return false;}
    public float left(int signal){return leftAt(slot(signal));}
    public float top(int signal){return topAt(slot(signal));}
    public float width(int signal){return widthAt(slot(signal));}
    public float height(int signal){return heightAt(slot(signal));}
    private DashboardPage.Bounds grid(){
        DashboardPage.Bounds b=new DashboardPage.Content(theme,"overview",viewportWidth,viewportHeight,false,sideMedia).padded(theme);
        ThemeSpec.Overview o=theme.overview();
        int left=o==null?0:o.left,right=o==null?0:o.right,top=o==null?0:o.top,bottom=o==null?0:o.bottom;
        return new DashboardPage.Bounds(b.x+left,b.y+top,b.width-left-right,b.height-top-bottom);
    }
    private int columns(){return theme.overview()==null?theme.columns:theme.overview().columns;}
    private int rows(){return theme.overview()==null?theme.rows:theme.overview().rows;}
    private int gapX(){return theme.overview()==null?theme.layoutSpacing().gap:theme.overview().gapX;}
    private int gapY(){return theme.overview()==null?theme.layoutSpacing().gap:theme.overview().gapY;}
    private float cellWidth(DashboardPage.Bounds b){return (b.width-(columns()-1)*gapX())/columns();}
    private float cellHeight(DashboardPage.Bounds b){return (b.height-(rows()-1)*gapY())/rows();}
    public float leftAt(int slot){DashboardPage.Bounds b=grid();return b.x+(theme.overview()==null?slot%columns():cardAt(slot).column)*(cellWidth(b)+gapX());}
    public float topAt(int slot){DashboardPage.Bounds b=grid();return b.y+(theme.overview()==null?slot/columns():cardAt(slot).row)*(cellHeight(b)+gapY());}
    public float widthAt(int slot){int span=theme.overview()==null?1:cardAt(slot).columnSpan;return span*cellWidth(grid())+(span-1)*gapX();}
    public float heightAt(int slot){int span=theme.overview()==null?1:cardAt(slot).rowSpan;return span*cellHeight(grid())+(span-1)*gapY();}
    public int hit(float x,float y){
        for(int slot=0;slot<count();slot++){int signal=signalAt(slot);float l=leftAt(slot),t=topAt(slot);if(x>=l&&x<l+widthAt(slot)&&y>=t&&y<t+heightAt(slot))return signal;}
        return -1;
    }
}
