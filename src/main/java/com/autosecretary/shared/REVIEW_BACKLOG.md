# /shared — Code Review Backlog

## [warning] Manual XML synchronization of WidgetConfiguration constant

**Location:** `WidgetConfiguration.java:31`

**What the smell is:**
The `WIDGET_UPDATE_PERIOD_MILLIS` constant must be manually kept in sync with two separate XML configuration files (`widget_task_info.xml` and `widget_budget_info.xml`). Android's framework limitation prevents direct Java constant references in XML, creating a synchronization requirement.

**Why it will cause problems:**
- Violates DRY principle (value exists in three places)
- Error-prone: developers may change the Java constant and forget to update one or both XML files
- Risk compounds over time if more widgets are added (more sync points)
- Silent failure: Java will use one update period, but widgets may use different periods from XML, creating hard-to-debug timing inconsistencies
- Currently mitigated by strong documentation, but documentation alone is fragile

**Current state:**
- Both XML files currently have correct value (1800000)
- Javadoc clearly documents the requirement and lists both files
- No build-time verification exists

**Possible fixes:**
1. Add a build task (Gradle) to verify both XML files contain the correct value, fail at compile-time if mismatch exists
2. Move widget configuration to a shared resource or constants that can be read by both Java and XML (requires significant refactoring)
3. Continue current approach with enhanced tool-assisted validation

**Recommendation:** Defer. While this is a real smell, fixing it requires either build system changes (beyond code review scope) or substantial architecture changes. The current documentation mitigates risk adequately for now. A future cycle should consider adding build-time verification.
