package com.veloster.dashboard.view.graph;

import com.veloster.dashboard.vehicle.*;

import android.app.Activity;
import android.content.SharedPreferences;

/** Local graph selection and time window only; no vehicle setting commands. */
public final class GraphEditor {
    private final GraphLayout layout;
    private final SharedPreferences prefs;
    private final Runnable changed;
    public GraphEditor(Activity a,GraphLayout l,Runnable invalidate){
        layout=l;changed=invalidate;prefs=a.getSharedPreferences("nmode_vehicle_graphs",0);
        try{layout.window(prefs.getInt("window",60000));}catch(IllegalArgumentException e){}catch(ClassCastException e){}
        for(int i=0;i<GraphLayout.SLOTS;i++)try{layout.signal(i,prefs.getInt("signal_"+i,layout.signal(i)));}catch(IllegalArgumentException e){}catch(ClassCastException e){}
    }
    public void save(){SharedPreferences.Editor e=prefs.edit().putInt("window",(int)layout.window());for(int i=0;i<GraphLayout.SLOTS;i++)e.putInt("signal_"+i,layout.signal(i));e.apply();changed.run();}
    public void dismiss(){}
    public void window(long value){layout.window(value);save();}
}
