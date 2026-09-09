package de.thonktank.autosecretary

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobilePresentationArchitectureTest {
    @Test
    fun sharedMobilePrimitivesStayStatelessAndDomainFree() {
        val directory = File(
            "src/main/kotlin/de/thonktank/autosecretary/presentation/mobile",
        )
        val sources = directory.listFiles { file -> file.extension == "kt" }
            .orEmpty()
            .associate { it.name to it.readText() }

        assertTrue(sources.keys.containsAll(listOf(
            "MobileComposePrimitives.kt",
            "RemainingDurationFormatter.kt",
        )))
        sources.forEach { (name, source) ->
            assertFalse(name, source.contains("ViewModel"))
            assertFalse(name, source.contains("ActionSink"))
            assertFalse(name, source.contains("domain.usecase"))
            assertFalse(name, source.contains("domain.model"))
            assertFalse(name, source.contains("System.currentTimeMillis"))
        }
    }

    @Test
    fun taskAndFlowScreensUseTheSharedTimeFormatterWithoutReadingTheWallClock() {
        val allTasks = File(
            "src/main/kotlin/de/thonktank/autosecretary/presentation/alltasks/AllTasksRunningFlows.kt",
        ).readText()
        val flowRuns = File(
            "src/main/kotlin/de/thonktank/autosecretary/presentation/flowruns/FlowRunsComposeScreen.kt",
        ).readText()

        assertTrue(allTasks.contains("remainingDurationText"))
        assertTrue(flowRuns.contains("remainingDurationText"))
        assertFalse(allTasks.contains("System.currentTimeMillis"))
        assertFalse(flowRuns.contains("System.currentTimeMillis"))
    }
}
