package de.thonktank.autosecretary.presentation.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.thonktank.autosecretary.DayPalette
import de.thonktank.autosecretary.R
import de.thonktank.autosecretary.domain.model.ResistanceLoad
import de.thonktank.autosecretary.domain.model.StepPrescription
import de.thonktank.autosecretary.domain.model.TrainingAssistantPolicy
import de.thonktank.autosecretary.domain.model.TrainingAssistantState
import de.thonktank.autosecretary.domain.model.TrainingMuscleGroup
import de.thonktank.autosecretary.domain.model.TrainingPrescription

/** Editor owner for every assistant-specific control, bound only to canonical domain values. */
@Composable
internal fun TrainingAssistantEditorSection(
    prescription: StepPrescription,
    policy: TrainingAssistantPolicy?,
    assistantState: TrainingAssistantState,
    hasLoadIssue: Boolean,
    stepId: String,
    palette: DayPalette,
    onChange: (StepPrescription, TrainingAssistantPolicy?) -> Unit,
) {
    val training = prescription.training
    val enabled = policy != null
    Label(R.string.training_assistant_label, palette, Modifier.padding(top = 22.dp, bottom = 10.dp))
    ChipFlow {
        EditorChip(R.string.training_assistant_off, !enabled, palette) {
            onChange(StepPrescription(prescription.amount, prescription.rest, null), null)
        }
        EditorChip(R.string.training_assistant_automatic, enabled, palette) {
            if (!enabled) {
                val load = ResistanceLoad.numeric(
                    ResistanceLoad.Mode.EXTERNAL,
                    ResistanceLoad.Unit.KG,
                    0,
                )
                onChange(
                    StepPrescription(
                        prescription.amount,
                        prescription.rest,
                        TrainingPrescription(load, 2),
                    ),
                    TrainingAssistantPolicy.defaults(null),
                )
            }
        }
    }
    if (policy == null || training == null) {
        EditorText(
            stringResource(R.string.training_assistant_explanation),
            Color.argb(palette.muted),
            14,
            Modifier.padding(top = 8.dp),
            italic = true,
        )
        return
    }

    val assistantStatus = when (assistantState.status) {
        TrainingAssistantState.Status.CALIBRATING -> stringResource(
            R.string.training_status_calibrating,
            assistantState.eligibleObservations.coerceAtMost(3),
        )
        TrainingAssistantState.Status.ACTIVE -> stringResource(R.string.training_status_active)
        TrainingAssistantState.Status.PAUSED -> stringResource(R.string.training_status_paused)
        TrainingAssistantState.Status.DISABLED -> stringResource(R.string.training_assistant_off)
    }
    EditorText(
        assistantStatus,
        Color.argb(palette.ink2),
        14,
        Modifier.padding(top = 8.dp).testTag("task-editor:training-status:$stepId"),
        serif = false,
    )

    Label(R.string.training_load_mode, palette, Modifier.padding(top = 18.dp, bottom = 8.dp))
    ChipFlow {
        trainingLoadModes.forEach { (label, mode) ->
            EditorChip(label, training.load.mode == mode, palette) {
                val load = if (mode == ResistanceLoad.Mode.BODYWEIGHT) {
                    ResistanceLoad.bodyweight()
                } else {
                    ResistanceLoad.numeric(
                        mode,
                        if (training.load.unit == ResistanceLoad.Unit.LB) ResistanceLoad.Unit.LB
                        else ResistanceLoad.Unit.KG,
                        training.load.milliUnits ?: 0,
                    )
                }
                updateTraining(prescription, policy, training, load = load, onChange = onChange)
            }
        }
    }
    if (training.load.adjustable()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            NumberInput(
                ((training.load.milliUnits ?: 0L) / 1000L).toInt(),
                R.string.training_load_value,
                palette,
                { value ->
                    val load = ResistanceLoad.numeric(
                        training.load.mode,
                        training.load.unit,
                        (value ?: 0).coerceAtLeast(0) * 1000L,
                    )
                    updateTraining(
                        prescription,
                        policy,
                        training,
                        load = load,
                        onChange = onChange,
                    )
                },
                Modifier.weight(1f),
                trainingFocusTag("training-load", stepId),
            )
            ChipFlow(Modifier.weight(1f)) {
                EditorChip(
                    R.string.training_unit_kg,
                    training.load.unit == ResistanceLoad.Unit.KG,
                    palette,
                ) {
                    updateTraining(
                        prescription,
                        policy,
                        training,
                        load = ResistanceLoad.numeric(
                            training.load.mode,
                            ResistanceLoad.Unit.KG,
                            training.load.milliUnits ?: 0,
                        ),
                        onChange = onChange,
                    )
                }
                EditorChip(
                    R.string.training_unit_lb,
                    training.load.unit == ResistanceLoad.Unit.LB,
                    palette,
                ) {
                    updateTraining(
                        prescription,
                        policy,
                        training,
                        load = ResistanceLoad.numeric(
                            training.load.mode,
                            ResistanceLoad.Unit.LB,
                            training.load.milliUnits ?: 0,
                        ),
                        onChange = onChange,
                    )
                }
            }
        }
        if (hasLoadIssue) ErrorText(R.string.err_training_load_required, palette)
    }

    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        NumberInput(
            training.targetRir,
            R.string.training_target_rir,
            palette,
            { value ->
                updateTraining(
                    prescription,
                    policy,
                    training,
                    targetRir = (value ?: 2).coerceIn(0, 5),
                    onChange = onChange,
                )
            },
            Modifier.weight(1f),
            trainingFocusTag("training-rir", stepId),
        )
        NumberInput(
            policy.automaticWeeklySetCeiling,
            R.string.training_weekly_ceiling,
            palette,
            { value ->
                updateTraining(
                    prescription,
                    policy,
                    training,
                    ceiling = (value ?: 10).coerceAtLeast(1),
                    onChange = onChange,
                )
            },
            Modifier.weight(1f),
            trainingFocusTag("training-ceiling", stepId),
        )
    }

    Label(R.string.training_primary_muscle, palette, Modifier.padding(top = 18.dp, bottom = 8.dp))
    ChipFlow {
        TrainingMuscleGroup.values().forEach { muscle ->
            EditorChip(trainingMuscleLabel(muscle), policy.primaryMuscle == muscle, palette) {
                updateTraining(
                    prescription,
                    policy,
                    training,
                    primary = muscle,
                    onChange = onChange,
                )
            }
        }
    }
    Label(R.string.training_secondary_muscles, palette, Modifier.padding(top = 14.dp, bottom = 8.dp))
    ChipFlow {
        TrainingMuscleGroup.values().filter { it != policy.primaryMuscle }.forEach { muscle ->
            EditorChip(
                trainingMuscleLabel(muscle),
                policy.secondaryMuscles.contains(muscle),
                palette,
            ) {
                val selected = policy.secondaryMuscles.toMutableSet()
                if (!selected.add(muscle)) selected.remove(muscle)
                updateTraining(
                    prescription,
                    policy,
                    training,
                    secondaries = selected,
                    onChange = onChange,
                )
            }
        }
    }
}

