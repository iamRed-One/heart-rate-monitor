# API foundation

The root endpoint and `/health` return HTTP 200 with `{"status":"OK"}`. `/docs` provides interactive OpenAPI documentation. `/ready` verifies PostgreSQL connectivity and migration revision `0001_initial`: success returns HTTP 200 with `{"status":"OK","database":"connected"}`; failure returns HTTP 503 without credentials or internal error details.

Database settings load from the repository-root `.env` regardless of the working directory, with process environment variables taking precedence. It shares `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB` and `POSTGRES_PORT` with Compose, plus an optional `POSTGRES_HOST` (default `127.0.0.1`). The password is passed directly to the driver rather than interpolated into a URL. Connections have a three-second connection timeout and a three-second query timeout and are closed after each probe.

From the project root, enter `backend`, then run (Bash or PowerShell, requires uv):

```powershell
cd backend
uv sync
uv run alembic upgrade head
uv run uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
```

Dependencies are installed in `backend/.venv` using `uv.lock`. Press Ctrl+C to stop the API. If package downloads are blocked in the coding session, run the commands in your own terminal. No wrapper script is required.

Run the database-readiness unit checks from `backend` after installing dependencies (database integration tests are skipped unless explicitly enabled):

```powershell
uv run python -m unittest discover -s tests -v
```

These checks cover safe failures, settings and connection lifecycle using mocks. A successful live `/ready` request is still required to verify your actual PostgreSQL instance.

Registration, login, current-user lookup and logout are at `/api/auth/register`, `/api/auth/login`, `/api/auth/me` and `/api/auth/logout`. Read [the authentication guide](../docs/15-authentication.md) for browser Origin/CSRF requirements, cookie handling and isolated integration-test instructions. Password-reset, email verification, frontend screens and CSV endpoints are not included yet.

Migration sources are in `migrations/versions`; `migrations/schema.sql` is the initial schema export for review, not a substitute for Alembic revision tracking. Use `uv run alembic check` to detect model/schema drift. Migrations are never applied automatically on API startup.
