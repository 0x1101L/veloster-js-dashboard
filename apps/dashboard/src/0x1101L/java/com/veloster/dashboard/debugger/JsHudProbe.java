package com.veloster.dashboard.debugger;

import com.veloster.dashboard.listener.JsRouteStatusObserver;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import java.lang.reflect.Method;

/** JS navigation-state observation and explicit TBT API boundary. */
public final class JsHudProbe {
    public interface Sink { void event(String name, String detail); }
    private final Context context;
    private final Sink sink;
    private final JsRouteStatusObserver routeObserver;
    private static final Uri NAV_STATE=Uri.parse("content://com.mnsoft.navi/State_Table");
    private HandlerThread navThread;
    private Handler navHandler;
    private ContentObserver navObserver;
    private volatile boolean navActive;
    private volatile int navGeneration;

    public JsHudProbe(Context context, Sink sink) {
        if(context==null||sink==null)throw new IllegalArgumentException("context and sink required");
        if(!"js_kr".equals(Build.PRODUCT)||!"eng.vmadmin.20260114.090656".equals(Build.VERSION.INCREMENTAL))
            throw new IllegalStateException("Unexpected product/build");
        this.context=context;this.sink=sink;
        routeObserver=new JsRouteStatusObserver(context,sink);
    }
    /** Inspect only. An Android Context is required for listener registration or TBT. */
    public static void main(String[] args){
        if(args.length!=0&&!(args.length==1&&"--inspect".equals(args[0])))
            throw new IllegalArgumentException("Only --inspect is available from main");
        if(!"js_kr".equals(Build.PRODUCT)||!"eng.vmadmin.20260114.090656".equals(Build.VERSION.INCREMENTAL))
            throw new IllegalStateException("Unexpected product/build");
        Sink stdout=new Sink(){public void event(String name,String detail){System.out.println(name+": "+detail);}};
        inspectTypes(stdout);inspectServices(stdout);
    }
    private static void inspectServices(Sink output){
        inspectService(output,"micom","com.hkmc.micom.hscanmicomservice",null);
        inspectService(output,"cluster","com.hkmc.micom.cluster","com.hkmc.cluster.IClusterService");
    }
    private static void inspectService(Sink output,String label,String name,String expected){
        try{
            Class<?> sm=Class.forName("android.os.ServiceManager");
            Object binder=sm.getMethod("getService",String.class).invoke(null,name);
            if(binder==null)output.event(label+".service","absent or inaccessible");
            else if(expected==null)output.event(label+".service","present; descriptor and permissions unverified");
            else{
                String descriptor=((android.os.IBinder)binder).getInterfaceDescriptor();
                output.event(label+".service",expected.equals(descriptor)?
                    "present; matching descriptor; method permissions unverified":"descriptor mismatch");
            }
        }catch(Throwable e){output.event(label+".service.error",e.getClass().getName());}
    }
    /** Examine declarations; neither service discovery nor these listeners prove HUD reception. */
    public void inspect(){inspectTypes(sink);inspectServices(sink);}
    private static void inspectTypes(Sink sink){
        for(String name:new String[]{"com.hkmc.cluster.NavigationCluster", "com.hkmc.cluster.NavigationClusterListener",
                "com.hkmc.cluster.ClusterManager", "com.hkmc.micom.NModeListener",
                "com.hkmc.micom.VehicleInfoListener", "com.hkmc.hscan.IHscanMicomService",
                "com.hkmc.hscan.IHscanMicomListener"}){
            try{
                Class<?> type=Class.forName(name);sink.event("class",name);
                for(Method m:type.getMethods()){
                    String n=m.getName().toLowerCase(java.util.Locale.US);
                    if(n.contains("listener")||n.contains("tbt")||n.contains("hud")||n.startsWith("on"))
                        sink.event("method",m.toGenericString());
                }
            }catch(Throwable e){sink.event("unavailable",name+" "+e.getClass().getName());}
        }
    }

