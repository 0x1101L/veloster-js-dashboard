package com.veloster.dashboard;

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
import com.veloster.vehicle.VehicleDash;
import com.veloster.i18n.LanguageSettings;

/** Own package, own task and own data subscriptions. Never starts the OEM NMode app. */
public final class DashboardActivity extends Activity {
    private VehicleDataClient client;
    private JsHudProbe hudProbe;
    private boolean wifiWatching,jsProfile;
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
        VehicleDash.profile(profile==HscanProfile.Kind.PD,jsProfile,profile==HscanProfile.Kind.JSN);
        VehicleDash.attachStandalone(this);
        client=new VehicleDataClient(this);
        if(jsProfile)try{
            VehicleDash.diagReset();
            hudProbe=new JsHudProbe(getApplicationContext(),new JsHudProbe.Sink(){
                public void event(String name,String detail){VehicleDash.diagEvent(name,detail);android.util.Log.i("JsHudProbe",name+" "+detail);}
            });
            final JsHudProbe probe=hudProbe;
            new Thread(new Runnable(){public void run(){probe.inspect();}},"js-hud-inspect").start();
        }catch(RuntimeException e){android.util.Log.e("JsHudProbe","Initialization failed",e);}
        catch(LinkageError e){android.util.Log.e("JsHudProbe","Initialization unavailable",e);}
    }
    private void refreshWifi(){
        int feature=-1,service=-1,state=-1;
        try{feature=getPackageManager().hasSystemFeature("android.hardware.wifi")?1:0;}catch(RuntimeException ignored){}
        try{Object value=getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            service=value instanceof WifiManager?1:0;
            if(service==1)state=((WifiManager)value).getWifiState();
        }catch(SecurityException e){state=-2;}catch(RuntimeException ignored){}
        VehicleDash.diagWifi(feature,service,state,wifiWatching?1:0);
    }
    protected void onResume(){super.onResume();LanguageSettings.init(this);VehicleDash.resumeStandalone(this);client.start();
        if(hudProbe!=null)hudProbe.startNavigationObservation();
        if(jsProfile){try{registerReceiver(wifiReceiver,new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));wifiWatching=true;}catch(RuntimeException e){wifiWatching=false;}refreshWifi();}}
    private void stopWifiObserver(){if(wifiWatching){try{unregisterReceiver(wifiReceiver);}catch(RuntimeException ignored){}wifiWatching=false;}}
    protected void onPause(){stopWifiObserver();
        if(hudProbe!=null)hudProbe.close();client.stop();VehicleDash.pauseStandalone(this);super.onPause();}
    protected void onDestroy(){stopWifiObserver();if(hudProbe!=null)hudProbe.close();if(client!=null)client.stop();super.onDestroy();}
}
