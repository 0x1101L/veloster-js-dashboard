# Veloster Dashboard

The versionCode 12 JS trial checks Binder responsiveness and displays “Binder connected”; this does not establish method permissions or HUD output. See [build status](docs/STATUS.md).

*from [Veloster N Dashboard](https://github.com/reikop/veloster-n-dashboard)*

**English** | [한국어](README_ko.md)

A standalone dashboard project for Veloster JS, JSN and i30 PD factory head units, with **My Apps** for opening the dashboard and installed apps. Korean and English included. This repository ships a JS trial APK. The historical JSN installer and its APKs are in the original upstream bundle; unified JS/JSN/PD installation is not yet available.

[Download](#download) · [Install](docs/INSTALL_EN.md) · [Compatibility & status](docs/STATUS.md) · [Documentation](docs/README.md)

<img src="docs/images/dashboard-overview-in-car.jpg" alt="N Dashboard overview on the Korean Veloster N factory head unit" width="480">

Owner-provided in-car photo. Missing readings display a dash. Location/camera metadata has been removed; image pixels are unchanged.

The renderer uses no outer margin. `layout.spacing.horizontal`, `vertical` and `gap` define spacing between top-level regions; content-specific `itemGap` values apply inside each region.

The [Astryx theme workbench](theme/readme.md) lives in `theme/`. Dependencies and caches are ignored by Git; review material and approval records live in the linked `@memory/theme_support/`. App integration follows user approval. The [v3 theme engine](docs/theme_engine_v3.md) loads split files with inheritance, supports left or top navigation, shared drawing and touch geometry, F1 placement and configurable overview grids. Existing theme IDs and the default theme are unchanged. All pages share the theme’s frame inset and region gaps. Drawing and touch use the same bounds, and text and circles keep their proportions. Settings → UI settings → Visible pages saves the visibility of each of the five page entries for both navigation positions; settings and back remain accessible. On JS, open [Diagnostics](docs/JS_DIAGNOSTICS.md) from Settings, even if Overview is hidden. The left clock stays at the top and back at the bottom, with one continuous screen background. Media buttons and the music icon are centered within their containers.

Settings → UI settings → Top menu display lets you choose Theme default, Text, or Icons for the top navigation. Theme default follows each theme’s `shell.menuPresentation`, and overrides are saved per theme. Left navigation always uses icons. The theme picker includes **Liquid Glass**, **Liquid Glass (Flat)**, and **Liquid Glass (Optics)**. Liquid Glass enables the left navigation dock by default, which Flat and Optics inherit. Flat changes the surface material; Optics adds blurred containers. Their bottom media bars and wide players show media controls on a shared background without individual button backgrounds. On standard displays, F1 uses the same shared media footer and expanded media panel as other pages; it has no F1-specific media bar or player. All three share the wallpaper options, fonts, and content layout. Their left menu is 64 logical units wide and uses rounded square buttons. The `flat` menu uses the rounded outline and label to show selection, without a separate underline. Their top menu uses 8 logical units above and below and 16 on each side. The F1 grid frame has a base inset of 16, halved next to the left menu and right media region; the wide media card retains 16 on all sides. The Overview uses the same 5×4 grid and card order as Original, with no straight accent lines above the cards. Every bundled theme separates neighboring F1 cards using its grid spacing and divides the available width equally among speed, gear, and engine RPM after subtracting the two column gaps. Original uses 16 horizontal and 12 vertical logical units. This also applies when F1 and media share the screen. See the [CarPlay reference review](docs/f1_2_carplay_reference_review.md).

**Material Auto** is a standalone bundled theme in the normal theme picker. It uses the shared 5×4 Overview, a left dock, dark Material surfaces and a selectable accent palette. [800×480 Overview](docs/theme-previews/material-auto/js-0-received-ko-material_auto_cowork.png) · [1920×720 Overview with media](docs/theme-previews/material-auto/cn7-0-received-ko-material_auto_cowork.png) · [Point color settings](docs/theme-previews/material-auto/material-auto-accent-palette.png) · [all previews](docs/theme-previews/material-auto/README.md). These are Java2D host previews with synthetic data, not screenshots from an installed head unit.

All three Liquid Glass themes use squircle corners across the display frame, cards, buttons, tiles, and artwork. Each component keeps its own radius; the display frame uses 24 units at 800×480. Themes can also choose circular corners. The mask leaves layout and touch targets in place.

Swipe vertically in the settings page content to move its separate list sections or wallpaper grid together; there is no visible scrollbar. Settings uses smaller text and a little more space around each group. In F1 settings, the target RPM list opens at the saved value; tap Apply to save a new value. On standard displays, the bottom media bar and its controls are available on every page. Tap its artwork or music note to open or close the common media panel on the current page. UI settings offers Always show (default), Always hide and Only while playing, plus media notifications, media controls visibility, and icon (default) or text labels. Track change notifications appear only with Always hide and notifications enabled. Wide displays keep the right player on every page; media controls settings remain configurable. [Behavior and verification limits](docs/f1_media_bar.md).

Settings → UI settings groups theme selection, menu layout, visible pages, and **Top menu display**. Choose Theme default, Text, or Icons for the top navigation bar; overrides are stored per theme. Left navigation always uses icons. Canvas vector icons render at 80% size. In the top menu, icons use equal button widths except for settings, and the back button is shorter. In the left menu, page, settings, and back buttons are all square. The clock has its own space. Menu layout can follow the theme, appear at the top or appear on the left; unsupported positions fall back to the theme default. Theme engine sources and bundled assets live under `apps/dashboard/src/0x1101L/`, while the packaged asset path remains `themes/`. Host checks do not establish Android or vehicle behavior.

When the menu is on the left, Settings → UI settings → **Left menu dock**, directly below **Menu layout**, offers **Theme default**, **On**, and **Off**. The dock replaces individual button fills and borders with a shared surface in the current theme's style, keeps the selected button highlighted, and makes the clock 10% smaller.

Overview and F1 share panel drawing with the navigation buttons through `ThemeSurface`. Screens retain their own data and layout, and `DashboardPage` owns navigation hit bounds.

Standard Overview cards use base sizes 12 for titles, 24 for values, and 10 for units. Themes can adjust unit position and element spacing in `layouts/overview.json`. Overall region spacing comes from `layout.spacing`; repeated card and row spacing belongs to the area's `itemGap` settings. Special cards retain [separate style definitions](docs/theme_engine_v3.md#전체-조망-카드-내부).

The [theme engine](docs/theme_engine_v3.md#역할별-표면-재질) reads surface styles, card top lines, navigation underlines and button fills from each theme's `components.json`. Themes can opt into a shared left dock, grouped media controls and cached blur inside containers. `colors.json` defines the palette and full screen backdrop. The backdrop base and gradient stops must be opaque; container fills can be translucent. Every overview theme uses a fixed 5×4 single screen with no previous/next pager.

Themes using Glass offer five transparency and five blur levels in Settings → UI settings → Theme settings → Liquid Glass, plus highlight intensity. Reduced transparency disables blur; other levels respect each surface's configured blur strength. Preferences survive theme changes and view recreation.

F1 uses matching value text sizes in the left and right slots regardless of whether they show speed, RPM or gear; the center slot keeps its larger emphasis. If either side needs to fit a long value, both use the smaller fitting size. F1 display options are shared across themes. Settings → F1 settings offers independent continuous/segmented pedal and brake bars, plus all six left-to-right speed/RPM/gear orders. Settings editors use the same page layout. [Behavior and CarPlay background research](docs/common_display_settings.md).


## Features

| N Dashboard | My Apps |
| --- | --- |
| F1 view and 20-card overview | Dashboard and installed-app launcher |
| Editable 6/12-slot gauges and graphs | Opt-in edge button and notification shortcut |
| RPM, speed, available temperatures and four TPMS pressures | Engineering mode, USB debugging and Android utilities |
| Media controls and artwork when available from the source | Bluetooth pairing and restart confirmation |
| Saved display layouts and Korean/English selection | Editable app list and Korean/English selection |

Vehicle telemetry uses existing receive paths without adding ECU diagnostic requests. Readings without a valid sample stay unknown. Bluetooth album-art reception, exhaust temperature and e-LSD remain unresolved; wireless CarPlay and automatic updates are not implemented. [Full limitations](docs/LIMITATIONS.md)

## Source profiles and references

| Vehicle | Codename | `Build.PRODUCT` | Head-unit software version |
| --- | --- | --- | --- |
| Veloster N | JSN | `jsn_kr` | SOP V141 · 2026-01-14 · STD_H |
| Veloster | JS | `js_kr` | SOP V141 · 2026-01-14 · STD_H |
| i30 | PD | `pd_kr` | SOP V141 · 2026-01-14 · STD_H |

The readable version above is abbreviated from the model-specific `*.KOR.SOP.V141.260114.STD_H` software identifier. Source profile gates use the exact incremental build values recorded in the project notes. JS is labeled **Veloster (WIP)** until its dashboard work is complete. These source branches have host/static verification only; they are not an installer compatibility claim.

- **JSN app baseline:** [reikop/veloster-n-dashboard](https://github.com/reikop/veloster-n-dashboard).
- **PD profile and signal-table reference (PD-1):** [Linuchan5468/i30-pd-dashboard](https://github.com/Linuchan5468/i30-pd-dashboard). Its PD decoder and signal table are the starting reference for the JS decoder.
- **JS vehicle identification:** owner-provided head-unit photo and the matching local firmware catalog; firmware binaries are not included in this repository.

Current repository status and shared instructions are in the [documentation index](docs/README.md) and [verification status](docs/STATUS.md). Detailed vehicle and firmware evidence lives in the connected private project-memory workspace and is intentionally not included in this repository.

A JS trial-build runtime crash caused by reverse lookup of a PD-baseline overview signal remapping has been fixed. The versionCode 7 candidate was installed on the matching JS head unit and remained active during a brief launch check; longer operation and signal accuracy remain unverified.

## Compatibility

The existing guarded installer supports **Korean Veloster N JSN**, product `jsn_kr`, **Android 4.2.2 / API 17**, incremental build `eng.vmadmin.20250902.081552` (`250902`, `STD_H`). The dashboard requires the factory `automotive` library. JS and PD source profiles do not yet mean their devices can be installed or used safely. Other models, regions, firmware and ordinary Android devices are not validated.

**2026-10-27:** N Dashboard v6 and My Apps v10 were installed and their APKs read back with matching hashes. The owner confirmed reopening My Apps from the HOME edge button. **Restoration after full power-off and extended operation remain unverified.** Later private experimental builds are separate from these releases. [Evidence and current status](docs/STATUS.md)

## Download

**[Download the complete v2026.10.26-i18n ZIP](https://github.com/reikop/veloster-n-dashboard/releases/download/v2026.10.26-i18n/veloster-n-dashboard-v2026.10.26-i18n.zip)** — both signed apps, installer and guides. This remains a prerelease.

| Current APK | Version |
| --- | --- |
| [JS Dashboard trial with JS_LOGGER](apks/JS-Dashboard-build-20261113-031241.apk) | versionCode 11 · 1.5-i18n · installed/readback and logger capture verified |
| [Previous JS Dashboard trial](apks/JS-Dashboard-build-20261112-184552.apk) | versionCode 10 · 1.5-i18n · installed/readback and brief runtime observation |
| [My Apps](apks/My-Apps-v10-API17.apk) | v10 · 1.9-i18n |

Use the original upstream ZIP for JSN installation. The JS trial APK is separate from that installer. Copying an APK onto an SD card does not install it. The dated ZIP retains its original documentation; this repository records later verification. [Older versions and release history](docs/RELEASE.md) · [Hashes and package identities](release.json)

## Quick start

Install and operate the screen while parked. Initial installation requires Python 3.9+ and a working [ADB connection](docs/ADB_EN.md).

1. Extract the complete download and open a terminal in its project directory.
2. Verify the files, then install using the device serial shown by `adb devices -l`:

   ```sh
   python3 scripts/verify_release.py
   python3 scripts/install.py --serial YOUR_DEVICE_SERIAL --parked
   ```

3. In My Apps, choose **Quick entry → Floating + notification → Save**.
4. Return to HOME and check **edge button → My Apps → N Dashboard** before disconnecting.
5. Separately verify reopening after a full shutdown/restart on your vehicle.

The installer makes fresh APK backups and verifies installed bytes. Use `--check` instead of `--parked` for inspection/backups without installing or launching. An accepted launch request does not confirm the screen appeared. The shortcut defaults to OFF; factory menus are not modified. [Installation and recovery](docs/INSTALL_EN.md) · [Quick-entry troubleshooting](docs/QUICK_ENTRY.md)

Once installed, normal use does not require a PC. If neither the edge button nor notification is accessible, see the [optional, exact-build OEM menu procedure](docs/MENU_ENTRY.md).

## Documentation

| Guide | English | 한국어 |
| --- | --- | --- |
| 01 · Compatibility and verification | [Status](docs/STATUS.md) | [확인 범위](docs/STATUS.md#한국어) |
| 02 · ADB and USB connection | [ADB](docs/ADB_EN.md) | [ADB 연결](docs/ADB.md) |
| 03 · Install, update and recover | [Install](docs/INSTALL_EN.md) | [설치·복귀](docs/INSTALL.md) |
| 04 · My Apps and shortcuts | [My Apps](docs/MY_APPS.md) · [Quick entry](docs/QUICK_ENTRY.md) | [내 앱](docs/MY_APPS_ko.md) · [바로가기](docs/QUICK_ENTRY_ko.md) |
| 05 · Language settings | [Languages](docs/LANGUAGES.md) | [언어](docs/LANGUAGES.md#한국어) |
| 06 · Development and research | [Documentation index](docs/README.md#development-and-research) | [개발·연구 색인](docs/README.md#development-and-research) |


JS uses a separate decoder and renderer. After the first valid reading, dashboard numbers keep showing the last valid value without an age label, even if later input is invalid or expires. An ignition session reset clears these values; HSCAN disconnect also clears TPMS. Freshness rules for calculations, diagnostics, and graph history still apply. JSN and PD retain their existing behavior. This source change has not been verified on a vehicle.

When a valid JS DCT gear is received, the display combines the selector mode and number, such as D1 or M1. The JS sequential signal mapping still needs vehicle verification.

The JS overview has a DCT temperature card. The source supports one candidate TCU11 value on the left and paired values only when a matching two-value diagnostic response is supplied. No JS TCU11 HSCAN slot has been established, so the single-value CAN entry point has no live source. The current source attempts a diagnostic request while JS ignition is on; actual transport, values and scales require JS verification. The source also contains an `0x0a` battery-voltage candidate that requires vehicle comparison. These source changes are not in the previously distributed trial APK.

The JS overview RPM card uses the standard accent in Normal and Smart, red in Sport, and green in Eco. Unknown or expired drive mode returns to the standard accent.

<a id="source-and-tests"></a>


JS and PD shift alerts apply the configured 3,000–6,700 rpm range to the actual cue. DCT gear handling has synthetic host coverage and still needs vehicle verification.

## Development

[`apps/`](apps/) contains the two apps; [`shared/`](shared/) contains dashboard/UI/data code. [`compile-stubs/`](compile-stubs/) supplies build-only OEM declarations. [`scripts/`](scripts/) and [`tests/`](tests/) provide build, integrity and host checks. [`experimental/`](experimental/README.md) indexes separate local research; its experimental source is not included in this update.

Standalone JS hardware and HSCAN investigation tools live in [`diag/js_kr/`](diag/js_kr/README.md). The app source observes the navigation state provider and the integer in the OEM `Route_Guide_Status` broadcast. Diag distinguishes registration/waiting, raw values and errors. Sticky broadcasts may contain historical state and their sender is not authenticated. The cluster service check looks up `com.hkmc.micom.cluster` and verifies its Binder descriptor. These observations do not establish maneuver/distance reception, method permissions or physical HUD output. These new checks are not in existing APKs. See the [HUD data path investigation](docs/JS_HUD_RESEARCH.md) for artifact provenance, the music cluster API and remaining checks.

[Build and test](docs/BUILD.md) · [Research walkthrough](docs/RESEARCH.md) · [NEMESIS review](docs/NEMESIS_REVIEW.md)

Run `python3 tools/js_ui_preview.py` for the interactive desktop preview. Select **Avante (Elantra) CN7** to render and capture a **10.25-inch, 1920 × 720** frame with sample data. The theme background fills the display, the top menu spans the full width, and the content and persistent right-side media player share the space below in a five-to-two ratio. F1 cards reflow to use the available content width. The player aligns the artwork and track information, shows progress when the source supplies it, and gives play/pause the largest button. Select the received state for a sample track and cover. This preset is for display testing. Use `--check` to verify render sizes without opening a window.

The desktop preview saves dashboard theme, language, display and gauge/graph settings, plus the selected vehicle preset and sample state, under `~/Library/Application Support/JS Dashboard Preview/preferences/`. Preferences are isolated by checkout path, so opening the same checkout restores them; a different checkout starts with its own defaults. These files are separate from Android app settings.

Use the preview's **Refresh** button or `R` to recompile the current Java sources and restart the preview against the current theme assets and JSON files. Saved user preferences are retained. If compilation fails, the current preview stays open and shows the error.

The separate layout and theme editor runs with `python3 tools/layout_editor.py` or the macOS launcher at `dist/JS Dashboard Layout Editor.app`, built with `sh tools/build_layout_editor_app.sh` (Python 3 and a JDK are required). It hosts the production `DashboardView` in a Swing canvas, so JSON edits update the live renderer and mouse, drag, and wheel input follow the dashboard view's handlers. Drag the split handles to resize the navigation and inspector panels. Their widths are remembered, and the live canvas keeps its minimum width. The window enforces a usable minimum size. Choose Page or Theme Settings in the first editor dropdown. The second dropdown lists only the documents in that area, and the editor remembers your last selection in each. In Theme Settings → Screen Shell → Left Menu you can change the sidebar width and padding. In Page → Overview → Shared Cards you can change each edge's padding. **App Theme Settings** opens the dashboard's interactive theme and wallpaper settings in the canvas; it does not write theme JSON. Enter and the step buttons apply a validated draft to the live canvas. The inspector distinguishes theme values, inherited values, and renderer defaults. Reset removes the local override. **Save** writes changes for the selected theme; inherited objects stay sparse, and arrays are replaced as a unit. Role edits are checked against the app-bundle role schemas and Java theme parser. `python3 tools/layout_editor.py --check` compares the previewer and editor across every catalog theme, supported screen, vehicle profile, sample state, and language, then verifies a live edit, invalid-value rejection, and source isolation. Update both tools and their regression checks when the renderer contract changes.

The source-only [JS diagnostics page](docs/JS_DIAGNOSTICS.md) has five categories: HUD/navigation, CarPlay, vehicle, connectivity and Bindings. The vehicle page shows vehicle signals and DCT together; the connectivity page shows Wi-Fi and Bluetooth together. Bluetooth requests protected `READ_LOGS`, reads only the bounded `bt_vendor` logcat tag and displays the Broadcom controller identification, selected HCD filename and firmware initialization result. Whether the vehicle grants this permission remains unverified. This is the latest HAL initialization record, not proof of the currently active controller.

The JS source now exports those existing snapshots through versioned `JS_LOGGER` v1 records; the host extractor creates CSV, summary JSON and analysis while preserving old `JS_SAMPLE` logs. The logger records known callback/decoder sources and labels unknown provenance explicitly. It omits raw payloads, navigation destinations/coordinates, media details and device identifiers. The paired Java logger and Python parser must always be changed and verified together under the same contract version. Held values retain their last valid input source. The new trial APK includes the logger; a short device capture was extracted; signal accuracy remains unverified. [Capture contract and command](docs/JS_DEBUG_CAPTURE.md).

Project-authored code and documentation: [MIT](LICENSE). [Dependency and signing notices](NOTICE.md). The public AOSP test certificate is not a private publisher identity or an OTA key. Source builds are unsigned. OEM firmware/APKs, private logs, identifiers and signing keys are not distributed. Unofficial project; no manufacturer affiliation.

Dashboard Java sources use `apps/dashboard/src/main/java`. The Activity is in `com.veloster.dashboard`, vehicle connections in `com.veloster.dashboard.vehicle`, and views and page renderers under `com.veloster.dashboard.view`. JS, PD and JSN decoders are under `apps/dashboard/src/main/java/com/veloster/dashboard/vehicle/js` `pd` and `jsn`. The authored HUD/Bluetooth probes and route listener use the single `src/0x1101L/java` root. Bluetooth log access and in-vehicle output remain unverified; these source changes are not in the existing APKs.

For the JS HUD observation event log, retrieval, and limitations, see [HUD research](docs/JS_HUD_RESEARCH.md). This log does not establish camera data reception or HUD output.

The JS diagnostics CarPlay tab displays existing media status and provides a CPRAW1 import action that validates and stores the unchanged capture in a dedicated app-private directory, separate from DCT/HSCAN and HUD event summaries. The candidate native iAP2 hook, API17 file picker, and actual vehicle capture remain unverified. See [HUD research](docs/JS_HUD_RESEARCH.md).

CarPlay media diagnostic events are recorded without track or artist text. On JS, media observation remains active during Activity pause and stops on destruction; actual CarPlay background behavior remains unverified.

The JS CarPlay tab includes an opt-in delayed screen access probe that does not store screen pixels. It tests capture availability, not navigation reception or HUD output. See [HUD research](docs/JS_HUD_RESEARCH.md).

In the JS `Vehicle > DCT` diagnostic screen, use the transmission settings button to select and store DCT or MT. The choice persists across ignition and app restarts and can be changed from the same button. Clearing app data or reinstalling may remove it. Before selection the displayed trial estimate is unverified. Existing diagnostic requests for temperature remain separate.

Settings → UI settings → Theme settings → Wallpaper shows the wallpaper thumbnails declared by every theme. A chosen wallpaper persists across theme changes; **Theme default** restores the active theme's bundled background. F1-2 defaults to Blue and also offers Red, Green and Mono.
