# Threat model, security review and DevOps plan

**Status:** Draft threat analysis and planned controls. No security audit, scan, CI run or deployment has occurred.

## Assets and trust boundaries

Assets are account credentials, session/CSRF secrets, private readings, preview payloads, recording metadata and database availability. Boundaries are browser to FastAPI, application to PostgreSQL, uploaded CSV to parser, and developer/CI environment to test deployment. The browser, upload bytes, filenames and all client-provided IDs are untrusted. PostgreSQL is not exposed directly to users.

## STRIDE analysis

| Category | Concrete threat | Planned controls | Verification |
| --- | --- | --- | --- |
| Spoofing | Guess credentials or reuse a stolen session | Established password hashing, login throttling, random tokens stored as digests, 12-hour expiry, revoke on logout, secure cookies | T-01, T-02, T-21 |
| Tampering | Replace preview content or alter another user's recording | Server-owned preview, CSRF, origin checks, owner-scoped reads/writes, database constraints and transactions | T-03, T-09–T-11, T-21 |
| Repudiation | Dispute import/delete operations without a trace | Log timestamp, request ID, internal owner ID, action and resource ID with outcome; omit file content and secrets | Review safe audit logs; retain for proposed 7 days in test environment |
| Information disclosure | Guess IDs, inspect logs, inject HTML or expose database | Ownership on every endpoint, React text rendering without unsafe HTML injection, parameterised queries, private DB, restricted logs, HTTPS | T-03, T-17, T-21; configuration review |
| Denial of service | Oversized files, repeated previews or expensive chart queries | 5 MiB/10,000-row cap, three active previews/user, bounded errors/pages/charts, login/upload throttling, request timeouts | T-08, T-12, T-16, T-24 |
| Elevation of privilege | Client supplies another owner ID or bypasses route checks | Derive owner from session, no admin role in v1, repository owner filters and negative tests | T-03, T-21 |

Proposed throttling baseline: 5 failed logins per account/IP combination in 15 minutes; 10 preview attempts per account in 10 minutes. Return 429 with a retry interval. Exact storage/implementation is to be selected during coding; an in-memory limiter alone must not be claimed to protect multiple deployed workers. Do not implement permanent account lockout that lets an attacker deny access indefinitely.

## OWASP Top 10 review plan

The workbook requires an OWASP Top 10 check but does not specify an edition. The following are risk-focused review areas, **not edition-specific category IDs or a completed compliance claim**. At Week 11, record the official edition used, its category IDs, evidence and findings for every category; update the checklist if the edition differs from this draft's grouping.

| Review area | Planned project check |
| --- | --- |
| Access control | Ownership on previews, recordings, readings, summaries and mutations |
| Cryptographic protection | Established password hashes, HTTPS deployment and no secrets in Git/logs |
| Injection | Parameterised persistence, HTML escaping, strict CSV and metadata validation |
| Insecure design | Review preview expiry/idempotency/transaction and delete flows against abuse cases |
| Configuration | Private database, no production debug traces, restrictive CORS, secure cookies and headers |
| Dependencies/supply chain | Pinned packages, dependency scans, reviewed updates and reproducible build inputs |
| Authentication | Login throttling, session renewal on login, logout revocation and CSRF/origin checks |
| Integrity of software and data | Protected review path, required checks, validated migrations and all-or-nothing import |
| Logging and detection | Safe action/error logs with request IDs, readiness checks and documented failure response |
| Server-side requests | No user-provided URL-fetch feature in v1; verify no indirect arbitrary fetch is introduced |
| Exception/failure handling | Safe errors, bounded parsing, rollback and no false-success responses |

Pending review entry format: edition/category, applicable assets/endpoints, inspected files/configuration, test/scan evidence, finding, severity, remediation and retest. Do not claim every category is covered merely because a planned control appears here.

## CI design

Trigger on every push and pull request once hosting is selected. A proposed workflow will:

1. Check out the exact revision and install pinned Python and frontend dependencies in a clean environment using the committed lockfile for npm.
2. Start an isolated PostgreSQL service and wait for readiness.
3. Run migrations against the empty test database.
4. Run Python formatting/lint/static analysis, backend tests and coverage with a minimum 60% gate; run React component tests, TypeScript checks and frontend lint separately.
5. Run dependency vulnerability and secret checks. Fail on actionable high/critical findings; exceptions need a documented rationale and review.
6. Build the React production bundle and package it with the FastAPI deployment artifact/container to meet the workbook build requirement; smoke-test startup, readiness and direct navigation to React routes.
7. Retain test, coverage, scan and build reports keyed to the revision.

No workflow file is created yet. Select supported scanner/build tooling and verify current commands at implementation time. Forked/untrusted contributions must not receive deployment credentials. Keep test database credentials disposable and separate from deployment credentials.

## Test deployment and operating checks

Provider and URL are pending. Use HTTPS ingress, environment-based application/database secrets and private PostgreSQL. Run reviewed migrations before serving the new build. Verify registration/login, valid import, invalid import, filtering, ownership and logout against synthetic data. Confirm preview cleanup runs and readiness reflects actual database/schema state. Capture revision, environment, timestamp and smoke-test results.

Proposed rollback: retain the previous application artifact and an explicit migration compatibility note. Take a database backup before destructive schema changes; do not assume rolling back application code reverses data migrations. Rehearse backup/restore in an isolated environment and record the result before claiming recovery capability.

## Scope limits

No medical-device certification, clinical validation or formal legal compliance claim is made. Security decisions here are implementation requirements to verify, not assurances that an unbuilt application is secure.
