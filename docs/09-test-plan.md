# Software test plan and planned test cases

**Status:** Not executed. No coverage, defect count or pass rate is claimed.  
**Scope:** Version 1 CSV viewer | **Owner:** Ambali Ridwan Oladele

## Approach and environment

Use unit tests for CSV rules, summary rules, clock-dependent decisions and service branching. Use integration tests against a dedicated PostgreSQL test database for transactions, ownership, constraints and authenticated endpoints. Run system tests in the browser for complete workflows and accessibility states. Keep the test database separate from development and deployment data and reset only an explicitly identified test database.

Proposed tools: pytest, coverage reporting, a FastAPI-compatible request client and Ruff static checks for the backend. Add React component tests for import/error states and filters, plus TypeScript checks, frontend lint and production-build verification. Select and pin the frontend test runner during setup. Exact versions and commands will be documented when dependencies exist. Use synthetic fixtures; record Python, Node, PostgreSQL, OS, browser and dependency versions. The existing 60% application Python coverage target remains; report frontend results separately without implying Python coverage measures React code.

## Test cases

All cases begin in **Not run** state. Their IDs are stable for traceability. Test results must record actual observations, build commit and evidence path.

| ID | Layer | Scenario / input | Expected result | Requirements |
| --- | --- | --- | --- | --- |
| T-01 | Integration | Register valid user; retry normalised email | One account; duplicate rejected; only password hash stored | FR-01 |
| T-02 | Integration | Correct login, wrong login, logout, expired session | Correct status/cookie lifecycle; old token unusable | FR-02 |
| T-03 | Integration | User B reads/renames/deletes A's recording and reads/confirms/discards A's preview | 404 for each; no data change | FR-03, NFR-01 |
| T-04 | Unit + integration | Three-row valid canonical fixture, LF/CRLF, BOM, header order swapped | Count 3 and expected UTC range; preview creates no recording | FR-04, FR-05 |
| T-05 | Unit | Missing/duplicate/extra headers, malformed quotes, invalid UTF-8 and empty file | Specific validation failure; no saved data | FR-05 |
| T-06 | Unit | BPM 1,300,0,301,-1,72.5,blank,text | Accept only 1 and 300 among boundary cases; reject invalid rows | FR-05 |
| T-07 | Unit | Naive/impossible date, two equivalent offsets, >6 fractional digits, future +5m/+5m1s | Reject missing/impossible/over-precision dates and duplicate instants; enforce clock tolerance | FR-05, NFR-10 |
| T-08 | Unit + integration | Exactly 5 MiB and 5 MiB+1; 10,000 and 10,001 rows; unknown Content-Length | Boundaries enforced during receipt/parse; 413 for oversized bytes; row-limit error for excess records | NFR-09 |
| T-09 | Integration | Confirm valid preview twice, including concurrent requests | One recording with all readings; both resolve to same recording | FR-06 |
| T-10 | Integration | Two previews of identical raw bytes confirmed concurrently under same account, with differing labels/source declarations | Unique digest prevents duplicate recording; both resolve safely; winner's metadata is preserved and existing-recording outcome is disclosed | FR-06 |
| T-11 | Integration | Force insert failure midway through confirmation | Transaction rolls back recording/readings/consumption; retry remains possible until expiry | NFR-02 |
| T-12 | Integration | Confirm after expiry; scheduled cleanup delayed; fourth active preview | 410 even before cleanup; no save; active preview cap returns 429 | FR-06, FR-14, NFR-09 |
| T-13 | Integration | Several imports; navigate history pages | Correct ownership, stable import ordering and no repeats across static-data pages | FR-07 |
| T-14 | Unit + integration | Filtered BPM 72,75,78; empty range; singleton | Means 75.0, null, singleton respectively; correct min/max/count | FR-09, FR-10 |
| T-15 | Integration | UTC midnight boundaries, inclusive end date, reversed dates | Start inclusive/end-next-day exclusive; reversed dates rejected | FR-09, NFR-10 |
| T-16 | Integration + system | More than 1,000 points and a sparse/overlapping-session dataset | At most 500 nonempty chart buckets, disclosed aggregation; exact table and full-data summaries agree | FR-08, FR-10 |
| T-17 | Integration | Rename valid/blank/>100-char/HTML-looking label | Valid label persists; invalid rejected; HTML displayed as text | FR-11, NFR-01 |
| T-18 | Integration + system | Cancel deletion, confirm deletion, force deletion failure | Cancel preserves; success cascades readings; failure rolls back | FR-12 |
| T-19 | System | Synthetic, recorded and mixed-source selections | Correct source labels remain visible; no live-data claims | FR-13 |
| T-20 | System | Loading, no imports, empty filter, invalid upload and unavailable server | Clear status and recovery action; no false success | FR-14, FR-15 |
| T-21 | Security integration | Missing/wrong CSRF, foreign Origin, SQL-like labels and forged owner IDs | Mutation rejected or text safely handled; no ownership bypass | NFR-01 |
| T-22 | System | Keyboard-only core flow; 360px and 1280px layouts; supported browsers | Labelled controls, visible focus, usable table and no page-wide overflow | NFR-04 |
| T-23 | Integration | Expire preview, confirm preview, logout and run cleanup; retry after metadata removal | Payload/raw data removed on schedule; revoked tokens unusable; consumed-deleted preview cannot resurrect data; cleaned-up ID returns 404 and identical re-upload resolves by digest | NFR-07 |
| T-24 | Performance | 100,000 stored rows; 30 sequential read samples and 10,000-row preview | Record p95/read and preview duration against NFR-03; report actual environment and misses | NFR-03 |
| T-25 | Setup/system | Clean environment, migration, restart with persisted volume and readiness check | Reproducible start and retained records; no dev secrets required in Git | NFR-08 |
| T-26 | System | Complete register → login → preview → confirm → filter → rename → delete → logout | All steps work with documented outputs and no cross-user access | Core acceptance |

## Coverage, entry and exit criteria

Workbook target: **60% or more coverage**. Measure line coverage for application Python modules, including services, parsers, repositories and routes; exclude generated migrations and test files, and document any other exclusions. Retain both machine-readable and readable reports. A percentage does not prove the critical scenarios were tested.

Entry: implemented increment, runnable isolated database, deterministic fixtures, documented expected results and pinned environment. Exit for prototype: valid import-to-view flow plus invalid-file, rollback and ownership cases pass. Exit for final build: planned critical cases pass, at least 60% coverage achieved, no unresolved blocker/critical security or data-integrity defects, CI passes and deployment smoke checks pass. Lower-severity unresolved defects must be documented with lecturer/user-visible limitations.

## Reporting and defects

Use the test run and defect templates in [12](12-evidence-and-report-templates.md). Record expected and actual output, not just a tick. Failed tests produce a defect or a justified test correction. Re-run affected and related regression cases after a fix. No coverage report, system-test result or defect log entry exists yet.
