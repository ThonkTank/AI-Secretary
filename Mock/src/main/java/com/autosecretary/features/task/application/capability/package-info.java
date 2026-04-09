/**
 * Capability map for the bucket-based task planning target architecture.
 *
 * <h2>Why this package exists</h2>
 * Declares the high-level decomposition so implementations stay modular:
 * scoring, slot-eligibility, capacity, and planning are independent contracts.
 *
 * <h2>Who calls these capabilities</h2>
 * Orchestration (nightly job and completion-triggered replan) from application call-chains.
 *
 * <h2>How capabilities interact</h2>
 * <ol>
 *   <li>Task-definition capability validates and persists name/parent/recurrence contracts.</li>
 *   <li>Capacity is computed from user bucket windows + calendar blockers.</li>
 *   <li>Scoring computes rankable task priorities (without explicit clock-time fit).</li>
 *   <li>Slot assignment maps tasks to one-or-many eligible buckets.</li>
 *   <li>Planner combines score + eligibility + capacity into a concrete bucket plan.</li>
 *   <li>Completion replan removes stale later duplicates and refills freed minutes.</li>
 * </ol>
 *
 * <h2>Why this split matters</h2>
 * Each component returns data exactly in the form downstream needs, reducing ad-hoc
 * data transformation in callers and making parameter planning explicit up-front.
 */
package com.autosecretary.features.task.application.capability;
