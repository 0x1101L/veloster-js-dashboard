package com.veloster.dashboard.theme;

/** Transactional modal selection state; only apply exposes the chosen ID for persistence. */
public final class ThemeSelectionDraft {
    private final ThemeSpec original;
    private ThemeSpec draft;
    private boolean closed;

    public ThemeSelectionDraft(ThemeSpec selected){original=selected==null?ThemeSpec.original():selected;draft=original;}
    public ThemeSpec selected(){return draft;}
    public void choose(ThemeSpec value){if(closed)throw new IllegalStateException("selection already closed");draft=value==null?ThemeSpec.original():value;}
    public ThemeSpec apply(){if(closed)throw new IllegalStateException("selection already closed");closed=true;return draft;}
    public ThemeSpec cancel(){if(closed)throw new IllegalStateException("selection already closed");closed=true;return original;}
    public boolean closed(){return closed;}
}
