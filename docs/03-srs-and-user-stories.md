# Software Requirements Specification and user stories

**Project:** SP-332, Wearable Heart-Rate Data Viewer  
**Author:** Ambali Ridwan Oladele, U23CS1019 | **Course:** CSC403 Software Engineering  
**Version:** Draft 0.1 | **Date:** 2026-11-15

## 1. Introduction

### 1.1 Purpose

Specify version 1 of the web viewer for implementation and assessment. This document supplies the workbook's draft SRS Sections 1–3, with functional and non-functional requirements and linked user stories.

### 1.2 Scope

The system accepts recorded wearable heart-rate data in a defined CSV format, stores it privately and presents historical readings, charts and descriptive summaries. Acquisition from the ET488, Bluetooth, USB, live collection and Android integration are deferred. The application is a data viewer and does not produce diagnoses or medical advice.

### 1.3 Definitions and references

BPM means beats per minute. A recording session is one successfully imported file. An account session is a signed-in browser session, distinct from a recording session. UTC is the storage and display time zone in version 1. A preview is a server-validated candidate import that has not yet created a recording. The course source is [00-workbook-requirements.md](00-workbook-requirements.md).

## 2. Overall description

### 2.1 Product perspective

A standalone React browser application using the Astryx design system, backed by FastAPI and PostgreSQL. The browser has no direct database access. No wearable connection or existing Vigil backend is required. CSV conversion from another application's export is outside the core import flow until that format is inspected.

### 2.2 Stakeholders and elicitation

| Stakeholder | Interest | Current evidence |
| --- | --- | --- |
| Student/developer | Deliver a manageable, assessable project | Conversation: CSV-first scope and prescribed stack selected |
| Wearable owner | Understand their recorded measurements | Proposed workflow; usability interview still pending |
| Lecturer/assessor | Evidence against weekly course requirements | Workbook supplied; lecturer-specific feedback pending |
| Test participant/peer | Try workflows and review quality | Not yet recruited; no interview or peer assessment claimed |

Elicitation completed so far: workbook analysis, scope discussion and inspection of the separate Android measurement path. Further validation: ask a participant to import a supported sample and explain the chart, ask the lecturer to review scope, and inspect a real export before claiming vendor compatibility.

### 2.3 Constraints and assumptions

- Use Python/FastAPI and PostgreSQL. Local PostgreSQL is intended to run in Docker.
- Use React with Astryx for the frontend, as selected by the student. Vite/TypeScript are proposed build/language choices. FastAPI remains the backend; no Jinja2 page rendering is planned.
- Users supply files that conform to the CSV contract below. A synthetic fixture enables device-independent assessment.
- Registration and user isolation are proposed baseline features from the earlier feature discussion; no admin or clinician role is included.
- All limits and performance targets are project design assumptions to validate, not manufacturer specifications or clinical thresholds.
- Initial supported browsers: current stable Chrome and Edge on desktop, and Chrome on Android; record exact tested versions later.

## 3. Specific requirements

### 3.1 Functional requirements

| ID | Requirement | Acceptance condition |
| --- | --- | --- |
| FR-01 | Register a user with a unique email and password | Valid registration creates one account with a password hash; duplicate normalised email is rejected |
| FR-02 | Sign in and sign out | Valid credentials create a new account session; sign-out invalidates it; invalid login uses a generic error |
| FR-03 | Isolate all user-owned data | Another user's recording and preview IDs cannot be read, edited, imported or deleted; return 404 for inaccessible IDs |
| FR-04 | Accept a CSV upload for preview | An authenticated user can select a file within the size and row limits; no recording is created yet |
| FR-05 | Validate files and report errors | Structural and row errors include useful reasons and CSV record numbers; failed validation cannot be confirmed |
| FR-06 | Confirm a valid preview atomically | One confirmation creates one recording and all its readings; retry cannot create a second recording |
| FR-07 | Show imported recording sessions | The owner sees a paginated list ordered by import time, including label, source type, reading count and recorded range |
| FR-08 | Show heart-rate readings in a chart and table | Display time-ordered data from the same selected recording/date range; table offers exact readings |
| FR-09 | Filter data by date range and optional recording | Use the same UTC filter for readings and summaries; invalid ranges are rejected |
| FR-10 | Calculate descriptive summaries | Show count, minimum, maximum and arithmetic mean BPM for the filtered data; empty results show no-data text |
| FR-11 | Rename a recording | Owner can set a nonblank label of 1–100 characters; rendered text is escaped |
| FR-12 | Delete a recording after confirmation | Owner deletion removes its readings in one transaction; cancel leaves it unchanged |
| FR-13 | Identify synthetic data | User marks each upload as recorded or synthetic; both session and dashboard show that declaration; mixed selections say mixed sources |
| FR-14 | Show actionable states | Loading, empty, validation failure, expired preview, failed save and unavailable server states explain the next step without exposing internals |
| FR-15 | Provide import-format help | Import screen explains headers, timestamp offsets, limits, duplicates and the all-or-nothing rule and offers a labelled synthetic sample |

