# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Project Overview

**AI Secretary** is a native Android task management app developed in Termux on Android. The project is currently in Phase 0 (Foundation Systems), implementing auto-update and logging infrastructure before building the main Taskmaster feature suite.

**Scope:** Personal use only - not intended for Google Play distribution.

**Current Status:** Phase 0 code implemented but not yet functional - requires debugging.

**Repository:** https://github.com/ThonkTank/AI-Secretary

---

## Development Environment

### Hardware & System
- **Primary:** Google Pixel 8 (aarch64)
- **Android Version:** Android 16 (API Level 36)
- **Development:** Termux (googleplay.2025.10.05)
- **Java:** OpenJDK 21.0.9
- **Android SDK:** API 33 (compile), API 28-36 (runtime)

### Android 16 Storage Constraints
- **Scoped Storage:** Fully enforced (since API 29)
- **WRITE_EXTERNAL_STORAGE:** Deprecated, no longer works
- **File Access:** Apps cannot read files created by other apps in shared storage
- **Impact:** Termux cannot read log files created by AI Secretary in Download folder
- **Solution Required:** MANAGE_EXTERNAL_STORAGE permission or ContentProvider

### Critical Constraint: No Local Gradle
Gradle does NOT work in Termux (JVM libiconv error). Use GitHub Actions for all builds.

---

## Build & Deployment

### Production Build (REQUIRED)
```bash
# 1. Update version in AndroidManifest.xml (versionCode and versionName)

# 2. Commit and push to GitHub
git add .
git commit -m "Your message"
git push origin main

# 3. Wait for GitHub Actions build (takes ~45 seconds)
export GH_TOKEN=$(cat ~/.github_token)
gh run list --limit 1  # Check if build completed

# 4. Download APK from GitHub Releases
VERSION="0.0.23"  # Use current version
ASSET_ID=$(curl -s -H "Authorization: token $GH_TOKEN" \
  https://api.github.com/repos/ThonkTank/AI-Secretary/releases/tags/v$VERSION \
  | grep -o '"id": [0-9]*' | head -1 | cut -d' ' -f2)

cd ~/storage/downloads
curl -L -H "Accept: application/octet-stream" \
  -H "Authorization: token $GH_TOKEN" \
  -o AISecretary-v$VERSION.apk \
  https://api.github.com/repos/ThonkTank/AI-Secretary/releases/assets/$ASSET_ID

# 5. Make file visible to Android and install
termux-media-scan AISecretary-v$VERSION.apk
termux-open AISecretary-v$VERSION.apk
```

**GitHub Token:** Stored in `~/.github_token` (readable with `cat ~/.github_token`)

**Workflow:** `.github/workflows/build-and-release.yml`
- Triggers on push to main or manual dispatch
- Uses Android SDK 33, Build Tools 33.0.2
- Java source/target: 8 (for compatibility)
- Extracts version from `AndroidManifest.xml` for release tagging

### Local Development Build (Learning Only)
```bash
# Only for simple testing - NOT for production
./build.sh

# Installs:
cp app_signed.apk ~/storage/downloads/
termux-open ~/storage/downloads/app_signed.apk
```

**Limitations:**
- No support for external libraries (AppCompat, Room, etc.)
- Manual resource compilation
- No dependency management
- Use only for quick local tests

---

## Architecture

### Current Structure (Phase 0)
```
src/com/secretary/
├── MainActivity.java        # Main UI, Settings dialog, Update UI
├── UpdateChecker.java       # GitHub Releases API client
├── UpdateInstaller.java     # APK download via DownloadManager
├── AppLogger.java           # In-memory logging (500 line buffer)
└── LogProvider.java         # ContentProvider for log access

res/
├── layout/
│   ├── activity_main.xml        # Main screen (displays logs)
│   ├── dialog_settings.xml      # Settings with update check
│   └── dialog_logs.xml          # Log viewer dialog
├── menu/main_menu.xml           # Action bar (Settings icon)
└── values/strings.xml
```

### Key Components

**UpdateChecker.java**
- Checks GitHub Releases API for latest version
- Compares with current app version
- Returns update info via callback interface
- ⚠️ Contains hardcoded GitHub token (security issue - must fix)

**UpdateInstaller.java**
- Downloads APK using Android DownloadManager
- Registers BroadcastReceiver for download completion
- Launches installation prompt
- ⚠️ Potential memory leak (receiver not always unregistered)

