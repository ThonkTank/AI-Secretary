package de.thonktank.autosecretary.presentation.flowruns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.thonktank.autosecretary.DayPalette
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
internal fun FlowStatusPanel(text: String, palette: DayPalette, tag: String) {
    val shape = RoundedCornerShape(30.dp, 10.dp, 30.dp, 10.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .mobileLeaf(palette, shape, level = 3)
            .padding(horizontal = 18.dp, vertical = 18.dp)
            .testTag(tag),
    ) {
        MobileText(text, mobileColor(palette.hint), 17)
    }
}

@Composable
internal fun FlowErrorPanel(
    text: String,
    errorId: Long,
    palette: DayPalette,
    onDismiss: (Long) -> Unit,
) {
    val shape = RoundedCornerShape(30.dp, 10.dp, 30.dp, 10.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .mobileLeaf(palette, shape, level = 3, edgeColor = palette.bad)
            .padding(horizontal = 18.dp, vertical = 18.dp)
            .testTag(FlowRunsSemantics.ERROR),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MobileText(text, mobileColor(palette.bad), 17)
        MobileActionButton(
            label = stringResource(R.string.flow_error_dismiss),
            palette = palette,
            onClick = { onDismiss(errorId) },
            modifier = Modifier.testTag(FlowRunsSemantics.ERROR_DISMISS),
            style = MobileActionStyle.DESTRUCTIVE,
        )
    }
}

@Composable
internal fun flowRunStatus(run: FlowRunSummary, nowEpochMillis: Long): String = when (run.state) {
    StepFlowRunState.OFFERED -> stringResource(R.string.flow_status_ready_short)
    StepFlowRunState.WAITING_TIME -> stringResource(
        R.string.flow_status_waiting_time_short,
        remainingDurationText(run.readyAtEpochMillis, nowEpochMillis),
    )
    else -> stringResource(R.string.flow_status_waiting_capacity_short)
}

@Composable
internal fun flowRunResources(run: FlowRunSummary): String {
    val resources = LocalContext.current.resources
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
