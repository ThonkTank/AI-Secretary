package controller;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import entities.Account;
import entities.todoList;
import entities.trackedItem;
import entities.Transaction;
import repository.SQLrepo;
import repository.Table;
import scheduling.buildToDo;
import scheduling.CalendarReader;

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
     *  startTimer(slotId) - Setzt workStart auf LocalTime.now()
     *      → Persistiert + Listener benachrichtigen
     *
     *  stopTimer(slotId) - Setzt workEnd auf LocalTime.now(), delegiert an completeSlot
     *
     *  completeSlot(slotId) - Markiert einen Task-Slot als erledigt
     *      1. Slot in currentDayList finden und completed=true setzen
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
     *   slotDuration      → Berechnete Slot-Dauer in Minuten
     *   start / end       → Zeitfenster im Tagesplan
     *   completed         → Aktueller Checkbox-State
     *   goalTitle         → Übergeordnetes Goal (Kontext-Anzeige)
     *   goalSlotId        → Goal-Slot ID (für Goal-Completion-Check)
     *   isCalendarEvent   → Kalender-Termin (nicht abhakbar)
     *   workStart         → Timer gestartet? (null = nicht gestartet)
     *
     */

    private Context context;
    private SQLrepo repo;
    private todoList currentDayList;
    private LocalDate currentDay;
    private TodoListener listener;
    // Trackt letzten completed Task PRO PARENT (Goal-ID → letztes completed Item-ID)
    private Map<Long, Long> lastCompletedByParent = new HashMap<>();

    public todoManager(Context context) {
        this.context = context;
        this.repo = SQLrepo.getInstance(context);
    }

    public interface TodoListener {
        void onListUpdated();
        /** Wird aufgerufen wenn feste Termine nicht eingeplant werden konnten. */
        default void onSchedulingConflicts(List<scheduling.buildToDo.SchedulingConflict> conflicts) {}
    }

    public record TaskEntry(
        Long slotId,            // TimeSlot ID (zum Abhaken)
        String taskTitle,       // Titel des Tasks
        String taskDescription, // Beschreibung des Tasks
        int slotDuration,       // Berechnete Slot-Dauer in Minuten
        LocalTime start,        // Slot-Startzeit
        LocalTime end,          // Slot-Endzeit
        boolean completed,      // Checkbox-State
        String goalTitle,       // Titel des übergeordneten Goals
        Long goalSlotId,        // Goal-Slot ID (für Goal-Completion-Check)
        boolean isCalendarEvent,// Kalender-Termin (nicht abhakbar)
        LocalTime workStart,    // Timer gestartet? (null = nicht gestartet)
        LocalDate deadline,     // Fälligkeitsdatum (null = keine Deadline)
        String goalIcon,        // Emoji-Icon des Goals (null = kein Icon)
        String goalColor,       // Hex-Farbcode des Goals (null = Standard)
        int currentStreak,      // Aktuelle Streak-Laenge des Tasks
        int remainingDays,      // Verbleibende Tage (Deadline/Periode)
        int progressCurrent,    // Aktueller Fortschritt (0 wenn kein Tracking)
        int progressTarget,     // Ziel-Fortschritt (0 wenn kein Tracking)
        String progressUnit     // Einheit (z.B. "Seiten", nullable)
    ) {}

    // ============================================================================
    // getTodayStart / getTodayEnd - Tagesgrenzen aus currentDayList exponieren
    // ============================================================================
    public LocalTime getTodayStart() {
        return currentDayList != null ? currentDayList.start : LocalTime.of(6, 0);
    }

    public LocalTime getTodayEnd() {
        return currentDayList != null ? currentDayList.end : LocalTime.of(18, 0);
    }

    // ============================================================================
    // isCurrentDayToday - Prüft ob der aktuell geladene Tag heute ist
    // ============================================================================
    public boolean isCurrentDayToday() {
        return currentDay != null && currentDay.equals(LocalDate.now());
    }

    // ============================================================================
    // replanToday - Löscht heutigen Plan und generiert ihn neu via buildToDo
    // ============================================================================
    public void replanToday() {
        LocalDate today = LocalDate.now();
        todoList todayPlan = repo.fetch(Table.TODOS, Map.of("date", today.toString()));

        if (todayPlan != null) {
            // Eingeplante Items entplanen (scheduled-Datum entfernen)
            if (todayPlan.timeSlots != null) {
                unscheduleSlots(todayPlan.timeSlots, today);
            }

            // TodoList + TimeSlots aus DB löschen
            SQLiteDatabase db = repo.getWritableDatabase();
            db.delete("time_slots", "todo_id = ?", new String[]{String.valueOf(todayPlan.id)});
            db.delete("todos", "id = ?", new String[]{String.valueOf(todayPlan.id)});
        }

        // Neu planen
        buildToDo planner = new buildToDo(repo,
            (day, start, end) -> CalendarReader.getEventsForDay(context, day, start, end)
        );
        planner.planWeek();

        if (listener != null) {
            listener.onListUpdated();
            List<scheduling.buildToDo.SchedulingConflict> conflicts = planner.getConflicts();
            if (!conflicts.isEmpty()) {
                listener.onSchedulingConflicts(conflicts);
            }
        }
    }

    private void unscheduleSlots(List<todoList.TimeSlot> slots, LocalDate day) {
        for (todoList.TimeSlot slot : slots) {
            if (slot.item != null) {
                trackedItem item = repo.fetch(Table.ITEMS, slot.item);
                if (item != null && item.scheduled != null) {
                    item.scheduled.remove(day);
                    item.blockedDays = item.getBlockedDays();
                    repo.write(item);
                }
            }
            if (slot.timeSlots != null) {
                unscheduleSlots(slot.timeSlots, day);
            }
        }
    }

    // ============================================================================
    // provideList - Lädt heutige Liste aus DB, konvertiert zu flacher TaskEntry-Liste
    // ============================================================================
    public List<TaskEntry> provideList() {
        return provideList(LocalDate.now());
    }

    public List<TaskEntry> provideList(LocalDate day) {
        currentDay = day;
        currentDayList = repo.fetch(Table.TODOS, Map.of("date", day.toString()));
        lastCompletedByParent.clear();  // Reset Completion-Tracking bei neuem Laden

        if (currentDayList == null || currentDayList.timeSlots == null) {
            return new ArrayList<>();
        }

        List<TaskEntry> entries = new ArrayList<>();

        for (todoList.TimeSlot goalSlot : currentDayList.timeSlots) {

            // Kalender-Events als eigene Eintraege
            if (Boolean.TRUE.equals(goalSlot.isCalendarEvent)) {
                int duration = (int) java.time.temporal.ChronoUnit.MINUTES.between(goalSlot.start, goalSlot.end);
                entries.add(new TaskEntry(
                    goalSlot.id,
                    goalSlot.calendarTitle,
                    null,
                    duration,
                    goalSlot.start,
                    goalSlot.end,
                    false,
                    "Kalender",
                    null,
                    true,
                    null,
                    null,
                    null,
                    null,
                    0,
                    0,  // remainingDays
                    0,  // progressCurrent
                    0,  // progressTarget
                    null // progressUnit
                ));
                continue;
            }

            // Goal laden
            trackedItem goal = repo.fetch(Table.ITEMS, goalSlot.item);
            String goalTitle = (goal != null) ? goal.title : "";
            String goalIcon = (goal != null) ? goal.goalIcon : null;
            String goalColor = (goal != null) ? goal.goalColor : null;

            if (goalSlot.timeSlots == null) continue;

            // Task-Slots innerhalb des Goals durchgehen
            for (todoList.TimeSlot taskSlot : goalSlot.timeSlots) {
                trackedItem task = repo.fetch(Table.ITEMS, taskSlot.item);
                if (task == null) continue;

                // Angezeigter Progress:
                // - progressPerRep=true: NUR slot.progressDelta (jeder Slot startet bei 0)
                // - progressPerRep=false: item.progressCurrent + slot.progressDelta (globaler Progress)
                int displayProgress;
                if (task.progressPerRep) {
                    displayProgress = (taskSlot.progressDelta != null) ? taskSlot.progressDelta : 0;
                } else {
                    displayProgress = task.progressCurrent;
                    if (taskSlot.progressDelta != null) {
                        displayProgress += taskSlot.progressDelta;
                    }
                }

                // Slot-Dauer berechnen (tatsaechlich geplante Zeit)
                int slotDuration = (int) java.time.temporal.ChronoUnit.MINUTES.between(taskSlot.start, taskSlot.end);
                entries.add(new TaskEntry(
                    taskSlot.id,
                    task.title,
                    task.description,
                    slotDuration,
                    taskSlot.start,
                    taskSlot.end,
                    Boolean.TRUE.equals(taskSlot.completed),
                    goalTitle,
                    goalSlot.id,
                    false,
                    taskSlot.workStart,
                    task.deadline,
                    goalIcon,
                    goalColor,
                    task.currentStreak,
                    task.remainingTime(day),
                    displayProgress,  // Kombinierter Progress (item + slot delta)
                    task.progressTarget,
                    task.progressUnit
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
    // uncompleteSlot - Setzt Task-Slot auf unerledigt zurück
    // ============================================================================
    public void uncompleteSlot(Long slotId) {
        if (currentDayList == null || currentDayList.timeSlots == null) return;

        for (todoList.TimeSlot goalSlot : currentDayList.timeSlots) {
            if (goalSlot.timeSlots == null) continue;

            for (todoList.TimeSlot taskSlot : goalSlot.timeSlots) {
                if (slotId.equals(taskSlot.id)) {
                    taskSlot.completed = false;
                    taskSlot.workStart = null;
                    taskSlot.workEnd = null;

                    // Goal-Slot ebenfalls zurücksetzen
                    goalSlot.completed = false;

                    repo.write(currentDayList);
                    if (listener != null) listener.onListUpdated();
                    return;
                }
            }
        }
    }

    // ============================================================================
    // startTimer - Setzt workStart auf aktuelle Uhrzeit
    // ============================================================================
    public void startTimer(Long slotId) {
        if (currentDayList == null || currentDayList.timeSlots == null) return;

        for (todoList.TimeSlot goalSlot : currentDayList.timeSlots) {
            if (goalSlot.timeSlots == null) continue;
            for (todoList.TimeSlot taskSlot : goalSlot.timeSlots) {
                if (slotId.equals(taskSlot.id)) {
                    taskSlot.workStart = LocalTime.now();
                    repo.write(currentDayList);
                    if (listener != null) listener.onListUpdated();
                    return;
                }
            }
        }
    }

    // ============================================================================
    // stopTimer - Setzt workEnd und delegiert Completion an completeSlot
    // ============================================================================
    public void stopTimer(Long slotId) {
        if (currentDayList == null || currentDayList.timeSlots == null) return;

        for (todoList.TimeSlot goalSlot : currentDayList.timeSlots) {
            if (goalSlot.timeSlots == null) continue;
            for (todoList.TimeSlot taskSlot : goalSlot.timeSlots) {
                if (slotId.equals(taskSlot.id)) {
                    taskSlot.workEnd = LocalTime.now();
                    completeSlot(slotId);
                    return;
                }
            }
        }
    }

    // ============================================================================
    // completeSlot - Markiert Task-Slot als erledigt, prüft Goal-Completion
    // ============================================================================
    public void completeSlot(Long slotId) {
        if (currentDayList == null || currentDayList.timeSlots == null) return;

        // Slot finden und completed setzen, Goal-Completion prüfen
        for (todoList.TimeSlot goalSlot : currentDayList.timeSlots) {
            if (goalSlot.timeSlots == null) continue;

            for (todoList.TimeSlot taskSlot : goalSlot.timeSlots) {
                if (slotId.equals(taskSlot.id)) {
                    taskSlot.completed = true;

                    // Track actual completion order (nur Geschwister innerhalb desselben Goals)
                    Long parentGoalId = goalSlot.item;
                    taskSlot.previousCompletedItemId = lastCompletedByParent.get(parentGoalId);
                    lastCompletedByParent.put(parentGoalId, taskSlot.item);

                    // Alle Task-Slots des Goals completed? → Goal-Slot completed
                    boolean allDone = goalSlot.timeSlots.stream()
                        .allMatch(s -> Boolean.TRUE.equals(s.completed));
                    if (allDone) {
                        goalSlot.completed = true;
                    }

                    // === TRANSACTION START ===
                    // Alle DB-Writes atomar ausführen für Konsistenz
                    SQLiteDatabase db = repo.getWritableDatabase();
                    db.beginTransaction();
                    try {
                        // 1. TodoList mit Slot-Updates persistieren
                        repo.write(currentDayList);

                        // 2. Auto-Transaction für budgetierte Tasks
                        trackedItem item = repo.fetch(Table.ITEMS, taskSlot.item);
                        if (item != null && item.budgetRequirementCents > 0) {
                            Long accountId = item.budgetAccountId;

                            // Falls kein spezifisches Konto: erstes aktives mit includeInTotal
                            if (accountId == null) {
                                List<Long> ids = repo.lookups("accounts",
                                    Map.of("is_active", "1", "include_in_total", "1"), "id");
                                if (!ids.isEmpty()) accountId = ids.get(0);
                            }

                            if (accountId != null) {
                                Transaction tx = new Transaction.Builder(
                                    accountId,
                                    -item.budgetRequirementCents,  // Negativ = Ausgabe
                                    LocalDate.now(),
                                    item.budgetCategoryId  // null = unkategorisiert
                                )
                                .description("Task: " + item.title)
                                .isConfirmed(false)  // User muss bestätigen
                                .build();
                                repo.write(tx);

                                // Konto-Saldo aktualisieren
                                Account acc = repo.fetch(Table.ACCOUNTS, accountId);
                                if (acc != null) {
                                    acc.currentBalanceCents -= item.budgetRequirementCents;
                                    repo.write(acc);
                                }
                            }
                        }

                        db.setTransactionSuccessful();
                    } finally {
                        db.endTransaction();
                    }
                    // === TRANSACTION END ===

                    // 3. Meal-Task-Completion: Vorrat reduzieren, ConsumptionLog erstellen
                    // Außerhalb der Transaction, da mealManager eigene Schreiblogik hat
                    trackedItem itemForMeal = repo.fetch(Table.ITEMS, taskSlot.item);
                    if (itemForMeal != null && itemForMeal.mealPlanId != null) {
                        try {
                            mealManager mealMgr = new mealManager(context);
                            mealMgr.completeMeal(itemForMeal.mealPlanId, 0);  // 0 = geplante Portionen
                        } catch (Exception e) {
                            android.util.Log.e("todoManager",
                                "Meal completion failed: " + itemForMeal.mealPlanId, e);
                            // Weiter - Task ist trotzdem erledigt
                        }
                    }

                    // Listener benachrichtigen
                    if (listener != null) listener.onListUpdated();
                    return;
                }
            }
        }
    }

    // ============================================================================
    // incrementProgress - Erhöht progressDelta im Slot, markiert Slot als erledigt
    // WICHTIG: Item wird NICHT direkt geändert - erst bei Mitternacht via update()
    // ============================================================================
    public void incrementProgress(Long slotId) {
        if (currentDayList == null || currentDayList.timeSlots == null) return;

        for (todoList.TimeSlot goalSlot : currentDayList.timeSlots) {
            if (goalSlot.timeSlots == null) continue;

            for (todoList.TimeSlot taskSlot : goalSlot.timeSlots) {
                if (slotId.equals(taskSlot.id)) {
                    // Item laden für Grenzprüfung
                    trackedItem item = repo.fetch(Table.ITEMS, taskSlot.item);
                    if (item == null) return;

                    // Progress-Delta initialisieren falls null
                    if (taskSlot.progressDelta == null) taskSlot.progressDelta = 0;

                    // Grenzprüfung:
                    // - progressPerRep=true: slot.progressDelta < progressTarget (Slot-lokale Grenze)
                    // - progressPerRep=false: item.progressCurrent + slot.progressDelta < progressTarget
                    int effectiveProgress = item.progressPerRep
                        ? taskSlot.progressDelta
                        : item.progressCurrent + taskSlot.progressDelta;
                    if (effectiveProgress < item.progressTarget) {
                        taskSlot.progressDelta++;
                    }

                    // Slot als "für heute erledigt" markieren
                    taskSlot.completed = true;

                    // Track actual completion order (nur beim ersten Mal setzen)
                    Long parentGoalId = goalSlot.item;
                    if (taskSlot.previousCompletedItemId == null) {
                        taskSlot.previousCompletedItemId = lastCompletedByParent.get(parentGoalId);
                        lastCompletedByParent.put(parentGoalId, taskSlot.item);
                    }

                    // Goal-Completion prüfen
                    boolean allDone = goalSlot.timeSlots.stream()
                        .allMatch(s -> Boolean.TRUE.equals(s.completed));
                    if (allDone) {
                        goalSlot.completed = true;
                    }

                    // NUR die todoList speichern, NICHT das Item!
                    repo.write(currentDayList);
                    if (listener != null) listener.onListUpdated();
                    return;
                }
            }
        }
    }

    // ============================================================================
    // decrementProgress - Verringert progressDelta im Slot, setzt ggf. Slot zurück
    // WICHTIG: Item wird NICHT direkt geändert - erst bei Mitternacht via update()
    // ============================================================================
    public void decrementProgress(Long slotId) {
        if (currentDayList == null || currentDayList.timeSlots == null) return;

        for (todoList.TimeSlot goalSlot : currentDayList.timeSlots) {
            if (goalSlot.timeSlots == null) continue;

            for (todoList.TimeSlot taskSlot : goalSlot.timeSlots) {
                if (slotId.equals(taskSlot.id)) {
                    // Item laden für Grenzprüfung
                    trackedItem item = repo.fetch(Table.ITEMS, taskSlot.item);
                    if (item == null) return;

                    // Progress-Delta initialisieren falls null
                    if (taskSlot.progressDelta == null) taskSlot.progressDelta = 0;

                    // Grenzprüfung:
                    // - progressPerRep=true: slot.progressDelta > 0 (Slot-lokale Grenze)
                    // - progressPerRep=false: item.progressCurrent + slot.progressDelta > 0
                    int effectiveProgress = item.progressPerRep
                        ? taskSlot.progressDelta
                        : item.progressCurrent + taskSlot.progressDelta;
                    if (effectiveProgress > 0) {
                        taskSlot.progressDelta--;
                    }

                    // Slot zurücksetzen wenn Delta wieder 0 oder negativ
                    if (taskSlot.progressDelta <= 0) {
                        taskSlot.completed = false;
                        goalSlot.completed = false;
                    }

                    // NUR die todoList speichern, NICHT das Item!
                    repo.write(currentDayList);
                    if (listener != null) listener.onListUpdated();
                    return;
                }
            }
        }
    }

}
