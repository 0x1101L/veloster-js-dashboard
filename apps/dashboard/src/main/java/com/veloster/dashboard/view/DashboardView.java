package com.veloster.dashboard.view;

import com.veloster.dashboard.vehicle.*;
import com.veloster.dashboard.DashboardActivity;
import com.veloster.dashboard.theme.ThemeCatalog;
import com.veloster.dashboard.theme.ThemeRepository;
import com.veloster.dashboard.theme.ThemeSettingsStore;
import com.veloster.dashboard.theme.ThemeSpec;
import com.veloster.dashboard.theme.ThemePreviewController;
import com.veloster.dashboard.vehicle.js.JsTransmissionSetting;
import com.veloster.dashboard.debugger.JsDiagState;
import com.veloster.dashboard.debugger.JsLogger;
import com.veloster.dashboard.debugger.CarPlayRawLog;
import com.veloster.dashboard.debugger.BluetoothRuntimeProbe;

import com.veloster.dashboard.view.overview.OverviewLayout;
import com.veloster.dashboard.view.gauge.GaugeEditor;
import com.veloster.dashboard.view.gauge.GaugeLayout;
import com.veloster.dashboard.view.graph.GraphEditor;
import com.veloster.dashboard.view.graph.GraphLayout;

import com.veloster.i18n.UiText;
import com.veloster.i18n.LanguageSettings;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.lang.ref.WeakReference;

