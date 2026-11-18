package com.veloster.dashboard.view.gauge;

import com.veloster.dashboard.vehicle.*;
import android.app.Activity;
import android.content.SharedPreferences;

/** Local display customization only. Uses the same card geometry as GaugeRenderer. */
public final class GaugeEditor {
    private final Activity activity;
    private final GaugeLayout layout;
    private final Runnable invalidate;
    private final SharedPreferences prefs;
    public GaugeEditor(Activity a,GaugeLayout l,Runnable changed){
        activity=a;layout=l;invalidate=changed;prefs=a.getSharedPreferences("nmode_vehicle_gauges",0);
        try{layout.dense(prefs.getInt("dense",0)==1);}catch(ClassCastException e){/* Keep default grid. */}
        for(int i=0;i<GaugeLayout.SLOTS;i++){
            try{layout.set(i,prefs.getInt("signal_"+i,l.signal(i)),prefs.getInt("style_"+i,l.style(i)));}
            catch(IllegalArgumentException e){/* Corrupt selection: retain this slot's default. */}
            catch(ClassCastException e){/* Preferences from an incompatible version. */}
        }
    }
    public int hit(float x,float y){return layout.hit(x,y);}
    public void dismiss(){}
    public void toggleGrid(){layout.dense(!layout.dense());save();}
    public void save(){SharedPreferences.Editor e=prefs.edit();e.putInt("dense",layout.dense()?1:0);for(int i=0;i<GaugeLayout.SLOTS;i++){e.putInt("signal_"+i,layout.signal(i));e.putInt("style_"+i,layout.style(i));}e.apply();invalidate.run();}
}
