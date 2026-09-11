package de.thonktank.autosecretary.presentation.flowruns

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.thonktank.autosecretary.DayPalette
import de.thonktank.autosecretary.R
import de.thonktank.autosecretary.domain.model.FlowRunSummary
import de.thonktank.autosecretary.presentation.mobile.MobileText
import de.thonktank.autosecretary.presentation.mobile.mobileColor
import de.thonktank.autosecretary.presentation.mobile.mobileLeaf

@Composable
internal fun FlowRunCard(
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
            .testTag(FlowRunsSemantics.card(run.id)),
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
        if (run.steps.isNotEmpty()) {
            run.steps.filter { it.state != de.thonktank.autosecretary.domain.model.FlowGraphRun.State.BLOCKED &&
                (it.state != de.thonktank.autosecretary.domain.model.FlowGraphRun.State.DONE || it.canAdjustWait) }.forEach { step ->
                val status = when (step.state) {
                    de.thonktank.autosecretary.domain.model.FlowGraphRun.State.AVAILABLE -> "Bereit"
                    de.thonktank.autosecretary.domain.model.FlowGraphRun.State.WAITING_TIME ->
                        de.thonktank.autosecretary.presentation.mobile.remainingDurationText(step.readyAtEpochMillis, nowEpochMillis)
                    de.thonktank.autosecretary.domain.model.FlowGraphRun.State.DONE -> "Wartezeit abgelaufen"
                    else -> "Wartet auf Kapazität"
                }
                MobileText("${step.title} · $status", mobileColor(palette.ink2), 16,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                if (step.canAdjustWait) {
                    val readyAt = step.readyAtEpochMillis ?: nowEpochMillis
                    de.thonktank.autosecretary.presentation.mobile.MobileActionButton(
                        "Wartezeit ändern", palette, { callbacks.onAdjustWait(run.id, step.waitId, readyAt) },
                        modifier = Modifier.testTag("flow-run:wait:${step.waitId}"), enabled = !busy)
                }
            }
            if (run.collectionAvailable) MobileText("Tau im Aufgabenblatt einsammeln", mobileColor(palette.accent), 16)
            val held = run.resources.filter { it.state.consumesCapacity() }
            if (held.isNotEmpty()) MobileText(held.joinToString(" · ") { "${it.units} × ${it.name}" },
                mobileColor(palette.muted), 14, modifier = Modifier.padding(top = 10.dp))
        } else {
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
                flowRunStatus(run, nowEpochMillis),
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
                flowRunResources(run),
                mobileColor(palette.muted),
                14,
                modifier = Modifier.padding(top = 10.dp),
                maxLines = 2,
            )
        }
        FlowRunActions(run, index, runs, busy, palette, callbacks)
        }
    }
}
