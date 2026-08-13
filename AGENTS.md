# Repository Guidelines

## Structure

This is a single-module Android app. Active code is under `src/main/` and follows inward-facing
layers: `domain` ← `application` ← `data`/`platform`/`ui`, composed only in `app`. `ai` produces
typed proposals without persistence access. `background` and `widget` are Android entry points.
Reference-only history is under `.history/`. Never stage the local evidence mirror at
`/home/aaron/Schreibtisch/projects/references/` without explicit approval.

## Safe commands

- `./gradlew checkArchitecture`: JVM, Robolectric, Room migration and ArchUnit tests
- `./gradlew testDebugUnitTest`: stable alias for the model-free `devDebug` behavior suite
- `./gradlew lintDevDebug`: Android API, manifest and resource validation for minSdk 26
- `./gradlew assembleDevDebug`: fast preview APK without the bundled model
- `./gradlew assembleFullDebug`: complete preview APK; may download the pinned model
- `./gradlew installDevDebug`: install the side-by-side preview build
- `ops/test_schedule.sh`: optional connected-device preview smoke test
- `ops/device_release_gate.sh`: destructive-by-confirmation production upgrade/device gate

There is intentionally no local publish/version-bump Gradle task. Production releases are signed and
published only by `.github/workflows/android-release.yml`. Do not invoke or emulate that workflow
unless the task is explicitly a release.

## Conventions

Use Java 17 style already present: four spaces, declaration-line braces and descriptive names under
`com.autosecretary.*`. Domain models are immutable. Business mutations go through application
contracts and Room transactions; Activities/adapters render state only. `AppExecutors` is the sole
production thread-pool owner. Keep `checkArchitecture` green.

External-source decisions must use the `source-references` skill when it is available and preserve a
local citation path. Changes to agent-facing instructions must use `agent-instruction-engineering`
when that skill is available. If a required global skill is unavailable, state that limitation and
make only the smallest evidence-backed instruction correction.

## Tests

Required invariants belong in `src/test`. Prefer pure domain tests, then Robolectric + in-memory or
file-backed Room for ViewModel → application → domain → data flows. `src/androidTest` is reserved
for the explicitly required `fullDebug` 20-case typed-command suite, strict parser rejection and
real bundled-model smoke inference; do not expand it into a general Espresso suite. Migration tests
must open an actual old-version SQLite file through Room so schema validation runs. Name tests after
the invariant they protect.

## Commits and PRs

Use short imperative subjects. Describe affected layers, behavior, migrations and validation. Add
screenshots for visible UI changes. Never stage local backups, keystores, model files or source
evidence.
