package de.thonktank.autosecretary.presentation.alltasks

import androidx.compose.runtime.Immutable
import de.thonktank.autosecretary.domain.model.Recurrence
import de.thonktank.autosecretary.domain.model.TaskSlot

@Immutable
data class AllTasksComposeCallbacks(
    val onQuery: (String) -> Unit,
    val onStatus: (AllTasksUiState.Status) -> Unit,
    val onSlots: (Set<TaskSlot>) -> Unit,
    val onRecurrences: (Set<Recurrence>) -> Unit,
    val onWeekday: (Int) -> Unit,
    val onMode: (AllTasksUiState.Mode) -> Unit,
    val onFiltersExpanded: (Boolean) -> Unit,
    val onResetFilters: () -> Unit,
    val onToggleTask: (String) -> Unit,
    val onEditTask: (String) -> Unit,
    val onEditStep: (String, String) -> Unit,
    val onAddStep: (String) -> Unit,
    val onDeleteTask: (String, String) -> Unit,
    val onMoveSchedule: (String, TaskSlot, String?) -> Unit,
    val onMoveStep: (String, String, String?) -> Unit,
    val onSwapSteps: (String, String) -> Unit,
)

/** Pure action mapping shared by Compose semantics, gestures and focused tests. */
internal class AllTasksComposeDispatcher(
    private val state: AllTasksUiState,
    private val callbacks: AllTasksComposeCallbacks,
) {
    private val rows = AllTasksRow.project(state)

    fun drop(sourceKey: String, targetKey: String): Boolean {
        val source = rows.firstOrNull { it.key == sourceKey } ?: return false
        val target = rows.firstOrNull { it.key == targetKey } ?: return false
        if (source.kind == AllTasksRow.Kind.STEP && !source.task.archived) {
            if (target.kind == AllTasksRow.Kind.STEP_TARGET) {
                callbacks.onMoveStep(source.step.id, target.taskId, target.beforeId)
                return true
            }
            if (target.kind == AllTasksRow.Kind.STEP && !target.task.archived) {
                if (source.step.id != target.step.id) {
                    callbacks.onSwapSteps(source.step.id, target.step.id)
                }
                return true
            }
            if (target.kind == AllTasksRow.Kind.TASK_HEADER && !target.task.archived) {
                callbacks.onMoveStep(source.step.id, target.taskId, null)
                return true
            }
        }
        if (source.kind == AllTasksRow.Kind.SCHEDULE) {
            if (target.kind == AllTasksRow.Kind.SCHEDULE_TARGET) {
                callbacks.onMoveSchedule(source.schedule.id, target.slot, target.beforeId)
                return true
            }
            if (target.kind == AllTasksRow.Kind.SCHEDULE) {
                callbacks.onMoveSchedule(source.schedule.id, target.slot, target.schedule.id)
                return true
            }
        }
        return false
    }

    fun moveStepBy(row: AllTasksRow, delta: Int): Boolean {
        if (!canMoveStepBy(row, delta)) return false
        val index = stepIndex(row)
        val target = index + delta
        val before = if (delta < 0) row.task.steps[target].id
        else row.task.steps.getOrNull(index + 2)?.id
        callbacks.onMoveStep(row.step.id, row.taskId, before)
        return true
    }

    fun moveStepToTask(row: AllTasksRow, direction: Int): Boolean {
        val target = otherTask(row, direction) ?: return false
        callbacks.onMoveStep(row.step.id, target.task.id.value, null)
        return true
    }

    fun moveScheduleBy(row: AllTasksRow, delta: Int): Boolean {
        if (!canMoveScheduleBy(row, delta)) return false
        val values = scheduleInSlot(row)
        val index = scheduleIndex(row)
        val target = index + delta
        val before = if (delta < 0) values[target].id else values.getOrNull(index + 2)?.id
        callbacks.onMoveSchedule(row.schedule.id, row.slot, before)
        return true
    }

    fun moveScheduleToSlot(row: AllTasksRow, direction: Int): Boolean {
        if (!canMoveScheduleToSlot(row, direction)) return false
        val target = TaskSlot.values().getOrNull(row.slot.rank + direction) ?: return false
        callbacks.onMoveSchedule(row.schedule.id, target, null)
        return true
    }

    fun canMoveStepBy(row: AllTasksRow, delta: Int): Boolean {
        if (row.kind != AllTasksRow.Kind.STEP || row.task.archived) return false
        val index = stepIndex(row)
        return index >= 0 && index + delta in row.task.steps.indices
    }

    fun canMoveStepToTask(row: AllTasksRow, direction: Int): Boolean =
        otherTask(row, direction) != null

    fun canMoveScheduleBy(row: AllTasksRow, delta: Int): Boolean {
        if (row.kind != AllTasksRow.Kind.SCHEDULE) return false
        val index = scheduleIndex(row)
        return index >= 0 && index + delta in scheduleInSlot(row).indices
    }

    fun canMoveScheduleToSlot(row: AllTasksRow, direction: Int): Boolean =
        row.kind == AllTasksRow.Kind.SCHEDULE &&
            row.slot.rank + direction in TaskSlot.values().indices

    private fun stepIndex(row: AllTasksRow): Int =
        if (row.kind == AllTasksRow.Kind.STEP) {
            row.task.steps.indexOfFirst { it.id == row.step.id }
        } else -1

    private fun otherTask(
        row: AllTasksRow,
        direction: Int,
    ): AllTasksUiState.TaskItem? {
        if (row.kind != AllTasksRow.Kind.STEP || row.task.archived) return null
        val current = state.tasks.indexOfFirst { it.cardKey == row.cardKey }
        if (current < 0) return null
        var index = current + direction
        while (index in state.tasks.indices) {
            val candidate = state.tasks[index]
            if (!candidate.archived && candidate.task.id.value != row.taskId) return candidate
            index += direction
        }
        return null
    }

    private fun scheduleInSlot(row: AllTasksRow): List<AllTasksUiState.ScheduleItem> =
        if (row.kind == AllTasksRow.Kind.SCHEDULE) {
            state.schedule.filter { it.slot == row.slot }
        } else emptyList()

    private fun scheduleIndex(row: AllTasksRow): Int =
        scheduleInSlot(row).indexOfFirst { it.id == row.schedule.id }
}
