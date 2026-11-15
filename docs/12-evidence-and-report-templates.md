# Evidence logs and report templates

**Status:** Blank templates for later execution. No demonstration, peer review, test run, scan, change request or deployment has been completed by writing this document. Duplicate the relevant template for each real event and retain its date and revision.

## Weekly progress and Week 8 prototype report

| Field | To complete |
| --- | --- |
| Week / date / build revision | Pending |
| Planned backlog items and hours | Pending |
| Completed items, actual hours and evidence links | Pending |
| Incomplete work and reasons | Pending |
| Prototype flow demonstrated | Pending |
| Feedback source and observations | Pending |
| Backlog changes, risks and next actions | Pending |

Prototype script: register/sign in; import the synthetic three-row fixture; show preview; confirm; show count 3, minimum 72, maximum 78, average 75.0; filter; attempt an invalid import; show that no recording was created. Demonstrate private access with a second account. Record what actually worked and failed. A live demo means demonstrating the working application; it does **not** mean live sensor acquisition.

## Peer code-review log

| Date | Reviewer | Revision/PR | Scope and requirements | Finding and severity | Response/change | Retest / resolution |
| --- | --- | --- | --- | --- | --- | --- |

No peer has reviewed the project yet. A self-review must be labelled self-review and cannot be substituted silently for the workbook's peer review.

## Static analysis and scan report

Record tool/version, command/configuration, revision, timestamp, files covered, raw report path, finding counts by severity, suppressed findings with rationale, fixes and rerun evidence. Status: **Not run**.

## Test execution report

| Field | To complete |
| --- | --- |
| Run ID / date / tester / revision | Pending |
| Environment and database isolation | Pending |
| Test IDs executed and expected outcomes | Pending |
| Actual outcomes and failure evidence | Pending |
| Coverage tool, application scope and exclusions | Pending |
| Measured coverage / report paths | Not measured |
| Exit criteria met / exceptions | Not assessed |

## Defect log

| ID | Date | Test/requirement | Reproduction and actual result | Expected result | Severity | Owner/status | Fix revision | Retest evidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |

Severity: blocker stops the core flow; critical compromises privacy/integrity; major breaks a required feature; minor affects presentation or a recoverable edge. Zero recorded defects is not proof of zero defects, especially before tests run.

## CI and deployment evidence

Record hosting provider, pipeline URL/run ID, commit, build artifact identifier, test/coverage/scan outputs, environment, deployment URL, migration result, readiness result and smoke checks. Capture secret-free screenshots if required. Status: **Not configured or deployed**.

## Change request and impact analysis

| Field | To complete |
| --- | --- |
| CR ID / requester / date / rationale | Pending actual request |
| Current behaviour and proposed change | Pending |
| Affected SRS IDs, stories and acceptance criteria | Pending |
| UI/API/schema/security/data-migration impact | Pending |
| Estimate, schedule/risk impact and alternatives | Pending |
| Decision / reviewer / date | Pending |
| Implementation revision and regression tests | Pending |
| Actual outcome and documentation updates | Pending |

A possible future exercise is a verified mapping for a real vendor export, but it is not approved, implemented or counted as the required maintenance change. Select one real change after a baseline exists.

## Software metrics report

| Metric | Definition / collection method | Result |
| --- | --- | --- |
| Size | Nonblank, noncomment application source lines, excluding tests/generated/vendor code; name tool and scope | Not measured |
| Complexity | Cyclomatic complexity per function/module; name tool/version and identify highest values | Not measured |
| Defect density | Confirmed defects from a stated observation period divided by application KLOC for the same revision/scope | Not measured |
| Coverage | Covered application lines / executable application lines, with exclusions disclosed | Not measured |
| Delivery effort | Actual focused hours per backlog item versus initial estimate | Not recorded |

If application KLOC is zero or no meaningful test period exists, report defect density as not applicable/not measured rather than a misleading zero. Define whether reopened/duplicate defects count before collecting results.

## Final report structure

1. Title page: project, author, matric, course, lecturer, institution and submission date.
2. Abstract: actual problem, implemented approach and measured outcome.
3. Introduction: problem, users, objectives, scope and exclusions.
4. Requirements: elicitation evidence, SRS, stories and prioritisation.
5. Process and planning: selected model, estimates, schedule, actual progress and risks.
6. Analysis/design: UML, architecture, ERD, schema, patterns, SOLID and wireframes.
7. Implementation: actual stack/versions, modules, selected code explanations and real Git/PR history.
8. Testing and quality: cases, results, coverage, defects, review, static analysis and limitations.
9. Security and deployment: threat model, named OWASP edition/checks, CI and deployed evidence.
10. Maintenance and metrics: actual change request, documentation and measured metrics.
11. Evaluation and future work: objective-by-objective evidence; live readings and companion integration remain future unless separately implemented and verified.
12. Reflection, references and appendices containing evidence links and relevant artifacts.

## Reflection prompts

What changed between estimates and actual effort? Which import or time-zone assumption caused difficulty? How did the process model help or hinder? What did peer feedback change? Which tests prevented regressions? What would you do differently? Answer from actual events; do not write invented first-person experience now.

## Peer assessment

Record assessor name, date, build, scope, rubric, comments and any score required by the lecturer. Obtain an actual assessment; do not prefill a score or fabricate feedback.

## Final demonstration checklist

Use a known build and synthetic dataset. Demonstrate account isolation, valid/invalid import, duplicate handling, history, filter/summary agreement, chart/table, rename/delete and logout. Show CI/test evidence and known limitations. Record audience/date, feedback and final revision. Sensor hardware is not required for this version's demonstration.
