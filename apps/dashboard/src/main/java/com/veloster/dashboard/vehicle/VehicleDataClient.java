package com.veloster.dashboard.vehicle;

import android.content.Context;
import android.os.Handler;
import com.hkmc.bcm.BCMManager;
import com.hkmc.bcm.BCMPowerListener;
import com.hkmc.micom.NModeListener;
import com.hkmc.micom.NModeManager;
import com.hkmc.micom.VehicleInfoListener;
import com.hkmc.micom.VehicleInfoManager;
import com.hkmc.pgs.PgsManager;
import com.hkmc.pgs.PgsStatusListener;
import com.veloster.dashboard.view.DashboardView;

/** Passive framework subscriptions only. No drive-mode setters, stream commands or OEM app IPC. */
public final class VehicleDataClient implements Runnable {
    private final Context context;
    private final Handler handler=new Handler();
    private volatile boolean active,ignition;
    private volatile int generation;
    private int retryMs=2000;
    private volatile HscanDataClient hscan;
    private NModeManager nmode;
    private VehicleInfoManager vehicle;
    private BCMManager power;
    private PgsManager rear;
    private volatile NModeListener nlistener;
    private volatile VehicleInfoListener vlistener;
    private volatile BCMPowerListener plistener;
    private volatile PgsStatusListener rlistener;
    public VehicleDataClient(Context context){this.context=context;}
    public synchronized void start(){if(active)return;active=true;generation++;retryMs=2000;ignition=false;DashboardView.ignition(false);run();}
    private boolean accepts(int token){return active&&token==generation&&ignition&&HscanDataClient.supportedProfile();}
    public synchronized void run(){
        if(!active)return;
        handler.removeCallbacks(this);
        final int token=generation;
        if(power==null)try{
            power=(BCMManager)com.hkmc.VehicleInfoManager.getInstance(context).getService(com.hkmc.VehicleInfoManager.BCM_SERVICE);
            if(power==null)throw new IllegalStateException("BCM service unavailable");
            ignition=power.getIGNStatus();
            plistener=new BCMPowerListener(){public void onIgnitionChanged(boolean on){
                synchronized(VehicleDataClient.this){
                if(!active||token!=generation||plistener!=this)return;
                binding("bcm","received");
                if(ignition!=on){
                    ignition=on;
                    HscanDataClient current=hscan;
                    if(current!=null)current.stop();
                    DashboardView.ignition(on);
                    if(on&&current!=null)current.start();
                }
                }
            }};
            power.addPowerListener(plistener);binding("bcm","registered");
        }catch(RuntimeException e){releasePower();ignition=false;log("power",e);}catch(LinkageError e){releasePower();ignition=false;log("power",e);}
        if(nmode==null)try{
            nmode=new NModeManager(context);
            nlistener=new NModeListener(){
                public void onPowerTorqueTurboBoostReceived(byte[] b){synchronized(VehicleDataClient.this){if(nlistener==this&&accepts(token)){binding("nmode","received");DashboardView.rpm(b);DashboardView.calculateObserved();}}}
                public void onGForceDataReceived(byte[] b){synchronized(VehicleDataClient.this){if(nlistener==this&&accepts(token)){binding("nmode","received");DashboardView.gforce(b);DashboardView.calculateObserved();}}}
                public void onOilCoolantTemperature(byte[] b){synchronized(VehicleDataClient.this){if(nlistener==this&&accepts(token)){binding("nmode","received");DashboardView.temperatures(b);}}}
                public void onSpeedTempUnitTempReceived(byte[] b){synchronized(VehicleDataClient.this){if(nlistener==this&&accepts(token)&&!HscanDataClient.pdProfile()){binding("nmode","received");DashboardView.units(b);}}}
                public void onNAddInfoReceived(byte[] b){synchronized(VehicleDataClient.this){if(nlistener==this&&accepts(token)){binding("nmode","received");DashboardView.additional(b);}}}
                // Some OEM app subclasses declare this; captured250902 base/proxy lacks dispatch.
                public void onNModeZeroSpeedChanged(byte[] b){synchronized(VehicleDataClient.this){if(nlistener==this&&accepts(token)){binding("nmode","received");DashboardView.wheels(b);}}}
            };
            nmode.registerNModeListener(nlistener);binding("nmode","registered");
        }catch(RuntimeException e){releaseNmode();log("NMode",e);}catch(LinkageError e){releaseNmode();log("NMode",e);}
        if(vehicle==null)try{
            vehicle=new VehicleInfoManager(context);
            vlistener=new VehicleInfoListener(){public void onVehicleSpeedChanged(int speed){synchronized(VehicleDataClient.this){if(vlistener==this&&accepts(token)&&!HscanDataClient.pdProfile()){binding("speed","received");DashboardView.speed(speed);}}}};
            vehicle.addListener(vlistener);binding("speed","registered");
        }catch(RuntimeException e){releaseVehicle();log("speed",e);}catch(LinkageError e){releaseVehicle();log("speed",e);}
        if(rear==null)try{
            rear=new PgsManager(context);
            rlistener=new PgsStatusListener(){public void onRVMViewStatusChanged(int view){synchronized(VehicleDataClient.this){if(rlistener==this&&accepts(token)){binding("pgs","received");DashboardView.rearView(view);}}}};
            rear.registerPgsStatusListener(rlistener);binding("pgs","registered");
        }catch(RuntimeException e){releaseRear();log("rear view",e);}catch(LinkageError e){releaseRear();log("rear view",e);}
        if(hscan==null)try{
            hscan=new HscanDataClient(new HscanDataClient.Gate(){public boolean accepts(){return VehicleDataClient.this.accepts(token);}});
        }catch(RuntimeException e){hscan=null;log("HSCAN",e);}catch(LinkageError e){hscan=null;log("HSCAN",e);}
        if(hscan!=null&&ignition)hscan.start();
        // Retry every missing subscriber, including PGS and HSCAN construction.
        // Existing registrations stay in place; unavailable services back off.
        if(power==null||nmode==null||vehicle==null||rear==null||hscan==null){
            handler.postDelayed(this,retryMs);retryMs=Math.min(30000,retryMs*2);
        }else retryMs=2000;
    }
    public synchronized void stop(){
        if(!active)return;active=false;generation++;handler.removeCallbacks(this);
        if(hscan!=null){hscan.stop();hscan=null;}
        releaseNmode();releaseVehicle();releasePower();releaseRear();retryMs=2000;
    }
    private void releaseNmode(){
        NModeManager manager=nmode;NModeListener listener=nlistener;nmode=null;nlistener=null;
        try{if(manager!=null&&listener!=null){manager.unregisterNModeListener(listener);binding("nmode","stopped");}}
        catch(RuntimeException e){log("NMode stop",e);}catch(LinkageError e){log("NMode stop",e);}
    }
    private void releaseVehicle(){
        VehicleInfoManager manager=vehicle;VehicleInfoListener listener=vlistener;vehicle=null;vlistener=null;
        try{if(manager!=null&&listener!=null){manager.removeListener(listener);binding("speed","stopped");}}
        catch(RuntimeException e){log("speed stop",e);}catch(LinkageError e){log("speed stop",e);}
    }
    private void releasePower(){
        BCMManager manager=power;BCMPowerListener listener=plistener;power=null;plistener=null;
        try{if(manager!=null&&listener!=null){manager.removePowerListener(listener);binding("bcm","stopped");}}
        catch(RuntimeException e){log("power stop",e);}catch(LinkageError e){log("power stop",e);}
    }
    private void releaseRear(){
        PgsManager manager=rear;PgsStatusListener listener=rlistener;rear=null;rlistener=null;
        try{if(manager!=null&&listener!=null){manager.unregisterPgsStatusListener(listener);binding("pgs","stopped");}}
        catch(RuntimeException e){log("rear view stop",e);}catch(LinkageError e){log("rear view stop",e);}
    }
    private static void binding(String source,String status){DashboardView.diagEvent("binding."+source,status);}
    private static void log(String source,Throwable error){binding(source.startsWith("power")?"bcm":source.startsWith("NMode")?"nmode":source.startsWith("rear view")?"pgs":source.startsWith("speed")?"speed":source,error instanceof SecurityException?"permission denied":error instanceof LinkageError?"unsupported":"error");com.veloster.diagnostics.ErrorLog.record(source,error);android.util.Log.e("NDashboard","Subscription unavailable: "+source,error);}
}
