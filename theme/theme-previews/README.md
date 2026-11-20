# Dashboard Java2D previews

This folder contains source-rendered examples from the current Dashboard code. The screens use the Java `PitRenderer`/`JsPitRenderer`, production theme assets, and the Java2D graphics shim; generated values are synthetic. These images do not establish Android API 17, installed-app, or vehicle behavior.

- [Material Auto screen set](material-auto/README.md): overview, settings, accent palette, performance, F1, gauges, and graphs at JS/CN7 preview resolutions.
- [Performance page captures](performance/): English and Korean at 800×480 with the Original theme.

Run `python3 tools/render_theme_previews.py` from the repository root to refresh these files. The palette capture uses the production `SettingsPageRenderer` swatch-page renderer and `ThemePalette` values.
