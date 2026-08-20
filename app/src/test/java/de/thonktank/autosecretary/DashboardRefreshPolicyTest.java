package de.thonktank.autosecretary;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;

import org.junit.Test;

public final class DashboardRefreshPolicyTest {
    private final DashboardRefreshPolicy policy = new DashboardRefreshPolicy();
    private final LocalDate today = LocalDate.of(2026, 8, 20);

    @Test public void dateTickOnlyRefreshesWhenCalendarDateChanged() {
        assertFalse(policy.requiresLoad(DashboardRefreshReason.DATE_CHANGED, today, today));
        assertTrue(policy.requiresLoad(DashboardRefreshReason.DATE_CHANGED,
                today.minusDays(1), today));
        assertTrue(policy.requiresLoad(DashboardRefreshReason.DATE_CHANGED, null, today));
    }

    @Test public void foregroundAndExternalChangesAlwaysRefresh() {
        assertTrue(policy.requiresLoad(DashboardRefreshReason.FOREGROUND, today, today));
        assertTrue(policy.requiresLoad(DashboardRefreshReason.EXTERNAL_DATA, today, today));
        assertTrue(policy.requiresLoad(DashboardRefreshReason.PERSISTED_CHANGE, today, today));
    }
}
