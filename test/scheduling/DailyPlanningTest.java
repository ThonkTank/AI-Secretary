package scheduling;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import data.seedTestData;
import repository.SQLrepo;
import usecases.daliyPlanning.buildToDo;
import usecases.daliyPlanning.cleanToDo;

@RunWith(RobolectricTestRunner.class)
public class DailyPlanningTest {

    private Context context;
    private SQLrepo repo;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("autosecretary.db");
        repo = new SQLrepo(context);
        // Testdaten seeden (wie die App es macht)
        new seedTestData(context).seed();
        new buildToDo(context).makeToDoList();
    }

    @Test
    public void dailyPlanning_vollstaendigerZyklus() {
        // Simuliert was der Receiver um 00:00 macht:
        // 1. Gestrige Liste auswerten
        System.out.println("=== cleanToDo.clean() ===");
        cleanToDo.clean(repo);
        System.out.println("cleanToDo fertig");

        // 2. Neue 7-Tage-Planung
        System.out.println("=== buildToDo.makeToDoList() ===");
        new buildToDo(context).makeToDoList();
        System.out.println("buildToDo fertig");

        // Wenn wir hier ankommen ohne Exception, funktioniert der Zyklus
        assertTrue("Daily Planning Zyklus durchgelaufen", true);
    }
}
