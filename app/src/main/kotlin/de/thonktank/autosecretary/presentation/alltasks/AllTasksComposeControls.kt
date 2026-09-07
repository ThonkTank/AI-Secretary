package de.thonktank.autosecretary.presentation.alltasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.thonktank.autosecretary.DayPalette
import de.thonktank.autosecretary.R
import de.thonktank.autosecretary.domain.model.Recurrence
import de.thonktank.autosecretary.domain.model.TaskSlot
import java.util.EnumSet

@Composable
internal fun AllTasksComposeControls(
    state: AllTasksUiState,
    palette: DayPalette,
    callbacks: AllTasksComposeCallbacks,
    onOpenFilters: () -> Unit,
) {
    if (state.mode == AllTasksUiState.Mode.LIST) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AllTasksControlButton(
                label = filterButtonLabel(state),
                palette = palette,
                onClick = onOpenFilters,
                modifier = Modifier.weight(1f).testTag("all-tasks:filters-button"),
            )
            AllTasksControlButton(
                label = stringResource(R.string.all_order_action),
                palette = palette,
                onClick = { callbacks.onMode(AllTasksUiState.Mode.SORT) },
                modifier = Modifier.weight(1f).testTag("all-tasks:mode"),
                prominent = true,
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AllTasksText(
                stringResource(R.string.all_order_action),
                color(palette.ink),
                24,
                Modifier.weight(1f).semantics { heading() },
                serif = true,
            )
            AllTasksActionText(
                stringResource(R.string.all_done),
                palette,
                { callbacks.onMode(AllTasksUiState.Mode.LIST) },
                Modifier.testTag("all-tasks:mode"),
                underline = true,
            )
        }
        AllTasksText(
            stringResource(R.string.all_order_help),
            color(palette.hint),
            15,
            Modifier.fillMaxWidth().padding(top = 2.dp),
            maxLines = 2,
        )
        AllTasksControlButton(
            label = filterButtonLabel(state),
            palette = palette,
            onClick = onOpenFilters,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                .testTag("all-tasks:filters-button"),
        )
    }

    AllTasksText(
        resultLabel(state),
        color(palette.muted),
        14,
        Modifier.fillMaxWidth().padding(start = 2.dp, top = 10.dp),
        maxLines = 2,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AllTasksComposeFilterSheet(
    state: AllTasksUiState,
    palette: DayPalette,
    callbacks: AllTasksComposeCallbacks,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 620.dp)
            .leaf(
                palette = palette,
                level = 1,
                shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            )
            .testTag("all-tasks:filter-sheet")
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 30.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AllTasksText(
                    stringResource(R.string.all_filter_sheet_title),
                    color(palette.ink),
                    25,
                    serif = true,
                )
                AllTasksText(
                    stringResource(R.string.all_filter_sheet_hint),
                    color(palette.hint),
                    14,
                    Modifier.padding(top = 2.dp),
                )
            }
            AllTasksActionText(
                stringResource(R.string.all_done),
                palette,
                onClose,
                Modifier.testTag("all-tasks:filter-sheet:done"),
                underline = true,
            )
        }

        if (state.mode == AllTasksUiState.Mode.LIST) {
            FilterSectionTitle(stringResource(R.string.all_filter_status), palette)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    AllTasksUiState.Status.ACTIVE to R.string.all_status_active,
                    AllTasksUiState.Status.ARCHIVED to R.string.all_status_archived,
                    AllTasksUiState.Status.ALL to R.string.all_status_all,
                ).forEach { (value, label) ->
                    AllTasksChoiceChip(
                        stringResource(label), palette, state.status == value,
                        { callbacks.onStatus(value) },
                        Modifier.testTag("all-tasks:filter:status:${value.name}"),
                    )
                }
            }
        }

        FilterSectionTitle(stringResource(R.string.all_filter_time), palette)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TaskSlot.values().forEach { slot ->
                AllTasksChoiceChip(
                    slotLabel(slot), palette, state.slots.contains(slot),
                    {
                        val selected = state.slots.mutableEnumCopy(TaskSlot::class.java)
                        if (!selected.add(slot)) selected.remove(slot)
                        callbacks.onSlots(selected)
                    },
                    Modifier.testTag("all-tasks:filter:slot:${slot.name}"),
                )
            }
        }

        FilterSectionTitle(stringResource(R.string.all_filter_rhythm), palette)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            recurrenceValues.forEach { recurrence ->
                AllTasksChoiceChip(
                    recurrenceLabel(recurrence), palette,
                    state.recurrences.contains(recurrence),
                    {
                        val selected = state.recurrences.mutableEnumCopy(Recurrence::class.java)
                        if (!selected.add(recurrence)) selected.remove(recurrence)
                        callbacks.onRecurrences(selected)
                    },
                    Modifier.testTag("all-tasks:filter:rhythm:${recurrence.name}"),
                )
            }
        }

        if (state.mode == AllTasksUiState.Mode.SORT) {
            FilterSectionTitle(stringResource(R.string.all_filter_day), palette)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AllTasksChoiceChip(
                    stringResource(R.string.all_every_day), palette, state.weekday == 0,
                    { callbacks.onWeekday(0) },
                    Modifier.testTag("all-tasks:filter:weekday:0"),
                )
                weekdayResources.forEachIndexed { index, resource ->
                    AllTasksChoiceChip(
                        stringResource(resource), palette, state.weekday == index + 1,
                        { callbacks.onWeekday(index + 1) },
                        Modifier.testTag("all-tasks:filter:weekday:${index + 1}"),
                    )
                }
            }
        }

        if (activeFilterCount(state) > 0) {
            Spacer(Modifier.height(12.dp))
            AllTasksActionText(
                stringResource(R.string.all_filter_reset),
                palette,
                callbacks.onResetFilters,
                Modifier.fillMaxWidth().testTag("all-tasks:filter:reset"),
                underline = true,
                minHeight = 48.dp,
            )
        }
    }
}

