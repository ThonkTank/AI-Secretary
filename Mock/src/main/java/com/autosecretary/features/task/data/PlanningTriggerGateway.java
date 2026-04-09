package com.autosecretary.features.task.data;

/**
 * Platform integration seam for nightly planning execution.
 *
 * <h2>Who uses this</h2>
 * App boot/startup lifecycle schedules trigger; system callback invokes nightly chain.
 *
 * <h2>External APIs implementation will access</h2>
 * Platform scheduler/alarm APIs.
 */
public interface PlanningTriggerGateway {

    /** Schedules or re-arms nightly trigger. */
    void scheduleNightlyTrigger();

    /** Callback fired by system scheduler to start nightly planning call-chain. */
    void onNightlyTrigger();
}
