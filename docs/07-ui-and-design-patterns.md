# UI/UX wireframes, patterns and SOLID design

**Status:** Proposed screen and module designs. These are low-fidelity wireframes, not implemented screenshots.

## Navigation and screen inventory

Primary navigation: Dashboard, Import, History and Sign out. Account pages precede the workspace. All measurement times are labelled UTC. The interface describes recorded data and the latest stored reading, never a live connection. Implement screens in React using Astryx components and tokens; inspect the installed CLI's component documentation before selecting APIs. Theme selection remains open. The CLI installation itself does not install runtime UI components into this project.

### Account access

```text
+---------------------------------------------------+
| Wearable Heart-Rate Data Viewer                    |
| Sign in                   [Create account]        |
| Email       [________________________________]    |
| Password    [________________________________]    |
| [Sign in]                                         |
| Inline validation / generic credential error      |
+---------------------------------------------------+
```

### Import and preview

```text
+---------------------------------------------------+
| Dashboard   Import   History             Sign out |
| Import recorded heart-rate data                   |
| [CSV format help] [Download synthetic sample]     |
| Session label [______________________________]    |
| Device label  [____________________] (optional)   |
| Source        ( ) Recorded   ( ) Synthetic         |
| CSV file      [Choose file]                       |
| [Validate and preview]                            |
| Error: record 4, heart_rate must be a whole number |
|                                                   |
| Preview: 3 readings | 08:00–08:02 UTC | Synthetic   |
| Timestamp (UTC)                    BPM            |
| 2026-11-01 08:00:00                 72             |
| ... up to ten rows                                |
| Expires in 15 minutes   [Cancel] [Confirm import]  |
+---------------------------------------------------+
```

The preview is a distinct state with server-owned data. Confirmation is disabled after validation errors and on expiry; server checks are authoritative. Duplicate confirmation opens the existing recording and explains the outcome. Never show a successful-save message before the server confirms the transaction.

### Dashboard and recording detail

```text
+---------------------------------------------------+
| Dashboard   Import   History             Sign out |
| Recorded heart-rate overview                      |
| Recording [All / selected]  Source: recorded/mixed |
| From [UTC date] To [UTC date] [Apply] [Clear]       |
| Count [3]  Min [72]  Max [78]  Average [75.0 BPM]   |
| Latest stored: 78 BPM at 2026-11-01 08:02 UTC       |
|                                                   |
| BPM     Heart-rate chart                          |
|  78 |                         *                   |
|  75 |             *                               |
|  72 | *                                           |
|     +---------------------------- UTC time        |
| [Chart] [Exact readings table]                    |
| Aggregated when needed: interval and count shown  |
| Table: timestamp | BPM | recording     [Next page] |
+---------------------------------------------------+
```

The chart must not imply measurements exist between separate recording sessions. For aggregated results disclose bucket width and show minimum/maximum range and mean; exact rows remain available in the table. Summaries always use full filtered data.

### History and management

```text
+---------------------------------------------------+
| Recording history                     [Import]   |
| Label       Source       Count   Recorded range   |
| Morning     Recorded     120     08:00–10:00 UTC   |
| Demo        Synthetic      3     08:00–08:02 UTC   |
| [Open] [Rename] [Delete]              [Next page] |
|                                                   |
| Delete "Morning" and its 120 readings?            |
| This cannot be undone.       [Cancel] [Delete]    |
+---------------------------------------------------+
```

## Interaction and accessibility rules

- Use clear page titles and units on numbers and chart axes; keep source status visible.
- At narrow widths, stack summary cards and filters; allow the table to scroll without making the whole page overflow.
- Provide visible focus, semantic form labels, associated errors and keyboard-operable confirmation. Move focus to validation summary or dialog as appropriate and restore it on close.
- Charts have table equivalents; never use colour alone to convey source or errors. Aim for WCAG AA contrast and verify actual colours later.
- Loading prevents accidental repeated submission but does not replace server idempotency. Preserve user-entered label and dates after recoverable errors.
- No-data screens distinguish no imports from a filter with no matching readings. Provide Import or Clear filters as the next step.

## Design pattern 1: Strategy for import parsing

Define an import parser interface returning a normalised validation result. `CanonicalCsvParser` is its initial implementation. `ImportService` depends on that interface, not on request objects or a specific CSV parsing implementation. A future verified vendor adapter can implement the same interface without changing transaction logic. Do not build speculative adapters now.

Verification later: replace the parser with a deterministic fake in a service test; prove the service handles valid/error results without opening a file or contacting the watch. Document the actual implementation and file references for Week 6 evidence.

## Design pattern 2: Repository for persistence

Use a recording repository interface for owner-scoped lookups, summary queries and atomic recording persistence. The PostgreSQL implementation handles joins, constraints and transactions. Services do not construct SQL or depend on database-specific result shapes. Transaction ownership must be explicit so a repository call cannot commit half an import.

Verification later: use real PostgreSQL integration tests to prove atomicity and ownership; service tests may use a fake repository for branching behaviour. Do not claim SQLite proves PostgreSQL semantics.

## Planned SOLID module: ImportService

| Principle | Planned application | Later verification |
| --- | --- | --- |
| Single responsibility | Coordinate validation/preview/commit; parser handles CSV, repository handles storage | Inspect module boundaries and failure tests |
| Open/closed | Select a parser implementation through an interface | Fake alternate parser works without editing service logic |
| Liskov substitution | All parsers obey one validation result contract and error conventions | Contract tests cover implementations |
| Interface segregation | Small parser and import repository interfaces | No unrelated chart/auth operations on parser |
| Dependency inversion | Inject parser, repository and clock abstractions | Deterministic expiry tests without real waiting |

This is a design plan. The workbook also asks for an applied SOLID-compliant module; that evidence can only be supplied after implementation.
