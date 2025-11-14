# core/logging - Centralized Logging System

**Purpose:** Provides in-memory logging with HTTP server access for development and debugging.

**Status:** Kotlin migration in progress (1/2 files converted)

**Architecture:** Core system - No business logic or UI dependencies

---

## Directory Structure

```
core/logging/
├── AppLogger.kt              # ✅ KOTLIN (Wave 1) - Singleton logging system (122 lines)
├── HttpLogServer.java        # ⏳ Java (Wave 2) - HTTP server on localhost:8080
└── CLAUDE.md                 # This file
```

**Total:** 2 files
**Converted:** 1 Kotlin (AppLogger)
**Remaining:** 1 Java (HttpLogServer - Wave 2)

---

## Purpose and Goals

**Problem:** Claude Code needs real-time visibility into app behavior without file I/O (which requires storage permissions on Android).

**Solution:** In-memory circular buffer (500 lines) exposed via HTTP server on localhost:8080.

**Goals:**
1. **Zero Permissions:** No storage/file permissions needed
2. **Real-Time Access:** Logs available immediately via curl
3. **Auto-Trimming:** Automatic buffer management (circular buffer)
4. **Redundancy:** Logs to memory + Android Logcat + HTTP server
5. **Simple API:** Single method calls for info/debug/error

**Integration:** Used throughout entire app for all logging operations.

---

## Important Workflows and Functions

### 1. Initialize Logging System

**When:** App startup in MainActivity.onCreate()

**Flow:**
```kotlin
// In MainActivity
AppLogger.initialize(context)
// Logs: "AppLogger initialized (IN-MEMORY ONLY)"
// Logs: "Logs accessible via HTTP server: curl http://localhost:8080/logs"
```

**Code Reference:** `AppLogger.kt:32-38`

---

### 2. Write Logs

**Usage:**
```kotlin
// Kotlin
companion object {
    private const val TAG = "YourClassName"
}

AppLogger.info(TAG, "Operation completed successfully")
AppLogger.debug(TAG, "Variable value: $value")
AppLogger.error(TAG, "Operation failed", exception)
```

**Java (legacy):**
```java
private static final String TAG = "YourClassName";

AppLogger.getInstance(context).info(TAG, "Operation completed");
AppLogger.getInstance(context).error(TAG, "Failed", exception);
```

**What happens:**
1. Log entry created with timestamp: `[2025-11-13 14:30:15] [INFO] [TAG] message`
2. Written to Android Logcat (Log.i/Log.d/Log.e)
3. Added to in-memory circular buffer
4. If buffer > 500 lines, oldest entries trimmed
5. Available immediately via HTTP: `curl http://localhost:8080/logs`

**Code Reference:**
- Write logic: `AppLogger.kt:73-95`
- Trimming logic: `AppLogger.kt:88-94`

---

### 3. Read Logs via HTTP

**Primary Method:**
```bash
# Get all logs
curl http://localhost:8080/logs

# Last 30 lines
curl -s http://localhost:8080/logs | tail -30

# Search for errors
curl -s http://localhost:8080/logs | grep ERROR

# Live monitoring
while true; do clear; curl -s http://localhost:8080/logs | tail -20; sleep 2; done
```

**How it works:**
1. HttpLogServer runs in background thread (started in MainActivity)
2. Listens on localhost:8080
3. On request to `/logs`, calls `AppLogger.readLogs()`
4. Returns plain text response (one log entry per line)

**Code Reference:**
- Read method: `AppLogger.kt:100-103`
- HTTP server: `HttpLogServer.java` (to be converted in Wave 2)

---

### 4. Clear Logs

**Usage:**
```kotlin
AppLogger.clearLogs()
// Logs: "Logs manually cleared by user"
```

**When:** User triggers "Clear Logs" in Settings menu

**Code Reference:** `AppLogger.kt:108-112`

---

## Key Implementation Details

