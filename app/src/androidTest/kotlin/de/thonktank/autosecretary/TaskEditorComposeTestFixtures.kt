package de.thonktank.autosecretary

import de.thonktank.autosecretary.domain.model.Recurrence
import de.thonktank.autosecretary.domain.model.StepAmount
import de.thonktank.autosecretary.domain.model.TaskBoundKind
import de.thonktank.autosecretary.domain.model.TaskSlot
import de.thonktank.autosecretary.domain.model.TimeOfDay

internal fun taskEditorComposeReferenceState(): EditorUiState {
    val steps = listOf(
        EditorStepState("step-1", "Dehnen", 0, StepAmount.none(), ""),
        EditorStepState("step-2", "Atmen", 0, StepAmount.none(), ""),
    )
    return EditorUiState.create().draft(
        "Morgenroutine",
        TaskSlot.MORNING,
        30,
        Recurrence.DAILY,
        1,
        0,
        TimeOfDay.MORNING.bit,
        TaskBoundKind.FOREVER,
        null,
        null,
        null,
        null,
        "ruhig beginnen",
        steps,
        null,
        3,
    )
}
