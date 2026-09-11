package de.thonktank.autosecretary

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import de.thonktank.autosecretary.domain.model.TaskId
import de.thonktank.autosecretary.domain.model.TaskKind
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Real production host, worker, Room save, own edit route and cancel boundary. */
class FlowProductEditorInstrumentationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)

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
                // The top-level cancel discards the complete draft, not a running chain.
                device.findObjects(By.desc("Abbrechen")).first().click()
                assertTrue(device.wait(Until.gone(By.res("flow-editor")), 5_000))
            }
            assertEquals("Vorbereiten", container.flows.loadGraph.execute(saved).steps.single().text)
        } finally {
            val id = saved ?: container.catalog.loadTaskCatalog.execute().items
                .firstOrNull { it.task.title == title }?.task?.id
            id?.let { container.catalog.delete.execute(it) }
        }
    }

    private fun input(field: String): UiObject2 = requireNotNull(device.wait(
        Until.findObject(By.res("flow-editor:$field").clazz("android.widget.EditText")), 5_000))

    private fun button(label: String): UiObject2 = requireNotNull(device.wait(
        Until.findObject(By.desc(label)), 5_000) ?: device.findObject(By.text(label))) { "Missing action $label" }
}
