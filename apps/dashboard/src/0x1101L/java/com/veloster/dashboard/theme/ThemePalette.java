package com.veloster.dashboard.theme;

/** Maps a renderer's historical color to a semantic theme token without changing Original. */
public final class ThemePalette {
    private ThemePalette() {}
    public static int accent(ThemeSpec theme,int original){return theme==null||theme.usesComponentPalette()?original:theme.accent;}
    public static int primary(ThemeSpec theme,int original){return theme==null||theme.usesComponentPalette()?original:theme.textPrimary;}
    public static int secondary(ThemeSpec theme,int original){return theme==null||theme.usesComponentPalette()?original:theme.textSecondary;}
    public static int critical(ThemeSpec theme,int original){return theme==null||theme.usesComponentPalette()?original:theme.critical;}
    public static int warning(ThemeSpec theme,int original){return theme==null||theme.usesComponentPalette()?original:theme.warning;}
    public static int stroke(ThemeSpec theme,int original){return theme==null||theme.usesComponentPalette()?original:theme.stroke;}
    public static int panel(ThemeSpec theme,int original){return theme==null||theme.usesComponentPalette()?original:theme.panel;}
    public static int background(ThemeSpec theme,int original){return theme==null||theme.usesComponentPalette()?original:theme.background;}
}
