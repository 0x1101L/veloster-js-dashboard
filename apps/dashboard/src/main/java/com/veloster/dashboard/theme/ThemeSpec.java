package com.veloster.dashboard.theme;

/** Immutable visual values consumed by Dashboard renderers. Vehicle semantics stay outside this type. */
public final class ThemeSpec {
    public static final int VERSION = 1;
    public final String id;
    public final String displayName;
    public final int background;
    public final int panel;
    public final int panelRaised;
    public final int stroke;
    public final int textPrimary;
    public final int textSecondary;
    public final int accent;
    public final int warning;
    public final int critical;
    public final int unavailable;
    public final int columns;
    public final int rows;
    public final int pageSize;
    public final boolean cutPanels;
    public final String bodyFont;
    public final String numericFont;
    public final String displayFont;
    public final String backgroundAsset;

    ThemeSpec(String id, String displayName, int background, int panel, int panelRaised,
              int stroke, int textPrimary, int textSecondary, int accent, int warning,
              int critical, int unavailable, int columns, int rows, boolean cutPanels,
              String bodyFont, String numericFont, String displayFont, String backgroundAsset) {
        if (id == null || displayName == null || columns < 1 || rows < 1) {
            throw new IllegalArgumentException("invalid theme definition");
        }
        this.id = id;
        this.displayName = displayName;
        this.background = background;
        this.panel = panel;
        this.panelRaised = panelRaised;
        this.stroke = stroke;
        this.textPrimary = textPrimary;
        this.textSecondary = textSecondary;
        this.accent = accent;
        this.warning = warning;
        this.critical = critical;
        this.unavailable = unavailable;
        this.columns = columns;
        this.rows = rows;
        this.pageSize = columns * rows;
        this.cutPanels = cutPanels;
        this.bodyFont=bodyFont;
        this.numericFont=numericFont;
        this.displayFont=displayFont;
        this.backgroundAsset=backgroundAsset;
    }

    public static ThemeSpec original() {
        return new ThemeSpec("original", "Original", 0xff040506, 0xff101315,
                0xff233b46, 0xff303638, 0xffe1edf2, 0xff91a5ae, 0xffa4d9ef,
                0xffffb547, 0xffff4059, 0xff91a5ae, 5, 4, true,
                "", "", "", "");
    }

    public static ThemeSpec auroraFocus() {
        return new ThemeSpec("aurora_focus", "Aurora Focus", 0xff071118,
                0xff10232c, 0xff173641, 0xff28505a, 0xffedf8fa, 0xff9bb9c0,
                0xff65e0d0, 0xffffc45c, 0xffff6679, 0xff8299a0, 5, 4, false,
                "", "", "", "backgrounds/dashboard.png");
    }

    static ThemeSpec fromManifest(String id,String displayName,int background,int panel,int panelRaised,
            int stroke,int textPrimary,int textSecondary,int accent,int warning,int critical,int unavailable,
            int columns,int rows,boolean cutPanels,String bodyFont,String numericFont,String displayFont,String backgroundAsset) {
        return new ThemeSpec(id,displayName,background,panel,panelRaised,stroke,textPrimary,textSecondary,
                accent,warning,critical,unavailable,columns,rows,cutPanels,bodyFont,numericFont,displayFont,backgroundAsset);
    }

    public boolean isOriginal() { return "original".equals(id); }
}