/** Persisted training audit belongs to the existing step editor, never to Today. */
@Composable
internal fun TrainingHistorySection(
    history: TrainingHistoryUiModel?,
    dirty: Boolean,
    stepId: String,
    palette: DayPalette,
    onUndo: () -> Unit,
) {
    if (history == null || history.entries.isEmpty()) return
    var expanded by rememberSaveable(stepId) { mutableStateOf(false) }
    val toggleDescription = stringResource(if (expanded) {
        R.string.content_collapse_training_history
    } else {
        R.string.content_expand_training_history
    })
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics {
                    contentDescription = toggleDescription
                    role = Role.Button
                }
                .testTag("task-editor:training-history:$stepId"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditorText(
                stringResource(R.string.training_history_toggle),
                Color.argb(palette.ink2),
                17,
                Modifier.weight(1f),
                serif = false,
            )
            EditorText(
                if (expanded) "⌃" else "⌄",
                Color.argb(palette.muted),
                18,
                serif = false,
            )
        }
        if (expanded) {
            history.entries.forEachIndexed { index, entry ->
                EditorText(
                    entry,
                    Color.argb(palette.muted),
                    14,
                    Modifier.padding(top = if (index == 0) 4.dp else 8.dp),
                    serif = false,
                )
            }
            if (dirty && history.canUndo) {
                EditorText(
                    stringResource(R.string.training_history_dirty_hint),
                    Color.argb(palette.muted),
                    14,
                    Modifier.padding(top = 10.dp),
                    italic = true,
                )
            }
            if (history.canUndo) {
                EditorButton(
                    text = stringResource(R.string.training_undo),
                    palette = palette,
                    onClick = onUndo,
                    enabled = !dirty,
                    modifier = Modifier.padding(top = 6.dp)
                        .testTag("task-editor:training-undo:$stepId"),
                )
            }
        }
    }
}

private fun updateTraining(
    prescription: StepPrescription,
    currentPolicy: TrainingAssistantPolicy,
    currentTraining: TrainingPrescription,
    load: ResistanceLoad = currentTraining.load,
    targetRir: Int = currentTraining.targetRir,
    ceiling: Int = currentPolicy.automaticWeeklySetCeiling,
    primary: TrainingMuscleGroup? = currentPolicy.primaryMuscle,
    secondaries: Set<TrainingMuscleGroup> = currentPolicy.secondaryMuscles,
    onChange: (StepPrescription, TrainingAssistantPolicy?) -> Unit,
) = onChange(
    StepPrescription(
        prescription.amount,
        prescription.rest,
        TrainingPrescription(load, targetRir),
    ),
    TrainingAssistantPolicy(
        currentPolicy.minSets,
        currentPolicy.maxSets,
        currentPolicy.minRepetitions,
        currentPolicy.maxRepetitions,
        ceiling,
        primary,
        secondaries,
    ),
)

@StringRes
private fun trainingMuscleLabel(muscle: TrainingMuscleGroup): Int = when (muscle) {
    TrainingMuscleGroup.CHEST -> R.string.training_muscle_chest
    TrainingMuscleGroup.BACK -> R.string.training_muscle_back
    TrainingMuscleGroup.SHOULDERS -> R.string.training_muscle_shoulders
    TrainingMuscleGroup.BICEPS -> R.string.training_muscle_biceps
    TrainingMuscleGroup.TRICEPS -> R.string.training_muscle_triceps
    TrainingMuscleGroup.QUADRICEPS -> R.string.training_muscle_quadriceps
    TrainingMuscleGroup.HAMSTRINGS -> R.string.training_muscle_hamstrings
    TrainingMuscleGroup.GLUTES -> R.string.training_muscle_glutes
    TrainingMuscleGroup.CALVES -> R.string.training_muscle_calves
    TrainingMuscleGroup.CORE -> R.string.training_muscle_core
}

private fun trainingFocusTag(field: String, stepId: String): String = "step:$stepId:$field"

private val trainingLoadModes = listOf(
    R.string.training_load_external to ResistanceLoad.Mode.EXTERNAL,
    R.string.training_load_bodyweight to ResistanceLoad.Mode.BODYWEIGHT,
    R.string.training_load_bodyweight_plus to ResistanceLoad.Mode.BODYWEIGHT_PLUS,
    R.string.training_load_assisted to ResistanceLoad.Mode.ASSISTED_BODYWEIGHT,
)
