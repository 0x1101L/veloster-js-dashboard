package com.veloster.dashboard;

import com.veloster.dashboard.vehicle.HscanDataClient;
import com.veloster.dashboard.vehicle.HscanProfile;
import com.veloster.dashboard.vehicle.VehicleDataClient;

import com.veloster.dashboard.debugger.JsHudProbe;
import com.veloster.dashboard.debugger.HudEventLog;
import com.veloster.dashboard.debugger.CarPlayScreenProbe;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import com.veloster.dashboard.view.DashboardView;
import com.veloster.i18n.LanguageSettings;

/** Own package, own task and own data subscriptions. Never starts the OEM NMode app. */
public final class DashboardActivity extends Activity implements DashboardView.ScreenProbeStarter {
    private VehicleDataClient client;
    private JsHudProbe hudProbe;
    private boolean wifiWatching,jsProfile;
    private android.os.HandlerThread hudLogThread;
    private volatile android.os.Handler hudLogHandler;
    private HudEventLog hudLog;
    private volatile int screenProbeGeneration;
    private final BroadcastReceiver wifiReceiver=new BroadcastReceiver(){
        public void onReceive(Context context,Intent intent){refreshWifi();}
    };
    protected void onCreate(Bundle saved){
        super.onCreate(saved);
        LanguageSettings.init(this);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(new FrameLayout(this));
        HscanProfile.Kind profile=HscanDataClient.profile();
        jsProfile=profile==HscanProfile.Kind.JS;
        DashboardView.profile(profile==HscanProfile.Kind.PD,jsProfile,profile==HscanProfile.Kind.JSN);
        if(jsProfile){
            DashboardView.diagReset();
            hudLog=new HudEventLog(new java.io.File(getFilesDir(),"diagnostics"));
            hudLogThread=new android.os.HandlerThread("hud-event-log");hudLogThread.start();
            hudLogHandler=new android.os.Handler(hudLogThread.getLooper());
            DashboardView.setDiagnosticEventSink(new DashboardView.DiagnosticEventSink(){
                public void event(String name,String detail){recordHudEvent(name,detail);}
            });
        }
        DashboardView.attachStandalone(this);
        client=new VehicleDataClient(this);
        if(jsProfile)try{
            hudProbe=new JsHudProbe(getApplicationContext(),new JsHudProbe.Sink(){
                public void event(final String name,final String detail){DashboardView.diagEvent(name,detail);android.util.Log.i("JsHudProbe",name+" "+detail);
                    recordHudEvent(name,detail);}
            });
            final JsHudProbe probe=hudProbe;
            new Thread(new Runnable(){public void run(){probe.inspect();}},"js-hud-inspect").start();
        }catch(RuntimeException e){android.util.Log.e("JsHudProbe","Initialization failed",e);}
        catch(LinkageError e){android.util.Log.e("JsHudProbe","Initialization unavailable",e);}
    }
    private void recordHudEvent(final String name,final String detail){
        final long time=android.os.SystemClock.elapsedRealtime();
        android.os.Handler handler=hudLogHandler;final HudEventLog log=hudLog;
        if(handler!=null&&log!=null&&HudEventLog.accepts(name,detail))
            handler.post(new Runnable(){public void run(){if(!log.record(time,name,detail))android.util.Log.e("HudEventLog","Private event log write failed");}});
    }
    public void startCarPlayScreenProbe(){
        if(!jsProfile||hudLogHandler==null)return;
        final int generation=++screenProbeGeneration;
        DashboardView.diagEvent("carplay.screen.probe","armed");recordHudEvent("carplay.screen.probe","armed");
        final Context context=getApplicationContext();
        hudLogHandler.postDelayed(new Runnable(){public void run(){
            if(generation!=screenProbeGeneration)return;
            String result=CarPlayScreenProbe.run(context);
            DashboardView.diagEvent("carplay.screen.probe",result);recordHudEvent("carplay.screen.probe",result);
        }},20000);
    }
    private void refreshWifi(){
        int feature=-1,service=-1,state=-1;
        try{feature=getPackageManager().hasSystemFeature("android.hardware.wifi")?1:0;}catch(RuntimeException ignored){}
        try{Object value=getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            service=value instanceof WifiManager?1:0;
            if(service==1)state=((WifiManager)value).getWifiState();
        }catch(SecurityException e){state=-2;}catch(RuntimeException ignored){}
        DashboardView.diagWifi(feature,service,state,wifiWatching?1:0);
    }
    protected void onResume(){super.onResume();LanguageSettings.init(this);DashboardView.resumeStandalone(this);client.start();
        if(hudProbe!=null)hudProbe.startNavigationObservation();
        if(jsProfile){try{registerReceiver(wifiReceiver,new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));wifiWatching=true;}catch(RuntimeException e){wifiWatching=false;}refreshWifi();}}
    private void stopWifiObserver(){if(wifiWatching){try{unregisterReceiver(wifiReceiver);}catch(RuntimeException ignored){}wifiWatching=false;}}
    protected void onPause(){stopWifiObserver();
        if(hudProbe!=null)hudProbe.close();client.stop();DashboardView.pauseStandalone(this);super.onPause();}
    protected void onDestroy(){screenProbeGeneration++;stopWifiObserver();if(hudProbe!=null)hudProbe.close();if(client!=null)client.stop();if(jsProfile){DashboardView.stopStandaloneMedia(this);DashboardView.setDiagnosticEventSink(null);}if(hudLogThread!=null){final android.os.HandlerThread thread=hudLogThread;
            if(hudLogHandler==null||!hudLogHandler.post(new Runnable(){public void run(){thread.quit();}}))thread.quit();
            hudLogThread=null;hudLogHandler=null;}super.onDestroy();}
}
