package de.thonktank.autosecretary

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FlowRunsComposeArchitectureTest {
    private val sourceDirectory = File(
        "src/main/kotlin/de/thonktank/autosecretary/presentation/flowruns",
    )

    @Test
    fun flowRunsUseFoundationComposeBehindTheExistingStateOwner() {
        val sources = sourceDirectory.listFiles { file -> file.extension == "kt" }
            .orEmpty().associate { it.name to it.readText() }
        assertTrue(sources.isNotEmpty())
        sources.forEach { (name, source) ->
            assertFalse(name, source.contains("androidx.compose.material"))
            assertFalse(name, Regex("0x[0-9a-fA-F]{6,8}").containsMatchIn(source))
            assertFalse(name, source.contains("FlowRunsAction"))
        }
        val host = sources.getValue("FlowRunsComposeHostView.kt")
        assertTrue(host.contains("this.state = state"))
        assertTrue(host.contains("FlowRunsComposeScreen("))
        val screen = sources.getValue("FlowRunsComposeScreen.kt")
        assertTrue(screen.contains("LazyColumn("))
        assertTrue(screen.contains("itemsIndexed(state.runs"))
        assertTrue(screen.contains("busy = state.changing"))
        assertTrue(screen.contains("enabled = !busy"))
    }

    @Test
    fun activityKeepsNavigationDialogsAndViewModelDispatchOnly() {
        val activity = File(
            "src/main/java/de/thonktank/autosecretary/FlowRunsActivity.java",
        ).readText()
        assertTrue(activity.contains("new FlowRunsComposeHostView(this)"))
        assertTrue(activity.contains("FlowDurationDialog.show"))
        assertTrue(activity.contains("new AlertDialog.Builder"))
        assertTrue(activity.contains("Toast.makeText"))
        assertTrue(activity.contains("viewModel.dispatch(FlowRunsAction"))
        assertFalse(activity.contains("LinearLayout"))
        assertFalse(activity.contains("runCard("))
        assertFalse(File(
            "src/main/java/de/thonktank/autosecretary/FlowRunningStripView.java",
        ).exists())
    }
}
