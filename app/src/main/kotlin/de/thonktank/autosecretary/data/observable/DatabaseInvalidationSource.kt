package de.thonktank.autosecretary.data.observable

import de.thonktank.autosecretary.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate

/** Cold database invalidation stream; synchronous repositories remain the only data readers. */
class DatabaseInvalidationSource(database: AppDatabase) {
    val changes: Flow<Set<String>> = database.invalidationTracker
        .createFlow(*TABLE_NAMES, emitInitialState = true)
        .conflate()

    private companion object {
        private val TABLE_NAMES = arrayOf(
            "tasks",
            "task_steps",
            "task_schedule_entries",
            "occurrences",
            "occurrence_steps",
            "repetition_results",
            "stats",
            "combo_progress",
            "reward_bookings",
            "reward_assignments",
            "capacity_resources",
            "step_transitions",
            "step_resource_leases",
            "step_flow_runs",
            "flow_run_steps",
            "flow_run_resources",
        )
    }
}
