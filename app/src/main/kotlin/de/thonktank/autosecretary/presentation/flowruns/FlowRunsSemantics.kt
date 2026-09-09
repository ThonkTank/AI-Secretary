package de.thonktank.autosecretary.presentation.flowruns

/** Stable identifiers shared by Compose tests and Android accessibility automation. */
object FlowRunsSemantics {
    const val SCREEN = "flow_runs_screen"
    const val BACK = "flow_runs_back"
    const val LOADING = "flow_runs_loading"
    const val CHANGING = "flow_runs_changing"
    const val ERROR = "flow_runs_error"
    const val ERROR_DISMISS = "flow_runs_error_dismiss"
    const val EMPTY = "flow_runs_empty"

    @JvmStatic fun card(runId: String): String = "flow_runs_card__$runId"
    @JvmStatic fun defer(runId: String): String = action("defer", runId)
    @JvmStatic fun postpone(runId: String): String = action("postpone", runId)
    @JvmStatic fun readyNow(runId: String): String = action("ready_now", runId)
    @JvmStatic fun adjustTime(runId: String): String = action("adjust_time", runId)
    @JvmStatic fun moveUp(runId: String): String = action("move_up", runId)
    @JvmStatic fun moveDown(runId: String): String = action("move_down", runId)
    @JvmStatic fun cancel(runId: String): String = action("cancel", runId)

    private fun action(kind: String, runId: String): String = "flow_runs_action_${kind}__$runId"
}
