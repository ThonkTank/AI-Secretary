package activities.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import androidx.core.content.ContextCompat;

import com.autosecretary.R;

import activities.inApp.MainActivity;
import controller.BudgetManager;
import data.BudgetDisplayData;

/**
 * Budget-Widget Provider - zeigt Saldo und freies Budget auf dem Home-Screen.
 *
 * Layout:
 *   [Budget] [+]
 *   Saldo: 1.234,56 EUR  |  Frei: 456,78 EUR
 *
 * Interaktionen:
 *   - Widget-Tap -> oeffnet Budget-Tab in MainActivity
 *   - "+" Button -> oeffnet Transaktions-Erfassung
 */
public class BudgetWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_OPEN_BUDGET = "com.autosecretary.widget.ACTION_OPEN_BUDGET";
    public static final String ACTION_CREATE_TRANSACTION = "com.autosecretary.widget.ACTION_CREATE_TRANSACTION";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_budget);

            // 1. Daten laden
            BudgetManager bm = new BudgetManager(context);
            int totalCents = bm.getTotalBalanceCents();
            int freeCents = bm.getFreeBudgetCents();

            // 2. Werte setzen
            rv.setTextViewText(R.id.budget_total_value, BudgetDisplayData.formatCents(totalCents));
            rv.setTextViewText(R.id.budget_free_value, BudgetDisplayData.formatCents(freeCents));

            // 3. Farbe fuer "Frei" (rot wenn negativ)
            int freeColor = freeCents >= 0
                ? ContextCompat.getColor(context, R.color.budget_income)
                : ContextCompat.getColor(context, R.color.budget_expense);
            rv.setTextColor(R.id.budget_free_value, freeColor);

            // 4. "+" Button -> Oeffnet Transaktions-Modal
            Intent addIntent = new Intent(context, MainActivity.class);
            addIntent.setAction(ACTION_CREATE_TRANSACTION);
            addIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent addPending = PendingIntent.getActivity(
                context, 200, addIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            rv.setOnClickPendingIntent(R.id.budget_widget_add, addPending);

            // 5. Widget-Tap -> Oeffnet Budget-Tab
            Intent appIntent = new Intent(context, MainActivity.class);
            appIntent.setAction(ACTION_OPEN_BUDGET);
            appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent appPending = PendingIntent.getActivity(
                context, 201, appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            rv.setOnClickPendingIntent(R.id.budget_widget_content, appPending);
            rv.setOnClickPendingIntent(R.id.budget_widget_title, appPending);

            // 6. Widget aktualisieren
            appWidgetManager.updateAppWidget(appWidgetId, rv);
        }
    }

    /**
     * Aktualisiert alle Budget-Widget-Instanzen.
     * Kann von externen Komponenten aufgerufen werden (BudgetManager, TodoManager, etc.).
     */
    public static void notifyWidgetUpdate(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName widget = new ComponentName(context, BudgetWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(widget);

        if (ids != null && ids.length > 0) {
            // Vollstaendiges Update (Header + Werte neu laden)
            Intent updateIntent = new Intent(context, BudgetWidgetProvider.class);
            updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            context.sendBroadcast(updateIntent);
        }
    }
}
