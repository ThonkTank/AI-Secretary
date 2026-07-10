package com.autosecretary.features.budget.ui.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;

import com.autosecretary.R;
import com.autosecretary.features.budget.application.LoadBudgetWidgetSummaryUseCase;
import com.autosecretary.features.budget.ui.BudgetDependencies;
import com.autosecretary.features.budget.ui.internal.CurrencyFormatter;
import com.autosecretary.shared.WidgetConfiguration;

public class BudgetWidgetProvider extends AppWidgetProvider {
    // Widget update period is defined in WidgetConfiguration and configured in widget_budget_info.xml.
    // Both must be kept in sync. If they diverge, the widget may update more or less frequently than
    // intended. See widget_budget_info.xml (src/main/res/xml/) for the configured period in milliseconds.

    // Intent routing constants for MainActivity. When a widget button is clicked,
    // the PendingIntent launches MainActivity with these extras:
    // - EXTRA_OPEN_TAB="budget" tells MainActivity to show the budget tab
    // - EXTRA_BUDGET_ACTION="add_transaction" (optional) tells the budget screen to open the add-transaction dialog
    // See MainActivity for consumption of these extras.
    public static final String EXTRA_OPEN_TAB = "open_tab";
    public static final String EXTRA_BUDGET_ACTION = "budget_action";
    public static final String TAB_BUDGET = "budget";
    public static final String ACTION_ADD_TRANSACTION = "add_transaction";

    // Number of distinct PendingIntent slots reserved per widget instance.
    // Android requires unique request codes for PendingIntents with different extras.
    // We reserve 10 slots per widget to allow for up to 10 button actions without request code
    // collisions. Currently used by 2 buttons (open, add); the buffer enables future expansion
    // without needing to rework the request code scheme.
    private static final int ACTIONS_PER_WIDGET = 10;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }

    private void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.budget_widget);

        // Load summary from database. Widget updates run off the main Activity thread,
        // so synchronous reads are acceptable. Keep the query fast to avoid delaying render.
        // The summary includes netBalance (sum of all account balances) and freeBudget (remaining budget).
        BudgetDependencies dependencies = (BudgetDependencies) context.getApplicationContext();
        LoadBudgetWidgetSummaryUseCase useCase = dependencies.createLoadBudgetWidgetSummaryUseCase();
        LoadBudgetWidgetSummaryUseCase.BudgetWidgetSummary summary = useCase.execute();

        views.setTextViewText(R.id.BudgetWidgetTotalValue, CurrencyFormatter.eurosPlain(summary.netBalanceCents()));
        views.setTextViewText(R.id.BudgetWidgetFreeValue, CurrencyFormatter.eurosPlain(summary.freeBudgetCents()));

        views.setOnClickPendingIntent(R.id.BudgetWidgetOpenButton, buildPendingIntent(context, widgetId, 0, null));
        views.setOnClickPendingIntent(R.id.BudgetWidgetAddButton, buildPendingIntent(context, widgetId, 1, ACTION_ADD_TRANSACTION));

        manager.updateAppWidget(widgetId, views);
    }

    /**
     * Build a PendingIntent for a widget button, with a unique request code.
     *
     * Android requires distinct request codes for PendingIntents carrying different extras.
     * Request code calculation: (widgetId * ACTIONS_PER_WIDGET + actionIndex)
     * ensures uniqueness across:
     * - Multiple widget instances (widgetId varies)
     * - Multiple actions per widget (actionIndex varies, 0-based, max 9)
     *
     * Example: widget 42, action 1 → request code 421
     *          widget 43, action 0 → request code 430
     *
     * @param context Android context for building the intent
     * @param widgetId unique identifier for this widget instance
     * @param actionIndex action slot (0-based, 0-9 within ACTIONS_PER_WIDGET)
     * @param budgetAction optional extra action (e.g., "add_transaction"); null for default
     * @return PendingIntent routing to MainActivity with budget tab and action extras
     */
    private PendingIntent buildPendingIntent(Context context, int widgetId, int actionIndex,
                                             @Nullable String budgetAction) {
        Intent launchIntent = launchIntent(context);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        launchIntent.putExtra(EXTRA_OPEN_TAB, TAB_BUDGET);
        if (budgetAction != null) {
            launchIntent.putExtra(EXTRA_BUDGET_ACTION, budgetAction);
        }
        return PendingIntent.getActivity(
                context,
                widgetId * ACTIONS_PER_WIDGET + actionIndex,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private Intent launchIntent(Context context) {
        PackageManager packageManager = context.getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(context.getPackageName());
        return intent != null ? intent : new Intent();
    }

    public static void notifyWidgetUpdate(Context context) {
        WidgetConfiguration.notifyUpdate(context, BudgetWidgetProvider.class);
    }
}
