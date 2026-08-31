package de.thonktank.autosecretary

import de.thonktank.autosecretary.domain.model.Recurrence
import de.thonktank.autosecretary.domain.model.StepAmount
import de.thonktank.autosecretary.domain.model.StepActivationKind
import de.thonktank.autosecretary.domain.model.StepPrescription
import de.thonktank.autosecretary.domain.model.TaskBoundKind
import de.thonktank.autosecretary.domain.model.TaskSlot
import de.thonktank.autosecretary.domain.model.TimeOfDay
import de.thonktank.autosecretary.domain.model.ResistanceLoad
import de.thonktank.autosecretary.domain.model.RestTimerPolicy
import de.thonktank.autosecretary.domain.model.TrainingAssistantPolicy
import de.thonktank.autosecretary.domain.model.TrainingMuscleGroup
import de.thonktank.autosecretary.domain.model.TrainingPrescription

internal fun taskEditorComposeReferenceState(): EditorUiState {
    val steps = listOf(
        editorStep("step-1", "Dehnen"),
        editorStep("step-2", "Atmen"),
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

private fun editorStep(id: String, text: String) = EditorStepState(
    id, text, StepCadenceMode.ALWAYS, 0, null,
    StepPrescription.forAmount(StepAmount.none()), null, "", StepActivationKind.SCHEDULED,
)

internal fun taskEditorComposeEditReferenceState(): EditorUiState {
    val bundle = taskEditorComposeReferenceState().toBundle()
    bundle.putString("task_id", "task-1")
    return EditorUiState.fromBundle(bundle)
}

internal fun trainingAssistantEditorState(): EditorUiState {
    val step = EditorStepState(
        "press", "Beinpresse", StepCadenceMode.ALWAYS, 0, null,
        StepPrescription(
            StepAmount.setsReps(3, 12),
            RestTimerPolicy.inherit(),
            TrainingPrescription(
                ResistanceLoad.numeric(
                    ResistanceLoad.Mode.EXTERNAL,
                    ResistanceLoad.Unit.KG,
                    0,
                ),
                2,
            ),
        ),
        TrainingAssistantPolicy.defaults(TrainingMuscleGroup.QUADRICEPS),
        "",
        StepActivationKind.SCHEDULED,
    )
    val issue = ValidationIssue.step(ValidationIssue.Field.TRAINING_LOAD, step.id)
    return EditorUiState.create().draft(
        "Gym", TaskSlot.MORNING, 45, Recurrence.DAILY, 1, 0,
        TimeOfDay.MORNING.bit, TaskBoundKind.FOREVER, null, null, null, null, "",
        listOf(step), step.id, 2,
    ).withPage(EditorUiState.Page.STEPS, false)
        .withValidationAttempt(EditorUiState.Page.STEPS, step.id, setOf(issue))
}
