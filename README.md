# Wearable Heart-Rate Data Viewer

**Project:** SP-332 | **Course:** CSC403 Software Engineering  
**Student:** Ambali Ridwan Oladele | **Matric number:** U23CS1019

A planned web application for importing recorded wearable heart-rate data, exploring charts and viewing recording summaries. The selected backend stack is **Python / FastAPI + PostgreSQL**, matching the workbook. The frontend will use **React with the Astryx design system**, as requested by the student. PostgreSQL will run locally in Docker. Direct Bluetooth, live readings and Android companion integration are future work.

**Current stage:** authentication and CSV import API. `backend/` contains status/readiness, private cookie sessions, CSV validation/preview/confirmation and basic recording history. `frontend/` reserves the React/Astryx structure; screens are not implemented yet. `compose.yaml` reuses the existing PostgreSQL image with pulling disabled. See [authentication](docs/15-authentication.md) and [CSV import/Postman instructions](docs/16-csv-import.md).

Start the API in PowerShell:

```bash
cd backend
uv sync
uv run alembic upgrade head
uv run uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
```

Then open http://127.0.0.1:8000/ or http://127.0.0.1:8000/health for `{"status":"OK"}`, and http://127.0.0.1:8000/docs to try authentication. Check http://127.0.0.1:8000/ready to verify PostgreSQL and migration readiness. These live endpoints and the initial migration have been verified locally.

See the [backend run instructions](backend/README.md). The local configuration selects the existing `postgres:16-alpine` image; Compose will not download one. `/health` does not require a database; `/ready` checks database connectivity and the expected migration revision.

Start with the [documentation index](docs/README.md) and [project proposal](docs/01-project-proposal.md). The original workbook remains unchanged.
