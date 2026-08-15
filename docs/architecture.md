# Architecture boundaries

## Dependency direction

```text
app (composition, Android entry points)
 ├── presentation (Java/XML UI and retained state)
 ├── infrastructure (Android/external adapters)
 └── core (domain, use cases, ports)

presentation ──> core <── infrastructure
```

`core` is a plain Java library. It owns domain invariants, use cases and ports such as calendar,
location, persistence, AI, time and updates. `infrastructure` implements those ports but cannot
reach UI or delivery entry points. `presentation` depends on ports and domain values but cannot
construct or import adapters. `app` is the only composition root and may see all three modules.

The split is enforced twice: Gradle makes forbidden module imports impossible to compile, and
ArchUnit protects package directions inside the app test runtime. `checkClockBoundary` additionally
rejects direct default-zone or wall-clock reads outside `SystemTimeProvider`.

## UI state and effects

Top-level destinations and work-item filters are enums with stable serialized values. Dashboard,
editor, planning settings, AI and update each own mutually exclusive sealed states. Editor and
planning dialogs have dedicated activity-scoped ViewModels and report committed changes through
Fragment Results. Snackbar, error/dialog and installer transitions use consumable effects instead
of nullable render flags; update effects persist their consumption through `SavedStateHandle`.

`MainActivity` only hosts and navigates. `TodayFragment`, `WorkItemsFragment`, `AiFragment` and
`UpdatePanelFragment` own independent XML/ViewBinding feature trees and rendering; ViewModels
remain the only retained state owners. Dialog fragments obtain activity-scoped
ViewModels through the narrow factory capability instead of feature-specific activity interfaces.

## Time and calendar

All production decisions receive `TimeProvider`; only `SystemTimeProvider` reads the system clock
or default time zone. `TodayTimeline` is the canonical query for the remainder of a day and supplies
stable row identities, ordering, the preceding calendar occurrence and the next refresh boundary.
The app and widget consume the same result rather than rebuilding ordering independently.

Calendar rows carry provider-derived stable IDs and explicit title visibility. Query projections
are read by column name, occurrences are deduplicated, and hidden titles never double as logic
sentinels.

## Data evolution

Schema 35 is the stable Room baseline. Destructive fallback is constrained to prototype schemas
1–34, and a pinned schema hash rejects accidental edits to that baseline. Every future schema
version requires an explicit migration and upgrade test. PR CI additionally performs a real
N→N+1 `adb install -r` and verifies package/UID, Room, preferences, undo and model retention.
Updates use the same package and permanent signer, so Android retains the application data.

## Distribution contracts

`main` is the sole release trigger. The workflow is idempotent by commit, allocates versions from
all existing release tags, and uses a draft as the transaction boundary. It publishes `Latest` only
after locally and remotely downloaded assets agree.

The app update port is separated into release-feed, HTTP trust, resumable system download, Android
package evidence and pure verification adapters. Verified future APKs survive process recreation;
installed and stale packages are cleaned up. The optional AI model is a separate pinned artifact
described by an app asset manifest and validated before atomic installation into private storage.

## Model compatibility

Model compatibility has two deliberately distinct verification levels. The path-filtered GitHub
workflow always downloads the exact artifact revision from `model-manifest.json`, enforces HTTPS
across at most five redirects, verifies its byte count and SHA-256, and compiles the Android device
suite. The manifest retains the original LiteRT source revision and names the public,
byte-identical mirror revision separately; the pinned size and digest are the trust anchor.
Real MediaPipe initialization and the German evaluation suite require a physical Android device:
run `./gradlew modelCompatibilityTest` with exactly one authorized device attached.

A self-hosted Actions runner labeled `autosecretary-android-device` runs the same task automatically
when the repository variable `AUTOSECRETARY_MODEL_DEVICE_RUNNER` is set to `enabled`. The physical
job is visibly skipped when that hardware contract is not configured; a hosted emulator result is
never presented as proof of model compatibility.
