package com.veloster.dashboard.vehicle.js;

import android.app.Activity;
import android.content.SharedPreferences;

/** Explicit per-install JS transmission selection. Unknown is never persisted as MT. */
public final class JsTransmissionSetting {
    private static final String KEY="transmission_type";
    private final SharedPreferences prefs;
    public JsTransmissionSetting(Activity activity){prefs=activity.getSharedPreferences("js_vehicle_settings",0);}
    public int get(){try{int value=prefs.getInt(KEY,0);return value==1||value==2?value:0;}catch(ClassCastException ignored){return 0;}}
    public void save(int value){if(value<1||value>2)throw new IllegalArgumentException("transmission selection");prefs.edit().putInt(KEY,value).apply();}
}
