package de.thonktank.autosecretary.presentation.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.thonktank.autosecretary.DayPalette
import de.thonktank.autosecretary.EditorStepState
import de.thonktank.autosecretary.EditorUiState
import de.thonktank.autosecretary.R
import de.thonktank.autosecretary.StepCadenceMode
import de.thonktank.autosecretary.ValidationIssue
import de.thonktank.autosecretary.domain.model.Recurrence
import de.thonktank.autosecretary.domain.model.RestTimerPolicy
import de.thonktank.autosecretary.domain.model.ResistanceLoad
import de.thonktank.autosecretary.domain.model.StepAmount
import de.thonktank.autosecretary.domain.model.StepAmountKind
import de.thonktank.autosecretary.domain.model.TrainingAssistantConfig
import de.thonktank.autosecretary.domain.model.TrainingAssistantState
import de.thonktank.autosecretary.domain.model.TrainingMuscleGroup
import de.thonktank.autosecretary.editor.TaskEditorStateReducer
import de.thonktank.autosecretary.presentation.TaskEditorTextFormatter

@Composable
internal fun EditorStepsPage(
    state: EditorUiState,
    palette: DayPalette,
    layout: EditorLayout,
    formatter: TaskEditorTextFormatter,
    dispatcher: TaskEditorComposeDispatcher,
) {
    Question(R.string.editor_frage_schritte, palette)
    state.stepStates.forEachIndexed { index, step ->
        CollapsedStep(state, step, index, palette, formatter, dispatcher)
    }
    val addLabel = stringResource(R.string.step_add)
    val addShape = leafShape(10, 64, 10, 64)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
            .height(52.dp)
            .drawBehind {
                drawRoundRect(
                    color = Color.argb(palette.dot),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(6.dp.toPx(), 5.dp.toPx()),
                        ),
                    ),
                )
            }
            .clickable(role = Role.Button) {
                dispatcher.emit(TaskEditorStateReducer.addStep(state))
            }
            .semantics {
                role = Role.Button
                contentDescription = addLabel
            }
            .testTag("task-editor:add-step")
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        EditorText(
            stringResource(R.string.editor_step_add_label, addLabel),
            Color.argb(palette.ink2),
            15,
            serif = false,
        )
    }
    if (state.stepStates.size >= 2) {
        val flowLabel = stringResource(R.string.flow_editor_open)
        val flowMeta = stringResource(
            if (state.flowDraft.configured()) R.string.flow_editor_configured
            else R.string.flow_editor_optional,
        )
        LeafSurface(
            palette = palette,
            level = 3,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp)
                .testTag("task-editor:flow-open"),
            rotation = .6f,
            clickableLabel = "$flowLabel. $flowMeta",
            onClick = {
                dispatcher.navigate(EditorUiState.Page.FLOW, state.returnToSummary)
            },
            padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Column {
                EditorText(flowLabel, Color.argb(palette.ink2), 18)
                EditorText(
                    flowMeta,
                    Color.argb(palette.muted),
                    14,
                    Modifier.padding(top = 2.dp),
                    serif = false,
                )
            }
        }
    }
}

