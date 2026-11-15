# CSV validation and import API

## Implemented flow

Login, upload a CSV for validation/preview, review the result, then confirm the preview ID. Only confirmation creates a recording and its readings. The importer uses the existing schema; no new migration or PostgreSQL image is required for this increment.

| Method and path | Purpose |
| --- | --- |
| POST /api/imports/preview | Multipart file and metadata; 200 with preview ID, expiry, count, UTC range and first ten rows |
| POST /api/imports/{preview_id}/confirm | No row payload; 201 for new recording, 200 for duplicate/retry |
| DELETE /api/imports/{preview_id} | Discard an owned preview without deleting a committed recording |
| GET /api/recordings | Owned recording history; limit default 20/max 100; next_cursor for further pages |
| GET /api/recordings/{recording_id} | Owned metadata, count, range, minimum/maximum/average BPM |

New routes require a valid session cookie. Mutations additionally require an allowed `Origin` and the login response's `X-CSRF-Token`. GET routes do not require an Origin/CSRF header. API responses do not expose another owner's resource: unknown and foreign IDs return 404.

## Try it in Postman

Use the same host for login and imports (for example, `http://localhost:8000`) so Postman's cookie jar sends the existing session cookie.

1. Log in. Keep the `csrf_token` from the response; Postman should retain `hr_session` in its cookie jar.
2. POST `http://localhost:8000/api/imports/preview` with headers `Origin: http://localhost:8000` and `X-CSRF-Token: <your csrf_token>`.
3. Choose **Body → form-data**. Add the fields below. Let Postman generate the multipart Content-Type/boundary; remove a manually set `Content-Type: application/json` for this request.

| Key | Postman type | Example |
| --- | --- | --- |
| file | File | Select `backend/samples/synthetic-heart-rate.csv` |
| label | Text | Synthetic demo |
| source_type | Text | synthetic |
| device_label | Text, optional | Sample fixture |

4. A successful preview returns count 3, readings 72/75/78 and a UTC range of 08:00–08:02 on 1 November 2026. No recording exists yet.
5. Copy `preview_id`, then POST `/api/imports/<preview_id>/confirm` with the same Origin/CSRF headers and no body.
6. Expect outcome `created` and recording count 3, minimum 72, maximum 78 and average 75.0. Repeating confirmation returns `existing_recording` with HTTP 200 and no duplicated readings.
7. GET `/api/recordings` or inspect the recording ID in TablePlus. The sample is synthetic, not an ET488 capture.

Sample file: [synthetic-heart-rate.csv](../backend/samples/synthetic-heart-rate.csv).

## Validation and lifecycle

- UTF-8 CSV, optional BOM, exactly `timestamp` and `heart_rate` headers in either order; timestamps require seconds and an offset. Display/storage instants are UTC.
- Maximum 5 MiB file bytes and 10,000 data rows. The whole multipart request is separately bounded to 5 MiB + 64 KiB overhead, even without Content-Length. Oversized input returns 413 before multipart processing; the file's own byte limit is checked again independently.
- BPM is an integer from 1 through 300, a software input bound rather than a clinical interpretation. Invalid dates, offsets, fractional precision, values over five minutes in the future and duplicate UTC instants are rejected.
- Empty physical lines are ignored. Standard quoted CSV fields are supported. CSV parsing also rejects fields over Python's default field-size guard; this prevents a single enormous field from consuming disproportionate parser work. Valid canonical timestamps/BPM never approach that guard.
- Invalid files return 422 with total errors and up to 50 safe record/field/reason details. No partial preview or recording is saved.
- Valid preview payloads expire after 15 minutes. Maximum three active pending previews per owner. Preview attempts are limited to ten per account per ten minutes, backed by PostgreSQL counters.
- Confirmations lock the preview and persist recording, all readings and preview consumption in one transaction. Any persistence failure rolls everything back; a valid preview can be retried until expiry.
- SHA-256 of identical raw file bytes deduplicates within an account. Concurrent identical confirmations converge on the same recording. Existing label/source/device metadata is preserved; inspect the returned recording if duplicate upload metadata differs.
- Confirmed preview payloads are cleared using SQL NULL; only consumed metadata remains for 24 hours. Reconfirming a consumed preview returns its original recording while metadata exists. Expired known previews return 410; after cleanup they return 404. Re-uploading identical bytes can still resolve to the original recording by digest.
- The API runs cleanup at startup and every five minutes for expired previews, consumed metadata older than 24 hours, expired sessions and old rate counters. An explicit maintenance command is also available: `uv run python -m app.maintenance` from backend. Cleanup failures are logged safely and do not claim successful deletion.

The import parser interface separates canonical parsing from database transactions. The repository keeps preview and recording access owner-scoped. This implements the planned parser Strategy and Repository boundaries without claiming vendor formats are supported.

## Verification and limits

Tests run against the explicitly separate `_test` PostgreSQL database. Parser tests cover formats, UTC equivalence, input bounds and row limits. Integration tests cover preview/confirm/history, invalid input, CSRF, foreign ownership, expiry/cap/discard, cleanup, transaction rollback and concurrent duplicates. Test-client lifespans disable the production maintenance task; explicit cleanup tests use only the isolated test session.

This increment provides basic history and per-recording summaries. Date-filtered reading endpoints, chart aggregation, rename/delete recording UI and React screens are still pending. No live wearable acquisition or arbitrary vendor CSV mapping is included.

Local verification on 2026-11-15: **30 tests passed** in 9.108 seconds, covering both auth and import increments. Ruff lint/format checks passed, locked offline dependency sync succeeded and Alembic found no schema drift. Live `/ready` returned HTTP 200, and `/openapi.json` exposed all five import/history routes. No coverage percentage or peer review is claimed.

Live curl verification also passed for all five endpoints using the student's test account: preview 200, confirmation 201, history/detail 200 and preview deletion 204. The fixture produced count 3, minimum 72, maximum 78 and mean 75.0 BPM. Repeat confirmation returned 200 without adding readings; invalid CSV returned 422 with two validation errors; confirming a discarded preview returned 404. The test session was logged out. Credentials and session tokens were not saved in project files. One labelled synthetic recording was retained for inspection.
