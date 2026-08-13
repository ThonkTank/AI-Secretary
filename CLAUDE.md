# Implementation Guide

## Source of truth

- Version: `version.properties`
- Database: `FocusDatabase`, version 34, exported schemas in `schemas/`
- Composition: `AppGraph`
- Thread pools: `AppExecutors` (`database`, `io`, `ai`)
- Screen state: `MainViewModel` / `MainUiState`
- Planner: `domain/FocusPlanner`
- Release: `.github/workflows/android-release.yml`

Do not infer architecture from `.history/`; it is reference-only.

## Boundary rules

`domain` is Java-only and immutable. `application` depends only on `domain` and Java types. `data`
implements application repositories with Room. `platform` implements device/network/preferences
ports. `ui` depends on application/domain contracts, never concrete adapters; only the Android host
`MainActivity` may request ViewModel factories and platform effects through the `app` composition
facade. `ai` can depend on the domain but cannot persist. `app` is the only composition layer.
These rules execute on bytecode in `ArchitectureRulesTest`.

## Persistence invariants

- Never use SQLite `REPLACE` for parent rows with cascading children; use Room `@Upsert`.
- Step identity is the ID, never list position. Reordering temporarily parks positions inside the
  same transaction to satisfy the unique `(workItemId, position)` index.
- AI change sets validate revisions, write atomically and store one bounded undo journal entry.
- Undo checks post-change revisions and must refuse to overwrite newer work.
- A v27–33 database is backed up before Room opens it. The production-observed v27 and v30 schemas
  additionally require their exact Room identities. Unsupported legacy semantics become explicit
  migration candidates; never silently approximate them.

## Planner invariants

Priority ordering and gap placement are separate phases. Calendar intervals and transition buffers
produce gaps across the configured horizon. A task must finish before its deadline. Assignments are
returned chronologically. Manual ordering persists only a relative directive for the moved item.
Learning uses at most 20 samples per item from 90 days, requires at least three samples, and remains
bounded by explicit time preferences.

## Build and validation

```bash
./gradlew checkArchitecture
./gradlew lintDevDebug
./gradlew assembleDevDebug
```

Use `assembleFullDebug` only when the embedded-model path is relevant. It downloads a 304 MB pinned
asset; `dev` deliberately avoids that cost. `ops/device_release_gate.sh` assembles and installs the
manual device suite without keeping a Gradle device-test daemon alive. It checks 20 typed German
commands, strict rejection and a real bundled-model inference on an Android device.

Production output is valid only after the version-controlled upgrade gate is `PASSED` and CI verifies
the selected permanent identity. Prefer Build-4 SHA-256
`1e0e90509d79efacebaec1af024f2577d7799cf5534e841db7417184287dbfb2`. If that key remains
unrecoverable, the verified ADB bridge and device gate must precede use of fallback SHA-256
`79eb85409ede6aa014b125dd6190206c9809f0b948a5f92342a99376f81d0fef`. The historical Build-4 APK
has an Android Debug certificate DN; that exact fingerprint alone is accepted. A current or
arbitrary debug key is never a production identity.
