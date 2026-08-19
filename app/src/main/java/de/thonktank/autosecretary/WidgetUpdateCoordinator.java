package de.thonktank.autosecretary;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.widget.RemoteViews;

import de.thonktank.autosecretary.widget.WidgetPresenter;
import de.thonktank.autosecretary.widget.WidgetUiModel;

import java.util.Arrays;
import java.util.concurrent.Executor;

import de.thonktank.autosecretary.infrastructure.AppLogger;

public final class WidgetUpdateCoordinator {
    interface CycleLoader { WidgetPresenter.CycleData load(); }
    interface Projector {
        WidgetUiModel present(WidgetPresenter.CycleData data, WidgetSizeClassifier.Size size);
    }
    interface ViewsFactory { RemoteViews create(WidgetUiModel model); }
    interface Host {
        Bundle options(int widgetId);
        void update(int widgetId, RemoteViews views);
    }

    private static final Runnable NO_COMPLETION = () -> { };
    private final Context context;
    private final Executor executor;
    private final AppLogger logger;
    private final WidgetSizeClassifier sizes;
    private final CycleLoader loader;
    private final Projector projector;
    private final ViewsFactory viewsFactory;

    WidgetUpdateCoordinator(Context context, Executor executor, AppLogger logger,
                            WidgetSizeClassifier sizes, CycleLoader loader,
                            Projector projector, ViewsFactory viewsFactory) {
        this.context = context.getApplicationContext();
        this.executor = executor;
        this.logger = logger;
        this.sizes = sizes;
        this.loader = loader;
        this.projector = projector;
        this.viewsFactory = viewsFactory;
    }

    static WidgetUpdateCoordinator create(Context context, AppContainer container,
                                          Executor executor) {
        WidgetPresenter presenter = new WidgetPresenter(context, container);
        WidgetRemoteViewsFactory factory = new WidgetRemoteViewsFactory(context,
                new WidgetForestCache());
        return new WidgetUpdateCoordinator(context, executor, container.logger,
                new WidgetSizeClassifier(), presenter::load, presenter::present, factory::create);
    }

    public void update(AppWidgetManager manager, int[] widgetIds, Runnable completion) {
        int[] stableIds = widgetIds == null ? new int[0] : Arrays.copyOf(widgetIds, widgetIds.length);
        enqueue(() -> runCycle(stableIds, managerHost(manager), completion), completion);
    }

    public void updateOne(AppWidgetManager manager, int widgetId, Bundle options,
                          Runnable completion) {
        Bundle stableOptions = options == null ? new Bundle() : new Bundle(options);
        Host host = new Host() {
            @Override public Bundle options(int ignored) { return stableOptions; }
            @Override public void update(int id, RemoteViews views) {
                manager.updateAppWidget(id, views);
            }
        };
        enqueue(() -> runCycle(new int[]{widgetId}, host, completion), completion);
    }

    public void updateAll() {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, TaskWidgetProvider.class));
        update(manager, ids, NO_COMPLETION);
    }

    void runCycle(int[] widgetIds, Host host, Runnable completion) {
        try {
            if (widgetIds.length == 0) return;
            WidgetPresenter.CycleData data = loader.load();
            for (int widgetId : widgetIds) {
                try {
                    WidgetSizeClassifier.Size size = sizes.classify(host.options(widgetId));
                    host.update(widgetId, viewsFactory.create(projector.present(data, size)));
                } catch (RuntimeException error) {
                    logger.error("WidgetUpdate", "Could not update widget " + widgetId, error);
                }
            }
        } catch (RuntimeException error) {
            logger.error("WidgetUpdate", "Could not load widget data", error);
        } finally {
            completion.run();
        }
    }

    private Host managerHost(AppWidgetManager manager) {
        return new Host() {
            @Override public Bundle options(int widgetId) {
                return manager.getAppWidgetOptions(widgetId);
            }
            @Override public void update(int widgetId, RemoteViews views) {
                manager.updateAppWidget(widgetId, views);
            }
        };
    }

    private void enqueue(Runnable work, Runnable completion) {
        try {
            executor.execute(work);
        } catch (RuntimeException error) {
            logger.error("WidgetUpdate", "Could not schedule widget update", error);
            completion.run();
        }
    }
}
