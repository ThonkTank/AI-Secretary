package de.thonktank.autosecretary.ui.today;

import static org.junit.Assert.*;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.content.Context;
import android.database.Cursor;
import android.view.accessibility.AccessibilityWindowInfo;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import de.thonktank.autosecretary.AppContainer;
import de.thonktank.autosecretary.AutoSecretaryApplication;
import de.thonktank.autosecretary.R;
import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.domain.today.TodayQueue;
import de.thonktank.autosecretary.domain.usecase.MoveTodayItem;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;

/** Real product windows, input, ViewModel, worker and Room. Only setup and readback bypass UI. */
@RunWith(AndroidJUnit4.class)
public final class StepQuickEditInstrumentationTest {
    private static final long TIMEOUT = 10_000;
    private static final String ORIGINAL = "40 kg";
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final List<TaskId> created = new ArrayList<>();
    private Context context;
    private AppContainer container;
    private UiDevice device;
    private ProductActivitySession session;
    private String keyboardSetting, keyboardPackage;
    private int accessibilityFlags;
    private String stepTitle, templateId, occurrenceStepId;

    @Before public void setUp() throws Exception {
        context = instrumentation.getTargetContext();
        assertEquals("Fixtures must not touch a production installation",
                "de.thonktank.autosecretary.test", context.getPackageName());
        container = AutoSecretaryApplication.from(context).container();
        device = UiDevice.getInstance(instrumentation);
        keyboardPackage = device.executeShellCommand("settings get secure default_input_method").trim().split("/")[0];
        keyboardSetting = device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim();
        device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1");
        AccessibilityServiceInfo info = instrumentation.getUiAutomation().getServiceInfo();
        accessibilityFlags = info.flags;
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        instrumentation.getUiAutomation().setServiceInfo(info);
    }

    @After public void tearDown() throws Exception {
        try {
            if (session != null) session.close();
        } finally {
            try {
                for (TaskId id : created) container.catalog.delete.execute(id);
            } finally {
                if (keyboardSetting != null) device.executeShellCommand("null".equals(keyboardSetting)
                        ? "settings delete secure show_ime_with_hard_keyboard"
                        : "settings put secure show_ime_with_hard_keyboard " + keyboardSetting);
                AccessibilityServiceInfo info = instrumentation.getUiAutomation().getServiceInfo();
                info.flags = accessibilityFlags;
                instrumentation.getUiAutomation().setServiceInfo(info);
            }
        }
    }

    @Test public void menuEditsAllFieldsAndDefersPrescriptionAfterRecordedSet() {
        seed(false);
        openQuick();
        assertTrue(device.takeScreenshot(new java.io.File(context.getExternalFilesDir(null), "quick-editor.png")));
        field("title").setText("Neuer Schritt");
        field("sets").setText("4");
        field("note").setText("42 kg\nSitz 7");
        saveQuick();
        assertEquals("Neuer Schritt", container.database.steps().template(templateId).text);
        assertEquals(Integer.valueOf(4), container.database.steps().template(templateId).plannedSets);
        assertEquals(Integer.valueOf(3), container.database.steps().occurrenceStep(occurrenceStepId).plannedSets);
        assertStored("42 kg\nSitz 7");
    }

    @Test public void cancelDiscardsDraftWithoutOpeningTaskWizard() {
        seed(false); openQuick();
        field("title").setText("Verwerfen");
        cancelQuick();
        assertEquals(stepTitle, container.database.steps().template(templateId).text);
        assertStored(ORIGINAL);
        assertFalse(device.hasObject(By.res("task-editor:save")));
    }

    @Test public void invalidTitleKeepsDialogOpenAndCanBeCorrected() {
        seed(false); openQuick();
        field("title").setText("");
        object(By.res("step-editor:save")).click();
        assertNotNull(object(By.text(context.getString(R.string.err_step_empty))));
        assertStored(ORIGINAL);
        field("title").setText("Korrigiert");
        saveQuick();
        assertEquals("Korrigiert", container.database.steps().template(templateId).text);
    }

    @Test public void recreationKeepsUnsavedDraftAndCancelStillDiscardsIt() {
        seed(false); openQuick();
        field("title").setText("Entwurf");
        session.recreate();
        assertEquals("Entwurf", field("title").getText());
        assertKeyboardVisible();
        assertEquals(stepTitle, container.database.steps().template(templateId).text);
        cancelQuick();
        openQuick();
        assertEquals(stepTitle, field("title").getText());
        cancelQuick();
    }

