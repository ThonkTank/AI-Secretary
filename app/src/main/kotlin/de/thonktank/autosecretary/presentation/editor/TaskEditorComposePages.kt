package de.thonktank.autosecretary.presentation.editor

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.widget.EditText
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.thonktank.autosecretary.DayPalette
import de.thonktank.autosecretary.EditorUiState
import de.thonktank.autosecretary.R
import de.thonktank.autosecretary.ValidationIssue
import de.thonktank.autosecretary.domain.model.MissedOccurrenceMode
import de.thonktank.autosecretary.domain.model.Recurrence
import de.thonktank.autosecretary.domain.model.TaskBoundKind
import de.thonktank.autosecretary.domain.model.TimeOfDay
import de.thonktank.autosecretary.editor.TaskEditorStateReducer
import de.thonktank.autosecretary.presentation.TaskEditorTextFormatter
import java.time.LocalDate
import java.time.ZoneId

@Composable
internal fun EditorPageContent(
    state: EditorUiState,
    palette: DayPalette,
    today: LocalDate,
    layout: EditorLayout,
    formatter: TaskEditorTextFormatter,
    dispatcher: TaskEditorComposeDispatcher,
    modifier: Modifier,
) {
    Column(modifier) {
        when {
            state.expandedStepId != null -> EditorStepDetailPage(state, palette, layout, dispatcher)
            state.page == EditorUiState.Page.TITLE -> EditorTitlePage(state, palette, today, dispatcher)
            state.page == EditorUiState.Page.SCHEDULE -> EditorSchedulePage(state, palette, layout, dispatcher)
            state.page == EditorUiState.Page.STEPS -> EditorStepsPage(state, palette, layout, formatter, dispatcher)
            else -> EditorSummaryPage(state, palette, formatter, dispatcher)
        }
    }
}

@Composable
private fun EditorTitlePage(
    state: EditorUiState,
    palette: DayPalette,
    today: LocalDate,
    dispatcher: TaskEditorComposeDispatcher,
) {
    val titleError = state.hasIssue(ValidationIssue.Field.TITLE)
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(titleError) { if (titleError) titleFocus.requestFocus() }
    Question(R.string.editor_frage_titel, palette)
    EditorInput(
        value = state.title,
        onValueChange = { dispatcher.emit(TaskEditorStateReducer.updateTitle(state, it.take(120))) },
        palette = palette,
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(52.dp),
        hint = stringResource(R.string.field_title_hint),
        error = titleError,
        textSize = 25,
        serif = true,
        focusRequester = titleFocus,
        tag = "task:title",
    )
    if (titleError) {
        ErrorText(
            if (state.title.trim().isEmpty()) R.string.err_title_empty else R.string.err_title_long,
            palette,
        )
    }
    Label(R.string.field_note_label, palette, Modifier.padding(top = 26.dp, bottom = 4.dp))
    EditorInput(
        value = state.note,
        onValueChange = { dispatcher.emit(TaskEditorStateReducer.updateNote(state, it)) },
        palette = palette,
        modifier = Modifier.fillMaxWidth(),
        hint = stringResource(R.string.field_note_hint),
        multiline = true,
        tag = "task:note",
    )
    BoundOrDeadline(state, palette, today, dispatcher)
}

