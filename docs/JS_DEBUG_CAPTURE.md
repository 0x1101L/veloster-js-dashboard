# JS telemetry capture handoff

This workflow prepares structured JS values for a separate debugging agent. It does not operate the vehicle, install an APK, or change ECU/CAN state. Run device capture only when that agent has an explicitly authorized, identified JS head unit and a diagnostic build.

## Data contract

The current source emits low-rate `JS_LOGGER` records from snapshots already retained by the existing listeners and telemetry model. It does not register another listener or receiver. The shared Java emitter and Python parser are contract version 1 and must be changed together. Each record contains profile/build, monotonic record time, kind/name, known source (or `unknown`), status, optional numeric value/unit, receive and valid timestamps/ages, and count. Logger records use this exact key set:

    JS_LOGGER v=1 profile=js_kr build=eng.vmadmin.20260114.090656 t_ms=123456 type=signal name=rpm source=nmode status=valid value=812.25 unit=rpm received_ms=123442 received_age_ms=14 valid_ms=123442 valid_age_ms=14 count=22

The logger does not emit wall-clock time, ignition state (which is not available from this snapshot API), OEM payload bytes, navigation destination content/coordinates, media metadata, or device identifiers. For valid and held signals, source identifies the last valid value; for states without a value it identifies the latest input, or `unknown` when unavailable. Receive time can describe a later invalid input than the held value. Provider change notifications use `navigation_provider`. Signal states distinguish `missing`, `valid`, `invalid`, `held`, and `expired`. Invalid, missing and expired values are `na`; held values remain explicitly marked held. Times are Android monotonic milliseconds, not event-origin timestamps.

Previously captured logs use the legacy marker and exact key set:

    JS_SAMPLE t_ms=123456 profile=js_kr build=eng.vmadmin.20260114.090656 ign=true signal=rpm source=nmode value=812.25 unit=rpm valid=true age_ms=14

The Python extractor continues to accept historical `JS_SAMPLE` records with their original fields. It writes the original `js_samples.csv`, `js_summary.json`, and `agent_review.md` for those records. `JS_LOGGER` records produce `js_logger.csv`, `js_logger_summary.json`, and `js_logger_analysis.md`. Mixed files are split into these separate output sets.

## Extract and hand off

After the debugging agent has authorization and a diagnostic app is installed, either capture live from the exact connected device:

    python3 scripts/extract_js_debug_samples.py --serial YOUR_ADB_SERIAL --seconds 60 --out-dir /private/path/js-review

This checks the connected head unit's product and build, reads its uptime, and observes VehicleDash logcat for up to 600 seconds. It does not clear log buffers or alter device state. The capture starts at invocation time and ignores older buffered samples. The serial is never written to output.

For an already exported logcat file, run:

    python3 scripts/extract_js_debug_samples.py --input /private/path/js-logcat.txt --out-dir /private/path/js-review

The input-file mode is offline. Live mode checks the exact JS product/build and reads only `JSLogger` and legacy `VehicleDash` log tags for the bounded capture. A wrong contract version, malformed record, or another profile/build is an error. Do not commit raw logs or generated captures.

The debugging agent reviews the summary against the capture conditions and independent vehicle reference readings, then records only interpreted, evidence-backed findings in the [JS investigation record](../@memory/20_documents/js_debugging.md), using the [record template](../@memory/20_documents/js_debug_record_template.md). Update the signal matrix only when the evidence changes a signal's support or validation status. The generated draft is not itself proof of correctness.

## Evidence boundary

The extractor reports observed app samples, validity, age and basic range statistics. It does not verify units, scaling, latency, accuracy, or vehicle compatibility. It cannot replace a same-time comparison with the vehicle's own reference display. Keep driver interaction out of the capture; inspect output only after stopping safely.
