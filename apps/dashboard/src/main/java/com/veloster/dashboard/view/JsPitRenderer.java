package com.veloster.dashboard.view;

import com.veloster.dashboard.vehicle.*;
import com.veloster.dashboard.theme.ThemeSpec;
import com.veloster.dashboard.theme.ThemeSurface;
import com.veloster.dashboard.theme.ThemeResources;
import com.veloster.dashboard.debugger.JsDiagState;
import com.veloster.dashboard.debugger.BluetoothRuntimeProbe;

import com.veloster.dashboard.view.overview.OverviewLayout;
import com.veloster.dashboard.view.overview.JsOverviewRenderer;
import com.veloster.dashboard.view.gauge.GaugeLayout;
import com.veloster.dashboard.view.gauge.JsGaugeRenderer;
import com.veloster.dashboard.view.graph.GraphLayout;
import com.veloster.dashboard.view.graph.GraphRenderer;
import com.veloster.dashboard.view.f1.JsClusterRenderer;
import com.veloster.dashboard.debugger.view.JsDiagnosticsRenderer;
import com.veloster.dashboard.view.performance.JsPerformanceRenderer;

import com.veloster.i18n.UiText;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.Path;
import android.graphics.Bitmap;
import java.util.Locale;

/** Original N/PIT drawing geometry, with only received vehicle samples as values. */
public final class JsPitRenderer {
    private final ThemeSurface surface=new ThemeSurface();
    private final Paint paint=new Paint(3);