    /** Observe provider and route-status broadcasts independently; neither supplies turn-by-turn data. */
    public synchronized void startNavigationObservation(){
        routeObserver.start();
        if(navObserver!=null)return;
        try{
            final int generation=++navGeneration;
            navThread=new HandlerThread("js-nav-state");navThread.start();
            navHandler=new Handler(navThread.getLooper());
            final Handler worker=navHandler;
            final java.util.concurrent.atomic.AtomicBoolean queued=new java.util.concurrent.atomic.AtomicBoolean();
            final Runnable read=new Runnable(){public void run(){
                try{if(isCurrent(generation))readNavigationState(generation);}finally{queued.set(false);}
            }};
            navObserver=new ContentObserver(worker){
                @Override public void onChange(boolean selfChange){
                    synchronized(JsHudProbe.this){
                        if(isCurrent(generation)){
                            sink.event("navigation.notification","received");
                            if(queued.compareAndSet(false,true)&&!worker.postDelayed(read,250))queued.set(false);
                        }
                    }
                }
            };
            navActive=true;
            context.getContentResolver().registerContentObserver(NAV_STATE,true,navObserver);
            sink.event("navigation.observer","registered; TBT details not established");
            if(queued.compareAndSet(false,true)&&!worker.post(read))queued.set(false);
        }catch(RuntimeException e){
            stopNavigationObservation();
            sink.event("navigation.observer.error",e.getClass().getName());
        }
    }
    private boolean isCurrent(int generation){return navActive&&navGeneration==generation;}
    private synchronized void navEvent(int generation,String name,String detail){if(isCurrent(generation))sink.event(name,detail);}
    private void readNavigationState(int generation){
        Cursor cursor=null;
        try{
            cursor=context.getContentResolver().query(NAV_STATE,new String[]{"State_Destination"},null,null,null);
            if(!isCurrent(generation))return;
            if(cursor==null){navEvent(generation,"navigation.state","provider returned null");return;}
            int column=cursor.getColumnIndex("State_Destination");
            if(column<0||!cursor.moveToFirst()||cursor.isNull(column))navEvent(generation,"navigation.state","unavailable");
            else {
                String value=cursor.getString(column);
                try{navEvent(generation,"navigation.state_destination",String.valueOf(Integer.parseInt(value)));}
                catch(NumberFormatException e){navEvent(generation,"navigation.state","unavailable");}
            }
        }catch(RuntimeException e){navEvent(generation,"navigation.state.error",e.getClass().getName());}
        finally{if(cursor!=null)try{cursor.close();}catch(RuntimeException e){navEvent(generation,"navigation.cursor.close",e.getClass().getName());}}
    }
    private synchronized void stopNavigationObservation(){
        navActive=false;navGeneration++;
        if(navObserver!=null)sink.event("navigation.observer.stopped","stopped");
        if(navObserver!=null){try{context.getContentResolver().unregisterContentObserver(navObserver);}
            catch(RuntimeException e){sink.event("navigation.observer.close",e.getClass().getName());}navObserver=null;}
        if(navThread!=null){navThread.quit();navThread=null;navHandler=null;}
    }

    /** Explicit opt-in TBT call: requires real, independently verified arguments; never runs on registration. */
    public void sendTbt(int displayType,int sideStreet,float direction,float distance,int distanceError,
                        int combiSideStreet,int scale,int barGraphLevel,int barGraphLevelError) throws Exception {
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Use a worker thread");
        Class<?> type=Class.forName("com.hkmc.cluster.NavigationCluster");
        Object cluster=type.getConstructor(Context.class).newInstance(context);
        Method m=type.getMethod("updateTBTInfo",int.class,int.class,float.class,float.class,
                int.class,int.class,int.class,int.class,int.class);
        m.invoke(cluster,displayType,sideStreet,direction,distance,distanceError,combiSideStreet,scale,barGraphLevel,barGraphLevelError);
        sink.event("tbt.returned","Method returned; physical cluster/HUD display not confirmed");
    }
    public synchronized void close(){
        routeObserver.stop();
        stopNavigationObservation();
    }
}
