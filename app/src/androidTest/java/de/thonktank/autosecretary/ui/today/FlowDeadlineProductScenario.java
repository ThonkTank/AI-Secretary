package de.thonktank.autosecretary.ui.today;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import de.thonktank.autosecretary.*;
import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.domain.today.TodayQueue;
import de.thonktank.autosecretary.domain.usecase.MoveTodayItem;
import de.thonktank.autosecretary.presentation.today.TodayViewModel;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Real MainActivity and Room. Fixture writes set up waits; only product events activate them. */
final class FlowDeadlineProductScenario {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    void execute(boolean background) {
        Context context = instrumentation.getTargetContext();
        assertEquals("de.thonktank.autosecretary.test", context.getPackageName());
        AppContainer container = AutoSecretaryApplication.from(context).container();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String title = "Warteablauf " + suffix, seed = "Start " + suffix, next = "Weiter " + suffix;
        FlowEditorDraft draft = FlowEditorDraft.empty().rename(title)
                .addStep(seed, FlowDelayPolicy.fixed(300_000)).addStep(next, FlowDelayPolicy.fixed(0));
        draft = draft.withGraph(draft.graph.join(List.of(draft.graph.stepIds.get(0)), draft.graph.stepIds.get(1)));
        TaskId task = container.flows.saveGraph.execute(draft.edit());
        try {
            container.today.materializeDue.execute();
            TodayQueue.Entry entry = TodayQueue.visible(dashboard(container), container.clock.today()).stream()
                    .filter(e -> e.sheet != null && e.sheet.entries.stream().anyMatch(candidate ->
                            candidate.candidateTemplate != null && candidate.candidateTemplate.taskId.equals(task)))
                    .findFirst().orElseThrow(AssertionError::new);
            String candidate = entry.sheet.entries.get(0).targetId;
            container.today.moveItem.execute(entry.kind, entry.id, MoveTodayItem.Action.FIRST);
            String runId = container.flows.runtime.start(candidate, null).runId;
            String waitId = dashboard(container).flowRuns.stream().filter(run -> run.id.equals(runId))
                    .findFirst().orElseThrow(AssertionError::new).steps.get(0).waitId;
            int initialXp = dashboard(container).xp;
            try (ProductActivitySession session = new ProductActivitySession(instrumentation)) {
                MainActivity activity = session.launch();
                View root = activity.getWindow().getDecorView();
                TodayViewModel model = model(activity);
                PresentationAwaiter.await(instrumentation, "Waiting run was not loaded", () ->
                        !model.state().getValue().loading && model.state().getValue().today().flowRuns.stream()
                                .anyMatch(run -> run.id.equals(runId)), root);
                assertHidden(root, title, next);
                if (background) session.background();
                // Replace the distant fixture deadline. No explicit refresh/activation follows this write.
                assertTrue(container.flows.runtime.adjustWait(runId, waitId, System.currentTimeMillis() + 1_000));
                if (background) {
                    elapsed(1_500);
                    assertSame("Exercise resume of the existing activity", activity, session.foreground());
                }
                PresentationAwaiter.await(instrumentation, "Elapsed wait did not reveal its next action", () ->
                        hasText(root, next), root);
                assertEquals(initialXp, dashboard(container).xp);
                assertTrue(dashboard(container).flowRuns.stream().filter(run -> run.id.equals(runId))
                        .flatMap(run -> run.steps.stream()).anyMatch(step -> next.equals(step.title)
                                && step.state == FlowGraphRun.State.AVAILABLE));
                MainActivity recreated = session.recreate();
                View restored = recreated.getWindow().getDecorView();
                PresentationAwaiter.await(instrumentation, "Ready flow was lost during recreation", () ->
                        hasText(restored, next), restored);
                assertEquals(initialXp, dashboard(container).xp);
            }
        } finally { container.catalog.delete.execute(task); }
    }

    private TodayViewModel model(MainActivity activity) {
        AtomicReference<TodayViewModel> result = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> result.set(new ViewModelProvider(activity).get(TodayViewModel.class)));
        return result.get();
    }
    private Dashboard dashboard(AppContainer container) {
        return container.today.loadDashboard.execute(container.clock.today());
    }
    private void assertHidden(View root, String title, String next) {
        instrumentation.runOnMainSync(() -> {
            assertFalse("A wait-only sheet must be hidden", hasText(root, title));
            assertFalse("The dependent step must not appear early", hasText(root, next));
        });
    }
    private boolean hasText(View view, String text) {
        if (view.getVisibility() != View.VISIBLE) return false;
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(text)) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            if (hasText(((ViewGroup) view).getChildAt(i), text)) return true;
        return false;
    }
    private void elapsed(long millis) {
        CountDownLatch elapsed = new CountDownLatch(1);
        Handler main = new Handler(Looper.getMainLooper()); Runnable signal = elapsed::countDown;
        main.postDelayed(signal, millis);
        try {
            if (!elapsed.await(5, TimeUnit.SECONDS)) throw new AssertionError("Device clock did not advance");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new AssertionError(error);
        } finally { main.removeCallbacks(signal); }
    }
}
