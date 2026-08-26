package de.thonktank.autosecretary.presentation.alltasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.thonktank.autosecretary.DayPalette
import de.thonktank.autosecretary.R
import java.time.format.DateTimeFormatter

@Composable
internal fun AllTasksComposeRow(
    row: AllTasksRow,
    state: AllTasksUiState,
    palette: DayPalette,
    dispatcher: AllTasksComposeDispatcher,
    callbacks: AllTasksComposeCallbacks,
    dragActive: Boolean,
    selectedSwapStep: String?,
    onSelectSwap: (String?) -> Unit,
    onOpenTaskMenu: (String) -> Unit,
) {
    val modifier = Modifier
        .fillMaxWidth()
        .testTag("all-tasks:row:${row.key}")
    when (row.kind) {
        AllTasksRow.Kind.TASK_HEADER -> TaskHeaderRow(
            row, palette, callbacks, onOpenTaskMenu, modifier,
        )
        AllTasksRow.Kind.STEP_TARGET -> StepTargetRow(row, palette, dragActive, modifier)
        AllTasksRow.Kind.STEP -> StepRow(
            row,
            palette,
            dispatcher,
            callbacks,
            selectedSwapStep,
            onSelectSwap,
            modifier,
        )
        AllTasksRow.Kind.STEP_ADD -> AddStepRow(row, palette, callbacks, modifier)
        AllTasksRow.Kind.SLOT_HEADER -> SlotHeaderRow(row, palette, modifier)
        AllTasksRow.Kind.SCHEDULE_TARGET -> ScheduleTargetRow(row, palette, modifier)
        AllTasksRow.Kind.SCHEDULE -> ScheduleRow(row, palette, dispatcher, modifier)
        AllTasksRow.Kind.EMPTY -> EmptyRow(row, state, palette, modifier)
    }
}

@Composable
private fun TaskHeaderRow(
    row: AllTasksRow,
    palette: DayPalette,
    callbacks: AllTasksComposeCallbacks,
    onOpenTaskMenu: (String) -> Unit,
    modifier: Modifier,
) {
    val item = row.task
    val resources = LocalResources.current
    val meta = taskMeta(item)
    val shape = if (item.expanded) leafShape(42.dp, 8.dp, 0.dp, 0.dp) else leafShape()
    Column(
        modifier = modifier
            .leaf(palette, shape)
            .semantics {
                contentDescription = resources.getString(
                    R.string.a11y_task_row,
                    item.task.title,
                    meta,
                )
            }
            .padding(start = 18.dp, top = 14.dp, end = 8.dp,
                bottom = if (item.expanded) 0.dp else 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AllTasksText(item.task.title, color(palette.ink), 22, serif = true)
                AllTasksText(
                    meta,
                    color(palette.hint),
                    14,
                    Modifier.padding(top = 3.dp),
                    maxLines = 1,
                )
            }
            AllTasksActionText(
                "⋮",
                palette,
                { onOpenTaskMenu(item.task.id.value) },
                Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = resources.getString(
                            R.string.a11y_task_menu,
                            item.task.title,
                        )
                    },
                minHeight = 48.dp,
            )
        }
        val steps = stepLine(item)
        Box(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = item.steps.isNotEmpty(),
                    role = Role.Button,
                    onClick = { callbacks.onToggleTask(item.cardKey) },
                    onLongClick = null,
                )
                .semantics {
                    if (item.steps.isNotEmpty()) {
                        contentDescription = resources.getString(
                            if (item.expanded) R.string.a11y_collapse_task
                            else R.string.a11y_expand_task,
                        )
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            AllTasksText(steps, color(palette.ink2), 15)
        }
    }
}

@Composable
private fun StepTargetRow(
    row: AllTasksRow,
    palette: DayPalette,
    dragActive: Boolean,
    modifier: Modifier,
) {
    if (!dragActive) {
        Spacer(modifier.height(0.dp))
        return
    }
    val description = stringResource(R.string.a11y_step_drop_target)
    Box(
        modifier = modifier
            .height(44.dp)
            .leaf(palette, leafShape(0.dp, 0.dp, 0.dp, 0.dp))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(2.dp)
            .background(color(palette.light)))
    }
}

