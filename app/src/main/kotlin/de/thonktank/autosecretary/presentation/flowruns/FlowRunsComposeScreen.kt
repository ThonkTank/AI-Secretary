package de.thonktank.autosecretary.presentation.flowruns

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.thonktank.autosecretary.DayPalette
import de.thonktank.autosecretary.FlowRunsScreenState
import de.thonktank.autosecretary.R
import de.thonktank.autosecretary.domain.model.FlowResourceState
import de.thonktank.autosecretary.domain.model.FlowRunSummary
import de.thonktank.autosecretary.domain.model.StepFlowRunState

private val FlowRunsSerif = FontFamily(
    Font(R.font.newsreader, FontWeight.Normal),
    Font(R.font.newsreader_italic, FontWeight.Normal, FontStyle.Italic),
)
private val FlowRunsSans = FontFamily(
    Font(R.font.alegreya_sans, FontWeight.Normal),
    Font(R.font.alegreya_sans_bold, FontWeight.Bold),
)

@Composable
internal fun FlowRunsComposeScreen(
    state: FlowRunsScreenState,
    palette: DayPalette,
    callbacks: FlowRunsComposeCallbacks,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(flowColor(palette.background))
            .testTag("flow-runs:screen"),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            FlowRunsHeader(palette, callbacks::onBack)
        }
        item(key = "intro") {
            FlowText(
                stringResource(R.string.flow_runs_description),
                flowColor(palette.ink2),
                16,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (state.changing) {
            item(key = "changing") {
                FlowStatusPanel(
                    text = stringResource(R.string.flow_changing),
                    palette = palette,
                    tag = "flow-runs:changing",
                )
            }
        }
        if (state.loading) {
            item(key = "loading") {
                FlowStatusPanel(
                    text = stringResource(R.string.flow_loading),
                    palette = palette,
                    tag = "flow-runs:loading",
                )
            }
        }
        if (state.errorMessage != null) {
            item(key = "error") {
                FlowStatusPanel(
                    text = state.errorMessage,
                    palette = palette,
                    tag = "flow-runs:error",
                    error = true,
                )
            }
        }
        if (!state.loading && state.errorMessage == null && state.runs.isEmpty()) {
            item(key = "empty") {
                FlowStatusPanel(
                    text = stringResource(R.string.flow_runs_empty),
                    palette = palette,
                    tag = "flow-runs:empty",
                )
            }
        } else {
            itemsIndexed(state.runs, key = { _, run -> run.id }) { index, run ->
                FlowRunCard(
                    run = run,
                    index = index,
                    runs = state.runs,
                    busy = state.changing,
                    palette = palette,
                    callbacks = callbacks,
                )
            }
        }
    }
}

@Composable
private fun FlowRunsHeader(palette: DayPalette, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val label = stringResource(R.string.flow_back)
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(flowColor(palette.leaf1), RoundedCornerShape(24.dp))
                .border(1.dp, flowColor(palette.leaf1Edge), RoundedCornerShape(24.dp))
                .semantics { contentDescription = label; role = Role.Button }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack,
                ),
            contentAlignment = Alignment.Center,
        ) {
            FlowText("‹", flowColor(palette.ink), 32, serif = true)
        }
        FlowText(
            stringResource(R.string.flow_runs_title),
            flowColor(palette.ink),
            29,
            modifier = Modifier.weight(1f),
            serif = true,
        )
    }
}

