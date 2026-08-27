package de.thonktank.autosecretary.presentation.alltasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.thonktank.autosecretary.DayPalette
import de.thonktank.autosecretary.R
import de.thonktank.autosecretary.domain.model.Recurrence
import de.thonktank.autosecretary.domain.model.TaskSlot
import java.util.EnumSet

internal enum class AllTasksFilterMenu { STATUS, SLOTS, RHYTHMS, WEEKDAY }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AllTasksComposeControls(
    state: AllTasksUiState,
    palette: DayPalette,
    callbacks: AllTasksComposeCallbacks,
    onOpenMenu: (AllTasksFilterMenu) -> Unit,
    onCloseMenu: () -> Unit,
) {
    AllTasksSearch(
        query = state.query,
        palette = palette,
        onQuery = callbacks.onQuery,
        modifier = Modifier.fillMaxWidth().testTag("all-tasks:search"),
    )
    if (state.filtersExpanded) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.mode == AllTasksUiState.Mode.LIST) {
                AllTasksChip(
                    statusLabel(state), palette,
                    state.status != AllTasksUiState.Status.ACTIVE,
                    { onOpenMenu(AllTasksFilterMenu.STATUS) },
                    Modifier.testTag("all-tasks:filter:status"),
                )
            }
            AllTasksChip(
                multiLabel(stringResource(R.string.all_filter_time), slotLabels(state)),
                palette,
                state.slots.isNotEmpty(),
                { onOpenMenu(AllTasksFilterMenu.SLOTS) },
                Modifier.testTag("all-tasks:filter:slots"),
            )
            AllTasksChip(
                multiLabel(stringResource(R.string.all_filter_rhythm), recurrenceLabels(state)),
                palette,
                state.recurrences.isNotEmpty(),
                { onOpenMenu(AllTasksFilterMenu.RHYTHMS) },
                Modifier.testTag("all-tasks:filter:rhythms"),
            )
            if (state.mode == AllTasksUiState.Mode.SORT) {
                AllTasksChip(
                    weekdayLabel(state.weekday), palette, state.weekday != 0,
                    { onOpenMenu(AllTasksFilterMenu.WEEKDAY) },
                    Modifier.testTag("all-tasks:filter:weekday"),
                )
            }
            if (activeFilterCount(state) > 0) {
                AllTasksActionText(
                    stringResource(R.string.all_filter_reset),
                    palette,
                    {
                        onCloseMenu()
                        callbacks.onResetFilters()
                    },
                    underline = true,
                )
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AllTasksText(
            resultLabel(state),
            color(palette.muted),
            14,
            Modifier.weight(1f).padding(start = 2.dp),
            maxLines = 2,
        )
        AllTasksActionText(
            stringResource(
                if (state.mode == AllTasksUiState.Mode.LIST) R.string.all_sort_mode
                else R.string.all_tasks_mode,
            ),
            palette,
            {
                onCloseMenu()
                callbacks.onMode(
                    if (state.mode == AllTasksUiState.Mode.LIST) AllTasksUiState.Mode.SORT
                    else AllTasksUiState.Mode.LIST,
                )
            },
            Modifier.testTag("all-tasks:mode"),
        )
        val filters = buildString {
            append(stringResource(R.string.all_filter_toggle))
            if (!state.filtersExpanded && activeFilterCount(state) > 0) {
                append(" · ")
                append(activeFilterCount(state))
            }
            append(if (state.filtersExpanded) " ⌃" else " ⌄")
        }
        AllTasksActionText(
            filters,
            palette,
            {
                onCloseMenu()
                callbacks.onFiltersExpanded(!state.filtersExpanded)
            },
            Modifier.testTag("all-tasks:filters-toggle"),
        )
    }
}

