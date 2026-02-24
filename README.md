# AI-Secretary

## Repository map

- `src/` → active runtime application code.
- `history/` → non-runtime historical snapshots and references.
- `ops/` → non-runtime operational release artifacts and metadata.

## Build and release tasks

- `./gradlew assembleDebug` builds the debug APK without any automatic Git commit/push side effects.
- `./gradlew copyToRelease` explicitly copies `AutoSecretary.apk` to `ops/release/` and updates `ops/release/version.txt`.
- `./gradlew publishReleaseArtifact` runs the release artifact publication flow (`copyToRelease` + `pushToGitHub`) when you intentionally want to commit and push the updated release artifact.

## Repository layout

- Active code lives under `src/`.
- Legacy snapshots are stored under `history/legacy/` and are intentionally excluded from active source sets.
