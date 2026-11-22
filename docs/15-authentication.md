# Authentication and schema increment

This increment implements private accounts and browser sessions before CSV import. The frontend is not implemented yet; use Swagger UI or an HTTP client for these endpoints.

## Run locally

From Bash in the project root:

```bash
cd backend
uv sync
uv run alembic upgrade head
uv run uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
```

Migrations use credentials from root `.env`; Alembic does not store passwords in configuration files. Re-running `upgrade head` is safe. Do not use `downgrade` on data you want to keep. Restart the API after dependency or `.env` changes. No new Docker image is required.

Refresh TablePlus's `public` schema to see `users`, `account_sessions`, `recording_sessions`, `heart_rate_readings`, `import_previews`, plus supporting `auth_rate_limits` and `alembic_version` tables. Creating recording tables does not implement import endpoints yet.

`/health` verifies the process. `/ready` verifies database connectivity and migration revision `0001_initial`; it returns 503 if the migration has not run or PostgreSQL is unavailable.

## Endpoints

| Endpoint | Behaviour |
| --- | --- |
| POST /api/auth/register | Create an account; no automatic login; 201 success, 409 duplicate email |
| POST /api/auth/login | Check credentials; issue a 12-hour HttpOnly cookie and return user + CSRF token |
| GET /api/auth/me | Return only the current user and that session's CSRF token; 401 if missing/expired |
| POST /api/auth/logout | Require current session, allowed Origin and X-CSRF-Token; revoke session and clear cookie |

Register/login JSON body: `email` and `password`. Email is trimmed and lowercased; password length is 12–128 characters and spaces are preserved. Validation responses omit input values so passwords cannot be echoed. Wrong-email and wrong-password login attempts return the same generic 401 response.

For browser requests, Origin must exactly match a comma-separated `ALLOWED_ORIGINS` entry; defaults are `http://127.0.0.1:8000,http://localhost:8000`. An arbitrary Origin supplied by a nonbrowser client is not identity proof; authentication still requires credentials/cookies. No permissive CORS is enabled. Future Vite proxy configuration must add its actual browser origin explicitly.

Use http://127.0.0.1:8000/docs to register and log in. The browser stores the HttpOnly cookie. Copy the returned CSRF token into the logout endpoint's `X-CSRF-Token` header. `/me` returns it again if needed. Do not paste session tokens into URLs or logs.

Raw session tokens are never stored in PostgreSQL. Only SHA-256 digests are stored; CSRF tokens are derived from the raw session token and their digests are checked with constant-time comparison. Passwords use Argon2id via argon2-cffi. Logging in again rotates the browser's existing session, and logout invalidates it server-side. Cookie defaults are HttpOnly and SameSite=Lax. `COOKIE_SECURE=false` is for local HTTP only; HTTPS deployment requires true.

Persistent throttling permits five failed logins per account/IP in 15 minutes and ten registration requests per IP in ten minutes. Database advisory locks coordinate counters between API workers. Successful login resets its failure counter. Requests rejected before credential validation do not consume those counters. The later CSV maintenance task removes old counters and expired sessions. A general HTTP/IP abuse limiter remains future operating work; these targeted counters are not complete denial-of-service protection. No password-reset or email-verification flow is included yet.

## Schema decisions

UUID account/recording IDs, timestamp-with-time-zone values, unique normalised email, indexed foreign keys, owner/file-digest uniqueness, unique timestamps per recording and BPM bounds are enforced in PostgreSQL. Preview lifecycle checks distinguish pending payloads from consumed previews. Deleting recordings cascades to readings and clears preview references. Future import endpoints must also enforce owner-scoped operations, expiry, row limits and atomic commit; constraints alone do not authorise requests.

The supporting rate-limit table extends the initial five-table design for multi-worker-safe auth. Migrations are versioned and the generated schema SQL can be reviewed without applying it:

```bash
uv run alembic upgrade head --sql
uv run alembic check
```

## Verification

Unit checks run without a live database. Integration checks opt in with `HR_RUN_DB_TESTS=1` and target the configured database name plus `_test`, never the application database. Create that separate test database before running them. Integration tests delete test data only in that explicitly verified database.

```bash
uv run python -m unittest discover -s tests -v
HR_RUN_DB_TESTS=1 uv run python -m unittest discover -s tests -v
```

Tests cover password hashing, normalisation/duplicates, session cookies/rotation/expiry, generic login errors, persistent throttling, CSRF/origin rejection, logout revocation, account isolation and database constraints. This document does not imply a completed frontend or CSV workflow.

## Local verification record — 2026-11-15

- Applied revision `0001_initial` to the existing local `heart_rate_monitor` database using its existing `postgres:16-alpine` container. No image pull or volume replacement occurred.
- `alembic check` found no model/schema drift.
- Live HTTP `/health` and `/ready` returned 200 with the expected status bodies; `/openapi.json` lists all four auth routes.
- Integration tests ran against separate `heart_rate_monitor_test`; no test accounts were inserted in the application database.
- Final test run: **15 tests passed** (7 PostgreSQL integration cases and 8 readiness/configuration unit cases), in 5.132 seconds. The readiness unit suite includes a migration-revision mismatch check.
- Ruff lint passed; formatting checks were applied. Offline locked dependency sync succeeded. No coverage percentage or peer review is claimed.
