# Wearable Heart-Rate Data Viewer

**Project:** SP-332 | **Course:** CSC403 Software Engineering  
**Student:** Ambali Ridwan Oladele | **Matric number:** U23CS1019

A planned web application for importing recorded wearable heart-rate data, exploring charts and viewing recording summaries. The selected backend stack is **Python / FastAPI + PostgreSQL**, matching the workbook. The frontend will use **React with the Astryx design system**, as requested by the student. PostgreSQL will run locally in Docker. Direct Bluetooth, live readings and Android companion integration are future work.

**Current stage:** initial API foundation. `backend/` contains a FastAPI status endpoint; `frontend/` reserves the React/Astryx structure. `compose.yaml` reuses an explicitly selected local PostgreSQL image with pulling disabled. No application tables, authentication, imports or frontend screens exist yet. Git is initialised; no commits or remote changes were made for this setup.

Start the API in PowerShell:

```bash
cd backend
uv sync
uv run uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
```

Then open http://127.0.0.1:8000/ or http://127.0.0.1:8000/health for `{"status":"OK"}`, and http://127.0.0.1:8000/docs for the API reference. Check http://127.0.0.1:8000/ready to verify PostgreSQL connectivity. The student reports successful local API and PostgreSQL connection. The coding session's Docker/network access remains restricted.

See the [backend run instructions](backend/README.md). The local configuration selects the existing `postgres:16-alpine` image; Compose will not download one. `/health` does not require a database; `/ready` checks database connectivity but not migrations yet.

Start with the [documentation index](docs/README.md) and [project proposal](docs/01-project-proposal.md). The original workbook remains unchanged.
