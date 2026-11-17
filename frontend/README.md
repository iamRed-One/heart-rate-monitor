# Heart Rate frontend

React, TypeScript and Vite with Astryx 0.7.16 and a local `field-journal` theme. The home page introduces CSV imports; cookie authentication opens the dashboard, import workflow and recording collection.

## Run locally

Start PostgreSQL and FastAPI on `127.0.0.1:8000`, then run from this folder:

```bash
npm ci
npm run dev
```

Open **http://127.0.0.1:5173**. Vite forwards `/api` to FastAPI while preserving the browser Origin. The root `.env` must allow `http://127.0.0.1:5173` and `http://localhost:5173` in `ALLOWED_ORIGINS`. The client handles cookies and CSRF tokens; it does not store passwords or session tokens in localStorage.

## Build and check

```bash
npm run build
npm run astryx -- doctor --built dist
```

`npm run test:e2e` uses the installed Google Chrome and the running API. Public-page and focus-regression checks run without credentials. Set `HR_TEST_EMAIL` and `HR_TEST_PASSWORD` to enable the real import/rename/delete workflow with a local test account; it removes only its own temporary recording. Keep these values out of source control.

Manrope is self-hosted through `@fontsource-variable/manrope`. Astryx's doctor may warn that the theme descriptor does not declare this application-level font import. No external font service is required. Astryx's standalone theme preview currently fails to load CSS in its Node renderer; verify changes in the running application.

## Pages

- `/`: redirects to sign-in. The home page is preserved at `/home` for later use.
- `/login`, `/register`: full-height image/form split without a navbar or card. Labelled fields, password visibility and validation; the form fills the phone view.
- `/dashboard`: recording/date filters, summaries, exact or aggregated chart and paginated readings.
- `/import`: CSV validation, source labels, preview expiry and explicit confirmation/discard.
- `/recordings`: paginated collection, rename and confirmed deletion.
- `/recordings/:id`: one recording's chart and readings.
- `/live`: temporary ET488/Vigil showcase with one-use pairing, fresh/stale feedback and a BPM-paced heart illustration. See [watch and companion findings](../docs/18-watch-companion-live.md).

Dates use UTC. Chart lines stop across recordings and gaps longer than five minutes. Aggregated charts show bucket ranges and averages; the table keeps exact readings available. Wide tables scroll horizontally and can receive keyboard focus. Motion is limited to control feedback, the auth panel and Astryx dialogs, with reduced-motion support.

The sidebar can collapse to an icon rail and remembers that preference. Its 240 ms transition uses FLIP translations and a clipped background, without stretching text or animating layout widths. Reduced motion uses immediate positioning. The live heart is illustrative, follows fresh BPM updates and stops when data becomes stale.

## Design and assets

The implementation adapts Astryx's login-split, dashboard, documentation, form-flow and table-page scaffolds. Reusable components live in `src/components`; API handling lives in `src/lib/api.ts`. Theme tokens live in `themes/field-journal/field-journal.theme.ts`. Recompile changes with:

```bash
npm run astryx -- experimental theme compile themes/field-journal/field-journal.theme.ts
npm run astryx -- theme add field-journal
```

Do not edit generated `src/themes` files. The watch photograph is illustrative generated artwork, not a claim of ET488 connectivity. Its adjacent JSON records the exact prompt.
