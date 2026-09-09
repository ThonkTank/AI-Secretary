package de.thonktank.autosecretary.presentation.flowruns

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.thonktank.autosecretary.DayPalette
import de.thonktank.autosecretary.FlowRunsScreenState
import de.thonktank.autosecretary.R
import de.thonktank.autosecretary.domain.model.FlowResourceState
import de.thonktank.autosecretary.domain.model.FlowRunSummary
import de.thonktank.autosecretary.domain.model.StepFlowRunState
import de.thonktank.autosecretary.presentation.mobile.MobileActionButton
import de.thonktank.autosecretary.presentation.mobile.MobileActionStyle
import de.thonktank.autosecretary.presentation.mobile.MobileText
import de.thonktank.autosecretary.presentation.mobile.mobileColor
import de.thonktank.autosecretary.presentation.mobile.mobileLeaf
import de.thonktank.autosecretary.presentation.mobile.remainingDurationText

@Composable
internal fun FlowRunsComposeScreen(
    state: FlowRunsScreenState,
    palette: DayPalette,
    callbacks: FlowRunsComposeCallbacks,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(mobileColor(palette.background))
            .testTag("flow-runs:screen"),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            FlowRunsHeader(palette, callbacks::onBack)
        }
        item(key = "intro") {
            MobileText(
                stringResource(R.string.flow_runs_description),
                mobileColor(palette.ink2),
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
                    nowEpochMillis = state.nowEpochMillis,
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
                .mobileLeaf(palette, RoundedCornerShape(24.dp), level = 1)
                .semantics { contentDescription = label; role = Role.Button }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack,
                ),
            contentAlignment = Alignment.Center,
        ) {
            MobileText("‹", mobileColor(palette.ink), 32, serif = true)
        }
        MobileText(
            stringResource(R.string.flow_runs_title),
            mobileColor(palette.ink),
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
            .mobileLeaf(
                palette,
                shape,
                level = 3,
                edgeColor = if (error) palette.bad else null,
            )
            .padding(horizontal = 18.dp, vertical = 18.dp)
            .testTag(tag),
    ) {
        MobileText(text, mobileColor(if (error) palette.bad else palette.hint), 17)
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
    nowEpochMillis: Long,
    callbacks: FlowRunsComposeCallbacks,
) {
    val shape = RoundedCornerShape(42.dp, 12.dp, 42.dp, 12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .mobileLeaf(palette, shape, level = 2)
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .testTag("flow-runs:card:${run.id}"),
    ) {
        MobileText(run.seedTitle, mobileColor(palette.ink), 23, serif = true, maxLines = 2)
        MobileText(
            run.taskTitle,
            mobileColor(palette.muted),
            14,
            modifier = Modifier.padding(top = 1.dp),
            maxLines = 1,
        )
        Spacer(Modifier.height(11.dp))
        MobileText(
            run.currentStepTitle,
            mobileColor(palette.ink2),
            18,
            bold = true,
            maxLines = 2,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MobileText(
                stringResource(
                    R.string.flow_step_number,
                    run.currentPosition + 1,
                    run.totalSteps,
                ),
                mobileColor(palette.hint),
                14,
            )
            MobileText(
                flowStatus(run, nowEpochMillis),
                mobileColor(palette.status),
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
                .background(mobileColor(palette.leaf1Edge), progressShape),
        ) {
            Box(
                Modifier
                    .fillMaxWidth((run.currentPosition + 1f) / run.totalSteps.toFloat())
                    .height(5.dp)
                    .background(mobileColor(palette.accent), progressShape),
            )
        }
        if (run.resources.isNotEmpty()) {
            MobileText(
                flowResources(run),
                mobileColor(palette.muted),
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
    MobileActionButton(
        label = label,
        palette = palette,
        onClick = onClick,
        style = when {
            destructive -> MobileActionStyle.DESTRUCTIVE
            prominent -> MobileActionStyle.PRIMARY
            else -> MobileActionStyle.SECONDARY
        },
        enabled = !busy,
        bold = prominent,
    )
}

@Composable
private fun flowStatus(run: FlowRunSummary, nowEpochMillis: Long): String = when (run.state) {
    StepFlowRunState.OFFERED -> stringResource(R.string.flow_status_ready_short)
    StepFlowRunState.WAITING_TIME -> stringResource(
        R.string.flow_status_waiting_time_short,
        remainingDurationText(run.readyAtEpochMillis, nowEpochMillis),
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
