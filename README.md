# Auto Secretary

Auto Secretary is a single-user Android app for tasks, routines and a chronological
Now → Next → Later day plan. It reads the device calendar without writing to it, keeps task and
step changes in Room transactions, refreshes the home-screen widget, and runs the bundled Gemma 3
270M model locally. AI changes are previews until confirmed and create a persistent undo entry.

## Build and test

The project needs JDK 21, Android SDK 35 and the checked-in Gradle wrapper. Every APK has package
ID `com.autosecretary` and contains the pinned local model.

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

`assembleDebug` is for local development and uses the local debug key. Phone releases are built
only by `.github/workflows/android-release.yml`; local debug builds are not a second distribution
channel.

## Data format

Room v35 is the clean pre-stable schema. There is no historical data migration or archive importer.
During the initial test weeks, a fundamental schema change may deliberately clear test data by
using Room's destructive fallback. Once this schema is declared stable, every later schema change
must add a specific Room migration so normal app updates retain installed data.

Builds that keep the same schema update the installed app in place and retain its Room database,
preferences, undo journal and locally prepared model.

## Planning and background work

The planner orders work by urgency and user directives, then places it into calendar gaps inside
the configured day. Calendar access is read-only. Explicit morning/midday/evening preferences bound
learned completion times; learning can be enabled or disabled independently for each task or
routine. Coarse location is observed only while the app is active and the last value is stored
locally for travel-aware daylight rendering.

Planning runs when the app opens and after relevant user actions. Calendar changes are observed
while the activity is active. One 30-minute WorkManager job refreshes widgets and is allowed to
show a day transition slightly late.

## Releases and updates

There is one phone-update workflow, one package, one permanent signing identity and one
monotonically increasing Android `versionCode` sequence. After a completed implementation passes
the full local gate, the current branch is pushed and `Handy-Update veröffentlichen` is explicitly
dispatched for that exact commit. Normal pushes do not publish large APKs. The workflow publishes
only after its own test, lint, package, model and signing checks succeed.

The workflow uses the permanent `KEYSTORE_BASE64` and `KEYSTORE_PASSWORD` repository secrets,
verifies the expected certificate fingerprint, and publishes one normal GitHub `Latest` release
with:

- `AutoSecretary.apk`
- `release-metadata.json` with package, version, hash and signer

The in-app updater examines only that canonical latest release. One tap downloads and verifies the
APK, then opens Android's package installer. The app rejects an APK unless its package ID,
SHA-256, version and signer match. Android still requires its own installation confirmation and,
the first time, permission to install apps from this source. Every release uses the permanent key
and updates in place.

The Gemma weights remain subject to the [Gemma Terms of Use](https://ai.google.dev/gemma/terms) and
[Gemma Prohibited Use Policy](https://ai.google.dev/gemma/prohibited_use_policy); notices ship in
`src/main/assets/`.
