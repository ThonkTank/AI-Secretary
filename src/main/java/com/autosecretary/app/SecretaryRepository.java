package com.autosecretary.app;

import android.os.Handler;
import android.os.Looper;

import com.autosecretary.core.FocusPlanner;
import com.autosecretary.core.Obligation;
import com.autosecretary.core.PlanItem;
import com.autosecretary.core.PlanMove;
import com.autosecretary.data.DeviceCalendarReader;
import com.autosecretary.data.TaskStore;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Single application boundary for UI, widget and local-AI proposal confirmation. */
public final class SecretaryRepository {
    private final TaskStore store;
    private final DeviceCalendarReader calendar;
    private final FocusPlanner planner = new FocusPlanner();
    private final Executor executor;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable changeNotifier;

    public SecretaryRepository(
            TaskStore store,
            DeviceCalendarReader calendar,
            Executor executor,
            Runnable changeNotifier) {
        this.store = store;
        this.calendar = calendar;
        this.executor = executor;
        this.changeNotifier = changeNotifier;
    }

    public void loadDashboard(int focusLimit, Consumer<Dashboard> callback) {
        executor.execute(() -> {
            Dashboard dashboard = buildDashboard(focusLimit);
            main.post(() -> callback.accept(dashboard));
        });
    }

    public Dashboard loadDashboardBlocking(int focusLimit) {
        return buildDashboard(focusLimit);
    }

    private Dashboard buildDashboard(int focusLimit) {
        List<Obligation> obligations = store.readAll();
        LocalDate day = LocalDate.now();
        List<PlanItem> fullPlan = planner.plan(
                obligations,
                store.readRecentCompletions(200),
                calendar.read(day),
                LocalDateTime.now(),
                obligations.size());
        List<PlanItem> focus = new ArrayList<>(
                fullPlan.subList(0, Math.min(Math.max(0, focusLimit), fullPlan.size())));
        List<Obligation> ordered = new ArrayList<>();
        Set<String> openIds = new HashSet<>();
        for (PlanItem item : fullPlan) {
            ordered.add(item.obligation());
            openIds.add(item.obligation().id);
        }
        for (Obligation obligation : obligations) {
            if (!openIds.contains(obligation.id)) ordered.add(obligation);
        }
        return new Dashboard(focus, ordered);
    }

    public void save(Obligation obligation, Runnable callback) {
        mutate(() -> store.save(obligation), callback);
    }

    public void delete(String id, Runnable callback) {
        mutate(() -> store.delete(id), callback);
    }

    public void move(String id, PlanMove move, Runnable callback) {
        executor.execute(() -> {
            LocalDate day = LocalDate.now();
            List<Obligation> obligations = store.readAll();
            List<PlanItem> plan = planner.plan(
                    obligations,
                    store.readRecentCompletions(200),
                    calendar.read(day),
                    LocalDateTime.now(),
                    obligations.size());
            List<String> orderedIds = new ArrayList<>();
            for (PlanItem item : plan) orderedIds.add(item.obligation().id);
            int current = orderedIds.indexOf(id);
            if (current >= 0) {
                int target = switch (move) {
                    case FIRST -> 0;
                    case EARLIER -> Math.max(0, current - 1);
                    case LATER -> Math.min(orderedIds.size() - 1, current + 1);
                    case LAST -> orderedIds.size() - 1;
                };
                if (current != target) {
                    String moved = orderedIds.remove(current);
                    orderedIds.add(target, moved);
                }
                store.saveManualOrder(day, orderedIds);
                changeNotifier.run();
            }
            main.post(callback);
        });
    }

    public void complete(String id, Consumer<Obligation> callback) {
        executor.execute(() -> {
            Obligation completed = store.complete(id, LocalDateTime.now());
            changeNotifier.run();
            main.post(() -> callback.accept(completed));
        });
    }

    public void setStepCompleted(
            String obligationId,
            String stepId,
            boolean completed,
            Consumer<Obligation> callback) {
        executor.execute(() -> {
            Obligation result = store.setStepCompleted(
                    obligationId, stepId, completed, LocalDateTime.now());
            changeNotifier.run();
            main.post(() -> callback.accept(result));
        });
    }

    public void apply(List<Obligation> upserts, List<String> deletions, Runnable callback) {
        mutate(() -> {
            for (String id : deletions) store.delete(id);
            for (Obligation obligation : upserts) store.save(obligation);
        }, callback);
    }

    private void mutate(Runnable mutation, Runnable callback) {
        executor.execute(() -> {
            mutation.run();
            changeNotifier.run();
            main.post(callback);
        });
    }
}
