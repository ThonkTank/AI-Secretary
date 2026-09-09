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
    private val actionCounts = mutableMapOf<String, Int>()

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

    fun actionCount(action: String): Int = actionCounts[action] ?: 0

    override fun onBack() { record("back") }
    override fun onDefer(run: FlowRunSummary) { record("defer:${run.id}") }
    override fun onPostpone(run: FlowRunSummary) { record("postpone:${run.id}") }
    override fun onReadyNow(run: FlowRunSummary) { record("ready:${run.id}") }
    override fun onAdjustTime(run: FlowRunSummary) { record("adjust:${run.id}") }
    override fun onMoveBefore(runId: String, beforeRunId: String?) {
        record("move:$runId:$beforeRunId")
    }
    override fun onCancel(run: FlowRunSummary) { record("cancel:${run.id}") }
    override fun onDismissError(errorId: Long) { record("dismiss-error:$errorId") }

    private fun bind() = flowRuns.bind(state, palette, this)

    private fun record(action: String) {
        lastAction = action
        actionCounts[action] = actionCount(action) + 1
    }
}
