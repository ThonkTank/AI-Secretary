package de.thonktank.autosecretary.presentation.flowruns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.thonktank.autosecretary.DayPalette
import de.thonktank.autosecretary.R
import de.thonktank.autosecretary.domain.model.FlowRunSummary
import de.thonktank.autosecretary.domain.model.StepFlowRunState
import de.thonktank.autosecretary.presentation.mobile.MobileActionButton
import de.thonktank.autosecretary.presentation.mobile.MobileActionStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlowRunActions(
    run: FlowRunSummary,
    index: Int,
    runs: List<FlowRunSummary>,
    busy: Boolean,
    palette: DayPalette,
    callbacks: FlowRunsComposeCallbacks,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (run.state == StepFlowRunState.OFFERED) {
            FlowActionButton(
                label = stringResource(R.string.flow_run_defer),
                tag = FlowRunsSemantics.defer(run.id),
                palette = palette,
                busy = busy,
                onClick = { callbacks.onDefer(run) },
            )
            if (run.arrivalDelayMillis != null) {
                FlowActionButton(
                    label = stringResource(R.string.flow_run_not_ready),
                    tag = FlowRunsSemantics.postpone(run.id),
                    palette = palette,
                    busy = busy,
                    onClick = { callbacks.onPostpone(run) },
                )
            }
        }
        if (run.state == StepFlowRunState.WAITING_TIME) {
            FlowActionButton(
                label = stringResource(R.string.flow_run_ready_now),
                tag = FlowRunsSemantics.readyNow(run.id),
                palette = palette,
                busy = busy,
                prominent = true,
                onClick = { callbacks.onReadyNow(run) },
            )
            FlowActionButton(
                label = stringResource(R.string.flow_run_adjust_time),
                tag = FlowRunsSemantics.adjustTime(run.id),
                palette = palette,
                busy = busy,
                onClick = { callbacks.onAdjustTime(run) },
            )
        }
        if (index > 0) {
            FlowActionButton(
                label = stringResource(R.string.flow_run_move_up),
                tag = FlowRunsSemantics.moveUp(run.id),
                palette = palette,
                busy = busy,
                onClick = { callbacks.onMoveBefore(run.id, runs[index - 1].id) },
            )
        }
        if (index + 1 < runs.size) {
            FlowActionButton(
                label = stringResource(R.string.flow_run_move_down),
                tag = FlowRunsSemantics.moveDown(run.id),
                palette = palette,
                busy = busy,
                onClick = { callbacks.onMoveBefore(run.id, runs.getOrNull(index + 2)?.id) },
            )
        }
        FlowActionButton(
            label = stringResource(R.string.flow_run_cancel),
            tag = FlowRunsSemantics.cancel(run.id),
            palette = palette,
            busy = busy,
            destructive = true,
            onClick = { callbacks.onCancel(run) },
        )
    }
}

@Composable
private fun FlowActionButton(
    label: String,
    tag: String,
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
        modifier = Modifier.testTag(tag),
        style = when {
            destructive -> MobileActionStyle.DESTRUCTIVE
            prominent -> MobileActionStyle.PRIMARY
            else -> MobileActionStyle.SECONDARY
        },
        enabled = !busy,
        bold = prominent,
    )
}
