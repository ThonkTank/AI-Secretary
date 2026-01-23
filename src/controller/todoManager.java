package controller;

import android.content.Context;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import entities.todoList;
import entities.trackedItem;
import repository.SQLrepo;
import repository.Table;

public class todoManager {

    /**
     * ══════════════════════════════════════════════════════════════════════════════
     * TODO MANAGER - UI-Controller für die Tagesplan-Anzeige
     * ══════════════════════════════════════════════════════════════════════════════
     *
     * ZIEL:
     *   Stellt die Schnittstelle zwischen UI und Datenbank dar.
     *   Lädt den heutigen Tagesplan, konvertiert ihn in eine flache Task-Liste
     *   und ermöglicht das Abhaken einzelner Tasks.
     *
     * ──────────────────────────────────────────────────────────────────────────────
     * DATENFLUSS
     * ──────────────────────────────────────────────────────────────────────────────
     *
     *   ┌─────────────────────────────────────────────────────────────────────────┐
     *   │                           todoList (DB)                                 │
     *   │  Verschachtelte Struktur: Goal-Slots → Task-Slots                      │
     *   │  Erstellt von: buildToDo.makeToDoList()                                │
     *   └───────────────────────────────┬─────────────────────────────────────────┘
     *                                   │ provideList()
     *                                   ▼
     *   ┌─────────────────────────────────────────────────────────────────────────┐
     *   │                         List<TaskEntry>                                 │
     *   │  Flache, chronologisch sortierte Liste aller Tasks des Tages           │
     *   │  Jeder Eintrag enthält: Slot-ID, Titel, Zeit, Goal-Kontext             │
     *   └───────────────────────────────┬─────────────────────────────────────────┘
     *                                   │ UI zeigt Liste an
     *                                   ▼
     *   ┌─────────────────────────────────────────────────────────────────────────┐
     *   │                         completeSlot(id)                                │
     *   │  User hakt Task ab → Slot wird in DB als completed markiert            │
     *   │  Wenn alle Task-Slots eines Goals completed → Goal-Slot completed      │
     *   │  TodoListener wird benachrichtigt (UI-Update)                           │
     *   └─────────────────────────────────────────────────────────────────────────┘
     *
     * ──────────────────────────────────────────────────────────────────────────────
     * METHODEN-ÜBERSICHT
     * ──────────────────────────────────────────────────────────────────────────────
     *
     *  provideList() - Holt heutigen Plan aus DB, gibt flache TaskEntry-Liste zurück
     *      1. Heutiges Datum bestimmen
     *      2. todoList aus DB laden (Table.TODOS, filter: date=today)
     *      3. Für jeden Goal-Slot: Goal-Titel laden (Table.ITEMS, goalSlot.item)
     *      4. Für jeden Task-Slot im Goal: Task laden, TaskEntry erstellen
     *      5. Liste nach Startzeit sortieren
     *      Return: List<TaskEntry>
     *
     *  completeSlot(slotId) - Markiert einen Task-Slot als erledigt
     *      1. Slot in todayList finden und completed=true setzen
     *      2. Prüfen ob alle Task-Slots des zugehörigen Goals completed sind
     *      3. Falls ja: Goal-Slot ebenfalls als completed markieren
     *      4. Änderungen in DB persistieren
     *      5. Listener benachrichtigen
     *
     *  setListener(listener) - Registriert Callback für UI-Updates
     *
     * ──────────────────────────────────────────────────────────────────────────────
     * TASKENTRY RECORD
     * ──────────────────────────────────────────────────────────────────────────────
     *
     *   slotId            → TimeSlot ID in DB (zum Identifizieren beim Abhaken)
     *   taskTitle         → Angezeigter Name des Tasks
     *   taskDescription   → Optionale Beschreibung
     *   timeToComplete    → Geplante Dauer in Minuten
     *   start / end       → Zeitfenster im Tagesplan
     *   completed         → Aktueller Checkbox-State
     *   goalTitle         → Übergeordnetes Goal (Kontext-Anzeige)
     *   goalSlotId        → Goal-Slot ID (für Goal-Completion-Check)
     *
     */

    private SQLrepo repo;
    private todoList todayList;
    private TodoListener listener;

    public todoManager(Context context) {
        this.repo = new SQLrepo(context);
    }

    public interface TodoListener {
        void onListUpdated();
    }

    public record TaskEntry(
        Long slotId,            // TimeSlot ID (zum Abhaken)
        String taskTitle,       // Titel des Tasks
        String taskDescription, // Beschreibung des Tasks
        int timeToComplete,     // Dauer in Minuten
        LocalTime start,        // Slot-Startzeit
        LocalTime end,          // Slot-Endzeit
        boolean completed,      // Checkbox-State
        String goalTitle,       // Titel des übergeordneten Goals
        Long goalSlotId         // Goal-Slot ID (für Goal-Completion-Check)
    ) {}

    // ============================================================================
    // provideList - Lädt heutige Liste aus DB, konvertiert zu flacher TaskEntry-Liste
    // ============================================================================
    public List<TaskEntry> provideList() {
        LocalDate today = LocalDate.now();
        todayList = repo.fetch(Table.TODOS, Map.of("date", today.toString()));

        if (todayList == null || todayList.timeSlots == null) {
            return new ArrayList<>();
        }

        List<TaskEntry> entries = new ArrayList<>();

        for (todoList.TimeSlot goalSlot : todayList.timeSlots) {
            // Goal-Titel laden
            trackedItem goal = repo.fetch(Table.ITEMS, goalSlot.item);
            String goalTitle = (goal != null) ? goal.title : "";

            if (goalSlot.timeSlots == null) continue;

            // Task-Slots innerhalb des Goals durchgehen
            for (todoList.TimeSlot taskSlot : goalSlot.timeSlots) {
                trackedItem task = repo.fetch(Table.ITEMS, taskSlot.item);
                if (task == null) continue;

                entries.add(new TaskEntry(
                    taskSlot.id,
                    task.title,
                    task.description,
                    task.timeToComplete,
                    taskSlot.start,
                    taskSlot.end,
                    Boolean.TRUE.equals(taskSlot.completed),
                    goalTitle,
                    goalSlot.id
                ));
            }
        }

        // Chronologisch nach Startzeit sortieren
        entries.sort((a, b) -> a.start().compareTo(b.start()));
        return entries;
    }

    // ============================================================================
    // setListener - Registriert Callback für UI-Updates nach Slot-Completion
    // ============================================================================
    public void setListener (TodoListener listener) {
        this.listener = listener;
    }

    // ============================================================================
    // completeSlot - Markiert Task-Slot als erledigt, prüft Goal-Completion
    // ============================================================================
    public void completeSlot(Long slotId) {
        if (todayList == null || todayList.timeSlots == null) return;

        // Slot finden und completed setzen, Goal-Completion prüfen
        for (todoList.TimeSlot goalSlot : todayList.timeSlots) {
            if (goalSlot.timeSlots == null) continue;

            for (todoList.TimeSlot taskSlot : goalSlot.timeSlots) {
                if (slotId.equals(taskSlot.id)) {
                    taskSlot.completed = true;

                    // Alle Task-Slots des Goals completed? → Goal-Slot completed
                    boolean allDone = goalSlot.timeSlots.stream()
                        .allMatch(s -> Boolean.TRUE.equals(s.completed));
                    if (allDone) {
                        goalSlot.completed = true;
                    }

                    // Persistieren und Listener benachrichtigen
                    repo.write(todayList);
                    if (listener != null) listener.onListUpdated();
                    return;
                }
            }
        }
    }

}
