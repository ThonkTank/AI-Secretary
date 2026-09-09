package de.thonktank.autosecretary.presentation.flowruns

import de.thonktank.autosecretary.FlowRunsScreenState
import de.thonktank.autosecretary.domain.model.FlowResourceState
import de.thonktank.autosecretary.domain.model.FlowRunSummary
import de.thonktank.autosecretary.domain.model.StepFlowRunState
import de.thonktank.autosecretary.domain.model.TaskId

/** Deterministic run catalog shared by the visual and interaction harnesses. */
object FlowRunsComposeFixture {
    @JvmStatic
    fun state(now: Long = System.currentTimeMillis()): FlowRunsScreenState =
        FlowRunsScreenState.idle(now).withRuns(runs(now))

    @JvmStatic
    fun changing(now: Long = System.currentTimeMillis()): FlowRunsScreenState =
        state(now).withChanging()

    @JvmStatic
    fun loading(now: Long = System.currentTimeMillis()): FlowRunsScreenState =
        FlowRunsScreenState.idle(now).withLoading()

    @JvmStatic
    fun empty(now: Long = System.currentTimeMillis()): FlowRunsScreenState =
        FlowRunsScreenState.idle(now)

    @JvmStatic
    fun error(now: Long = System.currentTimeMillis()): FlowRunsScreenState =
        FlowRunsScreenState.idle(now)
        .withError(1L, "Abläufe konnten nicht geladen werden.")

    @JvmStatic
    fun runs(now: Long = System.currentTimeMillis()): List<FlowRunSummary> = listOf(
        run(
            id = "offered",
            taskTitle = "Wäsche waschen",
            seedTitle = "Buntwäsche",
            stepTitle = "Wäsche aufhängen",
            state = StepFlowRunState.OFFERED,
            position = 1,
            arrivalDelayMillis = 20 * 60_000L,
            resources = listOf(resource("Wäscheständer", FlowResourceState.RESERVED)),
        ),
        run(
            id = "timed",
            taskTitle = "Brot backen",
            seedTitle = "Sauerteigbrot",
            stepTitle = "Teig ruhen lassen",
            state = StepFlowRunState.WAITING_TIME,
            position = 0,
            readyAt = now + 90 * 60_000L,
            resources = listOf(resource("Backofen", FlowResourceState.PLANNED)),
        ),
        run(
            id = "capacity",
            taskTitle = "Wäsche waschen",
            seedTitle = "Feinwäsche",
            stepTitle = "Maschine anstellen",
            state = StepFlowRunState.WAITING_RESOURCE,
            position = 0,
            resources = listOf(resource("Waschmaschine", FlowResourceState.PLANNED)),
        ),
    )

    private fun run(
        id: String,
        taskTitle: String,
        seedTitle: String,
        stepTitle: String,
        state: StepFlowRunState,
        position: Int,
        readyAt: Long? = null,
        arrivalDelayMillis: Long? = null,
        resources: List<FlowRunSummary.Resource> = emptyList(),
    ) = FlowRunSummary(
        id,
        TaskId.of("task-$id"),
        taskTitle,
        "seed-$id",
        seedTitle,
        "step-$id",
        stepTitle,
        state,
        readyAt,
        if (state == StepFlowRunState.OFFERED) "occurrence-$id" else null,
        (position + 1L) * 1_024L,
        position,
        3,
        null,
        resources,
        arrivalDelayMillis,
    )

    private fun resource(name: String, state: FlowResourceState) = FlowRunSummary.Resource(
        "resource-${name.lowercase()}", name, 1, 0, 2, state,
    )
}
