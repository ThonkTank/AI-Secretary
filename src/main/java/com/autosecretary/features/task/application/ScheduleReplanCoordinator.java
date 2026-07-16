package com.autosecretary.features.task.application;

import com.autosecretary.shared.WidgetRefreshNotifier;

import java.util.function.Consumer;

/**
 * Single, coalescing entry point for schedule re-planning.
 *
 * <p>Every scheduling-input change — task created/edited/deleted, assistant changes, reserved
 * category windows, per-weekday scheduling windows, buffer/tuning settings, external calendar
 * events — funnels through {@link #requestReplan()} instead of calling
 * {@link RegenerateScheduleUseCase} directly. This gives two guarantees:
 *
 * <ul>
 *   <li><b>Coalescing.</b> While a re-plan is running, further requests collapse into a single
 *       follow-up run rather than stacking, so a burst of changes (a multi-op assistant proposal,
 *       a flurry of calendar {@code onChange} callbacks) triggers at most one extra run.</li>
 *   <li><b>Responsiveness without the task UI.</b> The coordinator performs the re-plan itself, so
 *       the schedule and home-screen widgets stay current even when the change originates on a
 *       different tab and the task list is not visible. When the task list <em>is</em> open it
 *       registers a {@linkplain #setListener listener} and refreshes immediately.</li>
 * </ul>
 *
 * <p><b>Preservation.</b> Re-planning never replaces already-started or completed work — that
 * invariant is enforced downstream by {@link RegenerateScheduleUseCase} (its delete filter skips
 * started/completed slots) and the slot generator (locked slots are non-displaceable). The
 * coordinator only decides <em>when</em> to re-plan, not <em>what</em> is preserved.
 *
 * <p><b>Threading.</b> {@link #requestReplan()} may be called from the main thread (view models,
 * dialogs) or a worker thread (repositories, apply/undo use cases); the {@code running} /
 * {@code rerunRequested} flags are guarded by {@code synchronized(this)}. The underlying
 * {@link RegenerateScheduleUseCase} runs its DB work on the shared db executor (serialized after
 * the mutation that requested the re-plan, so it sees committed data) and dispatches its callback
 * on the main thread, where the listener and widget refresh run.
 */
public final class ScheduleReplanCoordinator {

    private final RegenerateScheduleUseCase regenerateScheduleUseCase;
    private final WidgetRefreshNotifier widgetRefreshNotifier;

    private volatile Consumer<RegenerateScheduleUseCase.Result> listener;
    private boolean running;
    private boolean rerunRequested;

    public ScheduleReplanCoordinator(RegenerateScheduleUseCase regenerateScheduleUseCase,
                                     WidgetRefreshNotifier widgetRefreshNotifier) {
        this.regenerateScheduleUseCase = regenerateScheduleUseCase;
        this.widgetRefreshNotifier = widgetRefreshNotifier;
    }

    /**
     * Requests a schedule re-plan. If one is already running, marks a single follow-up run instead
     * of starting another; otherwise starts one now. Safe to call from any thread.
     */
    public void requestReplan() {
        synchronized (this) {
            if (running) {
                rerunRequested = true;
                return;
            }
            running = true;
        }
        regenerateScheduleUseCase.execute(this::onRunFinished);
    }

    private void onRunFinished(RegenerateScheduleUseCase.Result result) {
        boolean again;
        synchronized (this) {
            running = false;
            again = rerunRequested;
            rerunRequested = false;
        }
        widgetRefreshNotifier.refreshTaskWidgets();
        Consumer<RegenerateScheduleUseCase.Result> current = listener;
        if (current != null) {
            current.accept(result);
        }
        if (again) {
            requestReplan();
        }
    }

    /** Registers the listener notified with the {@link RegenerateScheduleUseCase.Result} after each run. */
    public void setListener(Consumer<RegenerateScheduleUseCase.Result> listener) {
        this.listener = listener;
    }

    /** Clears the listener, but only if it is still the registered one (safe across view-model recreation). */
    public void clearListener(Consumer<RegenerateScheduleUseCase.Result> listener) {
        if (this.listener == listener) {
            this.listener = null;
        }
    }
}
