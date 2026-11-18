package com.veloster.dashboard.theme;

import android.content.Context;
import android.content.SharedPreferences;

/** Preferences for dashboard appearance, separate from vehicle settings. */
public final class ThemeSettingsStore {
    private static final String PREFS = "dashboard_theme";
    private static final String KEY_ID = "theme_id";
    private static final String KEY_MENU_POSITION = "menu_position";
    private static final String KEY_MENU_DOCK = "menu_dock";
    private static final String KEY_WALLPAPER_ID = "wallpaper_id";
    private final SharedPreferences preferences;
    private final SharedPreferences uiPreferences;

    public ThemeSettingsStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        uiPreferences = context.getSharedPreferences("pit_vehicle_ui", Context.MODE_PRIVATE);
    }

    public String selectedId() {
        try { return preferences.getString(KEY_ID, "original"); }
        catch (ClassCastException e) { return "original"; }
    }

    public void save(String id) { preferences.edit().putString(KEY_ID, id).apply(); }
    public String selectedWallpaperId(){
        try{return preferences.getString(KEY_WALLPAPER_ID,"");}
        catch(ClassCastException ignored){return "";}
    }
    public void saveWallpaperId(String id){preferences.edit().putString(KEY_WALLPAPER_ID,id==null?"":id).apply();}
    public int menuPosition(){
        try {
            int value=preferences.getInt(KEY_MENU_POSITION,ThemeSpec.MENU_THEME);
            if(value==ThemeSpec.MENU_TOP||value==ThemeSpec.MENU_LEFT)return value;
        } catch(ClassCastException ignored){}
        return ThemeSpec.MENU_THEME;
    }

    public void saveMenuPosition(int value){preferences.edit().putInt(KEY_MENU_POSITION,value).apply();}
    public int menuDock(){
        try{
            int value=preferences.getInt(KEY_MENU_DOCK,ThemeSpec.DOCK_THEME);
            if(value==ThemeSpec.DOCK_ON||value==ThemeSpec.DOCK_OFF)return value;
        }catch(ClassCastException ignored){}
        return ThemeSpec.DOCK_THEME;
    }
    public void saveMenuDock(int value){preferences.edit().putInt(KEY_MENU_DOCK,value).apply();}
    private int option(String key,int count){
        try{int value=uiPreferences.getInt(key,0);return value>=0&&value<count?value:0;}
        catch(ClassCastException ignored){return 0;}
    }
    public int pedalBarStyle(){return option("pedal_bar_style",2);}
    public int brakeBarStyle(){return option("brake_bar_style",2);}
    public int f1Order(){return option("f1_order",6);}
    public boolean menuIcons(){return option("menu_icon_mode",2)==1;}
    public void saveMenuIcons(boolean value){uiPreferences.edit().putInt("menu_icon_mode",value?1:0).apply();}
    public void saveDisplayOptions(int pedal,int brake,int order){
        uiPreferences.edit().putInt("pedal_bar_style",pedal).putInt("brake_bar_style",brake).putInt("f1_order",order).apply();
    }
}