/** Opt-in in-activity panel; keeps the original content and all OEM controls alive. */
public final class DashboardView extends View implements Runnable {
    private static final Telemetry DATA=new Telemetry();
    private static final JsDiagState DIAG=new JsDiagState();
    private static WeakReference<DashboardView> current=new WeakReference<DashboardView>(null);
    public static void carPlayRawStatus(String status){DashboardView view=current.get();if(view!=null){view.jsRenderer.setCarPlayRawStatus(status);view.invalidate();}}
    public static void carPlayRuntimeStatus(final String library,final String process){final DashboardView view=current.get();if(view!=null)view.post(new Runnable(){public void run(){if(current.get()!=view)return;view.jsRenderer.setCarPlayRuntimeStatus(library,process);view.invalidate();}});}
    public static void bluetoothStatus(BluetoothRuntimeProbe.Snapshot status){DashboardView view=current.get();if(view!=null){view.jsRenderer.setBluetoothStatus(status);view.invalidate();}}
    private final Activity activity;
    private final JsTransmissionSetting transmissionSetting;
    private final PitRenderer renderer=new PitRenderer();
    private final JsPitRenderer jsRenderer=new JsPitRenderer();
    private final MediaState mediaState=new MediaState();
    private final MediaReceiver mediaReceiver=new MediaReceiver(mediaState);
    private final ArtworkLoader artwork;
    private final GaugeLayout gaugeLayout=new GaugeLayout();
    private final GaugeEditor gaugeEditor;
    private final GraphLayout graphLayout=new GraphLayout();
    private final GraphEditor graphEditor;
    private final OverviewLayout overviewLayout=new OverviewLayout();
    private boolean settingsVisible;
    private final ThemeCatalog themeCatalog;
    private final ThemeSettingsStore themeStore;
    private ThemeSpec selectedTheme,activeTheme;
    private int menuPosition,menuDock,pedalBarStyle,brakeBarStyle,f1Order;
    private int previewThemeIndex;
    private final SettingsPageRenderer.Editor settingsEditor=new SettingsPageRenderer.Editor();
    private boolean settingsListTouch,settingsListDragging;
    private int settingsListScreen;
    private float settingsListLastY;
    private boolean selectionEditor,selectionOriginSettings;
    private int editingGaugeSlot=-1,editingGraphSlot=-1;
    private int[] gaugeDraftSignals,gaugeDraftStyles;
    private int graphDraftSignal,transmissionDraft;
    private com.veloster.dashboard.theme.ThemeResources activeThemeResources;
    private int overviewDown=-1;
    private boolean media, touching;
    private int mediaRegionDown=MediaBar.NONE,mediaOverlayDown=MediaBar.NONE;
    private boolean sideMediaTouch,mediaOverlayTouch;
    private final DashboardPage.State pageState=new DashboardPage.State();
    private float downX,downY;
    private float shellDownX,shellDownY;
    private int shellDown=-1;
    private long lastDiagnostic;
    private long lastPolicyCheck;
    private boolean resumed;
    private long entryRequestUntil;
    private int shiftTarget;
    private DashboardPage.Menu pageMenu=new DashboardPage.Menu();
    private static final String MEDIA_BAR_POLICY="f1_media_bar_policy",MEDIA_NOTICES="media_notices";
    private int mediaBarPolicy=MediaBar.DEFAULT;
    private boolean mediaNotices=true;
    private final java.util.concurrent.atomic.AtomicBoolean mediaPending=new java.util.concurrent.atomic.AtomicBoolean();
    private Button entry;
    private boolean blocked;
    private boolean standalone;
    private final Runnable editHold=new Runnable(){public void run(){
        if(!settingsVisible&&(pageState.is(DashboardPage.GAUGES)||pageState.is(DashboardPage.GRAPHS))&&touching&&!blocked&&getVisibility()==VISIBLE){
            int slot=pageState.is(DashboardPage.GRAPHS)?graphLayout.hit(downX,downY):gaugeEditor.hit(downX,downY);
            if(slot>=0){touching=false;if(pageState.is(DashboardPage.GRAPHS))beginSelectionEditor(SettingsPageRenderer.GRAPH_SIGNAL,slot);else beginSelectionEditor(SettingsPageRenderer.GAUGE_MENU,slot);}
        }
    }};
    private DashboardView(Activity a) { super(a); activity=a; themeCatalog=ThemeCatalog.load(a);themeStore=new ThemeSettingsStore(a);menuPosition=themeStore.menuPosition();menuDock=themeStore.menuDock();pedalBarStyle=themeStore.pedalBarStyle();brakeBarStyle=themeStore.brakeBarStyle();f1Order=themeStore.f1Order();applyTheme(new ThemeRepository(themeCatalog).load(themeStore.selectedId(),ThemeRepository.SUPPORTED_SCHEMA_VERSION)); transmissionSetting=new JsTransmissionSetting(a);DATA.jsTransmissionSetting(transmissionSetting.get()); artwork=new ArtworkLoader(a,this);
        gaugeEditor=new GaugeEditor(a,gaugeLayout,new Runnable(){public void run(){invalidate();}});
        graphEditor=new GraphEditor(a,graphLayout,new Runnable(){public void run(){invalidate();}});
        for(int i=0;i<Telemetry.LABELS.length;i++)try{
            overviewLayout.style(i,a.getSharedPreferences("pit_vehicle_ui",0).getInt("overview_style_"+i,2));
        }catch(IllegalArgumentException e){/* Retain default for an invalid saved style. */}
        catch(ClassCastException e){/* Retain default for incompatible preferences. */}
        try{pageState.restore(a.getSharedPreferences("pit_vehicle_ui",0).getInt("dashboard_page",DashboardPage.OVERVIEW));}catch(ClassCastException ignored){pageState.restore(DashboardPage.OVERVIEW);}
        try{shiftTarget=ShiftGuide.clamp(a.getSharedPreferences("pit_vehicle_ui",0).getInt("shift_target",ShiftGuide.DEFAULT),DATA.pdLayoutProfile());}catch(ClassCastException ignored){shiftTarget=ShiftGuide.DEFAULT;}
        try{mediaBarPolicy=MediaBar.normalize(a.getSharedPreferences("pit_vehicle_ui",0).getInt(MEDIA_BAR_POLICY,MediaBar.DEFAULT));}catch(ClassCastException ignored){}
        try{mediaNotices=a.getSharedPreferences("pit_vehicle_ui",0).getInt(MEDIA_NOTICES,1)!=0;}catch(ClassCastException ignored){}
        try{pageMenu=new DashboardPage.Menu(a.getSharedPreferences("pit_vehicle_ui",0).getInt("page_menu_visible_mask",DashboardPage.Menu.ALL));}catch(ClassCastException ignored){}
        pageMenu.icons(themeStore.menuIcons());
        settingsRoot();setVisibility(GONE); }
    public static void attach(Activity a) {
        try {
            FrameLayout parent=(FrameLayout)a.findViewById(android.R.id.content);
            DashboardView old=current.get();
            if(old!=null && old.activity==a) return;
            final DashboardView dash=new DashboardView(a);
            parent.addView(dash,new FrameLayout.LayoutParams(-1,-1));
            Button button=new Button(a); dash.entry=button; button.setText(UiText.t("N 계기판"));
            FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-2,48,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
            parent.addView(button,lp);
            button.setOnClickListener(new OnClickListener() { public void onClick(View v) {
                if(!dash.blocked) dash.showDashboard();
            }});
            current=new WeakReference<DashboardView>(dash);
            android.util.Log.i("VehicleDash","attached N/PIT vehicle panel");
        } catch(RuntimeException e) { android.util.Log.e("VehicleDash","attach failed",e); }
    }
    /** Independent APK entry: no OEM view or disappearing entry button is involved. */
    public static void attachStandalone(Activity a){
        FrameLayout parent=(FrameLayout)a.findViewById(android.R.id.content);
        DashboardView dash=new DashboardView(a);dash.standalone=true;dash.blocked=false;
        current=new WeakReference<DashboardView>(dash);
        parent.addView(dash,new FrameLayout.LayoutParams(-1,-1));dash.setVisibility(VISIBLE);
    }
    public static void resumeStandalone(Activity a){
        DashboardView d=current.get();if(d==null||d.activity!=a)return;
        d.resumed=true;d.artwork.open();d.mediaReceiver.start(a);
        d.policy(false,"standalone-resume");d.setVisibility(VISIBLE);
    }
    public static void pauseStandalone(Activity a){
        DashboardView d=current.get();if(d==null||d.activity!=a)return;
        d.resumed=false;d.touching=false;d.removeCallbacks(d.editHold);
        d.gaugeEditor.dismiss();d.graphEditor.dismiss();if(!DATA.jsProfile())d.mediaReceiver.stop();d.artwork.close();
    }
    public static void stopStandaloneMedia(Activity a){DashboardView d=current.get();if(d!=null&&d.activity==a)d.mediaReceiver.stop();}
    private void showDashboard(){setVisibility(VISIBLE);if(entry!=null)entry.setVisibility(GONE);bringToFront();}
    private void close() { entryRequestUntil=0;removeCallbacks(editHold);touching=false;media=false;settingsVisible=false;selectionEditor=false;gaugeEditor.dismiss();graphEditor.dismiss();setVisibility(GONE); if(entry!=null) entry.setVisibility(blocked?GONE:VISIBLE); }
    public static void consumeEntryIntent(Activity a){entryIntent(a,a.getIntent());}
    /** Explicit opt-in only; consume once and require a live unrestricted OEM response. */
    public static void entryIntent(Activity a,android.content.Intent intent){
        if(intent==null)return;
        final String key="com.veloster.vehicle.OPEN_DASHBOARD";
        try{
            if(!intent.getBooleanExtra(key,false))return;
            intent.removeExtra(key);
            DashboardView d=current.get();
            if(d==null||d.activity!=a)return;
            d.entryRequestUntil=SystemClock.elapsedRealtime()+3000;
            d.tryRequestedEntry();
        }catch(RuntimeException e){android.util.Log.e("VehicleDash","invalid dashboard entry request",e);}
    }
    private void tryRequestedEntry(){
        if(entryRequestUntil==0||!resumed)return;
        int state=RegulationState.read(activity);
        if(state==1){entryRequestUntil=0;policy(true,"entry-service");Toast.makeText(activity,UiText.t("현재 차량 표시 제한으로 N 계기판을 열 수 없습니다."),Toast.LENGTH_SHORT).show();return;}
        if(SystemClock.elapsedRealtime()>=entryRequestUntil){entryRequestUntil=0;Toast.makeText(activity,UiText.t("차량 표시 상태를 확인하지 못했습니다. N 계기판 버튼으로 다시 열어주세요."),Toast.LENGTH_SHORT).show();return;}
        if(state==0){entryRequestUntil=0;policy(false,"entry-service");showDashboard();}
    }
    public static void hide(Activity a) {
        DashboardView d=current.get(); if(d!=null && d.activity==a) d.close();
    }
    public static void resume(Activity a,boolean cachedRestriction){
        attach(a);DashboardView d=current.get();
        if(d!=null&&d.activity==a){d.resumed=true;d.lastPolicyCheck=SystemClock.elapsedRealtime();regulation(a,cachedRestriction);d.tryRequestedEntry();android.util.Log.i("VehicleDash","entry resume cachedRestriction="+cachedRestriction);}
    }
    public static void pause(Activity a){DashboardView d=current.get();if(d!=null&&d.activity==a){d.resumed=false;d.close();}}
    private void policy(boolean restricted,String source){
        // The independent instrument display is not gated by the OEM parking-brake UI policy.
        if(standalone){blocked=false;setVisibility(VISIBLE);invalidate();return;}
        if(blocked!=restricted)android.util.Log.i("VehicleDash","OEM regulation="+restricted+" source="+source);
        blocked=restricted;
        if(blocked)close();
        else if(entry!=null){entry.setVisibility(getVisibility()==VISIBLE?GONE:VISIBLE);if(getVisibility()!=VISIBLE)entry.bringToFront();}
    }
    public static void regulation(final Activity a,final boolean blocked) {
        a.runOnUiThread(new Runnable(){public void run(){DashboardView d=current.get();if(d==null||d.activity!=a||d.standalone)return;
            int status=RegulationState.read(a);d.policy(status<0?blocked:status==1,status<0?"cached":"service");
        }});
    }
    public static void regulation(boolean blocked){
        DashboardView d=current.get();if(d!=null)regulation(d.activity,blocked);
    }
    public static void ignition(boolean on) { DATA.clear(); }
    public static void clearJsTirePressure() { DATA.clearJsTirePressure(); }
    public static void profile(boolean pd,boolean js,boolean jsn){DATA.profile(pd,js,jsn);}
    public interface DiagnosticEventSink { void event(String name,String detail); }
    private static volatile DiagnosticEventSink diagnosticEventSink;
    public static void setDiagnosticEventSink(DiagnosticEventSink sink){diagnosticEventSink=sink;}
    public static void diagReset(){DIAG.reset();}
    public static void diagEvent(String name,String detail){DIAG.event(name,detail,SystemClock.elapsedRealtime());DiagnosticEventSink sink=diagnosticEventSink;if(sink!=null&&("binding.media.3".equals(name)||name.startsWith("carplay.media.")))sink.event(name,detail);}
    public static void diagWifi(int feature,int service,int state,int watch){DIAG.wifi(feature,service,state,watch);}
    public static void diagHscan(int state){DIAG.hscan(state);}
    public static void diagLighting(int cid,int function,byte[] data,long receivedAt){
        if(cid!=0xc7||function!=0x01||data==null||data.length!=8)return;
        DIAG.lighting(data[3]&255,data[4]&255,receivedAt);
        final DashboardView view=current.get();if(view!=null)view.post(new Runnable(){public void run(){if(current.get()==view)view.invalidate();}});
    }
    public static void clearJsLighting(){
        DIAG.clearLighting();final DashboardView view=current.get();if(view!=null)view.post(new Runnable(){public void run(){if(current.get()==view)view.invalidate();}});
    }
    public static void rpm(byte[] data) { DATA.rpm(data,SystemClock.elapsedRealtime()); }
    public static void beginComputed(float rpm,float tqi,float acor,float standard,float boost,float ambient) { DATA.beginComputed(rpm,tqi,acor,standard,boost,ambient); }
    public static void computed(float power,float torque,float boostKpa) { DATA.computed(power,torque,boostKpa); }
    public static void calculateObserved(){DATA.calculateObserved(SystemClock.elapsedRealtime());}
    public static void temperatures(byte[] data) { DATA.temperatures(data,SystemClock.elapsedRealtime()); }
    public static void gforce(byte[] data) { DATA.gforce(data,SystemClock.elapsedRealtime()); }
    public static void wheels(byte[] data) { DATA.wheels(data,SystemClock.elapsedRealtime()); }
    public static void rearView(int data) { DATA.rearView(data); }
    public static void units(byte[] data) { DATA.units(data,SystemClock.elapsedRealtime()); }
    public static void speed(int data) { DATA.speed(data,SystemClock.elapsedRealtime()); }
    public static void additional(byte[] data) { DATA.additional(data,SystemClock.elapsedRealtime()); }
    public static void hscanJsn(int cid,int funcLow,byte[] data){DATA.hscanJsn(cid,funcLow,data,SystemClock.elapsedRealtime());}
    public static void hscanJsn(int cid,int funcLow,byte[] data,long receivedAt){DATA.hscanJsn(cid,funcLow,data,receivedAt);}
    public static void hscanPd(int cid,int function,byte[] data,long receivedAt){DATA.hscanPd(cid,function,data,receivedAt);}
    public static void hscanJs(int cid,int function,byte[] data,long receivedAt){DATA.hscanJs(cid,function,data,receivedAt);}
    public static void jsDctTemperatureReply(byte[] reply,long receivedAt){DATA.jsDctTemperatureReply(reply,receivedAt);}
    public static void jsTcu11Frame(int canId,byte[] frame,long receivedAt){DATA.jsTcu11Frame(canId,frame,receivedAt);}
    protected void onAttachedToWindow() { super.onAttachedToWindow(); artwork.open();mediaReceiver.start(activity); post(this); }
    protected void onDetachedFromWindow() { removeCallbacks(this);removeCallbacks(editHold);touching=false;gaugeEditor.dismiss();graphEditor.dismiss();mediaReceiver.stop();artwork.close();super.onDetachedFromWindow(); }
    public void run() {
        long delay=100;
        long tick=SystemClock.elapsedRealtime();
        if(resumed&&!standalone&&tick-lastPolicyCheck>=1000){lastPolicyCheck=tick;int status=RegulationState.read(activity);if(status>=0)policy(status==1,"service");}
        tryRequestedEntry();
        if(getVisibility()==VISIBLE) {
            invalidate();long now=SystemClock.elapsedRealtime();
            if(!media&&MediaBar.noticeVisible(displayRegions(),mediaBarPolicy,mediaNotices,mediaState.snapshot(now)))delay=16;
            else if(!settingsVisible&&pageState.is(DashboardPage.GRAPHS))delay=250;
            if(now-lastDiagnostic>=5000) { lastDiagnostic=now;android.util.Log.i("VehicleDash",DATA.diagnostics(now));if(DATA.jsProfile())JsLogger.emit(now,DATA,DIAG.snapshot(now)); }
        }
        postDelayed(this,delay);
    }
    protected void onDraw(Canvas c) {
        long now=SystemClock.elapsedRealtime();
        MediaState.Snapshot track=mediaState.snapshot(now);artwork.request(track);
        DashboardPage.Regions display=displayRegions();
        syncUiSettings(display);
        if(settingsVisible&&settingsEditor.screen==SettingsPageRenderer.WALLPAPER)
            loadWallpaperThumbnails(display,track);
        if(DATA.jsProfile()){
        jsRenderer.setSettingsEditor(settingsEditor);
        jsRenderer.setPageMenu(pageMenu);
        jsRenderer.setMediaDisplay(mediaBarPolicy,mediaNotices);
        jsRenderer.setPerformancePage(!settingsVisible&&pageState.is(DashboardPage.PERFORMANCE));
        jsRenderer.setSettingsPage(settingsVisible&&!diagnosticsOpen(),languageLabel(),activeTheme.displayName,menuPositionLabel());
        jsRenderer.setDiagnosticPage(diagnosticsOpen(),DIAG.snapshot(now));
        jsRenderer.setArtwork(artwork.bitmap());
        jsRenderer.setGaugeLayout(!settingsVisible&&pageState.is(DashboardPage.GAUGES)?gaugeLayout:null);
        jsRenderer.setGraphLayout(!settingsVisible&&pageState.is(DashboardPage.GRAPHS)?graphLayout:null);
        jsRenderer.setOverviewLayout(overviewLayout);
        jsRenderer.draw(c,getWidth(),getHeight(),!settingsVisible&&pageState.is(DashboardPage.F1),DATA,now,shiftTarget,media,track,display);
        }else{
        renderer.setSettingsEditor(settingsEditor);
        renderer.setPageMenu(pageMenu);
        renderer.setMediaDisplay(mediaBarPolicy,mediaNotices);
        renderer.setPerformancePage(!settingsVisible&&pageState.is(DashboardPage.PERFORMANCE));
        renderer.setSettingsPage(settingsVisible,languageLabel(),activeTheme.displayName,menuPositionLabel());
        renderer.setArtwork(artwork.bitmap());
        renderer.setGaugeLayout(!settingsVisible&&pageState.is(DashboardPage.GAUGES)?gaugeLayout:null);
        renderer.setGraphLayout(!settingsVisible&&pageState.is(DashboardPage.GRAPHS)?graphLayout:null);
        renderer.setOverviewLayout(overviewLayout);
        renderer.draw(c,getWidth(),getHeight(),!settingsVisible&&pageState.is(DashboardPage.F1),DATA,now,shiftTarget,media,track,display);
        }
    }
    private void requestMedia(final int command){
        if(blocked||!mediaPending.compareAndSet(false,true))return;
        final MediaState.Snapshot displayed=mediaState.snapshot(SystemClock.elapsedRealtime());
        final int displayedMode=MediaState.modeForSource(displayed.source);
        new Thread(new Runnable(){public void run(){
            String error=null;
            try{if(!(command==0?MediaControl.toggle(activity,displayedMode,displayed.playState):MediaControl.step(activity,command>0,displayedMode)))error=UiText.t("현재 재생 소스에서 음악을 제어할 수 없습니다");}
            catch(Exception e){android.util.Log.e("VehicleDash","Media control unavailable",e);error=UiText.t("음악 제어 요청을 보낼 수 없습니다");}
            final String message=error;
            activity.runOnUiThread(new Runnable(){public void run(){
                mediaPending.set(false);
                if(message!=null&&!activity.isFinishing())Toast.makeText(activity,message,Toast.LENGTH_SHORT).show();
            }});
        }},"NMode-track-control").start();
    }
    private DashboardPage.Regions displayRegions(){return DashboardPage.regions(getWidth(),getHeight(),activeTheme,media);}
    private DashboardPage.Content pageContent(DashboardPage.Regions display,boolean footer){
        return new DashboardPage.Content(activeTheme,viewportPage(),display,footer);
    }
    private int uiSettingsRowCount(DashboardPage.Regions display){
        return 5+(activeTheme.shell().left()?1:0)+(display.mediaEnabled?0:2)+(activeTheme.usesLiquidGlass()?1:0);
    }
    private void syncUiSettings(DashboardPage.Regions display){
        if(display.mediaEnabled)media=false;
        if(!settingsVisible)return;
        int screen=settingsEditor.screen;
        boolean mediaEditor=screen==SettingsPageRenderer.MEDIA_BAR||screen==SettingsPageRenderer.MEDIA_NOTICES;
        boolean changedGroup=screen==SettingsPageRenderer.UI_GROUP&&settingsEditor.actions.length!=uiSettingsRowCount(display);
        if(changedGroup||display.mediaEnabled&&mediaEditor){
            openSettingsPage(SettingsPageRenderer.UI_GROUP);
        }
    }
    private static String noticeLabel(boolean enabled){return UiText.t(enabled?"사용함":"사용 안 함");}
    private static final int[] GLASS_TRANSPARENCY_CHOICES={1,4,0,3,2},GLASS_BLUR_CHOICES={3,0,1,2,4};
    private static int glassChoiceIndex(int[] choices,int value){for(int i=0;i<choices.length;i++)if(choices[i]==value)return i;return 0;}
    private static String[] glassTransparencyLabels(){return tr("투명도 감소","덜 투명하게","표준","더 투명하게","매우 투명하게");}
    private static String[] glassBlurLabels(){return tr("사용 안 함","낮음","표준","높음","매우 높음");}
    private void applyTheme(ThemeSpec theme){
        selectedTheme=theme==null?ThemeSpec.original():theme;
        activeTheme=ThemeCatalog.withMenuPosition(selectedTheme,menuPosition).withMenuDock(menuDock)
                .withDisplayOptions(pedalBarStyle,brakeBarStyle,f1Order)
                .withLiquidGlassOptions(themeStore.liquidGlassTransparency(),themeStore.liquidGlassBlur(),themeStore.liquidGlassHighlight());
        ThemeSpec.Wallpaper selected=null;
        String selectedId=themeStore.selectedWallpaperId();
        if(selectedId.length()>0){
            selected=themeCatalog.findWallpaper(selectedId);
            if(selected==null)themeStore.saveWallpaperId("");
        }
        activeThemeResources=com.veloster.dashboard.theme.ThemeResources.load(activity,activeTheme,selected);
        overviewLayout.theme(activeTheme);renderer.setTheme(activeTheme,activeThemeResources);jsRenderer.setTheme(activeTheme,activeThemeResources);invalidate();
    }
    private String languageLabel(){String selected=LanguageSettings.selection(activity);return "auto".equals(selected)?UiText.t("시스템 설정 따름"):"ko".equals(selected)?"한국어":"English";}
    private static String[] tr(String... values){String[] result=values.clone();for(int i=0;i<result.length;i++)result[i]=UiText.t(result[i]);return result;}
    private String barLabel(int style){return UiText.t(style==1?"분할 막대":"연속 막대");}
    private String orderLabel(int value){
        String label="";for(int i=0;i<3;i++){String id=DashboardPage.f1OrderNode(value,i);if(i>0)label+=" · ";label+=UiText.t("speed".equals(id)?"속도":"gear".equals(id)?"기어":"RPM");}return label;
    }
    private void settingsRoot(){settingsEditor.root(languageLabel(),DATA.jsProfile());}
    private boolean diagnosticsOpen(){return settingsVisible&&DATA.jsProfile()&&settingsEditor.screen==SettingsPageRenderer.DIAGNOSTICS;}
    private void openSettingsPage(int screen){
        int ui=SettingsPageRenderer.UI_GROUP,f1=SettingsPageRenderer.F1_GROUP;
        String title="";String[] labels=new String[0],values=new String[0];int[] actions=new int[0];int selected=0,parent=ui;
        switch(screen){
        case SettingsPageRenderer.DIAGNOSTICS:
            if(!DATA.jsProfile())return;
            title=UiText.t("진단");parent=-1;break;
        case SettingsPageRenderer.UI_GROUP:{
            title=UiText.t("UI 설정");
            DashboardPage.Regions display=displayRegions();
            int count=uiSettingsRowCount(display);
            labels=java.util.Arrays.copyOf(tr("테마","배경화면","메뉴 위치"),count);
            values=java.util.Arrays.copyOf(new String[]{activeTheme.displayName,
                UiText.t(themeStore.selectedWallpaperId().length()==0?"테마 기본값":"사용자 선택"),
                menuPositionLabel()},count);
            actions=java.util.Arrays.copyOf(new int[]{SettingsPageRenderer.THEME,SettingsPageRenderer.WALLPAPER,SettingsPageRenderer.MENU_POSITION},count);
            int row=3;
            if(activeTheme.shell().left()){
                labels[row]=UiText.t("왼쪽 메뉴 독");values[row]=menuDockLabel();actions[row++]=SettingsPageRenderer.MENU_DOCK;
            }
            if(activeTheme.usesLiquidGlass()){
                labels[row]=UiText.t("Liquid Glass");values[row]=UiText.t("효과 설정");actions[row++]=SettingsPageRenderer.LIQUID_GLASS;
            }
            labels[row]=UiText.t("페이지 메뉴");values[row]=pageMenu.count()+" / 5";actions[row++]=SettingsPageRenderer.PAGE_MENU;
            labels[row]=UiText.t("메뉴 표시 방식");values[row]=UiText.t(pageMenu.icons()?"아이콘":"이름");actions[row++]=SettingsPageRenderer.MENU_STYLE;
            if(!display.mediaEnabled){
                labels[row]=UiText.t("하단 미디어 바");values[row]=MediaBar.label(mediaBarPolicy);actions[row++]=SettingsPageRenderer.MEDIA_BAR;
                labels[row]=UiText.t("미디어 알림");values[row]=noticeLabel(mediaNotices);actions[row++]=SettingsPageRenderer.MEDIA_NOTICES;
            }
            parent=-1;break;
        }
        case SettingsPageRenderer.F1_GROUP:
            title=UiText.t("F1 설정");labels=tr("가속 페달 바","브레이크 바","속도 · RPM · 기어 위치","F1 목표 RPM");
            values=new String[]{barLabel(pedalBarStyle),barLabel(brakeBarStyle),orderLabel(f1Order),shiftTarget+" rpm"};
            actions=new int[]{SettingsPageRenderer.PEDAL_BAR,SettingsPageRenderer.BRAKE_BAR,SettingsPageRenderer.F1_ORDER,SettingsPageRenderer.SHIFT_TARGET};parent=-1;break;
        case SettingsPageRenderer.LANGUAGE:
            title=UiText.t("언어");labels=new String[]{UiText.t("시스템 설정 따름"),"한국어","English"};
            String lang=LanguageSettings.selection(activity);selected="ko".equals(lang)?1:"en".equals(lang)?2:0;parent=-1;break;
        case SettingsPageRenderer.THEME:
            title=UiText.t("테마");ThemeSpec[] themes=themeCatalog.all().values().toArray(new ThemeSpec[0]);labels=new String[themes.length];
            for(int i=0;i<themes.length;i++){labels[i]=themes[i].displayName;if(themes[i].id.equals(selectedTheme.id))selected=i;}break;
        case SettingsPageRenderer.MENU_POSITION:
            title=UiText.t("메뉴 위치");labels=tr("테마 기본값","상단","왼쪽");selected=menuPosition;break;
        case SettingsPageRenderer.MENU_DOCK:
            if(!activeTheme.shell().left())return;
            title=UiText.t("왼쪽 메뉴 독");labels=tr("테마 기본값","사용함","사용 안 함");selected=menuDock;break;
        case SettingsPageRenderer.LIQUID_GLASS:
            if(!activeTheme.usesLiquidGlass())return;
            title=UiText.t("Liquid Glass");labels=tr("투명도","블러 강도","하이라이트");
            values=new String[]{glassTransparencyLabels()[glassChoiceIndex(GLASS_TRANSPARENCY_CHOICES,themeStore.liquidGlassTransparency())],
                glassBlurLabels()[glassChoiceIndex(GLASS_BLUR_CHOICES,themeStore.liquidGlassBlur())],
                UiText.t(new String[]{"은은하게","표준","강하게"}[themeStore.liquidGlassHighlight()])};
            actions=new int[]{SettingsPageRenderer.LIQUID_GLASS_TRANSPARENCY,SettingsPageRenderer.LIQUID_GLASS_BLUR,SettingsPageRenderer.LIQUID_GLASS_HIGHLIGHT};break;
        case SettingsPageRenderer.LIQUID_GLASS_TRANSPARENCY:
            title=UiText.t("투명도");labels=glassTransparencyLabels();selected=glassChoiceIndex(GLASS_TRANSPARENCY_CHOICES,themeStore.liquidGlassTransparency());parent=SettingsPageRenderer.LIQUID_GLASS;break;
        case SettingsPageRenderer.LIQUID_GLASS_BLUR:
            title=UiText.t("블러 강도");labels=glassBlurLabels();selected=glassChoiceIndex(GLASS_BLUR_CHOICES,themeStore.liquidGlassBlur());parent=SettingsPageRenderer.LIQUID_GLASS;break;
        case SettingsPageRenderer.LIQUID_GLASS_HIGHLIGHT:
            title=UiText.t("하이라이트");labels=tr("은은하게","표준","강하게");selected=themeStore.liquidGlassHighlight();parent=SettingsPageRenderer.LIQUID_GLASS;break;
        case SettingsPageRenderer.WALLPAPER:{
            java.util.List<ThemeSpec.Wallpaper> wallpapers=themeCatalog.wallpapers();
            selected=-1;
            String selectedId=themeStore.selectedWallpaperId();
            if(selectedId.length()==0)selectedId=selectedTheme.defaultWallpaperId();
            ThemeSpec.Wallpaper[] items=wallpapers.toArray(new ThemeSpec.Wallpaper[0]);
            for(int i=0;i<items.length;i++)if(items[i].id.equals(selectedId)){selected=i;break;}
            settingsEditor.wallpaperPage(items,selected);
            loadWallpaperThumbnails(displayRegions(),mediaState.snapshot(SystemClock.elapsedRealtime()));
            invalidate();return;
        }
        case SettingsPageRenderer.MENU_STYLE:
            title=UiText.t("메뉴 표시 방식");labels=tr("이름","아이콘");selected=pageMenu.icons()?1:0;break;
        case SettingsPageRenderer.PAGE_MENU:
            title=UiText.t("페이지 메뉴");labels=new String[]{UiText.t("퍼포먼스"),UiText.t("전체 조망"),"F1",UiText.t("게이지"),UiText.t("그래프")};break;
        case SettingsPageRenderer.PEDAL_BAR:case SettingsPageRenderer.BRAKE_BAR:
            title=UiText.t(screen==SettingsPageRenderer.PEDAL_BAR?"가속 페달 바":"브레이크 바");labels=tr("연속 막대","분할 막대");
            selected=screen==SettingsPageRenderer.PEDAL_BAR?pedalBarStyle:brakeBarStyle;parent=f1;break;
        case SettingsPageRenderer.F1_ORDER:
            title=UiText.t("속도 · RPM · 기어 위치");labels=new String[6];for(int i=0;i<6;i++)labels[i]=orderLabel(i);selected=f1Order;parent=f1;break;
        case SettingsPageRenderer.MEDIA_BAR:
            title=UiText.t("하단 미디어 바");labels=new String[]{MediaBar.label(0),MediaBar.label(1),MediaBar.label(2)};selected=mediaBarPolicy;break;
        case SettingsPageRenderer.MEDIA_NOTICES:
            title=UiText.t("미디어 알림");labels=new String[]{noticeLabel(true),noticeLabel(false)};selected=mediaNotices?0:1;break;
        case SettingsPageRenderer.SHIFT_TARGET:
            title=UiText.t("F1 목표 RPM");int min=ShiftGuide.clamp(0,DATA.pdLayoutProfile()),max=ShiftGuide.clamp(Integer.MAX_VALUE,DATA.pdLayoutProfile());
            labels=new String[(max-min)/100+1];for(int i=0;i<labels.length;i++)labels[i]=(min+i*100)+" rpm";selected=(shiftTarget-min)/100;parent=f1;break;
        }
        settingsEditor.page(screen,parent,title,labels,values,actions);settingsEditor.selected=selected;
        if(screen==SettingsPageRenderer.PAGE_MENU){settingsEditor.checked=new boolean[5];for(int i=0;i<5;i++)settingsEditor.checked[i]=pageMenu.visible(DashboardPage.menuPageAt(i));}
        invalidate();
    }
    private void settingsBack(){
        if(selectionEditor){
            int parent=settingsEditor.parent;
            if(settingsEditor.screen==SettingsPageRenderer.GAUGE_MENU||settingsEditor.screen==SettingsPageRenderer.GRAPH_SIGNAL||settingsEditor.screen==SettingsPageRenderer.TRANSMISSION){cancelSelectionEditor();return;}
            if(parent>=SettingsPageRenderer.GAUGE_MENU&&parent<=SettingsPageRenderer.TRANSMISSION){openSelectionPage(parent);return;}
        }
        int screen=settingsEditor.screen,parent=settingsEditor.parent;if(parent<0)settingsRoot();else openSettingsPage(parent);if(screen==11)settingsEditor.selected=previewThemeIndex;invalidate();
    }
    private void beginSelectionEditor(int screen,int slot){
        selectionOriginSettings=settingsVisible;selectionEditor=true;settingsVisible=true;editingGaugeSlot=screen==SettingsPageRenderer.GAUGE_MENU?slot:-1;editingGraphSlot=screen==SettingsPageRenderer.GRAPH_SIGNAL?slot:-1;
        if(screen==SettingsPageRenderer.GAUGE_MENU){gaugeDraftSignals=new int[GaugeLayout.SLOTS];gaugeDraftStyles=new int[GaugeLayout.SLOTS];for(int i=0;i<GaugeLayout.SLOTS;i++){gaugeDraftSignals[i]=gaugeLayout.signal(i);gaugeDraftStyles[i]=gaugeLayout.style(i);}}
        if(screen==SettingsPageRenderer.GRAPH_SIGNAL)graphDraftSignal=graphLayout.signal(slot);
        if(screen==SettingsPageRenderer.TRANSMISSION)transmissionDraft=transmissionSetting.get();
        openSelectionPage(screen);invalidate();
    }
    private void openSelectionPage(int screen){
        String title="";String[] labels=new String[0],values=new String[0];int[] actions=new int[0];int selected=0,parent=-1;
        switch(screen){
        case SettingsPageRenderer.GAUGE_MENU:
            title=UiText.t("게이지 ")+(editingGaugeSlot+1);labels=tr("표시 항목","게이지 모양","다른 칸과 위치 바꾸기","전체 기본 배치로 복원");
            values=new String[]{UiText.t(Telemetry.LABELS[gaugeDraftSignals[editingGaugeSlot]]),tr("원형","막대","숫자")[gaugeDraftStyles[editingGaugeSlot]],"", ""};
            actions=new int[]{SettingsPageRenderer.GAUGE_SIGNAL,SettingsPageRenderer.GAUGE_STYLE,SettingsPageRenderer.GAUGE_SWAP,SettingsPageRenderer.GAUGE_RESET};break;
        case SettingsPageRenderer.GAUGE_SIGNAL:
            title=UiText.t("표시 항목");labels=UiText.list(Telemetry.LABELS);selected=gaugeDraftSignals[editingGaugeSlot];parent=SettingsPageRenderer.GAUGE_MENU;break;
        case SettingsPageRenderer.GAUGE_STYLE:
            title=UiText.t("게이지 모양");labels=tr("원형","막대","숫자");selected=gaugeDraftStyles[editingGaugeSlot];parent=SettingsPageRenderer.GAUGE_MENU;break;
        case SettingsPageRenderer.GAUGE_SWAP:
            title=UiText.t("위치를 바꿀 칸");labels=new String[gaugeLayout.count()];for(int i=0;i<labels.length;i++)labels[i]=(i+1)+UiText.t("번 · ")+UiText.t(Telemetry.LABELS[gaugeDraftSignals[i]]);selected=editingGaugeSlot;parent=SettingsPageRenderer.GAUGE_MENU;break;
        case SettingsPageRenderer.GAUGE_RESET:
            title=UiText.t("기본 배치로 복원");labels=new String[]{UiText.t("모든 게이지의 항목과 모양을 초기화합니다.")};parent=SettingsPageRenderer.GAUGE_MENU;break;
        case SettingsPageRenderer.GRAPH_SIGNAL:
            title=UiText.t("그래프 ")+(editingGraphSlot+1)+UiText.t(" · 표시 항목");labels=UiText.list(Telemetry.LABELS);selected=graphDraftSignal;break;
        case SettingsPageRenderer.TRANSMISSION:
            title=UiText.t("변속기 선택");labels=tr("DCT","수동");selected=transmissionDraft-1;break;
        }
        settingsEditor.page(screen,parent,title,labels,values,actions);settingsEditor.selected=Math.max(0,selected);
    }
    private void cancelSelectionEditor(){
        boolean transmission=settingsEditor.screen==SettingsPageRenderer.TRANSMISSION;
        selectionEditor=false;settingsVisible=selectionOriginSettings;
        if(settingsVisible){
            if(transmission)openSettingsPage(SettingsPageRenderer.DIAGNOSTICS);
            else settingsRoot();
        }
        invalidate();
    }
    private void commitSelectionEditor(){
        int screen=settingsEditor.screen,selected=settingsEditor.selected;
        if(screen==SettingsPageRenderer.GAUGE_SIGNAL)gaugeDraftSignals[editingGaugeSlot]=selected;
        else if(screen==SettingsPageRenderer.GAUGE_STYLE)gaugeDraftStyles[editingGaugeSlot]=selected;
        else if(screen==SettingsPageRenderer.GAUGE_SWAP){int n=gaugeDraftSignals[editingGaugeSlot];gaugeDraftSignals[editingGaugeSlot]=gaugeDraftSignals[selected];gaugeDraftSignals[selected]=n;n=gaugeDraftStyles[editingGaugeSlot];gaugeDraftStyles[editingGaugeSlot]=gaugeDraftStyles[selected];gaugeDraftStyles[selected]=n;}
        else if(screen==SettingsPageRenderer.GAUGE_RESET){gaugeLayout.reset();gaugeEditor.save();cancelSelectionEditor();return;}
        else if(screen==SettingsPageRenderer.GRAPH_SIGNAL){graphLayout.signal(editingGraphSlot,selected);graphEditor.save();cancelSelectionEditor();return;}
        else if(screen==SettingsPageRenderer.TRANSMISSION){transmissionDraft=selected+1;transmissionSetting.save(transmissionDraft);DATA.jsTransmissionSetting(transmissionDraft);cancelSelectionEditor();return;}
        else if(screen==SettingsPageRenderer.GAUGE_MENU){for(int i=0;i<GaugeLayout.SLOTS;i++)gaugeLayout.set(i,gaugeDraftSignals[i],gaugeDraftStyles[i]);gaugeEditor.save();cancelSelectionEditor();return;}
        if(screen==SettingsPageRenderer.GAUGE_SIGNAL||screen==SettingsPageRenderer.GAUGE_STYLE||screen==SettingsPageRenderer.GAUGE_SWAP){
            for(int i=0;i<GaugeLayout.SLOTS;i++)gaugeLayout.set(i,gaugeDraftSignals[i],gaugeDraftStyles[i]);
            gaugeEditor.save();cancelSelectionEditor();return;
        }
        openSelectionPage(settingsEditor.parent);invalidate();
    }
    private void loadWallpaperThumbnails(DashboardPage.Regions display,MediaState.Snapshot track){
        DashboardPage.Content content=pageContent(display,MediaBar.footerVisible(display,mediaBarPolicy,track));
        SettingsPageRenderer.Layout layout=new SettingsPageRenderer.Layout(activeTheme,content,settingsEditor);
        ThemeSpec.Wallpaper[] items=settingsEditor.wallpapers;
        int first=layout.offset,end=first+layout.count;
        for(int i=0;i<items.length;i++){
            if(i>=first&&i<end){
                if(items[i].color==null&&!settingsEditor.wallpaperThumbnailTried[i]){
                    settingsEditor.wallpaperThumbnailTried[i]=true;
                    settingsEditor.wallpaperThumbnails[i]=com.veloster.dashboard.theme.ThemeResources.loadThumbnail(activity,items[i]);
                }
            }else if(settingsEditor.wallpaperThumbnails[i]!=null){
                settingsEditor.wallpaperThumbnails[i]=null;settingsEditor.wallpaperThumbnailTried[i]=false;
            }
        }
    }
    private void settingsTap(int item,float x,float y,SettingsPageRenderer.Layout layout){
        if(item==-2){if(selectionEditor)cancelSelectionEditor();else if(settingsEditor.screen<0)settingsVisible=false;else settingsBack();invalidate();return;}
        if(item==-7){layout.tapPreview(settingsEditor,x,y);invalidate();return;}
        if(settingsEditor.screen==SettingsPageRenderer.WALLPAPER){
            if(item==-8){
                themeStore.saveWallpaperId("");
                applyTheme(selectedTheme);
                openSettingsPage(SettingsPageRenderer.WALLPAPER);
            }else if(item>=0&&item<settingsEditor.wallpapers.length){
                themeStore.saveWallpaperId(settingsEditor.wallpapers[item].id);
                applyTheme(selectedTheme);
                settingsEditor.selected=item;
                settingsEditor.scrollY=layout.scrollY;
            }
            return;
        }
        if(item==-6){
            previewThemeIndex=settingsEditor.selected;
            ThemeSpec choice=themeCatalog.all().values().toArray(new ThemeSpec[0])[previewThemeIndex];
            final ThemePreviewController preview=new ThemePreviewController(activity,DATA.pdProfile(),DATA.jsProfile(),DATA.jsnProfile(),menuPosition,menuDock);
            preview.setDraft(choice.withDisplayOptions(pedalBarStyle,brakeBarStyle,f1Order)
                    .withLiquidGlassOptions(themeStore.liquidGlassTransparency(),themeStore.liquidGlassBlur(),themeStore.liquidGlassHighlight()));
            preview.setMediaBarPolicy(mediaBarPolicy);
            settingsEditor.page(11,SettingsPageRenderer.THEME,UiText.t("테마 미리보기"),new String[0],new String[0],new int[0]);
            settingsEditor.preview=new SettingsPageRenderer.Editor.Preview(){
                public void draw(Canvas canvas,int width,int height){preview.draw(canvas,width,height);}
                public void tap(float x,float y,int width,int height){
                    DashboardPage.Regions r=DashboardPage.regions(width,height,preview.draft());
                    if(!r.content.contains(x,y))return;
                    x/=r.scale;y/=r.scale;
                    int target=preview.layout().pageControlTarget(x,y);if(target>=0)preview.page(target);
                }
            };
            invalidate();return;
        }
        if(item==-3){if(selectionEditor)commitSelectionEditor();else applySettingsPage();return;}
        if(item<0)return;
        if(settingsEditor.actions.length>item){if(selectionEditor)openSelectionPage(settingsEditor.actions[item]);else openSettingsPage(settingsEditor.actions[item]);return;}
        if(selectionEditor&&settingsEditor.actions.length>item){openSelectionPage(settingsEditor.actions[item]);return;}
        if(settingsEditor.checked!=null)settingsEditor.checked[item]=!settingsEditor.checked[item];else settingsEditor.selected=item;
        invalidate();
    }
    private void applySettingsPage(){
        int selected=settingsEditor.selected;
        switch(settingsEditor.screen){
        case SettingsPageRenderer.LANGUAGE:
            if(!LanguageSettings.save(activity,new String[]{"auto","ko","en"}[selected])){settingsEditor.message=UiText.t("언어 설정을 저장하지 못했습니다.");invalidate();return;}break;
        case SettingsPageRenderer.THEME:
            ThemeSpec choice=themeCatalog.all().values().toArray(new ThemeSpec[0])[selected];themeStore.save(choice.id);applyTheme(choice);break;
        case SettingsPageRenderer.MENU_POSITION:
            if(selected!=ThemeSpec.MENU_THEME&&ThemeCatalog.withMenuPosition(selectedTheme,selected).shell().left()!=(selected==ThemeSpec.MENU_LEFT)){
                settingsEditor.message=UiText.t("현재 테마에서 지원 안 함");invalidate();return;}
            menuPosition=selected;themeStore.saveMenuPosition(selected);applyTheme(selectedTheme);break;
        case SettingsPageRenderer.MENU_DOCK:
            menuDock=selected;themeStore.saveMenuDock(selected);applyTheme(selectedTheme);break;
        case SettingsPageRenderer.PAGE_MENU:
            for(int i=0;i<5;i++)pageMenu.visible(DashboardPage.menuPageAt(i),settingsEditor.checked[i]);
            activity.getSharedPreferences("pit_vehicle_ui",0).edit().putInt("page_menu_visible_mask",pageMenu.mask()).apply();break;
        case SettingsPageRenderer.MENU_STYLE:
            pageMenu.icons(selected==1);themeStore.saveMenuIcons(pageMenu.icons());break;
        case SettingsPageRenderer.PEDAL_BAR:pedalBarStyle=selected;saveDisplayOptions();break;
        case SettingsPageRenderer.BRAKE_BAR:brakeBarStyle=selected;saveDisplayOptions();break;
        case SettingsPageRenderer.F1_ORDER:f1Order=selected;saveDisplayOptions();break;
        case SettingsPageRenderer.LIQUID_GLASS_TRANSPARENCY:
            themeStore.saveLiquidGlassOptions(GLASS_TRANSPARENCY_CHOICES[selected],themeStore.liquidGlassBlur(),themeStore.liquidGlassHighlight());applyTheme(selectedTheme);break;
        case SettingsPageRenderer.LIQUID_GLASS_BLUR:
            themeStore.saveLiquidGlassOptions(themeStore.liquidGlassTransparency(),GLASS_BLUR_CHOICES[selected],themeStore.liquidGlassHighlight());applyTheme(selectedTheme);break;
        case SettingsPageRenderer.LIQUID_GLASS_HIGHLIGHT:
            themeStore.saveLiquidGlassOptions(themeStore.liquidGlassTransparency(),themeStore.liquidGlassBlur(),selected);applyTheme(selectedTheme);break;
        case SettingsPageRenderer.MEDIA_BAR:
            mediaBarPolicy=selected;activity.getSharedPreferences("pit_vehicle_ui",0).edit().putInt(MEDIA_BAR_POLICY,selected).apply();break;
        case SettingsPageRenderer.MEDIA_NOTICES:
            mediaNotices=selected==0;activity.getSharedPreferences("pit_vehicle_ui",0).edit().putInt(MEDIA_NOTICES,mediaNotices?1:0).apply();break;
        case SettingsPageRenderer.SHIFT_TARGET:
            shiftTarget=ShiftGuide.clamp(0,DATA.pdLayoutProfile())+selected*100;
            activity.getSharedPreferences("pit_vehicle_ui",0).edit().putInt("shift_target",shiftTarget).apply();break;
        }
        settingsBack();
    }
    private void saveDisplayOptions(){themeStore.saveDisplayOptions(pedalBarStyle,brakeBarStyle,f1Order);applyTheme(selectedTheme);}
    private void showShiftTargetPicker(){openSettingsPage(SettingsPageRenderer.SHIFT_TARGET);}
    private void showPageMenuPicker(){openSettingsPage(SettingsPageRenderer.PAGE_MENU);}
    private void showMenuPositionPicker(){openSettingsPage(SettingsPageRenderer.MENU_POSITION);}
    private void showThemePicker(){settingsVisible=true;openSettingsPage(SettingsPageRenderer.THEME);}
    private String menuPositionLabel(){
        int actual=activeTheme.shell().left()?ThemeSpec.MENU_LEFT:ThemeSpec.MENU_TOP;
        return menuPosition==actual?SettingsPageRenderer.menuPositionLabel(menuPosition):
            SettingsPageRenderer.menuPositionLabel(ThemeSpec.MENU_THEME)+" ("+SettingsPageRenderer.menuPositionLabel(actual)+")";
    }
    private String menuDockLabel(){
        String actual=noticeLabel(activeTheme.shell().dock());
        return menuDock==ThemeSpec.DOCK_THEME?UiText.t("테마 기본값")+" ("+actual+")":actual;
    }
    private String viewportPage(){return diagnosticsOpen()?"diagnostics":settingsVisible?"settings":pageState.is(DashboardPage.PERFORMANCE)?"performance":pageState.is(DashboardPage.GRAPHS)?"graphs":pageState.is(DashboardPage.GAUGES)?"gauges":pageState.is(DashboardPage.F1)?"f1":"overview";}
    private void selectMenuTarget(int target){
        media=false;
        gaugeEditor.dismiss();graphEditor.dismiss();
        if(target==DashboardPage.BACK){if(standalone)activity.finish();else close();}
        else if(target==DashboardPage.SETTINGS){
            if(!settingsVisible||diagnosticsOpen()||settingsEditor.screen==SettingsPageRenderer.TRANSMISSION){
                settingsVisible=true;settingsRoot();
            }else settingsVisible=false;
            selectionEditor=false;
            invalidate();
        }else{
            settingsVisible=false;selectionEditor=false;
            int page=pageState.restore(target);
            activity.getSharedPreferences("pit_vehicle_ui",0).edit().putInt("dashboard_page",page).apply();invalidate();
        }
    }
    private void handleSettingsListTouch(MotionEvent event,DashboardPage.Regions display) {
        int action=event.getAction();
        if(blocked||!settingsVisible||settingsEditor.screen!=settingsListScreen
                ||(action!=MotionEvent.ACTION_MOVE&&action!=MotionEvent.ACTION_UP)){
            settingsListTouch=touching=false;return;
        }
        float x=(event.getX()-display.left)/display.scale,y=(event.getY()-display.top)/display.scale;
        float dx=Math.abs(x-downX),dy=Math.abs(y-downY);
        if(action==MotionEvent.ACTION_MOVE){
            if(dx>=16||dy>=16)touching=false;
            if(dy>=16&&dy>=dx)settingsListDragging=true;
        }
        MediaState.Snapshot track=mediaState.snapshot(SystemClock.elapsedRealtime());
        DashboardPage.Content content=pageContent(display,MediaBar.footerVisible(display,mediaBarPolicy,track));
        SettingsPageRenderer.Layout layout=new SettingsPageRenderer.Layout(activeTheme,content,settingsEditor);
        if(settingsListDragging){
            if(layout.scrollBy(settingsEditor,settingsListLastY-y))invalidate();
            settingsListLastY=y;
        }else if(action==MotionEvent.ACTION_UP&&touching&&dx<16&&dy<16){
            int row=layout.hit(settingsEditor,x,y);
            if(row>=0&&row==layout.hit(settingsEditor,downX,downY))settingsTap(row,x,y,layout);
        }
        if(action==MotionEvent.ACTION_UP)settingsListTouch=touching=false;
    }
    @Override public boolean onGenericMotionEvent(MotionEvent event){
        if(event.getAction()==MotionEvent.ACTION_SCROLL&&settingsVisible&&!blocked&&!diagnosticsOpen()&&settingsEditor.preview==null){
            DashboardPage.Regions display=displayRegions();
            syncUiSettings(display);
            if(media)return true;
            MediaState.Snapshot track=mediaState.snapshot(SystemClock.elapsedRealtime());
            DashboardPage.Content content=pageContent(display,MediaBar.footerVisible(display,mediaBarPolicy,track));
            SettingsPageRenderer.Layout layout=new SettingsPageRenderer.Layout(activeTheme,content,settingsEditor);
            float x=(event.getX()-display.left)/display.scale,y=(event.getY()-display.top)/display.scale;
            if(layout.maxScroll>0&&layout.list.contains(x,y)){
                if(layout.scrollBy(settingsEditor,-event.getAxisValue(MotionEvent.AXIS_VSCROLL)*44))invalidate();
                return true;
            }
        }
        return super.onGenericMotionEvent(event);
    }
    public boolean onTouchEvent(MotionEvent event) {
        DashboardPage.Regions display=displayRegions();
        syncUiSettings(display);
        int action=event.getAction();
        if(action==MotionEvent.ACTION_DOWN)settingsListTouch=false;
        else if(settingsListTouch){handleSettingsListTouch(event,display);return true;}
        float px=event.getX(),py=event.getY();
        boolean inSide=display.mediaPanelVisible&&display.media.contains(px,py);
        if(action==MotionEvent.ACTION_DOWN){
            sideMediaTouch=inSide;
            mediaRegionDown=inSide?MediaBar.regionHit(activeTheme,display.media,px,py,display.mediaEnabled):MediaBar.NONE;
            if(inSide){
                downX=px;downY=py;touching=false;shellDown=-1;
                mediaOverlayTouch=false;mediaOverlayDown=MediaBar.NONE;
                removeCallbacks(editHold);return true;
            }
        }else if(sideMediaTouch){
            float slop=16*display.scale;
            boolean near=Math.abs(px-downX)<slop&&Math.abs(py-downY)<slop;
            if(action==MotionEvent.ACTION_MOVE&&!near)mediaRegionDown=MediaBar.NONE;
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
                int pressed=mediaRegionDown;
                sideMediaTouch=false;mediaRegionDown=MediaBar.NONE;
                if(action==MotionEvent.ACTION_UP&&near&&inSide&&!blocked&&pressed!=MediaBar.NONE
                        &&pressed==MediaBar.regionHit(activeTheme,display.media,px,py,display.mediaEnabled))requestMedia(pressed);
            }
            return true;
        }
        float x=(event.getX()-display.left)/display.scale,y=(event.getY()-display.top)/display.scale;
        MediaState.Snapshot track=mediaState.snapshot(SystemClock.elapsedRealtime());
        boolean footer=MediaBar.footerVisible(display,mediaBarPolicy,track);
        DashboardPage.Content content=pageContent(display,footer);
        boolean inFooter=footer&&content.footer(MediaBar.NONE).contains(x,y);
        boolean inNotice=!media&&MediaBar.noticeVisible(display,mediaBarPolicy,mediaNotices,track)&&content.contains(x,y)
                &&MediaBar.noticeBounds(content.notice(activeTheme),track.noticeAgeMs).contains(x,y);
        int mediaAction=inFooter?MediaBar.hit(content,x,y):MediaBar.NONE;
        if(action==MotionEvent.ACTION_DOWN){
            mediaOverlayTouch=inFooter||inNotice;
            mediaOverlayDown=mediaAction;
            if(mediaOverlayTouch){
                downX=x;downY=y;touching=false;shellDown=-1;removeCallbacks(editHold);return true;
            }
        }else if(mediaOverlayTouch){
            if(action==MotionEvent.ACTION_MOVE&&(Math.abs(x-downX)>=16||Math.abs(y-downY)>=16))mediaOverlayDown=MediaBar.NONE;
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
                int pressed=mediaOverlayDown;
                mediaOverlayTouch=false;mediaOverlayDown=MediaBar.NONE;
                if(action==MotionEvent.ACTION_UP&&!blocked&&pressed!=MediaBar.NONE&&pressed==mediaAction
                        &&Math.abs(x-downX)<16&&Math.abs(y-downY)<16){
                    if(pressed==MediaBar.TOGGLE){
                        media=pressed==MediaBar.TOGGLE&&!media;mediaState.dismissNotice();invalidate();
                    }
                    else requestMedia(pressed);
                }
            }
            return true;
        }else if(action==MotionEvent.ACTION_UP&&(inFooter||inNotice||inSide)){
            touching=false;shellDown=-1;removeCallbacks(editHold);return true;
        }
        if(activeTheme.shell().left()){
            if(event.getAction()==MotionEvent.ACTION_DOWN){shellDownX=x;shellDownY=y;shellDown=DashboardPage.shellHit(activeTheme,pageMenu,x,y);}
            if(event.getAction()==MotionEvent.ACTION_UP&&shellDown>=0){
                int target=DashboardPage.shellHit(activeTheme,pageMenu,x,y);
                boolean shellTap=touching&&target==shellDown&&Math.abs(x-shellDownX)<16&&Math.abs(y-shellDownY)<16;
                shellDown=-1;touching=false;removeCallbacks(editHold);
                if(shellTap&&!blocked)selectMenuTarget(target);
                return true;
            }
        }
        boolean inPage=content.contains(x,y);
        boolean diagnostics=diagnosticsOpen();
        if(!settingsVisible&&pageState.is(DashboardPage.GAUGES))gaugeLayout.viewport(activeTheme,content);
        if(!settingsVisible&&pageState.is(DashboardPage.GRAPHS))graphLayout.viewport(activeTheme,content);
        if(diagnostics)jsRenderer.diagnosticsViewport(content);
        overviewLayout.viewport(display.logicalContentWidth,content.bodyViewportHeight,display.mediaPanelVisible);
        if(event.getAction()==MotionEvent.ACTION_DOWN) {
            removeCallbacks(editHold);touching=true;downX=x;downY=y;
            if(settingsVisible&&!blocked&&!diagnostics&&settingsEditor.preview==null){
                SettingsPageRenderer.Layout layout=new SettingsPageRenderer.Layout(activeTheme,content,settingsEditor);
                if(layout.maxScroll>0&&layout.list.contains(x,y)){
                    settingsListTouch=true;settingsListDragging=false;settingsListLastY=y;
                    settingsListScreen=settingsEditor.screen;
                    settingsEditor.scrollY=layout.scrollY;
                    return true;
                }
            }
            overviewDown=!settingsVisible&&pageState.is(DashboardPage.OVERVIEW)&&!blocked&&inPage?overviewLayout.hit(x,y):-1;
            if(!settingsVisible&&!blocked&&((pageState.is(DashboardPage.GAUGES)&&gaugeEditor.hit(x,y)>=0)||(pageState.is(DashboardPage.GRAPHS)&&graphLayout.hit(x,y)>=0)))postDelayed(editHold,600);
            return true;
        }
        if(event.getAction()==MotionEvent.ACTION_MOVE){if(Math.abs(x-downX)>=16||Math.abs(y-downY)>=16){touching=false;removeCallbacks(editHold);}return true;}
        if(event.getAction()==MotionEvent.ACTION_CANCEL) { touching=false;removeCallbacks(editHold);return true; }
        if(event.getAction()!=MotionEvent.ACTION_UP)return true;
        removeCallbacks(editHold);
        boolean tap=touching && Math.abs(x-downX)<16 && Math.abs(y-downY)<16;
        touching=false;
        if(settingsVisible&&!diagnostics){
            if(!tap)return true;
            if(!blocked&&inPage){
                SettingsPageRenderer.Layout layout=new SettingsPageRenderer.Layout(activeTheme,content,settingsEditor);
                int item=layout.hit(settingsEditor,x,y);
                if(item==layout.hit(settingsEditor,downX,downY))settingsTap(item,x,y,layout);
            }
            if(activeTheme.shell().left()||event.getY()>=display.headerHeight)return true;
        }
        if(tap&&!blocked&&diagnostics&&inPage&&jsRenderer.selectDiagnosticTab(downX,downY,x,y)){invalidate();return true;}
        if(tap&&!blocked&&diagnostics&&inPage&&jsRenderer.carPlayImportHit(downX,downY)&&jsRenderer.carPlayImportHit(x,y)){
            try{Intent intent=new Intent(Intent.ACTION_GET_CONTENT);intent.setType("application/octet-stream");activity.startActivityForResult(Intent.createChooser(intent,UiText.t("CarPlay 캡처 선택")),DashboardActivity.REQUEST_CARPLAY_CAPTURE);}
            catch(RuntimeException e){Toast.makeText(activity,UiText.t("파일 선택기를 열 수 없습니다"),Toast.LENGTH_SHORT).show();}
            return true;
        }
        if(tap&&!blocked&&diagnostics&&inPage&&jsRenderer.transmissionHit(downX,downY)&&jsRenderer.transmissionHit(x,y)){
            beginSelectionEditor(SettingsPageRenderer.TRANSMISSION,-1);return true;
        }
        if(tap&&!blocked&&diagnostics&&inPage&&jsRenderer.wifiSettingsHit(downX,downY)&&jsRenderer.wifiSettingsHit(x,y)){
            try{activity.startActivity(new Intent("android.settings.WIFI_SETTINGS"));}
            catch(RuntimeException e){Toast.makeText(activity,UiText.t("Wi-Fi 설정을 열 수 없습니다"),Toast.LENGTH_SHORT).show();}
            return true;
        }
        if(tap&&!blocked&&!settingsVisible&&pageState.is(DashboardPage.OVERVIEW)&&overviewDown>=0&&overviewLayout.hit(x,y)==overviewDown&&inPage){
            if(OverviewLayout.dctCard(overviewDown,DATA.jsProfile()))return true;
            overviewLayout.cycle(overviewDown);
            activity.getSharedPreferences("pit_vehicle_ui",0).edit().putInt("overview_style_"+overviewDown,overviewLayout.style(overviewDown)).apply();
            invalidate();return true;
        }
        if(tap&&!blocked&&!settingsVisible&&pageState.is(DashboardPage.GAUGES)&&gaugeLayout.toggle().contains(x,y)&&gaugeLayout.toggle().contains(downX,downY)){gaugeEditor.toggleGrid();return true;}
        if(tap&&!blocked&&!settingsVisible&&pageState.is(DashboardPage.GRAPHS)){
            for(int i=0;i<2;i++)if(graphLayout.timeButton(i).contains(x,y)&&graphLayout.timeButton(i).contains(downX,downY)){
                graphEditor.window(i==0?60000:300000);return true;
            }
        }
        if(tap&&!blocked&&!activeTheme.shell().left()){
            int selected=DashboardPage.tap(activeTheme,pageMenu,downX,downY,x,y,display.logicalDisplayWidth);
            if(selected>=0)selectMenuTarget(selected);
        }
        return true;
    }
}
