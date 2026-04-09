# `app/` Package

Top-level application package. Contains:

- **Application and Activity bootstrapping** — `AutoSecretaryApplication`, `MainActivity`
- **Manual dependency injection root** — `AppCompositionRoot`
- **Cross-feature settings** — backup, restore, factory reset (`settings/`)
- **Self-update** — version check and APK install from GitHub (`update/`)
- **App-level user preferences** — per-day scheduling window (`Preferences`)

---

## Reading order for a newcomer

1. **`AutoSecretaryApplication.java`** — Android entry point. Creates the composition root and
   registers the daily alarm and the widget-refresh receiver.

2. **`AppCompositionRoot.java`** — The single most important file to understand the app's
   architecture. All features' dependencies are wired here. Read this before diving into any
   feature package to understand what talks to what.

3. **`MainActivity.java`** — The only Activity. Hosts all three feature tabs (Tasks, Budget,
   Meal). Handles deep links from home-screen widgets.

4. **`settings/`** — Backup/restore and factory reset logic. Two classes:
   - `SettingsController` — UI (dialogs, menus)
   - `SettingsDataService` — File I/O and SQLite WAL management

5. **`update/`** — Self-update via GitHub. See `update/README.md` for the full picture.

6. **`Preferences.java`** — Reads per-day scheduling-window times (start/end) from
   SharedPreferences. Used by the task scheduler.

---

## Architecture context

This package sits at the top of the dependency graph. It may import from any feature package,
but **no feature package should import from `app/`** (except `Preferences` — see below).

```
app/ ──────────────────────────────────────────────────────────────────┐
     imports from: features/task/, features/budget/, features/meal/,   │
                   database/, shared/                                   │
└──────────────────────────────────────────────────────────────────────┘
```

`Preferences.java` is an exception: `features/task/` imports it because it provides the
per-day scheduling window that the task scheduler needs. A move to
`features/task/application/internal/` has been considered but deferred (see `REVIEW_BACKLOG.md`).

---

## Dependency injection pattern

`AppCompositionRoot` is a *Composition Root* — a single class that constructs and connects all
objects. This is manual DI: no framework, no annotations, just Java constructors.

The pattern used for each object:

```java
private SomeObject cachedObject;  // null until first use

public synchronized SomeObject getSomeObject() {
    if (cachedObject == null) {
        cachedObject = new SomeObject(…dependencies…);
    }
    return cachedObject;
}
```

`synchronized` prevents double-initialisation if two threads call the getter simultaneously
(e.g., a BroadcastReceiver and MainActivity both starting up at the same time).

**Learn more:** [Composition Root pattern](https://blog.ploeh.dk/2011/07/28/CompositionRoot/)

---

## Settings: backup and restore

The app stores its data in a single SQLite database managed by Room. The settings menu offers:

| Option | Effect |
|---|---|
| Manual Backup | Copies the `.db` file (after WAL checkpoint) to `files/backups/` |
| Restore Backup | Replaces the active `.db` with the selected backup, then recreates the Activity |
| Factory Reset | Creates a safety backup, deletes the `.db`, then recreates the Activity |

SQLite WAL (Write-Ahead Logging) requires flushing pending writes before copying the database.
See `SettingsDataService.java` for detailed explanations of the WAL handling.

**Learn more:** [SQLite WAL mode](https://www.sqlite.org/wal.html)

---

## Self-update (`update/`)

On every app start, `UpdateChecker` fetches `ops/release/version.txt` from GitHub and
compares it to the installed version code. If the remote version is higher, it shows a
dialog and offers to download and install the APK directly.

This is why `./gradlew publishReleaseArtifact` increments `version.txt` and pushes to GitHub.
See `update/README.md` for full details.
