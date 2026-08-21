package de.thonktank.autosecretary;

import de.thonktank.autosecretary.presentation.FocusStepUiModel;
import de.thonktank.autosecretary.presentation.RepetitionProgressUiModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.data.preferences.FocusStepLimit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class FocusTaskViewTest {
    @Test public void plannedRepetitionsAndNoteStayVisibleBelowTheExercise() {
        Context context = ApplicationProvider.getApplicationContext();
        FocusStepUiModel step = FocusStepUiModel.of("step-1", "Beinpresse",
                "3 × 12", "23 kg, Sitz 5", false,
                RepetitionProgressUiModel.sets(3, 12, Collections.emptyList()),
                0, 10, 0);
        TaskSnapshot task = new TaskSnapshot("gym", "occurrence-gym", "Gym",
                TaskSlot.MORNING, "heute am Morgen", "Beinpresse", Recurrence.DAILY,
                Collections.singletonList(step), 1, false, false, false, false, 0, 1L);

        FocusTaskView view = new FocusTaskView(context);
        DashboardEventSink actions = event -> { };
        view.bind(task, false, false, DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO),
                actions);

        assertTrue(visibleTexts(view).contains("23 kg, Sitz 5"));
        assertTrue(visibleTexts(view).contains("12"));
    }

    @Test public void stepRowOwnsRenderingAnchorAndIdBasedActions() {
        Context context = ApplicationProvider.getApplicationContext();
        FocusStepUiModel step = FocusStepUiModel.of("step-1", "Beinpresse",
                "3 × 12", "23 kg", false,
                RepetitionProgressUiModel.sets(3, 12, Collections.emptyList()),
                2, 15, 0);
        DashboardEventRecorder events = new DashboardEventRecorder();
        FocusStepRowView row = new FocusStepRowView(context);

        row.bind(step, true, DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO),
                RepetitionInputState.idle(), events);

        assertEquals("Beinpresse", row.renderedTitle().toString());
        assertEquals("23 kg", row.renderedSubtitle().toString());
        assertFalse(firstText(row, "Beinpresse").hasOnClickListeners());
        assertFalse(firstText(row, "23 kg").hasOnClickListeners());
        assertTrue(row.rewardAnchor().getContentDescription().toString()
                .contains("Satz 1 mit 12 Wiederholungen"));
        assertTrue(row.grainTextViews().size() >= 4);
        View plus = row.findViewById(R.id.rep_stepper_increment);
        assertTrue(plus.performClick());
        DashboardEvent.AdjustRepetition adjustment =
                events.last(DashboardEvent.AdjustRepetition.class);
        assertEquals("step-1", adjustment.stepId);
        assertEquals(1, adjustment.delta);
    }

    @Test public void singleRepetitionsConfirmOnceWhileDurationCompletesDirectly() {
        Context context = ApplicationProvider.getApplicationContext();
        DayPalette palette = DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO);
        FocusStepUiModel repetitions = FocusStepUiModel.of("reps", "Liegestütze",
                "20 Wdh.", "", false,
                RepetitionProgressUiModel.single(20, Collections.emptyList()),
                0, 10, 0);
        DashboardEventRecorder events = new DashboardEventRecorder();
        FocusStepRowView row = new FocusStepRowView(context);

        row.bind(repetitions, true, palette,
                RepetitionInputState.idle().adjust(repetitions, -3), events);

        assertTrue(row.editorVisible());
        View barsScroll = (View) row.findViewById(R.id.set_bars).getParent();
        assertEquals(View.GONE, barsScroll.getVisibility());
        row.rewardAnchor().performClick();
        assertEquals("reps", events.last(DashboardEvent.SubmitRepetition.class).stepId);

        FocusStepUiModel duration = FocusStepUiModel.of("duration", "Planke",
                "45 Sek.", "ruhig atmen", false,
                null, 0, 10, 0);
        row.bind(duration, true, palette, RepetitionInputState.idle(), events);

        assertFalse(row.editorVisible());
        row.rewardAnchor().performClick();
        assertEquals("duration", events.last(DashboardEvent.ToggleStep.class).stepId);
    }

    @Test public void reboundFutureRowAdvancesWithItsPlannedValueWithoutShowingEditor() {
        Context context = ApplicationProvider.getApplicationContext();
        DayPalette palette = DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO);
        FocusStepUiModel active = FocusStepUiModel.of("active", "Aktiv", "3 × 12", "",
                false, RepetitionProgressUiModel.sets(3, 12, Collections.singletonList(10)),
                0, 10, 0);
        FocusStepUiModel future = FocusStepUiModel.of("future", "Später", "3 × 12", "",
                false, RepetitionProgressUiModel.sets(3, 12, Collections.emptyList()),
                0, 10, 0);
        DashboardEventRecorder events = new DashboardEventRecorder();
        FocusStepRowView row = new FocusStepRowView(context);

        row.bind(active, true, palette, RepetitionInputState.idle(), events);
        assertTrue(row.rewardAnchor().isClickable());
        assertTrue(row.rewardAnchor().isFocusable());
        row.bind(future, false, palette, RepetitionInputState.idle(), events);

        assertTrue(row.rewardAnchor().isClickable());
        assertTrue(row.rewardAnchor().isFocusable());
        assertTrue(row.rewardAnchor().performClick());
        View barsScroll = (View) row.findViewById(R.id.set_bars).getParent();
        assertEquals(View.GONE, ((View) barsScroll.getParent()).getVisibility());
        assertEquals("future", events.last(DashboardEvent.AdvanceTodayStep.class).stepId);
    }

    @Test public void configuredLimitCountsFollowingStepsAndReportsTheRest() {
        Context context = ApplicationProvider.getApplicationContext();
        List<FocusStepUiModel> models = Arrays.asList(
                FocusStepUiModel.of("1", "Eins", false),
                FocusStepUiModel.of("2", "Zwei", false),
                FocusStepUiModel.of("3", "Drei", false),
                FocusStepUiModel.of("4", "Vier", false),
                FocusStepUiModel.of("5", "Fünf", false));
        TaskSnapshot task = new TaskSnapshot("routine", "today", "Routine",
                TaskSlot.MORNING, "", "Eins", Recurrence.DAILY, models, 5,
                false, false, false, false, 0, 1L);
        FocusTaskView view = new FocusTaskView(context);
        DashboardEventSink actions = event -> { };

        view.bind(task, false, false,
                DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO), FocusStepLimit.ONE,
                RepetitionInputState.idle(), actions);

        List<String> texts = visibleTexts(view);
        assertTrue(texts.contains("Eins"));
        assertTrue(texts.contains("Zwei"));
        assertTrue(texts.contains("3 weitere"));
        assertTrue(!texts.contains("Drei"));
        assertFalse(firstText(view, "3 weitere").isClickable());
        assertFalse(firstText(view, "3 weitere").hasOnClickListeners());
        List<DewDotView> dews = visibleDews(view);
        assertTrue(dews.get(0).isClickable());
        assertTrue(dews.get(1).getContentDescription() + " / " + dews.size(),
                dews.get(1).isClickable());

        view.bind(task, false, false,
                DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO), FocusStepLimit.THREE,
                RepetitionInputState.idle(), actions);

        texts = visibleTexts(view);
        assertTrue(texts.contains("Vier"));
        assertTrue(texts.contains("1 weitere"));
        assertTrue(!texts.contains("Fünf"));
    }

    @Test public void accessibilityActionsCanMoveTheCurrentStepBehindTheNextOne() {
        Context context = ApplicationProvider.getApplicationContext();
        List<FocusStepUiModel> models = Arrays.asList(
                FocusStepUiModel.of("first", "Eins", false),
                FocusStepUiModel.of("second", "Zwei", false));
        TaskSnapshot task = new TaskSnapshot("routine", "today", "Routine",
                TaskSlot.MORNING, "", "Eins", Recurrence.DAILY, models, 2,
                false, false, false, false, 0, 1L);
        DashboardEventRecorder events = new DashboardEventRecorder();
        FocusTaskView view = new FocusTaskView(context);
        view.bind(task, false, false,
                DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO), FocusStepLimit.AUTO,
                RepetitionInputState.idle(), events);

        FocusStepListLayout list = findFirst(view, FocusStepListLayout.class);
        View firstBody = list.visibleRows().get(0).stepBody();
        assertTrue(firstBody.performAccessibilityAction(
                R.id.action_today_step_down, null));
        DashboardEvent.MoveTodayStep move = events.last(DashboardEvent.MoveTodayStep.class);
        assertEquals("first", move.stepId);
        assertNull(move.beforeStepId);
    }

    @Test public void viewportMeasurementSafelyReducesAutomaticAndNumericLimits() {
        Context context = ApplicationProvider.getApplicationContext();
        TaskSnapshot task = longRoutine();
        FocusTaskView view = new FocusTaskView(context);
        DashboardEventSink actions = event -> { };
        DayPalette palette = DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO);
        int width = Math.round(330 * context.getResources().getDisplayMetrics().density);
        int shortHeight = Math.round(430 * context.getResources().getDisplayMetrics().density);
        int tallHeight = Math.round(720 * context.getResources().getDisplayMetrics().density);

        view.bind(task, false, false, palette, FocusStepLimit.FIVE,
                RepetitionInputState.idle(), actions);
        measureExactly(view, width, shortHeight);
        int shortManual = ViewTestQueries.visibleFollowingStepRows(view);
        assertTrue(shortManual < 5);

        view.bind(task, false, false, palette, FocusStepLimit.AUTO,
                RepetitionInputState.idle(), actions);
        measureExactly(view, width, tallHeight);
        int tallAutomatic = ViewTestQueries.visibleFollowingStepRows(view);
        assertTrue(tallAutomatic > shortManual);

        view.bind(task, false, false, palette, FocusStepLimit.ONE,
                RepetitionInputState.idle(), actions);
        measureExactly(view, width, tallHeight);
        assertEquals(1, ViewTestQueries.visibleFollowingStepRows(view));
    }

    private static TaskSnapshot longRoutine() {
        List<FocusStepUiModel> models = new ArrayList<>();
        models.add(FocusStepUiModel.of("active", "Kniebeugen", "3 × 12",
                "Hantel 10 kg, langsam runter", false,
                RepetitionProgressUiModel.sets(3, 12, Collections.singletonList(12)),
                0, 10, 0));
        for (int index = 1; index <= 5; index++)
            models.add(FocusStepUiModel.of("future-" + index, "Folgeschritt " + index,
                    "12 Wdh.", "Eine lange unveränderte Notiz für zwei Zeilen", false,
                    RepetitionProgressUiModel.single(12, Collections.emptyList()),
                    0, 10, 0));
        return new TaskSnapshot("routine-long", "today-long", "Lange Routine",
                TaskSlot.MORNING, "", "Kniebeugen", Recurrence.DAILY, models, models.size(),
                false, false, false, false, 0, 1L);
    }

    private static void measureExactly(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private static <T extends View> T findFirst(View root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                T match = findFirst(group.getChildAt(index), type);
                if (match != null) return match;
            }
        }
        return null;
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

    private static TextView firstText(View root, String expected) {
        if (root instanceof TextView
                && expected.contentEquals(((TextView) root).getText())) return (TextView) root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView found = firstText(group.getChildAt(index), expected);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static List<DewDotView> visibleDews(View root) {
        List<DewDotView> result = new ArrayList<>();
        collectDews(root, result);
        return result;
    }

    private static void collectDews(View root, List<DewDotView> result) {
        if (root.getVisibility() != View.VISIBLE) return;
        if (root instanceof DewDotView) result.add((DewDotView) root);
        if (!(root instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) root;
        for (int index = 0; index < group.getChildCount(); index++)
            collectDews(group.getChildAt(index), result);
    }
}
