package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.TaskStepUiModel;
import de.thonktank.autosecretary.presentation.SetProgressUiModel;

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
        view.bind(task, false, false, DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO),
                new NoOpActions());

        assertTrue(visibleTexts(view).contains("3 × 12 Wdh. · 23 kg, Sitz 5"));
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

    private static final class NoOpActions implements FocusTaskView.Actions {
        @Override public void onComplete(TaskSnapshot task) { }
        @Override public void onDefer(TaskSnapshot task) { }
        @Override public void onToggleStep(TaskStepUiModel step) { }
    }
}
