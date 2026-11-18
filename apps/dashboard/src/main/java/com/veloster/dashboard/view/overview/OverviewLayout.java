package com.veloster.dashboard.view.overview;

import com.veloster.dashboard.vehicle.*;
import com.veloster.dashboard.vehicle.js.JsGearState;
import com.veloster.dashboard.theme.ThemeSpec;
import com.veloster.dashboard.view.DashboardPage;

/** Overview signal choices and page geometry; signal preferences remain keyed by signal ID. */
public final class OverviewLayout {
    private static final int[] ORDER={7,0,8,9,10,1,2,29,14,13,15,16,17,11,12,18,22,26,25,27};
    private final int[] styles=new int[Telemetry.LABELS.length];
    private boolean pd,js;
    private ThemeSpec theme=ThemeSpec.original();
    private ThemeLayout layout=new ThemeLayout(theme);
    private int page;
    private float viewportWidth=800,viewportHeight=480;
    private boolean sideMedia;
    private static void check(int i){if(i<0||i>=Telemetry.LABELS.length)throw new IllegalArgumentException("overview signal");}
    public OverviewLayout(){for(int i=0;i<styles.length;i++)styles[i]=2;}
    public void pdProfile(boolean value){pd=value;}
    public void jsProfile(boolean value){js=value;}
    public void theme(ThemeSpec value){theme=value==null?ThemeSpec.original():value;layout=new ThemeLayout(theme);page=Math.min(page,pageCount()-1);}
    public void viewport(float width,float height){viewport(width,height,false);}
    public void viewport(float width,float height,boolean sideMedia){viewportWidth=Math.max(1,width);viewportHeight=Math.max(1,height);this.sideMedia=sideMedia;}
    public ThemeSpec theme(){return theme;}
    public int page(){return page;}
    public int pageCount(){return theme.overview()==null?layout.pages(ORDER.length):theme.overview().pages;}
    public boolean page(int value){if(value<0||value>=pageCount()||page==value)return false;page=value;return true;}
    public int count(){if(theme.overview()==null)return Math.min(layout.pageSize(),ORDER.length-page*layout.pageSize());int n=0;for(int i=0;i<theme.overview().count();i++)if(theme.overview().card(i).page==page)n++;return n;}
    private ThemeSpec.Card cardAt(int slot){
        if(slot<0)throw new IllegalArgumentException("overview slot");
        for(int i=0;i<theme.overview().count();i++){ThemeSpec.Card card=theme.overview().card(i);if(card.page==page&&slot--==0)return card;}
        throw new IllegalArgumentException("overview slot");
    }
    private int absoluteSlot(int slot){if(slot<0||slot>=count())throw new IllegalArgumentException("overview slot");return page*layout.pageSize()+slot;}
    public int signalAt(int slot){
        if(theme.overview()!=null)return cardAt(slot).source;
        int absolute=absoluteSlot(slot);return js&&absolute==18?Telemetry.CLUSTER_FUEL:pd&&absolute==3?Telemetry.ACCEL_PEDAL:pd&&absolute==10?9:pd&&absolute==16?Telemetry.PD_TCS_ENABLED:ORDER[absolute];
    }
    private int slot(int signal){check(signal);for(int i=0;i<count();i++)if(signalAt(i)==signal)return i;throw new IllegalArgumentException("overview signal is not on current page");}
    public int style(int signal){check(signal);return styles[signal];}
    public void style(int signal,int value){check(signal);if(value<0||value>2)throw new IllegalArgumentException("overview style");styles[signal]=value;}
    /** JS reuses the oil card's geometry, while oil telemetry and preferences keep their own ID. */
    public static boolean dctCard(int signal,boolean js){return js&&signal==1;}
    public boolean grouped(int signal){return (signal==Telemetry.CLUSTER_FUEL&&js)||signal==Telemetry.TPMS_FL||signal==Telemetry.TCS||signal==Telemetry.PD_TCS_ENABLED;}
    public void cycle(int signal){if(grouped(signal))return;style(signal,style(signal)==2?1:style(signal)==1?0:2);}
    public boolean headline(int signal){return theme.overview()==null&&page==0&&slot(signal)<5;}
    /** Returns the target overview page for the visible pager buttons, or -1. */
    public int pageControlTarget(float x,float y){
        if(!hasPager()||y<pagerY()-22||y>pagerY()+10)return -1;
        x=x-pagerX();
        int target=x>=-70&&x<-16?page-1:x>16&&x<=70?page+1:-1;
        return target>=0&&target<pageCount()?target:-1;
    }
    public float left(int signal){return leftAt(slot(signal));}
    public float top(int signal){return topAt(slot(signal));}
    public float width(int signal){return widthAt(slot(signal));}
    public float height(int signal){return heightAt(slot(signal));}
    private DashboardPage.Bounds grid(){
        DashboardPage.Bounds b=new DashboardPage.Content(theme,"overview",viewportWidth,viewportHeight,false,sideMedia).padded(theme);
        ThemeSpec.Overview o=theme.overview();
        int left=o==null?0:o.left,right=o==null?0:o.right,top=o==null?0:o.top,bottom=o==null?0:o.bottom;
        int pager=hasPager()?22+theme.spacing.gridGapY:0;
        return new DashboardPage.Bounds(b.x+left,b.y+top,b.width-left-right,b.height-top-bottom-pager);
    }
    public boolean hasPager(){return pageCount()>1;}
    public float pagerX(){return grid().atX(.5f);}
    public float pagerY(){return grid().bottom()+theme.spacing.gridGapY+16;}
    private int columns(){return theme.overview()==null?theme.columns:theme.overview().columns;}
    private int rows(){return theme.overview()==null?theme.rows:theme.overview().rows;}
    private int gapX(){return theme.overview()==null?theme.spacing.gridGapX:theme.overview().gapX;}
    private int gapY(){return theme.overview()==null?theme.spacing.gridGapY:theme.overview().gapY;}
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
