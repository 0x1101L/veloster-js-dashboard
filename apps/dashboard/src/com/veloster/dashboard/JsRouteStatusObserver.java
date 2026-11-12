package com.veloster.dashboard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/** Passive OEM navigation status; no maneuvers, destinations or TBT transmission. */
final class JsRouteStatusObserver {
    static final String ACTION="com.mnsoft.navi.action.Route_Guide_Status";
    static final String STATE="ROUTE_GUIDE_STATE";
    private final Context context;
    private final JsHudProbe.Sink sink;
    private BroadcastReceiver receiver;
    private int generation;

    JsRouteStatusObserver(Context context,JsHudProbe.Sink sink){this.context=context;this.sink=sink;}

    synchronized void start(){
        if(receiver!=null)return;
        final int token=++generation;
        receiver=new BroadcastReceiver(){public void onReceive(Context ignored,Intent intent){
            synchronized(JsRouteStatusObserver.this){
                if(receiver!=this||generation!=token||intent==null||!ACTION.equals(intent.getAction()))return;
                try{
                    Object value=intent.getExtras()==null?null:intent.getExtras().get(STATE);
                    if(value instanceof Integer){
                        sink.event("navigation.route.source",isInitialStickyBroadcast()?"sticky":"broadcast");
                        sink.event("navigation.route_state",String.valueOf(value));
                    }
                    else sink.event("navigation.route.invalid","missing or non-integer state");
                }catch(RuntimeException e){sink.event("navigation.route.invalid",e.getClass().getName());}
            }
        }};
        sink.event("navigation.route.starting","waiting for registration");
        try{
            // Android delivers the matching sticky intent through onReceive, if one exists.
            context.registerReceiver(receiver,new IntentFilter(ACTION));
            sink.event("navigation.route.observer","registered; status only; sender not authenticated");
        }catch(RuntimeException e){
            stop();sink.event("navigation.route.observer.error",e.getClass().getName());
        }
    }

    synchronized void stop(){
        if(receiver==null)return;
        BroadcastReceiver old=receiver;receiver=null;generation++;
        try{context.unregisterReceiver(old);}
        catch(RuntimeException e){sink.event("navigation.route.close",e.getClass().getName());}
        sink.event("navigation.route.stopped","stopped");
    }
}
