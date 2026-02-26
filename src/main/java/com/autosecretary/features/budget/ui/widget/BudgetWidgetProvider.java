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

import java.util.Locale;

public class BudgetWidgetProvider extends AppWidgetProvider {
    public static final String EXTRA_OPEN_TAB = "open_tab";
    public static final String EXTRA_BUDGET_ACTION = "budget_action";
    public static final String TAB_BUDGET = "budget";
    public static final String ACTION_ADD_TRANSACTION = "add_transaction";

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

        views.setTextViewText(R.id.budget_widget_total_value, formatCurrency(summary.netBalanceCents()));
        views.setTextViewText(R.id.budget_widget_free_value, formatCurrency(summary.freeBudgetCents()));

        views.setOnClickPendingIntent(
                R.id.budget_widget_open_button,
                buildOpenBudgetIntent(context, widgetId, false)
        );
        views.setOnClickPendingIntent(
                R.id.budget_widget_add_button,
                buildOpenBudgetIntent(context, widgetId, true)
        );

        manager.updateAppWidget(widgetId, views);
    }

    private PendingIntent buildOpenBudgetIntent(Context context, int widgetId, boolean openAddDialog) {
        Intent launchIntent = new Intent(context, MainActivity.class);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        launchIntent.putExtra(EXTRA_OPEN_TAB, TAB_BUDGET);
        if (openAddDialog) {
            launchIntent.putExtra(EXTRA_BUDGET_ACTION, ACTION_ADD_TRANSACTION);
        }

        // Unique per widget instance + action: Android requires distinct request codes
        // for PendingIntents that carry different extras.
        int requestCode = widgetId * 10 + (openAddDialog ? 1 : 0);
        return PendingIntent.getActivity(
                context,
                requestCode,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private String formatCurrency(long amountCents) {
        String sign = amountCents < 0 ? "-" : "";
        return String.format(Locale.GERMAN, "%s%.2f €", sign, Math.abs(amountCents) / 100.0);
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
