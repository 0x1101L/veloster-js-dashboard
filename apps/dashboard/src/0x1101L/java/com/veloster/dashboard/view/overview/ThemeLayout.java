package com.veloster.dashboard.view.overview;

import com.veloster.dashboard.theme.ThemeSpec;

/** Overview geometry shared by drawing and hit testing. */
public final class ThemeLayout {
    private final ThemeSpec theme;
    public ThemeLayout(ThemeSpec theme) { this.theme=theme==null?ThemeSpec.original():theme; }
    public int columns(){return 5;}
    public float left(int slot){return 18+(slot%columns())*155;}
    public float top(int slot){return 58+(slot/columns())*104;}
    public float width(){return 155-theme.layoutSpacing().gap;}
    public float height(){return 104-theme.layoutSpacing().gap;}
}
