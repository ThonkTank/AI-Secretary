package de.thonktank.autosecretary;

import java.time.LocalDate;

/** Pure decision policy for refresh causes; it does not perform I/O. */
final class DashboardRefreshPolicy {
    boolean requiresLoad(DashboardRefreshReason reason, LocalDate loadedDate, LocalDate today) {
        if (reason == null) return false;
        if (reason == DashboardRefreshReason.DATE_CHANGED)
            return loadedDate == null || today == null || !today.equals(loadedDate);
        return reason == DashboardRefreshReason.INITIAL
                || reason == DashboardRefreshReason.FOREGROUND
                || reason == DashboardRefreshReason.PERSISTED_CHANGE
                || reason == DashboardRefreshReason.EXTERNAL_DATA;
    }
}
