# Implementation plan, API contract and coding standard

**Status:** Design only. Paths below are intended application routes, not existing files or working endpoints.

## Proposed module layout

```text
app/
  main.py                  application assembly
  config.py                validated environment settings
  routes/                  auth, imports, recordings, dashboard
  services/                import, account, recording, summary
  parsers/                 parser interface and canonical CSV
  repositories/            owner-scoped persistence
  models/                  PostgreSQL persistence models
  schemas/                 request/response validation contracts
frontend/
  src/                     React pages, Astryx components and API client
  public/                  public static assets
  package.json             pinned frontend dependencies and scripts
  package-lock.json        reproducible npm dependency resolution
migrations/                versioned database changes
tests/                     unit, integration and system fixtures
docs/                      coursework documentation
```

Application files, executable schema, dependency manifest, Docker configuration and CI will be created after documentation review. Avoid creating placeholder modules merely to imply progress.

## Page routes

`/register`, `/login`, `/dashboard`, `/imports/new`, `/recordings` and `/recordings/{id}` are React client routes. A proposed history-based router requires an SPA fallback for non-API page requests, including direct navigation to nested routes. Do not rewrite missing `/api` paths or missing assets to HTML. React redirects unauthenticated users to login; the API independently returns 401 for unauthenticated requests. State changes use POST/PATCH/DELETE endpoints with CSRF protection, never GET. During development, Vite proxies `/api`, `/health` and `/ready` to FastAPI; deployment serves frontend and API at the same origin.

## JSON endpoint contract

All user-owned resources are scoped by the authenticated account, never a client-supplied owner ID. IDs are opaque. Times return ISO 8601 UTC with `Z`. Errors contain a stable code, safe message and optional field/record errors; do not return stack traces.

| Method and route | Request | Success | Failure cases |
| --- | --- | --- | --- |
| POST /api/auth/register | email, password | 201 account identity, no automatic login | 422 invalid fields; 409 email unavailable |
| POST /api/auth/login | email, password | 200 account identity plus session cookie and CSRF token | 401 generic invalid credentials; 429 throttled |
| POST /api/auth/logout | CSRF token | 204; revoke cookie session | 401 invalid session; 403 CSRF |
| GET /api/auth/me | Session cookie | 200 identity and per-session CSRF token | 401 |
| POST /api/imports/preview | Multipart CSV, label, source_type, optional device_label; CSRF | 200 preview ID, expiry, count, UTC range, first ten rows | 413 bytes; 422 structure/row/metadata errors; 429 preview cap |
| POST /api/imports/{id}/confirm | Preview ID in path; CSRF; no row payload | 201 new recording; 200 existing recording on retry/duplicate | 404 inaccessible; 410 expired/consumed-deleted; 503 database unavailable |
| DELETE /api/imports/{id} | Owner's preview ID; CSRF | 204 discard preview | 404 inaccessible; no recording deletion |
| GET /api/recordings | Optional cursor, limit default 20 max 100 | 200 items and next_cursor | 422 invalid cursor/limit |
| GET /api/recordings/{id} | Recording ID | 200 owned metadata | 404 |
| PATCH /api/recordings/{id} | New label; CSRF | 200 updated metadata | 404; 422 |
| DELETE /api/recordings/{id} | ID; CSRF after UI confirmation | 204 | 404; rollback on failure |
| GET /api/readings | Optional recording_id, start_date, end_date, cursor; limit default 100 max 100 | 200 exact rows and next_cursor | 404 selected recording inaccessible; 422 filters |
| GET /api/summary | Same recording/date filters, no cursor | 200 count, min, max, mean, newest reading and source status | 404 / 422 |
| GET /api/chart | Same recording/date filters | 200 exact points or bounded buckets, mode and bucket width | 404 / 422 |
| GET /health | No authentication | 200 basic liveness without secrets | Service-level failure |
| GET /ready | No authentication, restricted response detail | 200 if database accessible and schema ready | 503 |

Auth defaults: email trimmed/lowercased, basic email validation, password length 12–128 characters, no password logging or silent truncation. Account sessions use cryptographically random tokens, store digests only and expire after 12 hours. Cookie is HttpOnly, SameSite=Lax and Secure under HTTPS; local HTTP development is an explicitly isolated exception. Use origin checks for register/login and CSRF tokens plus origin checks for authenticated mutations. Same-origin deployment avoids needing permissive CORS.

Preview error example shape: code `csv_validation_failed`, message `Correct the file and upload it again`, total error count, and up to 50 entries containing record number, field and reason. No original CSV contents in logs. For request errors, HTTP statuses in this contract are authoritative and must be tested.

Duplicate confirmation responses include outcome `existing_recording` and the existing metadata; newly supplied label/source/device metadata does not overwrite it. A consumed preview can be retried for its 24-hour metadata-retention period. Known expired or consumed-deleted previews return 410; IDs already removed by cleanup return 404. Both cases offer re-upload without implying successful confirmation.

Both table and chart requests use the same filter semantics described in SRS 3.4. Recording list cursors encode import timestamp and ID; reading cursors encode measurement timestamp and ID. Validate cursor scope so changing a recording/filter cannot silently reuse an incompatible cursor.

## Development and review standards

- Python naming: snake_case functions/modules, PascalCase classes and UPPER_CASE constants. Type request/response boundaries and service interfaces.
- Keep validation in reusable services/parsers, not only HTML forms. Use parameterised ORM/database queries.
- Make transaction boundaries visible. Translate database errors at a safe boundary while preserving rollback and server-side diagnostics without data content.
- Apply formatter/linter consistently. Document rationale for non-obvious decisions; avoid comments that repeat code.
- Use synthetic test fixtures with deterministic times. Inject a clock for expiry/clock-skew tests.
- Pin dependency versions, Python and Node versions after checking compatibility. Retain the frontend lockfile, check TypeScript and frontend lint/build output, and verify Astryx component accessibility in the actual UI. Record the actual PostgreSQL image tag/digest and Docker versions in the operating guide.
- Feature branches and pull requests will provide actual history required by the course. Do not fabricate review, push or merge evidence. The repository is not yet created and Git hosting remains unspecified.
- Each PR describes behaviour, requirements covered, validation and limitations. Record actual peer feedback and follow-up changes.

## First implementation increment

Prepare the environment and database, create schema migrations, then implement accounts and owner-scoped import. Deliver one valid CSV from upload through confirmation to a basic recording view before broadening the UI. Complete tests for invalid input, duplicate confirmation, rollback and cross-user access alongside that increment.
