package scheduling;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import repository.SQLrepo;
import usecases.dailyPlanning.buildToDo;
import usecases.dailyPlanning.cleanToDo;

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

        // 2. Neue 7-Tage-Planung erstellen
        Log.d(TAG, "buildToDo.makeToDoList() startet");
        new buildToDo(context).makeToDoList();
        Log.d(TAG, "buildToDo.makeToDoList() fertig");

        // 3. Nächsten Alarm registrieren
        DailyPlanningScheduler.scheduleDaily(context);
        Log.d(TAG, "Nächster Alarm registriert");
    }
}
