package de.thonktank.autosecretary;

import static org.junit.Assert.*;

import android.app.Activity;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.SavedStateHandle;
import androidx.room.Room;
import de.thonktank.autosecretary.calendar.*;
import de.thonktank.autosecretary.data.observable.*;
import de.thonktank.autosecretary.data.preferences.UiPreferences;
import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.presentation.*;
import de.thonktank.autosecretary.presentation.observable.PresentationInvalidationSource;
import de.thonktank.autosecretary.presentation.shell.AppShellScreenState;
import de.thonktank.autosecretary.presentation.today.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Clock -> scheduler -> invalidation -> production preparation -> Room -> model -> renderer. */
@RunWith(RobolectricTestRunner.class) @Config(sdk = {26, 35})
public final class FlowDeadlineIntegrationTest {
    @Rule public final InstantTaskExecutorRule instant = new InstantTaskExecutorRule();
    private ActivityController<Activity> host;
    private AppDatabase database;
    private ApplicationUseCaseComposition app;
    private TodayViewModel model;
    private PresentationInvalidationSource invalidations;
    private DashboardRenderer renderer;
    private LinearLayout content;
    private ScrollView scroll;
    private long now = 1_000_000;
    private int sequence, wakes;
    private final Clock clock = new Clock() {
        public LocalDate today() { return LocalDate.of(2026, 9, 17); }
        public LocalTime time() { return LocalTime.NOON; }
    };
    private final AppLogger logger = new AppLogger() {
        public void info(String tag, String message) { }
        public void error(String tag, String message, Throwable error) { throw new AssertionError(message, error); }
    };

    @Before public void setup() {
        host = Robolectric.buildActivity(Activity.class).setup();
        database = Room.inMemoryDatabaseBuilder(host.get(), AppDatabase.class).allowMainThreadQueries()
                .setQueryExecutor(Runnable::run).setTransactionExecutor(Runnable::run).build();
        app = new ApplicationUseCaseComposition(database, clock, () -> now,
                () -> "deadline-" + ++sequence, ComboPolicySource.defaults());
    }
    @After public void close() {
        if (model != null) model.onCleared();
        if (invalidations != null) invalidations.close();
        host.pause().stop().destroy(); database.close();
    }

    @Test public void hiddenSheetAppearsAtDeadlineThroughTheInitialSubscription() {
        start(true); mount(); assertHidden();
        advance(999); assertHidden(); assertEquals(0, wakes);
        advance(1); assertStep(); assertEquals(1, wakes);
        advance(2_000); assertStep(); assertEquals(1, wakes); assertEquals(0, xp());
    }

    @Test public void extendingAndShorteningWaitReplaceThePublishedDeadline() {
        String run = start(true); mount();
        String wait = model.state().getValue().today().flowRuns.get(0).steps.get(0).waitId;
        model.dispatch(TodayAction.adjustFlowWait(run, wait, now + 5_000)); drain();
        advance(1_000); assertHidden(); assertEquals(0, wakes);
        model.dispatch(TodayAction.adjustFlowWait(run, wait, now + 1_000)); drain();
        advance(999); assertHidden();
        advance(1); assertStep(); assertEquals(1, wakes);
        advance(4_000); assertEquals(1, wakes); assertEquals(0, xp());
    }

    @Test public void backgroundCancelsTheWakeAndResumeCatchesUpWithoutManualRefresh() {
        start(true); mount(); assertHidden(); model.setFlowForeground(false);
        advance(2_000); assertHidden(); assertEquals(0, wakes);
        model.setFlowForeground(true); drain(); assertStep(); assertEquals(1, wakes);
        model.setFlowForeground(false); model.setFlowForeground(true); drain();
        assertStep(); assertEquals(1, wakes); assertEquals(0, xp());
    }

    @Test public void finalWaitShowsCollectionOnlyAndRepeatedRefreshNeverPaysTwice() {
        String run = start(false); mount(); assertHidden();
        advance(1_000);
        FocusTaskUiModel focus = model.state().getValue().today().focus;
        assertNotNull(focus); assertTrue(focus.steps.isEmpty()); assertEquals(1, focus.chains.size());
        assertEquals(FlowChainUiModel.Mode.READY, focus.chains.get(0).mode);
        assertTrue(hasText(content, "Buntwäsche")); assertEquals(0, xp());
        model.dispatch(TodayAction.collectFlow(run)); drain();
        int paid = xp(); assertTrue(paid > 0); assertHidden();
        model.setFlowForeground(false); model.setFlowForeground(true); advance(2_000);
        model.dispatch(TodayAction.collectFlow(run)); drain();
        assertEquals(paid, xp()); assertHidden();
    }

