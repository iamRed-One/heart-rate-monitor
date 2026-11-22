# Wearable acquisition and HeaRt live recording

The original Vigil showcase below is historical acquisition evidence. The current independent application is **HeaRt**, under `companion/`; see [its setup and recovery notes](../companion/README.md).

HeaRt uses the same account API, repeats measurement cycles until Stop, saves a local outbox and uploads persistent recordings to PostgreSQL. The web Live page follows these samples and can stop collection. Existing Overview/Recordings endpoints include saved live readings. The web viewer shows only the latest 300 samples; older saved samples remain accessible through recording endpoints. The companion can convert compatible full Vigil CSV exports before backend preview/confirmation. Protocol support is explicit, not universal watch compatibility.

## Original Vigil acquisition evidence

## What was verified

The connected Android phone is a Samsung SM-A055F. Vigil recognises the ET488 and connects through the Veepoo SDK (`VPOperateManager`). The existing Pulse action produced a new reading on the phone. The new opt-in showcase subsequently delivered fresh readings through the API to the React live view; browser checks saw changing BPM values and no page errors. No stored CSV rows were replayed as live readings.

The path used for this local test is:

```text
ET488 -- Bluetooth / Veepoo SDK --> Vigil on Android
      -- HTTP over adb reverse / USB --> FastAPI :8000
      -- authenticated one-second polling --> React /live
```

USB is the phone-to-computer transport in this setup. The watch still connects to the phone over Bluetooth. It does not appear as a USB CSV drive in this tested setup.

## How Vigil exports existing data

Vigil's `Exporter.kt` reads its Room database, not the watch directly. Sync or measure first; then open **Your data**, scroll to the bottom and long-press the clinic-settings footer. The existing **Export CSV / JSON to phone** action creates files beneath:

```text
/sdcard/Android/data/com.example.myapplication/files/export/
  self/<device-id>/<export-date>/heart_rate.csv
```

The ET488 export observed on this phone identifies model `ET488#5535`, firmware `03.17.01`, SDK `veepoo-2.3.87`. Ring and watch files are separated by device directory. The date in that directory is the export date: the file can contain older measurements from several days. The manifest records file counts and hashes. Exporting copies data; it does not erase the watch.

The heart-rate file has **15 columns**:

```csv
record_id,participant_id,device_id,device_model,firmware,ts_utc,tz_offset_min,device_ts_raw,source,quality,app_version,sdk_version,metric,value,unit
```

## What the two-column format means

The viewer's `timestamp,heart_rate` CSV is a **canonical heart-rate interchange format**, not the ET488's native protocol and not a representation of every device field. The current strict importer does not accept Vigil's full export directly.

| Viewer field | Vigil field | Rule |
|---|---|---|
| `timestamp` | `ts_utc` | Use the measurement's UTC timestamp, not the export-folder date. |
| `heart_rate` | `value` | Only `metric=HEART_RATE`, `unit=bpm`, whole values from 1 to 300. A value such as `68.0` is numerically integral; do not round fractional values. |
| recording device label | `device_model`, `device_id` | Keep devices separate and identify which watch produced the data. |
| provenance | remaining columns | Preserve original identity, firmware, quality, SDK and source when designing a native import profile. |

Vigil's `source` values such as `HISTORY` and `MEASUREMENT` describe **how the phone acquired a reading**. They are not the viewer's recorded/synthetic distinction: both can be real recorded data. `device_ts_raw` may be epoch milliseconds; it should not silently replace `ts_utc` without a documented conversion.

Before finalising a broader database/import schema, support explicit format profiles and retained provenance rather than accepting arbitrary columns or silently dropping information. Long exports may also exceed the present 10,000-row limit. No database schema was changed during this investigation, and no native-export compatibility is claimed yet.

## Run the temporary live showcase

1. Start the existing PostgreSQL container, FastAPI on port 8000 and Vite on port 5173.
2. Connect the phone with USB debugging and run:

   ```bash
   adb reverse tcp:8000 tcp:8000
   ```

3. Sign in to the web app, open **Live watch** and select **Start showcase**.
4. In the debug Vigil build, open **Your data → Live heart-rate showcase**. Enter `http://127.0.0.1:8000` and the 12-character code from the viewer.
5. Wear the connected watch and select **Pair and measure for 2 minutes**. Keep Vigil open. Stop on the phone to end detector use; **End showcase** on the web revokes its upload token.

Codes are one-use and expire after ten minutes. A paired upload token is write-only for that session. Viewing and ending a session require the owning browser account; browser mutations still require Origin and CSRF checks. Pairing attempts and reading rates are bounded. Server memory retains at most 300 readings per session for at most 30 minutes. A server restart ends sessions, including development auto-reloads. Use one API process for this prototype.

The heart illustration is paced by the latest BPM. **It is not an ECG, pulse waveform or exact beat timing.** After ten seconds without a new reading, or a connection error, it stops animating and the last value is labelled stale. Reduced motion also keeps it still. Live showcase readings are not yet saved as web recordings.

## Keep the Vigil changes removable

The temporary additions carry `TEMPORARY_HEART_RATE_SHOWCASE` markers. The entry is debug-only. Follow `C:/Users/ambal/Desktop/Gigs/MyApplication/docs/TEMPORARY_HEART_RATE_SHOWCASE.md` to remove the two new files and the four marked integration sites. The final dedicated sender does not write showcase samples to Vigil's database or clinic outbox. Its normal Pulse, history-sync and export paths retain their original behaviour. Existing clinic settings, manifest, network configuration and database schema were not changed.
