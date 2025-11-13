# Logging System - Technical Documentation

**Component:** AppLogger.java & SimpleHttpServer.java
**Status:** Phase 0 Complete - HTTP Server Implementation
**Last Updated:** 2025-11-13

---

## Overview

The AI Secretary logging system provides real-time access to application logs via an embedded HTTP server. This enables Claude Code and developers to monitor app behavior, debug issues, and verify feature implementations without file-based logging.

**Key Features:**
- ✅ HTTP server on localhost:8080
- ✅ In-memory buffer (500 entries, auto-trimming)
- ✅ Three log levels: INFO, DEBUG, ERROR
- ✅ Thread-safe singleton pattern
- ✅ Parallel logging to Android Logcat
- ✅ Zero file I/O (no storage permissions needed)

---

## Architecture

### Components

**1. AppLogger.java** - Centralized logging management
- Singleton pattern for global access
- In-memory circular buffer (ArrayList with auto-trimming)
- Timestamp formatting with SimpleDateFormat
- Thread synchronization for concurrent access
- Parallel logging to Android Logcat for debugging

**2. SimpleHttpServer.java** - HTTP server for log access
- Runs on localhost:8080 (local only, no external access)
- Single-threaded, synchronous request handling
- RESTful endpoints: `/logs`, `/` (help)
- Reads from AppLogger's in-memory buffer
- Started automatically in MainActivity.onCreate()

**Reference:**
- Implementation: [`src/com/secretary/AppLogger.java`](../src/com/secretary/AppLogger.java)
- HTTP Server: [`src/com/secretary/SimpleHttpServer.java`](../src/com/secretary/SimpleHttpServer.java)
- Integration: [`src/com/secretary/MainActivity.java:50-60`](../src/com/secretary/MainActivity.java)

---

## Log Format

```
[YYYY-MM-DD HH:mm:ss] [LEVEL] [TAG] Message
```

**Examples:**
```
[2025-11-13 09:05:32] [INFO] [MainActivity] === Application started ===
[2025-11-13 09:05:32] [INFO] [MainActivity] App version: 0.3.25 (code: 325)
[2025-11-13 09:05:33] [DEBUG] [UpdateChecker] GitHub API response code: 200
[2025-11-13 09:05:34] [INFO] [TaskActivity] Loaded 12 tasks from database
[2025-11-13 09:05:35] [ERROR] [TaskDatabaseHelper] Database migration failed
```

**Components:**
- **Timestamp:** `[YYYY-MM-DD HH:mm:ss]` - When the log entry was created
- **Level:** `[INFO|DEBUG|ERROR]` - Severity level
- **Tag:** `[ClassName]` - Component that generated the log
- **Message:** Descriptive text with optional exception stack traces

---

## Accessing Logs

### Primary Method: HTTP Server

**Base URL:** `http://localhost:8080`

**Endpoints:**

```bash
# Get all logs (primary method)
curl http://localhost:8080/logs

# Get server status (help)
curl http://localhost:8080/
```

**Common Usage Patterns:**

```bash
# Save logs to file for analysis
curl http://localhost:8080/logs > ~/secretary_logs.txt

# View last 20 entries
curl -s http://localhost:8080/logs | tail -20

# View first 20 entries
curl -s http://localhost:8080/logs | head -20

# Search for errors
curl -s http://localhost:8080/logs | grep ERROR

# Search for specific component
curl -s http://localhost:8080/logs | grep "TaskActivity"

# Live monitoring (refresh every 2 seconds)
while true; do clear; curl -s http://localhost:8080/logs | tail -20; sleep 2; done

# Count log entries by level
curl -s http://localhost:8080/logs | grep -c INFO
curl -s http://localhost:8080/logs | grep -c ERROR
```

### Helper Script (Optional)

A helper script `~/secretary_log_access.sh` may be available:

```bash
./secretary_log_access.sh logs    # Get all logs
./secretary_log_access.sh latest  # Last 10 entries
./secretary_log_access.sh errors  # ERROR logs only
./secretary_log_access.sh watch   # Live monitoring
```

### Alternative: Logcat

Logs are also written to Android Logcat:

```bash
# Real-time filtering
logcat | grep Secretary

# Filter by specific components
logcat | grep -E "(MainActivity|TaskActivity|TaskDatabaseHelper)"

# Only errors
logcat | grep -E "ERROR|Exception|FATAL"

# Clear logcat before testing
logcat -c
```

---

## Usage in Code

### Initialization

```java
// In Activity or class with Context
private static final String TAG = "YourClassName";
private AppLogger logger;

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Get logger instance
    logger = AppLogger.getInstance(this);
}
```

### Logging Methods

```java
// INFO level - General information
logger.info(TAG, "User clicked button");
logger.info(TAG, "Database opened successfully");

// DEBUG level - Detailed debugging information
logger.debug(TAG, "Variable value: " + someValue);
logger.debug(TAG, "API response code: " + responseCode);

// ERROR level - Errors and exceptions
logger.error(TAG, "Operation failed: " + errorMessage);
logger.error(TAG, "Exception occurred", exception);  // Includes stack trace
```

