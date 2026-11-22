# HeaRt Android companion

Independent Kotlin/Jetpack Compose application (`com.ambali.heart`) for the existing FastAPI/PostgreSQL account API. It installs alongside Vigil and does not read or modify Vigil's app storage.

## Screens

- Sign in / create account using the same account API as the web viewer.
- Devices: scan, connect, reconnect and disconnect supported Bluetooth devices.
- Home: explicitly read heart rate once without a server, or start/stop a saved recording with an ongoing Android notification and a local SQLite outbox. Connecting alone never starts measurement.
- History: browse account recordings and exact readings, and delete a recording after confirmation.
- Files: preview/confirm canonical CSV imports, convert compatible Vigil heart-rate exports, and export locally captured readings through Android's document picker.

## Local development

For account, import and recording features, start the repository's existing PostgreSQL container and API first. Local Bluetooth measurements do not require the API. Connect the Android phone with USB debugging, then run:

```powershell
adb reverse tcp:8000 tcp:8000
cd companion
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --offline
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
```

Use `http://127.0.0.1:8000` in the debug app. USB forwards API traffic; Bluetooth connects the wearable. Release builds require HTTPS. Set your own Android SDK location in ignored `local.properties`; do not commit machine paths. Dependencies and vendor SDKs reuse the local Vigil setup. Vendor binaries remain subject to their vendors' licenses.

Connect the device, then press Read heart rate on Home for one measurement. The sensor stops after a result, timeout or Stop. To save repeated readings, sign in and choose Save a recording. The phone can create its own recording. Alternatively, start recording on the web and enter that one-use pairing code on the phone. Open the web Live page to follow collection; Stop works from either interface.

## Device support

ET488 uses the Veepoo SDK; Q520 uses the existing Yucheng adapter. A separate adapter accepts devices advertising the standard Bluetooth Heart Rate service (180D, measurement 2A37). This does not support every watch: proprietary devices require a compatible adapter/SDK. Standard-service decoding is covered by software tests; hardware claims must identify the actual device tested.

## Saving and recovery

Each new sample is saved locally before upload. The API acknowledges persistent samples after committing them to PostgreSQL, making them available through existing recording/dashboard endpoints. Stable sample IDs and unique recording/timestamp pairs prevent retries from duplicating saved readings. Delayed uploads use capture time and do not appear as fresh measurements.

Collection repeats device measurement cycles until Stop, rather than imposing the former two-minute showcase limit. Brief connection/network interruptions retry. Android foreground-service restrictions, force-stop, sensor contact, watch firmware and process termination can still interrupt collection. This is heart rate, not ECG or clinical monitoring.

Login cookies and upload tokens stay in memory. A process/API restart requires a new live connection; already uploaded readings remain saved. Unsent samples survive in the phone's local database and can be recovered by CSV export after signing back into their account. This version does not resume upload tokens across process restarts. Local export includes the signed-in account's captured samples, up to 100,000 rows; server imports accept 10,000 rows per file.

The native converter accepts `timestamp,heart_rate` and compatible full Vigil heart-rate exports (`ts_utc,value,metric,unit,device_id`). It rejects mixed devices and non-heart-rate values. Keep original full exports for their extra provenance; canonical uploads preserve timestamp/BPM only.

## Reuse cleanup

Kept the tested SDK adapters, serial command queue, Bluetooth scanner and a small set of shared UI primitives. Removed Vigil's clinic networking, reminders, background workers, ECG screens, profile/insight pages, old Room schema, old authentication UI, temporary showcase bridge and unrelated tests/resources. HeaRt has its own API client, account screens, collection coordinator and local outbox.

The retained source namespace `com.example.myapplication` avoids rewriting vendor integration imports. The installed application ID, storage and branding are independent.

## November 22 measurement update

The crimson native redesign separates passive connection, one-shot Read heart rate and explicit continuous recording. Measurement cleanup runs on success, timeout and cancellation, and disconnect waits for the stop command. The last BPM stays visible with its age and a short crossfade; system-disabled animation is respected. Offline debug build and all 11 unit tests passed. The updated APK was installed on the Samsung phone. ET488 reconnect attempts were unsuccessful during this check, so physical sensor release still needs confirmation on a connected watch. Backend/server flows were not retested in this UI and device-control pass.

## Appearance

The app follows Android light/dark mode initially. Use the sun/moon button in the app header or account screen to switch; your choice is remembered across app restarts. Both themes share a compact flat bottom tab bar.
