package de.thonktank.autosecretary;

import de.thonktank.autosecretary.ui.today.HeaderView;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.provider.Settings;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import de.thonktank.autosecretary.domain.model.RewardBooking;
import de.thonktank.autosecretary.domain.model.RewardReceipt;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowValueAnimator;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class PresentationContractsRobolectricTest {
    @Test public void anchorRegistryUsesTypedKeysAndIgnoresHiddenAnchors() {
        RewardAnchorRegistry registry = new RewardAnchorRegistry();
        View first = new View(ApplicationProvider.getApplicationContext());
        View second = new View(ApplicationProvider.getApplicationContext());
        RewardAnchorKey key = new RewardAnchorKey(RewardAnchorKey.Kind.STEP, "step-1");

        registry.register(key, first);
        registry.register(new RewardAnchorKey(RewardAnchorKey.Kind.VESSEL, "task-1"), second);

        assertSame(first, registry.find(key));
        assertSame(second, registry.firstVisible(RewardAnchorKey.Kind.VESSEL));
        first.setVisibility(View.GONE);
        assertNull(registry.find(key));
    }

    @Test public void rewardAnimatorAcknowledgesImmediatelyWhenMotionIsDisabled() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        HeaderView header = new HeaderView(activity, () -> { });
        root.addView(header);
        activity.setContentView(root);
        RewardAnchorRegistry anchors = new RewardAnchorRegistry();
        anchors.register(RewardAnchorKey.head(), header.rewardAnchor());
        AtomicBoolean acknowledged = new AtomicBoolean();
        Settings.Global.putFloat(activity.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 0f);
        new RewardAnimator(root, header, anchors).play(effect(),
                DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO), 0,
                () -> acknowledged.set(true));

        assertTrue(acknowledged.get());
        assertEquals(1, root.getChildCount());
        Settings.Global.putFloat(activity.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        ShadowValueAnimator.reset();
    }

    @Test @LooperMode(LooperMode.Mode.PAUSED)
    public void disposedRewardAnimatorCannotAcknowledgeFromDestroyedHost() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        HeaderView header = new HeaderView(activity, () -> { });
        root.addView(header);
        activity.setContentView(root);
        RewardAnchorRegistry anchors = new RewardAnchorRegistry();
        anchors.register(RewardAnchorKey.head(), header.rewardAnchor());
        AtomicBoolean acknowledged = new AtomicBoolean();
        Settings.Global.putFloat(activity.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        RewardAnimator animator = new RewardAnimator(root, header, anchors);

        animator.play(effect(), DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO), 0,
                () -> acknowledged.set(true));
        animator.dispose();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertFalse(acknowledged.get());
        assertEquals(1, root.getChildCount());
        ShadowValueAnimator.reset();
    }

    @Test public void taskEditorCoordinatorOwnsMountingAndDashboardVisibility() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        View dashboard = new View(activity);
        root.addView(dashboard);
        activity.setContentView(root);
        TaskEditorCoordinator coordinator = new TaskEditorCoordinator(activity, root, dashboard,
                new TaskEditorView.Listener() {
                    @Override public void onDraftChanged(EditorUiState draft) { }
                    @Override public void onSave(EditorUiState draft) { }
                    @Override public void onDelete(String taskId) { }
                    @Override public void onDismiss() { }
                });
        DayPalette palette = DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO);

        coordinator.render(EditorUiState.create(), palette, LocalDate.of(2026, 8, 19));
        assertEquals(View.INVISIBLE, dashboard.getVisibility());
        assertTrue(root.getChildCount() > 1);

        coordinator.render(EditorUiState.closed(), palette, LocalDate.of(2026, 8, 19));
        assertEquals(View.VISIBLE, dashboard.getVisibility());
        assertEquals(1, root.getChildCount());
        assertFalse(coordinator.handleBack());
    }

    @Test public void editorStateCanOpenBeforeItsPresentationTransitionCompletes() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        View dashboard = new View(activity);
        root.addView(dashboard);
        activity.setContentView(root);
        TaskEditorCoordinator coordinator = new TaskEditorCoordinator(activity, root, dashboard,
                new TaskEditorView.Listener() {
                    @Override public void onDraftChanged(EditorUiState draft) { }
                    @Override public void onSave(EditorUiState draft) { }
                    @Override public void onDelete(String taskId) { }
                    @Override public void onDismiss() { }
                });
        DayPalette palette = DayPalette.at(LocalTime.NOON, DayPalette.Mode.AUTO);

        coordinator.deferNextOpen();
        coordinator.render(EditorUiState.create(), palette, LocalDate.of(2026, 8, 24));

        assertEquals(View.VISIBLE, dashboard.getVisibility());
        assertEquals(1, root.getChildCount());

        coordinator.completeDeferredOpen();

        assertEquals(View.INVISIBLE, dashboard.getVisibility());
        assertEquals(2, root.getChildCount());
    }

    private static RewardEffect effect() {
        RewardBooking booking = new RewardBooking("booking", "transaction", "occurrence",
                null, "task:test", RewardBooking.Kind.SINGLE_COMPLETION,
                RewardBooking.Target.HEAD, 10, 0, LocalDate.of(2026, 8, 19), null);
        return RewardEffect.from(RewardReceipt.of("transaction",
                        Collections.singletonList(booking), RewardReceipt.Target.HEAD),
                new UiCommand(UiCommand.Kind.COMPLETE, "occurrence"));
    }
}
