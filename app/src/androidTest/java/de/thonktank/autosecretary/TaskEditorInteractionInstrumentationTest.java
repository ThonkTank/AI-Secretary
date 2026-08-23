package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskBoundKind;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.model.TimeOfDay;
import de.thonktank.autosecretary.editor.TaskEditorStateReducer;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.function.BooleanSupplier;

@RunWith(AndroidJUnit4.class)
public final class TaskEditorInteractionInstrumentationTest {
    private static final long TIMEOUT_MILLIS = 5_000L;
    private TaskEditorInteractionHarnessActivity activity;

    @After public void closeActivity() {
        if (activity != null) activity.finish();
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    @Test public void hardwareBackShowsAndDismissesTheRealDiscardPrompt() {
        mount(TaskEditorStateReducer.updateTitle(EditorUiState.create(TaskSlot.MORNING),
                "Morgenroutine"));

        pressHardwareBack();
        await("discard prompt did not open", () -> activity.prompt() != null
                && activity.prompt().isShowing()
                && activity.editorState().prompt == EditorUiState.Prompt.DISCARD);
        AlertDialog prompt = activity.prompt();
        assertNotNull(prompt.getWindow());
        assertTrue(prompt.getWindow().getDecorView().isShown());

        pressHardwareBack();
        await("discard prompt did not close", () -> activity.prompt() == null
                && activity.editorState().prompt == EditorUiState.Prompt.NONE);
        assertFalse(activity.dismissed());
    }

    @Test public void realDeletePromptKeepsTheTaskAndBackReturnsFromSummaryEdit() {
        EditorUiState edit = editState(validState().withPage(EditorUiState.Page.SUMMARY, false));
        mount(edit);
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.runOnMainSync(() -> activity.editor()
                .findViewById(R.id.task_editor_delete).performClick());
        await("delete prompt did not open", () -> activity.prompt() != null
                && activity.prompt().isShowing()
                && activity.editorState().prompt == EditorUiState.Prompt.DELETE);

        instrumentation.runOnMainSync(() -> {
            TextView keep = text(activity.prompt().getWindow().getDecorView(),
                    activity.getString(R.string.ask_delete_keep));
            assertNotNull(keep);
            keep.performClick();
        });
        await("keep did not close delete prompt", () -> activity.prompt() == null
                && activity.editorState().prompt == EditorUiState.Prompt.NONE);
        assertEquals(null, activity.deletedTaskId());

        pressHardwareBack();
        await("summary back did not open discard prompt", () -> activity.prompt() != null
                && activity.editorState().prompt == EditorUiState.Prompt.DISCARD);
    }

    @Test public void errorCorrectionKeepsRealFocusAndHardwareBackNavigates() {
        mount(validState().draft("", TaskSlot.MORNING, 30, Recurrence.DAILY, 1, 0,
                TimeOfDay.MORNING.bit, TaskBoundKind.FOREVER, null, null,
                null, null, "", new ArrayList<>(), null, 1));
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        instrumentation.runOnMainSync(() -> activity.editor()
                .findViewById(R.id.task_editor_save).performClick());
        await("title validation did not disable next", () -> !((Button) activity.editor()
                .findViewById(R.id.task_editor_save)).isEnabled());

        instrumentation.runOnMainSync(() -> {
            EditText title = activity.editor().findViewWithTag("task:title");
            title.requestFocus();
            title.setText("Korrigierter Titel");
        });
        await("corrected title did not re-enable next", () -> {
            EditText title = activity.editor().findViewWithTag("task:title");
            return title != null && title.hasFocus()
                    && ((Button) activity.editor().findViewById(R.id.task_editor_save)).isEnabled()
                    && activity.editorState().issues.isEmpty();
        });
        instrumentation.runOnMainSync(() -> activity.editor()
                .findViewById(R.id.task_editor_save).performClick());
        await("next did not navigate", () -> activity.editorState().page
                == EditorUiState.Page.SCHEDULE);

        pressHardwareBack();
        await("hardware back did not return to title", () -> activity.editorState().page
                == EditorUiState.Page.TITLE);
    }

    private void mount(EditorUiState initial) {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(instrumentation.getTargetContext(),
                TaskEditorInteractionHarnessActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = (TaskEditorInteractionHarnessActivity) instrumentation.startActivitySync(intent);
        instrumentation.runOnMainSync(() -> activity.mount(initial));
        instrumentation.waitForIdleSync();
        await("editor window never received focus", activity::hasWindowFocus);
    }

    private void pressHardwareBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void await(String message, BooleanSupplier condition) {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MILLIS;
        while (SystemClock.uptimeMillis() < deadline) {
            final boolean[] matched = {false};
            instrumentation.runOnMainSync(() -> matched[0] = condition.getAsBoolean());
            if (matched[0]) return;
            SystemClock.sleep(16L);
        }
        fail(message);
    }

    private static TextView text(View root, String value) {
        if (root instanceof TextView && value.contentEquals(((TextView) root).getText()))
            return (TextView) root;
        if (!(root instanceof android.view.ViewGroup)) return null;
        android.view.ViewGroup group = (android.view.ViewGroup) root;
        for (int index = 0; index < group.getChildCount(); index++) {
            TextView found = text(group.getChildAt(index), value);
            if (found != null) return found;
        }
        return null;
    }

    private static EditorUiState validState() {
        return EditorUiState.create(TaskSlot.MORNING).draft("Morgenroutine",
                TaskSlot.MORNING, 30, Recurrence.DAILY, 1, 0,
                TimeOfDay.MORNING.bit, TaskBoundKind.FOREVER, null, null,
                null, null, "", new ArrayList<>(), null, 1);
    }

    private static EditorUiState editState(EditorUiState state) {
        android.os.Bundle bundle = state.toBundle();
        bundle.putString("task_id", "task");
        return EditorUiState.fromBundle(bundle);
    }
}
