package de.thonktank.autosecretary.presentation.alltasks

import de.thonktank.autosecretary.domain.model.Recurrence
import de.thonktank.autosecretary.domain.model.Task
import de.thonktank.autosecretary.domain.model.TaskBoundKind
import de.thonktank.autosecretary.domain.model.TaskCatalog
import de.thonktank.autosecretary.domain.model.TaskId
import de.thonktank.autosecretary.domain.model.TaskScheduleEntry
import de.thonktank.autosecretary.domain.model.TaskSlot
import de.thonktank.autosecretary.domain.model.TaskStepTemplate
import java.time.LocalDate

/** Deterministic debug-only catalog shared by phase-6a comparison hosts and tests. */
object AllTasksComposeFixture {
    @JvmStatic
    fun state(includeArchived: Boolean = true): AllTasksUiState = AllTasksUiState.from(
        catalog(includeArchived),
        AllTasksPresentationState.defaults(),
    )

    @JvmStatic
    fun catalog(includeArchived: Boolean = true): TaskCatalog {
        val items = mutableListOf(
            item(
                "morning", "Morgenroutine", Recurrence.DAILY, 1_024,
                listOf(TaskSlot.MORNING, TaskSlot.EVENING),
                listOf("Haare waschen", "Anziehen", "Tabletten nehmen"), false,
            ),
            item(
                "bed", "Bett machen", Recurrence.DAILY, 2_048,
                listOf(TaskSlot.MORNING), emptyList(), false,
            ),
            item(
                "office", "Abgabe Statistik-Übung", Recurrence.ONCE, 3_072,
                listOf(TaskSlot.MIDDAY), listOf("Aufgabenblatt lesen"), false,
            ),
            item(
                "wash", "Wäsche waschen", Recurrence.WEEKDAYS, 4_096,
                listOf(TaskSlot.EVENING),
                listOf("Wäsche sortieren", "Maschine anstellen", "Aufhängen"), false,
            ),
        )
        if (includeArchived) {
            items += item(
                "archive", "Winterprojekt", Recurrence.ONCE, 5_120,
                listOf(TaskSlot.LATER), listOf("Kisten sortieren"), true,
            )
        }
        return TaskCatalog(items)
    }

    @JvmStatic
    fun longState(count: Int): AllTasksUiState {
        val items = (0 until count).map { index ->
            item(
                "task-$index",
                "Aufgabe $index",
                Recurrence.DAILY,
                index.toLong() * 1_024,
                listOf(TaskSlot.MORNING),
                emptyList(),
                false,
            )
        }
        return AllTasksUiState.from(TaskCatalog(items), AllTasksPresentationState.defaults())
    }

    @JvmStatic
    fun longDragState(count: Int): AllTasksUiState {
        val items = mutableListOf(
            item(
                "drag-source",
                "Drag Quelle",
                Recurrence.DAILY,
                0,
                listOf(TaskSlot.MORNING),
                listOf("Drag Schritt"),
                false,
            ),
        )
        items += (1 until count).map { index ->
            item(
                "drag-target-$index",
                "Drag Ziel $index",
                Recurrence.DAILY,
                index.toLong() * 1_024,
                listOf(TaskSlot.MORNING),
                emptyList(),
                false,
            )
        }
        return AllTasksUiState.from(TaskCatalog(items), AllTasksPresentationState.defaults())
            .toggleExpanded(AllTasksUiState.cardKey("drag-source", TaskSlot.MORNING))
    }

    private fun item(
        id: String,
        title: String,
        recurrence: Recurrence,
        order: Long,
        slots: List<TaskSlot>,
        steps: List<String>,
        archived: Boolean,
    ): TaskCatalog.Item {
        val date = LocalDate.of(2026, 8, 23)
        val task = Task.restore(
            TaskId.of(id), title, recurrence, 1,
            if (recurrence == Recurrence.WEEKDAYS) 1 or 8 else 0,
            false, "", false, archived,
            if (archived) null else date,
            if (archived) LocalDate.of(2026, 8, 20) else null,
            if (archived) LocalDate.of(2026, 8, 20) else null,
            if (archived) null else date,
            order, archived, null, TaskBoundKind.FOREVER,
            null, null, null, null, "",
        )
        val schedule = slots.map { slot ->
            TaskScheduleEntry("$id-${slot.name}", task.id, slot, order)
        }
        val templates = steps.mapIndexed { index, text ->
            TaskStepTemplate("$id-step-$index", task.id, index, text)
        }
        return TaskCatalog.Item(task, templates, schedule)
    }
}