@Composable
private fun BoundOrDeadline(
    state: EditorUiState,
    palette: DayPalette,
    today: LocalDate,
    dispatcher: TaskEditorComposeDispatcher,
) {
    val context = LocalContext.current
    Label(
        if (state.recurrence == Recurrence.ONCE) R.string.editor_label_deadline
        else R.string.field_bound_label,
        palette,
        Modifier.padding(top = 26.dp, bottom = 10.dp),
    )
    ChipFlow {
        if (state.recurrence == Recurrence.ONCE) {
            EditorChip(R.string.deadline_none, state.deadlineOn == null, palette) {
                dispatcher.emit(TaskEditorStateReducer.updateDeadline(state, null))
            }
            EditorChip(R.string.deadline_today, state.deadlineOn == today, palette) {
                dispatcher.emit(TaskEditorStateReducer.updateDeadline(state, today))
            }
            EditorChip(R.string.deadline_tomorrow, state.deadlineOn == today.plusDays(1), palette) {
                dispatcher.emit(TaskEditorStateReducer.updateDeadline(state, today.plusDays(1)))
            }
            val custom = state.deadlineOn != null && state.deadlineOn != today &&
                    state.deadlineOn != today.plusDays(1)
            EditorChip(R.string.deadline_date, custom, palette) {
                pickDate(context, state.deadlineOn ?: today, today) {
                    dispatcher.emit(TaskEditorStateReducer.updateDeadline(state, it))
                }
            }
        } else {
            EditorChip(R.string.bound_forever, state.boundKind == TaskBoundKind.FOREVER, palette) {
                dispatcher.emit(TaskEditorStateReducer.updateBoundKind(state, TaskBoundKind.FOREVER, today))
            }
            EditorChip(R.string.bound_until, state.boundKind == TaskBoundKind.UNTIL_DATE, palette) {
                dispatcher.emit(TaskEditorStateReducer.updateBoundKind(state, TaskBoundKind.UNTIL_DATE, today))
            }
            EditorChip(R.string.bound_weeks, state.boundKind == TaskBoundKind.FOR_WEEKS, palette) {
                dispatcher.emit(TaskEditorStateReducer.updateBoundKind(state, TaskBoundKind.FOR_WEEKS, today))
            }
            EditorChip(R.string.bound_times, state.boundKind == TaskBoundKind.N_TIMES, palette) {
                dispatcher.emit(TaskEditorStateReducer.updateBoundKind(state, TaskBoundKind.N_TIMES, today))
            }
        }
    }
    when {
        state.recurrence == Recurrence.ONCE && state.deadlineOn != null -> ValueLeaf(
            stringResource(R.string.bound_until_value, state.deadlineOn.formatGerman()),
            palette,
        ) {
            pickDate(context, state.deadlineOn, today) {
                dispatcher.emit(TaskEditorStateReducer.updateDeadline(state, it))
            }
        }
        state.boundKind == TaskBoundKind.UNTIL_DATE && state.recurrence != Recurrence.ONCE -> {
            val value = state.boundUntilOn ?: today
            ValueLeaf(stringResource(R.string.bound_until_value, value.formatGerman()), palette) {
                pickDate(context, value, today) {
                    dispatcher.emit(TaskEditorStateReducer.updateBound(state, it, null, null))
                }
            }
        }
        state.boundKind == TaskBoundKind.FOR_WEEKS && state.recurrence != Recurrence.ONCE -> {
            val weeks = state.boundWeeks?.coerceAtLeast(1) ?: 1
            ValueLeaf(
                stringResource(R.string.bound_weeks_value, weeks, today.plusWeeks(weeks.toLong()).formatGerman()),
                palette,
            ) {
                pickNumber(context, weeks) {
                    dispatcher.emit(TaskEditorStateReducer.updateBound(state, today.plusWeeks(it.toLong()), it, null))
                }
            }
        }
        state.boundKind == TaskBoundKind.N_TIMES && state.recurrence != Recurrence.ONCE -> {
            val count = state.remainingCount?.coerceAtLeast(1) ?: 1
            ValueLeaf(stringResource(R.string.bound_times_value, count), palette) {
                pickNumber(context, count) {
                    dispatcher.emit(TaskEditorStateReducer.updateBound(state, null, null, it))
                }
            }
        }
    }
    if (state.hasIssue(ValidationIssue.Field.BOUND)) ErrorText(R.string.err_until_past, palette)
}

@Composable
private fun ValueLeaf(text: String, palette: DayPalette, onClick: () -> Unit) {
    val description = stringResource(R.string.a11y_editor_value_row, text, stringResource(R.string.editor_change))
    LeafSurface(
        palette = palette,
        level = 3,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        clickableLabel = description,
        onClick = onClick,
        padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            EditorText(text, Color.argb(palette.ink2), 17, Modifier.weight(1f))
            EditorText(stringResource(R.string.editor_change), Color.argb(palette.accent), 14, italic = true)
        }
    }
}

