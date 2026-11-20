package com.veloster.dashboard.view;

import com.veloster.dashboard.vehicle.*;
import com.veloster.dashboard.theme.ThemeSpec;
import com.veloster.dashboard.theme.ThemeSurface;
import com.veloster.dashboard.theme.ThemeResources;

import com.veloster.dashboard.view.overview.OverviewLayout;
import com.veloster.dashboard.view.overview.OverviewRenderer;
import com.veloster.dashboard.view.gauge.GaugeLayout;
import com.veloster.dashboard.view.gauge.GaugeRenderer;
import com.veloster.dashboard.view.graph.GraphLayout;
import com.veloster.dashboard.view.graph.GraphRenderer;
import com.veloster.dashboard.view.f1.ClusterRenderer;
import com.veloster.dashboard.view.performance.PerformanceRenderer;

import com.veloster.i18n.UiText;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.Path;
import android.graphics.Bitmap;
import java.util.Locale;

/** Original N/PIT drawing geometry, with only received vehicle samples as values. */
public final class PitRenderer {
    private final ThemeSurface surface=new ThemeSurface();
    private final Paint paint=new Paint(3);

    private static final int BG=0xff040506,PANEL=0xff101315,LINE=0xff303638,BLUE=0xffa4d9ef,WHITE=0xffe1edf2,MUTED=0xff91a5ae,RED=0xffff4059;
    private final ClusterRenderer clusterRenderer=new ClusterRenderer(false,surface);
    private Bitmap artwork;
    private final OverviewRenderer overviewRenderer=new OverviewRenderer(surface);
    private final GaugeRenderer gaugeRenderer=new GaugeRenderer();
    private final GraphRenderer graphRenderer=new GraphRenderer();
    private GraphLayout graphLayout;
    private GaugeLayout gaugeLayout;
    private OverviewLayout overviewLayout=new OverviewLayout();
    private ThemeSpec theme=ThemeSpec.original();
    private ThemeResources resources;
    private boolean performancePage;
    private boolean settingsPage;
    private final MenuIcons menuIcons=new MenuIcons();
    private DashboardPage.Menu pageMenu=new DashboardPage.Menu();
    public void setPageMenu(DashboardPage.Menu value){pageMenu=value;}
    private int mediaBarPolicy=MediaBar.DEFAULT;
    private boolean mediaNotices=true;
    private final MediaBar mediaBar=new MediaBar(surface);
    private String settingsLanguage="System / 시스템",settingsTheme="Original",settingsMenuPosition=SettingsPageRenderer.menuPositionLabel(ThemeSpec.MENU_THEME);
    private final SettingsPageRenderer settingsRenderer=new SettingsPageRenderer(surface);
    private final PerformanceRenderer performanceRenderer=new PerformanceRenderer();
    private final java.text.SimpleDateFormat clockFormat=new java.text.SimpleDateFormat("HH:mm",Locale.US);
    private long clockMinute=Long.MIN_VALUE;
    private String clockText="";
    private String clock(){
        long now=System.currentTimeMillis(),minute=now/60000;
        if(minute!=clockMinute){clockMinute=minute;clockFormat.setTimeZone(java.util.TimeZone.getDefault());clockText=clockFormat.format(new java.util.Date(now));}
        return clockText;
    }
    public void setPerformancePage(boolean value){performancePage=value;}
    public void setMediaDisplay(int policy,boolean notices){mediaBarPolicy=MediaBar.normalize(policy);mediaNotices=notices;}
    public void setMediaDisplay(int policy,boolean notices,boolean controls){setMediaDisplay(policy,notices);mediaBar.setControlsVisible(controls);}
    public void setMediaDisplay(int policy,boolean notices,boolean controls,boolean icons){setMediaDisplay(policy,notices,controls);mediaBar.setButtonIcons(icons);}
    private SettingsPageRenderer.Editor settingsEditor;
    public void setSettingsEditor(SettingsPageRenderer.Editor editor){settingsEditor=editor;}
    public void setSettingsPage(boolean value,String language,String themeName,String menuPosition){settingsPage=value;settingsLanguage=language;settingsTheme=themeName;settingsMenuPosition=menuPosition;}
    public void setArtwork(Bitmap value){artwork=value;}
    public void setGaugeLayout(GaugeLayout value){gaugeLayout=value;}
    public void setGraphLayout(GraphLayout value){graphLayout=value;}
    public void setOverviewLayout(OverviewLayout value){if(value!=null)overviewLayout=value;}
    public void setTheme(ThemeSpec value){setTheme(value,null);}
    public void setTheme(ThemeSpec value,ThemeResources assets){theme=value==null?ThemeSpec.original():value;resources=assets;overviewRenderer.setTheme(theme,assets);clusterRenderer.setTheme(theme,assets);gaugeRenderer.setTheme(theme,assets);graphRenderer.setTheme(theme,assets);performanceRenderer.setTheme(theme,assets);}
    public void draw(Canvas c,int width,int height,boolean cluster,Telemetry model,long now){
        draw(c,width,height,cluster,model,now,ShiftGuide.DEFAULT);
    }
    public void draw(Canvas c,int width,int height,boolean cluster,Telemetry model,long now,int target){
        draw(c,width,height,cluster,model,now,target,false,null);
    }
    public void draw(Canvas c,int width,int height,boolean cluster,Telemetry model,long now,int target,boolean media,MediaState.Snapshot track){
        draw(c,width,height,cluster,model,now,target,media,track,DashboardPage.regions(width,height,theme));
    }
    void draw(Canvas c,int width,int height,boolean cluster,Telemetry model,long now,int target,boolean media,MediaState.Snapshot track,DashboardPage.Regions regions){
        boolean expanded=media&&!regions.mediaEnabled;
        boolean pdProfile=model.pdProfile(),jsProfile=model.jsProfile();
        overviewLayout.pdProfile(pdProfile||jsProfile);performanceRenderer.profile(pdProfile,jsProfile,model.jsnProfile(),model.supportedProfile());
        Telemetry.Sample[] samples=model.snapshot(now);
        clusterRenderer.prepare(model,samples,now,ShiftGuide.clamp(target,model.pdLayoutProfile()),false,track,artwork);
        surface.backdrop(c,theme,resources,width,height);
        if(!theme.shell().left()){
            c.save();c.clipRect(regions.topMenu.x,regions.topMenu.y,regions.topMenu.x+regions.topMenu.width,regions.topMenu.y+regions.topMenu.height);
            float outerX=theme.layoutSpacing().horizontalPadding*regions.scale,outerY=theme.layoutSpacing().verticalPadding*regions.scale;
            box(c,outerX,outerY,width-outerX,outerY+regions.headerHeight,theme.panel);box(c,outerX,outerY+regions.headerHeight-1,width-outerX,outerY+regions.headerHeight,theme.stroke);
            c.scale(regions.scale,regions.scale);
            float navWidth=regions.logicalDisplayWidth;
            menuButton(c,UiText.t("퍼포먼스"),DashboardPage.PERFORMANCE,performancePage,navWidth);
            menuButton(c,UiText.t("전체 조망"),DashboardPage.OVERVIEW,!settingsPage&&!performancePage&&!cluster&&gaugeLayout==null&&graphLayout==null,navWidth);
            menuButton(c,"F1",DashboardPage.F1,cluster&&gaugeLayout==null&&graphLayout==null,navWidth);
            menuButton(c,UiText.t("게이지"),DashboardPage.GAUGES,gaugeLayout!=null,navWidth);
            menuButton(c,UiText.t("그래프"),DashboardPage.GRAPHS,graphLayout!=null,navWidth);
            menuButton(c,UiText.t("설정"),DashboardPage.SETTINGS,settingsPage,navWidth);
            DashboardPage.Bounds topClock=DashboardPage.navigationBounds(theme,pageMenu,DashboardPage.CLOCK,navWidth);
            surface.navigationClock(c,theme,resources,paint,clock(),topClock.x,topClock.y,topClock.width,topClock.height,theme.shell().topMenuClockPosition);
            menuButton(c,UiText.t("돌아가기"),DashboardPage.BACK,false,navWidth);
            c.restore();
        }
        c.save();c.clipRect(regions.content.x,regions.content.y,regions.content.x+regions.content.width,regions.content.y+regions.content.height);
        c.translate(regions.left,regions.top);c.scale(regions.scale,regions.scale);
        surface.coordinates(regions.left,regions.top,regions.scale);
        if(theme.shell().left()){
            DashboardPage.Bounds menuSurface=DashboardPage.leftMenuBounds(theme);
            surface.navigationMenu(c,theme,paint,menuSurface.x,menuSurface.y,menuSurface.width,menuSurface.height);
            menuButton(c,UiText.t("퍼포먼스"),DashboardPage.PERFORMANCE,performancePage);
            menuButton(c,UiText.t("전체 조망"),DashboardPage.OVERVIEW,!settingsPage&&!performancePage&&!cluster&&gaugeLayout==null&&graphLayout==null);
            menuButton(c,"F1",DashboardPage.F1,cluster&&gaugeLayout==null&&graphLayout==null);
            menuButton(c,UiText.t("게이지"),DashboardPage.GAUGES,gaugeLayout!=null);
            menuButton(c,UiText.t("그래프"),DashboardPage.GRAPHS,graphLayout!=null);
            menuButton(c,UiText.t("설정"),DashboardPage.SETTINGS,settingsPage);
            DashboardPage.Bounds sideClock=DashboardPage.navigationBounds(theme,pageMenu,DashboardPage.CLOCK);
            surface.navigationClock(c,theme,resources,paint,clock(),sideClock.x,sideClock.y,sideClock.width,sideClock.height);
            menuButton(c,UiText.t("돌아가기"),DashboardPage.BACK,false);
        }
        String page=settingsPage?"settings":performancePage?"performance":graphLayout!=null?"graphs":gaugeLayout!=null?"gauges":cluster?"f1":"overview";
        boolean footer=MediaBar.footerVisible(regions,mediaBarPolicy,track);
        DashboardPage.Content content=new DashboardPage.Content(theme,page,regions,footer);
        overviewLayout.viewport(regions.logicalContentWidth+regions.logicalOuterPaddingX,
            content.bodyViewportHeight+regions.logicalOuterPaddingY,regions.mediaPanelVisible);
        c.save();c.clipRect(content.x,content.y,content.x+content.width,content.y+content.height);
        if(settingsPage){if(settingsEditor!=null)settingsRenderer.draw(c,theme,resources,settingsEditor,content);else settingsRenderer.draw(c,theme,resources,settingsLanguage,false,content);}
        else if(performancePage)performanceRenderer.draw(c,model,now,content);
        else if(graphLayout!=null){graphLayout.viewport(theme,content);graphRenderer.draw(c,model,now,graphLayout);}
        else if(gaugeLayout!=null){gaugeLayout.viewport(theme,content);gaugeRenderer.draw(c,model,now,gaugeLayout);}
        else if(cluster)clusterRenderer.drawAdaptive(c,content);
        else {overviewRenderer.draw(c,samples,overviewLayout,pdProfile,jsProfile);}
        if(!expanded&&MediaBar.noticeVisible(regions,mediaBarPolicy,mediaNotices,track))clusterRenderer.drawMedia(c,content.notice(theme));
        c.restore();
        if(footer)mediaBar.drawAdaptive(c,theme,resources,track,artwork,expanded,content);
        c.restore();
        if(regions.mediaPanelVisible){
            surface.coordinates(0,0,1);
            mediaBar.drawRegion(c,theme,resources,track,artwork,regions.media,regions.mediaEnabled);
        }
        surface.maskScreenCorners(c,theme,width,height,regions.scale);
    }
    private void box(Canvas c,float l,float t,float r,float b,int color){paint.setColor(color);c.drawRect(l,t,r,b,paint);}
    private void text(Canvas c,String s,float x,float y,float size,int color,boolean bold){
        rawText(c,s,x,y,theme.textSize(s,size,bold),color,bold);
    }
    private void rawText(Canvas c,String s,float x,float y,float size,int color,boolean bold){
        paint.setColor(color);paint.setTextSize(size);paint.setTypeface(resources==null?(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT):resources.fontForText(s,bold));c.drawText(s,x,y,paint);
    }
    private void menuButton(Canvas c,String label,int id,boolean on){
        menuButton(c,label,id,on,800);
    }
    private void menuButton(Canvas c,String label,int id,boolean on,float layoutWidth){
        if(!pageMenu.visible(id))return;
        DashboardPage.Bounds bounds=DashboardPage.navigationBounds(theme,pageMenu,id,layoutWidth);
        float x=bounds.x,y=bounds.y,w=bounds.width,h=bounds.height;
        boolean icons=pageMenu.icons();
        float contentX=theme.shell().left()?x+w/2:DashboardPage.topMenuContentX(theme,bounds);
        surface.navigationButton(c,theme,resources,paint,icons?null:label,x,y,w,h,on,theme.accent,theme.shell().left()?"center":theme.shell().topMenuContentAlignment);
        if(icons)menuIcons.draw(c,id,contentX,y+h/2,on?theme.accent:theme.textSecondary);
    }
    private void centered(Canvas c,String s,float x,float y,float size,int color,boolean bold){
        size=theme.textSize(s,size,bold);
        paint.setTextSize(size);paint.setTypeface(resources==null?(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT):resources.fontForText(s,bold));
        rawText(c,s,x-paint.measureText(s)/2,y,size,color,bold);
    }
}
