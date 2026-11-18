package com.veloster.dashboard.theme;

/** Resolves a saved stable ID against the APK's bundled catalog. */
public final class ThemeRepository {
    public static final int SUPPORTED_SCHEMA_VERSION = ThemeSpec.VERSION;
    private final ThemeCatalog catalog;

    public ThemeRepository(ThemeCatalog catalog) {
        if (catalog == null) throw new IllegalArgumentException("catalog required");
        this.catalog = catalog;
    }

    public ThemeSpec load(String id, int schemaVersion) {
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) return catalog.resolve("original");
        return catalog.resolve(id);
    }

    public ThemeSpec preview(String id) { return catalog.resolve(id); }
    public ThemeCatalog catalog() { return catalog; }
}