@Composable
private fun CollapsedStep(
    state: EditorUiState,
    step: EditorStepState,
    index: Int,
    palette: DayPalette,
    formatter: TaskEditorTextFormatter,
    dispatcher: TaskEditorComposeDispatcher,
) {
    val title = step.text.ifEmpty { stringResource(R.string.step_name_hint) }
    val meta = if (state.flowDraft.isFollowUp(step.id)) {
        stringResource(R.string.flow_role_follow_up)
    } else {
        formatter.stepMeta(step)
    }
    val accessibleMeta = meta.ifEmpty { stringResource(R.string.editor_summary_empty) }
    val description = stringResource(R.string.a11y_editor_step_row, index + 1, title, accessibleMeta)
    val up = stringResource(R.string.a11y_editor_move_up_step, title)
    val down = stringResource(R.string.a11y_editor_move_down_step, title)
    val error = stepHasAnyIssue(state, step.id)
    val even = index % 2 == 0
    LeafSurface(
        palette = palette,
        level = 2,
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp).testTag("task-editor:step:${step.id}"),
        rotation = if (even) -.8f else .9f,
        topStart = if (even) 56 else 8,
        topEnd = if (even) 8 else 56,
        bottomEnd = if (even) 56 else 8,
        bottomStart = if (even) 8 else 56,
        clickableLabel = description,
        onClick = { dispatcher.emit(TaskEditorStateReducer.expandStep(state, step.id)) },
        padding = PaddingValues(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
        fillOverride = if (error) Color.argb(palette.bad).copy(alpha = .10f) else null,
        edgeOverride = if (error) Color.argb(palette.bad) else null,
        strokeWidth = if (error) 2.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().semantics {
                customActions = listOf(
                    CustomAccessibilityAction(up) {
                        moveStep(state, index, index - 1, dispatcher)
                        true
                    },
                    CustomAccessibilityAction(down) {
                        moveStep(state, index, index + 1, dispatcher)
                        true
                    },
                )
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditorText((index + 1).toString(), Color.argb(palette.muted), 16, italic = true)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                EditorText(
                    title,
                    Color.argb(if (error) palette.bad else palette.ink),
                    19,
                    maxLines = 1,
                )
                if (meta.isNotEmpty()) {
                    EditorText(meta, Color.argb(palette.muted), 14, italic = true, maxLines = 1)
                }
            }
            MoveButton(R.string.editor_move_up_symbol, up, palette) {
                moveStep(state, index, index - 1, dispatcher)
            }
            MoveButton(R.string.editor_move_down_symbol, down, palette) {
                moveStep(state, index, index + 1, dispatcher)
            }
        }
    }
}

@Composable
private fun MoveButton(
    @StringRes symbol: Int,
    description: String,
    palette: DayPalette,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        EditorText(stringResource(symbol), Color.argb(palette.dot), 15, serif = false)
    }
}

@Composable
internal fun EditorStepDetailPage(
    state: EditorUiState,
    palette: DayPalette,
    layout: EditorLayout,
    dispatcher: TaskEditorComposeDispatcher,
) {
    val index = dispatcher.expandedIndex()
    if (index < 0) return
    val step = state.stepStates[index]
    val titleError = state.hasStepIssue(ValidationIssue.Field.STEP_TITLE, step.id)
    val titleFocus = remember(step.id) { FocusRequester() }
    LaunchedEffect(titleError, step.id) { if (titleError) titleFocus.requestFocus() }
    Question(R.string.editor_frage_schritt, palette)
    EditorInput(
        value = step.text,
        onValueChange = { updateStep(state, index, step.withText(it), dispatcher) },
        palette = palette,
        modifier = Modifier.fillMaxWidth().padding(top = 22.dp).height(48.dp),
        hint = stringResource(R.string.step_name_hint),
        error = titleError,
        focusRequester = titleFocus,
        tag = focusTag("title", step.id),
    )
    if (titleError) {
        ErrorText(R.string.err_step_empty, palette)
    }
    if (state.recurrence != Recurrence.ONCE && !state.flowDraft.isFollowUp(step.id)) {
        StepCadence(state, step, index, palette, layout, dispatcher)
    }
    Label(R.string.step_amount_label, palette, Modifier.padding(top = 24.dp, bottom = 10.dp))
    ChipFlow {
        amountChoices.forEach { (label, kind) ->
            EditorChip(label, step.amount.kind() == kind, palette) {
                updateStep(state, index, step.withAmount(selectedAmount(kind, step.amount)), dispatcher)
            }
        }
    }
    AmountInputs(state, step, index, palette, dispatcher)
    if (step.amount is StepAmount.SetsReps) {
        TrainingAssistantInputs(state, step, index, palette, dispatcher)
        RestTimerInputs(state, step, index, palette, dispatcher)
    }
    if (state.hasStepIssue(ValidationIssue.Field.STEP_AMOUNT, step.id)) {
        ErrorText(R.string.err_amount_zero, palette)
    }
    Label(R.string.step_note_label, palette, Modifier.padding(top = 24.dp, bottom = 4.dp))
    EditorInput(
        value = step.note,
        onValueChange = { updateStep(state, index, step.withNote(it), dispatcher) },
        palette = palette,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        hint = stringResource(R.string.field_note_hint),
        tag = focusTag("note", step.id),
    )
}