@Composable
internal fun AllTasksComposeFilterDropdown(
    state: AllTasksUiState,
    palette: DayPalette,
    menu: AllTasksFilterMenu,
    callbacks: AllTasksComposeCallbacks,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .leaf(
                palette = palette,
                level = 1,
                shape = leafShape(8.dp, 24.dp, 8.dp, 24.dp),
            )
            .padding(vertical = 6.dp)
            .testTag("all-tasks:dropdown:${menu.name.lowercase()}"),
    ) {
        when (menu) {
            AllTasksFilterMenu.STATUS -> {
                MenuItem(stringResource(R.string.all_status_active),
                    state.status == AllTasksUiState.Status.ACTIVE, palette) {
                    onClose(); callbacks.onStatus(AllTasksUiState.Status.ACTIVE)
                }
                MenuItem(stringResource(R.string.all_status_archived),
                    state.status == AllTasksUiState.Status.ARCHIVED, palette) {
                    onClose(); callbacks.onStatus(AllTasksUiState.Status.ARCHIVED)
                }
                MenuItem(stringResource(R.string.all_status_all),
                    state.status == AllTasksUiState.Status.ALL, palette) {
                    onClose(); callbacks.onStatus(AllTasksUiState.Status.ALL)
                }
            }
            AllTasksFilterMenu.SLOTS -> TaskSlot.values().forEach { slot ->
                MenuItem(slotLabel(slot), state.slots.contains(slot), palette) {
                    val selected = if (state.slots.isEmpty()) EnumSet.noneOf(TaskSlot::class.java)
                    else EnumSet.copyOf(state.slots)
                    if (!selected.add(slot)) selected.remove(slot)
                    callbacks.onSlots(selected)
                }
            }
            AllTasksFilterMenu.RHYTHMS -> listOf(
                Recurrence.ONCE,
                Recurrence.DAILY,
                Recurrence.INTERVAL,
                Recurrence.WEEKDAYS,
            ).forEach { recurrence ->
                MenuItem(
                    recurrenceLabel(recurrence),
                    state.recurrences.contains(recurrence),
                    palette,
                ) {
                    val selected = if (state.recurrences.isEmpty()) {
                        EnumSet.noneOf(Recurrence::class.java)
                    } else EnumSet.copyOf(state.recurrences)
                    if (!selected.add(recurrence)) selected.remove(recurrence)
                    callbacks.onRecurrences(selected)
                }
            }
            AllTasksFilterMenu.WEEKDAY -> {
                MenuItem(stringResource(R.string.all_every_day), state.weekday == 0, palette) {
                    onClose(); callbacks.onWeekday(0)
                }
                weekdayResources.forEachIndexed { index, resource ->
                    MenuItem(stringResource(resource), state.weekday == index + 1, palette) {
                        onClose(); callbacks.onWeekday(index + 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(
    label: String,
    selected: Boolean,
    palette: DayPalette,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(if (selected) color(palette.accent) else Color.Transparent)
            .semantics { this.selected = selected; role = Role.Button }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        AllTasksText(
            label,
            color(if (selected) palette.accentText else palette.ink),
            17,
            bold = selected,
        )
    }
}

@Composable
private fun resultLabel(state: AllTasksUiState): String {
    val resources = LocalResources.current
    if (state.mode == AllTasksUiState.Mode.SORT) {
        val matched = state.schedule.size
        if (matched == 0) return ""
        return if (hasQueryOrFilters(state)) {
            resources.getString(
                R.string.all_schedule_result_filtered,
                matched,
                state.schedulePoolSize,
            )
        } else {
            resources.getQuantityString(R.plurals.all_schedule_result, matched, matched)
        }
    }
    val matched = state.tasks.size
    if (matched == 0) return ""
    return if (hasQueryOrFilters(state)) {
        resources.getString(R.string.all_task_result_filtered, matched, state.taskPoolSize)
    } else {
        resources.getString(
            R.string.all_task_result,
            matched,
            state.tasks.sumOf { it.steps.size },
        )
    }
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

@Composable
private fun statusLabel(state: AllTasksUiState): String {
    val value = stringResource(
        when (state.status) {
            AllTasksUiState.Status.ACTIVE -> R.string.all_status_active
            AllTasksUiState.Status.ARCHIVED -> R.string.all_status_archived
            AllTasksUiState.Status.ALL -> R.string.all_status_all
        },
    )
    return stringResource(R.string.all_filter_value, stringResource(R.string.all_filter_status), value)
}

@Composable
private fun weekdayLabel(weekday: Int): String {
    if (weekday == 0) return stringResource(R.string.all_filter_day)
    return stringResource(
        R.string.all_filter_value,
        stringResource(R.string.all_filter_day),
        stringResource(weekdayResources[weekday - 1]),
    )
}

@Composable
private fun slotLabels(state: AllTasksUiState): List<String> =
    TaskSlot.values().filter(state.slots::contains).map { slotLabel(it) }

@Composable
private fun recurrenceLabels(state: AllTasksUiState): List<String> =
    listOf(Recurrence.ONCE, Recurrence.DAILY, Recurrence.INTERVAL, Recurrence.WEEKDAYS)
        .filter(state.recurrences::contains).map { recurrenceLabel(it) }

private fun multiLabel(base: String, selected: List<String>): String = when (selected.size) {
    0 -> base
    1 -> "$base: ${selected.first()}"
    else -> "$base: ${selected.size} gewählt"
}

private fun activeFilterCount(state: AllTasksUiState): Int =
    state.slots.size + state.recurrences.size +
        if (state.mode == AllTasksUiState.Mode.LIST &&
            state.status != AllTasksUiState.Status.ACTIVE) 1
        else if (state.mode == AllTasksUiState.Mode.SORT && state.weekday != 0) 1 else 0

private fun hasQueryOrFilters(state: AllTasksUiState): Boolean =
    state.query.trim().isNotEmpty() || activeFilterCount(state) > 0

private val weekdayResources = intArrayOf(
    R.string.day_mon,
    R.string.day_tue,
    R.string.day_wed,
    R.string.day_thu,
    R.string.day_fri,
    R.string.day_sat,
    R.string.day_sun,
)
