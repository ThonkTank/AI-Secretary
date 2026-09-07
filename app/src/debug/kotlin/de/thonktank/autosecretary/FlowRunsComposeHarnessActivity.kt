package de.thonktank.autosecretary

import android.os.Bundle
import androidx.activity.ComponentActivity
import de.thonktank.autosecretary.domain.model.FlowRunSummary
import de.thonktank.autosecretary.presentation.flowruns.FlowRunsComposeCallbacks
import de.thonktank.autosecretary.presentation.flowruns.FlowRunsComposeFixture
import de.thonktank.autosecretary.presentation.flowruns.FlowRunsComposeHostView
import java.time.LocalTime

/** Debug-only authoritative loop for flow-run screen interaction verification. */
class FlowRunsComposeHarnessActivity : ComponentActivity(), FlowRunsComposeCallbacks {
    lateinit var flowRuns: FlowRunsComposeHostView
        private set
    var state: FlowRunsScreenState = FlowRunsComposeFixture.state()
        private set
    var palette: DayPalette = DayPalette.at(LocalTime.of(9, 40), DayPalette.Mode.LIGHT)
        private set
    var lastAction: String? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        flowRuns = FlowRunsComposeHostView(this)
        setContentView(flowRuns)
        bind()
    }

    fun render(value: FlowRunsScreenState, palette: DayPalette = this.palette) {
        state = value
        this.palette = palette
        bind()
    }

    override fun onBack() { lastAction = "back" }
    override fun onDefer(run: FlowRunSummary) { lastAction = "defer:${run.id}" }
    override fun onPostpone(run: FlowRunSummary) { lastAction = "postpone:${run.id}" }
    override fun onReadyNow(run: FlowRunSummary) { lastAction = "ready:${run.id}" }
    override fun onAdjustTime(run: FlowRunSummary) { lastAction = "adjust:${run.id}" }
    override fun onMoveBefore(runId: String, beforeRunId: String?) {
        lastAction = "move:$runId:$beforeRunId"
    }
    override fun onCancel(run: FlowRunSummary) { lastAction = "cancel:${run.id}" }

    private fun bind() = flowRuns.bind(state, palette, this)
}
