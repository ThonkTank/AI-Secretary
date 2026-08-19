package de.thonktank.autosecretary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalTime;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class TaskStepsEditorViewTest {
    @Test public void addActionEmitsTheReducedStateThroughTheComponentBoundary() {
        Context context = ApplicationProvider.getApplicationContext();
        EditorUiState[] emitted = new EditorUiState[1];
        TaskStepsEditorView view = new TaskStepsEditorView(context, new UiStyle(context),
                EditorUiState.create(), DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO),
                (state, rerender) -> emitted[0] = state);

        assertEquals(2, view.getChildCount());
        View add = view.getChildAt(1);
        add.performClick();

        assertEquals(1, emitted[0].stepStates.size());
        assertEquals("draft:1", emitted[0].expandedStepId);
        assertTrue(emitted[0].dirty);
    }
}
