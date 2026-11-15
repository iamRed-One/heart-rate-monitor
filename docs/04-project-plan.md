# Backlog, estimate, schedule and risk register

**Status:** Proposed planning baseline | **Author:** Ambali Ridwan Oladele

## Prioritised product backlog

Estimates below are initial focused implementation hours, including item-level tests and review. They are not measured effort. Documentation and delivery work is estimated separately to avoid double counting.

| Item | Work | MoSCoW | Estimate | Dependencies | Acceptance reference |
| --- | --- | --- | ---: | --- | --- |
| B-01 | Foundation, PostgreSQL connection and migrations | Must | 6h | None | NFR-08 |
| B-02 | Accounts, sessions, CSRF and ownership boundary | Must | 10h | B-01 | US-01, US-02 |
| B-03 | CSV parser, limits and error report | Must | 10h | B-01 | US-03 |
| B-04 | Owner-bound preview, atomic confirmation and duplicates | Must | 10h | B-02, B-03 | US-04, US-12 |
| B-05 | Basic history and recording view | Must | 6h | B-04 | US-05 |
| B-06 | Summary calculations and UTC filters | Must | 6h | B-05 | US-07, US-08 |
| B-07 | Chart, exact table and aggregation | Must | 8h | B-06 | US-06 |
| B-08 | Rename and confirmed deletion | Must | 4h | B-05 | US-09, US-10 |
| B-09 | Import help, source labels and synthetic fixture | Must | 3h | B-04 | US-11 |
| B-10 | Responsive states and keyboard/accessibility review | Must | 5h | B-05–B-09 | NFR-04, FR-14 |
| B-11 | Additional usability refinements after feedback | Should | 4h | Prototype review | Feedback to define |
| B-12 | Dashboard preference persistence | Could | Not scheduled | Core complete | Future criteria needed |
| B-13 | Live data, Bluetooth and Android integration | Won't in v1 | Not scheduled | Separate feasibility study | Outside baseline |

Core implementation estimate: **68h** (B-01 through B-10). Allocate **30h** for proposal, requirements, planning, diagrams and design; **36h** for broader review, system/security checks, CI, deployment, maintenance, manuals and presentations. Baseline **134h**, plus **20h contingency**, total **154h** across 13 weeks, approximately **11.8h/week**. B-11 would consume contingency or require replanning. Actual student availability is unconfirmed. Record actuals and revise scope/estimates if this allowance is unavailable.

Frontend revision: the student has selected React with Astryx instead of the initial server-rendered proposal. Provisionally reserve **8h of the existing 20h contingency** for React/Astryx setup, component/API integration and frontend build/test wiring. The total allowance remains 154h, with 12h unallocated contingency. This is an unmeasured allowance; re-estimate after checking the design-system setup. B-11 would use the remaining contingency or require replanning. Backend stack, CSV scope and coursework requirements are unchanged.

## 13-week schedule

Weeks are relative course weeks, not calendar dates. Exact course topics, tasks, deliverables and marks are in [00](00-workbook-requirements.md). Work estimates are planning assumptions and are independent of marks.

| Week | Planned project work | Baseline hours | Completion evidence |
| --- | --- | ---: | --- |
| 1 | Review proposal/process; create repository when authorised | 4 | Proposal, justification, real repository URL |
| 2 | Validate SRS and stories | 8 | Requirements and recorded feedback |
| 3 | Review priorities, effort, schedule and risks | 5 | Updated planning baseline |
| 4 | Review UML against import flow | 6 | Rendered model set |
| 5 | Finalise architecture/ERD; later create actual migration/schema artifact | 4 | Architecture and schema review |
| 6 | Review wireframes, patterns and SOLID design | 3 | Design note and screen feedback |
| 7 | Implement B-01–B-04 | 36 | Working accounts and validated import; checks and Git evidence |
| 8 | Implement B-05 and basic B-06; demonstrate core flow | 12 | Live prototype, progress report and revised backlog |
| 9 | Complete B-06–B-10; peer review and static analysis | 20 | Remaining modules and quality evidence |
| 10 | Broaden tests, measure coverage and execute system cases | 12 | Test run, coverage and defect log |
| 11 | Verify threats/controls, CI and test deployment | 10 | Pipeline results and deployed build |
| 12 | Execute one real change request; finish manuals and metrics | 8 | Change record, documentation and metrics |
| 13 | Final acceptance run, demo, report, reflection and peer assessment | 6 | Actual final submission evidence |
| Total | Contingency of 20h held separately | 134 | No work claimed complete by this schedule |

Week 7 and Week 9 are heavier than the average. If availability cannot support them, bring preparatory work forward only after the documentation review, reduce nonessential work, or revise the course-relative schedule with the lecturer. Do not hide the peak load behind the weekly average. The requested documents-first workflow means Week 5's executable schema and Week 6's implemented SOLID example remain pending until coding begins.

## Risk register

Likelihood/impact: Low, Medium or High. Owner for all initial risks is the student/developer; lecturer review is needed for assessment changes.

| ID | Risk | Likelihood | Impact | Mitigation / trigger / fallback |
| --- | --- | --- | --- | --- |
| R-01 | Real wearable export differs from canonical CSV | High | Medium | Inspect one export before promising compatibility; retain documented CSV and synthetic fixture; propose mapping as a change |
| R-02 | Docker/PostgreSQL setup fails | Medium | High | Check engine and persistent storage during setup; document exact tested versions; postpone feature work until database smoke test passes |
| R-03 | Cross-user data exposure | Medium | High | Ownership filters in repositories plus negative integration tests; block release on any failure |
| R-04 | Large or malformed uploads exhaust resources | Medium | High | Enforce byte/row/time limits and cleanup; test boundaries and concurrency |
| R-05 | Time-zone mistakes distort charts | Medium | High | Require offsets, normalise UTC and test boundary/offset-equivalence cases |
| R-06 | Live integration expands scope | High | High | Keep it in future backlog; require separate feasibility/effort decision |
| R-07 | Week 7/9 workload exceeds availability | High | High | Track actual effort per item; use contingency; review scope and course dates early |
| R-08 | No peer or deployment access when due | Medium | Medium | Identify a peer and test host by Week 8; record pending evidence honestly |
| R-09 | Real personal data enters repository | Medium | High | Use synthetic fixtures, ignore local data/secrets, inspect staged files |
| R-10 | Duplicate imports or partial saves distort statistics | Medium | High | Digest checks, unique constraints and atomic transaction; retry/rollback tests |
| R-11 | UI implies diagnosis or continuous sensing | Medium | Medium | Use descriptive summaries, UTC timestamps and explicit historical/synthetic labels |

## Review and change control

At each weekly review record completed items, actual hours, blockers, feedback and revised estimates. Do not change the exact coursework requirements; map scope changes to affected requirements and tests. Use the change-request template in [12](12-evidence-and-report-templates.md). Review risks again before the prototype and final demo.
