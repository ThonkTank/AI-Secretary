# Auto-Update System - Technical Documentation

**Components:** UpdateChecker.java & UpdateInstaller.java
**Status:** Phase 0 Complete - GitHub Releases Integration
**Last Updated:** 2025-11-13

---

## Overview

The AI Secretary auto-update system enables seamless app updates via GitHub Releases. Users can check for updates in-app, download new versions, and install them with minimal friction.

**Key Features:**
- ✅ GitHub Releases API integration
- ✅ Automatic version comparison
- ✅ In-app update notifications
- ✅ One-tap download and install
- ✅ Changelog display
- ✅ Background download via DownloadManager
- ✅ No Google Play dependency

---

## Architecture

### Components

**1. UpdateChecker.java** - Version checking and GitHub API integration
- Connects to GitHub Releases API
- Fetches latest release information
- Parses version numbers and compares with current
- Locates APK assets in releases
- Callback interface for async results

**2. UpdateInstaller.java** - APK download and installation
- Uses Android DownloadManager for downloads
- Handles download completion via BroadcastReceiver
- Launches package installer
- Manages download IDs and error states

**Reference:**
- Implementation: [`src/com/secretary/UpdateChecker.java`](../src/com/secretary/UpdateChecker.java)
- Installer: [`src/com/secretary/UpdateInstaller.java`](../src/com/secretary/UpdateInstaller.java)
- Integration: [`src/com/secretary/MainActivity.java`](../src/com/secretary/MainActivity.java)

---

## User Flow

### Complete Update Process

**1. User initiates check**
```
Open App → Tap Settings (⚙) → "Check for Updates"
```

**2. Version comparison**
```
UpdateChecker queries GitHub API
↓
Compares current version (e.g., 0.3.25) with latest (e.g., 0.3.26)
↓
If newer available → Shows update dialog
If up-to-date → Shows "Already up to date" message
```

**3. User accepts update**
```
Update dialog shows:
- New version number
- Changelog/release notes
- "Download & Install" button
↓
User taps button
```

**4. Download process**
```
UpdateInstaller starts DownloadManager
↓
System notification shows download progress
↓
Download completes
```

**5. Installation**
```
Package installer launches automatically
↓
User confirms installation
↓
App updates and relaunches
```

---

## UpdateChecker.java

### API Integration

**Endpoint:** `https://api.github.com/repos/ThonkTank/AI-Secretary/releases/latest`

**Authentication:** None (public repository)

**Response Format:**
```json
{
  "tag_name": "v0.3.26",
  "name": "AI Secretary v0.3.26",
  "body": "Release notes...",
  "assets": [
    {
      "name": "AISecretary-signed.apk",
      "browser_download_url": "https://github.com/.../AISecretary-signed.apk"
    }
  ]
}
```

### Usage

```java
// In Activity
UpdateChecker.checkForUpdates(context, new UpdateChecker.UpdateListener() {
    @Override
    public void onUpdateAvailable(String version, String downloadUrl, String changelog) {
        // Show update dialog
        logger.info(TAG, "Update available: " + version);
        showUpdateDialog(version, downloadUrl, changelog);
    }

    @Override
    public void onNoUpdateAvailable() {
        // Inform user
        logger.info(TAG, "App is up to date");
        Toast.makeText(context, "Already up to date!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onError(String error) {
        // Handle errors
        logger.error(TAG, "Update check failed: " + error);
        Toast.makeText(context, "Check failed: " + error, Toast.LENGTH_SHORT).show();
    }
});
```

### Version Comparison

**Logic:**
```java
// Extract version numbers (e.g., "0.3.25" → 0, 3, 25)
String[] currentParts = currentVersion.split("\\.");
String[] latestParts = latestVersion.split("\\.");

// Compare major.minor.patch
for (int i = 0; i < Math.max(currentParts.length, latestParts.length); i++) {
    int current = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
    int latest = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;

    if (latest > current) return true;  // Update available
    if (latest < current) return false; // Current is newer
}
return false; // Same version
```

---

## UpdateInstaller.java

### Download Management

