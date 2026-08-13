# Operations

- `test_schedule.sh`: runs all JVM/architecture tests, builds and installs `devDebug`, launches it,
  checks the main navigation and fails on a crash. It never uninstalls production.
- `check_only.sh`: verifies an already running preview process and crash buffer.
- `adb_database_bridge.sh`: verifies the production package/certificate and exact v27/v30 schema,
  exports DB/WAL/SHM with
  hashes, and stages the archive for the first-run app importer. Exports go to ignored
  `ops/local-backups/`.
- `device_release_gate.sh`: verifies both signed APKs and exercises the real upgrade, schema marker,
  reboot, day/calendar refresh, next-version update, 20 typed German commands and a real local-model
  inference. After proving the code-7 update it reinstalls code 6 and reimports the same externally
  secured Build-4 archive, so the device is not stranded on an unpublished future version.
  `device_release_gate.sh ai` runs only the non-destructive side-by-side Preview gate.
  Reports from complete production runs go to ignored `ops/local-gates/`.

All device scripts discover `adb` from `PATH`; override with `ADB=/absolute/path/to/adb`.

## Safe upgrade fallback

Export first:

```bash
ops/adb_database_bridge.sh export com.autosecretary
```

Copy the archive and its `.sha256` outside the repository. Stage it for Android's document picker:

```bash
ops/adb_database_bridge.sh stage ops/local-backups/AutoSecretary-build4-TIMESTAMP.zip
```

The new app accepts the archive only on first run before its focus database exists, verifies every
hash, the Build-4 certificate marker and the matching exact v27/v30 Room identity, checkpoints
captured WAL data in isolation, then installs
the consolidated database with one atomic rename. WorkManager or other unrelated databases remain
untouched. Room then migrates and the importer compares the expected and actual core counts.
`device_release_gate.sh fallback ...` is the only scripted path that offers an uninstall, and it
requires the literal interactive confirmation `ARCHIV-EXTERN-GESICHERT`.
The direct, same-signer path requires the same confirmation before installing over production,
although it never uninstalls the package.

Production artifacts are created only by the manual signed GitHub workflow after the reviewed gate.
No APK is checked in under `ops/release/`.

The manual `Android upgrade-gate APKs` workflow is the non-publishing exception needed to exercise
the real v2.0.0 → v2.0.1 device update before production can be released. It has read-only repository
permissions, signs both APKs with the selected permanent identity, uploads them as a one-day CI
artifact and never creates or updates a GitHub Release. Feed those two verified APKs to
`device_release_gate.sh`; do not publish either gate artifact.
