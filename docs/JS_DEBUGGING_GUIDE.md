# JS debugging: ADB, in-app instrumentation, and host tests

This guide separates three layers that are easy to conflate. ADB is a transport and shell/logcat tool; app instrumentation produces observations; host tests exercise code without the vehicle. They can be combined, but one does not prove the others worked.

| Layer | Current entry points | What it can establish | What it cannot establish |
|---|---|---|---|
| ADB read-only observation | `diag/js_kr/carplay_adb_probe.py`, `diag/js_kr/run_carplay_tbt_preflight.sh` | Exact target build/API; allowlisted package/service presence; retrieval of already-written allowlisted HUD event summaries when explicitly selected | Running CarPlay/TBT session, permission to use an OEM service, incoming navigation payload, HUD output, or absence of data |
| ADB logcat/sample capture | `scripts/extract_js_debug_samples.py` | Structured `JS_LOGGER` snapshots already emitted by the installed app, plus supported legacy records; offline parsing of saved input | Creating a new producer, raw CarPlay/iAP2 access, data accuracy, or vehicle behavior beyond the captured interval |
| In-app observation | `JsLogger`, `JsDiagState`, `JsHudProbe`, `HudEventLog`, diagnostics renderer and existing receivers | State and counters the app already observes, with each logger's documented fields and lifecycle | Signals for which no producer/listener exists; CarPlay TBT merely because media or route-status events appear |
| Host-only tests/previews | `python3 -m unittest discover -s tests -p 'test_*.py'`, `python3 scripts/test.py`, `tools/js_ui_preview.py` | Parser, decoder, lifecycle, renderer and synthetic UI behavior covered by those tests | Android API17 behavior, OEM permissions/services, installation, vehicle signals, CarPlay, or physical HUD rendering |

## How the layers differ

ADB does not itself observe vehicle data. It selects a device and invokes operations such as `getprop`, `pm`, `service`, `run-as`, or `logcat`. The scripts constrain which commands and output are allowed. The USB port is either attached to ADB or to the iPhone for wired CarPlay in the present setup; these procedures do not claim simultaneous access. The event-log procedure therefore disconnects/reconnects USB around the CarPlay interval and reads the app's existing private file afterward.

In-app debugging means code inside Dashboard observes a producer and either renders a status or writes a bounded summary. For example, `JsLogger` periodically serializes existing telemetry snapshots; it does not add listeners. `HudEventLog` stores a separate allowlisted event summary in app-private storage. `JsHudProbe` observes the OEM navigation-status API already present in the app; that status is not a CarPlay maneuver stream. The on-screen diagnostics page is useful only while the app is running and showing that page. ADB may later retrieve an app-produced file, so these layers can be chained.

The JS DCT client is a separate vehicle-service client, not a passive log reader. Its request loop invokes the OEM service and is gated by ignition/profile state; host tests use stubs and do not verify the real service's effects or access policy. Treat any live service operation as an explicit device test, not as a generic debugging command.

## Repository map

| Area | Files | Role |
|---|---|---|
| ADB probes | `diag/js_kr/carplay_adb_probe.py`, `run_carplay_tbt_preflight.sh` | Read-only target/inventory checks and optional allowlisted summary retrieval |
| ADB hardware/HSCAN | `hardware_inventory.py`, `hscan_capture.py` | Hardware inventory; HSCAN capture is a distinct raw vehicle-data workflow. `--try-enable` opts into a Wi-Fi state change and restoration attempt |
| App producers and stores | `apps/dashboard/src/0x1101L/java/com/veloster/dashboard/debugger/`, `DashboardActivity.java`, `MediaReceiver.java`, `JsHudProbe.java`, `JsLogger.java` | Current in-app observation, event filtering and bounded persistence |
| Host extraction | `scripts/extract_js_debug_samples.py` | ADB `logcat` sampling or offline parsing; preserves JS_LOGGER v1 and legacy-format compatibility |
| Verification | `tests/`, `scripts/test.py`, `scripts/verify_release.py` | Synthetic/host behavior, Java host programs and tracked artifact hashes |
| Operating instructions | `JS_DEBUG_CAPTURE.md`, `JS_DIAGNOSTICS.md`, `DIAGNOSTICS.md`, `diag/js_kr/README.md` | Per-tool procedure and output contracts; this page is the routing overview |

## Suggested order with one USB port

1. Run host tests and inspect the exact tool contract before connecting hardware.
2. When the head unit is on ADB, run the explicit-serial target/inventory preflight. This does not require or test a CarPlay session.
3. If an already-installed, known Dashboard build contains the bounded logger, disconnect ADB and use the USB port for iPhone CarPlay. Record human-readable maneuver start/end times separately; do not start an unapproved capture or output path.
4. End CarPlay, reconnect ADB, then explicitly retrieve `events-current` and `events-previous`. Interpret missing files or records as inconclusive. Event timestamps use elapsed monotonic time, so they do not directly align with wall-clock notes across reboot; event sampling and deduplication can also omit quiet intervals.
5. Report separately: host test result, ADB target/check result, app event evidence, and any actual vehicle/HUD observation. Do not upgrade one category into another.

The current `events` runner is a bounded private-log reader. It does not execute the full TBT gates or collect logcat. The CarPlay study and test sequence are in [the research review](CARPLAY_NAVIGATION_DASHBOARD_RESEARCH.md) and [the dated test plan](CARPLAY_TBT_TEST_PLAN_20261115.md).
