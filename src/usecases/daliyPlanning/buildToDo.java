package usecases.daliyPlanning;

import android.content.Context;

import repository.SQLrepo;
import repository.Table;
import entities.todoList;
import entities.trackedItem;
import entities.todoList.TimeSlot;
import entities.trackedItem.RepetitionType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
     *  makeToDoList() - Hauptfunktion, erstellt Wochenplan
     *      Für jeden Tag der nächsten 7 Tage:
     *      1. Wochentag bestimmen und Zeitspanne aus config_schedules holen (start_time, end_time)
     *      2. getItems(day, null) → Holt relevante Goals für den Tag
     *      3. prioritize(goals, day) → Sortiere nach Dringlichkeit
     *      4. toSlots(sortedGoals, start, end, day) → Weise Zeitslots zu (inkl. verschachtelter Tasks)
     *      5. todoList-Objekt bauen: date, start, end=actualEnd, timeSlots=slots
     *      Return: List<todoList> (7 Tagespläne)
     *
     *  getItems(day, parentId) - Holt relevante Items
     *      parentId==null → Goals holen (type="goal", is_completed=0)
     *      parentId!=null → Tasks eines Goals holen (parent=parentId, is_completed=0)
     *      Für jedes Item: fetch mit "id", "next_rep_start", "cooldown", "lastCompletion", "parent"
     *         Skip-Bedingungen:
     *         a) Parent-Check (nur wenn parentId!=null): Wenn item.parent existiert und parent.scheduled nicht leer
     *            → Skip wenn day.isBefore(Collections.max(parent.scheduled))
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
     *  toSlots(sortedItems, start, end, day) - Weist Zeitslots zu (rekursiv)
     *      Cursor beginnt bei start, remainingItems als Arbeitskopie
     *      Solange remainingItems nicht leer:
     *      1. timeAdjustItems(remainingItems, cursor) → Passt Prioritäten an aktuelle Zeit an
     *         → normalizedDiff = 1.0 - (MinutenDifferenz zu prefTime / 480)
     *         → adjustedPrio = prio * normalizedDiff
     *      2. Für jeden angepassten Eintrag (höchste Prio zuerst):
     *         → Prüfe ob genug Zeit bis end (cursor + timeToComplete ≤ end)
     *         → TimeSlot erstellen (cursor, item.id, completed=false)
     *         → Falls Goal: getItems(day, item.id) → prioritize → toSlots (rekursiv)
     *           → itemSlot.timeSlots = taskResult.slots()
     *           → itemSlot.end = taskResult.actualEnd()
     *         → cursor = itemSlot.end
     *         → Item aus remainingItems entfernen
     *         → item.schedule(day, repo) aufrufen
     *         → break (nächste Iteration)
     *      3. Abbruch wenn kein Item gescheduled werden konnte
     *      Return: SlotResult(slots, cursor)
     *
     */

    SQLrepo repo;

    public buildToDo(Context context) {
        this.repo = new SQLrepo(context);
    }

    // ============================================================================
    // makeToDoList - Hauptfunktion, erstellt ToDoListen für die nächsten 7 Tage.
    // ============================================================================
    public void makeToDoList(){

        // Für jeden Tag der nächsten 7 Tage:
        for (int i = 0; i < 7; i++) {
            LocalDate day = LocalDate.now().plusDays(i);
            String weekday = day.getDayOfWeek().toString();
            
            //Holt schedule von config
            Map<String, String> scheduleFilter = Map.of("day_of_week", weekday);
            LocalTime start = repo.lookup("config_schedules", scheduleFilter, "start_time");
            LocalTime end = repo.lookup("config_schedules", scheduleFilter, "end_time");

            // Holt Goal Liste
            List<trackedItem> goals = getItems(day, null);

            // Priorität an Dringlichkeit anpassen und liste sortieren.
            List<PrioritizedItem> sortedGoals = prioritize(goals, day);
            
            // Liste in Slots zuweisen
            SlotResult slottedGoals = toSlots(sortedGoals, start, end, day);
            
            //Return: Formatierte Liste mit Zeitslots für die nächsten 7 Tage
            todoList tagesPlan = new todoList();
            tagesPlan.date = day;
            tagesPlan.start = start;
            tagesPlan.end = slottedGoals.actualEnd();
            tagesPlan.timeSlots = slottedGoals.slots();

            repo.write(tagesPlan);
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

    private SlotResult toSlots(List<PrioritizedItem> sortedItems, LocalTime start, LocalTime end, LocalDate day) {
        List<TimeSlot> slots = new ArrayList<>();

        // Kopie der Liste erstellen (damit wir Items entfernen können)
        List<PrioritizedItem> remainingItems = new ArrayList<>(sortedItems);
        //Wo sind wir grade im tagesplan?
        LocalTime cursor = start;

        //Loopen bis keine Items mehr übrig
        while (!remainingItems.isEmpty()) {
            //Items an zeit anpassen
            List<PrioritizedItem> timeAdjustedItems = timeAdjustItems(remainingItems, cursor);
            // Abbruch wenn kein item mehr gescheduled werden kann
            Boolean scheduled = false;

            for (PrioritizedItem pi : timeAdjustedItems) {
                    trackedItem item = pi.item();
                    int ttc = item.timeToComplete;

                    //Prüfe, ob noch genug Zeit übrig ist.
                    LocalTime slotEnd = cursor.plusMinutes(ttc);
                    if (slotEnd.isAfter(end)) {
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

                        // Liste in Slots zuweisen
                        SlotResult taskResult = toSlots(sortedtasks, cursor, slotEnd, day);
                        
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

                    //item.scheduled in Datenbank aktualisieren
                    item.schedule(day, repo);

                    //Markieren das etwas gescheduled wurde
                    scheduled = true;

                    //loop brechen wenn passendes item gefunden
                    break;
            }
            //Abbruch wenn nichtsmehr gescheduled werden konnte
            if (!scheduled) {
                break;
            }
        }

        return new SlotResult(slots, cursor);
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
