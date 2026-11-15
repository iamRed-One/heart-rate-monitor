# API foundation

The root endpoint and `/health` return HTTP 200 with `{"status":"OK"}`. `/docs` provides interactive OpenAPI documentation. These are process health checks. `/ready` connects to PostgreSQL and runs `SELECT 1`: success returns HTTP 200 with `{"status":"OK","database":"connected"}`; failure returns HTTP 503 without credentials or internal error details. No application tables or migrations exist yet; `/ready` currently checks connectivity only.

Database settings load from the repository-root `.env` regardless of the working directory, with process environment variables taking precedence. It shares `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB` and `POSTGRES_PORT` with Compose, plus an optional `POSTGRES_HOST` (default `127.0.0.1`). The password is passed directly to the driver rather than interpolated into a URL. Connections have a three-second connection timeout and a three-second query timeout and are closed after each probe.

From the project root, enter `backend`, then run (Bash or PowerShell, requires uv):

```powershell
cd backend
uv sync
uv run uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
```

Dependencies are installed in `backend/.venv` using `uv.lock`. Press Ctrl+C to stop the API. If package downloads are blocked in the coding session, run the commands in your own terminal. No wrapper script is required.

Run the database-readiness unit checks from `backend` after installing dependencies:

```powershell
uv run python -m unittest discover -s tests -v
```

These checks cover safe failures, settings and connection lifecycle using mocks. A successful live `/ready` request is still required to verify your actual PostgreSQL instance.
