# Auto Secretary

Auto Secretary turns immutable tasks, due routines, local completion evidence and a read-only
calendar into a calm, multi-day Now → Next → Later plan. Tasks and routines can contain stable,
independently completed steps. Local AI may propose typed changes, but only an explicit user
confirmation writes an atomic change set.

## Active architecture

This remains one Android module. Package boundaries inside it are enforced by ArchUnit:

- `domain/`: immutable `Task`, `Routine`, `Step`, time windows and the deterministic planner
- `application/`: repository/platform contracts and atomic use cases
- `data/`: Room v34, normalized step/completion tables and tested v27–33 migrations
- `platform/`: calendar, clock, preferences and signed-update adapters
- `ui/`: the Activity host, retained ViewModels, pure rendering adapters, immutable editor state and lifecycle-safe DialogFragments
- `ai/`: on-device inference and typed proposals; no persistence dependency
- `background/`: configured-day-start planning plus periodic calendar/widget refresh via WorkManager
- `app/`: only application startup, dependency wiring, ViewModel factories and executor ownership
- `widget/`: the home-screen rendering and actions

Legacy snapshots under `.history/` are reference-only. They are not in the runtime source set.

## Build and test

Requirements are JDK 21, Android SDK 35 and the checked-in Gradle wrapper. Source/bytecode
compatibility is Java 17.

```bash
./gradlew checkArchitecture       # all JVM/Robolectric tests plus real ArchUnit rules
./gradlew testDebugUnitTest       # stable model-free debug test alias
./gradlew lintDevDebug            # min-SDK, manifest and Android resource checks
./gradlew assembleDevDebug        # fast preview build, no 304 MB model download
./gradlew assembleFullDebug       # complete preview build with the pinned local model
```

The fast APK is `build/outputs/apk/dev/debug/AutoSecretary-devDebug.apk` and installs as
`com.autosecretary.preview`. Preview builds use a separate 1,000,000+ versionCode namespace so they
can upgrade older 10xxx previews without changing production version ordering. `full` variant tasks
prepare the pinned model, use explicit network
timeouts, verify SHA-256, and reuse the ignored `.gradle/bundled-ai/` cache even after `clean`;
`dev` tasks never depend on it.

## Persistence and upgrades

Room schema history is exported to `schemas/`. Before opening a v27–33 database, the app creates a
byte-for-byte recovery copy (including WAL/SHM when present) under its private files directory. The
migration imports the current focus core, completion evidence and compatible preferences. Complex
old recurrence rules and fixed appointments are quarantined as migration candidates instead of
being silently reinterpreted. A visible report explains what was imported and what remains only in
the recovery copy. Its hash-documented ZIP can be saved outside the app through Android's system
share chooser before lossy migration decisions are confirmed.

The fixed upgrade fixture at `src/test/resources/fixtures/build4-v27.db` uses the exact 27-table
schema generated from tag `build-4` (`f5d9d0bc49b1caf690bb12a8a57f193042428db9`); its synthetic
rows, Room identity hash and fixture SHA-256 are pinned in the adjacent properties file. A real,
authorized Pixel 8 subsequently proved that the currently installed production-signed app had
already advanced to database v30, Room identity `51ffa9b42fba4bd0b74c6eb9d8809c00`, and versionCode 5.
`build4-v30.db` is a schema-identical reconstruction containing synthetic rows only. Both v27 and
v30 are therefore exact upgrade fixtures; v28/v29 remain explicit direct migration paths.

Steps live in normalized `steps`, `step_days` and `step_completions` tables. IDs are independent of
positions. AI batches, manual order directives and their bounded undo payloads are committed in
Room transactions; undo survives process restarts and refuses to overwrite newer edits.

## Planning and background behavior

The planner first orders candidates, then places each one into globally available gaps. Output is
chronological. Day bounds, morning/midday/evening windows, transition time, calendar buffers and the
1–14 day horizon are user-configurable. Learning requires at least three recent observations and is
bounded by explicit time preferences.

