package com.veloster.dashboard.vehicle.js;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import com.veloster.i18n.UiText;

/** Explicit per-install JS transmission selection. Unknown is never persisted as MT. */
public final class JsTransmissionSetting {
    private static final String KEY="transmission_type";
    private final Activity activity;
    private final SharedPreferences prefs;
    public JsTransmissionSetting(Activity activity){this.activity=activity;prefs=activity.getSharedPreferences("js_vehicle_settings",0);}
    public int get(){try{int value=prefs.getInt(KEY,0);return value==1||value==2?value:0;}catch(ClassCastException ignored){return 0;}}
    public void show(final Runnable changed){
        new AlertDialog.Builder(activity).setTitle(UiText.t("변속기 선택"))
            .setSingleChoiceItems(new String[]{"DCT",UiText.t("수동")},get()-1,new DialogInterface.OnClickListener(){
                public void onClick(DialogInterface dialog,int index){
                    if(index<0||index>1)return;
                    prefs.edit().putInt(KEY,index+1).apply();dialog.dismiss();changed.run();
                }
            }).setNegativeButton(UiText.t("취소"),null).show();
    }
}