    private static final int BG=0xff040506,PANEL=0xff101315,LINE=0xff303638,BLUE=0xffa4d9ef,WHITE=0xffe1edf2,MUTED=0xff91a5ae,RED=0xffff4059;
    private final JsClusterRenderer clusterRenderer=new JsClusterRenderer(surface);
    private Bitmap artwork;
    private final JsOverviewRenderer overviewRenderer=new JsOverviewRenderer(surface);
    private final JsGaugeRenderer gaugeRenderer=new JsGaugeRenderer();
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
    private boolean diagnosticPage;
    private JsDiagState.Snapshot diagnostic=new JsDiagState().snapshot();
    private String carPlayRawStatus="내부 저장 캡처 없음";
    private String carPlayLibraryStatus="미확인";
    private String carPlayProcessStatus="미확인";
    private BluetoothRuntimeProbe.Snapshot bluetoothStatus=BluetoothRuntimeProbe.parse("");
    private final JsPerformanceRenderer performanceRenderer=new JsPerformanceRenderer();
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
    private SettingsPageRenderer.Editor settingsEditor;
    public void setSettingsEditor(SettingsPageRenderer.Editor editor){settingsEditor=editor;}
    public void setSettingsPage(boolean value,String language,String themeName,String menuPosition){settingsPage=value;settingsLanguage=language;settingsTheme=themeName;settingsMenuPosition=menuPosition;}
    public void setDiagnosticPage(boolean value,JsDiagState.Snapshot status){diagnosticPage=value;if(status!=null)diagnostic=status;}
    private final JsDiagnosticsRenderer diagnosticsRenderer=new JsDiagnosticsRenderer();
    public void diagnosticsViewport(DashboardPage.Content content){diagnosticsRenderer.viewport(content);}
    public boolean transmissionHit(float x,float y){return diagnosticsRenderer.transmissionHit(x,y);}
    public boolean wifiSettingsHit(float x,float y){return diagnosticsRenderer.wifiSettingsHit(x,y);}
    public boolean selectDiagnosticTab(float downX,float downY,float x,float y){return diagnosticsRenderer.select(downX,downY,x,y);}
    public boolean carPlayImportHit(float x,float y){return diagnosticsRenderer.carPlayImportHit(x,y);}
    public void setCarPlayRawStatus(String value){carPlayRawStatus=value==null?"내부 저장 캡처 없음":value;}
    public void setCarPlayRuntimeStatus(String library,String process){carPlayLibraryStatus=library==null?"미확인":library;carPlayProcessStatus=process==null?"미확인":process;}
    public void setBluetoothStatus(BluetoothRuntimeProbe.Snapshot status){if(status!=null)bluetoothStatus=status;}
    public void setArtwork(Bitmap value){artwork=value;}
    public void setGaugeLayout(GaugeLayout value){gaugeLayout=value;}
    public void setGraphLayout(GraphLayout value){graphLayout=value;}
    public void setOverviewLayout(OverviewLayout value){if(value!=null)overviewLayout=value;}
    public void setTheme(ThemeSpec value){setTheme(value,null);}
    public void setTheme(ThemeSpec value,ThemeResources assets){theme=value==null?ThemeSpec.original():value;resources=assets;overviewRenderer.setTheme(theme,assets);clusterRenderer.setTheme(theme,assets);gaugeRenderer.setTheme(theme,assets);graphRenderer.setTheme(theme,assets);performanceRenderer.setTheme(theme,assets);diagnosticsRenderer.setTheme(theme,assets);}
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
        overviewLayout.pdProfile(pdProfile||jsProfile);overviewLayout.jsProfile(jsProfile);performanceRenderer.profile(pdProfile,jsProfile,model.jsnProfile(),model.supportedProfile());
        Telemetry.Sample[] samples=model.snapshot(now);
        clusterRenderer.prepare(model,samples,now,ShiftGuide.clamp(target,model.pdLayoutProfile()),false,track,artwork);
        surface.backdrop(c,theme,resources,width,height);
        if(!theme.shell().left()){
            c.save();c.clipRect(regions.topMenu.x,regions.topMenu.y,regions.topMenu.x+regions.topMenu.width,regions.topMenu.y+regions.topMenu.height);
            box(c,0,0,width,regions.headerHeight,theme.panel);box(c,0,regions.headerHeight-1,width,regions.headerHeight,theme.stroke);
            c.scale(regions.scale,regions.scale);
            float navWidth=regions.logicalDisplayWidth;
            menuButton(c,UiText.t("퍼포먼스"),DashboardPage.PERFORMANCE,performancePage,navWidth);
            menuButton(c,UiText.t("전체 조망"),DashboardPage.OVERVIEW,!settingsPage&&!diagnosticPage&&!performancePage&&!cluster&&gaugeLayout==null&&graphLayout==null,navWidth);
            menuButton(c,"F1",DashboardPage.F1,cluster&&gaugeLayout==null&&graphLayout==null,navWidth);
            menuButton(c,UiText.t("게이지"),DashboardPage.GAUGES,gaugeLayout!=null,navWidth);
            menuButton(c,UiText.t("그래프"),DashboardPage.GRAPHS,graphLayout!=null,navWidth);
            menuButton(c,UiText.t("설정"),DashboardPage.SETTINGS,settingsPage||diagnosticPage,navWidth);
            DashboardPage.Bounds topClock=DashboardPage.navigationBounds(theme,pageMenu,DashboardPage.CLOCK,navWidth);
            surface.navigationClock(c,theme,resources,paint,clock(),topClock.x,topClock.y,topClock.width,topClock.height);
            menuButton(c,UiText.t("돌아가기"),DashboardPage.BACK,false,navWidth);
            c.restore();
        }
        c.save();c.clipRect(regions.content.x,regions.content.y,regions.content.x+regions.content.width,regions.content.y+regions.content.height);
        c.translate(regions.left,regions.top);c.scale(regions.scale,regions.scale);
        surface.coordinates(regions.left,regions.top,regions.scale);
        if(theme.shell().left()){
            if(theme.shell().dock()){
                DashboardPage.Bounds dock=DashboardPage.dockBounds(theme);
                surface.drawWithLegacy(c,theme,ThemeSpec.SurfaceRole.NAVIGATION_DOCK,paint,
                        dock.x,dock.y,dock.width,dock.height,theme.panel,theme.stroke);
            }
            menuButton(c,UiText.t("퍼포먼스"),DashboardPage.PERFORMANCE,performancePage);
            menuButton(c,UiText.t("전체 조망"),DashboardPage.OVERVIEW,!settingsPage&&!diagnosticPage&&!performancePage&&!cluster&&gaugeLayout==null&&graphLayout==null);
            menuButton(c,"F1",DashboardPage.F1,cluster&&gaugeLayout==null&&graphLayout==null);
            menuButton(c,UiText.t("게이지"),DashboardPage.GAUGES,gaugeLayout!=null);
            menuButton(c,UiText.t("그래프"),DashboardPage.GRAPHS,graphLayout!=null);
            menuButton(c,UiText.t("설정"),DashboardPage.SETTINGS,settingsPage||diagnosticPage);
            DashboardPage.Bounds sideClock=DashboardPage.navigationBounds(theme,pageMenu,DashboardPage.CLOCK);
            surface.navigationClock(c,theme,resources,paint,clock(),sideClock.x,sideClock.y,sideClock.width,sideClock.height);
            menuButton(c,UiText.t("돌아가기"),DashboardPage.BACK,false);
        }
        String page=settingsPage?"settings":diagnosticPage?"diagnostics":performancePage?"performance":graphLayout!=null?"graphs":gaugeLayout!=null?"gauges":cluster?"f1":"overview";
        boolean footer=MediaBar.footerVisible(regions,mediaBarPolicy,track);
        DashboardPage.Content content=new DashboardPage.Content(theme,page,regions,footer);
        overviewLayout.viewport(regions.logicalContentWidth,content.bodyViewportHeight,regions.mediaPanelVisible);
        c.save();c.clipRect(content.x,content.y,content.x+content.width,content.y+content.height);
        if(settingsPage){if(settingsEditor!=null)settingsRenderer.draw(c,theme,resources,settingsEditor,content);else settingsRenderer.draw(c,theme,resources,settingsLanguage,model.jsProfile(),content);}
        else if(diagnosticPage){diagnosticsRenderer.viewport(content);diagnosticsRenderer.draw(c,samples,diagnostic,model.dctTemperatures(now),track,carPlayRawStatus,carPlayLibraryStatus,carPlayProcessStatus,bluetoothStatus);}
        else if(performancePage)performanceRenderer.draw(c,model,now,content);
        else if(graphLayout!=null){graphLayout.viewport(theme,content);graphRenderer.draw(c,model,now,graphLayout);}
        else if(gaugeLayout!=null){gaugeLayout.viewport(theme,content);gaugeRenderer.draw(c,model,now,gaugeLayout);}
        else if(cluster)clusterRenderer.drawAdaptive(c,content);
        else {overviewRenderer.draw(c,samples,overviewLayout,pdProfile,jsProfile,model.dctTemperatures(now),model.jsDriveMode(now),model.jsFuelTrial(now));drawOverviewPager(c);}
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
    private void drawOverviewPager(Canvas c){if(!overviewLayout.hasPager())return;float x=overviewLayout.pagerX(),y=overviewLayout.pagerY();centered(c,"‹",x-42,y,20,theme.accent,true);centered(c,(overviewLayout.page()+1)+" / "+overviewLayout.pageCount(),x,y,14,theme.textPrimary,true);centered(c,"›",x+42,y,20,theme.accent,true);}
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
        surface.navigationButton(c,theme,resources,paint,icons?null:label,x,y,w,h,on,theme.accent);
        if(icons)menuIcons.draw(c,id,x+w/2,y+h/2,on?theme.accent:theme.textSecondary);
    }
    private void centered(Canvas c,String s,float x,float y,float size,int color,boolean bold){
        size=theme.textSize(s,size,bold);
        paint.setTextSize(size);paint.setTypeface(resources==null?(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT):resources.fontForText(s,bold));
        rawText(c,s,x-paint.measureText(s)/2,y,size,color,bold);
    }
}
