# /src/main/res/xml — Code Review Backlog

## [warning] Manual XML synchronization of WidgetConfiguration constant

**Location:** `widget_task_info.xml:19`, `widget_budget_info.xml:19`

**What the smell is:**
Both widget XML files contain hardcoded `android:updatePeriodMillis="1800000"` that must be manually kept in sync with `WidgetConfiguration.WIDGET_UPDATE_PERIOD_MILLIS` in Java code. Android's framework limitation prevents direct Java constant references in XML, creating a synchronization requirement across three locations: the Java constant and two XML files.

**Why it will cause problems:**
- Violates DRY principle (magic number duplicated in three separate files)
- Error-prone: developers may change the Java constant and forget to update one or both XML files
- Risk compounds over time if more widgets are added (more sync points)
- Silent failure: Java code will use one update period, but widgets may use different periods from XML, creating hard-to-debug timing inconsistencies
- Widget refresh rates could diverge silently without any build-time warnings

**Current state:**
- Both XML files currently have correct value (1800000 = 30 minutes)
- ✅ Fixed: Comments in XML files have been enhanced to explicitly state the synchronization requirement, consequences of mismatch, and provide clear guidance
- Javadoc in WidgetConfiguration.java documents the requirement clearly
- No build-time verification exists

**Deferred fix:**
Build-time verification (option below) requires Gradle configuration changes and is deferred:
- Add a build task (Gradle) to verify both XML files contain the correct value, fail at compile-time if mismatch exists

**Recommendation:** Documentation has been improved to mitigate the risk of synchronization failures. The core design smell (magic number duplication) remains an Android framework limitation, but developers are now better informed when editing these files.
