# UML model set

**Status:** Draft design, not reverse-engineered implementation. Actor: registered wearable owner. The assessor uses the same user flows with synthetic data.

## Use-case model

Mermaid does not provide a native UML use-case renderer here, so this is a use-case view using actor and oval notation. The catalogue supplies the preconditions, alternatives and outcomes needed to interpret it.

```mermaid
flowchart LR
    U["«actor» Wearable owner"]
    subgraph S[Wearable Heart-Rate Data Viewer]
        A([Register / sign in])
        B([Preview CSV])
        C([Confirm import])
        D([View dashboard and recording])
        E([Filter readings])
        F([Rename recording])
        G([Delete recording])
        H([Sign out])
        V([Validate CSV])
    end
    U --- A
    U --- B
    U --- C
    U --- D
    U --- E
    U --- F
    U --- G
    U --- H
    B -. "«include»" .-> V
```

| Use case | Preconditions | Main flow | Alternative / postcondition |
| --- | --- | --- | --- |
| UC-01 Account access | Register: unused email; login: existing account | Enter credentials, submit, open workspace | Invalid input gets generic failure; logout invalidates account session |
| UC-02 Import recording | Signed in; supported file available | Select source/label/file, preview, inspect, confirm | Invalid file: no recording; expired preview: re-upload; retry: existing recording |
| UC-03 Explore history | Signed in | Select recording or all, select UTC dates, read summary/chart/table | Empty range shows no-data state; inaccessible recording returns 404 |
| UC-04 Manage recording | Signed in and owner | Rename, or request deletion and confirm | Invalid label rejected; cancel preserves data; deletion removes associated readings |

## Class diagram

```mermaid
classDiagram
    class User {
        UUID id
        string email
        string passwordHash
    }
    class AccountSession {
        string tokenDigest
        datetime expiresAt
    }
    class ImportPreview {
        UUID id
        string fileDigest
        datetime expiresAt
        string status
    }
    class RecordingSession {
        UUID id
        string label
        string sourceType
        string fileDigest
    }
    class HeartRateReading {
        long id
        datetime measuredAt
        int bpm
    }
    class CsvParser {
        validate(file) ValidationResult
    }
    class ImportService {
        preview(owner, file)
        confirm(owner, previewId)
    }
    class RecordingRepository {
        findOwned(owner, id)
        saveAtomically(owner, preview)
        deleteOwned(owner, id)
    }
    class SummaryService {
        summarise(owner, filters)
    }
    User "1" --> "0..*" AccountSession : authenticates
    User "1" --> "0..*" ImportPreview : owns
    User "1" --> "0..*" RecordingSession : owns
    RecordingSession "1" *-- "1..*" HeartRateReading : contains
    ImportPreview "0..*" --> "0..1" RecordingSession : resolves to
    ImportService --> CsvParser
    ImportService --> RecordingRepository
    SummaryService --> RecordingRepository
```

## Sequence diagram: import a CSV

```mermaid
sequenceDiagram
    actor Owner
    participant Browser
    participant Routes as FastAPI routes
    participant Service as ImportService
    participant Parser as CsvParser
    participant DB as PostgreSQL
    Owner->>Browser: Select CSV, label and source
    Browser->>Routes: POST preview with session and CSRF token
    Routes->>Service: preview(authenticated owner, bounded file)
    Service->>Parser: Decode, parse and validate
    alt Invalid file
        Parser-->>Service: Error count and record details
        Service-->>Browser: 422 errors, no recording
    else Valid file
        Parser-->>Service: Validated rows and metadata
        Service->>DB: Store expiring owner-bound preview
        Service-->>Browser: Preview ID and summary
        Owner->>Browser: Confirm import
        Browser->>Routes: POST confirmation with CSRF token
        Routes->>Service: confirm(owner, preview ID)
        Service->>DB: Begin transaction; lock owned preview
        alt Expired or inaccessible
            DB-->>Service: No usable preview
            Service-->>Browser: 410 expired or 404 inaccessible
        else Already committed or identical file
            Service->>DB: Resolve existing owned recording
            Service-->>Browser: Existing recording ID
        else New valid import
            Service->>DB: Insert recording and all readings
            Service->>DB: Mark preview consumed; clear payload; commit
            Service-->>Browser: 201 recording ID
        end
    end
```

On a database exception the entire new import transaction rolls back; the preview remains available until its original expiry. Concurrent identical imports resolve using the account/file-digest unique constraint and return the surviving recording. Server details are not exposed to the user.

## Activity diagram: main flow

```mermaid
flowchart TD
    S((Start)) --> L[Sign in]
    L --> U[Choose CSV, label and source]
    U --> V[Validate limits, structure and readings]
    V --> Q{Valid?}
    Q -- No --> E[Show errors; save no recording]
    E --> U
    Q -- Yes --> P[Display expiring preview]
    P --> C{Confirm?}
    C -- Cancel --> X[Discard preview]
    C -- Yes --> O{Owned and usable?}
    O -- No --> R[Explain failure or expiry]
    R --> U
    O -- Yes --> T[Commit atomically or return existing recording]
    T --> D[View summary, chart and table]
    D --> F[Apply UTC date filters]
    F --> D
    D --> Z((End))
    X --> Z
```

## Review checklist

Check names against the database and API documents. Confirm both owner checks and failure paths. Export legible figures before submission if the lecturer requires images or formal UML tooling. No diagram asserts that a physical device is connected.
