# Dashboard queries and recording management

Implemented backend increment; React screens remain pending. No database migration or new dependency was needed.

## Read endpoints

All three GET endpoints require the logged-in session cookie. They accept the same optional query parameters:

| Parameter | Meaning |
| --- | --- |
| recording_id | Restrict to one owned recording. Unknown/foreign UUID returns 404. Omit for all owned recordings. |
| start_date | Inclusive UTC calendar date, for example `2026-11-01`. |
| end_date | Inclusive UTC calendar date; implemented as an exclusive next-day boundary. |

Omitting one date leaves that side unbounded. Reversed/invalid ranges return 422. The maximum end date `9999-12-31` is rejected because its exclusive next-day boundary cannot be represented. A valid range with no data returns an empty result, not 404. Inputs and predicates are shared across readings, summary and chart.

### GET /api/readings

Returns `items` and `next_cursor`. Each item includes `id`, `recording_id`, UTC `timestamp`, `heart_rate` and recorded/synthetic `source_type`.

Default `limit` is 100 and maximum is 100. Rows are ordered by timestamp ascending, then reading ID ascending to break ties across recordings. Pass `next_cursor` as `cursor` for the next page, preserving recording/date filters. Cursors are bound to the current owner and normalised filters; malformed, foreign-scope or filter-mismatched cursors return 422. Page size may change without changing the filter scope. A cursor is not a permission token; every page still enforces ownership.

Example: `GET /api/readings?recording_id=<id>&start_date=2026-11-01&end_date=2026-11-01&limit=100`.

### GET /api/summary

Returns `count`, `minimum_bpm`, `maximum_bpm`, `average_bpm`, `start_at`, `end_at`, `latest_reading` and `source_type`.

The mean is the arithmetic mean of every matching imported reading, rounded to one decimal place. It is not time-weighted and does not derive from paginated rows or chart buckets. Source type is `recorded`, `synthetic`, `mixed` or `none`. Empty results have count 0, source `none` and null statistics/timestamps/latest reading. The newest measurement is historical and must never be presented as live. Overlapping imports with different file digests remain distinct recordings and their rows count separately, as specified in the SRS.

### GET /api/chart

Returns `mode`, full matching `count`, `source_type`, `bucket_width_seconds`, `points` and `buckets`.

- Up to 1,000 matching rows: `mode=exact`, ordered points with recording IDs, empty buckets and null bucket width.
- More than 1,000 rows: `mode=aggregated`, empty points and at most 500 nonempty equal-duration UTC buckets. Each bucket supplies its interval, first/last actual reading timestamps, count, minimum/maximum/mean BPM and distinct recording count.
- Bucket width is calculated in whole microseconds with numeric PostgreSQL arithmetic, including the latest instant without producing a 501st bucket. Multiple recordings sharing one instant collapse into one one-microsecond bucket if aggregation is needed; there is no division by zero.
- Empty results use exact mode with no points or buckets. Empty time buckets are omitted. Frontend charts must disclose aggregation and must not connect unrelated recordings or empty time gaps as if continuous measurements existed. Bucket range bars/bands can convey observed min/max without inventing measurements.

Read requests start a PostgreSQL REPEATABLE READ snapshot after authentication. Summary counts and returned points/buckets therefore describe one consistent snapshot even if another request imports or deletes data concurrently. Cursors provide stable keyset ordering but do not freeze the entire dataset across separate requests; newly inserted earlier timestamps may require refreshing the view.

## Recording mutations

Both require the session cookie, an allowed `Origin` and `X-CSRF-Token`. Use the same localhost/127.0.0.1 hostname as login so the browser or Postman sends its cookie. No client-supplied owner ID is accepted.

| Request | Success | Failure cases |
| --- | --- | --- |
| PATCH /api/recordings/{id}, JSON `{"label":"Morning recording"}` | 200 with updated metadata and summaries | 422 blank/>100-character label or extra fields; 401 no session; 403 missing/invalid CSRF or Origin; 404 unknown/foreign recording |
| DELETE /api/recordings/{id} | 204 with no body | 401/403/404 as above; failed database transaction returns safe 503 |

Labels are trimmed. Rename does not change source metadata or readings. Deletion cascades to readings and sets consumed preview recording references to NULL. Reconfirming such a preview returns 410 rather than resurrecting data. The future UI must ask for confirmation before sending DELETE; cancellation means making no mutation request. There is no user-facing undo.

## Verification — 2026-11-15

- **39 automated tests passed** in 16.871 seconds. All integration data was isolated in `heart_rate_monitor_test`.
- Dashboard cases verified exact values, empty results, inclusive UTC boundaries, tied-timestamp pagination, cursor scope/validation, mixed source labels, cross-user isolation and mutation rejection.
- A concurrent-insert test verified that an exact 1,000-point chart remains internally consistent even when another transaction inserts the 1,001st reading after the chart's statistics query.
- Forced database failures after rename/delete verified rollback. Cascade deletion and consumed-preview non-resurrection were tested.
- Ruff lint/format checks passed. No additional runtime dependencies or migrations were required. No coverage percentage or peer assessment is claimed.

Live curl testing used the existing test account without saving its credentials or tokens. All five endpoints passed:

| Endpoint | Observed successful output | Observed failures |
| --- | --- | --- |
| Readings | 200; pages `[72,75]`, then `[78]` | 401 no session; 404 unknown recording; 422 invalid dates/limits/cursors and changed cursor filters |
| Summary | 200; count 3, min 72, max 78, mean 75.0, newest 78; empty-range null statistics | 401/404; 422 invalid or reversed dates |
| Chart | 200; exact 3 readings; 1,001-reading fixture aggregated to 500 buckets with count sum 1,001 | 401/404; 422 invalid or reversed dates |
| Rename | 200; trimmed label persisted | 401 no session; 403 no CSRF; 404 missing ID; 422 invalid label |
| Delete | 204; later read/repeat delete returned 404; consumed preview confirmation returned 410 | 401/403 left the recording intact |

Both temporary live fixtures were deleted. Pre-existing recordings were preserved and the test login session was logged out. Backend core API scope is now ready for React integration; deployment, CI, measured performance/coverage and final coursework evidence remain separate work.
