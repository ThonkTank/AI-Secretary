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

Top-level destinations and work-item filters are enums with stable serialized values. Dialogs use
an activity-scoped `ViewModelProvider.Factory` capability rather than requiring the activity to
implement feature-specific host interfaces. The update flow is a sealed state machine; settings
and installer launches are identified one-shot effects persisted through `SavedStateHandle`.

`MainActivity` is the Android lifecycle/composition shell for the existing XML screen. Dedicated
Today, complete-list, navigation, AI and update controllers own their bindings and rendering;
ViewModels remain the only retained state owners. Dialog fragments obtain activity-scoped
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
1–34. Every future schema version requires an explicit migration and upgrade test. Updates use the
same package and permanent signer, so Android retains the database and preferences.

## Distribution contracts

`main` is the sole release trigger. The workflow is idempotent by commit, allocates versions from
all existing release tags, and uses a draft as the transaction boundary. It publishes `Latest` only
after locally and remotely downloaded assets agree.

The app update port is separated into release-feed, HTTP trust, resumable system download, Android
package evidence and pure verification adapters. Verified future APKs survive process recreation;
installed and stale packages are cleaned up. The optional AI model is a separate pinned artifact
described by an app asset manifest and validated before atomic installation into private storage.
