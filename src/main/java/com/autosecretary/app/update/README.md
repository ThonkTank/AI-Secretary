# Update Module (`app/update/`)

## Overview

The update module checks the latest published GitHub Release for a newer AutoSecretary build and, when available, offers a direct APK install through the Android system installer.

Release contract:
- Repository: `ThonkTank/AI-Secretary`
- Endpoint: `GET /repos/ThonkTank/AI-Secretary/releases/latest`
- Required release assets: `AutoSecretary.apk` and `version.txt`
- `version.txt` format: one integer `versionCode`

`UpdateChecker` runs automatically on app startup and can also be triggered manually from Settings. Startup checks stay silent when the app is already current or when GitHub/network checks fail. Manual checks show a result dialog.

## Runtime Flow

1. `UpdateChecker` asks `GitHubReleaseUpdateClient` for the latest release.
2. The client reads release assets from the GitHub JSON response, downloads `version.txt`, and compares the remote `versionCode` to `BuildConfig.VERSION_CODE`.
3. If the remote version is higher, `UpdateChecker` shows an update dialog.
4. Before downloading the APK, the app checks `PackageManager.canRequestPackageInstalls()`.
5. If Android blocks installs from this source, the app opens `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` for this package.
6. If allowed, the APK downloads into `cacheDir/update.apk`, `FileProvider` exposes it as a `content://` URI, and `Intent.ACTION_VIEW` starts the system installer with `application/vnd.android.package-archive`.

## Release Publishing

`./gradlew copyToRelease` prepares local release assets by copying `AutoSecretary.apk` into `ops/release/` and incrementing `ops/release/version.txt`.

`./gradlew publishReleaseArtifact` depends on that preparation and creates a GitHub Release with both required assets through `gh release create`. It no longer commits or pushes `ops/release/` as raw files to `main`.

## Testing Notes

The client accepts an injectable latest-release URL for tests. Unit tests use a local JDK `HttpServer` instead of live GitHub traffic.

The installer intent is exposed package-locally for Robolectric assertions. Tests verify that the APK is shared through `${applicationId}.fileprovider`, uses `application/vnd.android.package-archive`, and grants temporary read access.

## Source References

- `/home/aaron/Schreibtisch/projects/references/github-releases/github-rest-releases-latest-and-create.md`
- `/home/aaron/Schreibtisch/projects/references/android-self-update/android-package-install-permission.md`
- `/home/aaron/Schreibtisch/projects/references/android-self-update/androidx-fileprovider-content-uri.md`