### Best Practices

**DO:**
- ✅ Log all user actions (button clicks, menu selections)
- ✅ Log API requests/responses (status codes, not full body)
- ✅ Log database operations (CRUD, migrations)
- ✅ Log lifecycle events (onCreate, onResume, onDestroy)
- ✅ Log errors with context (what operation failed, why)
- ✅ Use consistent TAG naming (class name)

**DON'T:**
- ❌ Log passwords, tokens, or sensitive data
- ❌ Excessive logging in loops (use sparingly)
- ❌ Log user-identifiable information (names, emails)
- ❌ Log full API response bodies (just status codes)
- ❌ Log in performance-critical paths without reason

---

## Log Categories by Component

### MainActivity
```java
private static final String TAG = "MainActivity";

logger.info(TAG, "=== Application started ===");
logger.info(TAG, "App version: " + version);
logger.info(TAG, "Settings button clicked");
logger.info(TAG, "Update dialog shown");
logger.info(TAG, "Tasks button clicked");
```

### TaskActivity
```java
private static final String TAG = "TaskActivity";

logger.info(TAG, "TaskActivity created");
logger.info(TAG, "Loaded " + taskCount + " tasks from database");
logger.info(TAG, "Task completed: " + taskTitle);
logger.info(TAG, "Task deleted: " + taskId);
logger.debug(TAG, "Filter applied: status=" + status + ", priority=" + priority);
```

### TaskDatabaseHelper
```java
private static final String TAG = "TaskDatabaseHelper";

logger.info(TAG, "Database created successfully");
logger.info(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);
logger.info(TAG, "Task inserted with ID: " + taskId);
logger.info(TAG, "Task updated: " + taskId);
logger.error(TAG, "Database migration failed", exception);
```

### UpdateChecker
```java
private static final String TAG = "UpdateChecker";

logger.info(TAG, "Starting update check. Current version: " + currentVersion);
logger.debug(TAG, "GitHub API response code: " + responseCode);
logger.info(TAG, "Latest version from GitHub: " + latestVersion);
logger.info(TAG, "Update available! " + currentVersion + " -> " + latestVersion);
logger.error(TAG, "Update check failed: " + errorMessage);
```

### UpdateInstaller
```java
private static final String TAG = "UpdateInstaller";

logger.info(TAG, "Starting download for version " + version);
logger.info(TAG, "Download enqueued with ID: " + downloadId);
logger.info(TAG, "Download completed for ID: " + downloadId);
logger.info(TAG, "APK installer intent launched successfully");
logger.error(TAG, "Download failed with status: " + status);
```

---

## Log Buffer Management

### Auto-Trimming

**Configuration:**
- **Max Entries:** 500
- **Trimming Method:** Statistical (random check)
- **Trigger:** ~2% probability per log entry
- **Retention:** Keeps latest 500 entries when trimming

**Implementation:**
```java
// In AppLogger.java
private static final int MAX_LOG_ENTRIES = 500;

private void trimLogsIfNeeded() {
    // ~2% chance of trimming check
    if (Math.random() < 0.02 && logs.size() > MAX_LOG_ENTRIES) {
        // Keep only last 500 entries
        List<String> recentLogs = new ArrayList<>(
            logs.subList(logs.size() - MAX_LOG_ENTRIES, logs.size())
        );
        logs.clear();
        logs.addAll(recentLogs);
    }
}
```

**Memory Usage:**
- Average entry: ~100 bytes
- 500 entries: ~50 KB
- Negligible impact on app performance

---

## HTTP Server Details

### Startup

**When:** Automatically in MainActivity.onCreate()
**Port:** 8080
**Bind Address:** localhost only (127.0.0.1)
**Thread:** Background thread (does not block UI)

```java
// In MainActivity.onCreate()
try {
    httpServer = new SimpleHttpServer(8080, logger);
    httpServer.start();
    logger.info(TAG, "HTTP Log Server started on port 8080");
} catch (IOException e) {
    logger.error(TAG, "Failed to start HTTP Log Server", e);
}
```

### Endpoints

