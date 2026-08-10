# Auto Secretary

Auto Secretary is a deliberately small Android app for ADHD time blindness and forgetfulness. It does one thing: turn open tasks, due routines, observed behavior, and today's read-only calendar into a calm **Now → Next → Later** queue.

## Product rules

- The focus surface shows at most three blocks.
- Tasks and routines can both contain a stable, manually ordered step sequence. Routine steps may additionally apply only on selected weekdays.
- Steps can be completed individually. Completing the final step completes the task or routine occurrence; the block-level action remains a shortcut for completing all remaining steps.
- A missed recurring routine stays due until completed. Only one occurrence can be open, so missed weeks never create a backlog pile.
- Flexibility and time-of-day preference are independent. Flexible items learn completion time and adjacent order locally; an optional morning, midday, or evening preference remains a stronger soft anchor around which learning may move the item. Inflexible items ignore learned timing.
- Today's open work can be manually moved first, one position earlier/later, or last. **Später** is the shortcut for moving an item last without changing its real due date.
- Calendar events are read as titled busy intervals and their titles are shown as planning context. The app has no calendar write permission and never creates calendar events.
- The Waldmorgen interface follows local sunrise and sunset using approximate device location. Automatic, fixed light and fixed dark modes are available; location is never stored with task data.
- Local AI changes are generated on the phone by the bundled Gemma 3 270M IT model. Every mutation is shown as a preview and requires explicit confirmation. A custom MediaPipe `.task` model remains an optional replacement.

## Architecture

The active app is intentionally one module and five small packages:

- `core/` — deterministic recurrence, behavior learning, and focus planning
- `data/` — two SQLite tables plus read-only device calendar access
- `app/` — one screen and one repository boundary
- `widget/` — the home-screen behavior anchor
- `ai/` — local bulk-edit proposal generation; never writes directly

Historical source remains under `.history/` for reference but is not part of the build. An upgrade imports compatible facts from the old `task_core` table once; old feature tables are left untouched.

## Build and test

Requirements: JDK 21 (required by the on-device GenAI dependency), Android SDK 35, the included Gradle wrapper, and network access for the first model-backed build. App source and bytecode compatibility remain Java 17.

```bash
./gradlew checkArchitecture
./gradlew assembleDebug
```

The first asset merge downloads the 304 MB Gemma task bundle into `build/bundled-ai/`, verifies its pinned SHA-256, and embeds it uncompressed in the APK. Later builds reuse the local file; CI caches it. The APK is written to `build/outputs/apk/debug/AutoSecretary.apk`.

## Local model

Gemma 3 270M IT is included in every complete APK. On first AI use, the app asks for acceptance of the Gemma terms and copies the verified model from the APK into app-private storage. No model download or file picker is required on the phone. Neither prompts nor task data are sent to a server.

The build pins the official model artifact's SHA-256 (`0f7147f1…f2265ef5`). Because Google's canonical Hugging Face repository is gated, the bytes are fetched from a public mirror and accepted only when they match that official hash. **Modell austauschen** can still install another compatible `.task` file as an explicit override.

The bundled weights remain subject to the [Gemma Terms of Use](https://ai.google.dev/gemma/terms) and [Gemma Prohibited Use Policy](https://ai.google.dev/gemma/prohibited_use_policy). Required copies and notices ship in `src/main/assets/`.

The model deliberately produces only a structured proposal. The app validates it, lets the user include or exclude every addition/update/deletion, and applies nothing until the selected changes are confirmed. The last confirmed AI block can be undone as one operation.

## Publishing

The existing release tasks remain available:

- `./gradlew copyToRelease` builds and copies `AutoSecretary.apk` to `ops/release/`, then increments `ops/release/version.txt`.
- `./gradlew publishReleaseArtifact` uses the existing GitHub release path to publish the APK and version file.

Additionally, pushes to `codex/**` or `agent/**` run the Android test workflow and publish a monotonically versioned latest release containing `AutoSecretary.apk` and `version.txt`. CI phone-test builds use the separate package `com.autosecretary.preview`, so they install alongside an existing production/debug build despite differing signing keys.
