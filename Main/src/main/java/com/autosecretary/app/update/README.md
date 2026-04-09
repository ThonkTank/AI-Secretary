# Update Module (`app/update/`)

## Overview

Implements self-update functionality: the app periodically checks GitHub for a newer version and prompts the user to install it if available.

**Two-stage process:**
1. **Version check**: Fetch the remote version code from `ops/release/version.txt` on GitHub
2. **APK download & install**: Download the new APK from `ops/release/AutoSecretary.apk` and trigger system installer

The entire process runs asynchronously on a background executor to avoid blocking app startup. All UI operations (dialogs, install intent) post to the main thread.

---

## Key Class: `UpdateChecker`

### Threading Model

- **Version check**: Asynchronous, runs on a background executor
- **UI callbacks**: Post to the main thread via `Handler`
- **Safety**: Uses `WeakReference<Activity>` to prevent memory leaks when background tasks outlive the Activity lifecycle (e.g., user closes app during download)

### Integration Points

**When to instantiate:**
- In `MainActivity.onCreate()` or via `AppCompositionRoot`

**Example:**
```java
UpdateChecker checker = new UpdateChecker(this, executorService);
checker.checkForUpdate();  // Safe to call on main thread; internally async
```

**Lifecycle:**
- Create once per activity (e.g., in `onCreate()`)
- Call `checkForUpdate()` on app startup
- Android system handles cleanup; no explicit shutdown needed

---

## External Dependencies

### GitHub URLs (Hardcoded)

The module fetches from two fixed GitHub URLs:

1. **Version file**: `https://raw.githubusercontent.com/ThonkTank/AI-Secretary/main/ops/release/version.txt`
   - Format: Single integer (version code) on a single line
   - Example: `42`

2. **APK artifact**: `https://github.com/ThonkTank/AI-Secretary/raw/main/ops/release/AutoSecretary.apk`
   - Binary APK file

### FileProvider Configuration

The APK install flow requires `FileProvider` configured in `AndroidManifest.xml`:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_provider_paths" />
</provider>
```

And in `res/xml/file_provider_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path name="cache" path="." />
</paths>
```

**Why FileProvider?** Android 7.0+ (API 24+) restricts direct `file://` URIs. `FileProvider` provides a `content://` URI that respects scoped file access restrictions.

---

## Testing & Debugging

### Test Locally (Without Real Network Calls)

To avoid hitting the real GitHub URLs during testing:

1. **Mock the version check**: Create a test activity that instantiates `UpdateChecker` with a custom executor that simulates a local version check.

2. **Override URLs** (if supported): Currently, the URLs are hardcoded. To test with local/staging URLs, you would need to:
   - Expose the URLs as configurable constants (not currently supported)
   - Or add a constructor parameter to accept custom URLs

3. **Manual testing with emulator**:
   - Bump the remote version in `ops/release/version.txt` to be higher than the current build
   - Run the app on an emulator with network access
   - Check `logcat` for UpdateChecker logs

### Common Failure Modes & Troubleshooting

| Failure | Root Cause | How to Debug |
|---------|-----------|--------------|
| "Failed to find app installer" | System `PackageInstaller` app is missing or disabled | Check `adb shell pm list packages \| grep installer`; ensure `com.android.packageinstaller` is present |
| Version check timeout | Network unreachable or GitHub is slow | Check `logcat` for `UpdateChecker` logs; verify network connectivity in emulator |
| APK download fails | Network drops mid-download; file I/O error | Check `logcat` for I/O exceptions; verify cache directory is writable |
| Dialog doesn't appear | Activity was destroyed before version check completed | Check `logcat` for `getAliveActivity() == null`; this is expected behavior (safe degradation) |
| APK downloaded but install doesn't start | FileProvider authority mismatch | Verify `AndroidManifest.xml` authority matches `${applicationId}.fileprovider` |

### Enabling Debug Logging

The module logs to `UpdateChecker` tag:
```bash
adb logcat UpdateChecker:V *:S
```

---

## Configuration & Tuning

### Network Timeouts

Defaults in `UpdateChecker.java`:

| Timeout | Value | Purpose |
|---------|-------|---------|
| `VERSION_CONNECT_TIMEOUT_MS` | 5000 ms | Initial connection for version check |
| `VERSION_READ_TIMEOUT_MS` | 5000 ms | Read timeout for version text file |
| `DOWNLOAD_CONNECT_TIMEOUT_MS` | 10000 ms | Initial connection for APK download |
| `DOWNLOAD_READ_TIMEOUT_MS` | 30000 ms | Read timeout for large APK file |

**Why different values?**
- Version check is small (< 10 bytes) and must be fast → 5s total
- APK download is large (100+ MB) and may be slow on mobile → 30s read timeout allows continued downloads without stalling

**To adjust:** Edit the constants in `UpdateChecker.java` and rebuild.

### Buffer Size

`DOWNLOAD_BUFFER_SIZE_BYTES = 8192` — Standard Java I/O buffer. Safe to adjust for specific performance needs (e.g., increase to 16384 for very large files).

---

## Release/Deployment Workflow

This module is called by `UpdateChecker`, which is integrated during app startup (typically in `MainActivity.onCreate()`).

**Integration with build/release:**
- `./gradlew copyToRelease` — Copies debug APK to `ops/release/` **and increments** `ops/release/version.txt`
- `./gradlew publishReleaseArtifact` — Runs `copyToRelease`, then commits and pushes to GitHub

This is why the version.txt file is kept in the release directory and incremented automatically.

---

## References

- **Android FileProvider**: https://developer.android.com/reference/androidx/core/content/FileProvider
- **Android Intent.ACTION_VIEW**: https://developer.android.com/reference/android/content/Intent#ACTION_VIEW
- **Android PackageManager**: https://developer.android.com/reference/android/content/pm/PackageManager
- **Scoped File Access (Android 7.0+)**: https://developer.android.com/about/versions/nougat/android-7.0-changes#scoped_storage
- **WeakReference (Java)**: https://docs.oracle.com/javase/8/docs/api/java/lang/ref/WeakReference.html
- **Handler & Looper (Android)**: https://developer.android.com/reference/android/os/Handler

---

## Future Improvements

- **Configurable URLs**: Allow staging/test URLs via build flavors or SharedPreferences
- **Progress indication**: Show download progress percentage during APK download
- **Retry logic**: Exponential backoff for network failures
- **Delta updates**: Download only changed parts (e.g., via Bsdiff) to reduce bandwidth
