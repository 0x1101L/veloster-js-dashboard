package com.veloster.dashboard;

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
import com.veloster.vehicle.VehicleDash;

/** Passive framework subscriptions only. No drive-mode setters, stream commands or OEM app IPC. */
final class VehicleDataClient implements Runnable {
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
    VehicleDataClient(Context context){this.context=context;}
    synchronized void start(){if(active)return;active=true;generation++;retryMs=2000;ignition=false;VehicleDash.ignition(false);run();}
    private boolean accepts(int token){return active&&token==generation&&ignition;}
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
                if(ignition!=on){
                    ignition=on;
                    HscanDataClient current=hscan;
                    if(current!=null)current.stop();
                    VehicleDash.ignition(on);
                    if(on&&current!=null)current.start();
                }
                }
            }};
            power.addPowerListener(plistener);
        }catch(RuntimeException e){releasePower();ignition=false;log("power",e);}catch(LinkageError e){releasePower();ignition=false;log("power",e);}
        if(nmode==null)try{
            nmode=new NModeManager(context);
            nlistener=new NModeListener(){
                public void onPowerTorqueTurboBoostReceived(byte[] b){if(nlistener==this&&accepts(token)){VehicleDash.rpm(b);VehicleDash.calculateObserved();}}
                public void onGForceDataReceived(byte[] b){if(nlistener==this&&accepts(token)){VehicleDash.gforce(b);VehicleDash.calculateObserved();}}
                public void onOilCoolantTemperature(byte[] b){if(nlistener==this&&accepts(token))VehicleDash.temperatures(b);}
                public void onSpeedTempUnitTempReceived(byte[] b){if(nlistener==this&&accepts(token))VehicleDash.units(b);}
                public void onNAddInfoReceived(byte[] b){if(nlistener==this&&accepts(token))VehicleDash.additional(b);}
                // Some OEM app subclasses declare this; captured250902 base/proxy lacks dispatch.
                public void onNModeZeroSpeedChanged(byte[] b){if(nlistener==this&&accepts(token))VehicleDash.wheels(b);}
            };
            nmode.registerNModeListener(nlistener);
        }catch(RuntimeException e){releaseNmode();log("NMode",e);}catch(LinkageError e){releaseNmode();log("NMode",e);}
        if(vehicle==null)try{
            vehicle=new VehicleInfoManager(context);
            vlistener=new VehicleInfoListener(){public void onVehicleSpeedChanged(int speed){if(vlistener==this&&accepts(token))VehicleDash.speed(speed);}};
            vehicle.addListener(vlistener);
        }catch(RuntimeException e){releaseVehicle();log("speed",e);}catch(LinkageError e){releaseVehicle();log("speed",e);}
        if(rear==null)try{
            rear=new PgsManager(context);
            rlistener=new PgsStatusListener(){public void onRVMViewStatusChanged(int view){if(rlistener==this&&accepts(token))VehicleDash.rearView(view);}};
            rear.registerPgsStatusListener(rlistener);
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
    synchronized void stop(){
        if(!active)return;active=false;generation++;handler.removeCallbacks(this);
        if(hscan!=null){hscan.stop();hscan=null;}
        releaseNmode();releaseVehicle();releasePower();releaseRear();retryMs=2000;
    }
    private void releaseNmode(){
        NModeManager manager=nmode;NModeListener listener=nlistener;nmode=null;nlistener=null;
        try{if(manager!=null&&listener!=null)manager.unregisterNModeListener(listener);}
        catch(RuntimeException e){log("NMode stop",e);}catch(LinkageError e){log("NMode stop",e);}
    }
    private void releaseVehicle(){
        VehicleInfoManager manager=vehicle;VehicleInfoListener listener=vlistener;vehicle=null;vlistener=null;
        try{if(manager!=null&&listener!=null)manager.removeListener(listener);}
        catch(RuntimeException e){log("speed stop",e);}catch(LinkageError e){log("speed stop",e);}
    }
    private void releasePower(){
        BCMManager manager=power;BCMPowerListener listener=plistener;power=null;plistener=null;
        try{if(manager!=null&&listener!=null)manager.removePowerListener(listener);}
        catch(RuntimeException e){log("power stop",e);}catch(LinkageError e){log("power stop",e);}
    }
    private void releaseRear(){
        PgsManager manager=rear;PgsStatusListener listener=rlistener;rear=null;rlistener=null;
        try{if(manager!=null&&listener!=null)manager.unregisterPgsStatusListener(listener);}
        catch(RuntimeException e){log("rear view stop",e);}catch(LinkageError e){log("rear view stop",e);}
    }
    private static void log(String source,Throwable error){com.veloster.diagnostics.ErrorLog.record(source,error);android.util.Log.e("NDashboard","Subscription unavailable: "+source,error);}
}