**AppLogger.java**
- Singleton pattern for centralized logging
- Triple redundancy: In-memory (max 500 lines) + File (backup) + Logcat
- Three levels: INFO, DEBUG, ERROR
- **Purpose:** Enable Claude Code to read logs for informed development decisions
- User can view logs in-app (Settings → View Logs)
- **Access:** Via LogProvider ContentProvider (see "Accessing Logs" section below)

**LogProvider.java**
- ContentProvider that exports logs to external tools
- URI: `content://com.secretary.helloworld.logs/file`
- Reads in-memory logs from AppLogger
- Creates temporary file in cache for each access
- Exported without permissions (personal use, no security risk)
- Enables Claude Code to read logs with `content read --uri ...`

**MainActivity.java**
- Single Activity app
- Action bar with Settings menu
- Main screen shows app logs (for debugging)
- Settings dialog with version info and update check

### Package Structure
- **Current:** Flat structure in `com.secretary.helloworld`
- **Planned:** Migrate to `com.secretary` and layer-based structure (data/domain/presentation)

---

## Accessing Logs

**LogProvider.java** - ContentProvider for log access

The app exposes logs via a ContentProvider that Claude Code can read without any permissions. This enables informed debugging and development decisions based on real app behavior.

### How to Read Logs

```bash
# Read current logs (recommended method)
content read --uri content://com.secretary.helloworld.logs/file

# Save logs to file for analysis
content read --uri content://com.secretary.helloworld.logs/file > ~/app_logs.txt

# View last 20 lines
content read --uri content://com.secretary.helloworld.logs/file | tail -20

# Search for errors
content read --uri content://com.secretary.helloworld.logs/file | grep ERROR
```

### When to Read Logs

- **After app crashes:** Understand what happened before crash
- **After feature implementation:** Verify feature is working correctly
- **When debugging issues:** See actual execution flow and errors
- **Before planning next steps:** Base decisions on real behavior, not assumptions

### Technical Details

- **Provider:** LogProvider.java exports in-memory logs
- **URI:** `content://com.secretary.helloworld.logs/file`
- **Format:** Plain text, one log line per line
- **Access:** No permissions required (exported provider)
- **Availability:** Works even if app crashes (last 500 lines preserved in memory)

---

## Version Management

**Location:** `AndroidManifest.xml`

**Versioning Scheme (Phase-based):**
- **0.0.x** = Phase 0 (Foundation Systems - Update & Logging)
- **0.1.x** = Phase 1 (Taskmaster Foundation & Database)
- **0.2.x** = Phase 2 (Core Task Management)
- **0.3.x** = Phase 3 (Tracking & Analytics)
- **0.4.x** = Phase 4 (Motivation & Statistics)
- **0.5.x** = Phase 5 (Intelligent Planning)
- **0.6.x** = Phase 6 (Widget & Polish)
- **1.0.0** = Taskmaster MVP Release

```xml
<manifest package="com.secretary.helloworld"
    android:versionCode="25"
    android:versionName="0.0.25">
```

**Update Version:**
1. Increment `versionCode` (integer, sequential)
2. Update `versionName` (phase-based: major.minor.patch)
   - major = 0 until Taskmaster v1.0
   - minor = development phase (0-6)
   - patch = build number within phase
3. Commit and push - GitHub Actions will create release with tag `v{versionName}`

**Current:** v0.0.25 (Build 25) - Phase 0 Development

---

## Debugging & Testing

### View Logs
```bash
# Real-time logcat filtering
logcat | grep Secretary

# Or filter by specific tags
logcat | grep -E "(MainActivity|UpdateChecker|AppLogger)"

# Check for errors
logcat | grep -E "ERROR|Exception"
```

### Install and Test App
```bash
# Install from local build
pm install -r app_signed.apk

# Or from GitHub release
pm install -r ~/storage/downloads/AISecretary-signed.apk

# Launch app
am start -n com.secretary.helloworld/.MainActivity

# Check app info
pm dump com.secretary.helloworld | grep -A5 "version"
```

### Common Issues

**Update System Not Working:**
- Check GitHub API response in logcat
- Verify internet permission in manifest
- Check for JSON parsing errors
- Verify GitHub token is valid (UpdateChecker.java:17)

**Logging System Not Working:**
- Check AppLogger initialization in MainActivity
- Verify logs are being written (check logcat)
- Check TextView display logic in MainActivity

---

## Critical Known Issues (See ROADMAP.md)

### Security
1. **Hardcoded GitHub Token** (UpdateChecker.java:17)
   - CRITICAL: Must be revoked and regenerated
   - Move to GitHub Secrets
   - Use environment variable or remove if not needed

### Code Quality
2. **Resource Leaks** (UpdateChecker.java:36-54)
   - HttpURLConnection not closed
   - BufferedReader not closed