@Composable
private fun FlowStatusPanel(
    text: String,
    palette: DayPalette,
    tag: String,
    error: Boolean = false,
) {
    val shape = RoundedCornerShape(30.dp, 10.dp, 30.dp, 10.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(flowColor(palette.leaf3), shape)
            .border(1.dp, flowColor(if (error) palette.bad else palette.leaf3Edge), shape)
            .padding(horizontal = 18.dp, vertical = 18.dp)
            .testTag(tag),
    ) {
        FlowText(text, flowColor(if (error) palette.bad else palette.hint), 17)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRunCard(
    run: FlowRunSummary,
    index: Int,
    runs: List<FlowRunSummary>,
    busy: Boolean,
    palette: DayPalette,
    callbacks: FlowRunsComposeCallbacks,
) {
    val shape = RoundedCornerShape(42.dp, 12.dp, 42.dp, 12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(flowColor(palette.leaf2), shape)
            .border(1.dp, flowColor(palette.leaf2Edge), shape)
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .testTag("flow-runs:card:${run.id}"),
    ) {
        FlowText(run.seedTitle, flowColor(palette.ink), 23, serif = true, maxLines = 2)
        FlowText(
            run.taskTitle,
            flowColor(palette.muted),
            14,
            modifier = Modifier.padding(top = 1.dp),
            maxLines = 1,
        )
        Spacer(Modifier.height(11.dp))
        FlowText(run.currentStepTitle, flowColor(palette.ink2), 18, bold = true, maxLines = 2)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlowText(
                stringResource(
                    R.string.flow_step_number,
                    run.currentPosition + 1,
                    run.totalSteps,
                ),
                flowColor(palette.hint),
                14,
            )
            FlowText(
                flowStatus(run),
                flowColor(palette.status),
                14,
                bold = true,
                maxLines = 1,
            )
        }
        val progressShape = RoundedCornerShape(3.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 7.dp)
                .height(5.dp)
                .background(flowColor(palette.leaf1Edge), progressShape),
        ) {
            Box(
                Modifier
                    .fillMaxWidth((run.currentPosition + 1f) / run.totalSteps.toFloat())
                    .height(5.dp)
                    .background(flowColor(palette.accent), progressShape),
            )
        }
        if (run.resources.isNotEmpty()) {
            FlowText(
                flowResources(run),
                flowColor(palette.muted),
                14,
                modifier = Modifier.padding(top = 10.dp),
                maxLines = 2,
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (run.state == StepFlowRunState.OFFERED) {
                FlowActionButton(
                    stringResource(R.string.flow_run_defer), palette, busy,
                    onClick = { callbacks.onDefer(run) },
                )
                if (run.arrivalDelayMillis != null) {
                    FlowActionButton(
                        stringResource(R.string.flow_run_not_ready), palette, busy,
                        onClick = { callbacks.onPostpone(run) },
                    )
                }
            }
            if (run.state == StepFlowRunState.WAITING_TIME) {
                FlowActionButton(
                    stringResource(R.string.flow_run_ready_now), palette, busy, prominent = true,
                    onClick = { callbacks.onReadyNow(run) },
                )
                FlowActionButton(
                    stringResource(R.string.flow_run_adjust_time), palette, busy,
                    onClick = { callbacks.onAdjustTime(run) },
                )
            }
            if (index > 0) {
                FlowActionButton(
                    stringResource(R.string.flow_run_move_up), palette, busy,
                    onClick = { callbacks.onMoveBefore(run.id, runs[index - 1].id) },
                )
            }
            if (index + 1 < runs.size) {
                FlowActionButton(
                    stringResource(R.string.flow_run_move_down), palette, busy,
                    onClick = { callbacks.onMoveBefore(run.id, runs.getOrNull(index + 2)?.id) },
                )
            }
            FlowActionButton(
                stringResource(R.string.flow_run_cancel), palette, busy, destructive = true,
                onClick = { callbacks.onCancel(run) },
            )
        }
    }
}

@Composable
private fun FlowActionButton(
    label: String,
    palette: DayPalette,
    busy: Boolean,
    prominent: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val fill = if (prominent) flowColor(palette.accent) else flowColor(palette.leaf1)
    val edge = when {
        destructive -> flowColor(palette.bad)
        prominent -> flowColor(palette.accent)
        else -> flowColor(palette.leaf1Edge)
    }
    val foreground = when {
        destructive -> flowColor(palette.bad)
        prominent -> flowColor(palette.accentText)
        else -> flowColor(palette.ink2)
    }
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 44.dp)
            .background(fill, shape)
            .border(BorderStroke(1.dp, edge), shape)
            .alpha(if (busy) .5f else 1f)
            .semantics {
                contentDescription = label
                role = Role.Button
                if (busy) disabled()
            }
            .clickable(
                enabled = !busy,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        FlowText(label, foreground, 15, bold = prominent, maxLines = 1)
    }
}

@Composable
private fun flowStatus(run: FlowRunSummary): String = when (run.state) {
    StepFlowRunState.OFFERED -> stringResource(R.string.flow_status_ready_short)
    StepFlowRunState.WAITING_TIME -> stringResource(
        R.string.flow_status_waiting_time_short,
        remainingFlowTime(run.readyAtEpochMillis),
    )
    else -> stringResource(R.string.flow_status_waiting_capacity_short)
}

@Composable
private fun flowResources(run: FlowRunSummary): String {
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    return run.resources.joinToString(" · ") { resource ->
        val state = when (resource.state) {
            FlowResourceState.ACTIVE -> resources.getString(R.string.flow_resource_active)
            FlowResourceState.RESERVED -> resources.getString(R.string.flow_resource_reserved)
            FlowResourceState.RELEASED -> resources.getString(R.string.flow_resource_released)
            else -> resources.getString(R.string.flow_resource_planned)
        }
        resources.getString(R.string.flow_resource_summary, resource.name, resource.units, state)
    }
}

internal fun remainingFlowTime(readyAtEpochMillis: Long?, now: Long = System.currentTimeMillis()): String {
    if (readyAtEpochMillis == null) return ""
    val minutes = (kotlin.math.max(0L, readyAtEpochMillis - now) + 59_999L) / 60_000L
    if (minutes < 60L) return "$minutes min"
    val hours = minutes / 60L
    val restMinutes = minutes % 60L
    if (hours < 24L) return if (restMinutes == 0L) "$hours h" else "$hours h $restMinutes min"
    val days = hours / 24L
    val restHours = hours % 24L
    return if (restHours == 0L) "$days d" else "$days d $restHours h"
}

@Composable
private fun FlowText(
    text: String,
    color: Color,
    size: Int,
    modifier: Modifier = Modifier,
    serif: Boolean = false,
    bold: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontFamily = if (serif) FlowRunsSerif else FlowRunsSans,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            letterSpacing = if (serif && size >= 28) (-.5).sp else 0.sp,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun flowColor(value: Int): Color = Color(value)
