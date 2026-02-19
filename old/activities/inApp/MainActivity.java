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

import activities.widget.BudgetWidgetProvider;
import activities.widget.TaskWidgetProvider;
import controller.budgetTab.BudgetManager;
import controller.mealTab.MealManager;
import controller.SettingsManager;
import controller.taskTab.TodoManager;
import controller.UpdateChecker;
import data.Constants;
import scheduling.DailyPlanningScheduler;
import repository.SQLrepo;
import scheduling.tasks.BuildToDo;
import scheduling.tasks.CalendarReader;
import android.content.Intent;

import activities.inApp.tasksTab.TaskView;
import activities.inApp.budgetTab.BudgetView;
import activities.inApp.ernaehrungTab.MealPlanView;

public class MainActivity extends Activity {

    private static final int REQUEST_CALENDAR_PERMISSION = 100;
    private static final int REQUEST_FILE_PICKER = 101;

    private static final int TAB_TASKS = 0;
    private static final int TAB_BUDGET = 1;
    private static final int TAB_ERNAEHRUNG = 2;

    private TextView tabTasks;
    private TextView tabBudget;
    private TextView tabErnaehrung;
    private View indicator;
    private FrameLayout content;

    private View tasksTab;
    private View budgetTab;
    private View ernaehrungTab;
    private TaskView taskViewInstance;
    private BudgetView budgetViewInstance;
    private MealPlanView mealPlanViewInstance;

    private TodoManager manager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Kalender-Berechtigung pruefen (fuer Calendar-Integration in BuildToDo)
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
        SharedPreferences prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
        int savedDbVersion = prefs.getInt(Constants.PREF_DB_VERSION, 0);
        int currentDbVersion = Constants.DB_VERSION;

        if (savedDbVersion != currentDbVersion) {

            // DB oeffnen triggert onCreate (Neuinstall) oder onUpgrade (Migration)
            SQLrepo repo = SQLrepo.getInstance(this);
            repo.getWritableDatabase().close();

            // Wochenplan neu erstellen
            new BuildToDo(SQLrepo.getInstance(this),
                (day, start, end) -> CalendarReader.getEventsForDay(this, day, start, end)
            ).planWeek();

            prefs.edit().putInt(Constants.PREF_DB_VERSION, currentDbVersion).apply();
        }

        // Taeglichen Mitternachts-Alarm registrieren
        DailyPlanningScheduler.scheduleDaily(this);

        // Manager initialisieren
        manager = new TodoManager(this);

        // Nach Update-Check: UI aufbauen
        new UpdateChecker(this).checkForUpdate(this::buildUI);
    }

    // ============================================================================
    // buildUI - Inflated Tab-Layout aus XML, bindet Listener
    // ============================================================================
    private void buildUI() {
        setContentView(R.layout.activity_main);

        tabTasks = findViewById(R.id.tab_tasks);
        tabBudget = findViewById(R.id.tab_budget);
        tabErnaehrung = findViewById(R.id.tab_ernaehrung);
        indicator = findViewById(R.id.tab_indicator);
        content = findViewById(R.id.content);

        tabTasks.setOnClickListener(v -> selectTab(TAB_TASKS));
        tabBudget.setOnClickListener(v -> selectTab(TAB_BUDGET));
        tabErnaehrung.setOnClickListener(v -> selectTab(TAB_ERNAEHRUNG));

        // Settings-Button (Overflow-Menu)
        findViewById(R.id.btn_settings).setOnClickListener(v -> {
            new SettingsManager(this, this::recreate).showSettingsMenu();
        });

        // Tab-Views erstellen (bleibt dynamisch)
        tasksTab = buildTasksView();
        budgetTab = buildBudgetView();
        ernaehrungTab = buildErnaehrungView();

        // Standardmaessig "Tasks" aktiv
        selectTab(TAB_TASKS);

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
                // Task-Widget "+" -> Tasks-Tab oeffnen und Create-Modal anzeigen
                selectTab(TAB_TASKS);
                if (taskViewInstance != null) {
                    taskViewInstance.openCreateModal();
                }
            }
            case BudgetWidgetProvider.ACTION_OPEN_BUDGET -> {
                // Budget-Widget Tap -> Zum Budget-Tab wechseln
                selectTab(TAB_BUDGET);
            }
            case BudgetWidgetProvider.ACTION_CREATE_TRANSACTION -> {
                // Budget-Widget "+" -> Zum Budget-Tab wechseln und Modal oeffnen
                selectTab(TAB_BUDGET);
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

        tabTasks.setTextColor(index == TAB_TASKS ? accent : secondary);
        tabBudget.setTextColor(index == TAB_BUDGET ? accent : secondary);
        tabErnaehrung.setTextColor(index == TAB_ERNAEHRUNG ? accent : secondary);

        switch (index) {
            case TAB_TASKS -> content.addView(tasksTab);
            case TAB_BUDGET -> content.addView(budgetTab);
            case TAB_ERNAEHRUNG -> content.addView(ernaehrungTab);
        }

        // Indicator auf 1/3 Breite setzen und horizontal verschieben
        indicator.post(() -> {
            int totalWidth = ((View) indicator.getParent()).getWidth();
            int thirdWidth = totalWidth / 3;
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) indicator.getLayoutParams();
            params.width = thirdWidth;
            params.setMarginStart(index * thirdWidth);
            indicator.setLayoutParams(params);
        });
    }

    // ============================================================================
    // buildTasksView - Delegiert an TaskView (Sub-Tabs: Tagesplan + Verwalten)
    // ============================================================================
    private View buildTasksView() {
        taskViewInstance = new TaskView(this, manager);
        return taskViewInstance.buildView();
    }

    // ============================================================================
    // buildBudgetView - Delegiert an BudgetView View-Builder
    // ============================================================================
    private View buildBudgetView() {
        budgetViewInstance = new BudgetView(this, new BudgetManager(this));
        budgetViewInstance.setFilePickerCallback(this::openFilePicker);
        return budgetViewInstance.buildView();
    }

    // ============================================================================
    // buildErnaehrungView - Delegiert an MealPlanView View-Builder
    // ============================================================================
    private View buildErnaehrungView() {
        mealPlanViewInstance = new MealPlanView(this, new MealManager(this));
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

                    // An BudgetView uebergeben
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
