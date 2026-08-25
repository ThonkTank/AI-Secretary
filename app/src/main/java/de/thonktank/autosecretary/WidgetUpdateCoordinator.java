package de.thonktank.autosecretary;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

import de.thonktank.autosecretary.infrastructure.AppLogger;
import de.thonktank.autosecretary.presentation.observable.LatestReadPipeline;
import de.thonktank.autosecretary.presentation.observable.PresentationInvalidation;
import de.thonktank.autosecretary.presentation.observable.PresentationInvalidationSource;
import de.thonktank.autosecretary.widget.WidgetPresenter;
import de.thonktank.autosecretary.widget.WidgetUiModel;
import kotlinx.coroutines.flow.Flow;

/** Owns the single observable read pipeline for every installed RemoteViews widget. */
public final class WidgetUpdateCoordinator {
    interface CycleLoader { WidgetPresenter.CycleData load(); }
    interface LatestCycleLoader {
        WidgetPresenter.CycleData load(PresentationInvalidation invalidation);
    }
    interface Projector {
        WidgetUiModel present(WidgetPresenter.CycleData data, WidgetSizeClassifier.Size size);
    }
    interface ViewsFactory { RemoteViews create(WidgetUiModel model); }
    interface Host {
        Bundle options(int widgetId);
        void update(int widgetId, RemoteViews views);
    }
    interface InstalledHost extends Host { int[] installedIds(); }
    interface Invalidations {
        Flow<PresentationInvalidation> changes();
        void materializeHostChange();
    }

    private static final Runnable NO_COMPLETION = () -> { };
    private final Object lifecycleLock = new Object();
    private final Executor executor;
    private final AppLogger logger;
    private final WidgetSizeClassifier sizes;
    private final Runnable preparation;
    private final CycleLoader loader;
    private final LatestCycleLoader latestLoader;
    private final Projector projector;
    private final ViewsFactory viewsFactory;
    private final InstalledHost installedHost;
    private final Invalidations invalidations;
    private final List<Runnable> pendingCompletions = new ArrayList<>();
    private LatestReadPipeline<PresentationInvalidation, WidgetPresenter.CycleData> reads;

    WidgetUpdateCoordinator(Context context, Executor executor, AppLogger logger,
                            WidgetSizeClassifier sizes, CycleLoader loader,
                            Projector projector, ViewsFactory viewsFactory) {
        this(executor, logger, sizes, NO_COMPLETION, loader, ignored -> loader.load(), projector,
                viewsFactory, null, null);
    }

    WidgetUpdateCoordinator(Executor executor, AppLogger logger, WidgetSizeClassifier sizes,
                            Runnable preparation, CycleLoader loader,
                            LatestCycleLoader latestLoader, Projector projector,
                            ViewsFactory viewsFactory, InstalledHost installedHost,
                            Invalidations invalidations) {
        this.executor = executor;
        this.logger = logger;
        this.sizes = sizes;
        this.preparation = preparation;
        this.loader = loader;
        this.latestLoader = latestLoader;
        this.projector = projector;
        this.viewsFactory = viewsFactory;
        this.installedHost = installedHost;
        this.invalidations = invalidations;
    }

    static WidgetUpdateCoordinator create(Context context, AppContainer container,
                                          Executor executor) {
        Context app = context.getApplicationContext();
        WidgetPresenter presenter = new WidgetPresenter(app, container);
        WidgetRemoteViewsFactory factory = new WidgetRemoteViewsFactory(app,
                new WidgetForestCache());
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        InstalledHost host = new InstalledHost() {
            @Override public int[] installedIds() {
                return manager.getAppWidgetIds(new ComponentName(app, TaskWidgetProvider.class));
            }
            @Override public Bundle options(int widgetId) {
                return manager.getAppWidgetOptions(widgetId);
            }
            @Override public void update(int widgetId, RemoteViews views) {
                manager.updateAppWidget(widgetId, views);
            }
        };
        PresentationInvalidationSource source = container.presentationInvalidations;
        Invalidations invalidations = new Invalidations() {
            @Override public Flow<PresentationInvalidation> changes() {
                return source.getWidgetChanges();
            }
            @Override public void materializeHostChange() {
                source.materializeWidgetHostChange();
            }
        };
        return new WidgetUpdateCoordinator(executor, container.logger,
                new WidgetSizeClassifier(), presenter::prepare, presenter::load, presenter::load,
                presenter::present, factory::create, host, invalidations);
    }