    private String start(boolean nextStep) {
        FlowEditorDraft draft = FlowEditorDraft.empty().rename("Wäsche")
                .addStep("Buntwäsche", FlowDelayPolicy.fixed(1_000));
        if (nextStep) {
            draft = draft.addStep("Aufhängen", FlowDelayPolicy.fixed(0));
            draft = draft.withGraph(draft.graph.join(List.of(draft.graph.stepIds.get(0)), draft.graph.stepIds.get(1)));
        }
        app.flows.saveGraph.execute(draft.edit()); app.today.materializeDue.execute();
        String candidate = app.today.loadDashboard.execute(clock.today()).flowTaskSheets.get(0).entries.get(0).targetId;
        return app.flows.runtime.start(candidate, null).runId;
    }

    private void mount() {
        CalendarDataSource calendar = new CalendarDataSource() {
            public CalendarResult loadToday() { return new CalendarResult.Success(List.of()); }
            public Subscription observeChanges(Runnable observer) { return () -> { }; }
        };
        UiPreferences preferences = new UiPreferences(host.get(), logger);
        ClockInvalidationSource signals = new ClockInvalidationSource(clock, observer -> () -> { });
        invalidations = new PresentationInvalidationSource(new DatabaseInvalidationSource(database),
                new CalendarInvalidationSource(calendar), new PreferenceInvalidationSource(preferences), signals, Runnable::run);
        AndroidUiTextProvider texts = new AndroidUiTextProvider(host.get());
        DashboardPresenter presenter = new DashboardPresenter(clock, app.today.loadDashboard,
                app.today.materializeDue, new DashboardUiMapper(texts), app.today.applyComboDecay,
                app.today.settlePreviousPartialOccurrences, app.flows.activateReadyFlows, () -> { });
        FlowDeadlineScheduler deadlines = new FlowDeadlineScheduler(new Handler(Looper.getMainLooper()),
                () -> now, () -> { wakes++; signals.materializeForeground(); });
        model = new TodayViewModel(app.today, app.catalog, presenter, calendar, preferences, clock, logger,
                texts, invalidations, null, destination -> { }, new SavedStateHandle(), new DirectExecutor(),
                Runnable::run, app.flows, deadlines);
        scroll = new ScrollView(host.get()); content = new LinearLayout(host.get());
        content.setOrientation(LinearLayout.VERTICAL); scroll.addView(content); host.get().setContentView(scroll);
        renderer = new DashboardRenderer(host.get(), scroll, content, model::dispatch,
                action -> fail("Unexpected options action"), "test", new RewardAnchorRegistry(),
                action -> fail("Unexpected all-tasks action"));
        model.setFlowForeground(true); drain();
    }

    private void advance(long millis) {
        now += millis; ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(millis)); drain();
    }
    private void drain() {
        ShadowLooper.idleMainLooper();
        assertFalse(model.state().getValue().loading);
        assertTrue(model.state().getValue().requests.toString(), model.state().getValue().requests.isEmpty());
        renderer.render(new AppShellScreenState(NavigationDestination.TODAY,
                DayPalette.at(LocalTime.NOON, DayPalette.Mode.LIGHT)), model.state().getValue(), null, null);
        scroll.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY));
        scroll.layout(0, 0, 480, 900);
        ShadowLooper.idleMainLooper();
    }
    private int xp() { return app.today.loadDashboard.execute(clock.today()).xp; }
    private void assertHidden() {
        assertNull(model.state().getValue().today().focus);
        assertFalse(hasText(content, "Wäsche")); assertFalse(hasText(content, "Buntwäsche: Aufhängen"));
    }
    private void assertStep() {
        assertNotNull(model.state().getValue().today().focus);
        assertEquals(1, model.state().getValue().today().focus.steps.size());
        assertTrue("Visible step tree: " + textTree(content), hasText(content, "Buntwäsche: Aufhängen"));
    }
    private String textTree(View view) {
        String result = view instanceof TextView ? ((TextView) view).getText() + "[" + view.getVisibility() + "] " : "";
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            result += textTree(((ViewGroup) view).getChildAt(i));
        return result;
    }
    private boolean hasText(View view, String text) {
        if (view.getVisibility() != View.VISIBLE) return false;
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            if (hasText(((ViewGroup) view).getChildAt(i), text)) return true;
        return false;
    }
    private static final class DirectExecutor extends AbstractExecutorService {
        private boolean closed;
        public void shutdown() { closed = true; }
        public List<Runnable> shutdownNow() { closed = true; return List.of(); }
        public boolean isShutdown() { return closed; }
        public boolean isTerminated() { return closed; }
        public boolean awaitTermination(long timeout, TimeUnit unit) { return closed; }
        public void execute(Runnable action) { if (closed) throw new IllegalStateException(); action.run(); }
    }
}