WorkManager refreshes the plan at the configured local day start (07:00 by default) and schedules
the following wall-clock occurrence, including across DST changes. A separate 30-minute safety
loop refreshes calendar-derived planning and widgets. Boot, date, package replacement, clock and
timezone changes re-register both paths. While the process is alive, a permission-aware debounced
calendar observer refreshes immediately.

## Local AI

The `dev` flavor accepts a user-selected MediaPipe `.task` model but embeds none. The `full` flavor
ships the revision- and hash-pinned Gemma 3 270M IT asset. Inference, prompts and task state remain
on-device.
Explicit supported German commands are first compiled into typed proposals without guessing;
unrecognized wording falls through to the local model and the same strict parser. Existing step IDs
and revisions are included in proposals. Selected changes are applied in one transaction and
produce one persistent undo entry.

The weights remain subject to the [Gemma Terms of Use](https://ai.google.dev/gemma/terms) and
[Gemma Prohibited Use Policy](https://ai.google.dev/gemma/prohibited_use_policy); notices ship in
`src/main/assets/`.

## Production releases and updates

`version.properties` is the only app-version input. Production publishing is manual-only through
the `Android production release` workflow. The workflow additionally requires a reviewed
`ops/release/upgrade-gate.properties` with every device/upgrade check set to `PASSED`; the checked-in
gate intentionally remains `BLOCKED` until a real device run exists.

When the Build-4 key is recovered, its secrets are:

- `PRODUCTION_KEYSTORE_BASE64`
- `PRODUCTION_STORE_PASSWORD`
- `PRODUCTION_KEY_ALIAS`
- `PRODUCTION_KEY_PASSWORD`

The first repaired production target is v2.0.0 with versionCode 6: the authorized physical-device
baseline already uses versionCode 5, so reusing 5 would not be discoverable as an update. The
preferred identity is the Build-4 fingerprint
`1e0e90509d79efacebaec1af024f2577d7799cf5534e841db7417184287dbfb2`. Since that private key was not
found locally—and the historical APK identifies it as an Android Debug certificate—the only
permitted fallback first exports and verifies the exactly Build-4-signed v27 or v30 database,
then uses the already-established permanent key
`79eb85409ede6aa014b125dd6190206c9809f0b948a5f92342a99376f81d0fef`. CI verifies the selected
certificate exactly. It publishes `AutoSecretary.apk`, its SHA-256 file and `version.txt`. Preview
CI creates only prereleases and can never update `Latest`. Starting with versionCode 6, each
production release also publishes its signer fingerprint; CI rejects every later attempt to switch
the permanent production identity.

The app checks the latest non-prerelease GitHub release. Before opening Android's installer it
verifies the version, SHA-256, package ID and signer equality with the installed app. If the original
Build-4 key cannot be recovered, use `ops/adb_database_bridge.sh` to create the verified archive and
select it in the new app's first-run importer. `ops/device_release_gate.sh` then proves migration,
reboot, day/calendar autonomy, a 2.0.0→2.0.1 update and the 20-case `fullDebug` on-device AI
command evaluation plus a real bundled-model inference. No production release is allowed before
its reviewed report exists.
After the ephemeral code-7 update proof, the gate restores the externally secured source archive
under code 6 and verifies Room v34 again; it never leaves the production device on an unpublished
future version.
The reviewed gate also records SHA-256 for that source archive and both tested APKs. Release CI
publishes only when its newly built code-6 APK is byte-identical to the device-tested candidate.

Because that update proof needs two identically signed APKs before publishing is allowed, the
manual `Android upgrade-gate APKs` workflow creates code 6 and an ephemeral code 7 as a short-lived
CI artifact with read-only repository permissions. It never creates a Release. The production
workflow remains the only publisher and still rejects an unreviewed device-gate report.