**GET /** - Help/Status
- Returns: Server status and usage instructions
- Content-Type: text/plain
- Example: `curl http://localhost:8080/`

**GET /logs** - All logs
- Returns: All log entries from AppLogger buffer
- Content-Type: text/plain
- Format: One entry per line
- Example: `curl http://localhost:8080/logs`

### Error Handling

- **Port in use:** Logs error, app continues (logging still works via Logcat)
- **Connection error:** Client receives connection refused (check if server is running)
- **Request timeout:** Default HTTP timeout applies (typically 30s)

---

## Debugging Workflow

### Typical Scenarios

**1. Feature Implementation**
```bash
# Before implementing
curl http://localhost:8080/logs | tail -20  # Check current state

# Implement feature, rebuild, install

# After testing feature
curl http://localhost:8080/logs | grep "YourFeature"  # Verify logs
```

**2. Crash Debugging**
```bash
# Reproduce crash

# Immediately after crash (before restarting app)
logcat | grep -A 50 FATAL  # Get crash stack trace

# After app restarts
curl http://localhost:8080/logs | tail -100  # Check logs before crash
```

**3. Performance Analysis**
```bash
# Check timestamps for slow operations
curl http://localhost:8080/logs | grep "Operation started"
curl http://localhost:8080/logs | grep "Operation finished"
# Compare timestamps
```

**4. User-Reported Issue**
```bash
# User reproduces issue in app

# Retrieve logs
curl http://localhost:8080/logs > ~/user_issue_$(date +%Y%m%d_%H%M%S).log

# Analyze
cat ~/user_issue_*.log | grep ERROR
cat ~/user_issue_*.log | grep -B 5 -A 5 "problem keyword"
```

---

## When to Read Logs

### Critical Moments

1. **After crashes** - HTTP server may stop, but Logcat has full history
2. **During development** - Monitor real-time while testing features
3. **Before commits** - Verify changes work as expected
4. **After updates** - Confirm database migrations succeeded
5. **User reports issues** - Reproduce and capture logs

### Integration with Development

**Standard Workflow:**
```bash
# 1. Check current state
curl http://localhost:8080/logs | tail -20

# 2. Make changes, rebuild, install

# 3. Test feature

# 4. Verify logs
curl http://localhost:8080/logs | grep "YourComponent"

# 5. If errors found
curl http://localhost:8080/logs | grep ERROR

# 6. Fix and repeat
```

---

## Performance Impact

**Metrics:**
- **Memory:** ~50 KB (500 entries)
- **CPU:** Negligible (<0.1% during logging)
- **Battery:** No measurable impact
- **Network:** None (localhost only)
- **Disk I/O:** None (in-memory only)

**Thread Safety:**
- All logging methods are synchronized
- HTTP server runs on separate background thread
- No blocking of main/UI thread

---

## Comparison with File-Based Logging

| Aspect | HTTP Server (Current) | File-Based (Legacy) |
|--------|----------------------|---------------------|
| Permissions | None required | WRITE_EXTERNAL_STORAGE |
| Access | `curl localhost:8080/logs` | `cat /sdcard/...` |
| Storage | In-memory (50 KB) | File system (growing) |
| Android 11+ | ✅ Works | ❌ Scoped storage issues |
| Termux Access | ✅ Direct | ⚠️ Permission issues |
| Performance | ✅ Fast | ⚠️ File I/O overhead |
| History | 500 entries | Unlimited (until trimmed) |
| Persistence | ❌ Lost on restart | ✅ Survives restart |

**Why HTTP Server?**
- No storage permissions needed
- Works on all Android versions (including 16)
- Fast, in-memory access
- No Scoped Storage complications
- Direct access from Termux/Claude Code

---

## Troubleshooting

### HTTP Server Not Responding

**Symptoms:** `curl: (7) Failed to connect to localhost port 8080`

**Solutions:**
1. Check if app is running: `am start -n com.secretary.helloworld/.MainActivity`
2. Check Logcat for server startup: `logcat | grep "HTTP Log Server"`
3. Check for port conflicts: `netstat -an | grep 8080` (requires root)
4. Restart app to reinitialize server

### Logs Not Appearing

**Symptoms:** HTTP server responds but returns empty or incomplete logs

**Solutions:**
1. Verify logger is initialized: `curl http://localhost:8080/logs | head -5`
2. Check if logs are being written: `logcat | grep Secretary`
3. Ensure components are using AppLogger: Check source code
4. Verify MainActivity initialized logger: Check MainActivity.onCreate()

### Logs Lost After Crash

**Symptoms:** Cannot access logs after app crashes

**Solutions:**
- HTTP server stops with app crash
- Use Logcat for crash logs: `logcat | grep FATAL`
- Logs before crash are in Logcat history: `logcat -d | grep Secretary`
- Buffer is cleared on app restart (in-memory only)

---

## Future Enhancements

**Potential Improvements:**
- [ ] Persistent log history (SQLite or file backup)
- [ ] Log filtering via HTTP query params (`/logs?level=ERROR`)
- [ ] Real-time WebSocket streaming for live monitoring
- [ ] Log export functionality (download as file)
- [ ] Log search with regex patterns
- [ ] Authentication for external access (if needed)

---

## Related Documentation

- **[CLAUDE.md](../CLAUDE.md)** - Developer documentation (log access workflows)
- **[UPDATE_SYSTEM.md](./UPDATE_SYSTEM.md)** - Auto-update system documentation
- **[README.md](../README.md)** - Project overview
- **[~/CLAUDE.md](../../CLAUDE.md)** - Home directory guide

**Source Code:**
- `src/com/secretary/AppLogger.java` - Core logging implementation
- `src/com/secretary/SimpleHttpServer.java` - HTTP server
- `src/com/secretary/MainActivity.java` - Server initialization

---

**Last Updated:** 2025-11-13
**Component Version:** Phase 0 (Complete)
**Current Implementation:** HTTP Server on localhost:8080
