package de.thonktank.autosecretary

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AllTasksComposeArchitectureTest {
    private val mainDirectory = File(
        "src/main/kotlin/de/thonktank/autosecretary/presentation/alltasks",
    )

    @Test
    fun productionRendererUsesFoundationAndTheAuthoritativeActionBoundary() {
        val sources = mainDirectory.listFiles { file -> file.extension == "kt" }
            .orEmpty().associate { it.name to it.readText() }
        assertTrue(sources.isNotEmpty())
        sources.forEach { (name, source) ->
            assertFalse(name, source.contains("androidx.compose.material"))
            assertFalse(name, Regex("0x[0-9a-fA-F]{6,8}").containsMatchIn(source))
        }
        val host = sources.getValue("AllTasksComposeHostView.kt")
        assertTrue(host.contains("this.screenState = state"))
        assertFalse(host.contains("screenState = screenState.with"))
        assertTrue(host.contains("emit(AllTasksAction.queryChanged(it))"))
        val screen = sources.getValue("AllTasksComposeScreen.kt")
        assertTrue(screen.contains("LazyColumn("))
        assertTrue(screen.contains("key = { row -> row.key }"))
        assertTrue(screen.contains("AllTasksRow.project(state)"))
        assertTrue(screen.contains("allTasksDragContainer("))
        assertFalse(screen.contains("allTasksDragSource("))
        assertTrue(screen.contains("withFrameNanos"))
        assertTrue(sources.getValue("AllTasksComposeDispatcher.kt")
            .contains("callbacks.onMoveStep"))
    }

    @Test
    fun productionMountHasNoLegacyAllTasksRenderer() {
        val main = File("src/main/java/de/thonktank/autosecretary/DashboardRenderer.java").readText()
        assertTrue(main.contains("new AllTasksComposeHostView"))
        assertFalse(main.contains("new AllTasksView"))
        assertTrue(File(mainDirectory, "AllTasksComposeHostView.kt").exists())
        listOf(
            "AllTasksView.java",
            "AllTasksControlsView.java",
            "AllTasksListAdapter.java",
            "AllTasksReorderController.java",
            "AllTasksCoordinator.java",
            "AllTasksRowContent.java",
        ).forEach { legacy ->
            assertFalse(
                legacy,
                File("src/main/java/de/thonktank/autosecretary/presentation/alltasks", legacy)
                    .exists(),
            )
        }
        val golden = File(
            "src/test/kotlin/de/thonktank/autosecretary/AllTasksComposeGoldenRobolectricTest.kt",
        ).readText()
        assertTrue(golden.contains("GoldenAssertions.compareReadOnly"))
        assertFalse(golden.contains("UPDATE_ALL_TASKS_COMPOSE"))
    }
}
