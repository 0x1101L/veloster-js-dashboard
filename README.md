# Veloster Dashboard

*from [Veloster N Dashboard](https://github.com/reikop/veloster-n-dashboard)*

**English** | [한국어](README_ko.md)

A standalone dashboard project for Veloster JS, JSN and i30 PD factory head units, with **My Apps** for opening the dashboard and installed apps. Korean and English included. This repository ships a JS trial APK. The historical JSN installer and its APKs are in the original upstream bundle; unified JS/JSN/PD installation is not yet available.

[Download](#download) · [Install](docs/INSTALL_EN.md) · [Compatibility & status](docs/STATUS.md) · [Documentation](docs/README.md)

<img src="docs/images/dashboard-overview-in-car.jpg" alt="N Dashboard overview on the Korean Veloster N factory head unit" width="480">

Owner-provided in-car photo. Missing readings display a dash. Location/camera metadata has been removed; image pixels are unchanged.

## Features

| N Dashboard | My Apps |
| --- | --- |
| F1 view and 20-card overview | Dashboard and installed-app launcher |
| Editable 6/12-slot gauges and graphs | Opt-in edge button and notification shortcut |
| RPM, speed, available temperatures and four TPMS pressures | Engineering mode, USB debugging and Android utilities |
| Media controls and artwork when available from the source | Bluetooth pairing and restart confirmation |
| Saved display layouts and Korean/English selection | Editable app list and Korean/English selection |

Vehicle telemetry uses existing receive paths without adding ECU diagnostic requests. Missing or expired readings stay unknown. Bluetooth album-art reception, exhaust temperature and e-LSD remain unresolved; wireless CarPlay and automatic updates are not implemented. [Full limitations](docs/LIMITATIONS.md)

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

Current evidence and decisions are in the [project memory index](@memory/_index.md), available through the local project memory link.

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


JS source now uses a separate copy of the PD decoder and separate JS rendering. Invalid readings retain the last valid slow-changing value with a `LAST` age label for up to 60 seconds, except ambient temperature and TPMS, which have no time expiry within an ignition session; fast-changing readings expire after 2 seconds and are not displayed as retained. These are application policies, not validated OEM timeouts. JSN and PD retain their existing behavior. This is uninstalled source; vehicle validation is pending.

When a valid JS DCT gear is received, the display combines the selector mode and number, such as D1 or M1. The JS sequential signal mapping still needs vehicle verification.

The JS overview has a DCT temperature card. The source supports one candidate TCU11 value on the left and paired values only when a matching two-value diagnostic response is supplied. No JS TCU11 HSCAN slot has been established, so the single-value CAN entry point has no live source. Diagnostic collection is disabled; values and scales require JS verification. The source also contains an `0x0a` battery-voltage candidate that requires vehicle comparison. These source changes are not in the previously distributed trial APK.

The JS overview RPM card uses the standard accent in Normal and Smart, red in Sport, and green in Eco. Unknown or expired drive mode returns to the standard accent.

<a id="source-and-tests"></a>


JS and PD shift alerts apply the configured 3,000–6,700 rpm range to the actual cue. DCT gear handling has synthetic host coverage and still needs vehicle verification.

## Development

[`apps/`](apps/) contains the two apps; [`shared/`](shared/) contains dashboard/UI/data code. [`compile-stubs/`](compile-stubs/) supplies build-only OEM declarations. [`scripts/`](scripts/) and [`tests/`](tests/) provide build, integrity and host checks. [`experimental/`](experimental/README.md) indexes separate local research; its experimental source is not included in this update.

Standalone JS hardware and HSCAN investigation tools live in [`diag/js_kr/`](diag/js_kr/README.md). The app source observes the navigation state provider and the integer in the OEM `Route_Guide_Status` broadcast. Diag distinguishes registration/waiting, raw values and errors. Sticky broadcasts may contain historical state and their sender is not authenticated. The cluster service check looks up `com.hkmc.micom.cluster` and verifies its Binder descriptor. These observations do not establish maneuver/distance reception, method permissions or physical HUD output. These new checks are not in existing APKs. See the [HUD data path investigation](docs/JS_HUD_RESEARCH.md) for artifact provenance, the music cluster API and remaining checks.

[Build and test](docs/BUILD.md) · [Research walkthrough](docs/RESEARCH.md) · [NEMESIS review](docs/NEMESIS_REVIEW.md)

The source-only [JS diagnostics page](docs/JS_DIAGNOSTICS.md) now has HUD/navigation, vehicle-signal, Wi-Fi/DCT and Bindings tabs. It separates missing, invalid, valid, held and expired signal data, with reception counts and receive/valid ages. Navigation observations include initial sticky status and bounded error reasons. Counts and service presence do not prove calibration, TBT reception or physical HUD output.

The JS source now exports those existing snapshots through versioned `JS_LOGGER` v1 records; the host extractor creates CSV, summary JSON and analysis while preserving old `JS_SAMPLE` logs. The logger records known callback/decoder sources and labels unknown provenance explicitly. It omits raw payloads, navigation destinations/coordinates, media details and device identifiers. The paired Java logger and Python parser must always be changed and verified together under the same contract version. Held values retain their last valid input source. The new trial APK includes the logger; a short device capture was extracted; signal accuracy remains unverified. [Capture contract and command](docs/JS_DEBUG_CAPTURE.md).

Project-authored code and documentation: [MIT](LICENSE). [Dependency and signing notices](NOTICE.md). The public AOSP test certificate is not a private publisher identity or an OTA key. Source builds are unsigned. OEM firmware/APKs, private logs, identifiers and signing keys are not distributed. Unofficial project; no manufacturer affiliation.

Dashboard Java sources use `apps/dashboard/src/main/java`. The Activity is in `com.veloster.dashboard`, vehicle connections in `com.veloster.dashboard.vehicle`, and views and page renderers under `com.veloster.dashboard.view`. JS, PD and JSN decoders are under `apps/dashboard/src/main/java/com/veloster/dashboard/vehicle/js` `pd` and `jsn`. The authored HUD probe and route listener use the single `src/0x1101L/java` root. The Bindings tab reports passive subscription status, callback counts and receive ages; these source changes are not in the existing APKs.
