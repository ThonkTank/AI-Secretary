package de.thonktank.autosecretary.ui.today;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import de.thonktank.autosecretary.*;
import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.domain.today.TodayQueue;
import de.thonktank.autosecretary.domain.usecase.MoveTodayItem;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/** Production MainActivity, renderer, ViewModel, worker and Room; only setup/cleanup bypass the UI. */
final class TodayProductInteractionScenario {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    void execute() {
        Context context = instrumentation.getTargetContext();
        // This fixture must never seed or delete tasks in a production installation.
        assertEquals("de.thonktank.autosecretary.test", context.getPackageName());
        AppContainer container = AutoSecretaryApplication.from(context).container();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String firstTitle = "Heute A " + suffix;
        String secondTitle = "Heute B " + suffix;
        List<TaskId> created = new ArrayList<>();
        try {
            TaskId first = container.catalog.create.execute(definition(firstTitle));
            created.add(first);
            TaskId second = container.catalog.create.execute(definition(secondTitle));
            created.add(second);
            container.today.materializeDue.execute();
            List<TodayQueue.Entry> initial = queue(container);
            TodayQueue.Entry a = initial.stream().filter(e -> e.task != null && e.task.task.id.equals(first))
                    .findFirst().orElseThrow(AssertionError::new);
            TodayQueue.Entry b = initial.stream().filter(e -> e.task != null && e.task.task.id.equals(second))
                    .findFirst().orElseThrow(AssertionError::new);
            assertTrue(container.today.moveItem.execute(b.kind, b.id, MoveTodayItem.Action.FIRST));
            assertTrue(container.today.moveItem.execute(a.kind, a.id, MoveTodayItem.Action.FIRST));
            List<String> expected = keys(queue(container));
            UiDevice device = UiDevice.getInstance(instrumentation);

            try (ProductActivitySession scenario = new ProductActivitySession(instrumentation)) {
                MainActivity activity = scenario.launch();
                View root = activity.getWindow().getDecorView();
                View title = awaitView(root, view -> context.getString(R.string.today_open_task, firstTitle)
                        .contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription()));
                Rect bounds = reveal(activity, title);
                TouchGestureDriver touch = new TouchGestureDriver(instrumentation, title);
                try {
                    touch.down(new int[] {bounds.centerX(), bounds.centerY()});
                    touch.up();
                } finally { touch.up(); }
                awaitFocus(root, firstTitle);

                object(device, By.res(context.getPackageName(), "today_choose_task")).click();
                object(device, By.text(secondTitle)).click();
                awaitFocus(root, secondTitle);
                assertEquals(expected, keys(queue(container)));

                activity = scenario.recreate();
                root = activity.getWindow().getDecorView();
                awaitFocus(root, secondTitle);
                for (String step : List.of("Messer holen", "Gemüse schneiden", "Reis aufsetzen")) {
                    object(device, By.res(context.getPackageName(), "today_add_step")).click();
                    object(device, By.res(context.getPackageName(), "today_step_name_field")).setText(step);
                    object(device, By.text(context.getString(R.string.today_step_submit))).click();
                    object(device, By.text(step));
                }
                object(device, By.res(context.getPackageName(), "today_sort_steps")).click();
                object(device, By.desc(context.getString(R.string.today_sort_step_up, "Gemüse schneiden"))).click();
                Rect from = object(device, By.desc(context.getString(R.string.today_drag_step, "Reis aufsetzen")))
                        .getVisibleBounds();
                Rect to = object(device, By.desc(context.getString(R.string.today_drag_step, "Messer holen")))
                        .getVisibleBounds();
                assertTrue(device.swipe(from.centerX(), from.centerY(), to.centerX(), to.top + to.height() / 4, 25));
                object(device, By.text(context.getString(R.string.today_finish_sort))).click();
                View sortedRoot = root;
                PresentationAwaiter.await(instrumentation, "Step order not displayed", () -> {
                    View rice = find(sortedRoot, view -> view instanceof TextView
                            && "Reis aufsetzen".contentEquals(((TextView) view).getText()));
                    View knife = find(sortedRoot, view -> view instanceof TextView
                            && "Messer holen".contentEquals(((TextView) view).getText()));
                    if (rice == null || knife == null) return false;
                    int[] ricePosition = new int[2];
                    int[] knifePosition = new int[2];
                    rice.getLocationOnScreen(ricePosition);
                    knife.getLocationOnScreen(knifePosition);
                    return ricePosition[1] < knifePosition[1];
                }, sortedRoot);
                List<de.thonktank.autosecretary.data.local.OccurrenceStepEntity> sorted =
                        container.database.steps().occurrenceSteps(b.id);
                assertEquals(List.of("Gemüse schneiden", "Reis aufsetzen", "Messer holen"),
                        sorted.stream().map(step -> step.text).collect(java.util.stream.Collectors.toList()));

                object(device, By.res(context.getPackageName(), "today_back_to_overview")).click();
                object(device, By.desc(context.getString(R.string.today_hide_task, secondTitle))).click();
                object(device, By.text(context.getString(R.string.today_hidden_feedback, secondTitle)));
                assertFalse(keys(queue(container)).contains(b.key()));
                object(device, By.text(context.getString(R.string.action_undo))).click();
                object(device, By.desc(context.getString(R.string.today_hide_task, secondTitle))).click();
                object(device, By.text(context.getString(R.string.today_hidden_feedback, secondTitle)));

                scenario.recreate();
                object(device, By.text(context.getString(R.string.today_hidden_group, 1))).click();
                object(device, By.desc(context.getString(R.string.today_restore_task, secondTitle))).click();
                object(device, By.descStartsWith(context.getString(R.string.today_open_task, secondTitle)));
                assertEquals(expected, keys(queue(container)));
            }

        } finally {
            for (TaskId id : created) container.catalog.delete.execute(id);
        }
    }

    private static UiObject2 object(UiDevice device, BySelector selector) {
        UiObject2 found = device.wait(Until.findObject(selector), 12_000);
        assertNotNull("Missing " + selector, found);
        return found;
    }

    private static TaskDefinition definition(String title) {
        return new TaskDefinition(title, null, TaskSlot.MORNING, Recurrence.ONCE, 1, 0, 0,
                TaskBoundKind.FOREVER, null, null, null, null, "", Collections.emptyList());
    }
    private static List<TodayQueue.Entry> queue(AppContainer container) {
        return TodayQueue.visible(container.today.loadDashboard.execute(container.clock.today()), container.clock.today());
    }
    private static List<String> keys(List<TodayQueue.Entry> entries) {
        List<String> result = new ArrayList<>();
        for (TodayQueue.Entry entry : entries) result.add(entry.key());
        return result;
    }
    private void awaitFocus(View root, String title) {
        PresentationAwaiter.await(instrumentation, "Expected product focus: " + title, () -> {
            View card = find(root, view -> view instanceof FocusCardView);
            return card != null && find(card, view -> view instanceof TextView
                    && title.contentEquals(((TextView) view).getText())) != null;
        }, root);
    }
    private View awaitView(View root, Predicate<View> match) {
        AtomicReference<View> result = new AtomicReference<>();
        PresentationAwaiter.await(instrumentation, "Expected product action", () -> {
            result.set(find(root, match));
            return result.get() != null;
        }, root);
        return result.get();
    }
    private Rect reveal(MainActivity activity, View view) {
        PresentationAwaiter.await(instrumentation, "Action was not laid out",
                () -> view.getWidth() > 0 && view.getHeight() > 0,
                activity.getWindow().getDecorView(), view);
        instrumentation.runOnMainSync(() -> view.requestRectangleOnScreen(
                new Rect(0, 0, view.getWidth(), view.getHeight()), true));
        Rect bounds = new Rect();
        PresentationAwaiter.await(instrumentation, "Action did not become touch reachable", () -> {
            Rect window = new Rect();
            activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(window);
            return activity.hasWindowFocus() && view.isAttachedToWindow() && view.isShown()
                    && view.getGlobalVisibleRect(bounds) && bounds.intersect(window)
                    && bounds.width() >= 3 && bounds.height() >= 3;
        }, activity.getWindow().getDecorView(), view);
        return bounds;
    }
    private static View find(View view, Predicate<View> match) {
        if (view.getVisibility() != View.VISIBLE) return null;
        if (match.test(view)) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View result = find(((ViewGroup) view).getChildAt(i), match);
            if (result != null) return result;
        }
        return null;
    }
}