@Composable
private fun TrainingAssistantInputs(
    state: EditorUiState,
    step: EditorStepState,
    index: Int,
    palette: DayPalette,
    dispatcher: TaskEditorComposeDispatcher,
) {
    val config = step.trainingAssistant
    Label(R.string.training_assistant_label, palette, Modifier.padding(top = 22.dp, bottom = 10.dp))
    ChipFlow {
        EditorChip(R.string.training_assistant_off, !config.enabled, palette) {
            updateStep(state, index, step.withTrainingAssistant(TrainingAssistantConfig.disabled()), dispatcher)
        }
        EditorChip(R.string.training_assistant_automatic, config.enabled, palette) {
            if (!config.enabled) {
                updateStep(
                    state,
                    index,
                    step.withTrainingAssistant(
                        TrainingAssistantConfig.defaults(
                            ResistanceLoad.numeric(
                                ResistanceLoad.Mode.EXTERNAL,
                                ResistanceLoad.Unit.KG,
                                0,
                            ),
                            null,
                        ),
                    ),
                    dispatcher,
                )
            }
        }
    }
    if (!config.enabled) {
        EditorText(
            stringResource(R.string.training_assistant_explanation),
            Color.argb(palette.muted),
            14,
            Modifier.padding(top = 8.dp),
            italic = true,
        )
        return
    }

    val assistantStatus = when (step.assistantState.status) {
        TrainingAssistantState.Status.CALIBRATING -> stringResource(
            R.string.training_status_calibrating,
            step.assistantState.eligibleObservations.coerceAtMost(3),
        )
        TrainingAssistantState.Status.ACTIVE -> stringResource(R.string.training_status_active)
        TrainingAssistantState.Status.PAUSED -> stringResource(R.string.training_status_paused)
        TrainingAssistantState.Status.DISABLED -> stringResource(R.string.training_assistant_off)
    }
    EditorText(
        assistantStatus,
        Color.argb(palette.ink2),
        14,
        Modifier.padding(top = 8.dp).testTag("task-editor:training-status:${step.id}"),
        serif = false,
    )

    Label(R.string.training_load_mode, palette, Modifier.padding(top = 18.dp, bottom = 8.dp))
    ChipFlow {
        trainingLoadModes.forEach { (label, mode) ->
            EditorChip(label, config.load.mode == mode, palette) {
                val load = if (mode == ResistanceLoad.Mode.BODYWEIGHT) {
                    ResistanceLoad.bodyweight()
                } else {
                    ResistanceLoad.numeric(
                        mode,
                        if (config.load.unit == ResistanceLoad.Unit.LB) ResistanceLoad.Unit.LB
                        else ResistanceLoad.Unit.KG,
                        config.load.milliUnits ?: 0,
                    )
                }
                updateTraining(state, step, index, config, load = load, dispatcher = dispatcher)
            }
        }
    }
    if (config.load.adjustable()) {
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            NumberInput(
                ((config.load.milliUnits ?: 0L) / 1000L).toInt(),
                R.string.training_load_value,
                palette,
                { value ->
                    val load = ResistanceLoad.numeric(
                        config.load.mode,
                        config.load.unit,
                        (value ?: 0).coerceAtLeast(0) * 1000L,
                    )
                    updateTraining(state, step, index, config, load = load, dispatcher = dispatcher)
                },
                Modifier.weight(1f),
                focusTag("training-load", step.id),
            )
            ChipFlow(Modifier.weight(1f)) {
                EditorChip(R.string.training_unit_kg, config.load.unit == ResistanceLoad.Unit.KG, palette) {
                    updateTraining(
                        state, step, index, config,
                        load = ResistanceLoad.numeric(config.load.mode, ResistanceLoad.Unit.KG, config.load.milliUnits ?: 0),
                        dispatcher = dispatcher,
                    )
                }
                EditorChip(R.string.training_unit_lb, config.load.unit == ResistanceLoad.Unit.LB, palette) {
                    updateTraining(
                        state, step, index, config,
                        load = ResistanceLoad.numeric(config.load.mode, ResistanceLoad.Unit.LB, config.load.milliUnits ?: 0),
                        dispatcher = dispatcher,
                    )
                }
            }
        }
        if (state.hasStepIssue(ValidationIssue.Field.TRAINING_LOAD, step.id)) {
            ErrorText(R.string.err_training_load_required, palette)
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        NumberInput(
            config.targetRir,
            R.string.training_target_rir,
            palette,
            { value -> updateTraining(state, step, index, config, targetRir = (value ?: 2).coerceIn(0, 5), dispatcher = dispatcher) },
            Modifier.weight(1f),
            focusTag("training-rir", step.id),
        )
        NumberInput(
            config.automaticWeeklySetCeiling,
            R.string.training_weekly_ceiling,
            palette,
            { value -> updateTraining(state, step, index, config, ceiling = (value ?: 10).coerceAtLeast(1), dispatcher = dispatcher) },
            Modifier.weight(1f),
            focusTag("training-ceiling", step.id),
        )
    }

    Label(R.string.training_primary_muscle, palette, Modifier.padding(top = 18.dp, bottom = 8.dp))
    ChipFlow {
        TrainingMuscleGroup.values().forEach { muscle ->
            EditorChip(trainingMuscleLabel(muscle), config.primaryMuscle == muscle, palette) {
                updateTraining(state, step, index, config, primary = muscle, dispatcher = dispatcher)
            }
        }
    }
    Label(R.string.training_secondary_muscles, palette, Modifier.padding(top = 14.dp, bottom = 8.dp))
    ChipFlow {
        TrainingMuscleGroup.values().filter { it != config.primaryMuscle }.forEach { muscle ->
            EditorChip(trainingMuscleLabel(muscle), config.secondaryMuscles.contains(muscle), palette) {
                val selected = config.secondaryMuscles.toMutableSet()
                if (!selected.add(muscle)) selected.remove(muscle)
                updateTraining(state, step, index, config, secondaries = selected, dispatcher = dispatcher)
            }
        }
    }
}

