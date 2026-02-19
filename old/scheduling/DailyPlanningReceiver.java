package scheduling;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import controller.WidgetUpdateManager;
import controller.WidgetUpdateManager.DataDomain;
import repository.SQLrepo;
import scheduling.tasks.BuildToDo;
import scheduling.tasks.CalendarReader;
import scheduling.tasks.CleanToDo;

/**
 * Wird täglich um 00:00 vom AlarmManager getriggert.
 * Führt erst CleanToDo (gestern auswerten), dann BuildToDo (neue Woche planen) aus.
 */
public class DailyPlanningReceiver extends BroadcastReceiver {

    private static final String TAG = "DailyPlanning";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Alarm ausgelöst – starte Tagesplanung");
        SQLrepo repo = SQLrepo.getInstance(context);

        // 1. Gestrige Liste auswerten und aufräumen
        Log.d(TAG, "CleanToDo.clean() startet");
        CleanToDo.clean(repo);
        Log.d(TAG, "CleanToDo.clean() fertig");

        // 2. Neue 7-Tage-Planung erstellen (V2: globale Slot-Bewertung)
        Log.d(TAG, "BuildToDo.planWeek() startet");
        new BuildToDo(repo,
            (day, start, end) -> CalendarReader.getEventsForDay(context, day, start, end)
        ).planWeek();
        Log.d(TAG, "BuildToDo.planWeek() fertig");

        // TODO: Einkaufs-TrackedItem abfragen (ist heute scheduled?) und ggf.
        // GenerateMealPlan fuer die naechste Periode triggern.
        // Perioden-Grenzen aus TrackedItem.calcNextRepetition() ableiten.

        // 3. Nächsten Alarm registrieren
        DailyPlanningScheduler.scheduleDaily(context);
        Log.d(TAG, "Nächster Alarm registriert");

        // 4. Widget aktualisieren (via decoupled Manager)
        WidgetUpdateManager.notifyUpdate(context, DataDomain.TODO);
        Log.d(TAG, "Widget aktualisiert");
    }
}