@Composable
private fun EditorSchedulePage(
    state: EditorUiState,
    palette: DayPalette,
    layout: EditorLayout,
    dispatcher: TaskEditorComposeDispatcher,
) {
    Question(R.string.editor_frage_rhythmus, palette)
    ChipFlow(Modifier.padding(top = 24.dp)) {
        recurrenceChoices.forEach { (label, value) ->
            EditorChip(label, state.recurrence == value, palette) {
                dispatcher.emit(TaskEditorStateReducer.updateRecurrence(state, value))
            }
        }
    }
    when (state.recurrence) {
        Recurrence.WEEKDAYS -> {
            WeekdayPicker(
                mask = state.weekdayMask,
                columns = layout.weekdayColumns,
                palette = palette,
                modifier = Modifier.padding(top = 14.dp),
            ) {
                dispatcher.emit(TaskEditorStateReducer.updateWeekdays(state, it))
            }
            if (state.hasIssue(ValidationIssue.Field.WEEKDAYS)) ErrorText(R.string.err_weekdays_empty, palette)
        }
        Recurrence.INTERVAL -> {
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EditorInput(
                    value = state.intervalDays.toString(),
                    onValueChange = {
                        dispatcher.emit(TaskEditorStateReducer.updateInterval(state, it.toIntOrNull() ?: 0))
                    },
                    palette = palette,
                    modifier = Modifier.width(96.dp),
                    number = true,
                    error = state.hasIssue(ValidationIssue.Field.INTERVAL),
                    tag = "task:interval",
                )
                Label(R.string.editor_interval_unit, palette, Modifier.padding(start = 12.dp))
            }
            if (state.hasIssue(ValidationIssue.Field.INTERVAL)) ErrorText(R.string.err_interval_zero, palette)
        }
        else -> Unit
    }
    if (state.recurrence != Recurrence.ONCE) {
        Label(R.string.field_timeofday_label, palette, Modifier.padding(top = 24.dp, bottom = 10.dp))
        ChipFlow {
            timeChoices.forEach { (label, value) ->
                EditorChip(label, state.timeOfDayMask and value.bit != 0, palette) {
                    dispatcher.emit(TaskEditorStateReducer.toggleTime(state, value))
                }
            }
        }
        if (state.hasIssue(ValidationIssue.Field.TIMES)) ErrorText(R.string.err_timeofday_empty, palette)
        Label(R.string.field_backlog_label, palette, Modifier.padding(top = 26.dp, bottom = 8.dp))
        ChipFlow {
            EditorChip(
                R.string.backlog_collapse,
                state.missedOccurrenceMode == MissedOccurrenceMode.COLLAPSE,
                palette,
            ) {
                dispatcher.emit(TaskEditorStateReducer.updateMissedOccurrenceMode(state, MissedOccurrenceMode.COLLAPSE))
            }
            EditorChip(
                R.string.backlog_accumulate,
                state.missedOccurrenceMode == MissedOccurrenceMode.ACCUMULATE,
                palette,
            ) {
                dispatcher.emit(TaskEditorStateReducer.updateMissedOccurrenceMode(state, MissedOccurrenceMode.ACCUMULATE))
            }
        }
        EditorText(
            stringResource(R.string.field_backlog_description),
            Color.argb(palette.muted),
            14,
            Modifier.padding(top = 7.dp),
            serif = false,
        )
    }
    DurationPicker(state, palette, dispatcher)
}

@Composable
private fun DurationPicker(
    state: EditorUiState,
    palette: DayPalette,
    dispatcher: TaskEditorComposeDispatcher,
) {
    Label(R.string.field_duration_label, palette, Modifier.padding(top = 24.dp, bottom = 10.dp))
    val fixed = listOf(15 to R.string.duration_15, 30 to R.string.duration_30,
        45 to R.string.duration_45, 60 to R.string.duration_60)
    val custom = state.estimatedMinutes != null && fixed.none { it.first == state.estimatedMinutes }
    val durationFocus = remember { FocusRequester() }
    val durationError = state.hasIssue(ValidationIssue.Field.DURATION)
    LaunchedEffect(durationError, custom) {
        if (durationError && custom) durationFocus.requestFocus()
    }
    ChipFlow {
        fixed.forEach { (minutes, label) ->
            EditorChip(label, state.estimatedMinutes == minutes, palette) {
                dispatcher.emit(TaskEditorStateReducer.updateDuration(state, minutes))
            }
        }
        EditorChip(R.string.duration_custom, custom, palette) {
            dispatcher.emit(TaskEditorStateReducer.updateDuration(state, state.estimatedMinutes ?: 20))
        }
    }
    if (custom) {
        NumberInput(
            value = state.estimatedMinutes,
            unit = R.string.duration_custom_unit,
            palette = palette,
            onValueChange = { dispatcher.emit(TaskEditorStateReducer.updateDuration(state, it)) },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            tag = "task:duration",
            focusRequester = durationFocus,
        )
    }
    if (durationError) ErrorText(R.string.err_duration_zero, palette)
}

@Composable
private fun EditorSummaryPage(
    state: EditorUiState,
    palette: DayPalette,
    formatter: TaskEditorTextFormatter,
    dispatcher: TaskEditorComposeDispatcher,
) {
    val titleDescription = stringResource(
        R.string.a11y_editor_summary_row,
        stringResource(R.string.field_title_label),
        state.title,
        stringResource(R.string.editor_change),
    )
    LeafSurface(
        palette = palette,
        modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp),
        rotation = -.7f,
        clickableLabel = titleDescription,
        onClick = { dispatcher.navigate(EditorUiState.Page.TITLE, true) },
        padding = PaddingValues(horizontal = 26.dp, vertical = 24.dp),
    ) {
        Column {
            EditorText(state.title, Color.argb(palette.ink), 30)
            EditorText(
                formatter.summaryLine(state),
                Color.argb(palette.muted),
                16,
                Modifier.padding(top = 8.dp),
                italic = true,
            )
        }
    }
    val rows = listOf(
        SummaryItem(R.string.field_rhythm_label, formatter.rhythm(state), EditorUiState.Page.SCHEDULE),
        SummaryItem(R.string.field_timeofday_label, formatter.time(state), EditorUiState.Page.SCHEDULE),
        SummaryItem(R.string.field_duration_label, formatter.duration(state), EditorUiState.Page.SCHEDULE),
        SummaryItem(
            if (state.recurrence == Recurrence.ONCE) R.string.editor_label_deadline else R.string.field_bound_label,
            formatter.bound(state),
            EditorUiState.Page.TITLE,
        ),
        SummaryItem(R.string.field_steps_label, formatter.steps(state), EditorUiState.Page.STEPS),
        SummaryItem(R.string.field_note_label, state.note.ifEmpty { formatter.empty() }, EditorUiState.Page.TITLE),
    )
    rows.forEachIndexed { index, item ->
        SummaryRow(item, index, palette) { dispatcher.navigate(item.page, true) }
    }
}

