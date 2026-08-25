package de.thonktank.autosecretary.timer;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.CopyOnWriteArrayList;

import de.thonktank.autosecretary.infrastructure.AppLogger;

/** Owns independent, durable countdowns and their Android wake-up alarms. */
public final class TimerManager {
    public interface Listener { void onTimersChanged(Snapshot snapshot); }

    public static final class Snapshot {
        public final List<TimerSession> sessions;
        public final long elapsedRealtime;
        public final boolean exactAlarmsAvailable;
        public final boolean notificationsAvailable;

        Snapshot(List<TimerSession> sessions, long elapsedRealtime,
                 boolean exactAlarmsAvailable, boolean notificationsAvailable) {
            this.sessions = Collections.unmodifiableList(new ArrayList<>(sessions));
            this.elapsedRealtime = elapsedRealtime;
            this.exactAlarmsAvailable = exactAlarmsAvailable;
            this.notificationsAvailable = notificationsAvailable;
        }

        public static Snapshot empty() {
            return new Snapshot(Collections.emptyList(), 0, true, true);
        }

        public static Snapshot of(List<TimerSession> sessions, long elapsedRealtime,
                                  boolean exactAlarmsAvailable,
                                  boolean notificationsAvailable) {
            return new Snapshot(sessions, elapsedRealtime, exactAlarmsAvailable,
                    notificationsAvailable);
        }

        public TimerSession forStep(String stepId) {
            for (TimerSession session : sessions)
                if (session.stepId.equals(stepId)) return session;
            return null;
        }

        public boolean degraded() {
            return !exactAlarmsAvailable || !notificationsAvailable;
        }
    }

    private static final String TAG = "TaskTimers";
    private final TimerSessionStore store;
    private final TimerScheduler scheduler;
    private final TimerNotificationPublisher notifications;
    private final TimerClock clock;
    private final Executor serial;
    private final AppLogger logger;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, TimerSession> sessions = new LinkedHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private boolean ticking;

    public TimerManager(TimerSessionStore store, TimerScheduler scheduler,
                        TimerNotificationPublisher notifications, TimerClock clock,
                        Executor serial, AppLogger logger) {
        this.store = store;
        this.scheduler = scheduler;
        this.notifications = notifications;
        this.clock = clock;
        this.serial = serial;
        this.logger = logger;
    }

    public void reconcile() {
        reconcile(null);
    }

    public void reconcile(Runnable completion) {
        serial.execute(() -> {
            try {
                long elapsed = clock.elapsedRealtime();
                long epoch = clock.epochMillis();
                synchronized (sessions) {
                    sessions.clear();
                    for (TimerSession stored : store.all()) {
                        TimerSession current = stored;
                        if (stored.state == TimerSession.State.RUNNING) {
                            long remaining = Math.max(0, stored.targetEpochMillis - epoch);
                            current = remaining == 0 ? stored.finished()
                                    : new TimerSession(stored.id, stored.stepId, stored.title,
                                    stored.kind, stored.state, stored.totalSeconds, remaining,
                                    elapsed + remaining, epoch + remaining, stored.notificationId,
                                    stored.completionObserved);
                            store.put(current);
                            if (current.state == TimerSession.State.RUNNING)
                                scheduler.schedule(current);
                            else notifications.publish(current);
                        }
                        sessions.put(current.id, current);
                    }
                }
                publish();
            } catch (RuntimeException error) {
                logger.error(TAG, "Timer reconstruction failed", error);
            } finally {
                if (completion != null) completion.run();
            }
        });
    }

    public void start(String stepId, String title, TimerSession.Kind kind, int seconds) {
        if (seconds < 1) throw new IllegalArgumentException("Timer must be positive");
        serial.execute(() -> {
            String id = id(kind, stepId);
            synchronized (sessions) {
                TimerSession existing = sessions.get(id);
                if (existing != null && (existing.state == TimerSession.State.RUNNING
                        || existing.state == TimerSession.State.PAUSED)) return;
                if (existing != null) {
                    scheduler.cancel(existing);
                    notifications.cancel(existing);
                }
                long elapsed = clock.elapsedRealtime();
                long epoch = clock.epochMillis();
                long duration = seconds * 1000L;
                TimerSession created = new TimerSession(id, stepId, title, kind,
                        TimerSession.State.RUNNING, seconds, duration, elapsed + duration,
                        epoch + duration, notificationId(id), false);
                sessions.put(id, created);
                store.put(created);
                scheduler.schedule(created);
            }
            publish();
        });
    }

