package de.thonktank.autosecretary;

import de.thonktank.autosecretary.ui.today.*;

import de.thonktank.autosecretary.presentation.today.FocusStepUiModel;
import de.thonktank.autosecretary.presentation.today.RepetitionProgressUiModel;
import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;
import de.thonktank.autosecretary.presentation.today.TodayAction;
import de.thonktank.autosecretary.presentation.today.TodayActionSink;
import de.thonktank.autosecretary.presentation.today.TodayFeatureState;
import de.thonktank.autosecretary.presentation.today.TodayReducer;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowPopupMenu;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.thonktank.autosecretary.domain.model.Recurrence;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.timer.TimerManager;
import de.thonktank.autosecretary.timer.TimerSession;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class FocusTaskViewTest {
    @Test public void plannedRepetitionsAndNoteStayVisibleBelowTheExercise() {
        Context context = ApplicationProvider.getApplicationContext();
        FocusStepUiModel step = FocusTaskFixtures.step("step-1", "Beinpresse")
                .amount("3 × 12").note("23 kg, Sitz 5")
                .repetition(RepetitionProgressUiModel.sets(
                        3, 12, Collections.emptyList())).build();
        FocusTaskUiModel task = FocusTaskFixtures.task("gym", "Gym")
                .occurrence("occurrence-gym").slot(TaskSlot.MORNING)
                .recurrence(Recurrence.DAILY).steps(Collections.singletonList(step)).build();

        FocusTaskView view = new FocusTaskView(context);
        TodayActionSink actions = event -> { };
        view.bind(task, false, DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO),
                actions);

        assertTrue(visibleTexts(view).contains("23 kg, Sitz 5"));
        assertTrue(visibleTexts(view).contains("12"));
    }

    @Test public void stepRowOwnsRenderingAnchorAndIdBasedActions() {
        Context context = ApplicationProvider.getApplicationContext();
        FocusStepUiModel step = FocusTaskFixtures.step("step-1", "Beinpresse")
                .amount("3 × 12").note("23 kg")
                .repetition(RepetitionProgressUiModel.sets(
                        3, 12, Collections.emptyList())).combo(1).build();
        DashboardEventRecorder events = new DashboardEventRecorder();
        FocusStepRowView row = new FocusStepRowView(context);

        row.bind(step, true, DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO),
                RepetitionInputState.idle(), events);

        assertTrue(visibleTexts(row).contains("Beinpresse"));
        assertTrue(visibleTexts(row).contains("23 kg"));
        assertFalse(firstText(row, "Beinpresse").hasOnClickListeners());
        assertFalse(firstText(row, "23 kg").hasOnClickListeners());
        assertTrue(row.rewardAnchor().getContentDescription().toString()
                .contains("Satz 1 mit 12 Wiederholungen"));
        assertTrue(row.grainTextViews().size() >= 4);
        View plus = row.findViewById(R.id.rep_stepper_increment);
        assertTrue(plus.performClick());
        TodayAction adjustment = events.lastToday(TodayAction.Kind.ADJUST_REPETITION);
        assertEquals("step-1", adjustment.id);
        assertEquals(1, adjustment.value);
    }

    @Test public void quantitativeStepOffersTheTrailingFinishForTodayMenu() {
        Context context = ApplicationProvider.getApplicationContext();
        FocusStepUiModel step = FocusTaskFixtures.step("step-1", "Beinpresse")
                .amount("3 × 12").repetition(RepetitionProgressUiModel.sets(
                        3, 12, Collections.singletonList(10))).build();
        DashboardEventRecorder events = new DashboardEventRecorder();
        FocusStepRowView row = new FocusStepRowView(context);
        row.bind(step, true, DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO),
                RepetitionInputState.idle(), events);

        View menu = findByContentDescription(row,
                context.getString(R.string.content_step_actions, "Beinpresse"));
        assertTrue(menu != null);
        assertTrue(menu.getLayoutParams().width >= new UiStyle(context).dp(48));
        assertTrue(menu.performClick());
        android.widget.PopupMenu popup = ShadowPopupMenu.getLatestPopupMenu();
        assertTrue(popup != null);
        assertEquals("Für heute abschließen", popup.getMenu().getItem(0).getTitle().toString());
        assertTrue(((ShadowPopupMenu) Shadow.extract(popup)).getOnMenuItemClickListener()
                .onMenuItemClick(popup.getMenu().getItem(0)));
        assertEquals("step-1", events.lastToday(TodayAction.Kind.FINISH_STEP).id);
    }

    @Test public void singleRepetitionsConfirmOnceWhileDurationCompletesDirectly() {
        Context context = ApplicationProvider.getApplicationContext();
        DayPalette palette = DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO);
        FocusStepUiModel repetitions = FocusTaskFixtures.step("reps", "Liegestütze")
                .amount("20 Wdh.").repetition(RepetitionProgressUiModel.single(
                        20, Collections.emptyList())).build();
        DashboardEventRecorder events = new DashboardEventRecorder();
        FocusStepRowView row = new FocusStepRowView(context);

        row.bind(repetitions, true, palette,
                RepetitionInputState.idle().adjust(repetitions, -3), events);

        assertEquals(View.VISIBLE, ((View) barsScrollParent(row)).getVisibility());
        View barsScroll = (View) row.findViewById(R.id.set_bars).getParent();
        assertEquals(View.GONE, barsScroll.getVisibility());
        row.rewardAnchor().performClick();
        assertEquals("reps", events.lastToday(TodayAction.Kind.SUBMIT_REPETITION).id);

        FocusStepUiModel duration = FocusTaskFixtures.step("duration", "Planke")
                .amount("45 Sek.").note("ruhig atmen").build();
        row.bind(duration, true, palette, RepetitionInputState.idle(), events);

        assertEquals(View.GONE, ((View) barsScrollParent(row)).getVisibility());
        row.rewardAnchor().performClick();
        assertEquals("duration", events.lastToday(TodayAction.Kind.TOGGLE_STEP).id);
    }

    @Test public void durationStartsExplicitlyAndRunningRestCanBeSkipped() {
        Context context = ApplicationProvider.getApplicationContext();
        DayPalette palette = DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO);
        DashboardEventRecorder events = new DashboardEventRecorder();
        FocusStepRowView row = new FocusStepRowView(context);
        FocusStepUiModel duration = FocusTaskFixtures.step("duration", "Laufen")
                .amount("10 Min.").build().withDurationSeconds(600);

        row.bind(duration, true, palette, RepetitionInputState.idle(),
                TimerManager.Snapshot.empty(), events);
        assertTrue(visibleTexts(row).contains("10:00"));
        assertTrue(firstText(row, "Start").performClick());
        TodayAction start = events.lastToday(TodayAction.Kind.START_DURATION_TIMER);
        assertEquals("duration", start.id);
        assertEquals(600, start.value);

        FocusStepUiModel sets = FocusTaskFixtures.step("sets", "Liegestütze")
                .amount("3 × 12").repetition(RepetitionProgressUiModel.sets(
                        3, 12, Collections.singletonList(12))).build();
        TimerSession rest = new TimerSession("rest:sets", "sets", "Liegestütze",
                TimerSession.Kind.REST, TimerSession.State.RUNNING, 60, 60_000,
                160_000, 260_000, 123, false);
        row.bind(sets, true, palette, RepetitionInputState.idle(),
                TimerManager.Snapshot.of(Collections.singletonList(rest), 100_000,
                        true, true), events);

        assertFalse(row.rewardAnchor().isClickable());
        assertTrue(visibleTexts(row).contains("1:00"));
        assertTrue(firstText(row, "Pause überspringen").performClick());
        assertEquals("rest:sets", events.lastToday(TodayAction.Kind.RESET_TIMER).id);
    }

    @Test public void reboundFutureRowAdvancesWithItsPlannedValueWithoutShowingEditor() {
        Context context = ApplicationProvider.getApplicationContext();
        DayPalette palette = DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO);
        FocusStepUiModel active = FocusTaskFixtures.step("active", "Aktiv")
                .amount("3 × 12").repetition(RepetitionProgressUiModel.sets(
                        3, 12, Collections.singletonList(10))).build();
        FocusStepUiModel future = FocusTaskFixtures.step("future", "Später")
                .amount("3 × 12").repetition(RepetitionProgressUiModel.sets(
                        3, 12, Collections.emptyList())).available().build();
        DashboardEventRecorder events = new DashboardEventRecorder();
        FocusStepRowView row = new FocusStepRowView(context);

        row.bind(active, true, palette, RepetitionInputState.idle(), events);
        assertTrue(row.rewardAnchor().isClickable());
        assertTrue(row.rewardAnchor().isFocusable());
        row.bind(future, false, palette, RepetitionInputState.idle(), events);

        assertTrue(row.rewardAnchor().isClickable());
        assertTrue(row.rewardAnchor().isFocusable());
        AccessibilityNodeInfo info = row.rewardAnchor().createAccessibilityNodeInfo();
        assertEquals(android.widget.Button.class.getName(), info.getClassName());
        assertTrue(info.isCheckable());
        assertTrue(row.rewardAnchor().getContentDescription().toString()
                .contains("Planwert 12"));
        info.recycle();
        assertTrue(row.rewardAnchor().performClick());
        View barsScroll = (View) row.findViewById(R.id.set_bars).getParent();
        assertEquals(View.GONE, ((View) barsScroll.getParent()).getVisibility());
        assertEquals("future", events.lastToday(TodayAction.Kind.ADVANCE_STEP).id);
    }

    @Test public void configuredLimitCountsFollowingStepsAndReportsTheRest() {
        Context context = ApplicationProvider.getApplicationContext();
        List<FocusStepUiModel> models = Arrays.asList(
                FocusStepUiModel.of("1", "Eins", false),
                FocusStepUiModel.of("2", "Zwei", false),
                FocusStepUiModel.of("3", "Drei", false),
                FocusStepUiModel.of("4", "Vier", false),
                FocusStepUiModel.of("5", "Fünf", false));
        FocusTaskUiModel task = FocusTaskFixtures.task("routine", "Routine")
                .occurrence("today").recurrence(Recurrence.DAILY).steps(models).build();
        FocusTaskView view = new FocusTaskView(context);
        TodayActionSink actions = event -> { };

        view.bind(task, false,
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

        view.bind(task, false,
                DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO), FocusStepLimit.THREE,
                RepetitionInputState.idle(), actions);

        texts = visibleTexts(view);
        assertTrue(texts.contains("Vier"));
        assertTrue(texts.contains("1 weitere"));
        assertTrue(!texts.contains("Fünf"));
    }

    @Test public void accessibilityExposesAndEmitsAllTodayMoveActions() {
        Context context = ApplicationProvider.getApplicationContext();
        List<FocusStepUiModel> models = Arrays.asList(
                FocusStepUiModel.of("first", "Eins", false),
                FocusStepUiModel.of("second", "Zwei", false),
                FocusStepUiModel.of("third", "Drei", false),
                FocusStepUiModel.of("fourth", "Vier", false));
        FocusTaskUiModel task = FocusTaskFixtures.task("routine", "Routine")
                .occurrence("today").recurrence(Recurrence.DAILY).steps(models).build();
        DashboardEventRecorder events = new DashboardEventRecorder();
        FocusTaskView view = new FocusTaskView(context);
        TodayUiModel today = new TodayUiModel(
                new de.thonktank.autosecretary.domain.model.XpProgress(0), task,
                Collections.emptyList(), Collections.emptyList());
        bindFeature(view, TodayFeatureState.idle(today), FocusStepLimit.AUTO, events);

        View second = stepBody(view, context, "Zwei");
        AccessibilityNodeInfo secondInfo = second.createAccessibilityNodeInfo();
        assertTrue(hasAction(secondInfo, R.id.action_today_step_up));
        assertTrue(hasAction(secondInfo, R.id.action_today_step_down));
        assertTrue(hasAction(secondInfo, R.id.action_today_step_front));
        assertTrue(hasAction(secondInfo, R.id.action_today_step_back));
        secondInfo.recycle();

        assertMove(stepBody(view, context, "Zwei"), R.id.action_today_step_up,
                events, "second", "first");
        assertMove(stepBody(view, context, "Drei"), R.id.action_today_step_front,
                events, "third", "first");
        assertMove(stepBody(view, context, "Eins"), R.id.action_today_step_down,
                events, "first", "third");
        assertMove(stepBody(view, context, "Zwei"), R.id.action_today_step_back,
                events, "second", null);
    }

    @Test public void longPressAndPublicActionsShowPreviewCancelAndPersistOneDrop() {
        Context context = ApplicationProvider.getApplicationContext();
        List<FocusStepUiModel> models = Arrays.asList(
                FocusStepUiModel.of("first", "Eins", false),
                FocusStepUiModel.of("second", "Zwei", false),
                FocusStepUiModel.of("third", "Drei", false),
                FocusStepUiModel.of("fourth", "Vier", false));
        FocusTaskUiModel task = FocusTaskFixtures.task("routine", "Routine")
                .occurrence("today").recurrence(Recurrence.DAILY).steps(models).build();
        DashboardEventRecorder events = new DashboardEventRecorder();
        TodayUiModel today = new TodayUiModel(new de.thonktank.autosecretary.domain.model.XpProgress(0),
                task, Collections.emptyList(), Collections.emptyList());
        TodayReducer reducer = new TodayReducer();
        final TodayFeatureState[] state = {TodayFeatureState.idle(today)};
        final int[] commands = {0};
        de.thonktank.autosecretary.presentation.today.TodayActionSink sink = action -> {
            events.emit(action);
            TodayReducer.Result result;
            if (action.kind == TodayAction.Kind.BEGIN_REORDER)
                result = reducer.begin(state[0], action.id, action.order);
            else if (action.kind == TodayAction.Kind.PREVIEW_REORDER)
                result = reducer.preview(state[0], action.id, action.order);
            else if (action.kind == TodayAction.Kind.CANCEL_REORDER)
                result = reducer.cancel(state[0], action.id);
            else if (action.kind == TodayAction.Kind.DROP_REORDER)
                result = reducer.drop(state[0], action.id, action.relatedId, "command-1");
            else return;
            state[0] = result.state;
            if (result.command != null) commands[0]++;
        };
        FocusTaskView view = new FocusTaskView(context);
        bindFeature(view, state[0], FocusStepLimit.ONE, sink);
        // Robolectric does not establish a platform drag session, but the public long-click
        // still proves that the view emits Begin and compensating Cancel when start is refused.
        assertFalse(stepBody(view, context, "Vier").performLongClick());
        assertEquals(TodayAction.Kind.BEGIN_REORDER, events.todayActions().get(0).kind);
        assertEquals(TodayAction.Kind.CANCEL_REORDER, events.todayActions().get(1).kind);

        sink.emit(TodayAction.beginReorder("fourth",
                Arrays.asList("first", "second", "third", "fourth")));

        sink.emit(TodayAction.previewReorder("fourth",
                Arrays.asList("fourth", "first", "second", "third")));
        bindFeature(view, state[0], FocusStepLimit.ONE, sink);
        assertEquals(Arrays.asList("Vier", "Eins", "Zwei", "Drei"),
                visibleStepTitles(view));

        sink.emit(TodayAction.cancelReorder("fourth"));
        bindFeature(view, state[0], FocusStepLimit.ONE, sink);
        assertTrue(visibleStepTitles(view).containsAll(Arrays.asList("Eins", "Zwei")));
        assertFalse(visibleStepTitles(view).contains("Drei"));

        sink.emit(TodayAction.beginReorder("fourth",
                Arrays.asList("first", "second", "third", "fourth")));
        sink.emit(TodayAction.previewReorder("fourth",
                Arrays.asList("fourth", "first", "second", "third")));
        sink.emit(TodayAction.dropReorder("fourth", "first"));
        sink.emit(TodayAction.dropReorder("fourth", "first"));
        assertEquals(1, commands[0]);
    }

    @Test public void viewportMeasurementSafelyReducesAutomaticAndNumericLimits() {
        Context context = ApplicationProvider.getApplicationContext();
        FocusTaskUiModel task = longRoutine();
        FocusTaskView view = new FocusTaskView(context);
        TodayActionSink actions = event -> { };
        DayPalette palette = DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO);
        int width = Math.round(330 * context.getResources().getDisplayMetrics().density);
        int shortHeight = Math.round(430 * context.getResources().getDisplayMetrics().density);
        int tallHeight = Math.round(720 * context.getResources().getDisplayMetrics().density);

        view.bind(task, false, palette, FocusStepLimit.FIVE,
                RepetitionInputState.idle(), actions);
        measureExactly(view, width, shortHeight);
        int shortManual = ViewTestQueries.visibleFollowingStepRows(view);
        assertTrue(shortManual < 5);

        view.bind(task, false, palette, FocusStepLimit.AUTO,
                RepetitionInputState.idle(), actions);
        measureExactly(view, width, tallHeight);
        int tallAutomatic = ViewTestQueries.visibleFollowingStepRows(view);
        assertTrue(tallAutomatic > shortManual);

        view.bind(task, false, palette, FocusStepLimit.ONE,
                RepetitionInputState.idle(), actions);
        measureExactly(view, width, tallHeight);
        assertEquals(1, ViewTestQueries.visibleFollowingStepRows(view));
    }

    private static FocusTaskUiModel longRoutine() {
        List<FocusStepUiModel> models = new ArrayList<>();
        models.add(FocusTaskFixtures.step("active", "Kniebeugen")
                .amount("3 × 12").note("Hantel 10 kg, langsam runter")
                .repetition(RepetitionProgressUiModel.sets(
                        3, 12, Collections.singletonList(12))).build());
        for (int index = 1; index <= 5; index++)
            models.add(FocusTaskFixtures.step("future-" + index,
                            "Folgeschritt " + index).amount("12 Wdh.")
                    .note("Eine lange unveränderte Notiz für zwei Zeilen")
                    .repetition(RepetitionProgressUiModel.single(
                            12, Collections.emptyList())).build());
        return FocusTaskFixtures.task("routine-long", "Lange Routine")
                .occurrence("today-long").recurrence(Recurrence.DAILY)
                .steps(models).build();
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

    private static boolean hasAction(AccessibilityNodeInfo info, int actionId) {
        for (AccessibilityNodeInfo.AccessibilityAction action : info.getActionList())
            if (action.getId() == actionId) return true;
        return false;
    }

    private static void assertMove(View body, int actionId, DashboardEventRecorder events,
                                   String stepId, String beforeStepId) {
        int beforeCount = events.todayActions().size();
        assertTrue(body.performAccessibilityAction(actionId, null));
        assertEquals(beforeCount + 3, events.todayActions().size());
        assertEquals(TodayAction.Kind.BEGIN_REORDER,
                events.todayActions().get(beforeCount).kind);
        assertEquals(TodayAction.Kind.PREVIEW_REORDER,
                events.todayActions().get(beforeCount + 1).kind);
        TodayAction move = events.todayActions().get(beforeCount + 2);
        assertEquals(TodayAction.Kind.DROP_REORDER, move.kind);
        assertEquals(stepId, move.id);
        if (beforeStepId == null) assertNull(move.relatedId);
        else assertEquals(beforeStepId, move.relatedId);
    }

    private static Object barsScrollParent(FocusStepRowView row) {
        return ((View) row.findViewById(R.id.set_bars).getParent()).getParent();
    }

    private static View stepBody(View root, Context context, String title) {
        CharSequence expected = context.getString(R.string.a11y_today_step_row, title);
        View result = findByContentDescription(root, expected);
        if (result == null) throw new AssertionError("Missing step body " + title);
        return result;
    }

    private static View findByContentDescription(View root, CharSequence expected) {
        if (expected.equals(root.getContentDescription())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                View result = findByContentDescription(group.getChildAt(index), expected);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static void bindFeature(FocusTaskView view, TodayFeatureState state,
                                    FocusStepLimit limit,
                                    de.thonktank.autosecretary.presentation.today.TodayActionSink sink) {
        view.bind(state.today.focus, false,
                DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO), limit,
                RepetitionInputState.idle(), state.reorder, sink);
    }

    private static List<String> visibleStepTitles(View root) {
        List<String> result = new ArrayList<>();
        for (String value : visibleTexts(root))
            if (Arrays.asList("Eins", "Zwei", "Drei", "Vier").contains(value))
                result.add(value);
        return result;
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
