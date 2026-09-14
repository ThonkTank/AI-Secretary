package de.thonktank.autosecretary.presentation.today;

import android.os.Handler;
import android.os.Looper;
import java.util.*;
import java.util.function.LongSupplier;
import de.thonktank.autosecretary.domain.model.FlowRunSummary;

/** One foreground wakeup for all runs, including executions whose sheet is hidden. */
public final class FlowDeadlineScheduler {
    private final Handler handler;
    private final LongSupplier clock;
    private final Runnable refresh;
    private final Map<String, Long> deadlines = new LinkedHashMap<>();
    private final Set<String> fired = new HashSet<>();
    private boolean foreground;
    private boolean closed;
    private final Runnable wake = this::wake;

    public FlowDeadlineScheduler(Runnable refresh) {
        this(new Handler(Looper.getMainLooper()), System::currentTimeMillis, refresh);
    }

    public FlowDeadlineScheduler(Handler handler, LongSupplier clock, Runnable refresh) {
        this.handler = handler; this.clock = clock; this.refresh = refresh;
    }

    public void update(List<FlowRunSummary> runs) {
        handler.post(() -> {
            if (closed) return;
            deadlines.clear();
            for (FlowRunSummary run : runs) for (FlowRunSummary.Step step : run.steps)
                if (step.readyAtEpochMillis != null)
                    deadlines.put(step.waitId + ":" + step.readyAtEpochMillis, step.readyAtEpochMillis);
            fired.retainAll(deadlines.keySet());
            schedule();
        });
    }

    public void setForeground(boolean value) {
        foreground = value;
        if (value) fired.clear();
        schedule();
    }

    private void schedule() {
        handler.removeCallbacks(wake);
        if (!foreground || closed) return;
        Long next = null;
        for (Map.Entry<String, Long> entry : deadlines.entrySet())
            if (!fired.contains(entry.getKey()) && (next == null || entry.getValue() < next)) next = entry.getValue();
        if (next != null) handler.postDelayed(wake, Math.min(60_000L, Math.max(0L, next - clock.getAsLong())));
    }

    private void wake() {
        if (!foreground || closed) return;
        long now = clock.getAsLong();
        boolean expired = false;
        for (Map.Entry<String, Long> entry : deadlines.entrySet())
            if (entry.getValue() <= now && fired.add(entry.getKey())) expired = true;
        if (expired) refresh.run();
        schedule();
    }

    public void close() {
        closed = true; handler.removeCallbacks(wake);
    }
}
