package activities.generic;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import static activities.generic.UIConstants.*;
import static activities.generic.ViewHelper.dp;

import java.util.List;

import controller.todoManager;
import controller.todoManager.TaskEntry;
import controller.todoManager.TodoListener;
import usecases.dailyPlanning.buildToDo;

/**
 * ══════════════════════════════════════════════════════════════════════════════
 * TASK LIST VIEW - Dokumentation
 * ══════════════════════════════════════════════════════════════════════════════
 *
 * ZIEL:
 *   View-Builder für die Tagesplan-Aufgabenliste.
 *   Erstellt eine scrollbare Liste mit Goal-Headern und Task-Kacheln,
 *   die der Nutzer per Checkbox abhaken kann.
 *
 * NUTZUNG:
 *   taskList list = new taskList(context, manager);
 *   View view = list.buildView();
 *   list.setOnUpdate(() -> updateHeader());  // optional
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * VIEW-STRUKTUR
 * ──────────────────────────────────────────────────────────────────────────────
 *
 *   ┌─────────────────────────────────────────────────────────────────────────┐
 *   │ ScrollView (fillViewport=true)                                          │
 *   │  └─ LinearLayout (vertical, padding=16dp)                               │
 *   │      ├─ Button "Neu planen" (ACCENT-Hintergrund, löst rebuild aus)     │
 *   │      ├─ [Goal-Header: farbiger Balken mit Goal-Titel]                  │
 *   │      ├─ [TaskCard: Zeit | Checkbox | Timer | Titel + Dauer + Beschr.]  │
 *   │      ├─ [TaskCard: ...]                                                 │
 *   │      ├─ [CalendarCard: Zeit | Titel (kein Checkbox)]                   │
 *   │      ├─ [Goal-Header: nächstes Goal]                                   │
 *   │      ├─ [TaskCard: ...]                                                 │
 *   │      └─ ...                                                             │
 *   └─────────────────────────────────────────────────────────────────────────┘
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * DATENFLUSS
 * ──────────────────────────────────────────────────────────────────────────────
 *
 *  todoManager.provideList() → List<TaskEntry>
 *      → Gruppiert nach goalTitle (aufeinanderfolgende Einträge desselben Goals)
 *      → Jeder TaskEntry enthält: slotId, goalTitle, taskTitle, taskDescription,
 *      start, end, timeToComplete, completed, isCalendarEvent
 *
 *  Checkbox-Interaktion:
 *      checked   → manager.completeSlot(slotId)
 *      unchecked → manager.uncompleteSlot(slotId)
 *      → todoManager feuert onListUpdated() → re-render
 *  
 *  Timer-Interaktion:
 *      Der Timer sitzt neben der Checkbox und bietet einen alternativen Weg für Task Completion
 *      Der erste Klick markiert, dass die bearbeitung begonnen wurde, der zweite die beendigung.
 *      Pro klick wird LocalTime.now gespeichert.
 *      Der Slot wird wie gewohnt completed, aber workStart und workEnd des Slots werden zusätzlich auf start und endzeitgesetzt.
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * METHODEN-ABLAUF
 * ──────────────────────────────────────────────────────────────────────────────
 *
 *  buildView() - Erstellt ScrollView + Container, ruft render() auf
 *      Return: ScrollView mit gerenderter Task-Liste
 *
 *  render() - Baut den Container-Inhalt komplett neu auf
 *      1. Container leeren (removeAllViews)
 *      2. "Neu planen"-Button einfügen (→ buildToDo.makeToDoList + re-render)
 *      3. manager.provideList() → entries holen
 *      4. Falls leer: "Keine Aufgaben für heute." anzeigen
 *      5. Sonst: Für jeden Entry:
 *         → Neuer goalTitle? → buildGoalHeader(title) einfügen
 *         → buildTaskCard(entry) einfügen
 *
 *  onListUpdated() - TodoListener-Callback bei Listenänderung
 *      → post(render) auf UI-Thread + optionaler onUpdate-Callback
 *
 *  buildGoalHeader(title) - Goal-Abschnitts-Header
 *      → Weißer Text auf HEADER_BG, bold, 12dp Padding, 12dp topMargin
 *
 *  buildCalendarCard(entry) - Kalender-Event-Kachel (ohne Checkbox)
 *      → Zeitblock (start/end monospace) | Titel (ACCENT, bold)
 *      → CALENDAR_EVENT_BG Hintergrund
 *
 *  buildTaskCard(entry) - Task-Kachel mit Checkbox, Timer, Zeit und Info
 *      → Delegiert an buildCalendarCard falls isCalendarEvent
 *      → Zeitblock | Checkbox | Timer | InfoBlock (Titel, Dauer, Beschreibung)
 *      → Timer: ▶ (idle) → ⏹ (gestartet, ACCENT) → TEXT_MUTED (completed)
 *        Klick 1: manager.startTimer() → workStart = now
 *        Klick 2: manager.stopTimer() → workEnd = now + completeSlot()
 *      → Completed: SURFACE_COMPLETE bg, durchgestrichener Titel, TEXT_MUTED
 *      → Nicht completed: SURFACE bg, normaler Titel
 *
 */
