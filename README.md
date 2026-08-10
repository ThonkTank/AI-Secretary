# Auto Secretary

Auto Secretary is a deliberately small Android app for ADHD time blindness and forgetfulness. It does one thing: turn open tasks, due routines, observed behavior, and today's read-only calendar into a calm **Now → Next → Later** queue.

## Product rules

- The focus surface shows at most three blocks.
- A routine is one planning block with a complete, manually ordered step sequence.
- Steps have stable identities, can apply only on selected weekdays, and can be completed individually. Completing the final active step completes the routine occurrence; the block-level action remains a shortcut for completing all steps.
- A missed recurring routine stays due until completed. Only one occurrence can be open, so missed weeks never create a backlog pile.
- Completion time and adjacent completion order are learned locally and influence later plans. An optional morning, midday, or evening preference overrides learned timing for that item.
- Today's open work can be manually moved first, one position earlier/later, or last. **Später** is the shortcut for moving an item last without changing its real due date.
- Calendar events are read as titled busy intervals and their titles are shown as planning context. The app has no calendar write permission and never creates calendar events.
- Local AI changes are generated on the phone from a user-selected MediaPipe `.task` model. Every mutation is shown as a preview and requires explicit confirmation.

## Architecture

The active app is intentionally one module and five small packages:

- `core/` — deterministic recurrence, behavior learning, and focus planning
- `data/` — two SQLite tables plus read-only device calendar access
- `app/` — one screen and one repository boundary
- `widget/` — the home-screen behavior anchor
- `ai/` — local bulk-edit proposal generation; never writes directly

Historical source remains under `.history/` for reference but is not part of the build. An upgrade imports compatible facts from the old `task_core` table once; old feature tables are left untouched.

## Build and test

Requirements: JDK 21 (required by the on-device GenAI dependency), Android SDK 35, and the included Gradle wrapper. App source and bytecode compatibility remain Java 17.

```bash
./gradlew checkArchitecture
./gradlew assembleDebug
```

The APK is written to `build/outputs/apk/debug/AutoSecretary.apk`.

## Local model

Open the app, choose **Modell wählen**, and select a MediaPipe LLM Inference-compatible `.task` model stored on the device. The model is copied into app-private storage. Neither prompts nor task data are sent to a server.

The model deliberately produces only a structured proposal. The app validates it, shows additions/updates/deletions in a confirmation dialog, and applies nothing until the user taps **Änderungen übernehmen**.

## Publishing

The existing release tasks remain available:

- `./gradlew copyToRelease` builds and copies `AutoSecretary.apk` to `ops/release/`, then increments `ops/release/version.txt`.
- `./gradlew publishReleaseArtifact` uses the existing GitHub release path to publish the APK and version file.

Additionally, pushes to `codex/**` or `agent/**` run the Android test workflow and publish a monotonically versioned latest release containing `AutoSecretary.apk` and `version.txt`. CI phone-test builds use the separate package `com.autosecretary.preview`, so they install alongside an existing production/debug build despite differing signing keys.
