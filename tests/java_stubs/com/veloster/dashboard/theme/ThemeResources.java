package com.veloster.dashboard.theme;

import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.content.Context;

public final class ThemeResources {
    public static ThemeResources load(Context context,ThemeSpec spec){return new ThemeResources();}
    public Bitmap background(){return null;}
    public int backdropColor(){return 0;}
    public ThemeSpec.Backdrop backdrop(){return null;}
    public Typeface font(String role,boolean bold){return bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT;}
    public Typeface fontForText(String text,boolean bold){return bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT;}
}