@Composable
private fun StepRow(
    row: AllTasksRow,
    palette: DayPalette,
    dispatcher: AllTasksComposeDispatcher,
    callbacks: AllTasksComposeCallbacks,
    selectedSwapStep: String?,
    onSelectSwap: (String?) -> Unit,
    modifier: Modifier,
) {
    val resources = LocalResources.current
    val actions = mutableListOf<CustomAccessibilityAction>()
    fun action(label: Int, block: () -> Boolean) {
        actions += CustomAccessibilityAction(resources.getString(label), block)
    }
    if (!row.task.archived) {
        if (dispatcher.canMoveStepBy(row, -1)) {
            action(R.string.a11y_step_up) { dispatcher.moveStepBy(row, -1) }
        }
        if (dispatcher.canMoveStepBy(row, 1)) {
            action(R.string.a11y_step_down) { dispatcher.moveStepBy(row, 1) }
        }
        if (dispatcher.canMoveStepToTask(row, -1)) {
            action(R.string.a11y_step_previous_task) { dispatcher.moveStepToTask(row, -1) }
        }
        if (dispatcher.canMoveStepToTask(row, 1)) {
            action(R.string.a11y_step_next_task) { dispatcher.moveStepToTask(row, 1) }
        }
        action(R.string.a11y_step_select_swap) {
            onSelectSwap(row.step.id)
            true
        }
        if (selectedSwapStep != null && selectedSwapStep != row.step.id) {
            action(R.string.a11y_step_swap_selected) {
                callbacks.onSwapSteps(selectedSwapStep, row.step.id)
                onSelectSwap(null)
                true
            }
        }
    }
    Box(
        modifier = modifier
            .leaf(palette, leafShape(0.dp, 0.dp, 0.dp, 0.dp))
            .padding(start = 8.dp, end = 8.dp, bottom = 4.dp)
            .semantics {
                val selected = selectedSwapStep == row.step.id
                contentDescription = buildString {
                    append(resources.getString(R.string.a11y_step_row, row.step.text))
                    if (selected) {
                        append(' ')
                        append(resources.getString(R.string.a11y_step_selected))
                    }
                }
                if (selected) liveRegion = LiveRegionMode.Polite
                customActions = actions
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .background(color(palette.leaf1).copy(alpha = .72f), RoundedCornerShape(18.dp))
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = { callbacks.onEditStep(row.taskId, row.step.id) },
                    onLongClick = { },
                )
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!row.task.archived) {
                AllTasksText("☰", color(palette.dot), 20, Modifier.width(44.dp))
            }
            AllTasksText(row.step.text, color(palette.ink), 17, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AddStepRow(
    row: AllTasksRow,
    palette: DayPalette,
    callbacks: AllTasksComposeCallbacks,
    modifier: Modifier,
) {
    val resources = LocalResources.current
    Box(
        modifier = modifier
            .leaf(palette, leafShape(0.dp, 0.dp, 42.dp, 8.dp))
            .heightIn(min = 44.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = { callbacks.onAddStep(row.taskId) },
                onLongClick = null,
            )
            .semantics {
                contentDescription = resources.getString(R.string.a11y_add_step_target)
            }
            .padding(start = 20.dp, end = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        AllTasksText("＋ ${stringResource(R.string.all_add_step)}", color(palette.ink2), 14)
    }
}

@Composable
private fun SlotHeaderRow(row: AllTasksRow, palette: DayPalette, modifier: Modifier) {
    AllTasksText(
        slotLabel(row.slot),
        color(palette.muted),
        17,
        modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp).semantics { heading() },
        serif = true,
        italic = true,
    )
}

@Composable
private fun ScheduleTargetRow(row: AllTasksRow, palette: DayPalette, modifier: Modifier) {
    val resources = LocalResources.current
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics {
                contentDescription = resources.getString(
                    R.string.a11y_schedule_drop_target,
                    slotLabelPlain(resources, row.slot),
                )
            }
            .padding(start = 56.dp, end = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        AllTasksText(
            stringResource(R.string.all_schedule_insert_target),
            color(palette.muted),
            14,
        )
    }
}

@Composable
private fun ScheduleRow(
    row: AllTasksRow,
    palette: DayPalette,
    dispatcher: AllTasksComposeDispatcher,
    modifier: Modifier,
) {
    val resources = LocalResources.current
    val actions = mutableListOf<CustomAccessibilityAction>()
    if (dispatcher.canMoveScheduleBy(row, -1)) {
        actions += CustomAccessibilityAction(resources.getString(R.string.a11y_schedule_up)) {
            dispatcher.moveScheduleBy(row, -1)
        }
    }
    if (dispatcher.canMoveScheduleBy(row, 1)) {
        actions += CustomAccessibilityAction(resources.getString(R.string.a11y_schedule_down)) {
            dispatcher.moveScheduleBy(row, 1)
        }
    }
    if (dispatcher.canMoveScheduleToSlot(row, -1)) {
        actions += CustomAccessibilityAction(
            resources.getString(R.string.a11y_schedule_previous_slot),
        ) {
            dispatcher.moveScheduleToSlot(row, -1)
        }
    }
    if (dispatcher.canMoveScheduleToSlot(row, 1)) {
        actions += CustomAccessibilityAction(
            resources.getString(R.string.a11y_schedule_next_slot),
        ) {
            dispatcher.moveScheduleToSlot(row, 1)
        }
    }
    Row(
        modifier = modifier
            .leaf(palette, leafShape(36.dp, 8.dp, 36.dp, 8.dp))
            .defaultMinSize(minHeight = 56.dp)
            .semantics {
                contentDescription = resources.getString(
                    R.string.a11y_schedule_row,
                    row.schedule.title,
                    slotLabelPlain(resources, row.slot),
                    recurrenceLabelPlain(resources, row.schedule.recurrence),
                )
                customActions = actions
            }
            .padding(start = 8.dp, top = 4.dp, end = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AllTasksText("☰", color(palette.dot), 20, Modifier.width(48.dp))
        Column(Modifier.weight(1f)) {
            AllTasksText(row.schedule.title, color(palette.ink), 20, serif = true)
            AllTasksText(recurrenceLabel(row.schedule.recurrence), color(palette.hint), 14)
        }
    }
}

@Composable
private fun EmptyRow(
    row: AllTasksRow,
    state: AllTasksUiState,
    palette: DayPalette,
    modifier: Modifier,
) {
    val title: Int
    val subtitle: Int
    when (row.emptyReason) {
        AllTasksRow.EmptyReason.SEARCH -> {
            title = R.string.all_empty_search_title
            subtitle = R.string.all_empty_search_subtitle
        }
        AllTasksRow.EmptyReason.FILTERS -> {
            title = R.string.all_empty_filter_title
            subtitle = if (state.mode == AllTasksUiState.Mode.SORT) {
                R.string.all_empty_filter_sort_subtitle
            } else R.string.all_empty_filter_subtitle
        }
        else -> {
            title = R.string.all_empty_status_title
            subtitle = R.string.all_empty_status_subtitle
        }
    }
    val shape = leafShape(10.dp, 64.dp, 10.dp, 64.dp)
    Column(
        modifier = modifier
            .border(1.dp, color(palette.dot), shape)
            .padding(horizontal = 22.dp, vertical = 28.dp),
    ) {
        AllTasksText(stringResource(title), color(palette.ink), 25, serif = true)
        AllTasksText(
            stringResource(subtitle),
            color(palette.hint),
            16,
            Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
internal fun AllTasksComposeTaskMenu(
    item: AllTasksUiState.TaskItem,
    palette: DayPalette,
    callbacks: AllTasksComposeCallbacks,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.leaf(palette, level = 1, shape = leafShape(8.dp, 24.dp, 8.dp, 24.dp))) {
        AllTasksActionText(
            stringResource(R.string.task_edit), palette,
            { onClose(); callbacks.onEditTask(item.task.id.value) },
            Modifier.fillMaxWidth(), minHeight = 48.dp,
        )
        AllTasksActionText(
            stringResource(R.string.task_delete), palette,
            { onClose(); callbacks.onDeleteTask(item.task.id.value, item.task.title) },
            Modifier.fillMaxWidth(), minHeight = 48.dp,
        )
    }
}

@Composable
private fun taskMeta(item: AllTasksUiState.TaskItem): String {
    val resources = LocalResources.current
    val timing = item.task.nextDueOn?.let {
        resources.getString(
            R.string.all_next_due,
            it.format(DateTimeFormatter.ofPattern("dd.MM.")),
        )
    } ?: resources.getString(R.string.all_no_due)
    val base = "${slotLabel(item.slot)} · ${recurrenceLabel(item.task.recurrence)} · $timing"
    return if (item.archived) "$base · ${stringResource(R.string.all_archived)}" else base
}

@Composable
private fun stepLine(item: AllTasksUiState.TaskItem): String {
    val count = item.steps.size
    if (count == 0) return stringResource(R.string.all_no_steps)
    if (item.searchExpanded) {
        return stringResource(R.string.all_steps_matching, item.matchingSteps.size, count) + " ⌃"
    }
    val label = if (count == 1) stringResource(R.string.all_step_count)
    else stringResource(R.string.all_steps_count, count)
    return label + if (item.expanded) " ⌃" else " ⌄"
}

private fun slotLabelPlain(
    resources: android.content.res.Resources,
    slot: de.thonktank.autosecretary.domain.model.TaskSlot,
): String = resources.getString(
        when (slot) {
            de.thonktank.autosecretary.domain.model.TaskSlot.MORNING -> R.string.slot_morning
            de.thonktank.autosecretary.domain.model.TaskSlot.MIDDAY -> R.string.slot_midday
            de.thonktank.autosecretary.domain.model.TaskSlot.EVENING -> R.string.slot_evening
            de.thonktank.autosecretary.domain.model.TaskSlot.LATER -> R.string.slot_later
        },
    )

private fun recurrenceLabelPlain(
    resources: android.content.res.Resources,
    value: de.thonktank.autosecretary.domain.model.Recurrence,
): String = resources.getString(
        when (value) {
            de.thonktank.autosecretary.domain.model.Recurrence.ONCE -> R.string.rhythm_once
            de.thonktank.autosecretary.domain.model.Recurrence.DAILY -> R.string.rhythm_daily
            de.thonktank.autosecretary.domain.model.Recurrence.INTERVAL -> R.string.rhythm_every_n
            de.thonktank.autosecretary.domain.model.Recurrence.WEEKDAYS -> R.string.rhythm_weekdays
        },
    )