public class taskList implements TodoListener, ViewBuilder {

    private Context context;
    private todoManager manager;
    private LinearLayout container;
    private Runnable onUpdate;

    public taskList(Context context, todoManager manager) {
        this.context = context;
        this.manager = manager;
        this.manager.setListener(this);
    }

    /** Callback bei Listenänderung (optional) */
    public void setOnUpdate(Runnable onUpdate) {
        this.onUpdate = onUpdate;
    }

    /** Baut die komplette Task-Listen-View und gibt sie zurück */
    public View buildView() {
        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);

        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16));
        scrollView.addView(container);

        render();
        return scrollView;
    }

    /** Rendert die Task-Liste in den Container */
    public void render() {
        container.removeAllViews();

        // Neu-Planen Button
        Button replanBtn = new Button(context);
        replanBtn.setText("Neu planen");
        replanBtn.setTextColor(Color.WHITE);
        replanBtn.setBackgroundColor(ACCENT);
        replanBtn.setOnClickListener(v -> {
            new buildToDo(context).makeToDoList();
            render();
        });
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(0, 0, 0, dp(context, 12));
        replanBtn.setLayoutParams(btnParams);
        container.addView(replanBtn);

        List<TaskEntry> entries = manager.provideList();

        if (entries.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("Keine Aufgaben für heute.");
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, SP_TITLE);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(context, 64), 0, 0);
            container.addView(empty);
            return;
        }

        String currentGoal = "";
        for (TaskEntry entry : entries) {
            if (!entry.goalTitle().equals(currentGoal)) {
                currentGoal = entry.goalTitle();
                container.addView(buildGoalHeader(currentGoal));
            }
            container.addView(buildTaskCard(entry));
        }
    }

    @Override
    public void onListUpdated() {
        container.post(this::render);
        if (onUpdate != null) onUpdate.run();
    }

    // ============================================================================
    // buildGoalHeader - Goal-Abschnitts-Header
    // ============================================================================
    private View buildGoalHeader(String title) {
        TextView header = new TextView(context);
        header.setText(title);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, SP_BODY);
        header.setTypeface(null, Typeface.BOLD);
        header.setTextColor(Color.WHITE);
        header.setBackgroundColor(HEADER_BG);
        header.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(context, 12), 0, 0);
        header.setLayoutParams(params);

        return header;
    }

    // ============================================================================
    // buildCalendarCard - Kalender-Event-Kachel (kein Checkbox)
    // ============================================================================
    private View buildCalendarCard(TaskEntry entry) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dp(context, 8), dp(context, 10), dp(context, 8), dp(context, 10));
        card.setBackgroundColor(CALENDAR_EVENT_BG);
        card.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, dp(context, 1), 0, 0);
        card.setLayoutParams(cardParams);

        // Zeitblock
        TextView timeView = new TextView(context);
        timeView.setText(entry.start().toString() + "\n" + entry.end().toString());
        timeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, SP_BADGE);
        timeView.setTextColor(TEXT_SECONDARY);
        timeView.setTypeface(Typeface.MONOSPACE);
        timeView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
            dp(context, 48), LinearLayout.LayoutParams.WRAP_CONTENT);
        timeView.setLayoutParams(timeParams);
        card.addView(timeView);

        // Titel
        TextView titleView = new TextView(context);
        titleView.setText(entry.taskTitle());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, SP_TITLE);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(ACCENT);
        titleView.setPadding(dp(context, 12), 0, 0, 0);
        card.addView(titleView);

        return card;
    }

    // ============================================================================
    // buildTaskCard - Task-Kachel mit Checkbox, Zeit und Info
    // ============================================================================
    private View buildTaskCard(TaskEntry entry) {
        if (entry.isCalendarEvent()) return buildCalendarCard(entry);
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dp(context, 8), dp(context, 10), dp(context, 8), dp(context, 10));
        card.setBackgroundColor(entry.completed() ? SURFACE_COMPLETE : SURFACE);
        card.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, dp(context, 1), 0, 0);
        card.setLayoutParams(cardParams);

        // Zeitblock
        TextView timeView = new TextView(context);
        timeView.setText(entry.start().toString() + "\n" + entry.end().toString());
        timeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, SP_BADGE);
        timeView.setTextColor(TEXT_SECONDARY);
        timeView.setTypeface(Typeface.MONOSPACE);
        timeView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
            dp(context, 48), LinearLayout.LayoutParams.WRAP_CONTENT);
        timeView.setLayoutParams(timeParams);
        card.addView(timeView);

        // Checkbox
        CheckBox checkBox = new CheckBox(context);
        checkBox.setChecked(entry.completed());
        checkBox.setOnCheckedChangeListener((btn, checked) -> {
            if (checked && !entry.completed()) {
                manager.completeSlot(entry.slotId());
            } else if (!checked && entry.completed()) {
                manager.uncompleteSlot(entry.slotId());
            }
        });
        card.addView(checkBox);

        // Timer-Button
        TextView timerBtn = new TextView(context);
        boolean timerStarted = entry.workStart() != null;
        timerBtn.setText(timerStarted ? "\u23F9" : "\u25B6");
        timerBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, SP_HEADING);
        timerBtn.setTextColor(timerStarted ? ACCENT : TEXT_SECONDARY);
        timerBtn.setGravity(Gravity.CENTER);
        timerBtn.setPadding(dp(context, 8), 0, dp(context, 8), 0);
        if (!entry.completed()) {
            timerBtn.setOnClickListener(v -> {
                if (entry.workStart() == null) {
                    manager.startTimer(entry.slotId());
                } else {
                    manager.stopTimer(entry.slotId());
                }
            });
        } else {
            timerBtn.setTextColor(TEXT_MUTED);
        }
        card.addView(timerBtn);

        // Info-Block
        LinearLayout infoBlock = new LinearLayout(context);
        infoBlock.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        infoBlock.setLayoutParams(infoParams);

        // Titel
        TextView titleView = new TextView(context);
        titleView.setText(entry.taskTitle());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, SP_TITLE);
        titleView.setTypeface(null, Typeface.BOLD);
        if (entry.completed()) {
            titleView.setTextColor(TEXT_MUTED);
            titleView.setPaintFlags(titleView.getPaintFlags()
                | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        }
        infoBlock.addView(titleView);

        // Dauer
        TextView durationView = new TextView(context);
        durationView.setText(entry.timeToComplete() + " min");
        durationView.setTextSize(TypedValue.COMPLEX_UNIT_SP, SP_SMALL);
        durationView.setTextColor(TEXT_MUTED);
        infoBlock.addView(durationView);

        // Beschreibung (falls vorhanden)
        if (entry.taskDescription() != null && !entry.taskDescription().isEmpty()) {
            TextView descView = new TextView(context);
            descView.setText(entry.taskDescription());
            descView.setTextSize(TypedValue.COMPLEX_UNIT_SP, SP_SMALL);
            descView.setTextColor(0xFF616161);
            descView.setMaxLines(2);
            infoBlock.addView(descView);
        }

        card.addView(infoBlock);
        return card;
    }

}
