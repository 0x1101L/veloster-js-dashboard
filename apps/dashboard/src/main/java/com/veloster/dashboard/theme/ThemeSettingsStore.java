package com.veloster.dashboard.theme;

import android.content.Context;
import android.content.SharedPreferences;

/** Isolated preference for the selected bundled theme. */
public final class ThemeSettingsStore {
    private static final String PREFS = "dashboard_theme";
    private static final String KEY_ID = "theme_id";
    private final SharedPreferences preferences;

    public ThemeSettingsStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String selectedId() {
        try { return preferences.getString(KEY_ID, "original"); }
        catch (ClassCastException e) { return "original"; }
    }

    public void save(String id) { preferences.edit().putString(KEY_ID, id).apply(); }
}