    public void update(AppWidgetManager manager, int[] widgetIds, Runnable completion) {
        int[] stableIds = widgetIds == null ? new int[0] : Arrays.copyOf(widgetIds, widgetIds.length);
        if (stableIds.length == 0) {
            complete(completion);
            reconcileInstalledWidgets();
            return;
        }
        requestObservableUpdate(completion);
    }

    public void updateOne(AppWidgetManager manager, int widgetId, Bundle options,
                          Runnable completion) {
        requestObservableUpdate(completion);
    }

    /** Starts observation only when the launcher currently owns at least one widget. */
    public void reconcileInstalledWidgets() {
        if (installedHost == null || invalidations == null) return;
        int[] ids;
        try {
            ids = installedHost.installedIds();
        } catch (RuntimeException error) {
            logger.error("WidgetUpdate", "Could not inspect installed widgets", error);
            return;
        }
        if (ids == null || ids.length == 0) {
            stopObserving();
            return;
        }
        try {
            synchronized (lifecycleLock) {
                ensureObservingLocked();
            }
        } catch (RuntimeException error) {
            logger.error("WidgetUpdate", "Could not start widget observation", error);
        }
    }

    /** Stops source collection when the provider reports that its final widget was removed. */
    public void stopObserving() {
        LatestReadPipeline<PresentationInvalidation, WidgetPresenter.CycleData> active;
        List<Runnable> completions;
        synchronized (lifecycleLock) {
            active = reads;
            reads = null;
            completions = drainCompletionsLocked();
        }
        if (active != null) active.close();
        completeAll(completions);
    }

    void runCycle(int[] widgetIds, Host host, Runnable completion) {
        try {
            if (widgetIds.length == 0) return;
            render(loader.load(), widgetIds, host);
        } catch (RuntimeException error) {
            logger.error("WidgetUpdate", "Could not load widget data", error);
        } finally {
            complete(completion);
        }
    }

    boolean observingForTest() {
        synchronized (lifecycleLock) { return reads != null; }
    }

    private void requestObservableUpdate(Runnable completion) {
        if (installedHost == null || invalidations == null) {
            complete(completion);
            return;
        }
        try {
            synchronized (lifecycleLock) {
                pendingCompletions.add(completion);
                ensureObservingLocked();
            }
            invalidations.materializeHostChange();
        } catch (RuntimeException error) {
            logger.error("WidgetUpdate", "Could not request widget update", error);
            List<Runnable> completions;
            synchronized (lifecycleLock) { completions = drainCompletionsLocked(); }
            completeAll(completions);
        }
    }

    private void ensureObservingLocked() {
        if (reads != null) return;
        reads = LatestReadPipeline.prepared(invalidations.changes(), executor,
                ignored -> preparation.run(), latestLoader::load, this::publish,
                this::readFailed);
    }

    private void publish(WidgetPresenter.CycleData data) {
        int[] ids = null;
        try {
            ids = installedHost.installedIds();
            if (ids != null && ids.length > 0) render(data, ids, installedHost);
        } catch (RuntimeException error) {
            logger.error("WidgetUpdate", "Could not publish widget data", error);
        } finally {
            completePending();
        }
        if (ids == null || ids.length == 0) stopObserving();
    }

    private void readFailed(Throwable error) {
        logger.error("WidgetUpdate", "Could not load widget data", error);
        completePending();
    }

    private void render(WidgetPresenter.CycleData data, int[] widgetIds, Host host) {
        for (int widgetId : widgetIds) {
            try {
                WidgetSizeClassifier.Size size = sizes.classify(host.options(widgetId));
                host.update(widgetId, viewsFactory.create(projector.present(data, size)));
            } catch (RuntimeException error) {
                logger.error("WidgetUpdate", "Could not update widget " + widgetId, error);
            }
        }
    }

    private void completePending() {
        List<Runnable> completions;
        synchronized (lifecycleLock) { completions = drainCompletionsLocked(); }
        completeAll(completions);
    }

    private List<Runnable> drainCompletionsLocked() {
        List<Runnable> result = new ArrayList<>(pendingCompletions);
        pendingCompletions.clear();
        return result;
    }

    private void completeAll(List<Runnable> completions) {
        for (Runnable completion : completions) complete(completion);
    }

    private void complete(Runnable completion) {
        if (completion == null) return;
        try {
            completion.run();
        } catch (RuntimeException error) {
            logger.error("WidgetUpdate", "Could not finish widget update", error);
        }
    }
}
