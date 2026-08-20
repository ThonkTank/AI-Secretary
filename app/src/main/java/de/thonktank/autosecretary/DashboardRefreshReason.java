package de.thonktank.autosecretary;

/** Explicit lifecycle/data causes for a dashboard reload. */
enum DashboardRefreshReason {
    INITIAL,
    FOREGROUND,
    DATE_CHANGED,
    PERSISTED_CHANGE,
    EXTERNAL_DATA
}
