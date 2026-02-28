package com.autosecretary.features.budget.ui.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;

import com.autosecretary.R;
import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.app.MainActivity;
import com.autosecretary.features.budget.application.LoadBudgetWidgetSummaryUseCase;
import com.autosecretary.features.budget.ui.internal.CurrencyFormatter;
import com.autosecretary.shared.WidgetConfiguration;

public class BudgetWidgetProvider extends AppWidgetProvider {
    // Widget update period is defined in WidgetConfiguration and configured in widget_budget_info.xml.
    // Both must be kept in sync. If they diverge, the widget may update more or less frequently than
    // intended. See widget_budget_info.xml (src/main/res/xml/) for the configured period in milliseconds.
    @SuppressWarnings("unused")
    private static final long WIDGET_UPDATE_PERIOD_MILLIS = WidgetConfiguration.WIDGET_UPDATE_PERIOD_MILLIS;

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
        AutoSecretaryApplication app = AutoSecretaryApplication.from(context);
        LoadBudgetWidgetSummaryUseCase useCase = app.getAppCompositionRoot().createLoadBudgetWidgetSummaryUseCase();
        LoadBudgetWidgetSummaryUseCase.BudgetWidgetSummary summary = useCase.execute();

        views.setTextViewText(R.id.BudgetWidgetTotalValue, CurrencyFormatter.eurosNet(summary.netBalanceCents()));
        views.setTextViewText(R.id.BudgetWidgetFreeValue, CurrencyFormatter.eurosNet(summary.freeBudgetCents()));

        setupButton(views, widgetId, context, R.id.BudgetWidgetOpenButton, 0, null);
        setupButton(views, widgetId, context, R.id.BudgetWidgetAddButton, 1, ACTION_ADD_TRANSACTION);

        manager.updateAppWidget(widgetId, views);
    }

    private void setupButton(RemoteViews views, int widgetId, Context context, int viewId,
                             int actionIndex, @Nullable String budgetAction) {
        views.setOnClickPendingIntent(viewId, buildPendingIntent(context, widgetId, actionIndex, budgetAction));
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
        Intent launchIntent = new Intent(context, MainActivity.class);
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
