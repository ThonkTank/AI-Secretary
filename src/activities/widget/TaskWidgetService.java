package activities.widget;

/**
 * ══════════════════════════════════════════════════════════════════════════════
 * TASK WIDGET SERVICE - RemoteViewsService für Widget-Liste
 * ══════════════════════════════════════════════════════════════════════════════
 *
 * ZIEL:
 *   Android-required Service-Komponente für ListView-Widgets.
 *   Erstellt TaskWidgetFactory-Instanzen für jeden Widget.
 *
 * DESIGN:
 *   - Minimaler Boilerplate: nur onGetViewFactory() implementiert
 *   - Factory bekommt ApplicationContext übergeben
 *   - Manifest: android.permission.BIND_REMOTEVIEWS required
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * MANIFEST-REGISTRIERUNG
 * ──────────────────────────────────────────────────────────────────────────────
 *
 *   <service
 *       android:name="activities.widget.TaskWidgetService"
 *       android:permission="android.permission.BIND_REMOTEVIEWS"
 *       android:exported="false" />
 *
 */

import android.content.Intent;
import android.widget.RemoteViewsService;

public class TaskWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new TaskWidgetFactory(getApplicationContext());
    }
}
