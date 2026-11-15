# User manual and technical documentation

**Status:** Pre-implementation draft. Button labels and routes describe intended behaviour and must be checked against the finished UI. There are no working setup commands yet.

## User manual

### Purpose

View recorded heart-rate readings and their descriptive summaries. You need a supported CSV file or the clearly labelled synthetic sample. Version 1 does not connect to a watch, collect background data or show live readings.

### Create an account and sign in

Open the application URL (pending deployment). Choose Create account, supply an email and a password of 12–128 characters, then sign in. Use Sign out when finished, especially on a shared computer. Your recordings are private to your account. Forgotten-password recovery is not included in the initial scope; do not present it as an available feature.

### Prepare a file

Use UTF-8 CSV with exactly `timestamp` and `heart_rate` headers. Each timestamp needs seconds and a UTC offset or `Z`; each BPM must be an integer from 1 to 300. Files may contain up to 10,000 data rows and 5 MiB. A file from another app may require conversion; arbitrary wearable exports are not automatically supported.

For example, `2026-11-01T09:00:00+01:00,72` becomes 08:00 UTC in the viewer. The allowed BPM range is a software validation rule, not an interpretation of health. See [SRS 3.3](03-srs-and-user-stories.md) for complete format rules.

### Import a recording

1. Open Import and provide a recording label and source type (recorded or synthetic); optionally enter a device label.
2. Select a CSV and choose Validate and preview.
3. If errors appear, correct the indicated CSV records and upload again. Invalid files save no recording.
4. Review count, UTC range, source and sample rows. Confirm within 15 minutes.
5. Open the resulting recording. Reconfirming or uploading identical bytes returns the existing recording rather than duplicating it.

You can cancel a preview without saving. A duplicate file keeps the existing recording's label, source and device metadata; the new upload does not overwrite them. Preview retry IDs are retained for 24 hours after confirmation. If a preview ID is no longer available, re-uploading the identical file still finds the existing recording. A different representation of overlapping data can be a separate recording; summaries over all recordings count imported rows and may therefore include overlap. Only import data you intend to include.

### Explore data

Use Dashboard for all owned recordings or select one recording. Choose From/To dates; both dates are complete UTC days. Apply filters to update summaries, chart and table together. Average is an unweighted mean of matching readings. The latest stored reading includes its timestamp and may be old.

Charts with more than 1,000 readings use disclosed time buckets; use the exact readings table for individual values. Source badges distinguish recorded, synthetic and mixed selections. No matching readings produces a no-data state; clear filters or import data as appropriate.

### Rename or delete

Open History, select a recording and choose Rename for a label of 1–100 characters. Choose Delete and read the confirmation before proceeding. Deletion removes the recording and all associated readings; cancellation changes nothing. User-facing undo is not available.

### Troubleshooting

| Message/state | Intended action |
| --- | --- |
| Missing/extra columns | Use exactly the two canonical headers |
| Invalid timestamp | Include an explicit offset, valid date/time and no more than six fractional digits |
| Duplicate timestamp | Remove repeated instants, including timestamps written with equivalent offsets |
| Invalid BPM | Supply a whole number within the documented software bound |
| File too large / too many records | Split into intentional non-overlapping sessions within limits |
| Preview expired | Upload again and confirm before expiry |
| Too many previews / requests | Discard unused previews or wait for the stated retry interval |
| Sign-in expired | Sign in again; re-upload if the preview has expired |
| Server unavailable | Retry later; do not assume the recording was saved without confirmation |

This viewer does not determine whether a reading is medically normal and is not an emergency-alert system.

## Technical manual

### Intended environment

React with the Astryx design system provides the browser UI; Python/FastAPI provides JSON endpoints. Vite/TypeScript are proposed frontend tooling. PostgreSQL runs in Docker locally with a named volume. Runtime Astryx components/theme/style dependencies, persistence, migrations and test tools will be selected and pinned during implementation. Record exact Node, Python, dependency, PostgreSQL image and Docker versions after setup. A global Astryx CLI does not replace project dependencies or their lockfile.

### Configuration inventory

Proposed settings include database URL, environment name, allowed application origin, session expiry, cookie secure flag, upload bytes/rows, preview lifetime/cap, rate limits and log level. Secrets belong in a local untracked environment file or deployment secret store, never a committed example with real values. Defaults must match the SRS; configuration changes affecting acceptance require a documented baseline revision.

### Setup procedure to complete after coding

1. Verify Docker is running and choose the PostgreSQL image/version and isolated database credentials.
2. Create the documented named volume/container with loopback-only local port exposure.
3. Create the Python environment and install pinned backend dependencies; install frontend dependencies from its committed lockfile using the documented Node version.
4. Provide local configuration, run migrations and verify readiness.
5. Start FastAPI and the React development server with API proxying, then run synthetic-data smoke checks. For deployment, build the React bundle and serve it with the API under one origin.
6. Run tests against a separate test database and record versions/results.

Exact executable commands are deliberately pending until the relevant files exist and have been verified. Starting Docker alone does not create the project database, schema or persistent volume.

### Data lifecycle and maintenance

Original files are not retained. Valid preview payloads expire after 15 minutes and are cleared on successful confirmation. Cleanup runs every five minutes and deletes expired previews and consumed preview metadata older than 24 hours. Committed recordings remain until owner deletion. Safe operation logs have a proposed seven-day test-environment retention and exclude file contents/secrets.

Inspect readiness and safe error logs when the database is unavailable. Use versioned migrations for changes; never manually alter a shared schema without recording the change. Back up before destructive migrations. Test backup restoration to an isolated database, verify row counts/constraints and only then document the procedure as verified.

### Required updates before submission

Replace planned setup steps with tested commands, add actual deployment URL and screenshots, document implemented package versions, verify cleanup and backup/restore, record limitations and reconcile all UI labels with the delivered build. Architecture, endpoint contract and security notes are in [06](06-architecture-and-database.md), [08](08-implementation-and-api-plan.md) and [10](10-security-and-devops.md).