**Uses Android DownloadManager:**
```java
DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
request.setTitle("AI Secretary Update");
request.setDescription("Downloading version " + version);
request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AISecretary-signed.apk");

DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
long downloadId = downloadManager.enqueue(request);
```

**Download Tracking:**
- Download ID stored for completion tracking
- BroadcastReceiver listens for `DownloadManager.ACTION_DOWNLOAD_COMPLETE`
- On completion, launches package installer

### Installation Trigger

```java
// When download completes
private void installApk(Context context, long downloadId) {
    DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
    Uri downloadUri = downloadManager.getUriForDownloadedFile(downloadId);

    if (downloadUri != null) {
        Intent installIntent = new Intent(Intent.ACTION_VIEW);
        installIntent.setDataAndType(downloadUri, "application/vnd.android.package-archive");
        installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(installIntent);
    }
}
```

---

## Permissions

**Required in AndroidManifest.xml:**

```xml
<!-- Network access for GitHub API -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- Package installation -->
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

**Runtime Permissions:**
- INTERNET: Granted automatically (normal permission)
- REQUEST_INSTALL_PACKAGES: Granted automatically for API 26+

---

## GitHub Actions Integration

### Build Workflow

**File:** `.github/workflows/build-and-release.yml`

**Trigger:** Push to main branch or manual dispatch

**Build Steps:**
1. **Checkout** - Clone repository
2. **Setup JDK 17** - Java environment
3. **Install Android SDK** - API 33 + Build Tools 33.0.2
4. **Compile Resources** - AAPT2 compilation
5. **Compile Java** - Source 8, Target 8
6. **Create DEX** - d8 tool
7. **Align APK** - zipalign
8. **Sign APK** - apksigner with release keystore
9. **Extract Version** - From AndroidManifest.xml
10. **Create Release** - GitHub Release with tag
11. **Upload APK** - As release asset

### Release Creation

**Automated Process:**
```yaml
- name: Extract version
  id: version
  run: |
    VERSION=$(grep 'android:versionName=' AndroidManifest.xml | sed 's/.*versionName="\([^"]*\)".*/\1/')
    echo "version=$VERSION" >> $GITHUB_OUTPUT

- name: Create Release
  uses: softprops/action-gh-release@v1
  with:
    tag_name: v${{ steps.version.outputs.version }}
    name: AI Secretary v${{ steps.version.outputs.version }}
    body: |
      Automated build of AI Secretary
      Version: ${{ steps.version.outputs.version }}
      Commit: ${{ github.sha }}

      ## Changes
      ${{ github.event.head_commit.message }}
    files: AISecretary-signed.apk
```

**Result:**
- Tag: `v0.3.26`
- Release name: "AI Secretary v0.3.26"
- Asset: `AISecretary-signed.apk`
- Release notes: Commit message + build metadata

---

## Versioning

### Version Format

**Location:** `AndroidManifest.xml`

```xml
<manifest
    package="com.secretary.helloworld"
    android:versionCode="325"
    android:versionName="0.3.25">
```

**Components:**
- **versionCode:** Integer, incremental (Android requirement)
- **versionName:** Semantic version (user-visible)

### Phase-Based Versioning

**Scheme:**
- **0.0.x - 0.1.x** → Phase 0 (Foundation Systems)
- **0.2.x** → Phase 1 (Taskmaster Foundation)
- **0.3.x** → Phase 2-4 (Core Features) ← CURRENT
- **0.4.x** → Phase 5 (Intelligent Planning)
- **0.5.x** → Phase 6 (Widget & Polish)
- **1.0.0** → MVP Release

**Update Process:**
1. Increment `versionCode` by 1 (e.g., 325 → 326)
2. Update `versionName` (e.g., 0.3.25 → 0.3.26)
3. Commit and push → Triggers build
4. GitHub Actions creates release with new version

---

## Logging

### Update Check Sequence

**Typical Log Flow:**
```
[INFO] [MainActivity] Settings button clicked
[INFO] [MainActivity] User initiated update check from settings
[INFO] [UpdateChecker] Starting update check. Current version: 0.3.25 (code: 325)
[DEBUG] [UpdateChecker] GitHub API response code: 200
[INFO] [UpdateChecker] Latest version from GitHub: 0.3.26
[DEBUG] [UpdateChecker] Found 1 assets in release
[INFO] [UpdateChecker] Found APK asset: AISecretary-signed.apk
[INFO] [UpdateChecker] Update available! 0.3.25 -> 0.3.26
[INFO] [MainActivity] Update dialog shown to user for version 0.3.26
```

### Download & Install Sequence

```
[INFO] [MainActivity] User accepted update download
[INFO] [UpdateInstaller] Starting download for version 0.3.26
[INFO] [UpdateInstaller] Download enqueued with ID: 12345
[INFO] [UpdateInstaller] Download completed for ID: 12345
[INFO] [UpdateInstaller] Starting installation for: content://downloads/all_downloads/12345
[INFO] [UpdateInstaller] APK installer intent launched successfully
```

**Access logs:**
```bash
# Via HTTP server
curl http://localhost:8080/logs | grep "Update"

