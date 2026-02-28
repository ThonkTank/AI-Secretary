
## [consider] Options array construction verbosity — SettingsController:145–149

**File + lines:** SettingsController.java:145–149

**Current code:**
```java
String[] options = {
        context.getString(R.string.settings_option_restore_backup),
        context.getString(R.string.settings_option_manual_backup),
        context.getString(R.string.settings_option_factory_reset),
        context.getString(R.string.settings_option_about),
};
```

**Why it's not necessarily simpler:** The code is already direct and readable. A helper method only adds value if the array is reused or if the list grows significantly. In this case, the current approach is acceptable; only consider refactoring if the array becomes a maintenance burden.

