package de.thonktank.autosecretary

import de.thonktank.autosecretary.presentation.flowruns.FlowRunsSemantics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class FlowRunsPresentationContractTest {
    @Test
    fun errorPersistsAcrossPresentationChangesUntilItsMatchingAcknowledgement() {
        val error = FlowRunsScreenState.idle(1_000L).withError(7L, "Kapazität fehlt")

        val presented = error.presentAt(61_000L)
        val wrongAcknowledgement = presented.acknowledgeError(6L)

        assertEquals(7L, presented.errorId)
        assertEquals("Kapazität fehlt", presented.errorMessage)
        assertSame(presented, wrongAcknowledgement)

        val acknowledged = presented.acknowledgeError(7L)
        assertEquals(0L, acknowledged.errorId)
        assertNull(acknowledged.errorMessage)
        assertSame(acknowledged, acknowledged.acknowledgeError(7L))
    }

    @Test
    fun semanticIdentifiersAreStableAndDistinctPerActionAndRun() {
        val identifiers = listOf(
            FlowRunsSemantics.card("offered"),
            FlowRunsSemantics.defer("offered"),
            FlowRunsSemantics.postpone("offered"),
            FlowRunsSemantics.readyNow("timed"),
            FlowRunsSemantics.adjustTime("timed"),
            FlowRunsSemantics.moveUp("timed"),
            FlowRunsSemantics.moveDown("timed"),
            FlowRunsSemantics.cancel("timed"),
            FlowRunsSemantics.cancel("capacity"),
        )

        assertEquals(identifiers.size, identifiers.toSet().size)
        assertEquals("flow_runs_action_ready_now__timed", FlowRunsSemantics.readyNow("timed"))
    }
}
