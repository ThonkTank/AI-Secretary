package activities.generic;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import controller.todoManager;
import controller.todoManager.TaskEntry;
import controller.todoManager.TodoListener;

public class taskList extends Activity implements TodoListener {

    /**
     * ══════════════════════════════════════════════════════════════════════════════
     * TASK LIST - Tagesplan-Anzeige als chronologische Aufgabenliste
     * ══════════════════════════════════════════════════════════════════════════════
     *
     * ZIEL:
     *   Stellt alle Tasks eines Tages als scrollbare Liste dar.
     *   Jede Task als eigene Kachel auf einer Timeline, mit Titel und
     *   Checkbox zum Abhaken, sowie Beschreibung falls vorhanden.
     *
     * ──────────────────────────────────────────────────────────────────────────────
     * DATENFLUSS
     * ──────────────────────────────────────────────────────────────────────────────
     *
     *   ┌─────────────────────────────────────────────────────────────────────────┐
     *   │                         todoManager                                     │
     *   │  provideList() → List<TaskEntry>                                        │
     *   │  completeSlot(slotId) → markiert + benachrichtigt Listener              │
     *   └───────────────────────────────┬─────────────────────────────────────────┘
     *                                   │
     *                                   ▼
     *   ┌─────────────────────────────────────────────────────────────────────────┐
     *   │                     taskList Activity (diese Klasse)                    │
     *   │  onCreate() → Manager init, Liste laden, UI aufbauen                    │
     *   │  render() → LinearLayout mit Goal-Headern und Task-Kacheln befüllen     │
     *   │  CheckBox-Click → completeSlot(slotId)                                  │
     *   │  onListUpdated() → UI neu aufbauen                                      │
     *   └─────────────────────────────────────────────────────────────────────────┘
     *
     * ──────────────────────────────────────────────────────────────────────────────
     * UI-AUFBAU (programmatisch)
     * ──────────────────────────────────────────────────────────────────────────────
     *
     *   ScrollView
     *   └── container (LinearLayout, vertikal)
     *       ├── Goal-Header (TextView, bold, Hintergrundfarbe)
     *       ├── Task-Kachel (LinearLayout, horizontal)
     *       │   ├── Zeitblock (TextView: "06:00\n06:02")
     *       │   ├── CheckBox
     *       │   └── Info-Block (LinearLayout, vertikal)
     *       │       ├── Titel (TextView, bold)
     *       │       ├── Dauer (TextView, grau, "2 min")
     *       │       └── Beschreibung (TextView, optional)
     *       ├── Task-Kachel ...
     *       ├── Goal-Header ...
     *       └── ...
     *
     */

    private todoManager manager;
    private LinearLayout container;

    // ============================================================================
    // onCreate - Activity-Einstiegspunkt: Manager init, UI aufbauen
    // ============================================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        manager = new todoManager();
        manager.setListener(this);

        // ScrollView als Root
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        // Container für alle Einträge
        container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(16), dp(16), dp(16));
        scrollView.addView(container);

        setContentView(scrollView);

        render();
    }

    // ============================================================================
    // render - Baut die Task-Liste als Views im Container auf
    // ============================================================================
    private void render() {
        container.removeAllViews();

        List<TaskEntry> entries = manager.provideList();

        if (entries.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Keine Aufgaben für heute.");
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(64), 0, 0);
            container.addView(empty);
            return;
        }

        String currentGoal = "";

        for (TaskEntry entry : entries) {
            // Goal-Header bei Wechsel
            if (!entry.goalTitle().equals(currentGoal)) {
                currentGoal = entry.goalTitle();
                container.addView(buildGoalHeader(currentGoal));
            }
            // Task-Kachel
            container.addView(buildTaskCard(entry));
        }
    }

    // ============================================================================
    // buildGoalHeader - Erstellt einen Goal-Abschnitts-Header
    // ============================================================================
    private View buildGoalHeader(String title) {
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        header.setTypeface(null, Typeface.BOLD);
        header.setTextColor(Color.WHITE);
        header.setBackgroundColor(0xFF455A64); // Blaugrau
        header.setPadding(dp(12), dp(8), dp(12), dp(8));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(12), 0, 0);
        header.setLayoutParams(params);

        return header;
    }

    // ============================================================================
    // buildTaskCard - Erstellt eine Task-Kachel mit Checkbox, Zeit und Info
    // ============================================================================
    private View buildTaskCard(TaskEntry entry) {
        // Äußere Kachel: horizontal
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dp(8), dp(10), dp(8), dp(10));
        card.setBackgroundColor(entry.completed() ? 0xFFE8F5E9 : 0xFFFAFAFA);
        card.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, dp(1), 0, 0);
        card.setLayoutParams(cardParams);

        // Zeitblock (start/end untereinander)
        TextView timeView = new TextView(this);
        timeView.setText(entry.start().toString() + "\n" + entry.end().toString());
        timeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        timeView.setTextColor(0xFF757575);
        timeView.setTypeface(Typeface.MONOSPACE);
        timeView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(dp(48), LinearLayout.LayoutParams.WRAP_CONTENT);
        timeView.setLayoutParams(timeParams);
        card.addView(timeView);

        // Checkbox
        CheckBox checkBox = new CheckBox(this);
        checkBox.setChecked(entry.completed());
        checkBox.setOnCheckedChangeListener((btn, checked) -> {
            if (checked && !entry.completed()) {
                manager.completeSlot(entry.slotId());
            }
        });
        card.addView(checkBox);

        // Info-Block (Titel, Dauer, Beschreibung)
        LinearLayout infoBlock = new LinearLayout(this);
        infoBlock.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        infoBlock.setLayoutParams(infoParams);

        // Titel
        TextView titleView = new TextView(this);
        titleView.setText(entry.taskTitle());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        titleView.setTypeface(null, Typeface.BOLD);
        if (entry.completed()) {
            titleView.setTextColor(0xFF9E9E9E);
            titleView.setPaintFlags(titleView.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        }
        infoBlock.addView(titleView);

        // Dauer
        TextView durationView = new TextView(this);
        durationView.setText(entry.timeToComplete() + " min");
        durationView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        durationView.setTextColor(0xFF9E9E9E);
        infoBlock.addView(durationView);

        // Beschreibung (falls vorhanden)
        if (entry.taskDescription() != null && !entry.taskDescription().isEmpty()) {
            TextView descView = new TextView(this);
            descView.setText(entry.taskDescription());
            descView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            descView.setTextColor(0xFF616161);
            descView.setMaxLines(2);
            infoBlock.addView(descView);
        }

        card.addView(infoBlock);
        return card;
    }

    // ============================================================================
    // onListUpdated - TodoListener: UI nach Completion neu aufbauen
    // ============================================================================
    @Override
    public void onListUpdated() {
        runOnUiThread(this::render);
    }

    // ============================================================================
    // dp - Konvertiert dp zu Pixel
    // ============================================================================
    private int dp(int value) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }
}
