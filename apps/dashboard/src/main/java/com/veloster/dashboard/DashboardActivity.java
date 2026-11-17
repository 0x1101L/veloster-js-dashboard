package com.veloster.dashboard;

import com.veloster.dashboard.vehicle.HscanDataClient;
import com.veloster.dashboard.vehicle.HscanProfile;
import com.veloster.dashboard.vehicle.VehicleDataClient;

import com.veloster.dashboard.debugger.JsHudProbe;
import com.veloster.dashboard.debugger.HudEventLog;
import com.veloster.dashboard.debugger.CarPlayRawLog;
import com.veloster.dashboard.debugger.CarPlayRuntimeProbe;
import com.veloster.dashboard.debugger.BluetoothRuntimeProbe;

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
public final class DashboardActivity extends Activity {
    public static final int REQUEST_CARPLAY_CAPTURE=0x4350;
    private VehicleDataClient client;
    private JsHudProbe hudProbe;
    private boolean wifiWatching,jsProfile;
    private final java.util.concurrent.atomic.AtomicBoolean bluetoothProbeRunning=new java.util.concurrent.atomic.AtomicBoolean();
    private android.os.HandlerThread hudLogThread;
    private volatile android.os.Handler hudLogHandler;
    private HudEventLog hudLog;
    private CarPlayRawLog carPlayRawLog;
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
            carPlayRawLog=new CarPlayRawLog(new java.io.File(getFilesDir(),"diagnostics"));
            hudLogThread=new android.os.HandlerThread("hud-event-log");hudLogThread.start();
            hudLogHandler=new android.os.Handler(hudLogThread.getLooper());
            DashboardView.setDiagnosticEventSink(new DashboardView.DiagnosticEventSink(){
                public void event(String name,String detail){recordHudEvent(name,detail);}
            });
            DashboardView.carPlayRuntimeStatus("조회 대기 중", "조회 대기 중");
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
        if(jsProfile&&carPlayRawLog!=null)DashboardView.carPlayRawStatus(carPlayRawLog.latestCaptureSummary());
        if(jsProfile)new Thread(new Runnable(){public void run(){
            CarPlayRuntimeProbe.Snapshot status=CarPlayRuntimeProbe.inspect();
            DashboardView.carPlayRuntimeStatus(status.library,status.process);
        }},"carplay-runtime-probe").start();
        if(hudProbe!=null)hudProbe.startNavigationObservation();
        if(jsProfile){try{registerReceiver(wifiReceiver,new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));wifiWatching=true;}catch(RuntimeException e){wifiWatching=false;}refreshWifi();probeBluetooth();}}
    private void probeBluetooth(){
        if(!bluetoothProbeRunning.compareAndSet(false,true))return;
        new Thread(new Runnable(){public void run(){
            final BluetoothRuntimeProbe.Snapshot status=BluetoothRuntimeProbe.read();
            runOnUiThread(new Runnable(){public void run(){DashboardView.bluetoothStatus(status);bluetoothProbeRunning.set(false);}});
        }} ,"bluetooth-hal-probe").start();
    }
    private void stopWifiObserver(){if(wifiWatching){try{unregisterReceiver(wifiReceiver);}catch(RuntimeException ignored){}wifiWatching=false;}}
    protected void onPause(){stopWifiObserver();
        if(hudProbe!=null)hudProbe.close();client.stop();DashboardView.pauseStandalone(this);super.onPause();}
    protected void onDestroy(){stopWifiObserver();if(hudProbe!=null)hudProbe.close();if(client!=null)client.stop();if(jsProfile){DashboardView.stopStandaloneMedia(this);DashboardView.setDiagnosticEventSink(null);}if(hudLogThread!=null){final android.os.HandlerThread thread=hudLogThread;
            if(hudLogHandler==null||!hudLogHandler.post(new Runnable(){public void run(){thread.quit();}}))thread.quit();
            hudLogThread=null;hudLogHandler=null;}super.onDestroy();}
    protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=REQUEST_CARPLAY_CAPTURE||resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        try{
            if(carPlayRawLog==null)throw new java.io.IOException("JS CarPlay storage is unavailable");
            java.io.InputStream input=getContentResolver().openInputStream(data.getData());
            if(input==null)throw new java.io.IOException("selected capture cannot be opened");
            java.io.File saved=carPlayRawLog.importCapture(input,android.os.SystemClock.elapsedRealtime());
            DashboardView.carPlayRawStatus(saved.getName()+" · "+saved.length()+" bytes");
            android.widget.Toast.makeText(this,"CarPlay 원본 캡처를 앱 내부에 저장했습니다",android.widget.Toast.LENGTH_LONG).show();
        }catch(Exception e){android.util.Log.e("CarPlayRawLog","Capture import failed",e);android.widget.Toast.makeText(this,"캡처 검증 또는 내부 저장 실패: "+e.getMessage(),android.widget.Toast.LENGTH_LONG).show();}
    }
}
