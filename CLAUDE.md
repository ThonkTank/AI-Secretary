# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Project Overview

**AI Secretary** is a native Android task management app developed in Termux on Android. The project is currently in Phase 0 (Foundation Systems), implementing auto-update and logging infrastructure before building the main Taskmaster feature suite.

**Current Status:** Phase 0 code implemented but not yet functional - requires debugging.

**Repository:** https://github.com/ThonkTank/AI-Secretary

---

## Development Environment

### Hardware & System
- **Primary:** Google Pixel 8 (aarch64), Android 16
- **Development:** Termux (googleplay.2025.10.05)
- **Java:** OpenJDK 21.0.9
- **Android SDK:** API 33 (compile), API 28-35 (runtime)

### Critical Constraint: No Local Gradle
Gradle does NOT work in Termux (JVM libiconv error). Use GitHub Actions for all builds.

---

## Build & Deployment

### Production Build (REQUIRED)
```bash
# 1. Make code changes
# 2. Commit and push to GitHub
git add .
git commit -m "Your message"
git push origin main

# 3. GitHub Actions automatically:
#    - Builds APK with d8/aapt2
#    - Signs with debug keystore
#    - Creates GitHub Release with version from AndroidManifest.xml
#    - Uploads AISecretary-signed.apk as release asset

# 4. Download from GitHub Releases
# 5. Install on device
```

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
└── AppLogger.java           # In-memory logging (500 line buffer)

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
- In-memory storage (max 500 lines, auto-rotates)
- Three levels: INFO, DEBUG, ERROR
- Also logs to Android Logcat in parallel
- ⚠️ Contains unused `logFile` variable (dead code)

**MainActivity.java**
- Single Activity app
- Action bar with Settings menu
- Main screen shows app logs (for debugging)
- Settings dialog with version info and update check

### Package Structure
- **Current:** Flat structure in `com.secretary.helloworld`
- **Planned:** Migrate to `com.secretary` and layer-based structure (data/domain/presentation)

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
    android:versionCode="22"
    android:versionName="0.0.22">
```

**Update Version:**
1. Increment `versionCode` (integer, sequential)
2. Update `versionName` (phase-based: major.minor.patch)
   - major = 0 until Taskmaster v1.0
   - minor = development phase (0-6)
   - patch = build number within phase
3. Commit and push - GitHub Actions will create release with tag `v{versionName}`

**Current:** v0.0.22 (Build 22) - Phase 0 Development

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
1. Debug Update System - identify why GitHub API calls fail
2. Debug Logging System - identify why logs aren't displayed
3. Fix GitHub token security issue
4. Change package name to `com.secretary`
5. Fix resource/memory leaks

**After Phase 0 Works:**
1. Decide: More tech debt cleanup OR start Taskmaster Phase 1
2. If Phase 1: Migrate to Kotlin, implement MVVM, set up Room database

**Reference:** See `ROADMAP.md` for detailed task breakdowns and time estimates.

---

**Last Updated:** 2025-11-12
**Current Version:** v0.0.22 (Build 22)
**Status:** Phase 0 - File-based logging implemented
