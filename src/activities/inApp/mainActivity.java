package activities.inApp;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import static activities.generic.UIConstants.*;
import static activities.generic.ViewHelper.dp;

import activities.generic.taskList;
import controller.editorManager;
import controller.todoManager;
import controller.updateChecker;
import data.seedTestData;
import scheduling.DailyPlanningScheduler;
import usecases.daliyPlanning.buildToDo;

public class mainActivity extends Activity {

    private static final int REQUEST_CALENDAR_PERMISSION = 100;

    private TextView tabTagesplan;
    private TextView tabVerwalten;
    private View indicator;
    private FrameLayout content;

    private View tagesplanView;
    private View verwaltenView;

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
            new buildToDo(this).makeToDoList();
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
    // buildUI - Baut das gesamte Tab-Layout programmatisch auf
    // ============================================================================
    private void buildUI() {
        // Root: Vertikal
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(Color.WHITE);
        root.setFitsSystemWindows(true);

        // Tab-Bar
        root.addView(buildTabBar());

        // Indicator
        indicator = buildIndicator();
        root.addView(indicator);

        // Content-Bereich
        content = new FrameLayout(this);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        content.setLayoutParams(contentParams);
        root.addView(content);

        // Tab-Views erstellen
        tagesplanView = buildTagesplanView();
        verwaltenView = buildVerwaltenView();

        // Standardmäßig "Tagesplan" aktiv
        selectTab(0);

        setContentView(root);
    }

    // ============================================================================
    // buildTabBar - Erstellt die horizontale Tab-Leiste
    // ============================================================================
    private View buildTabBar() {
        LinearLayout tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(this, 48));
        tabBar.setLayoutParams(barParams);
        tabBar.setElevation(dp(this, 2));

        tabTagesplan = buildTabLabel("Tagesplan");
        tabTagesplan.setOnClickListener(v -> selectTab(0));
        tabBar.addView(tabTagesplan);

        tabVerwalten = buildTabLabel("Verwalten");
        tabVerwalten.setOnClickListener(v -> selectTab(1));
        tabBar.addView(tabVerwalten);

        return tabBar;
    }

    // ============================================================================
    // buildTabLabel - Erstellt ein einzelnes Tab-Label
    // ============================================================================
    private TextView buildTabLabel(String text) {
        TextView tab = new TextView(this);
        tab.setText(text);
        tab.setGravity(Gravity.CENTER);
        tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, SP_BODY);
        tab.setTypeface(null, Typeface.BOLD);
        tab.setTextColor(TEXT_SECONDARY);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        tab.setLayoutParams(params);
        return tab;
    }

    // ============================================================================
    // buildIndicator - Erstellt den farbigen Tab-Indicator (3dp Höhe)
    // ============================================================================
    private View buildIndicator() {
        View ind = new View(this);
        ind.setBackgroundColor(ACCENT);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(this, 3));
        ind.setLayoutParams(params);
        return ind;
    }

    // ============================================================================
    // selectTab - Wechselt aktiven Tab, bewegt Indicator, tauscht Content
    // ============================================================================
    private void selectTab(int index) {
        content.removeAllViews();

        if (index == 0) {
            tabTagesplan.setTextColor(ACCENT);
            tabVerwalten.setTextColor(TEXT_SECONDARY);
            content.addView(tagesplanView);
        } else {
            tabTagesplan.setTextColor(TEXT_SECONDARY);
            tabVerwalten.setTextColor(ACCENT);
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
        return new editItem(this, new editorManager(this)).buildView();
    }

}
