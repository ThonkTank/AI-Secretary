package activities.inApp;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.autosecretary.R;

import activities.generic.taskList;
import activities.widget.TaskWidgetProvider;
import controller.editorManager;
import controller.todoManager;
import controller.updateChecker;
import data.seedTestData;
import scheduling.DailyPlanningScheduler;
import repository.SQLrepo;
import scheduling.buildToDo;
import scheduling.CalendarReader;
import android.content.Intent;

public class mainActivity extends Activity {

    private static final int REQUEST_CALENDAR_PERMISSION = 100;

    private TextView tabTagesplan;
    private TextView tabVerwalten;
    private View indicator;
    private FrameLayout content;

    private View tagesplanView;
    private View verwaltenView;
    private editItem verwaltenEditor;

    private todoManager manager;
    private taskList taskListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Kalender-Berechtigung pruefen (fuer Calendar-Integration in buildToDo)
        if (checkSelfPermission(android.Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                new String[]{android.Manifest.permission.READ_CALENDAR},
                REQUEST_CALENDAR_PERMISSION);
        } else {
            initApp();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CALENDAR_PERMISSION) {
            // Egal ob granted oder denied: App laeuft weiter
            // Ohne Permission werden keine Calendar-Events geladen
            initApp();
        }
    }

    private void initApp() {
        // Bei jedem Update: DB löschen und mit sauberen Testdaten neu seeden
        SharedPreferences prefs = getSharedPreferences("secretary", MODE_PRIVATE);
        int currentVersion = data.constants.DB_VERSION;
        if (prefs.getInt("db_version", 0) != currentVersion) {
            deleteDatabase(data.constants.DB_NAME);
            new seedTestData(this).seed();
            new buildToDo(new SQLrepo(this),
                (day, start, end) -> CalendarReader.getEventsForDay(this, day, start, end)
            ).planWeek();
            prefs.edit().putInt("db_version", currentVersion).apply();
        }

        // Täglichen Mitternachts-Alarm registrieren
        DailyPlanningScheduler.scheduleDaily(this);

        // Manager initialisieren
        manager = new todoManager(this);

        // Nach Update-Check: UI aufbauen
        new updateChecker(this).checkForUpdate(this::buildUI);
    }

    // ============================================================================
    // buildUI - Inflated Tab-Layout aus XML, bindet Listener
    // ============================================================================
    private void buildUI() {
        setContentView(R.layout.activity_main);

        tabTagesplan = findViewById(R.id.tab_tagesplan);
        tabVerwalten = findViewById(R.id.tab_verwalten);
        indicator = findViewById(R.id.tab_indicator);
        content = findViewById(R.id.content);

        tabTagesplan.setOnClickListener(v -> selectTab(0));
        tabVerwalten.setOnClickListener(v -> selectTab(1));

        // Tab-Views erstellen (bleibt dynamisch)
        tagesplanView = buildTagesplanView();
        verwaltenView = buildVerwaltenView();

        // Standardmaessig "Tagesplan" aktiv
        selectTab(0);

        // Intent-Extra prüfen für Widget-Navigation
        handleWidgetIntent(getIntent());
    }

    // ============================================================================
    // onNewIntent - Verarbeitet Intents wenn Activity bereits läuft
    // ============================================================================
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleWidgetIntent(intent);
    }

    // ============================================================================
    // handleWidgetIntent - Öffnet Create-Modal bei Widget-"+" Klick
    // ============================================================================
    private void handleWidgetIntent(Intent intent) {
        if (intent != null && TaskWidgetProvider.ACTION_CREATE_ITEM.equals(intent.getAction())) {
            // Zum Verwalten-Tab wechseln und Modal öffnen
            selectTab(1);
            if (verwaltenEditor != null) {
                verwaltenEditor.openCreateModal();
            }
            // Intent-Action löschen um Wiederholung bei Config-Change zu verhindern
            intent.setAction(null);
        }
    }

    // ============================================================================
    // selectTab - Wechselt aktiven Tab, bewegt Indicator, tauscht Content
    // ============================================================================
    private void selectTab(int index) {
        content.removeAllViews();

        int accent = ContextCompat.getColor(this, R.color.accent);
        int secondary = ContextCompat.getColor(this, R.color.text_secondary);

        if (index == 0) {
            tabTagesplan.setTextColor(accent);
            tabVerwalten.setTextColor(secondary);
            content.addView(tagesplanView);
        } else {
            tabTagesplan.setTextColor(secondary);
            tabVerwalten.setTextColor(accent);
            content.addView(verwaltenView);
        }

        // Indicator auf halbe Breite setzen und horizontal verschieben
        indicator.post(() -> {
            int totalWidth = ((View) indicator.getParent()).getWidth();
            int halfWidth = totalWidth / 2;
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) indicator.getLayoutParams();
            params.width = halfWidth;
            params.setMarginStart(index * halfWidth);
            indicator.setLayoutParams(params);
        });
    }

    // ============================================================================
    // buildTagesplanView - Delegiert an taskList View-Builder
    // ============================================================================
    private View buildTagesplanView() {
        taskListView = new taskList(this, manager);
        return taskListView.buildView();
    }

    // ============================================================================
    // buildVerwaltenView - Delegiert an editItem View-Builder
    // ============================================================================
    private View buildVerwaltenView() {
        verwaltenEditor = new editItem(this, new editorManager(this));
        return verwaltenEditor.buildView();
    }

}
