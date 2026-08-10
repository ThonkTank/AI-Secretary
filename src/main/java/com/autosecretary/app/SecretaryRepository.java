package com.autosecretary.app;

import android.os.Handler;
import android.os.Looper;

import com.autosecretary.core.FocusPlanner;
import com.autosecretary.core.Obligation;
import com.autosecretary.data.DeviceCalendarReader;
import com.autosecretary.data.TaskStore;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
            List<Obligation> obligations = store.readAll();
            Dashboard dashboard = new Dashboard(
                    planner.plan(
                            obligations,
                            store.readRecentCompletions(200),
                            calendar.read(LocalDate.now()),
                            LocalDateTime.now(),
                            focusLimit),
                    obligations);
            main.post(() -> callback.accept(dashboard));
        });
    }

    public Dashboard loadDashboardBlocking(int focusLimit) {
        List<Obligation> obligations = store.readAll();
        return new Dashboard(
                planner.plan(
                        obligations,
                        store.readRecentCompletions(200),
                        calendar.read(LocalDate.now()),
                        LocalDateTime.now(),
                        focusLimit),
                obligations);
    }

    public void save(Obligation obligation, Runnable callback) {
        mutate(() -> store.save(obligation), callback);
    }

    public void delete(String id, Runnable callback) {
        mutate(() -> store.delete(id), callback);
    }

    public void postpone(String id, Runnable callback) {
        mutate(() -> store.postpone(id, LocalDate.now()), callback);
    }

    public void complete(String id, Consumer<Obligation> callback) {
        executor.execute(() -> {
            Obligation completed = store.complete(id, LocalDateTime.now());
            changeNotifier.run();
            main.post(() -> callback.accept(completed));
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
