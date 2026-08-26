package de.thonktank.autosecretary

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TaskEditorComposeArchitectureTest {
    private val composeDirectory = File(
        "src/debug/kotlin/de/thonktank/autosecretary/presentation/editor",
    )

    @Test
    fun composeEditorUsesTheExistingReducerWithoutMaterialOrIndependentColors() {
        val sources = composeDirectory.listFiles { file -> file.extension == "kt" }
            .orEmpty().associate { it.name to it.readText() }
        assertTrue(sources.isNotEmpty())
        sources.forEach { (name, source) ->
            assertFalse(name, source.contains("androidx.compose.material"))
            assertFalse(name, Regex("0x[0-9a-fA-F]{6,8}").containsMatchIn(source))
        }
        assertTrue(sources.getValue("TaskEditorComposePages.kt")
            .contains("TaskEditorStateReducer"))
        assertTrue(sources.getValue("TaskEditorComposeSteps.kt")
            .contains("TaskEditorStateReducer"))
        val golden = File(
            "src/test/kotlin/de/thonktank/autosecretary/TaskEditorComposeGoldenRobolectricTest.kt",
        ).readText()
        assertTrue(golden.contains("GoldenAssertions.compareReadOnly"))
        assertFalse(golden.contains("UPDATE_TASK_EDITOR_COMPOSE"))
    }

    @Test
    fun phaseFiveAHasNoSecondDraftOrProductionCutover() {
        val host = File(composeDirectory, "TaskEditorComposeHostView.kt").readText()
        val screen = File(composeDirectory, "TaskEditorComposeScreen.kt").readText()
        val coordinator = File(
            "src/main/java/de/thonktank/autosecretary/TaskEditorCoordinator.java",
        ).readText()
        assertFalse(host.contains("onDraftChanged = { editorState ="))
        assertTrue(host.contains("listener?.onDraftChanged(it)"))
        assertTrue(host.contains("fun handleBack(): Boolean"))
        assertTrue(host.contains(").back()"))
        assertFalse(screen.contains("BackHandler"))
        assertFalse(coordinator.contains("TaskEditorComposeHostView"))
        assertTrue(coordinator.contains("new TaskEditorView"))
        assertTrue(File("src/main/kotlin/de/thonktank/autosecretary/presentation/editor")
            .listFiles { file -> file.extension == "kt" }.orEmpty().isEmpty())
    }
}