# Via Logcat
logcat | grep -E "(UpdateChecker|UpdateInstaller)"
```

---

## Error Handling

### Common Errors

**1. Network Error**
```
[ERROR] [UpdateChecker] Update check failed: Unable to connect to GitHub
```
**Cause:** No internet connection or GitHub API down
**Solution:** User retries when network is available

**2. JSON Parse Error**
```
[ERROR] [UpdateChecker] Failed to parse GitHub API response
```
**Cause:** Unexpected API response format
**Solution:** Check GitHub API status, verify endpoint

**3. No APK Asset**
```
[ERROR] [UpdateChecker] No APK found in release assets
```
**Cause:** Release doesn't contain `AISecretary-signed.apk`
**Solution:** Check GitHub Actions build workflow

**4. Download Failed**
```
[ERROR] [UpdateInstaller] Download failed with status: DownloadManager.STATUS_FAILED
```
**Cause:** Network interruption, insufficient storage, or permission issue
**Solution:** User retries download, checks storage space

**5. Installation Blocked**
```
[ERROR] [UpdateInstaller] Unable to launch package installer
```
**Cause:** Missing REQUEST_INSTALL_PACKAGES permission or user cancelled
**Solution:** User grants permission in system settings

---

## Debugging

### Manual Testing

**Check for updates:**
```bash
# 1. Build and release new version
# (Update version in AndroidManifest.xml, commit, push)

# 2. Verify release created
export GH_TOKEN=$(cat ~/.github_token)
gh release list

# 3. Install older version on device

# 4. Launch app and check for updates
am start -n com.secretary.helloworld/.MainActivity
# (Tap Settings → Check for Updates)

# 5. Monitor logs
curl http://localhost:8080/logs | grep "Update"
```

**Simulate update check:**
```bash
# Check current app version
pm dump com.secretary.helloworld | grep versionName

# Check latest GitHub release
curl -s https://api.github.com/repos/ThonkTank/AI-Secretary/releases/latest | grep tag_name

# Compare versions manually
```

### Testing Workflow

**1. Version Comparison**
- Install older version (e.g., 0.3.24)
- Release newer version (e.g., 0.3.25)
- Check for updates in app
- Verify update dialog appears

**2. Download Process**
- Accept update
- Check notification area for download progress
- Verify APK in ~/storage/downloads/
- Check logs for download completion

**3. Installation**
- Verify package installer launches
- Install update
- Check app version after installation
- Verify app functionality

---

## API Rate Limits

**GitHub API:**
- **Authenticated:** 5000 requests/hour (not used)
- **Unauthenticated:** 60 requests/hour (current)

**Impact:** Low (users rarely check more than 1-2 times/hour)

**Monitoring:**
```bash
# Check rate limit status
curl -s https://api.github.com/rate_limit | grep remaining
```

---

## Known Limitations

### Current Issues

1. **No automatic checks** - User must manually check for updates
   - **Future:** Background WorkManager job for periodic checks

2. **No delta updates** - Downloads full APK every time
   - **Impact:** Minimal (APK is <1 MB)
   - **Future:** Consider Google Play App Signing for delta updates

3. **No rollback** - Cannot automatically revert to previous version
   - **Workaround:** User can manually install older APK from GitHub Releases

4. **No forced updates** - Cannot enforce critical security updates
   - **Current:** All updates are optional
   - **Future:** Add minimum version check

5. **BroadcastReceiver leak** - May not unregister if download fails
   - **Risk:** Memory leak if app closed during download
   - **Fix:** Implement proper lifecycle management

---

## Security Considerations

### APK Signing

**Current:** Release keystore stored in GitHub Secrets
- Keystore: Base64-encoded, decrypted during build
- Password: Stored in GitHub Secrets
- Key alias: "release"

**Verification:**
```bash
# Verify APK signature
apksigner verify -v ~/storage/downloads/AISecretary-signed.apk