@Composable
private fun SummaryRow(item: SummaryItem, index: Int, palette: DayPalette, onClick: () -> Unit) {
    val label = stringResource(item.label)
    val description = stringResource(
        R.string.a11y_editor_summary_row,
        label,
        item.value,
        stringResource(R.string.editor_change),
    )
    val even = index % 2 == 0
    LeafSurface(
        palette = palette,
        level = if (index == 5) 3 else 2,
        modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp),
        rotation = floatArrayOf(1.1f, -1.5f, .8f, -.7f, 1.4f, -1f)[index],
        topStart = if (even) 8 else 56,
        topEnd = if (even) 56 else 8,
        bottomEnd = if (even) 8 else 56,
        bottomStart = if (even) 56 else 8,
        clickableLabel = description,
        onClick = onClick,
        padding = PaddingValues(start = 16.dp, top = 9.dp, end = 14.dp, bottom = 9.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                EditorText(label, Color.argb(palette.muted), 14, italic = true)
                EditorText(item.value, Color.argb(palette.ink2), 18, maxLines = 1)
            }
            EditorText(stringResource(R.string.editor_change), Color.argb(palette.accent), 14, italic = true)
        }
    }
}

@Composable
internal fun WeekdayPicker(
    mask: Int,
    columns: Int,
    palette: DayPalette,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit,
) {
    val labels = listOf(
        R.string.day_mon, R.string.day_tue, R.string.day_wed, R.string.day_thu,
        R.string.day_fri, R.string.day_sat, R.string.day_sun,
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { label ->
                    val index = labels.indexOf(label)
                    EditorChip(
                        label = label,
                        selected = mask and (1 shl index) != 0,
                        palette = palette,
                        onClick = { onChange(mask xor (1 shl index)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun EditorUiState.hasIssue(field: ValidationIssue.Field): Boolean =
    issues.contains(ValidationIssue.task(field))

private data class SummaryItem(
    @param:StringRes val label: Int,
    val value: String,
    val page: EditorUiState.Page,
)

private val recurrenceChoices = listOf(
    R.string.rhythm_once to Recurrence.ONCE,
    R.string.rhythm_daily to Recurrence.DAILY,
    R.string.rhythm_weekdays to Recurrence.WEEKDAYS,
    R.string.rhythm_every_n to Recurrence.INTERVAL,
)

private val timeChoices = listOf(
    R.string.tod_morning to TimeOfDay.MORNING,
    R.string.tod_noon to TimeOfDay.MIDDAY,
    R.string.tod_evening to TimeOfDay.EVENING,
    R.string.tod_night to TimeOfDay.NIGHT,
)

private fun LocalDate.formatGerman(): String = "%02d.%02d.".format(dayOfMonth, monthValue)

private fun pickDate(
    context: android.content.Context,
    initial: LocalDate,
    minimum: LocalDate,
    onSelected: (LocalDate) -> Unit,
) {
    val value = if (initial.isBefore(minimum)) minimum else initial
    DatePickerDialog(
        context,
        { _, year, month, day -> onSelected(LocalDate.of(year, month + 1, day)) },
        value.year,
        value.monthValue - 1,
        value.dayOfMonth,
    ).apply {
        datePicker.minDate = minimum.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.show()
}

private fun pickNumber(context: android.content.Context, initial: Int, onSelected: (Int) -> Unit) {
    val input = EditText(context).apply {
        inputType = android.text.InputType.TYPE_CLASS_NUMBER
        setText(initial.coerceAtLeast(1).toString())
        setSelectAllOnFocus(true)
    }
    AlertDialog.Builder(context)
        .setView(input)
        .setPositiveButton(R.string.step_apply) { _, _ ->
            onSelected(input.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1)
        }
        .setNegativeButton(R.string.ask_delete_keep, null)
        .show()
}
