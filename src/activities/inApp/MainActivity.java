package activities.inApp;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.autosecretary.R;

import java.io.InputStream;

import activities.generic.taskList;
import activities.widget.BudgetWidgetProvider;
import activities.widget.TaskWidgetProvider;
import controller.budgetManager;
import controller.editorManager;
import controller.mealManager;
import controller.SettingsManager;
import controller.todoManager;
import controller.updateChecker;
import data.constants;
import repository.MigrationManager;
import scheduling.DailyPlanningScheduler;
import repository.SQLrepo;
import scheduling.buildToDo;
import scheduling.CalendarReader;
import android.content.Intent;

public class mainActivity extends Activity {

    private static final int REQUEST_CALENDAR_PERMISSION = 100;
    private static final int REQUEST_FILE_PICKER = 101;

    private TextView tabTagesplan;
    private TextView tabVerwalten;
    private TextView tabBudget;
    private TextView tabErnaehrung;
    private View indicator;
    private FrameLayout content;

    private View tagesplanView;
    private View verwaltenView;
    private View budgetViewContainer;
    private View ernaehrungView;
    private editItem verwaltenEditor;
    private budgetView budgetViewInstance;
    private mealPlanView mealPlanViewInstance;

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
        MigrationManager migrationManager = new MigrationManager(this);
        SharedPreferences prefs = getSharedPreferences(constants.PREF_NAME, MODE_PRIVATE);
        int savedDbVersion = prefs.getInt(constants.PREF_DB_VERSION, 0);
        int currentDbVersion = constants.DB_VERSION;

        if (savedDbVersion != currentDbVersion) {
            // Backup vor Migration erstellen (nur bei Upgrade, nicht bei Neuinstallation)
            if (savedDbVersion > 0 && savedDbVersion < currentDbVersion) {
                migrationManager.createBackup(savedDbVersion);
            }

            // DB oeffnen triggert onCreate (Neuinstall) oder onUpgrade (Migration)
            SQLrepo repo = new SQLrepo(this);
            repo.getWritableDatabase().close();

            // Erstes Produktions-Launch erkennen und markieren
            if (migrationManager.isFirstProductionLaunch()) {
                migrationManager.setProductionMode();
            }

            // Wochenplan neu erstellen
            new buildToDo(new SQLrepo(this),
                (day, start, end) -> CalendarReader.getEventsForDay(this, day, start, end)
            ).planWeek();

            prefs.edit().putInt(constants.PREF_DB_VERSION, currentDbVersion).apply();
        }

        // Taeglichen Mitternachts-Alarm registrieren
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
        tabBudget = findViewById(R.id.tab_budget);
        tabErnaehrung = findViewById(R.id.tab_ernaehrung);
        indicator = findViewById(R.id.tab_indicator);
        content = findViewById(R.id.content);

        tabTagesplan.setOnClickListener(v -> selectTab(0));
        tabVerwalten.setOnClickListener(v -> selectTab(1));
        tabBudget.setOnClickListener(v -> selectTab(2));
        tabErnaehrung.setOnClickListener(v -> selectTab(3));

        // Settings-Button (Overflow-Menu)
        findViewById(R.id.btn_settings).setOnClickListener(v -> {
            new SettingsManager(this, this::recreate).showSettingsMenu();
        });

        // Tab-Views erstellen (bleibt dynamisch)
        tagesplanView = buildTagesplanView();
        verwaltenView = buildVerwaltenView();
        budgetViewContainer = buildBudgetView();
        ernaehrungView = buildErnaehrungView();

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
    // handleWidgetIntent - Verarbeitet Widget-Intents (Task + Budget)
    // ============================================================================
    private void handleWidgetIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (action == null) return;

        switch (action) {
            case TaskWidgetProvider.ACTION_CREATE_ITEM -> {
                // Task-Widget "+" -> Zum Verwalten-Tab wechseln und Modal oeffnen
                selectTab(1);
                if (verwaltenEditor != null) {
                    verwaltenEditor.openCreateModal();
                }
            }
            case BudgetWidgetProvider.ACTION_OPEN_BUDGET -> {
                // Budget-Widget Tap -> Zum Budget-Tab wechseln
                selectTab(2);
            }
            case BudgetWidgetProvider.ACTION_CREATE_TRANSACTION -> {
                // Budget-Widget "+" -> Zum Budget-Tab wechseln und Modal oeffnen
                selectTab(2);
                if (budgetViewInstance != null) {
                    budgetViewInstance.showTransactionModal(null);
                }
            }
            default -> { return; }
        }

        // Intent-Action loeschen um Wiederholung bei Config-Change zu verhindern
        intent.setAction(null);
    }

    // ============================================================================
    // selectTab - Wechselt aktiven Tab, bewegt Indicator, tauscht Content
    // ============================================================================
    private void selectTab(int index) {
        content.removeAllViews();

        int accent = ContextCompat.getColor(this, R.color.accent);
        int secondary = ContextCompat.getColor(this, R.color.text_secondary);

        tabTagesplan.setTextColor(index == 0 ? accent : secondary);
        tabVerwalten.setTextColor(index == 1 ? accent : secondary);
        tabBudget.setTextColor(index == 2 ? accent : secondary);
        tabErnaehrung.setTextColor(index == 3 ? accent : secondary);

        switch (index) {
            case 0 -> content.addView(tagesplanView);
            case 1 -> content.addView(verwaltenView);
            case 2 -> content.addView(budgetViewContainer);
            case 3 -> content.addView(ernaehrungView);
        }

        // Indicator auf 1/4 Breite setzen und horizontal verschieben
        indicator.post(() -> {
            int totalWidth = ((View) indicator.getParent()).getWidth();
            int quarterWidth = totalWidth / 4;
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) indicator.getLayoutParams();
            params.width = quarterWidth;
            params.setMarginStart(index * quarterWidth);
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

    // ============================================================================
    // buildBudgetView - Delegiert an budgetView View-Builder
    // ============================================================================
    private View buildBudgetView() {
        budgetViewInstance = new budgetView(this, new budgetManager(this));
        budgetViewInstance.setFilePickerCallback(this::openFilePicker);
        return budgetViewInstance.buildView();
    }

    // ============================================================================
    // buildErnaehrungView - Delegiert an mealPlanView View-Builder
    // ============================================================================
    private View buildErnaehrungView() {
        mealPlanViewInstance = new mealPlanView(this, new mealManager(this));
        return mealPlanViewInstance.buildView();
    }

    // ============================================================================
    // FILE PICKER FOR IMPORT
    // ============================================================================

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/pdf", "text/csv", "text/plain"});
        startActivityForResult(intent, REQUEST_FILE_PICKER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_FILE_PICKER && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null && budgetViewInstance != null) {
                try {
                    // Dateiname extrahieren
                    String fileName = getFileNameFromUri(uri);

                    // MIME-Typ bestimmen
                    String mimeType = getContentResolver().getType(uri);
                    if (mimeType == null) {
                        mimeType = "application/pdf"; // Fallback
                    }

                    // Dateiinhalt lesen
                    InputStream inputStream = getContentResolver().openInputStream(uri);
                    byte[] fileBytes = inputStream.readAllBytes();
                    inputStream.close();

                    // An budgetView uebergeben
                    budgetViewInstance.onFileSelected(fileName, fileBytes, mimeType);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String fileName = "Unbekannt";
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return fileName;
    }

}
