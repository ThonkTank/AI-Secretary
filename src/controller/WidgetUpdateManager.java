package controller;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

/**
 * Zentrale Verwaltung für Widget-Update-Benachrichtigungen.
 *
 * Widgets registrieren sich hier; Manager benachrichtigen hier;
 * diese Klasse leitet an die passenden Widgets weiter.
 * Entkoppelt Manager von Widget-Implementierungen.
 *
 * Verwendung:
 *   // In Application.onCreate() oder Widget-Initialisierung:
 *   WidgetUpdateManager.registerWidget(DataDomain.BUDGET, ctx -> BudgetWidgetProvider.notifyWidgetUpdate(ctx));
 *
 *   // In Manager-Klassen:
 *   WidgetUpdateManager.notifyUpdate(context, DataDomain.BUDGET);
 */
public class WidgetUpdateManager {

    /**
     * Domänen für Widget-Updates.
     */
    public enum DataDomain {
        TODO,       // Task/Scheduling-Daten geändert
        BUDGET,     // Budget/Transaktions-Daten geändert
        MEAL        // Meal-Planning-Daten geändert
    }

    /**
     * Functional Interface für Widget-Update-Callbacks.
     */
    public interface WidgetUpdater {
        void update(Context context);
    }

    private static final List<Registration> registrations = new ArrayList<>();

    private record Registration(DataDomain domain, WidgetUpdater updater) {}

    /**
     * Registriert einen Widget-Updater für eine bestimmte Domäne.
     */
    public static void registerWidget(DataDomain domain, WidgetUpdater updater) {
        registrations.add(new Registration(domain, updater));
    }

    /**
     * Benachrichtigt alle registrierten Widgets für eine Domäne.
     */
    public static void notifyUpdate(Context context, DataDomain domain) {
        for (Registration reg : registrations) {
            if (reg.domain() == domain) {
                try {
                    reg.updater().update(context);
                } catch (Exception e) {
                    android.util.Log.w("WidgetUpdateManager",
                        "Widget update failed for " + domain + ": " + e.getMessage());
                }
            }
        }
    }

    /**
     * Entfernt alle Registrierungen (für Tests).
     */
    public static void clearRegistrations() {
        registrations.clear();
    }
}
