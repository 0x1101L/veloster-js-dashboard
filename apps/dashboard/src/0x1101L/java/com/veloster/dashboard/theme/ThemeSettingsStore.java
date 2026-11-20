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
    private static final String KEY_MENU_STYLE_MIGRATED = "menu_style_migrated";
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
    private String accentKey(String themeId){return "accent_palette_"+(themeId==null?"":themeId);}
    public int accentPalette(String themeId){
        try{int value=preferences.getInt(accentKey(themeId),0);return value>=0&&value<com.veloster.dashboard.theme.ThemePalette.accentCount(themeId)?value:0;}
        catch(ClassCastException ignored){return 0;}
    }
    public void saveAccentPalette(String themeId,int value){
        if(themeId==null||themeId.length()==0)return;
        int safe=value>=0&&value<com.veloster.dashboard.theme.ThemePalette.accentCount(themeId)?value:0;
        preferences.edit().putInt(accentKey(themeId),safe).apply();
    }
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
    private int glassOption(String key,int count,int defaultValue){
        try{int value=preferences.getInt(key,defaultValue);return value>=0&&value<count?value:defaultValue;}
        catch(ClassCastException ignored){return defaultValue;}
    }
    public int liquidGlassTransparency(){return glassOption("liquid_glass_transparency",5,0);}
    public int liquidGlassBlur(){return glassOption("liquid_glass_blur",5,1);}
    public int liquidGlassHighlight(){return glassOption("liquid_glass_highlight",3,1);}
    public void saveLiquidGlassOptions(int transparency,int blur,int highlight){
        preferences.edit().putInt("liquid_glass_transparency",transparency>=0&&transparency<=4?transparency:0)
                .putInt("liquid_glass_blur",blur>=0&&blur<=4?blur:1)
                .putInt("liquid_glass_highlight",highlight>=0&&highlight<=2?highlight:1).apply();
    }
    private String menuStyleKey(String themeId){return "menu_style_"+(themeId==null?"":themeId);}
    /** -1 means inherit the theme default; 0 is labels and 1 is icons. */
    public int menuStyleOverride(String themeId){
        try{int value=preferences.getInt(menuStyleKey(themeId),-1);return value==0||value==1?value:-1;}
        catch(ClassCastException ignored){return -1;}
    }
    public boolean menuIcons(String themeId,boolean themeDefault){
        int override=menuStyleOverride(themeId);
        return override<0?themeDefault:override==1;
    }
    public void saveMenuStyle(String themeId,int value){
        if(themeId==null||themeId.length()==0)return;
        preferences.edit().putInt(menuStyleKey(themeId),value<0?-1:value==1?1:0).apply();
    }
    /** Preserve the previous shared choice for the theme active during the one-time upgrade. */
    public void migrateLegacyMenuStyle(String themeId){
        try{if(preferences.getInt(KEY_MENU_STYLE_MIGRATED,0)!=0)return;}
        catch(ClassCastException ignored){}
        try{int old=uiPreferences.getInt("menu_icon_mode",-1);if(old==0||old==1)saveMenuStyle(themeId,old);}
        catch(ClassCastException ignored){}
        preferences.edit().putInt(KEY_MENU_STYLE_MIGRATED,1).apply();
    }
    public void saveDisplayOptions(int pedal,int brake,int order){
        uiPreferences.edit().putInt("pedal_bar_style",pedal).putInt("brake_bar_style",brake).putInt("f1_order",order).apply();
    }
}