    public void pause(String id) {
        serial.execute(() -> mutate(id, session -> session.paused(clock.elapsedRealtime()), true));
    }

    public void resume(String id) {
        serial.execute(() -> mutate(id,
                session -> session.resumed(clock.elapsedRealtime(), clock.epochMillis()), false));
    }

    public void reset(String id) {
        serial.execute(() -> {
            TimerSession removed;
            synchronized (sessions) {
                removed = sessions.remove(id);
                if (removed == null) return;
                scheduler.cancel(removed);
                notifications.cancel(removed);
                store.delete(id);
            }
            publish();
        });
    }

    public void resetForStep(String stepId) {
        serial.execute(() -> {
            List<TimerSession> removed = new ArrayList<>();
            synchronized (sessions) {
                for (TimerSession session : new ArrayList<>(sessions.values())) {
                    if (!session.stepId.equals(stepId)) continue;
                    sessions.remove(session.id);
                    scheduler.cancel(session);
                    notifications.cancel(session);
                    store.delete(session.id);
                    removed.add(session);
                }
            }
            if (!removed.isEmpty()) publish();
        });
    }

    public void finish(String id) {
        finish(id, null);
    }

    public void finish(String id, Runnable completion) {
        serial.execute(() -> {
            try {
                TimerSession finished;
                synchronized (sessions) {
                    TimerSession current = sessions.get(id);
                    TimerSession persisted = store.find(id);
                    if (persisted == null) {
                        if (current != null) {
                            sessions.remove(id);
                            scheduler.cancel(current);
                            publish();
                        }
                        return;
                    }
                    current = persisted;
                    if (current.state == TimerSession.State.FINISHED) return;
                    finished = current.finished();
                    sessions.put(id, finished);
                    store.put(finished);
                    scheduler.cancel(finished);
                }
                notifications.publish(finished);
                publish();
            } finally {
                if (completion != null) completion.run();
            }
        });
    }

    public void observeCompletion(String id) {
        serial.execute(() -> mutate(id, TimerSession::observed, false));
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
        main.post(() -> listener.onTimersChanged(snapshot()));
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void refreshCapabilities() {
        publish();
    }

    public Snapshot snapshot() {
        List<TimerSession> copy;
        synchronized (sessions) {
            copy = new ArrayList<>(sessions.values());
        }
        copy.sort(Comparator.comparingInt(value -> value.notificationId));
        return new Snapshot(copy, clock.elapsedRealtime(), scheduler.exactAlarmsAvailable(),
                notifications.notificationsAvailable());
    }

    private void mutate(String id, Mutation mutation, boolean cancelAlarm) {
        synchronized (sessions) {
            TimerSession current = sessions.get(id);
            if (current == null) return;
            TimerSession changed = mutation.apply(current);
            if (changed == current) return;
            if (cancelAlarm) scheduler.cancel(current);
            sessions.put(id, changed);
            store.put(changed);
            if (changed.state == TimerSession.State.RUNNING) scheduler.schedule(changed);
        }
        publish();
    }

    private void publish() {
        Snapshot snapshot = snapshot();
        main.post(() -> {
            for (Listener listener : listeners) listener.onTimersChanged(snapshot);
            ensureTicking(snapshot);
        });
    }

    private void ensureTicking(Snapshot snapshot) {
        boolean hasRunning = false;
        for (TimerSession session : snapshot.sessions)
            if (session.state == TimerSession.State.RUNNING) hasRunning = true;
        if (!hasRunning) {
            ticking = false;
            main.removeCallbacks(tick);
        } else if (!ticking) {
            ticking = true;
            main.post(tick);
        }
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            Snapshot snapshot = snapshot();
            boolean running = false;
            for (TimerSession session : snapshot.sessions) {
                if (session.state != TimerSession.State.RUNNING) continue;
                running = true;
                if (session.remainingAt(snapshot.elapsedRealtime) == 0) finish(session.id);
            }
            for (Listener listener : listeners) listener.onTimersChanged(snapshot);
            if (running) main.postDelayed(this, 1_000L);
            else ticking = false;
        }
    };

    private static String id(TimerSession.Kind kind, String stepId) {
        return kind.name().toLowerCase(java.util.Locale.ROOT) + ":" + stepId;
    }

    private static int notificationId(String id) {
        return 10_000 + (id.hashCode() & 0x3fffffff);
    }

    private interface Mutation { TimerSession apply(TimerSession session); }
}