    @Test public void candidateQuickEditSavesWithoutStartingFlow() {
        TaskId task = seed(true); openQuick();
        field("title").setText("Neuer Start");
        field("sets").setText("4");
        saveQuick();
        assertEquals("Neuer Start", container.database.steps().template(templateId).text);
        assertEquals(Integer.valueOf(4), container.database.steps().template(templateId).plannedSets);
        try (Cursor rows = container.database.getOpenHelper().getReadableDatabase().query(
                "SELECT COUNT(*) FROM step_flow_runs WHERE taskId=?", new Object[]{task.value})) {
            assertTrue(rows.moveToFirst()); assertEquals(0, rows.getInt(0));
        }
        assertTrue(occurrenceIds().isEmpty());
    }

    private void openQuick() {
        object(By.desc(context.getString(R.string.content_step_actions, stepTitle))).click();
        object(By.text(context.getString(R.string.action_edit_step))).click();
        object(By.res("step-editor:dialog"));
        object(By.res("step:" + templateId + ":title"));
        assertKeyboardVisible();
    }
    private UiObject2 field(String name) {
        BySelector selector = By.res("step:" + templateId + ":" + name);
        UiObject2 found = device.findObject(selector);
        for (int scroll = 0; found == null && scroll < 6; scroll++) {
            UiObject2 form = object(By.res("step-editor:fields"));
            if (!form.scroll(Direction.DOWN, .7f)) break;
            found = device.findObject(selector);
        }
        return object(selector);
    }
    private void saveQuick() {
        object(By.res("step-editor:save")).click();
        assertTrue("Save closes the quick editor", device.wait(Until.gone(By.res("step-editor:dialog")), TIMEOUT));
    }
    private void cancelQuick() {
        object(By.res("step-editor:cancel")).click();
        assertTrue("Cancel closes the quick editor", device.wait(Until.gone(By.res("step-editor:dialog")), TIMEOUT));
    }

    private TaskId seed(boolean flow) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        stepTitle = "Rudern " + suffix;
        TaskStepDefinition step = new TaskStepDefinition(null, 0, stepTitle, 0, 0,
                StepPrescription.forAmount(StepAmount.setsReps(3, 12)), ORIGINAL, StepActivationKind.SCHEDULED);
        TaskId task = flow ? container.flows.saveGraph.execute(new FlowGraphEdit(null,
                "Notizablauf " + suffix, new FlowTileGraph(List.of("start"), List.of()),
                List.of(step), Map.of("start", FlowDelayPolicy.fixed(0)), List.of(), List.of()))
                : container.catalog.create.execute(new TaskDefinition("Notiztest " + suffix, null,
                TaskSlot.MORNING, Recurrence.ONCE, 1, 0, 0, TaskBoundKind.FOREVER,
                null, null, null, null, "", List.of(step)));
        created.add(task);
        templateId = container.database.steps().templates(task.value).get(0).id;
        container.today.materializeDue.execute();
        if (!flow) {
            List<String> ids = occurrenceIds();
            assertEquals(1, ids.size());
            occurrenceStepId = ids.get(0);
            container.today.recordSetResult.execute(occurrenceStepId, new SetResult(10));
        }
        TodayQueue.Entry entry = TodayQueue.visible(container.today.loadDashboard.execute(container.clock.today()),
                container.clock.today()).stream().filter(e -> e.task != null ? e.task.task.id.equals(task) : e.sheet.task.id.equals(task))
                .findFirst().orElseThrow(AssertionError::new);
        assertTrue(container.today.moveItem.execute(entry.kind, entry.id, MoveTodayItem.Action.FIRST));
        session = new ProductActivitySession(instrumentation);
        session.launch();
        return task;
    }

    private List<String> occurrenceIds() {
        List<String> ids = new ArrayList<>();
        try (Cursor rows = container.database.getOpenHelper().getReadableDatabase().query(
                "SELECT id FROM occurrence_steps WHERE sourceTemplateId=?", new Object[]{templateId})) {
            while (rows.moveToNext()) ids.add(rows.getString(0));
        }
        return ids;
    }

    private UiObject2 object(BySelector selector) {
        UiObject2 result = device.wait(Until.findObject(selector), TIMEOUT);
        assertNotNull("Missing product control: " + selector, result);
        return result;
    }
    private void assertKeyboardVisible() {
        assertTrue("Soft keyboard must be visible while editing",
                device.wait(Until.hasObject(By.pkg(keyboardPackage)), TIMEOUT));
        assertTrue("An input-method window must be present", instrumentation.getUiAutomation().getWindows()
                .stream().anyMatch(window -> window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD));
    }

    private void assertStored(String expected) {
        assertEquals(expected, container.database.steps().template(templateId).note);
        if (occurrenceStepId == null) return;
        assertEquals(expected, container.database.steps().occurrenceStep(occurrenceStepId).note);
        assertFalse(container.database.steps().occurrenceStep(occurrenceStepId).done);
        var results = container.database.steps().repetitionResults(occurrenceStepId);
        assertEquals(1, results.size());
        assertEquals(10, results.get(0).actualRepetitions);
    }
}
