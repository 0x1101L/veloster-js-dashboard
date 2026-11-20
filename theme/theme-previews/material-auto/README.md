# Material Auto host previews

These images were regenerated from the current Dashboard Java renderers with the repository's Java2D `android.graphics` shim. Telemetry and media are synthetic examples. They are source previews, not Android-device or vehicle captures. Recreate the complete set with `python3 tools/render_theme_previews.py` from the repository root.

## Overview

- 800×480 Veloster JS, received values: ![Material Auto JS overview](js-0-received-ko-material_auto_cowork.png)
- 1920×720 CN7 display profile, received values and media: ![Material Auto wide overview](cn7-0-received-ko-material_auto_cowork.png)
- 1920×720 CN7 display profile, waiting state: ![Material Auto wide waiting state](cn7-0-waiting-ko-material_auto_cowork.png)

The CN7 selection controls preview resolution and layout only; its fixture reuses synthetic JS values and does not validate CN7 telemetry support.

## Settings

- 800×480 JS settings root: ![Material Auto settings](js-10-received-ko-material_auto_cowork.png)
- 800×480 point-color swatches, with teal selected: ![Material Auto point color palette](material-auto-accent-palette.png)
- 1920×720 CN7 settings with received and waiting fixtures: ![CN7 settings received](cn7-10-received-ko-material_auto_cowork.png) · ![CN7 settings waiting](cn7-10-waiting-ko-material_auto_cowork.png)
- 800×480 JS settings with waiting fixture: ![JS settings waiting](js-10-waiting-ko-material_auto_cowork.png)

The point-color page uses the production `SettingsPageRenderer` swatch layout and `ThemePalette` colors. Its background is the Material Auto canvas; it is a page-level renderer capture, not a complete `DashboardView` screen.

## Other pages · 800×480 JS

- Performance: ![Material Auto performance page](js-6-received-ko-material_auto_cowork.png)
- F1: ![Material Auto F1 page](js-7-received-ko-material_auto_cowork.png)
- Gauges: ![Material Auto gauges page](js-8-received-ko-material_auto_cowork.png)
- Graphs: ![Material Auto graphs page](js-9-received-ko-material_auto_cowork.png)