3. **Memory Leak** (UpdateInstaller.java:38-70)
   - BroadcastReceiver may not be unregistered if download fails
   - Should unregister in Activity.onDestroy()

4. **Dead Code** (AppLogger.java:21, 30, 94)
   - `logFile` variable created but never used
   - Remove or implement file logging

5. **Package Name** (AndroidManifest.xml:3)
   - Change from `com.secretary.helloworld` to `com.secretary`
   - Requires refactoring all imports

---

## Project Roadmap

See `ROADMAP.md` for complete development plan.

### Phase 0: Foundation Systems (Current - Not Functional)
- ❌ Auto-Update System - implemented, needs debugging
- ❌ Logging System - implemented, needs debugging
- Next: Debug both systems and fix critical security issues

### Future Phases (Blocked until Phase 0 works)
- **Phase 1:** Kotlin migration, MVVM architecture, Room database
- **Phase 2:** Task creation, display, CRUD operations
- **Phase 3:** Completion tracking, recurrence logic
- **Phase 4:** Streaks, statistics, motivation
- **Phase 5:** Intelligent planning algorithm
- **Phase 6:** Home screen widget, polish

**Estimated Timeline:** 12-16 weeks for MVP (Phases 1-4)

---

## Code Style

### Current Conventions
- **Language:** Java (planned migration to Kotlin)
- **Comments:** Mixed German/English (should migrate to English)
- **Logging:** Use AppLogger.getInstance(context).info/debug/error()
- **Threading:** Manual Thread creation (should use ExecutorService)

### Java Compilation
```bash
# Source/target compatibility
javac -source 8 -target 8 ...

# Classpath
-classpath $ANDROID_SDK_ROOT/platforms/android-33/android.jar
```

---

## Git Workflow

```bash
# Standard workflow
git status
git add .
git commit -m "feat: description"
git push origin main

# GitHub Actions will:
# 1. Build APK
# 2. Create release with version from manifest
# 3. Upload APK as release asset

# View build logs
# Go to GitHub → Actions tab
```

**Commit Message Format:**
- Use descriptive messages
- GitHub Actions uses commit message in release notes

---

## Accessing Logs in App

1. Install and launch app
2. Tap Settings icon (⚙) in action bar
3. Tap "View Logs" button
4. Logs displayed in scrollable dialog
5. Use "Copy to Clipboard" to share logs

**Main Screen:** Also displays logs automatically (activity_main.xml)

---

## Working with Resources

### Adding New Layouts
```xml
<!-- res/layout/your_layout.xml -->
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout ...>
    ...
</LinearLayout>
```

Update GitHub Actions workflow to compile new resource:
```yaml
# In build-and-release.yml
$ANDROID_SDK_ROOT/build-tools/33.0.2/aapt2 compile \
  res/layout/your_layout.xml \
  -o compiled_res/
```

### Adding New Java Files
Update GitHub Actions workflow:
```yaml
javac ... \
  src/com/secretary/YourNewClass.java \
  ...
```

---

## Protected Sections (🔒 in CLAUDE.md)

Do NOT modify these without explicit user permission:
- Project vision (Gesamtkonzept)
- Feature Suite 1: Taskmaster specifications
- Development roadmap phases

**Ask first** before changing architecture decisions or feature specifications.

---

## Useful Termux Commands

```bash
# Environment info
termux-info
getprop ro.build.version.release  # Android version
getprop ro.product.model          # Device model

# Package management
pkg list-installed | grep android
pkg install openjdk-17  # If needed

# File permissions
chmod +x build.sh

# APK analysis
aapt dump badging app_signed.apk
apksigner verify -v app_signed.apk
```

---

## Next Steps for Development

**Immediate (Phase 0 Completion):**
1. ✅ Logging System - ContentProvider implemented (v0.0.25)
2. ✅ Claude Code can read logs via `content read --uri content://...`
3. Debug Update System - test if app can check GitHub for updates
4. Fix GitHub token security issue (currently hardcoded in UpdateChecker.java)
5. Change package name to `com.secretary`
6. Fix resource/memory leaks

**After Phase 0 Works:**
1. Decide: More tech debt cleanup OR start Taskmaster Phase 1
2. If Phase 1: Migrate to Kotlin, implement MVVM, set up Room database

**Reference:** See `ROADMAP.md` for detailed task breakdowns and time estimates.

---

**Last Updated:** 2025-11-12
**Current Version:** v0.0.25 (Build 25)
**Status:** Phase 0 - ContentProvider logging system implemented
