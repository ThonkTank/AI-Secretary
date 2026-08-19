package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.TaskStepUiModel;
import de.thonktank.autosecretary.presentation.RepetitionProgressUiModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.data.preferences.FocusStepLimit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class FocusTaskViewTest {
    @Test public void plannedRepetitionsAndNoteStayVisibleBelowTheExercise() {
        Context context = ApplicationProvider.getApplicationContext();
        TaskStepUiModel step = new TaskStepUiModel("step-1", "Beinpresse",
                "3 × 12 Wdh. · 23 kg, Sitz 5", "3 × 12", "23 kg, Sitz 5", false,
                RepetitionProgressUiModel.sets(3, 12, Collections.emptyList()),
                0, 10, 0);
        TaskSnapshot task = new TaskSnapshot("gym", "occurrence-gym", "Gym",
                TaskSlot.MORNING, "heute am Morgen", "Beinpresse", Recurrence.DAILY,
                Collections.singletonList(step), 1, false, false, false, false, 0, 1L);

        FocusTaskView view = new FocusTaskView(context);
        NoOpActions actions = new NoOpActions();
        view.bind(task, false, false, DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO),
                actions, actions);

        assertTrue(visibleTexts(view).contains("23 kg, Sitz 5"));
        assertTrue(visibleTexts(view).contains("12"));
    }

    @Test public void stepRowOwnsRenderingAnchorAndIdBasedActions() {
        Context context = ApplicationProvider.getApplicationContext();
        TaskStepUiModel step = new TaskStepUiModel("step-1", "Beinpresse",
                "3 × 12 Wdh. · 23 kg", "3 × 12", "23 kg", false,
                RepetitionProgressUiModel.sets(3, 12, Collections.emptyList()),
                2, 15, 0);
        AtomicReference<String> changed = new AtomicReference<>();
        FocusTestActions actions = new FocusTestActions() {
            @Override public void onRepetitionInputStateChanged(
                    RepetitionInputState state) {
                changed.set(state.stepId);
            }
        };
        FocusStepRowView row = new FocusStepRowView(context);

        row.bind(step, true, DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO),
                RepetitionInputState.idle(), actions);

        assertEquals("Beinpresse", row.renderedTitle().toString());
        assertEquals("23 kg", row.renderedSubtitle().toString());
        assertTrue(row.rewardAnchor().getContentDescription().toString()
                .contains("Satz 1 mit 12 Wiederholungen"));
        assertTrue(row.grainTextViews().size() >= 4);
        View plus = row.findViewById(R.id.rep_stepper_increment);
        assertTrue(plus.performClick());
        assertEquals("step-1", changed.get());
    }

    @Test public void singleRepetitionsConfirmOnceWhileDurationCompletesDirectly() {
        Context context = ApplicationProvider.getApplicationContext();
        DayPalette palette = DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO);
        TaskStepUiModel repetitions = new TaskStepUiModel("reps", "Liegestütze",
                "20 Wdh.", "20 Wdh.", "", false,
                RepetitionProgressUiModel.single(20, Collections.emptyList()),
                0, 10, 0);
        AtomicReference<Integer> confirmed = new AtomicReference<>();
        AtomicReference<String> toggled = new AtomicReference<>();
        FocusTestActions actions = new FocusTestActions() {
            @Override public void onConfirmRepetitions(String stepId, int value) {
                confirmed.set(value);
            }

            @Override public void onToggleStep(String stepId) { toggled.set(stepId); }
        };
        FocusStepRowView row = new FocusStepRowView(context);

        row.bind(repetitions, true, palette,
                RepetitionInputState.idle().adjust(repetitions, -3), actions);

        assertTrue(row.editorVisible());
        View barsScroll = (View) row.findViewById(R.id.set_bars).getParent();
        assertEquals(View.GONE, barsScroll.getVisibility());
        row.rewardAnchor().performClick();
        assertEquals(Integer.valueOf(17), confirmed.get());

        TaskStepUiModel duration = new TaskStepUiModel("duration", "Planke",
                "45 Sek. · ruhig atmen", "45 Sek.", "ruhig atmen", false,
                null, 0, 10, 0);
        row.bind(duration, true, palette, RepetitionInputState.idle(), actions);

        assertFalse(row.editorVisible());
        row.rewardAnchor().performClick();
        assertEquals("duration", toggled.get());
    }

    @Test public void configuredLimitCountsFollowingStepsAndReportsTheRest() {
        Context context = ApplicationProvider.getApplicationContext();
        List<TaskStepUiModel> models = Arrays.asList(
                new TaskStepUiModel("1", "Eins", false),
                new TaskStepUiModel("2", "Zwei", false),
                new TaskStepUiModel("3", "Drei", false),
                new TaskStepUiModel("4", "Vier", false),
                new TaskStepUiModel("5", "Fünf", false));
        TaskSnapshot task = new TaskSnapshot("routine", "today", "Routine",
                TaskSlot.MORNING, "", "Eins", Recurrence.DAILY, models, 5,
                false, false, false, false, 0, 1L);
        FocusTaskView view = new FocusTaskView(context);
        NoOpActions actions = new NoOpActions();

        view.bind(task, false, false,
                DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO), FocusStepLimit.ONE,
                RepetitionInputState.idle(), actions, actions);

        List<String> texts = visibleTexts(view);
        assertTrue(texts.contains("Eins"));
        assertTrue(texts.contains("Zwei"));
        assertTrue(texts.contains("3 weitere"));
        assertTrue(!texts.contains("Drei"));

        view.bind(task, false, false,
                DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO), FocusStepLimit.THREE,
                RepetitionInputState.idle(), actions, actions);

        texts = visibleTexts(view);
        assertTrue(texts.contains("Vier"));
        assertTrue(texts.contains("1 weitere"));
        assertTrue(!texts.contains("Fünf"));
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
