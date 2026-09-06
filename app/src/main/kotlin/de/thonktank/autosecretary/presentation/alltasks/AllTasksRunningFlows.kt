package de.thonktank.autosecretary.presentation.alltasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.thonktank.autosecretary.DayPalette
import de.thonktank.autosecretary.R
import de.thonktank.autosecretary.domain.model.FlowRunSummary
import de.thonktank.autosecretary.domain.model.StepFlowRunState

@Composable
internal fun AllTasksRunningFlows(
    runs: List<FlowRunSummary>,
    palette: DayPalette,
    onOpen: () -> Unit,
) {
    if (runs.isEmpty()) return
    val resources = LocalResources.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .leaf(palette, level = 3)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onOpen,
            )
            .padding(horizontal = 18.dp, vertical = 15.dp)
            .testTag("all-tasks:running-flows"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AllTasksText(
                resources.getQuantityString(R.plurals.flow_runs_heading, runs.size, runs.size),
                color(palette.ink),
                20,
                Modifier.weight(1f),
                serif = true,
                bold = true,
            )
            AllTasksText(runs.size.toString(), color(palette.accent), 16, bold = true)
        }
        runs.take(3).forEach { run ->
            Row(
                Modifier.fillMaxWidth().padding(top = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AllTasksText(
                    run.seedTitle,
                    color(palette.ink2),
                    16,
                    Modifier.weight(.9f),
                    bold = true,
                )
                Spacer(Modifier.width(12.dp))
                AllTasksText(
                    flowStatus(run, resources),
                    color(palette.hint),
                    14,
                    Modifier.weight(1.1f),
                    maxLines = 2,
                )
            }
        }
        if (runs.size > 3) {
            AllTasksText(
                resources.getQuantityString(
                    R.plurals.flow_runs_more,
                    runs.size - 3,
                    runs.size - 3,
                ),
                color(palette.muted),
                14,
                Modifier.padding(top = 8.dp),
            )
        }
        AllTasksText(
            resources.getString(R.string.flow_runs_manage),
            color(palette.accent),
            15,
            Modifier.padding(top = 10.dp),
            bold = true,
        )
    }
}

private fun flowStatus(run: FlowRunSummary, resources: android.content.res.Resources): String =
    when (run.state) {
        StepFlowRunState.OFFERED -> resources.getString(
            R.string.all_flow_ready,
            run.currentStepTitle,
        )
        StepFlowRunState.WAITING_TIME -> resources.getString(
            R.string.all_flow_waiting_time,
            run.currentStepTitle,
            remaining(run.readyAtEpochMillis),
        )
        else -> resources.getString(R.string.all_flow_waiting_capacity, run.currentStepTitle)
    }

private fun remaining(readyAt: Long?): String {
    if (readyAt == null) return ""
    val minutes = ((readyAt - System.currentTimeMillis()).coerceAtLeast(0L) + 59_999L) / 60_000L
    if (minutes < 60L) return "$minutes min"
    val hours = minutes / 60L
    val restMinutes = minutes % 60L
    if (hours < 24L) return if (restMinutes == 0L) "$hours h" else "$hours h $restMinutes min"
    val days = hours / 24L
    val restHours = hours % 24L
    return if (restHours == 0L) "$days d" else "$days d $restHours h"
}
