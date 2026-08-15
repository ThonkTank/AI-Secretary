package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class TaskActionHandlerTest {
    @Test public void validActionExecutesOnceThenMaterializes() {
        RecordingActions actions = new RecordingActions();
        Intent intent = new Intent().setAction(TaskActionReceiver.COMPLETE)
                .putExtra(TaskActionReceiver.EXTRA_OCCURRENCE_ID, "occurrence-7");

        new TaskActionHandler(actions).handle(intent);

        assertEquals("occurrence-7", actions.completed);
        assertEquals(1, actions.materializations);
    }

    @Test public void missingIdIsReportedWithoutMaterialization() {
        RecordingActions actions = new RecordingActions();
        Intent intent = new Intent().setAction(TaskActionReceiver.TOGGLE_STEP);

        assertThrows(IllegalArgumentException.class,
                () -> new TaskActionHandler(actions).handle(intent));

        assertEquals(0, actions.materializations);
    }

    @Test public void obsoleteDestructiveBroadcastIsRejected() {
        RecordingActions actions = new RecordingActions();
        Intent intent = new Intent().setAction("de.thonktank.autosecretary.CLOSE")
                .putExtra("task_id", "task-7");

        assertThrows(IllegalArgumentException.class,
                () -> new TaskActionHandler(actions).handle(intent));

        assertEquals(0, actions.materializations);
    }

    private static final class RecordingActions implements TaskActionHandler.Actions {
        String completed;
        int materializations;
        @Override public void complete(String occurrenceId) { completed = occurrenceId; }
        @Override public void defer(String occurrenceId) { }
        @Override public void toggleStep(String stepId) { }
        @Override public void materializeDue() { materializations++; }
    }
}