private fun updateTraining(
    state: EditorUiState,
    step: EditorStepState,
    index: Int,
    current: TrainingAssistantConfig,
    load: ResistanceLoad = current.load,
    targetRir: Int = current.targetRir,
    ceiling: Int = current.automaticWeeklySetCeiling,
    primary: TrainingMuscleGroup? = current.primaryMuscle,
    secondaries: Set<TrainingMuscleGroup> = current.secondaryMuscles,
    dispatcher: TaskEditorComposeDispatcher,
) = updateStep(
    state,
    index,
    step.withTrainingAssistant(
        TrainingAssistantConfig(
            true,
            current.minSets,
            current.maxSets,
            current.minRepetitions,
            current.maxRepetitions,
            targetRir,
            ceiling,
            load,
            primary,
            secondaries,
        ),
    ),
    dispatcher,
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

@Composable
private fun StepCadence(
    state: EditorUiState,
    step: EditorStepState,
    index: Int,
    palette: DayPalette,
    layout: EditorLayout,
    dispatcher: TaskEditorComposeDispatcher,
) {
    Label(R.string.editor_label_tage_frage, palette, Modifier.padding(top = 24.dp, bottom = 10.dp))
    ChipFlow {
        cadenceChoices.forEach { (label, cadence) ->
            EditorChip(label, step.cadenceMode == cadence, palette) {
                updateStep(state, index, step.withCadenceMode(cadence), dispatcher)
            }
        }
    }
    when (step.cadenceMode) {
        StepCadenceMode.WEEKDAYS -> WeekdayPicker(
            mask = step.weekdayMask,
            columns = layout.weekdayColumns,
            palette = palette,
            modifier = Modifier.padding(top = 14.dp),
        ) {
            if (it != 0) updateStep(state, index, step.withWeekdayMask(it), dispatcher)
        }
        StepCadenceMode.INTERVAL -> {
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EditorInput(
                    value = step.intervalDays?.toString().orEmpty(),
                    onValueChange = {
                        updateStep(state, index, step.withIntervalDays(it.toIntOrNull()), dispatcher)
                    },
                    palette = palette,
                    modifier = Modifier.width(96.dp),
                    number = true,
                    error = state.hasStepIssue(ValidationIssue.Field.STEP_INTERVAL, step.id),
                    tag = focusTag("interval", step.id),
                )
                Label(R.string.editor_interval_unit, palette, Modifier.padding(start = 12.dp))
            }
            if (state.hasStepIssue(ValidationIssue.Field.STEP_INTERVAL, step.id)) {
                ErrorText(R.string.err_interval_zero, palette)
            }
        }
        else -> Unit
    }
}

