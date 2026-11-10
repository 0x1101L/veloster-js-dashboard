# JS telemetry capture handoff

This workflow prepares structured JS values for a separate debugging agent. It does not operate the vehicle, install an APK, or change ECU/CAN state. Run device capture only when that agent has an explicitly authorized, identified JS head unit and a diagnostic build.

## Data contract

The extractor expects one line per signal snapshot in the app log, with this marker and exact key set:

    JS_SAMPLE t_ms=123456 profile=js_kr build=eng.vmadmin.20260114.090656 ign=true signal=rpm source=nmode value=812.25 unit=rpm valid=true age_ms=14

The current app does not emit JS_SAMPLE records; it logs counters and ages but not numeric telemetry. A diagnostic build must add low-rate structured snapshots before this extractor can produce measurements. Keep raw OEM payloads and device identifiers out of these records.

## Extract and hand off

After the debugging agent has authorization and the diagnostic app is emitting JS_SAMPLE records, either capture live from the exact connected device:

    python3 scripts/extract_js_debug_samples.py --serial YOUR_ADB_SERIAL --seconds 60 --out-dir /private/path/js-review

This checks the connected head unit's product and build, reads its uptime, and observes VehicleDash logcat for up to 600 seconds. It does not clear log buffers or alter device state. The capture starts at invocation time and ignores older buffered samples. The serial is never written to output.

For an already exported logcat file, run:

    python3 scripts/extract_js_debug_samples.py --input /private/path/js-logcat.txt --out-dir /private/path/js-review

The input-file mode is offline. Both modes check the exact JS profile/build and write `js_samples.csv`, `js_summary.json`, and `agent_review.md`. A missing marker or another profile/build is an explicit error. Do not commit raw logs or generated captures.

The debugging agent reviews the summary against the capture conditions and independent vehicle reference readings, then records only interpreted, evidence-backed findings in the [JS investigation record](../@memory/20_documents/JS_DEBUGGING.md), using the [record template](../@memory/20_documents/JS_DEBUG_RECORD_TEMPLATE.md). Update the signal matrix only when the evidence changes a signal's support or validation status. The generated draft is not itself proof of correctness.

## Evidence boundary

The extractor reports observed app samples, validity, age and basic range statistics. It does not verify units, scaling, latency, accuracy, or vehicle compatibility. It cannot replace a same-time comparison with the vehicle's own reference display. Keep driver interaction out of the capture; inspect output only after stopping safely.
