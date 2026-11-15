# Exact deliverables schedule and traceability

**Source:** SE_Personal_Projects_13_Weeks.xlsx, Weekly Tasks filtered to SP-332. Deliverable text and marks in the first three columns are verbatim. The companion [source extraction](00-workbook-requirements.md) retains the full tasks and the general 13-Week Plan, including numeric requirements.

**Status:** Draft package prepared before implementation. A linked draft is not proof that its whole weekly deliverable is complete. Total marks: **100**.

| Week | Exact deliverable | Marks | Draft location | Outstanding evidence/action |
| --- | --- | ---: | --- | --- |
| 1 | Project proposal, process-model justification, repository link | 4 | [Proposal](01-project-proposal.md), [process](02-process-model.md), [README](../README.md) | Review drafts; create actual repository and supply URL |
| 2 | Draft SRS (Sections 1-3), user stories | 8 | [SRS and stories](03-srs-and-user-stories.md) | Validate with stakeholders/lecturer; record feedback |
| 3 | Backlog, schedule, risk register, estimate | 6 | [Project plan](04-project-plan.md) | Confirm availability/course dates; update actuals |
| 4 | UML model set | 8 | [UML](05-uml-models.md) | Review and render diagrams; convert to formal UML tooling if required |
| 5 | Architecture document, ERD, schema script | 8 | [Architecture/database](06-architecture-and-database.md) | Review design; executable schema script/migration is not yet written |
| 6 | Design-patterns note, wireframes | 6 | [UI/patterns](07-ui-and-design-patterns.md) | Validate screens; apply two patterns and SOLID module in code later |
| 7 | Working module 1, Git history | 6 | [Implementation plan](08-implementation-and-api-plan.md) | Implement core module; capture real branches/commits/PRs |
| 8 | Live demo, progress report | 10 | [Evidence templates](12-evidence-and-report-templates.md) | Run working prototype demo; record progress/feedback and revise backlog |
| 9 | Modules 2+, review log, static-analysis report | 6 | [Plan](08-implementation-and-api-plan.md), [templates](12-evidence-and-report-templates.md) | Implement remaining modules; real peer review, refactoring and analysis |
| 10 | Test plan, test cases, coverage report, defect log | 8 | [Test plan/cases](09-test-plan.md), [templates](12-evidence-and-report-templates.md) | Execute tests; record coverage and defects; target at least 60% |
| 11 | Threat model, CI pipeline, deployed build | 8 | [Security/DevOps](10-security-and-devops.md) | Verify controls/OWASP edition; implement CI build/test/scan and test deployment |
| 12 | Change request, documentation, metrics report | 6 | [Manuals](11-user-and-technical-manuals.md), [templates](12-evidence-and-report-templates.md) | Execute one real change with impact analysis; verify manuals and measure metrics |
| 13 | Final demo, final report, reflection, peer review | 16 | [Final report and assessment templates](12-evidence-and-report-templates.md) | Actual final build/demo/report/reflection and peer assessment |

## Requirement-to-test traceability

| Requirement | Story / design | Planned tests |
| --- | --- | --- |
| FR-01 | US-01; auth routes and account schema | T-01 |
| FR-02 | US-01; account session lifecycle | T-02 |
| FR-03 | US-02; owner-scoped repository | T-03, T-21 |
| FR-04 | US-03; import screen/parser | T-04, T-08 |
| FR-05 | US-03; CSV contract | T-04–T-08 |
| FR-06 | US-04, US-12; import sequence/transaction | T-09–T-12 |
| FR-07 | US-05; History | T-13 |
| FR-08 | US-06; chart/table | T-16, T-22 |
| FR-09 | US-07; UTC filters | T-14, T-15 |
| FR-10 | US-08; summary rules | T-14–T-16 |
| FR-11 | US-09; recording rename | T-17 |
| FR-12 | US-10; confirmed cascade deletion | T-18 |
| FR-13 | US-11; source metadata and badges | T-19 |
| FR-14 | US-08, US-12; UI states | T-12, T-20 |
| FR-15 | US-03, US-11; import help/sample | T-20, T-26 |
| NFR-01 | Security/ownership architecture | T-01–T-03, T-17, T-21 |
| NFR-02 | Atomic persistence | T-09–T-11, T-18 |
| NFR-03 | Bounded queries/performance targets | T-24 |
| NFR-04 | Accessible wireframes and table alternative | T-22 |
| NFR-05 | Layering, patterns and coding standards | Peer/self code review and static analysis, not yet run |
| NFR-06 | Coverage and critical-path testing | All relevant automated cases plus actual coverage report |
| NFR-07 | Retention and safe logging | T-23 plus log/config inspection |
| NFR-08 | Reproducible environment | T-25 |
| NFR-09 | Bounded upload/preview/query | T-08, T-12, T-16 |
| NFR-10 | Time normalization | T-07, T-15 |

## Package verification

Before submission, verify the exact deliverable strings and marks against the workbook, count at least 10 functional requirements, 5 non-functional requirements and 8 stories with acceptance criteria, and ensure each claimed result has evidence. Reconcile diagrams, schema and UI with implemented behaviour. Keep missing evidence explicit rather than filling reports with predicted successes.