@Composable
private fun AmountInputs(
    state: EditorUiState,
    step: EditorStepState,
    index: Int,
    palette: DayPalette,
    dispatcher: TaskEditorComposeDispatcher,
) {
    when (val amount = step.amount) {
        is StepAmount.None -> Unit
        is StepAmount.SetsReps -> Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            NumberInput(
                amount.sets,
                R.string.amount_sets_unit,
                palette,
                { value ->
                    updateStep(
                        state,
                        index,
                        step.withAmount(StepAmount.setsReps(value ?: 0, amount.repetitions)),
                        dispatcher,
                    )
                },
                Modifier.weight(1f),
                focusTag("sets", step.id),
            )
            EditorText(
                stringResource(R.string.editor_multiply),
                Color.argb(palette.muted),
                22,
                Modifier.width(34.dp).padding(bottom = 17.dp),
            )
            NumberInput(
                amount.repetitions,
                R.string.amount_reps_unit,
                palette,
                { value ->
                    updateStep(
                        state,
                        index,
                        step.withAmount(StepAmount.setsReps(amount.sets, value ?: 0)),
                        dispatcher,
                    )
                },
                Modifier.weight(1f),
                focusTag("repetitions", step.id),
            )
        }
        is StepAmount.Repetitions -> NumberInput(
            amount.repetitions,
            R.string.amount_reps_unit,
            palette,
            { value -> updateStep(state, index, step.withAmount(StepAmount.repetitions(value ?: 0)), dispatcher) },
            Modifier.fillMaxWidth().padding(top = 12.dp),
            focusTag("repetitions", step.id),
        )
        is StepAmount.Duration -> DurationInputs(
            minutes = amount.seconds / 60,
            seconds = amount.seconds % 60,
            palette = palette,
            minuteTag = focusTag("duration-minutes", step.id),
            secondTag = focusTag("duration-seconds", step.id),
        ) { minutes, seconds ->
            updateStep(state, index, step.withAmount(StepAmount.duration(minutes * 60 + seconds)), dispatcher)
        }
    }
}

@Composable
private fun RestTimerInputs(
    state: EditorUiState,
    step: EditorStepState,
    index: Int,
    palette: DayPalette,
    dispatcher: TaskEditorComposeDispatcher,
) {
    Label(R.string.step_rest_timer_label, palette, Modifier.padding(top = 20.dp, bottom = 10.dp))
    ChipFlow {
        EditorChip(R.string.rest_timer_inherit, step.restTimerPolicy.mode == RestTimerPolicy.Mode.INHERIT, palette) {
            updateStep(state, index, step.withRestTimerPolicy(RestTimerPolicy.inherit()), dispatcher)
        }
        EditorChip(R.string.rest_timer_custom, step.restTimerPolicy.mode == RestTimerPolicy.Mode.CUSTOM, palette) {
            updateStep(state, index, step.withRestTimerPolicy(RestTimerPolicy.custom(60)), dispatcher)
        }
        EditorChip(R.string.rest_timer_off, step.restTimerPolicy.mode == RestTimerPolicy.Mode.OFF, palette) {
            updateStep(state, index, step.withRestTimerPolicy(RestTimerPolicy.off()), dispatcher)
        }
    }
    if (step.restTimerPolicy.mode == RestTimerPolicy.Mode.CUSTOM) {
        val total = step.restTimerPolicy.customSeconds ?: 60
        DurationInputs(
            minutes = total / 60,
            seconds = total % 60,
            palette = palette,
            minuteTag = focusTag("rest-minutes", step.id),
            secondTag = focusTag("rest-seconds", step.id),
        ) { minutes, seconds ->
            val value = (minutes * 60 + seconds).coerceAtLeast(1)
            updateStep(state, index, step.withRestTimerPolicy(RestTimerPolicy.custom(value)), dispatcher)
        }
    }
}

