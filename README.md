# Auto Secretary

Auto Secretary is a single-user Android app for tasks, routines and a chronological
Now → Next → Later day plan. It reads the device calendar without writing to it, persists task and
step changes transactionally in Room, and refreshes a home-screen widget. Optional AI changes run
locally, remain previews until confirmed, and create a persistent undo entry.

## Build and test

The project needs JDK 21, Android SDK 35 and the checked-in Gradle wrapper. The complete local gate
is one command:

```bash
./gradlew --no-daemon --max-workers=1 qualityGate
```

The four production modules have one-way dependencies:

- `core`: domain and application ports; plain Java without Android
- `infrastructure`: Room, calendar/location, model and GitHub/Android update adapters
- `presentation`: Java/XML UI, ViewModels and feature dialogs against application ports
- `app`: Android composition root, activity shell, worker and widget entry points

`checkArchitecture` enforces these package directions and rejects direct production wall-clock
access outside `SystemTimeProvider`. See [docs/architecture.md](docs/architecture.md).

The release APK contains only the pinned `model-manifest.json`, not the roughly 304 MB Gemma
weights. The app downloads and cryptographically validates those weights only after the user
explicitly enables the AI feature. This keeps ordinary builds and app updates small and
deterministic.

## Data and planning

Room v35 is the stable schema baseline. Obsolete prototypes older than v35 may still be reset, but
every schema change from v35 onward must provide and test an explicit migration. Normal app updates
retain the Room database, preferences, undo journal and separately downloaded model.

The planner orders work by urgency and user directives, then places it into calendar gaps inside
the configured day. Its canonical `TodayTimeline` owns remaining-event selection, stable ordering,
duplicate handling and the preceding-calendar-event relation used by both the app and widget.
Calendar access is read-only and event titles are represented by an explicit visibility value, not
a magic fallback string. All date decisions receive a `TimeProvider`.

## One release path

There is no test-release channel and no manually dispatched phone-release path. A release is just
the automatic, production-signed result of a relevant commit reaching `main`:

1. Feature branches and pull requests run `Android-Prüfung`; they never publish an APK.
2. A relevant push to `main` runs `Handy-Update veröffentlichen` exactly once for that commit.
3. The workflow allocates the next monotonic `versionCode`, runs `qualityGate`, signs the small APK,
   verifies package/version/size/signer, stages a draft, verifies its downloaded assets, and only
   then atomically publishes it as GitHub `Latest`.
4. The installed app reads only that canonical latest release. It resumes the system download
   across process death, checks repository metadata, size, SHA-256, package, monotonic version and
   the installed signing identity, then opens Android's installer once.

Every release contains exactly `AutoSecretary.apk` and `release-metadata.json`. The machine-readable
contract lives in `release/release.properties` and `release/release-metadata.schema.json`. A rerun
for an already published commit is a no-op; a matching unfinished draft is resumed only while its
version still exceeds all published versions.

For a connected-device recovery or development check, `tools/update-connected-phone` installs the
latest published APK with `adb install -r`. Pass `--fresh` explicitly only when deleting the app and
all local app data is intended. The normal laptop-free path is the app's update button.

The release signer is permanent. CI accepts the explicit `ANDROID_RELEASE_*` secrets and retains
the old keystore secret names only as a temporary migration fallback. Android still requires its
own installation confirmation and, once, permission to install apps from this source.

The Gemma weights remain subject to the [Gemma Terms of Use](https://ai.google.dev/gemma/terms) and
[Gemma Prohibited Use Policy](https://ai.google.dev/gemma/prohibited_use_policy); notices ship in
`app/src/main/assets/`.