### AppLogger (Kotlin Singleton)

**Pattern:** Kotlin `object` (thread-safe singleton by default)

**Properties:**
- `MAX_LOG_LINES = 500` - Circular buffer size
- `logLines: MutableList<String>` - In-memory storage
- `initialized: Boolean` (@Volatile) - Initialization guard

**Thread Safety:**
- `@Synchronized` on all public methods
- Safe for concurrent access from multiple threads

**Initialization:**
- Idempotent: Safe to call `initialize()` multiple times
- First call logs "AppLogger initialized"
- Subsequent calls ignored

**Code Reference:** `AppLogger.kt:17-26`

---

### Java Interoperability

**For legacy Java code:**
```java
// Deprecated but functional
AppLogger logger = AppLogger.getInstance(context);
logger.info(TAG, "message");
```

**Implementation:**
```kotlin
@Deprecated("Use AppLogger.method() directly", ReplaceWith("AppLogger"))
@JvmStatic
fun getInstance(context: Context): AppLogger {
    initialize(context)
    return this
}
```

**Reason:** Smooth migration path during Kotlin conversion (Waves 2-7)

**Code Reference:** `AppLogger.kt:120-125`

---

## Testing and Debugging

### Manual Testing

```bash
# 1. Launch app
am start -n com.secretary.helloworld/.MainActivity

# 2. Verify logs accessible
curl http://localhost:8080/logs | tail -10

# Expected output:
# [2025-11-13 14:30:15] [INFO] [AppLogger] AppLogger initialized (IN-MEMORY ONLY)
# [2025-11-13 14:30:15] [INFO] [AppLogger] Logs accessible via HTTP server: curl http://localhost:8080/logs
# [2025-11-13 14:30:16] [INFO] [MainActivity] HTTP server started successfully

# 3. Perform operations in app

# 4. Read logs immediately
curl -s http://localhost:8080/logs | grep ERROR  # Should be empty for success
```

### Common Issues

**HTTP server not responding:**
- Check app is running: `am start -n com.secretary.helloworld/.MainActivity`
- Check logcat: `logcat | grep HttpLogServer`
- Verify port 8080 not blocked

**Logs disappearing:**
- Buffer is only 500 lines (by design)
- Old logs auto-trimmed when limit exceeded
- Use `curl` frequently to capture logs before trimming

**Logs not appearing:**
- Verify AppLogger initialized in MainActivity
- Check logcat: `logcat | grep AppLogger`
- Ensure using correct TAG constant

---

## Migration Notes (Phase 4.5.3)

**Wave 1 (Complete ✅):**
- Converted AppLogger.java → AppLogger.kt
- Changed from class to singleton object
- Reduced boilerplate (getInstance pattern → direct calls)
- Added @JvmStatic for Java interop

**Wave 2 (Next):**
- Convert HttpLogServer.java → HttpLogServer.kt
- Simplify with Kotlin coroutines (if needed)
- Maintain same HTTP API (curl compatibility)

**Breaking Changes:** None (backward compatible during migration)

---

## Related Documentation

- **[Project CLAUDE.md](../../../../CLAUDE.md)** - Overall architecture and logging usage
- **[docs/LOGGING_SYSTEM.md](../../../../docs/LOGGING_SYSTEM.md)** - Deep dive into logging implementation
- **[Parent CLAUDE.md](../CLAUDE.md)** - Core systems overview

**Deep Dive:** See [docs/LOGGING_SYSTEM.md](../../../../docs/LOGGING_SYSTEM.md) for:
- Detailed HTTP server implementation
- Performance considerations
- Troubleshooting guide
- Historical context (why in-memory, not file-based)

---

**Last Updated:** 2025-11-13
**Status:** Wave 1 complete (AppLogger in Kotlin), Wave 2 pending (HttpLogServer)
**Version:** v0.3.28 (Build 328)
**Package:** com.secretary.helloworld.core.logging
