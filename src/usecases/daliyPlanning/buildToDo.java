package usecases.daliyPlanning;

import android.content.Context;

import repository.SQLrepo;
import repository.Table;
import entities.todoList;
import entities.trackedItem;
import entities.todoList.TimeSlot;
import entities.trackedItem.RepetitionType;

import usecases.daliyPlanning.CalendarReader.CalendarEvent;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class buildToDo {
 
    /**
     * ══════════════════════════════════════════════════════════════════════════════
     * DAILY PLANNING ALGORITHM - Dokumentation
     * ══════════════════════════════════════════════════════════════════════════════
     *
     * ZIEL:
     *   Wird automatisch jeden Tag um 00:00 getriggert.
     *   Evaluiert die Planung der nächsten 7 Tage neu.
     *
     * ──────────────────────────────────────────────────────────────────────────────
     * HIERARCHIE-STRUKTUR (alle Ebenen sind trackedItems)
     * ──────────────────────────────────────────────────────────────────────────────
     *
     *   ┌─────────────────────────────────────────────────────────────────────────┐
     *   │                              PROJECT                                    │
     *   │  Oberste Ebene: Gruppiert thematisch verwandte Blöcke                   │
     *   │  Beispiel: "Fitness", "Arbeit", "Haushalt"                              │
     *   │  Attribute: minIntervalDays (Mindestabstand zwischen Scheduling)        │
     *   └───────────────────────────────┬─────────────────────────────────────────┘
     *                                   │ children[]
     *                                   ▼
     *   ┌─────────────────────────────────────────────────────────────────────────┐
     *   │                               BLOCK                                     │
     *   │  Organisatorische Einheit: Zusammenhängende Goals die in Abhängigkeit   │
     *   │  voneinander stehen und immer in fester Reihenfolge geplant werden.     │
     *   │                                                                         │
     *   │  Beispiel: "Schulter"-Block enthält:                                    │
     *   │    1. "Schulter Stretches"  →  2. "Schulter Training"                   │
     *   │  Diese Goals werden immer in dieser Reihenfolge eingeplant.             │
     *   │                                                                         │
     *   │  Attribute: prefTime (bevorzugte Tageszeit für diesen Block)            │
     *   └───────────────────────────────┬─────────────────────────────────────────┘
     *                                   │ children[]
     *                                   ▼
     *   ┌─────────────────────────────────────────────────────────────────────────┐
     *   │                               GOAL                                      │
     *   │  Inhaltliche Einheit: Konkretes Ziel mit Zeitbudget                     │
     *   │  Beispiel: "Krafttraining", "E-Mails bearbeiten", "Küche putzen"        │
     *   │  Attribute: timeToComplete (Zeitbudget in Minuten), priority            │
     *   └───────────────────────────────┬─────────────────────────────────────────┘
     *                                   │ children[]
     *                                   ▼
     *   ┌─────────────────────────────────────────────────────────────────────────┐
     *   │                               TASK                                      │
     *   │  Kleinste Einheit: Einzelne ausführbare Aufgabe                         │
     *   │  Beispiel: "10 Liegestütze", "E-Mail an Chef", "Spülmaschine ausräumen" │
     *   │  Attribute: timeToComplete, repetition, lastCompletion, followUps       │
     *   └─────────────────────────────────────────────────────────────────────────┘
     *
     * ──────────────────────────────────────────────────────────────────────────────
     * BENÖTIGTE EINGABEDATEN
     * ──────────────────────────────────────────────────────────────────────────────
     *
     *   CONFIG (aus config-Tabelle):
     *     - Active Time per Day: Von-bis Uhrzeit für ToDo-Liste
     *       (z.B. 6-18 Uhr an Wochentagen, 10-18 Uhr am Wochenende)
     *     - LocalDate.now
     *
     *   GOALS (aus items-Tabelle, type="Goal"):
     *     - Priority priority          → Für Priorisierung, Enum mit string und int wert
     *     - int timeToComplete         → Wieviel Zeit für das Goal eingeplant wird
     *     - List<Long> children        → Welche Tasks zum Goal gehören
     *
     *   TASKS (aus items-Tabelle, type="Task", nicht completed):
     *     - LocalDate lastCompletion   → Überfälligkeit berechnen
     *     - int completions            → Dringlichkeit bei "X pro Zeitraum"
     *     - Timeframe nextRepetition   → Überfälligkeit und Dringlichkeit
     *     - int timeToComplete         → Zeitslot-Zuweisung
     *     - Priority priority          → Priorisierung
     *     - Map<Long,Integer> followUps→ Chains (Tasks die oft nacheinander kommen)
     * 
     * ──────────────────────────────────────────────────────────────────────────────
     * SQL Zugriff (SQLrepo)
     * ──────────────────────────────────────────────────────────────────────────────
     *
     * =========== lookup(table, filters, outputColumns...) ===========
     * Durchsucht eine Tabelle nach Zeilen die den Filtern entsprechen.
     *
     * Beispiele:
     *   lookup("items", Map.of("id", "5"), "title")
     *   lookup("items", Map.of("type", "Goal", "is_completed", "0"), "id", "title")
     *   lookup("items", Map.of("type", "Task"), "*")
     *
     * @return List<Map<String, Object>> mit allen Treffern
     *
     * =========== fetch(table, id) ===========
     * Lädt eine Entity anhand ihrer ID. Delegiert an den passenden Builder.
     *
     * Beispiel:
     *   trackedItem item = repo.fetch(Table.ITEMS, 5);
     *
     * =========== write(Object) ===========
     * Schreibt ein Entity in die DB (INSERT oder UPDATE).
     *
     * Beispiel:
     *   repo.write(myTrackedItem);
     *   repo.write(wochenPlanList);
     *
     * ──────────────────────────────────────────────────────────────────────────────
     * ALGORITHMUS-ABLAUF
     * ──────────────────────────────────────────────────────────────────────────────
     *
     *  makeToDoList() - Hauptfunktion, erstellt/aktualisiert Wochenplan
     *      Für jeden Tag der nächsten 7 Tage:
     *      1. Wochentag bestimmen und Zeitspanne aus config_schedules holen (start_time, end_time)
     *      2. Kalender-Events für den Tag laden (CalendarReader.getEventsForDay)
     *      3. Prüfen ob bereits ein Plan existiert (repo.fetch mit date-Filter)
     *         → Falls ja: augmentPlan(existing, day, end) + repo.write
     *         → Falls nein: Neuen Plan erstellen:
     *           a) getItems(day, null) → Holt relevante Goals für den Tag
     *           b) prioritize(goals, day) → Sortiere nach Dringlichkeit
     *           c) toSlots(sortedGoals, start, end, day, calEvents) → Zeitslots zuweisen
     *           d) todoList-Objekt bauen: date, start, end=actualEnd, timeSlots=slots
     *           e) repo.write(tagesPlan)
     *
     *  getItems(day, parentId) - Holt relevante Items
     *      parentId==null → Goals holen (type="Goal", is_completed=0)
     *      parentId!=null → Tasks eines Goals holen (parent=parentId, is_completed=0)
     *      Für jedes Item: fetch(Table.ITEMS, itemID)
     *         Skip-Bedingungen:
     *         a) Parent-Check (immer wenn item.parent != null):
     *            → Skip wenn parent.scheduled nicht leer und day.isBefore(max(parent.scheduled))
     *            → Skip wenn parent.cooldown > 0, lastCompletion existiert,
     *              und day.isBefore(lastCompletion.plusDays(parent.cooldown))
     *         b) NextRepetition-Check: Wenn item.nextRepetition existiert
     *            → Skip wenn item.nextRepetition.start.isAfter(day)
     *         c) Cooldown-Check: Wenn item.cooldown > 0 und item.lastCompletion existiert
     *            → Skip wenn day.isBefore(item.lastCompletion.plusDays(item.cooldown))
     *      Return: List<trackedItem>
     *
     *  prioritize(items, day) - Sortiert nach kombinierter Dringlichkeit
     *      Für jedes Item:
     *      1. Basisdringlichkeit = priority.value + (priority.value * item.overdue(today) * 0.5)
     *      2. Für RepsPerTimeRepetition zusätzlich:
     *         → daysPerRemainingRep = remainingTime(day) / remainingReps(day)
     *         → normalizedFrequency = min(2.0, 1.0 + (1.0 / daysPerRemainingRep))
     *         → priority *= normalizedFrequency
     *      3. Sortiere nach Dringlichkeit (höchste zuerst)
     *      Return: List<PrioritizedItem> (record mit item und prio)
     *
     *  toSlots(sortedItems, start, end, day, calEvents) - Weist Zeitslots zu (rekursiv)
     *      Cursor beginnt bei start, remainingItems + remainingEvents als Arbeitskopien
     *      Solange remainingItems nicht leer:
     *      1. Kalender-Events einfügen die am/vor Cursor beginnen
     *         → CalendarEvent-Slots erstellen (isCalendarEvent=true, item=null)
     *         → Cursor hinter Event-Ende setzen
     *      2. Abbruch wenn Cursor >= end
     *      3. effectiveEnd = min(scheduleEnd, nächstes Event.start) bestimmen
     *      4. timeAdjustItems(remainingItems, cursor) → Passt Prioritäten an aktuelle Zeit an
     *      5. Für jeden angepassten Eintrag (höchste Prio zuerst):
     *         → Prüfe ob genug Zeit bis effectiveEnd (cursor + timeToComplete ≤ effectiveEnd)
     *         → TimeSlot erstellen (cursor, item.id, completed=false)
     *         → Falls Goal: getItems(day, item.id) → prioritize → toSlots (rekursiv, ohne calEvents)
     *           → itemSlot.timeSlots = taskResult.slots()
     *           → itemSlot.end = taskResult.actualEnd()
     *         → cursor = slotEnd
     *         → Item aus remainingItems entfernen
     *         → item.schedule(day, repo) NUR wenn day == today
     *         → break (nächste Iteration)
     *      6. Wenn nichts gescheduled: zum nächsten CalendarEvent springen oder abbrechen
     *      7. Nach Loop: verbliebene CalendarEvents anhängen (sofern vor end)
     *      Return: SlotResult(slots, cursor)
     *
     *  augmentPlan(existing, day, scheduleEnd) - Ergänzt bestehenden Plan
     *      1. Bereits geplante Goal-IDs sammeln
     *      2. Alle relevanten Goals holen, bereits geplante filtern
     *      3. Niedrigste Priorität im Plan ermitteln (Schwelle für neue Goals)
     *      4. Neue Goals an bester Stelle einfügen wenn prio >= lowestPlannedPrio
     *         → Beste Lücke finden basierend auf prefTime (findBestInsertionIndex)
     *         → Für jedes neue Goal: Tasks laden, priorisieren, toSlots (rekursiv)
     *         → Folgende Slots verschieben (shiftSlotsAfter)
     *      5. Task-Augmentation für bestehende Goals:
     *         → Neue eligible Tasks ermitteln (noch nicht geplant)
     *         → Einfügen wenn prio >= lowestTaskPrio und genug Zeit verfügbar (verglichen mit goal.timeToComplete)
     *         → Goal Slot Zeit anpassen und folgende Zeiten verschieben (shiftSlotsAfter)
     *      6. end-Zeit der Liste aktualisieren
     *
     *  timeAdjustItems(sortedItems, cursor) - Zeitbasierte Prioritätsanpassung
     *      Für jedes Item:
     *      → diff = Minuten zwischen prefTime und cursor
     *      → Wenn diff >= 0 (auf/nach prefTime): normalizedDiff = 1.0 (keine Strafe)
     *      → Wenn diff < 0 (vor prefTime): normalizedDiff = max(0.0, 1.0 + diff/480)
     *      → adjustedPrio = log1p(prio) * normalizedDiff² * 100
     *      Sortiere nach adjustedPrio (höchste zuerst)
     *      Return: List<PrioritizedItem>
     *
     */

    private Context context;
    SQLrepo repo;

    public buildToDo(Context context) {
        this.context = context;
        this.repo = new SQLrepo(context);
    }

    // ============================================================================
    // makeToDoList - Hauptfunktion, erstellt/aktualisiert ToDoListen für die nächsten 7 Tage.
    // ============================================================================
    public void makeToDoList(){
        LocalDate today = LocalDate.now();

        // Für jeden Tag der nächsten 7 Tage:
        for (int i = 0; i < 7; i++) {
            LocalDate day = today.plusDays(i);
            String weekday = day.getDayOfWeek().toString();

            //Holt schedule von config
            Map<String, String> scheduleFilter = Map.of("day_of_week", weekday);
            LocalTime start = repo.lookup("config_schedules", scheduleFilter, "start_time");
            LocalTime end = repo.lookup("config_schedules", scheduleFilter, "end_time");

            // Prüfen ob bereits ein Plan für diesen Tag existiert
            todoList existingPlan = repo.fetch(Table.TODOS, Map.of("date", day.toString()));

            // Kalender-Events fuer den Tag laden
            List<CalendarEvent> calEvents = CalendarReader.getEventsForDay(context, day, start, end);

            if (existingPlan != null) {
                // Plan existiert: re-validieren und ergänzen
                augmentPlan(existingPlan, day, end);
                repo.write(existingPlan);
            } else {
                // Kein Plan vorhanden: komplett neu planen
                List<trackedItem> goals = getItems(day, null);
                List<PrioritizedItem> sortedGoals = prioritize(goals, day);
                SlotResult slottedGoals = toSlots(sortedGoals, start, end, day, calEvents);

                todoList tagesPlan = new todoList();
                tagesPlan.date = day;
                tagesPlan.start = start;
                tagesPlan.end = slottedGoals.actualEnd();
                tagesPlan.timeSlots = slottedGoals.slots();

                repo.write(tagesPlan);
            }
        }
    }
    

    // ============================================================================
    // getItems - Holt relevante Goals für Tag oder Tasks für Goal
    // ============================================================================
    private List<trackedItem> getItems (LocalDate day, Long parentId) {
        List<trackedItem> relevantItems = new ArrayList<>();

        //Filter Bauen
        Map<String, String> filters;
        if (parentId != null) {
            filters = Map.of("parent", String.valueOf(parentId), "is_completed", "0");
        }
        else {
            filters = Map.of("type", "Goal", "is_completed", "0");
        }

        // get open item for day
        List<Long> openItems = repo.lookups("items", filters, "id");

        // Für alle IDs in der Liste
        for (Long itemID : openItems){

                //next rep start holen
                trackedItem item = repo.fetch(Table.ITEMS, itemID);
                // Projekt-Cooldown: Parent prüfen
                if (item.parent != null) {
                    trackedItem parent = repo.fetch(Table.ITEMS, item.parent);
                    if (parent != null) {
                        // Wenn Parent bereits scheduled, skip
                        if (parent.scheduled != null
                            && !parent.scheduled.isEmpty()
                            && day.isBefore(Collections.max(parent.scheduled))) {
                            continue;
                        }
                        // Wenn Parent cooldown aktiv, skip
                        if (parent.cooldown != 0
                            && item.lastCompletion != null
                            && day.isBefore(item.lastCompletion.plusDays(parent.cooldown))) {
                            continue;
                        }
                    }
                }

                //ist next repetition fällig?
                if (item.nextRepetition != null 
                    && item.nextRepetition != null
                    && item.nextRepetition.start.isAfter(day)) {
                        continue;
                } else if (item.cooldown != 0
                    && item.lastCompletion != null
                    && day.isBefore(item.lastCompletion.plusDays(item.cooldown))) {
                        continue;
                }

                relevantItems.add(item);
        }

        return relevantItems;
    }

    // ============================================================================
    // Prioritize - Priorisert Einträge in einer Liste
    // ============================================================================
    public record PrioritizedItem (trackedItem item, int prio) {}
    
    private List<PrioritizedItem> prioritize (List<trackedItem> items, LocalDate day) {
        List<PrioritizedItem> prioritizedItems = new ArrayList<PrioritizedItem>();
        // Für jedes Goal
        LocalDate today = LocalDate.now();
        for (trackedItem item : items) {
            // Dringlichkeit = priorität * (Überfälligkeit/2)
            int priority = item.priority.value + (int)(item.priority.value * (item.overdue(today) * 0.5));

            // Dringlichkeit für reps per time items
            if (item.repetition != null && item.repetition.type == RepetitionType.REPS_PER_TIME) {
                //rest Zeit für Periode in Tagen
                double daysPerRemainingRep = (double) item.remainingTime(day) / item.remainingReps(day);
                double normalizedFrequency = Math.min(2.0, 1.0 + (1.0 / daysPerRemainingRep));
                priority = (int)(priority * normalizedFrequency);
            }
            prioritizedItems.add(new PrioritizedItem(item, priority));
        }
        // Nach Dringlichkeit sortieren (höchste zuerst)
        prioritizedItems.sort((a, b) -> b.prio() - a.prio());

        return prioritizedItems;
    }



    // ============================================================================
    // toSlots - Wandelt priorisierte Liste in ToDoListe um
    // ============================================================================
    record SlotResult(List<TimeSlot> slots, LocalTime actualEnd) {}

    private SlotResult toSlots(List<PrioritizedItem> sortedItems, LocalTime start, LocalTime end, LocalDate day, List<CalendarEvent> calEvents) {
        List<TimeSlot> slots = new ArrayList<>();

        // Kopie der Listen erstellen
        List<PrioritizedItem> remainingItems = new ArrayList<>(sortedItems);
        List<CalendarEvent> remainingEvents = new ArrayList<>(calEvents);
        //Wo sind wir grade im tagesplan?
        LocalTime cursor = start;

        //Loopen bis keine Items mehr übrig
        while (!remainingItems.isEmpty()) {

            // Kalender-Events einfuegen die am/vor dem Cursor beginnen
            while (!remainingEvents.isEmpty()) {
                CalendarEvent nextEvent = remainingEvents.get(0);
                if (!nextEvent.start().isAfter(cursor)) {
                    // Event bereits komplett vom Cursor ueberholt → ueberspringen
                    if (!nextEvent.end().isAfter(cursor)) {
                        remainingEvents.remove(0);
                        continue;
                    }
                    TimeSlot calSlot = new TimeSlot();
                    calSlot.start = cursor.isBefore(nextEvent.start()) ? nextEvent.start() : cursor;
                    calSlot.end = nextEvent.end();
                    calSlot.isCalendarEvent = true;
                    calSlot.calendarTitle = nextEvent.title();
                    calSlot.item = null;
                    calSlot.completed = null;
                    slots.add(calSlot);
                    cursor = nextEvent.end();
                    remainingEvents.remove(0);
                } else {
                    break;
                }
            }

            // Abbruch wenn Cursor ueber Ende hinaus
            if (!cursor.isBefore(end)) break;

            // Effektives Ende: min(scheduleEnd, naechstes Event)
            LocalTime effectiveEnd = end;
            if (!remainingEvents.isEmpty()) {
                LocalTime nextEventStart = remainingEvents.get(0).start();
                if (nextEventStart.isBefore(effectiveEnd)) {
                    effectiveEnd = nextEventStart;
                }
            }

            //Items an zeit anpassen
            List<PrioritizedItem> timeAdjustedItems = timeAdjustItems(remainingItems, cursor);
            // Abbruch wenn kein item mehr gescheduled werden kann
            Boolean scheduled = false;

            for (PrioritizedItem pi : timeAdjustedItems) {
                    trackedItem item = pi.item();
                    int ttc = item.timeToComplete;

                    //Prüfe, ob noch genug Zeit bis zum naechsten Event/Ende
                    LocalTime slotEnd = cursor.plusMinutes(ttc);
                    if (slotEnd.isAfter(effectiveEnd)) {
                    continue;
                    }

                    //timeSlot erstellen
                    TimeSlot itemSlot = new TimeSlot();
                    itemSlot.start = cursor;
                    itemSlot.end = slotEnd;
                    itemSlot.item = item.id;
                    itemSlot.completed = false;

                    //Falls Goal, Tasks hinzufügen
                    if (item.type == trackedItem.ItemType.GOAL) {

                        // Holt Tasks für Slot shedulen
                        List<trackedItem> tasks = getItems(day, item.id);

                        // Priorität an Dringlichkeit anpassen und liste sortieren.
                        List<PrioritizedItem> sortedtasks = prioritize(tasks, day);

                        // Liste in Slots zuweisen (keine Calendar-Events auf Task-Ebene)
                        SlotResult taskResult = toSlots(sortedtasks, cursor, slotEnd, day, new ArrayList<>());

                        // falls tasks gefunden wurden: lots füllen, ende anpassen, slotEnd = actualEnd
                        if (!taskResult.slots().isEmpty()) {
                            itemSlot.timeSlots = taskResult.slots();
                            itemSlot.end = taskResult.actualEnd();
                            slotEnd = itemSlot.end;
                        }
                    }

                    //cursor zu ende bewegen
                    cursor = slotEnd;

                    //neuen Slot zu Liste hinzufügen
                    slots.add(itemSlot);

                    //item aus remaining entfernen
                    remainingItems.removeIf(i -> i.item().id.equals(item.id));

                    //item.scheduled nur für heute in Datenbank aktualisieren
                    if (day.equals(LocalDate.now())) {
                        item.schedule(day, repo);
                    }

                    //Markieren das etwas gescheduled wurde
                    scheduled = true;

                    //loop brechen wenn passendes item gefunden
                    break;
            }
            //Abbruch wenn nichtsmehr gescheduled werden konnte
            if (!scheduled) {
                // Wenn noch Events kommen, zum naechsten Event springen
                if (!remainingEvents.isEmpty()) {
                    CalendarEvent nextEvent = remainingEvents.get(0);
                    TimeSlot calSlot = new TimeSlot();
                    calSlot.start = nextEvent.start();
                    calSlot.end = nextEvent.end();
                    calSlot.isCalendarEvent = true;
                    calSlot.calendarTitle = nextEvent.title();
                    calSlot.item = null;
                    calSlot.completed = null;
                    slots.add(calSlot);
                    cursor = nextEvent.end();
                    remainingEvents.remove(0);
                    continue;
                }
                break;
            }
        }

        // Verbliebene Kalender-Events anhaengen
        for (CalendarEvent ev : remainingEvents) {
            if (ev.start().isBefore(end)) {
                TimeSlot calSlot = new TimeSlot();
                calSlot.start = ev.start();
                calSlot.end = ev.end().isAfter(end) ? end : ev.end();
                calSlot.isCalendarEvent = true;
                calSlot.calendarTitle = ev.title();
                calSlot.item = null;
                calSlot.completed = null;
                slots.add(calSlot);
                if (ev.end().isAfter(cursor)) cursor = ev.end();
            }
        }

        return new SlotResult(slots, cursor);
    }


    // ============================================================================
    // augmentPlan - Ergänzt einen bestehenden Tagesplan um fehlende Goals/Tasks
    // ============================================================================
    private void augmentPlan(todoList existing, LocalDate day, LocalTime scheduleEnd) {
        if (existing.timeSlots == null) existing.timeSlots = new ArrayList<>();

        // 1. Bereits geplante Goal-IDs sammeln
        Set<Long> plannedGoalIds = new HashSet<>();
        for (TimeSlot slot : existing.timeSlots) {
            if (slot.item != null) plannedGoalIds.add(slot.item);
        }

        // 2. Alle für den Tag relevanten Goals holen, bereits geplante filtern
        List<trackedItem> allGoals = getItems(day, null);
        List<trackedItem> candidates = new ArrayList<>();
        for (trackedItem goal : allGoals) {
            if (!plannedGoalIds.contains(goal.id)) {
                candidates.add(goal);
            }
        }

        // 3. Niedrigste Priorität im bestehenden Plan ermitteln (Schwelle für neue Goals)
        List<trackedItem> plannedGoals = new ArrayList<>();
        for (TimeSlot slot : existing.timeSlots) {
            if (slot.item != null) {
                trackedItem goal = repo.fetch(Table.ITEMS, slot.item);
                if (goal != null) plannedGoals.add(goal);
            }
        }
        List<PrioritizedItem> plannedPrios = prioritize(plannedGoals, day);
        int lowestPlannedPrio = plannedPrios.isEmpty() ? 0
                : plannedPrios.stream().mapToInt(PrioritizedItem::prio).min().orElse(0);

        // 4. Neue Goals an bester Stelle einfügen wenn prio >= lowestPlannedPrio
        if (!candidates.isEmpty()) {
            List<PrioritizedItem> sortedCandidates = prioritize(candidates, day);

            for (PrioritizedItem candidate : sortedCandidates) {
                if (candidate.prio() < lowestPlannedPrio) break;

                trackedItem goal = candidate.item();
                int duration = goal.timeToComplete;

                // Beste Einfügeposition finden basierend auf prefTime
                int bestIndex = findBestInsertionIndex(existing.timeSlots, goal.prefTime, duration, existing.start, scheduleEnd);
                if (bestIndex < 0) continue;

                // Startzeit bestimmen
                LocalTime insertStart;
                if (bestIndex == 0) {
                    insertStart = existing.start;
                } else {
                    insertStart = existing.timeSlots.get(bestIndex - 1).end;
                }
                LocalTime slotEnd = insertStart.plusMinutes(duration);

                // Goal-Slot erstellen
                TimeSlot goalSlot = new TimeSlot();
                goalSlot.start = insertStart;
                goalSlot.end = slotEnd;
                goalSlot.item = goal.id;
                goalSlot.completed = false;

                // Tasks innerhalb des Goal-Slots planen
                List<trackedItem> tasks = getItems(day, goal.id);
                if (!tasks.isEmpty()) {
                    List<PrioritizedItem> sortedTasks = prioritize(tasks, day);
                    SlotResult taskResult = toSlots(sortedTasks, insertStart, slotEnd, day, new ArrayList<>());
                    if (!taskResult.slots().isEmpty()) {
                        goalSlot.timeSlots = taskResult.slots();
                        goalSlot.end = taskResult.actualEnd();
                    }
                }

                // An bester Stelle einfügen und folgende Slots verschieben
                existing.timeSlots.add(bestIndex, goalSlot);
                shiftSlotsAfter(existing.timeSlots, bestIndex, scheduleEnd);
            }
        }

        // 5. Task-Augmentation für bestehende Goals
        for (int gi = 0; gi < existing.timeSlots.size(); gi++) {
            TimeSlot goalSlot = existing.timeSlots.get(gi);
            if (goalSlot.item == null) continue; // Calendar-Event überspringen

            trackedItem goal = repo.fetch(Table.ITEMS, goalSlot.item);
            if (goal == null) continue;

            // Bereits geplante Task-IDs im Goal
            Set<Long> plannedTaskIds = new HashSet<>();
            if (goalSlot.timeSlots != null) {
                for (TimeSlot taskSlot : goalSlot.timeSlots) {
                    if (taskSlot.item != null) plannedTaskIds.add(taskSlot.item);
                }
            }

            // Neue eligible Tasks ermitteln
            List<trackedItem> allTasks = getItems(day, goalSlot.item);
            List<trackedItem> newTasks = new ArrayList<>();
            for (trackedItem task : allTasks) {
                if (!plannedTaskIds.contains(task.id)) {
                    newTasks.add(task);
                }
            }
            if (newTasks.isEmpty()) continue;

            // Verfügbare Zeit = goal.timeToComplete - bereits verbrauchte Zeit
            long usedMinutes = 0;
            if (goalSlot.timeSlots != null) {
                for (TimeSlot ts : goalSlot.timeSlots) {
                    usedMinutes += ChronoUnit.MINUTES.between(ts.start, ts.end);
                }
            }
            long available = goal.timeToComplete - usedMinutes;

            // Niedrigste Task-Prio im Goal ermitteln
            int lowestTaskPrio = 0;
            if (goalSlot.timeSlots != null && !goalSlot.timeSlots.isEmpty()) {
                List<trackedItem> existingTasks = new ArrayList<>();
                for (TimeSlot ts : goalSlot.timeSlots) {
                    if (ts.item != null) {
                        trackedItem t = repo.fetch(Table.ITEMS, ts.item);
                        if (t != null) existingTasks.add(t);
                    }
                }
                List<PrioritizedItem> existingPrios = prioritize(existingTasks, day);
                lowestTaskPrio = existingPrios.stream().mapToInt(PrioritizedItem::prio).min().orElse(0);
            }

            // Cursor ans Ende der bestehenden Tasks setzen
            LocalTime taskCursor = goalSlot.start;
            if (goalSlot.timeSlots != null) {
                for (TimeSlot ts : goalSlot.timeSlots) {
                    if (ts.end != null && ts.end.isAfter(taskCursor)) {
                        taskCursor = ts.end;
                    }
                }
            }

            // Neue Tasks einfügen
            boolean goalGrew = false;
            List<PrioritizedItem> sortedNewTasks = prioritize(newTasks, day);
            for (PrioritizedItem pi : sortedNewTasks) {
                if (pi.prio() < lowestTaskPrio) break;
                trackedItem task = pi.item();
                if (task.timeToComplete > available) continue;

                TimeSlot taskSlot = new TimeSlot();
                taskSlot.start = taskCursor;
                taskSlot.end = taskCursor.plusMinutes(task.timeToComplete);
                taskSlot.item = task.id;
                taskSlot.completed = false;

                if (goalSlot.timeSlots == null) goalSlot.timeSlots = new ArrayList<>();
                goalSlot.timeSlots.add(taskSlot);
                taskCursor = taskSlot.end;
                available -= task.timeToComplete;
                goalGrew = true;
            }

            // Goal Slot Zeit anpassen und folgende Zeiten anpassen
            if (goalGrew && taskCursor.isAfter(goalSlot.end)) {
                goalSlot.end = taskCursor;
                shiftSlotsAfter(existing.timeSlots, gi, scheduleEnd);
            }
        }

        // 6. end-Zeit der Liste aktualisieren
        LocalTime maxEnd = existing.start;
        for (TimeSlot slot : existing.timeSlots) {
            if (slot.end != null && slot.end.isAfter(maxEnd)) {
                maxEnd = slot.end;
            }
        }
        existing.end = maxEnd;
    }

    // Findet die beste Einfügeposition basierend auf prefTime
    private int findBestInsertionIndex(List<TimeSlot> slots, LocalTime prefTime, int duration, LocalTime dayStart, LocalTime dayEnd) {
        int bestIndex = -1;
        long bestDistance = Long.MAX_VALUE;

        // Alle Lücken zwischen bestehenden Slots prüfen
        for (int i = 0; i <= slots.size(); i++) {
            LocalTime gapStart = (i == 0) ? dayStart : slots.get(i - 1).end;
            LocalTime gapEnd = (i == slots.size()) ? dayEnd : slots.get(i).start;

            if (gapStart == null || gapEnd == null) continue;

            long gapMinutes = ChronoUnit.MINUTES.between(gapStart, gapEnd);
            if (gapMinutes < duration) continue; // Lücke zu klein

            // Distanz der Lückenmitte zu prefTime berechnen
            LocalTime gapMid = gapStart.plusMinutes(gapMinutes / 2);
            long distance = Math.abs(ChronoUnit.MINUTES.between(prefTime, gapMid));

            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }

        return bestIndex;
    }

    // Verschiebt alle Slots nach insertedIndex so dass keine Überlappungen entstehen
    private void shiftSlotsAfter(List<TimeSlot> slots, int insertedIndex, LocalTime dayEnd) {
        for (int i = insertedIndex + 1; i < slots.size(); i++) {
            TimeSlot prev = slots.get(i - 1);
            TimeSlot curr = slots.get(i);

            if (prev.end != null && curr.start != null && prev.end.isAfter(curr.start)) {
                long shift = ChronoUnit.MINUTES.between(curr.start, prev.end);
                curr.start = curr.start.plusMinutes(shift);
                curr.end = curr.end.plusMinutes(shift);

                // Verschachtelte Task-Slots ebenfalls verschieben
                if (curr.timeSlots != null) {
                    for (TimeSlot taskSlot : curr.timeSlots) {
                        taskSlot.start = taskSlot.start.plusMinutes(shift);
                        taskSlot.end = taskSlot.end.plusMinutes(shift);
                    }
                }
            }
        }

        // Slots die über dayEnd hinausragen entfernen
        slots.removeIf(s -> s.start != null && !s.start.isBefore(dayEnd));
    }


    private List<PrioritizedItem> timeAdjustItems (List<PrioritizedItem> sortedItems, LocalTime cursor) {
        List<PrioritizedItem> timeAdjusteditems = new ArrayList<PrioritizedItem>();
        for (PrioritizedItem pi : sortedItems) {
            trackedItem item = pi.item;
            // positiv = Cursor ist NACH prefTime (überfällig), negativ = Cursor ist VOR prefTime (zu früh)
            long diff = ChronoUnit.MINUTES.between(item.prefTime, cursor);
            double normalizedDiff;
            if (diff >= 0) {
                // Auf oder nach prefTime → keine Strafe
                normalizedDiff = 1.0;
            } else {
                // Vor prefTime → Strafe proportional zur Distanz
                normalizedDiff = Math.max(0.0, 1.0 + (diff / 480.0));
            }
            double logPrio = Math.log1p(pi.prio());
            int adjustedPrio = (int)(logPrio * normalizedDiff * normalizedDiff * 100);
            timeAdjusteditems.add(new PrioritizedItem(pi.item, adjustedPrio));
        }
        timeAdjusteditems.sort((a, b) -> b.prio() - a.prio());

        return timeAdjusteditems;
    }

}
