# Operations

The scripts in this directory support the current Android app and its connected-device schedule
check.

- `test_schedule.sh` builds the debug APK, installs it on a connected Android device, starts the
  app, triggers schedule generation and validates the generated seven-day summary.
- `check_only.sh` repeats the schedule assertions against an already running installation without
  rebuilding or reinstalling it.
- `lib/` contains the shared device, log parsing, database and assertion helpers used by those two
  scripts.
- `release/` contains the APK and version file produced by the Gradle release tasks.

## Connected-device check

The scripts require Android platform tools and a device with USB debugging enabled. Set `ADB` in
`lib/common.sh` to the local `adb` binary, then run:

```bash
ops/test_schedule.sh
```

Optional flags are `--verbose` for the complete schedule log and `--pull-db` for a local database
copy. To validate the currently running app without rebuilding it, run:

```bash
ops/check_only.sh
```

## Release artifacts

`./gradlew copyToRelease` builds `AutoSecretary.apk`, copies it to `ops/release/` and advances the
version file. `./gradlew publishReleaseArtifact` invokes the existing GitHub publishing path. Do not
edit the generated APK or version file manually.
