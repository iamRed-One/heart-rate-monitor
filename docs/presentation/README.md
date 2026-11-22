# HeaRt project presentation

Open [HeaRt-Project-Demonstration.pptx](HeaRt-Project-Demonstration.pptx) in PowerPoint. [The PDF](HeaRt-Project-Demonstration.pdf) is a preview; it does not replace the editable deck. The deck has 16 slides and presenter notes.

## Narrative

1. Project and workbook thresholds.
2. Exact SP-332 deliverables and marks for all 13 weeks.
3. CSV-first scope, Android extension, architecture and persistence.
4. Verified results, remaining submission evidence and the manual-measurement change.
5. Proposed cloud topology and live-demonstration sequence.
6. Actual browser/Android screenshots, ending with the handoff to the live application.

`requirements.json` was extracted directly from the workbook's **Weekly Tasks** sheet, filtered to **SP-332**. Thirteen rows sum to 100 marks. A required deliverable in these slides is not a claim that its evidence has been submitted. The original workbook is excluded from Git; its checked-in text extraction is [00-workbook-requirements.md](../00-workbook-requirements.md).

## Screenshot provenance

The files in `assets/` are copied captures, not reconstructed interfaces. `heart.png` is the same generated anatomical illustration used by the app.

| Asset | Actual captured state |
|---|---|
| android-home-dark.png | November 22 Samsung phone, dark Home, disconnected; no live value displayed. |
| android-home-light.png | November 22 Samsung phone, light Home, connected/in-progress read; no result displayed yet. |
| android-account-dark.png | November 22 Samsung phone, dark sign-in screen. |
| android-devices-dark.png | November 22 Samsung phone, dark Devices screen, connecting state. |
| web-dashboard.png | Earlier browser capture, explicitly synthetic 72/75/78 BPM fixture with exact rows. |
| web-import.png | Earlier browser capture of a synthetic CSV preview. |
| web-live.png | Earlier browser capture, waiting for the companion; not an active stream. |

The screenshots are historical captures; the live demonstration must show the actual current watch/server state. See [hosting and rehearsal instructions](../20-demo-hosting.md).

## Editable source

The deck uses native text, editable tables and diagram shapes, reusable layouts, named theme colors, sections and speaker notes. Screenshots are embedded images. `build.cjs`, `requirements.json`, `assets.json` and the copied assets reproduce it. The PowerPoint author is the student.

On this machine, generation uses the installed PPTX skill's theme utility and an ignored tooling directory. In Git Bash from the repository root:

```bash
npm install --prefix .impeccable/presentation-tooling pptxgenjs
NODE_PATH="$PWD/.impeccable/presentation-tooling/node_modules" \
PPTX_SKILL_ROOT="$HOME/.agents/skills/pptx" \
node docs/presentation/build.cjs
```

The PPTX skill directory must exist for its `scripts/apply_theme.js` utility. The application/backend dependency files are not modified by presentation tooling. Regenerate the PDF from the resulting deck in PowerPoint after any edits.

## Validation

The final file was opened and rendered by installed Microsoft PowerPoint. Every slide was visually inspected; narrow table headers and diagram/text spacing were corrected. The PPTX skill's XML/relationship validator and a check against all 13 source deliverable strings were also run. Current software tests were not rerun solely for these documentation artifacts. A hosted build, current coverage percentage, peer assessment and final report are not fabricated by this presentation.
