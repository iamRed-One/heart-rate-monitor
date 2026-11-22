# Project documentation

**Author:** Ambali Ridwan Oladele | **Matric:** U23CS1019  
**Course:** CSC403 Software Engineering | **Project:** SP-332  
**Draft baseline:** 2026-11-15 | **Lecturer/supervisor:** To be supplied

These documents describe the proposed system. They are reviewable drafts, not evidence that implementation, testing, deployment or peer assessment has occurred. Requirements and numeric limits below are proposed design decisions unless explicitly attributed to the workbook. Actual calendar dates await the course start date.

Implementation update: the initial schema migration, account/session authentication, status/readiness endpoints and no-pull PostgreSQL configuration now exist. Live health/readiness and migration consistency have been verified. See the [authentication guide](15-authentication.md) and [backend instructions](../backend/README.md). Earlier implementation plans remain targets for undelivered features. Local development notes are intentionally excluded from Git.

| Document | Purpose | Status |
| --- | --- | --- |
| [00 Workbook requirements](00-workbook-requirements.md) | Exact project row, general course plan and project-specific tasks | Source transcription |
| [01 Project proposal](01-project-proposal.md) | Concise Week 1 proposal | Draft |
| [02 Process model](02-process-model.md) | Incremental model justification | Draft |
| [03 SRS and user stories](03-srs-and-user-stories.md) | Scope, requirements and acceptance criteria | Draft |
| [04 Planning](04-project-plan.md) | MoSCoW backlog, estimates, schedule and risks | Draft |
| [05 UML models](05-uml-models.md) | Use cases, class, sequence and activity models | Draft |
| [06 Architecture and database](06-architecture-and-database.md) | Components, deployment, ERD and schema specification | Draft; executable schema deferred |
| [07 UI and design patterns](07-ui-and-design-patterns.md) | Screen wireframes, two patterns and SOLID plan | Draft; implementation evidence deferred |
| [08 Implementation and API plan](08-implementation-and-api-plan.md) | Module boundaries, endpoint contract and coding standards | Draft |
| [09 Test plan](09-test-plan.md) | Test cases, coverage target and acceptance checks | Planned; no tests run |
| [10 Security and DevOps](10-security-and-devops.md) | STRIDE, security review and CI/deployment design | Planned; no deployed build |
| [11 User and technical manuals](11-user-and-technical-manuals.md) | Intended workflows and operating procedures | Pre-implementation draft |
| [12 Evidence and report templates](12-evidence-and-report-templates.md) | Progress, review, defects, change request, metrics and final report | Templates only |
| [13 Deliverables tracker](13-deliverables-tracker.md) | Exact weekly deliverables mapped to drafts and outstanding evidence | Tracking baseline |
| [15 Authentication](15-authentication.md) | Implemented schema/auth API, run commands and test evidence | Implemented locally; frontend pending |
| [16 CSV import](16-csv-import.md) | Preview/confirmation, basic recording history, sample and Postman instructions | Implemented locally; charts/frontend pending |
| [17 Dashboard API](17-dashboard-api.md) | Readings, summaries, bounded charts, rename/delete and verification | Backend core verified; frontend pending |

## Decisions and open items

- Confirmed by the student: build CSV import and historical viewing first; consider live data and a companion app later; use Python/FastAPI and PostgreSQL.
- Frontend decision updated by the student: React with the Astryx design system, replacing the initial Jinja2/server-rendered HTML proposal. Vite and TypeScript are proposed supporting tools. Retain same-origin cookie sessions, PostgreSQL in Docker and a layered FastAPI backend. Supporting package versions will be pinned during implementation.
- The workbook calls the stack optional, but the student has chosen to follow it.
- Student owns an ET488 and reports successful on-demand Pulse measurements in their separate Kotlin app, Vigil. That app is not a dependency of version 1. Its CSV compatibility has not been verified.
- Lecturer name, institution, submission date, course start date, actual weekly availability, Git hosting provider and deployment environment remain to be supplied.
- No clinical diagnosis, emergency alerts or clinical accuracy claims are included.
- A schema script, CI configuration, source code, real Git history, test results and deployment evidence must be produced during implementation. Their planned shape is documented; they are not complete deliverables yet.

## Reading order

The inspected Vigil export format and earlier live proof are documented in [18 Watch and companion findings](18-watch-companion-live.md). The independent HeaRt app and durable recording extension are documented in [19 HeaRt implementation and verification](19-heart-companion.md). These additions preserve the recorded-data coursework baseline.

The current [free-hosting guide](20-demo-hosting.md) covers Render, Neon, Android server settings, the browser proxy and TablePlus. The [presentation package](presentation/README.md) contains the editable PowerPoint, PDF preview, exact workbook deliverables and copied app screenshots. Cloud hosting remains proposed until a deployment URL is verified.

Read the proposal, SRS and planning documents first. Review import rules and scope before using the design as an implementation baseline. Compare the tracker against the verbatim workbook extraction when preparing submissions. Mermaid diagrams are text-based drafts; export rendered figures for a submission format if required.
