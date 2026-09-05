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
import de.thonktank.autosecretary.domain.model.StepAmount
import de.thonktank.autosecretary.domain.model.StepAmountKind
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
    trainingHistory: TrainingHistoryUiModel?,
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
            EditorChip(label, step.prescription.amount.kind() == kind, palette) {
                updateStep(state, index, step.withAmount(selectedAmount(kind, step.prescription.amount)), dispatcher)
            }
        }
    }
    AmountInputs(state, step, index, palette, dispatcher)
    if (step.prescription.amount is StepAmount.SetsReps) {
        TrainingAssistantEditorSection(
            prescription = step.prescription,
            policy = step.assistantPolicy,
            assistantState = step.assistantState,
            hasLoadIssue = state.hasStepIssue(ValidationIssue.Field.TRAINING_LOAD, step.id),
            stepId = step.id,
            palette = palette,
        ) { prescription, policy ->
            updateStep(state, index, step.withTraining(prescription, policy), dispatcher)
        }
        TrainingHistorySection(
            history = trainingHistory,
            dirty = state.dirty,
            stepId = step.id,
            palette = palette,
            onUndo = { dispatcher.undoTrainingAdjustment(step.id) },
        )
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
    when (val amount = step.prescription.amount) {
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
        EditorChip(R.string.rest_timer_inherit, step.prescription.rest.mode == RestTimerPolicy.Mode.INHERIT, palette) {
            updateStep(state, index, step.withRestTimerPolicy(RestTimerPolicy.inherit()), dispatcher)
        }
        EditorChip(R.string.rest_timer_custom, step.prescription.rest.mode == RestTimerPolicy.Mode.CUSTOM, palette) {
            updateStep(state, index, step.withRestTimerPolicy(RestTimerPolicy.custom(60)), dispatcher)
        }
        EditorChip(R.string.rest_timer_off, step.prescription.rest.mode == RestTimerPolicy.Mode.OFF, palette) {
            updateStep(state, index, step.withRestTimerPolicy(RestTimerPolicy.off()), dispatcher)
        }
    }
    if (step.prescription.rest.mode == RestTimerPolicy.Mode.CUSTOM) {
        val total = step.prescription.rest.customSeconds ?: 60
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


private val cadenceChoices = listOf(
    R.string.editor_tage_immer to StepCadenceMode.ALWAYS,
    R.string.editor_tage_feste to StepCadenceMode.WEEKDAYS,
    R.string.editor_tage_intervall to StepCadenceMode.INTERVAL,
)
