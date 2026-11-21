package de.thonktank.autosecretary

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
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

    @Test fun typePickerCreatesANormalTaskAndAllTasksOpensItForEditing() {
        val context = instrumentation.targetContext
        val title = "Aufgabenprüfung ${UUID.randomUUID()}"
        val renamed = "$title geändert"
        val next = By.desc(context.getString(R.string.editor_next))
        val save = By.desc(context.getString(R.string.action_save))
        try {
            ProductActivitySession(instrumentation).use { scenario ->
                scenario.launch()
                chooseTaskType("Aufgabe")
                awaitText(R.string.editor_ctx_new)
                awaitText(R.string.editor_frage_titel)
                editText().text = title
                click(next, "Weiter zum Rhythmus")
                awaitText(R.string.editor_frage_rhythmus)
                click(next, "Weiter zu den Schritten")
                awaitText(R.string.editor_frage_schritte)
                click(next, "Weiter zur Zusammenfassung")
                click(save, "Normale Aufgabe anlegen")
                assertTrue(device.wait(Until.gone(By.text(context.getString(R.string.editor_ctx_new))), 5_000))
                val created = catalogTask(title)
                assertEquals(TaskKind.TASK, created.kind)

                openFromAllTasks(title)
                awaitText(R.string.editor_ctx_edit)
                val titleRow = context.getString(R.string.a11y_editor_summary_row,
                    context.getString(R.string.field_title_label), title, context.getString(R.string.editor_change))
                click(By.desc(titleRow), "Titel aus der Zusammenfassung bearbeiten")
                awaitText(R.string.editor_frage_titel)
                editText(title).text = renamed
                click(next, "Geänderten Titel übernehmen")
                click(save, "Normale Aufgabe bearbeiten")
                assertTrue(device.wait(Until.gone(By.text(context.getString(R.string.editor_ctx_edit))), 5_000))
                val edited = catalogTask(renamed)
                assertEquals(created.id, edited.id)
                assertEquals(TaskKind.TASK, edited.kind)
            }
        } finally { deleteTestTasks(title, renamed) }
    }

    @Test fun typePickerCreatesAFlowAndAllTasksOpensTheDedicatedEditor() {
        val context = instrumentation.targetContext
        val container = AutoSecretaryApplication.from(context).container()
        val title = "Ablaufprüfung ${UUID.randomUUID()}"
        val renamed = "$title geändert"
        try {
            ProductActivitySession(instrumentation).use { scenario ->
                scenario.launch()
                chooseTaskType("Ablauf")
                input("title").text = title
                button("+ Schritt").click()
                input("name").text = "Vorbereiten"
                input("duration").text = "2"
                button("Beim Start nachfragen").click()
                button("Speichern").click()
                finishFlow()
                val created = catalogTask(title)
                assertEquals(TaskKind.FLOW, created.kind)
                openFromAllTasks(title)
                assertEquals(title, input("title").text)
                input("title").text = renamed
                finishFlow()
                assertEquals(created.id, catalogTask(renamed).id)
            }
            val saved = catalogTask(renamed).id
            val setup = container.flows.loadGraph.execute(saved)
            assertEquals(TaskKind.FLOW, setup.task.kind)
            assertEquals("Vorbereiten", setup.steps.single().text)
            val wait = setup.definition.nodes.values.single().waitAfter
            assertEquals(120_000L, wait.proposedDelayMillis())
            assertEquals(FlowDelayPolicy.Mode.REMEMBER_LAST, wait.mode)
            ActivityScenario.launch<FlowSetupActivity>(Intent(context, FlowSetupActivity::class.java)
                .putExtra(FlowSetupActivity.TASK_ID, saved.value)).use { scenario ->
                assertEquals(renamed, input("title").text)
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
        } finally { deleteTestTasks(title, renamed) }
    }

    private fun chooseTaskType(type: String) {
        val context = instrumentation.targetContext
        click(By.desc(context.getString(R.string.content_add_task)), "Aufgabentyp wählen")
        click(By.text(type), "$type anlegen")
    }

    private fun openFromAllTasks(title: String) {
        val context = instrumentation.targetContext
        click(By.res(context.resources.getResourceName(R.id.navigation_all_tasks)), "Alles öffnen")
        editText().text = title
        click(By.desc(context.getString(R.string.a11y_edit_task, title)), "Gespeicherten Eintrag bearbeiten")
    }

    private fun awaitText(resource: Int) {
        val text = instrumentation.targetContext.getString(resource)
        stage = "Seite $text"
        assertTrue(stage, device.wait(Until.hasObject(By.text(text)), 5_000))
    }

    private fun editText(value: String? = null): UiObject2 {
        val selector = By.clazz("android.widget.EditText")
        if (value != null) selector.text(value)
        return requireNotNull(device.wait(Until.findObject(selector), 5_000)) { "Eingabefeld fehlt: $stage" }
    }

    private fun finishFlow() {
        button("Weiter").click()
        button("Fertig").click()
        assertTrue("Speichern muss den Ablaufeditor schließen", device.wait(Until.gone(By.res("flow-editor")), 10_000))
    }

    private fun catalogTask(title: String) = AutoSecretaryApplication.from(instrumentation.targetContext)
        .container().catalog.loadTaskCatalog.execute().items.single { it.task.title == title }.task

    private fun deleteTestTasks(vararg titles: String) {
        val catalog = AutoSecretaryApplication.from(instrumentation.targetContext).container().catalog
        catalog.loadTaskCatalog.execute().items.filter { it.task.title in titles }.forEach { catalog.delete.execute(it.task.id) }
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