# Check signing certificate
keytool -printcert -jarfile ~/storage/downloads/AISecretary-signed.apk
```

### Download Security

**HTTPS only:** All downloads use HTTPS (GitHub CDN)
**Signature verification:** Android verifies APK signature before installation
**Same-key requirement:** Update must be signed with same key as installed app

---

## Future Enhancements

**Planned Improvements:**
- [ ] Automatic background update checks (WorkManager, daily)
- [ ] Update channel selection (stable/beta)
- [ ] In-app changelog viewer (Markdown rendering)
- [ ] Download progress bar in app (not just notification)
- [ ] Delta updates for large APKs
- [ ] Rollback functionality (revert to previous version)
- [ ] Forced updates for critical security patches
- [ ] Update scheduling (install on next app restart)

---

## Related Documentation

- **[CLAUDE.md](../CLAUDE.md)** - Developer documentation (update workflows)
- **[LOGGING_SYSTEM.md](./LOGGING_SYSTEM.md)** - Logging system documentation
- **[README.md](../README.md)** - Project overview
- **[ROADMAP.md](../ROADMAP.md)** - Feature roadmap
- **[~/CLAUDE.md](../../CLAUDE.md)** - Home directory guide

**Source Code:**
- `src/com/secretary/UpdateChecker.java` - Version checking & GitHub API
- `src/com/secretary/UpdateInstaller.java` - Download & installation
- `src/com/secretary/MainActivity.java` - Update UI integration
- `.github/workflows/build-and-release.yml` - Automated build & release

---

## Appendix: Complete Update Flow

```
User Action: Tap "Check for Updates"
    ↓
MainActivity.showSettingsDialog()
    ↓
UpdateChecker.checkForUpdates()
    ↓
HTTP GET: api.github.com/repos/.../releases/latest
    ↓
Parse JSON response
    ↓
Extract: tag_name, assets[].browser_download_url
    ↓
Compare versions: current vs latest
    ↓
┌─────────────────────────────────────┬─────────────────────────────┐
│ If Update Available                 │ If Up-to-Date               │
├─────────────────────────────────────┼─────────────────────────────┤
│ onUpdateAvailable() callback        │ onNoUpdateAvailable()       │
│ ↓                                   │ ↓                           │
│ Show update dialog                  │ Show "Up to date" toast     │
│ ↓                                   │ END                         │
│ User taps "Download & Install"      │                             │
│ ↓                                   │                             │
│ UpdateInstaller.downloadAndInstall()│                             │
│ ↓                                   │                             │
│ DownloadManager.enqueue()           │                             │
│ ↓                                   │                             │
│ BroadcastReceiver waits...          │                             │
│ ↓                                   │                             │
│ ACTION_DOWNLOAD_COMPLETE            │                             │
│ ↓                                   │                             │
│ Get download URI                    │                             │
│ ↓                                   │                             │
│ Launch PackageInstaller intent      │                             │
│ ↓                                   │                             │
│ User confirms installation          │                             │
│ ↓                                   │                             │
│ App updates and relaunches          │                             │
│ ↓                                   │                             │
│ END                                 │                             │
└─────────────────────────────────────┴─────────────────────────────┘
```

---

**Last Updated:** 2025-11-13
**Component Version:** Phase 0 (Complete)
**Current Implementation:** GitHub Releases with DownloadManager