@Composable
private fun DurationInputs(
    minutes: Int,
    seconds: Int,
    palette: DayPalette,
    minuteTag: String,
    secondTag: String,
    onChange: (Int, Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        NumberInput(
            minutes,
            R.string.amount_minutes_unit,
            palette,
            { onChange((it ?: 0).coerceAtLeast(0), seconds) },
            Modifier.weight(1f),
            minuteTag,
        )
        NumberInput(
            seconds,
            R.string.amount_seconds_unit,
            palette,
            { onChange(minutes, (it ?: 0).coerceIn(0, 59)) },
            Modifier.weight(1f),
            secondTag,
        )
    }
}

private fun updateStep(
    state: EditorUiState,
    index: Int,
    step: EditorStepState,
    dispatcher: TaskEditorComposeDispatcher,
) = dispatcher.emit(TaskEditorStateReducer.updateStep(state, index, step))

private fun moveStep(
    state: EditorUiState,
    from: Int,
    to: Int,
    dispatcher: TaskEditorComposeDispatcher,
) {
    val bounded = to.coerceIn(0, state.stepStates.lastIndex)
    val next = TaskEditorStateReducer.moveStep(state, from, bounded)
    if (next !== state) dispatcher.emit(next)
}

private fun stepHasAnyIssue(state: EditorUiState, stepId: String): Boolean =
    state.hasStepIssue(ValidationIssue.Field.STEP_TITLE, stepId) ||
            state.hasStepIssue(ValidationIssue.Field.STEP_AMOUNT, stepId) ||
            state.hasStepIssue(ValidationIssue.Field.TRAINING_LOAD, stepId) ||
            state.hasStepIssue(ValidationIssue.Field.STEP_INTERVAL, stepId)

private fun EditorUiState.hasStepIssue(field: ValidationIssue.Field, stepId: String): Boolean =
    issues.contains(ValidationIssue.step(field, stepId))

private fun focusTag(field: String, stepId: String): String = "step:$stepId:$field"

private fun selectedAmount(kind: StepAmountKind, previous: StepAmount): StepAmount = when (kind) {
    StepAmountKind.SETS_REPS -> if (previous is StepAmount.SetsReps) previous else StepAmount.setsReps(3, 12)
    StepAmountKind.REPS -> if (previous is StepAmount.Repetitions) previous else StepAmount.repetitions(12)
    StepAmountKind.DURATION -> if (previous is StepAmount.Duration) previous else StepAmount.duration(45)
    StepAmountKind.NONE -> StepAmount.none()
}

private val amountChoices = listOf(
    R.string.amount_none to StepAmountKind.NONE,
    R.string.amount_sets_reps to StepAmountKind.SETS_REPS,
    R.string.amount_reps to StepAmountKind.REPS,
    R.string.amount_duration to StepAmountKind.DURATION,
)

private val trainingLoadModes = listOf(
    R.string.training_load_external to ResistanceLoad.Mode.EXTERNAL,
    R.string.training_load_bodyweight to ResistanceLoad.Mode.BODYWEIGHT,
    R.string.training_load_bodyweight_plus to ResistanceLoad.Mode.BODYWEIGHT_PLUS,
    R.string.training_load_assisted to ResistanceLoad.Mode.ASSISTED_BODYWEIGHT,
)

private val cadenceChoices = listOf(
    R.string.editor_tage_immer to StepCadenceMode.ALWAYS,
    R.string.editor_tage_feste to StepCadenceMode.WEEKDAYS,
    R.string.editor_tage_intervall to StepCadenceMode.INTERVAL,
)
