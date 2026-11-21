package de.thonktank.autosecretary

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import de.thonktank.autosecretary.domain.model.TaskId
import de.thonktank.autosecretary.domain.model.TaskKind
import de.thonktank.autosecretary.domain.model.FlowDelayPolicy
import de.thonktank.autosecretary.ui.today.ProductActivitySession
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.UUID

/** Real production host, worker, Room save, own edit route and cancel boundary. */
class FlowProductEditorInstrumentationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)

    private var stage = "launch"
    @get:Rule val failureEvidence = object : TestWatcher() {
        override fun failed(error: Throwable, description: Description) {
            val hierarchy = runCatching {
                ByteArrayOutputStream().also { device.dumpWindowHierarchy(it) }.toString("UTF-8")
            }.getOrElse { "Hierarchy unavailable: $it" }
            Log.e("FlowProductEditorTest", "Stage: $stage\n$hierarchy", error)
        }
    }

    @Test fun createReopenAndCancelUseTheActualDedicatedEditor() {
        val context = instrumentation.targetContext
        val container = AutoSecretaryApplication.from(context).container()
        val title = "Ablaufprüfung ${UUID.randomUUID().toString().take(8)}"
        var saved: TaskId? = null
        try {
            ActivityScenario.launch<FlowSetupActivity>(Intent(context, FlowSetupActivity::class.java)).use {
                input("title").text = title
                button("+ Schritt").click()
                input("name").text = "Vorbereiten"
                input("duration").text = "2"
                button("Beim Start nachfragen").click()
                button("Speichern").click()
                button("Weiter").click()
                button("Fertig").click()
                assertTrue("The real save must finish the host", device.wait(Until.gone(By.res("flow-editor")), 10_000))
            }
            saved = container.catalog.loadTaskCatalog.execute().items.single { it.task.title == title }.task.id
            val setup = container.flows.loadGraph.execute(saved)
            assertEquals(TaskKind.FLOW, setup.task.kind)
            assertEquals("Vorbereiten", setup.steps.single().text)
            assertEquals(120_000L, setup.definition.nodes.values.single().waitAfter.proposedDelayMillis())
            assertEquals(de.thonktank.autosecretary.domain.model.FlowDelayPolicy.Mode.REMEMBER_LAST,
                setup.definition.nodes.values.single().waitAfter.mode)
            ActivityScenario.launch<FlowSetupActivity>(Intent(context, FlowSetupActivity::class.java)
                .putExtra(FlowSetupActivity.TASK_ID, saved.value)).use { scenario ->
                assertEquals(title, input("title").text)
                button("Vorbereiten").click()
                input("name").text = "Nicht speichern"
                scenario.recreate()
                assertEquals("Nicht speichern", input("name").text)
                // A modal cancel discards only that step edit. Close it before cancelling
                // the whole draft; the background action is intentionally inaccessible.
                requireNotNull(device.findObject(By.res("flow-editor:cancel-form"))).click()
                assertTrue(device.wait(Until.gone(By.res("flow-editor:form")), 5_000))
                requireNotNull(device.wait(Until.findObject(By.res("flow-editor:cancel-draft")), 5_000)).click()
                assertTrue(device.wait(Until.gone(By.res("flow-editor")), 5_000))
            }
            assertEquals("Vorbereiten", container.flows.loadGraph.execute(saved).steps.single().text)
        } finally {
            val id = saved ?: container.catalog.loadTaskCatalog.execute().items
                .firstOrNull { it.task.title == title }?.task?.id
            id?.let { container.catalog.delete.execute(it) }
        }
    }

    @Test fun productRoutesOpenTheNormalAndDedicatedEditors() {
        val context = instrumentation.targetContext
        val container = AutoSecretaryApplication.from(context).container()
        val title = "Ablaufweg " + UUID.randomUUID().toString().take(8)
        val draft = FlowEditorDraft.empty().rename(title)
            .addStep("Start", FlowDelayPolicy.fixed(0))
        val saved = container.flows.saveGraph.execute(draft.edit())
        val add = By.desc(context.getString(R.string.content_add_task))
        val normalHeading = By.text(context.getString(R.string.editor_ctx_new))
        val flowScreen = By.res("flow-editor")
        val cancelFlow = By.res("flow-editor:cancel-draft")
        try {
            ProductActivitySession(instrumentation).use { scenario ->
                scenario.launch()
                click(add, "Add task")
                click(By.text("Aufgabe"), "Choose the normal task editor")
                assertNotNull(device.wait(Until.findObject(normalHeading), 5_000))
                assertNull(device.findObject(flowScreen))
                button(context.getString(R.string.editor_cancel)).click()
                assertTrue(device.wait(Until.gone(normalHeading), 5_000))

                click(add, "Add flow")
                click(By.text("Ablauf"), "Choose the dedicated flow editor")
                assertNotNull(device.wait(Until.findObject(flowScreen), 5_000))
                click(cancelFlow, "Close the unsaved flow")
                assertTrue(device.wait(Until.gone(flowScreen), 5_000))

                click(By.res(context.resources.getResourceName(R.id.navigation_all_tasks)),
                    "Open All Tasks")
                stage = "Find flow in All Tasks"
                requireNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5_000))
                    .text = title
                click(By.desc(context.getString(R.string.a11y_edit_task, title)), "Edit flow from All Tasks")
                assertEquals(title, input("title").text)
                click(cancelFlow, "Close the existing flow")
                assertTrue(device.wait(Until.gone(flowScreen), 5_000))
            }
        } finally {
            container.catalog.delete.execute(saved)
        }
    }

    private fun click(selector: BySelector, label: String) {
        stage = label
        requireNotNull(device.wait(Until.findObject(selector), 5_000)) { "Missing action $label" }
        requireNotNull(device.findObject(selector)) { "Action disappeared: $label" }.click()
    }

    private fun input(field: String): UiObject2 = requireNotNull(device.wait(
        Until.findObject(By.res("flow-editor:$field").clazz("android.widget.EditText")), 5_000))

    private fun button(label: String): UiObject2 {
        stage = "click $label"
        requireNotNull(device.wait(Until.findObject(By.desc(label).enabled(true)), 5_000)
            ?: device.findObject(By.text(label).enabled(true))) { "Missing action $label" }
        // Saving the step closes its form and keyboard. Presence alone can expose
        // the next button while Compose and the resized window still move its bounds.
        assertFalse("Layout did not settle before $label",
            requireNotNull(instrumentation.uiAutomation.rootInActiveWindow)
                .waitForStable(5_000, 300, 50, true).isTimeout)
        // Resolve fresh coordinates after the transition; never retry a dispatched click.
        return requireNotNull(device.findObject(By.desc(label).enabled(true))
            ?: device.findObject(By.text(label).enabled(true))) { "Action disappeared after layout: $label" }
    }
}
