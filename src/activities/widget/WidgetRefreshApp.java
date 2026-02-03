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

public class WidgetRefreshApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // BroadcastReceiver für Geräte-Unlock registrieren
        IntentFilter filter = new IntentFilter(Intent.ACTION_USER_PRESENT);

        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Widget aktualisieren wenn Gerät entsperrt wird
                TaskWidgetProvider.notifyWidgetUpdate(context);
            }
        }, filter, Context.RECEIVER_NOT_EXPORTED);
    }
}
