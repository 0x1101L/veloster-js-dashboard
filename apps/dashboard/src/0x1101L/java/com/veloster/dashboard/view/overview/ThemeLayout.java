package com.veloster.dashboard.view.overview;

import com.veloster.dashboard.theme.ThemeSpec;

/** Overview geometry shared by drawing and hit testing. */
public final class ThemeLayout {
    private final ThemeSpec theme;
    public ThemeLayout(ThemeSpec theme) { this.theme=theme==null?ThemeSpec.original():theme; }
    public int columns(){return theme.columns;}
    public int pageSize(){return theme.pageSize;}
    public int pages(int count){return (count+pageSize()-1)/pageSize();}
    public float left(int slot){return theme.columns==4?18+(slot%columns())*191:18+(slot%columns())*155;}
    public float top(int slot){return theme.columns==4?58+(slot/columns())*132:58+(slot/columns())*104;}
    public float width(){return (theme.columns==4?191:155)-theme.spacing.gridGapX;}
    public float height(){return (theme.columns==4?132:104)-theme.spacing.gridGapY;}
}
