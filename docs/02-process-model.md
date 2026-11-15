# Process-model justification

**Project:** SP-332 | **Author:** Ambali Ridwan Oladele | **Status:** Draft

## Selected model: Incremental

The product has a stable initial boundary: import recorded heart-rate data and let the owner explore it. It can be delivered in useful increments, and the course explicitly requires a working prototype in Week 8. Each increment will include design, implementation, tests and documentation updates.

| Increment | Intended result | Exit condition |
| --- | --- | --- |
| 1: Core flow | Registration/sign-in, CSV validation, commit and basic recording view | A valid file produces a private recording; an invalid file produces understandable errors and no stored recording |
| 2: Exploration | Dashboard, date filters, summaries, chart/table and recording labels/deletion | Filters and summaries agree with known fixture data; access and deletion tests pass |
| 3: Quality and delivery | Security controls, CI, test deployment, manuals and maintenance exercise | Required checks pass, deployment is demonstrated and actual evidence is captured |

The initial six weeks prepare the coursework design baseline; implementation starts in Week 7. Feedback can still revise the baseline through the change log. Drafting the documents now does not bypass later validation with the lecturer or prototype users.

## Alternatives considered

Waterfall fits the sequential teaching topics, but postponing a usable system until the end would make import and usability mistakes costly. Full Scrum adds formal roles and ceremonies that are difficult to justify for a personal project. We will use a prioritised backlog, short reviews and feature branches within an Incremental model without claiming a Scrum team exists.

## Working cycle

Select a small backlog item, confirm its acceptance criteria, update affected designs, implement on a feature branch, run appropriate checks and review the change. Record actual effort and feedback. Integrate only after checks pass. Keep documentation aligned with the delivered behaviour. Commits and remote publication will be performed only when authorised; no historical commits or peer-review evidence will be invented.

Changes affecting CSV rules, data ownership or exclusions must update the SRS, test plan and traceability map. Live monitoring is future scope and requires its own feasibility decision before being added.
