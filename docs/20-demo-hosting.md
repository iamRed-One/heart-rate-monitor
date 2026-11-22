# Free hosting for the HeaRt demonstration

Checked against provider documentation on 22 November 2026. This is a deployment guide, not evidence of a completed cloud deployment. Keep local Docker/PostgreSQL intact as the fallback.

## Recommendation and alternatives

Use **Render Free for FastAPI + Neon Free for PostgreSQL**. Keep one API process: the current live-session coordinator holds active control state in memory. Uploaded samples are persisted in PostgreSQL.

| Service | Role | Current free allowance / limitation |
|---|---|---|
| [Render](https://render.com/docs/free) | FastAPI hosting | 750 instance-hours per workspace/month. Sleeps after 15 minutes without incoming traffic; waking takes about a minute. Filesystem is ephemeral. |
| [Koyeb](https://www.koyeb.com/docs/reference/instances) | Alternative FastAPI host | One free instance: 512 MB RAM, 0.1 vCPU, 2 GB disk. Sleeps after one hour idle. Account/card verification can apply; see [pricing FAQ](https://www.koyeb.com/docs/faqs/pricing). |
| [Neon](https://neon.com/pricing) | Recommended PostgreSQL | 1 GB/project and 100 CU-hours/project/month. Scales to zero after five minutes idle. Quota exhaustion can suspend compute. |
| [Supabase](https://supabase.com/pricing) | Alternative PostgreSQL | 500 MB/project; free projects pause after a week of inactivity. Its additional auth/storage features are optional; HeaRt already has its own account API. |
| [Railway](https://docs.railway.com/pricing/free-plan) | Alternative combined hosting | $1 usage credit/month after the introductory trial; too small an allowance to promise an uninterrupted demo environment. |

Do not choose Render's free PostgreSQL for a long-lived project: it expires after 30 days. Free tiers are suitable for a rehearsed preview, not a promise of continuous availability.

## 1. Create the Neon database

Create a Free project, choose PostgreSQL 16 if available to match local development, and choose a region near the API host. In **Connect**, select the database/role and turn **connection pooling off**. Copy the direct host, database, role and password privately.

Use the direct endpoint for this version: the existing connection code sends startup `options` for statement timeouts and read-only readiness checks. Neon's transaction pooler does not accept all such options. See [connection details](https://neon.com/docs/connect/connect-from-any-app) and [unsupported startup parameters](https://neon.com/docs/connect/connection-errors).

This is a fresh cloud database. Alembic creates its schema; local accounts/readings do not appear there automatically. Register a demo account and import a labelled fixture, or plan a separate data migration. Do not reset or overwrite the local database.

## 2. Create the Render web service

Connect the GitHub repository and select the branch containing the committed changes. A local commit must be pushed before Render can see it; this session did not push automatically. Choose **Python**, **Free**, and root directory **backend**. Render runs build/start commands relative to that directory. See [FastAPI deployment](https://render.com/docs/deploy-fastapi), [monorepos](https://render.com/docs/monorepo-support), and [build commands](https://render.com/docs/deploys).

Build command:

```bash
uv sync --locked --no-dev
```

Start command (a single line):

```bash
uv run --no-sync alembic upgrade head && uv run --no-sync uvicorn app.main:app --host 0.0.0.0 --port "$PORT" --workers 1
```

Set a supported Python 3.12–3.14 patch version in Render if the build image's default falls outside `backend/pyproject.toml`. Use `/health` for the process health check; check `/ready` manually after deployment. Do not use `--reload`, multiple workers or multiple service instances for this version.

Set these environment variables in Render, not in Git. Replace every example with the actual values:

```dotenv
POSTGRES_HOST=ep-your-direct-endpoint.region.aws.neon.tech
POSTGRES_PORT=5432
POSTGRES_USER=your_neon_role
POSTGRES_PASSWORD=your_private_database_password
POSTGRES_DB=your_neon_database
PGSSLMODE=require
COOKIE_SECURE=true
ALLOWED_ORIGINS=https://your-api.onrender.com,http://127.0.0.1:5173,http://localhost:5173
```

The application currently reads the separate `POSTGRES_*` fields, not a `DATABASE_URL` variable. `PGSSLMODE=require` instructs the psycopg/libpq connections, including migrations and readiness, to require encrypted transport. Only the API and your private TablePlus connection receive database credentials.

## 3. Connect the Android application

Open **Account → Server settings** and enter `https://your-api.onrender.com` without `/api`. Sign in/register against the cloud database. The app already adds `/api` and sends the server origin. Include that exact HTTPS origin in `ALLOWED_ORIGINS`. HTTPS avoids the debug-only loopback HTTP exception and removes the need for `adb reverse`.

Bluetooth remains **watch → phone**. The phone sends saved samples to **FastAPI → Neon**. The database does not connect to the watch. A normal one-shot reading works locally; saved recordings/import/history need the server/account.

## 4. Connect the web viewer for the presentation

The browser uses relative `/api` requests. Its development proxy in `frontend/vite.config.ts` currently targets `http://127.0.0.1:8000`. For the hosted-API rehearsal, change only that proxy target to `https://your-api.onrender.com`, keep `changeOrigin: false`, and restart Vite:

```bash
cd frontend
npm run dev
```

Open the exact local origin included above, for example `http://127.0.0.1:5173`. This keeps the browser's requests and cookie same-origin while Vite forwards API traffic to Render. With `COOKIE_SECURE=true`, browser cookie acceptance over local HTTP must be checked on the actual demo browser; if it is rejected, use local HTTPS or keep the complete local API/database setup for the browser demo. Do not weaken secure cookies on the public API to work around this.

For a fully public web URL, deploy the static build behind a same-origin HTTPS reverse proxy that serves the web files and forwards `/api/*` to FastAPI. The current server rejects `Sec-Fetch-Site: cross-site` and uses `SameSite=Lax` cookies, so putting the browser and API on unrelated domains and adding CORS alone will not fix authentication. Public frontend proxy/routing is a separate deployment step; no public viewer URL is claimed here.

## 5. Connect TablePlus

Create another PostgreSQL connection named **HeaRt — Neon demo**. Use the same direct Neon host, port 5432, role, password and database. Enable SSL with mode **Require**. Keep the existing local connection separately. Open `public.recording_sessions` and `public.heart_rate_readings` to inspect saved samples; use read-only queries during the presentation.

## Demo checks

1. Open `https://your-api.onrender.com/health`: expect `{"status":"OK"}`.
2. Open `/ready`: expect `{"status":"OK","database":"connected"}` after migration. A first request can time out while the free database wakes; retry after it is awake.
3. Register/sign in on phone and browser; confirm account ownership and cookie/CSRF requests work through the chosen proxy.
4. Wear and connect the ET488; a passive connection must leave its sensor idle. Test one manual read, Stop, and a saved recording before presenting.
5. Record for 30–60 seconds, Stop, then open the stored recording on web/phone and check its rows in TablePlus.
6. Verify that refresh retains uploaded readings. Do not restart/redeploy the API during capture: active control sessions end on process restart, although uploaded rows remain.
7. Warm `/ready` shortly before your talk and rehearse the complete path. Keep a labelled recorded/synthetic CSV and the local Docker setup ready if Bluetooth or free hosting fails. Never describe a CSV replay as a live watch measurement.

Cloud deployment, database credentials and a public URL remain to be configured in the provider accounts. No hosted service was created or charged by preparing this guide.
