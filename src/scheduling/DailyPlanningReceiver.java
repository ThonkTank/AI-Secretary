package scheduling;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import repository.SQLrepo;
import scheduling.buildToDo;
import scheduling.CalendarReader;
import scheduling.cleanToDo;

/**
 * Wird täglich um 00:00 vom AlarmManager getriggert.
 * Führt erst cleanToDo (gestern auswerten), dann buildToDo (neue Woche planen) aus.
 */
public class DailyPlanningReceiver extends BroadcastReceiver {

    private static final String TAG = "DailyPlanning";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Alarm ausgelöst – starte Tagesplanung");
        SQLrepo repo = new SQLrepo(context);

        // 1. Gestrige Liste auswerten und aufräumen
        Log.d(TAG, "cleanToDo.clean() startet");
        cleanToDo.clean(repo);
        Log.d(TAG, "cleanToDo.clean() fertig");

        // 2. Neue 7-Tage-Planung erstellen (V2: globale Slot-Bewertung)
        Log.d(TAG, "buildToDo.planWeek() startet");
        new buildToDo(repo,
            (day, start, end) -> CalendarReader.getEventsForDay(context, day, start, end)
        ).planWeek();
        Log.d(TAG, "buildToDo.planWeek() fertig");

        // 3. Nächsten Alarm registrieren
        DailyPlanningScheduler.scheduleDaily(context);
        Log.d(TAG, "Nächster Alarm registriert");
    }
}
