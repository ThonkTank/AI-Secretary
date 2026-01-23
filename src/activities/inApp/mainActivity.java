package activities.inApp;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import activities.generic.taskList;
import controller.updateChecker;
import data.seedTestData;
import usecases.daliyPlanning.buildToDo;

/**
 * Einstiegspunkt der App: Initialisierung, Update-Check, dann Weiterleitung zur TaskList.
 */
public class mainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Beim ersten Start: Testdaten einfügen und Tagesplan generieren
        SharedPreferences prefs = getSharedPreferences("secretary", MODE_PRIVATE);
        if (!prefs.getBoolean("seeded", false)) {
            new seedTestData(this).seed();
            new buildToDo(this).makeToDoList();
            prefs.edit().putBoolean("seeded", true).apply();
        }

        // Auf Updates prüfen, danach zur Aufgabenliste weiterleiten
        new updateChecker(this).checkForUpdate(() -> {
            startActivity(new Intent(this, taskList.class));
            finish();
        });
    }
}
