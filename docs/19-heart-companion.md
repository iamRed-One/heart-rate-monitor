# HeaRt implementation and verification

HeaRt is an independent Kotlin/Compose Android application in `companion/`, installed as `com.ambali.heart`. It shares the existing FastAPI account and PostgreSQL recording APIs with the React/Astryx viewer. The earlier Vigil showcase is replaced by HeaRt's own API client, account screens, device coordinator and local SQLite outbox.

## Delivered behavior

- Sign in and registration, visible Android status bar, and four native destinations: Devices, Live, Readings and Files.
- Existing ET488/Veepoo and Q520/Yucheng adapters, plus a standard Bluetooth Heart Rate service adapter. Proprietary watch support requires a compatible protocol; this is not universal Bluetooth watch support.
- User-controlled Start/Stop, repeated SDK measurement cycles, ongoing foreground notification and automatic short-outage retries.
- Local-first sample queue; durable PostgreSQL acknowledgement; timestamp-based freshness; bounded live display; owner-scoped recording/readings access.
- Web discovery of the phone's live session, one-use pairing when starting on the web, and remote Stop.
- Canonical CSV preview/confirmation and conversion of compatible full Vigil heart-rate exports on Android. Local collection can be exported through Android's file picker.

## Verification

| Check | Result |
|---|---|
| Backend suite | 44 passed, including integration tests against a separate `_test` database. |
| Browser suite | 5 passed, including account import/view/rename/delete and live freshness/sidebar behavior. |
| Android JVM suite | 5 HeaRt-specific tests cover CSV format/error cases and unsigned 8/16-bit standard Heart Rate decoding. The five copied RecordId tests were removed with the unused legacy model. |
| Android build | Offline debug APK assembled using installed tooling and existing SDK binaries. |
| Astryx | 20 passed, no failures; two existing font-metadata warnings. |
| Physical watch | Independent HeaRt connected to ET488#5535 through Veepoo on Samsung SM-A055F. |
| Real acquisition | 110 newly captured samples saved over about 174 seconds, including a background interval. Browser observed another 151 seconds after first attaching. |
| Stop and persistence | Web Stop reached the native collector, which displayed 110 captured / 110 sent. Recording remained available in native Readings and PostgreSQL-backed detail API. |
| Android navigation | System Back from exact readings returns to the recording list; verified on device after the reviewer fix. |
| Account UI | Sign-in succeeded; registration's invalid-email validation and visible dark status-bar icons were checked on device. |

The watch recording is real device evidence; the CSV/browser fixtures are synthetic. No stored CSV was replayed as live data. Standard-service devices were tested at decoder level; Q520 and other models were not physically verified as part of this HeaRt run.

## Cleanup and limits

Removed 52 unrelated copied source files, including clinic/profile/insight UI, reminders, old Room schema, clinic uploads and temporary showcase screens, plus unused workers/resources/tests. Vigil's four temporary source modifications and two added showcase source files were removed after the independent acquisition test. Its unrelated local files and changes were preserved, and its installed data was not cleared.

Live control currently runs in one API process. Active persistent sessions renew a 30-minute inactivity lease; a server/process restart ends the control connection without deleting saved readings. Native cookies and upload tokens remain in memory, so a process restart requires sign-in and a new recording. Unsent local samples remain available for CSV recovery. This version is not a clinical monitor and does not promise uninterrupted capture through Android force-stop or firmware/sensor failures.
