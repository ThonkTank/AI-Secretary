package activities.widget;

/**
 * ══════════════════════════════════════════════════════════════════════════════
 * WIDGET REFRESH APP - Custom Application für Auto-Refresh
 * ══════════════════════════════════════════════════════════════════════════════
 *
 * ZIEL:
 *   Registriert BroadcastReceiver für ACTION_USER_PRESENT (Geräte-Unlock).
 *   Aktualisiert Widget automatisch wenn User das Gerät entsperrt.
 *
 * DESIGN:
 *   - Registrierung in onCreate() mit RECEIVER_NOT_EXPORTED Flag (API 33+)
 *   - IntentFilter für Intent.ACTION_USER_PRESENT
 *   - Ruft TaskWidgetProvider.notifyWidgetUpdate() auf
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * MANIFEST-REGISTRIERUNG
 * ──────────────────────────────────────────────────────────────────────────────
 *
 *   <application
 *       android:name="activities.widget.WidgetRefreshApp"
 *       ...>
 *
 */

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import controller.WidgetUpdateManager;
import controller.WidgetUpdateManager.DataDomain;

public class WidgetRefreshApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Widgets beim WidgetUpdateManager registrieren
        WidgetUpdateManager.registerWidget(DataDomain.TODO, TaskWidgetProvider::notifyWidgetUpdate);
        WidgetUpdateManager.registerWidget(DataDomain.BUDGET, BudgetWidgetProvider::notifyWidgetUpdate);

        // BroadcastReceiver für Geräte-Unlock registrieren
        IntentFilter filter = new IntentFilter(Intent.ACTION_USER_PRESENT);

        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Alle Widgets aktualisieren wenn Geraet entsperrt wird
                WidgetUpdateManager.notifyUpdate(context, DataDomain.TODO);
                WidgetUpdateManager.notifyUpdate(context, DataDomain.BUDGET);
            }
        }, filter, Context.RECEIVER_NOT_EXPORTED);
    }
}
