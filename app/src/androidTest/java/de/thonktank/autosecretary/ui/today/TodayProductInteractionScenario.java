package de.thonktank.autosecretary.ui.today;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
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
            expected.remove(b.key());
            expected.add(0, b.key());

            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                AtomicReference<MainActivity> activity = new AtomicReference<>();
                scenario.onActivity(activity::set);
                View root = activity.get().getWindow().getDecorView();
                awaitFocus(root, firstTitle);
                View title = awaitView(root, view -> (secondTitle + ", jetzt bearbeiten")
                        .contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription()));
                Rect bounds = reveal(activity.get(), title);
                TouchGestureDriver touch = new TouchGestureDriver(instrumentation, title);
                try {
                    touch.down(new int[] {bounds.centerX(), bounds.centerY()});
                    touch.up();
                } finally { touch.up(); }
                awaitFocus(root, secondTitle);
                assertEquals(expected, keys(queue(container)));

                scenario.recreate();
                scenario.onActivity(activity::set);
                root = activity.get().getWindow().getDecorView();
                awaitFocus(root, secondTitle);
                View later = awaitView(root, view -> view instanceof TextView
                        && context.getString(R.string.action_later).contentEquals(((TextView) view).getText()));
                reveal(activity.get(), later);
                instrumentation.runOnMainSync(() -> assertTrue(later.performAccessibilityAction(
                        AccessibilityNodeInfo.ACTION_CLICK, null)));
                awaitFocus(root, firstTitle);
                assertEquals(a.key(), queue(container).get(0).key());
                assertTrue("Later must retain B as open work", keys(queue(container)).contains(b.key()));
                List<String> afterLater = keys(queue(container));
                scenario.recreate();
                scenario.onActivity(activity::set);
                awaitFocus(activity.get().getWindow().getDecorView(), firstTitle);
                assertEquals(afterLater, keys(queue(container)));
            }
        } finally {
            for (TaskId id : created) container.catalog.delete.execute(id);
        }
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
