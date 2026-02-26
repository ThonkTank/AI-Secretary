package com.autosecretary.features.budget.ui.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.autosecretary.R;
import com.autosecretary.app.MainActivity;
import com.autosecretary.database.AppDatabase;
import com.autosecretary.features.budget.application.LoadBudgetWidgetSummaryUseCase;
import com.autosecretary.features.budget.data.repository.BudgetWidgetRoomRepository;

import com.autosecretary.features.budget.ui.internal.CurrencyFormatter;

public class BudgetWidgetProvider extends AppWidgetProvider {
    public static final String EXTRA_OPEN_TAB = "open_tab";
    public static final String EXTRA_BUDGET_ACTION = "budget_action";
    public static final String TAB_BUDGET = "budget";
    public static final String ACTION_ADD_TRANSACTION = "add_transaction";

    // Number of distinct PendingIntent slots reserved per widget instance.
    // Each slot maps to one button action; offset within the slot identifies the action.
    private static final int ACTIONS_PER_WIDGET = 10;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }

    private void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.budget_widget);

        // Synchronous DB read — acceptable here because widget updates run outside the main
        // Activity thread. Keep this query fast to avoid delaying widget renders.
        AppDatabase db = AppDatabase.getInstance(context);
        BudgetWidgetRoomRepository repository = new BudgetWidgetRoomRepository(
                db.transactionDao(),
                db.budgetLimitDao()
        );
        LoadBudgetWidgetSummaryUseCase useCase = new LoadBudgetWidgetSummaryUseCase(repository);
        LoadBudgetWidgetSummaryUseCase.BudgetWidgetSummary summary = useCase.loadCurrentMonth();

        views.setTextViewText(R.id.budget_widget_total_value, CurrencyFormatter.eurosNet(summary.netBalanceCents()));
        views.setTextViewText(R.id.budget_widget_free_value, CurrencyFormatter.eurosNet(summary.freeBudgetCents()));

        views.setOnClickPendingIntent(
                R.id.budget_widget_open_button,
                buildPendingIntent(context, widgetId, 0, null)
        );
        views.setOnClickPendingIntent(
                R.id.budget_widget_add_button,
                buildPendingIntent(context, widgetId, 1, ACTION_ADD_TRANSACTION)
        );

        manager.updateAppWidget(widgetId, views);
    }

    // Unique per widget instance + action: Android requires distinct request codes
    // for PendingIntents that carry different extras. offset identifies the action slot.
    private PendingIntent buildPendingIntent(Context context, int widgetId, int offset, String budgetAction) {
        Intent launchIntent = new Intent(context, MainActivity.class);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        launchIntent.putExtra(EXTRA_OPEN_TAB, TAB_BUDGET);
        if (budgetAction != null) {
            launchIntent.putExtra(EXTRA_BUDGET_ACTION, budgetAction);
        }
        return PendingIntent.getActivity(
                context,
                widgetId * ACTIONS_PER_WIDGET + offset,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    public static void notifyWidgetUpdate(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName widget = new ComponentName(context, BudgetWidgetProvider.class);
        int[] widgetIds = manager.getAppWidgetIds(widget);
        if (widgetIds.length == 0) {
            return;
        }
        Intent updateIntent = new Intent(context, BudgetWidgetProvider.class);
        updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds);
        context.sendBroadcast(updateIntent);
    }
}
