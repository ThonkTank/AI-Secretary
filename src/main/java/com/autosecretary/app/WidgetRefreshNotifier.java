package com.autosecretary.app;

/**
 * Application-facing abstraction for triggering widget refreshes without
 * depending directly on UI-layer widget provider classes.
 */
public interface WidgetRefreshNotifier {
    void refreshTaskWidgets();
    void refreshBudgetWidgets();
}