@Composable
private fun FilterSectionTitle(label: String, palette: DayPalette) {
    AllTasksText(
        label,
        color(palette.ink2),
        17,
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp)
            .semantics { heading() },
        serif = true,
        italic = true,
    )
}

@Composable
private fun resultLabel(state: AllTasksUiState): String {
    val resources = LocalResources.current
    if (state.mode == AllTasksUiState.Mode.SORT) {
        val matched = state.schedule.size
        if (matched == 0) return ""
        return if (hasQueryOrFilters(state)) {
            resources.getString(R.string.all_schedule_result_filtered, matched, state.schedulePoolSize)
        } else resources.getQuantityString(R.plurals.all_schedule_result, matched, matched)
    }
    val matched = state.tasks.size
    if (matched == 0) return ""
    return if (hasQueryOrFilters(state)) {
        resources.getString(R.string.all_task_result_filtered, matched, state.taskPoolSize)
    } else resources.getString(
        R.string.all_task_result,
        matched,
        state.tasks.sumOf { it.steps.size },
    )
}

@Composable
private fun filterButtonLabel(state: AllTasksUiState): String = buildString {
    append(stringResource(R.string.all_filter_action))
    val count = activeFilterCount(state)
    if (count > 0) append(" · ").append(count)
}

@Composable
internal fun slotLabel(slot: TaskSlot): String = stringResource(
    when (slot) {
        TaskSlot.MORNING -> R.string.slot_morning
        TaskSlot.MIDDAY -> R.string.slot_midday
        TaskSlot.EVENING -> R.string.slot_evening
        TaskSlot.LATER -> R.string.slot_later
    },
)

@Composable
internal fun recurrenceLabel(value: Recurrence): String = stringResource(
    when (value) {
        Recurrence.ONCE -> R.string.rhythm_once
        Recurrence.DAILY -> R.string.rhythm_daily
        Recurrence.INTERVAL -> R.string.rhythm_every_n
        Recurrence.WEEKDAYS -> R.string.rhythm_weekdays
    },
)

private fun activeFilterCount(state: AllTasksUiState): Int =
    state.slots.size + state.recurrences.size +
        if (state.mode == AllTasksUiState.Mode.LIST &&
            state.status != AllTasksUiState.Status.ACTIVE
        ) 1 else if (state.mode == AllTasksUiState.Mode.SORT && state.weekday != 0) 1 else 0

private fun hasQueryOrFilters(state: AllTasksUiState): Boolean =
    state.query.trim().isNotEmpty() || activeFilterCount(state) > 0

private fun <E : Enum<E>> Set<E>.mutableEnumCopy(type: Class<E>): EnumSet<E> =
    if (isEmpty()) EnumSet.noneOf(type) else EnumSet.copyOf(this)

private val recurrenceValues = listOf(
    Recurrence.ONCE,
    Recurrence.DAILY,
    Recurrence.INTERVAL,
    Recurrence.WEEKDAYS,
)

private val weekdayResources = intArrayOf(
    R.string.day_mon,
    R.string.day_tue,
    R.string.day_wed,
    R.string.day_thu,
    R.string.day_fri,
    R.string.day_sat,
    R.string.day_sun,
)
