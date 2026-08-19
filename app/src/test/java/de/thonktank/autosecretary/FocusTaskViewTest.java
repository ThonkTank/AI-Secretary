package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.TaskStepUiModel;
import de.thonktank.autosecretary.presentation.SetProgressUiModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class FocusTaskViewTest {
    @Test public void plannedRepetitionsAndNoteStayVisibleBelowTheExercise() {
        Context context = ApplicationProvider.getApplicationContext();
        TaskStepUiModel step = new TaskStepUiModel("step-1", "Beinpresse",
                "3 × 12 Wdh. · 23 kg, Sitz 5", false,
                new SetProgressUiModel(3, 12, "23 kg, Sitz 5", Collections.emptyList()),
                0, 10, 0);
        TaskSnapshot task = new TaskSnapshot("gym", "occurrence-gym", "Gym",
                TaskSlot.MORNING, "heute am Morgen", "Beinpresse", Recurrence.DAILY,
                Collections.singletonList(step), 1, false, false, false, false, 0, 1L);

        FocusTaskView view = new FocusTaskView(context);
        NoOpActions actions = new NoOpActions();
        view.bind(task, false, false, DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO),
                actions, actions);

        assertTrue(visibleTexts(view).contains("3 × 12 Wdh. · 23 kg, Sitz 5"));
    }

    @Test public void stepRowOwnsRenderingAnchorAndIdBasedActions() {
        Context context = ApplicationProvider.getApplicationContext();
        TaskStepUiModel step = new TaskStepUiModel("step-1", "Beinpresse",
                "3 × 12 Wdh. · 23 kg", false,
                new SetProgressUiModel(3, 12, "23 kg", Collections.emptyList()),
                2, 15, 0);
        AtomicReference<String> changed = new AtomicReference<>();
        FocusTestActions actions = new FocusTestActions() {
            @Override public void onSetProgressEditorStateChanged(
                    SetProgressEditorState state) {
                changed.set(state.expandedStepId);
            }
        };
        FocusStepRowView row = new FocusStepRowView(context);

        row.bind(step, DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO),
                SetProgressEditorState.closed(), actions);

        assertEquals("Beinpresse", row.renderedTitle().toString());
        assertEquals("3 × 12 Wdh. · 23 kg", row.renderedSubtitle().toString());
        assertTrue(row.rewardAnchor().getContentDescription().toString()
                .contains("Beinpresse, 3 × 12 Wdh. · 23 kg"));
        assertEquals(2, row.grainTextViews().size());
        assertTrue(row.rewardAnchor().performClick());
        assertEquals("step-1", changed.get());
    }

    private static List<String> visibleTexts(View root) {
        List<String> values = new ArrayList<>();
        if (root.getVisibility() != View.VISIBLE) return values;
        if (root instanceof TextView) values.add(((TextView) root).getText().toString());
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++)
                values.addAll(visibleTexts(group.getChildAt(index)));
        }
        return values;
    }

    private static final class NoOpActions extends FocusTestActions { }
}