### 3.2 Non-functional requirements

| ID | Requirement and proposed verification |
| --- | --- |
| NFR-01 Security | Hash passwords with an established password-hashing library; enforce ownership and CSRF controls on mutations; verify by negative integration tests |
| NFR-02 Integrity | Validate before committing, use transactions and enforce constraints; inject a mid-save failure and prove zero partial recordings |
| NFR-03 Performance | On a documented local baseline of at least 4 CPU cores and 8 GB RAM, with 100,000 stored readings, target p95 under 2 seconds for 30 sequential filtered reads and under 10 seconds for a 10,000-row preview; measure server time and record environment |
| NFR-04 Usability/accessibility | Core flows work by keyboard, fields have labels, errors are associated with inputs, and charts have table equivalents; manually verify at 360px and 1280px viewport widths |
| NFR-05 Maintainability | Separate routes, services, parsing and persistence; require readable names, type annotations at boundaries and documented public contracts; verify through review |
| NFR-06 Testability | Target at least 60% line coverage of application Python code, plus explicit import/ownership/summary/delete tests; coverage alone is not an acceptance substitute |
| NFR-07 Privacy | Do not log passwords, session tokens or CSV content; remove temporary uploads after confirmation/expiry and verify deletion/retention behaviour |
| NFR-08 Reproducibility | A clean setup using pinned dependencies and the documented PostgreSQL container must run migrations and checks successfully; record actual versions at implementation |
| NFR-09 Resource limits | Enforce 5 MiB input limit, 10,000 data rows per import and bounded preview/chart responses; test limits including unknown content length |
| NFR-10 Time consistency | Require timestamp offsets, persist instants using TIMESTAMPTZ and label all displayed times UTC; test equivalent offsets and day boundaries |

### 3.3 CSV and validation contract

Proposed canonical format (illustrative synthetic values, not measurements):

```text
timestamp,heart_rate
2026-11-01T09:00:00+01:00,72
2026-11-01T09:01:00+01:00,75
2026-11-01T09:02:00+01:00,78
```

- UTF-8 with an optional initial BOM, comma-delimited, LF or CRLF. Standard quoted CSV fields are supported. Trim surrounding field whitespace.
- Header names are exactly `timestamp` and `heart_rate`, case-sensitive, in either order. Missing, repeated or extra columns are rejected. Additional metadata is entered on the import form.
- `timestamp` must be an ISO 8601 date-time with seconds and `Z` or a numeric offset. Fractional seconds are allowed up to six digits. Reject naive, impossible and more-than-six-fractional-digit values. Convert to UTC without silently assuming the server time zone.
- `heart_rate` is a base-10 whole number from 1 through 300 inclusive. This is a broad software input bound, not a clinical interpretation. Zero, negatives, decimals, missing values and text are errors.
- Empty physical lines are ignored; records with empty required fields are errors. Error numbers use logical CSV record numbers including the header as record 1, so quoted multiline fields do not confuse numbering.
- A file must contain 1–10,000 data records and be at most 5 MiB (5,242,880 bytes). Enforce limits while receiving/parsing, not solely from declared content length.
- Reject duplicate timestamps within a file after UTC normalisation, including duplicates with the same BPM. Out-of-order unique timestamps are accepted and sorted for display.
- Reject future timestamps more than five minutes ahead of the server clock. This tolerance is a proposed clock-skew allowance, not a health rule.
- Validation is all-or-nothing: a single invalid row prevents confirmation; show total errors and the first 50 details. Retain no failed upload after returning errors.
- Preview shows count, UTC range, first ten valid rows and the source declaration. Valid candidate data is held server-side under an owner-bound opaque ID for 15 minutes. The client cannot replace validated readings at confirmation.
- Within an account, identical raw file bytes are identified by SHA-256. A committed identical upload returns the existing recording rather than duplicating it. Its existing label, source declaration and device metadata remain unchanged, even if the duplicate upload supplies different metadata; explain this outcome and show the existing values. Different byte representations of equivalent data are allowed as separate recordings; aggregate counts count imported records, not unique physiological events.
- Preview confirmation is idempotent and transactional. Reconfirmation of a consumed preview returns the original recording while its preview metadata exists (retained for 24 hours after consumption). A known expired preview returns 410; after cleanup removes its metadata, the ID returns 404. In either case the UI asks the user to re-upload. An identical re-upload still resolves to the existing recording by digest. Deletion frees the original file digest for a later re-import, but does not revive its consumed preview.
- Original filename is display metadata, never a filesystem path. Do not retain the original CSV after validation; temporary validated payload expires after 15 minutes or is cleared upon confirmation.
- Label is 1–100 characters after trimming; source type is `recorded` or `synthetic`; optional device label is at most 100 characters. A source declaration does not prove a file's authenticity.

