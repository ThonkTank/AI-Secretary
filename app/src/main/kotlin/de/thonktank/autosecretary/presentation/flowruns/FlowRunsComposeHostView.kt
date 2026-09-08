package de.thonktank.autosecretary.presentation.flowruns

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import de.thonktank.autosecretary.DayPalette
import de.thonktank.autosecretary.FlowRunsScreenState
import de.thonktank.autosecretary.domain.model.FlowRunSummary
import java.time.LocalTime

/** Java-friendly interaction boundary between the stateless screen and its Activity host. */
interface FlowRunsComposeCallbacks {
    fun onBack()
    fun onDefer(run: FlowRunSummary)
    fun onPostpone(run: FlowRunSummary)
    fun onReadyNow(run: FlowRunSummary)
    fun onAdjustTime(run: FlowRunSummary)
    fun onMoveBefore(runId: String, beforeRunId: String?)
    fun onCancel(run: FlowRunSummary)
}

/** Compose host that mirrors only values published by the existing FlowRunsViewModel. */
class FlowRunsComposeHostView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AbstractComposeView(context, attrs) {
    private var state by mutableStateOf(FlowRunsScreenState.idle())
    private var palette by mutableStateOf(DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO))
    private var callbacks: FlowRunsComposeCallbacks? = null

    init {
        setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool,
        )
    }

    fun bind(
        state: FlowRunsScreenState,
        palette: DayPalette,
        callbacks: FlowRunsComposeCallbacks,
    ) {
        this.state = state
        this.palette = palette
        this.callbacks = callbacks
    }

    fun dispose() {
        callbacks = null
        disposeComposition()
    }

    @Composable
    override fun Content() {
        FlowRunsComposeScreen(
            state = state,
            palette = palette,
            callbacks = callbacks ?: NoopFlowRunsCallbacks,
        )
    }
}

private object NoopFlowRunsCallbacks : FlowRunsComposeCallbacks {
    override fun onBack() = Unit
    override fun onDefer(run: FlowRunSummary) = Unit
    override fun onPostpone(run: FlowRunSummary) = Unit
    override fun onReadyNow(run: FlowRunSummary) = Unit
    override fun onAdjustTime(run: FlowRunSummary) = Unit
    override fun onMoveBefore(runId: String, beforeRunId: String?) = Unit
    override fun onCancel(run: FlowRunSummary) = Unit
}
