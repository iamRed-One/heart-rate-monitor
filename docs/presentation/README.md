# HeaRt project presentation

Open [HeaRt-Project-Demonstration.pptx](HeaRt-Project-Demonstration.pptx) in PowerPoint. [The PDF](HeaRt-Project-Demonstration.pdf) is a preview; it does not replace the editable deck. The deck has 22 slides and presenter notes. It presents the project's analysis/design outputs and demonstrates Android only.

## Narrative

1. Proposal: the actual problem, users, scope and proposed solution.
2. Incremental process, prioritised backlog, estimates and risk responses.
3. Fifteen functional requirements, ten quality requirements and representative user stories with acceptance criteria.
4. Use-case, domain-class, import-sequence and import-activity diagrams.
5. Android/API architecture, core ERD, schema constraints, Strategy/Repository patterns and SOLID boundaries.
6. Native screen flow, API implementation, testing results and the STRIDE threat model.
7. Maintenance change, requirement-to-test traceability and remaining delivery evidence.
8. Actual Android screenshots, ending with the native live-demonstration sequence.

The source is the actual project work in [documents 01–13](../README.md), not a list of coursework instructions or marks. Later Android behavior and executed checks are reconciled with [19-heart-companion.md](../19-heart-companion.md) and the implemented code. The initial documents describe a CSV-first browser baseline; that baseline is distinguished from the approved Android/watch extension. The diagrams show the core data model and import rules, adapted to a mobile client where applicable. No web screenshots or web-preview slides are included.

## Screenshot provenance

The files in `assets/` are copied captures, not reconstructed interfaces. `heart.png` is the same generated anatomical illustration used by the app.

| Asset | Actual captured state |
|---|---|
| android-home-dark.png | November 22 Samsung phone, dark Home, disconnected; no live value displayed. |
| android-home-light.png | November 22 Samsung phone, light Home, connected/in-progress read; no result displayed yet. |
| android-account-dark.png | November 22 Samsung phone, dark sign-in screen. |
| android-devices-dark.png | November 22 Samsung phone, dark Devices screen, connecting state. |

The screenshots are historical captures; the live demonstration must show the actual current watch/server state. See [hosting and rehearsal instructions](../20-demo-hosting.md).

## Editable source

The deck uses native text and editable UML/architecture shapes, reusable layouts, named theme colors, sections and speaker notes. Screenshots are embedded images. `build.cjs`, `assets.json` and the copied Android assets reproduce it. The PowerPoint author is the student.

On this machine, generation uses the installed PPTX skill's theme utility and an ignored tooling directory. In Git Bash from the repository root:

```bash
npm install --prefix .impeccable/presentation-tooling pptxgenjs
NODE_PATH="$PWD/.impeccable/presentation-tooling/node_modules" \
PPTX_SKILL_ROOT="$HOME/.agents/skills/pptx" \
node docs/presentation/build.cjs
```

The PPTX skill directory must exist for its `scripts/apply_theme.js` utility. The application/backend dependency files are not modified by presentation tooling. Regenerate the PDF from the resulting deck in PowerPoint after any edits.

## Validation

The final file was opened and rendered by installed Microsoft PowerPoint. Every slide was visually inspected. The PPTX skill's XML/relationship validator, diagram-geometry checks and a check that no web screenshots/assignment-checklist slides remain were also run. Current software tests were not rerun solely for these documentation artifacts. Coverage, formal peer assessment and final-report evidence remain explicit rather than fabricated.