### 3.4 Query and calculation rules

Default dashboard selection is all owned recordings; show recorded/synthetic/mixed source status. Filtering uses an inclusive UTC start date and exclusive start of the day after the end date. For example, 1 November through 2 November includes both complete UTC days. Unspecified dates mean no bound; start after end returns a validation error.

Count is the number of matching stored readings. Mean is the unweighted arithmetic mean, rounded to one decimal place for display only. This is not a time-weighted average. For [72,75,78], show count 3, minimum 72, maximum 78 and mean 75.0. For zero rows show count 0 and no numeric extrema/mean. Label the newest stored measurement with its timestamp; never label it live.

Table pages contain 100 rows ordered by timestamp then reading ID, with a next-page cursor. Charts use exact points up to 1,000 readings. Above this, use at most 500 equal-duration UTC buckets with minimum, maximum, mean and count for each nonempty bucket, visibly labelled as aggregated. Compute summaries from the full filtered dataset, not chart buckets. An all-time, single-timestamp range needs no bucketing. Do not imply continuous observations across long recording gaps.

### 3.5 User stories with acceptance criteria

| ID | Story | Given / when / then | Requirements |
| --- | --- | --- | --- |
| US-01 | As a wearable owner, I want a private account | Given an unused email and valid password, when I register and sign in, then I can access my workspace; an invalid login creates no session | FR-01, FR-02 |
| US-02 | As an owner, I want only my recordings visible | Given accounts A and B, when B requests A's recording or preview, then the request returns 404 and changes nothing | FR-03 |
| US-03 | As an importer, I want mistakes explained before saving | Given a CSV with a bad timestamp or BPM, when I preview it, then I see record-level reasons and no recording is saved | FR-04, FR-05, FR-15 |
| US-04 | As an importer, I want to confirm what will be stored | Given a valid preview, when I confirm twice, then one recording with the expected rows exists and both responses identify it | FR-06 |
| US-05 | As an owner, I want to find previous recordings | Given several imports, when I open History, then I see their labels, count, source and time ranges in import order | FR-07 |
| US-06 | As a viewer, I want visual and exact readings | Given a selected recording, when I open it, then the chart and paginated table represent the same time-filtered data, with aggregation disclosed | FR-08 |
| US-07 | As a viewer, I want to narrow the period | Given readings across several days, when I choose a UTC date range, then readings and summaries include only that period | FR-09, FR-10 |
| US-08 | As a viewer, I want understandable summaries | Given BPM values 72,75,78, when I view the summary, then it shows 3,72,78,75.0; an empty range shows no-data text | FR-10, FR-14 |
| US-09 | As an owner, I want useful recording names | Given my recording, when I rename it with a valid label, then the new label persists; a blank label is rejected | FR-11 |
| US-10 | As an owner, I want to remove a recording | Given my recording, when I confirm deletion, then it and its readings disappear; cancelling changes nothing | FR-12 |
| US-11 | As an assessor, I want synthetic data identified | Given a sample import marked synthetic, when I view its session or dashboard, then its synthetic origin remains visible | FR-13, FR-15 |
| US-12 | As a user, I want to recover from an expired preview | Given a preview older than 15 minutes, when I confirm, then no recording is created and I am asked to re-upload | FR-06, FR-14 |

## Validation status

Requirements derive from the workbook and agreed discussion, with explicit proposed details added to make implementation testable. No external stakeholder interview or usability session has been completed. Validate the scope with the student/lecturer and record revisions before calling this an approved baseline.
