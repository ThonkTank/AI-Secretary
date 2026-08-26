package de.thonktank.autosecretary

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AllTasksComposeArchitectureTest {
    private val debugDirectory = File(
        "src/debug/kotlin/de/thonktank/autosecretary/presentation/alltasks",
    )

    @Test
    fun phaseSixAIsAFoundationOnlyReadOnlyComparisonRenderer() {
        val sources = debugDirectory.listFiles { file -> file.extension == "kt" }
            .orEmpty().associate { it.name to it.readText() }
        assertTrue(sources.isNotEmpty())
        sources.forEach { (name, source) ->
            assertFalse(name, source.contains("androidx.compose.material"))
            assertFalse(name, Regex("0x[0-9a-fA-F]{6,8}").containsMatchIn(source))
        }
        val host = sources.getValue("AllTasksComposeHostView.kt")
        assertTrue(host.contains("this.screenState = state"))
        assertFalse(host.contains("screenState = screenState.with"))
        assertTrue(host.contains("listener?.onQuery(it)"))
        val screen = sources.getValue("AllTasksComposeScreen.kt")
        assertTrue(screen.contains("LazyColumn("))
        assertTrue(screen.contains("key = { row -> row.key }"))
        assertTrue(screen.contains("AllTasksRow.project(state)"))
        assertTrue(sources.getValue("AllTasksComposeDispatcher.kt")
            .contains("callbacks.onMoveStep"))
    }

    @Test
    fun comparisonRendererCannotLeakIntoTheProductionMount() {
        val main = File("src/main/java/de/thonktank/autosecretary/DashboardRenderer.java").readText()
        assertFalse(main.contains("AllTasksComposeHostView"))
        assertTrue(main.contains("new AllTasksView"))
        assertFalse(File(
            "src/main/kotlin/de/thonktank/autosecretary/presentation/alltasks/" +
                "AllTasksComposeHostView.kt",
        ).exists())
        val golden = File(
            "src/test/kotlin/de/thonktank/autosecretary/AllTasksComposeGoldenRobolectricTest.kt",
        ).readText()
        assertTrue(golden.contains("GoldenAssertions.compareReadOnly"))
        assertFalse(golden.contains("UPDATE_ALL_TASKS_COMPOSE"))
    }
}
