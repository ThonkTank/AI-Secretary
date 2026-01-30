package usecases.dailyPlanning;

import repository.Repo;
import repository.Table;
import entities.CalendarEvent;
import entities.todoList;
import entities.trackedItem;
import entities.todoList.TimeSlot;
import entities.trackedItem.RepetitionType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class buildToDoV2 {

    /**
     * ══════════════════════════════════════════════════════════════════════════════
     * DAILY PLANNING ALGORITHM V2 - Dokumentation
     * ══════════════════════════════════════════════════════════════════════════════
     *
     * ZIEL:
     *   Wird automatisch jeden Tag um 00:00 getriggert.
     *   Evaluiert die Planung der nächsten 7 Tage neu.
     *
     * VERBESSERUNGEN gegenüber V1:
     *   - Kein chronologischer Cursor mehr: Items werden intelligent über die gesamte Woche verteilt
     *   - placeItem bewertet ALLE Slots aller 7 Tage gleichzeitig und wählt den global besten
     *   - Verdrängung: Höher-priorisierte Items können niedriger-priorisierte verdrängen
     *   - Slot-adjusted Prio wird im TimeSlot persistiert für fairen Verdrängungsvergleich
     *   - Kein Split mehr zwischen augment/create: alles läuft über dieselbe Pipeline
     *   - Goal-Tasks werden über placeItem statt toSlots platziert
     * 
     * TODO: Aufgaben mit mehreren Wiederholungen pro Tag ermöglichen.
     *
     * ──────────────────────────────────────────────────────────────────────────────
     * ALGORITHMUS-ABLAUF
     * ──────────────────────────────────────────────────────────────────────────────
     *
     *  1. Bestehende Pläne holen oder neue leere erstellen
     *  2. calendarSync() für jeden bestehenden Plan (alte Events entfernen, neue einfügen, Überlappungen entplanen)
     *  3. getItems() - alle Goals holen die in den nächsten 7 Tagen offen sind
     *     Goals mit mehreren Wiederholungen werden mehrfach zur Liste hinzugefügt.
     *  4. Folgendes loopen:
     *      a) getItems() + prioritize() - Liste KOMPLETT NEU aufbauen
     *         → Prios immer aktuell (REPS_PER_TIME Urgency, Parent-Blockierungen)
     *         → Verdrängte Items tauchen automatisch wieder auf (nicht mehr scheduled)
     *         → Durch Parent-Cooldown blockierte Siblings werden automatisch gefiltert
     *      b) placeItem(höchstes item, lists) - besten Slot global finden und platzieren
     *      c) Bei Erfolg: skipped-Set leeren (Landscape geändert), weiter bei a)
     *         Bei Misserfolg: Item überspringen, weiter bei a)
     *      d) Abbruch wenn Liste leer oder alles übersprungen
     *  5. Todolisten in DB schreiben.
     *
     *  placeItem(item, todoListen):
     *      Für alle Todolisten:
     *          Prüft, ob Item für den Tag blockiert ist (item.blockedDays)
     *          Findet alle freien Fenster und bewertet jeden möglichen Slot (scoreSlot = prefTime-Nähe × log-Priorität)
     *          Prüft belegte Slots: Verdrängung nur wenn eigener Score > existierender adjustedPrio
     *      Wählt global besten Slot (höchster Score über alle 7 Tage).
     *      Bei Verdrängung: unPlan(existierender Slot). Verdrängtes Item wird beim nächsten getItems() automatisch wieder aufgenommen.
     *      item.schedule(day)
     *      Wenn Item = Goal: fillGoalSlot() → Tasks über gleiche Rebuild-Pipeline platzieren.
     *
     *  calendarSync(list, day, start, end):
     *      Alte Calendar-Events entfernen.
     *      Frische Calendar-Events hinzufügen.
     *      Goals mit überlappender Zeit entplanen.
     *
     *  getItems(days, parentId):
     *      Alle passenden Items holen, bei denen mind. ein Tag in days nicht blockiert ist.
     *      REPS_PER_TIME Items werden mehrfach hinzugefügt (einmal pro verbleibender Rep).
     *
     *  prioritize(items, day):
     *      Basisdringlichkeit = priority.value + (priority.value × overdue × 0.5)
     *      Für REPS_PER_TIME: × normalizedFrequency (1.0-2.0)
     *      Sortiert nach Dringlichkeit (höchste zuerst).
     *
     *  scoreSlot(item, slotStart):
     *      Wenn kein prefTime: raw prio.
     *      Sonst: log1p(prio) × normalizedDiff² × 100
     *      (normalizedDiff = 1.0 wenn auf/nach prefTime, Strafe wenn davor)
     *
     */

    public record PrioritizedItem(trackedItem item, int prio) {}
    public record ScoredSlot(todoList list, LocalDate day, LocalTime start, LocalTime end,
                             int score, TimeSlot existingSlot) {}

    /** Liefert Kalender-Events für einen Tag. Abstrahiert CalendarReader für Testbarkeit. */
    @FunctionalInterface
    public interface CalendarProvider {
        List<CalendarEvent> getEventsForDay(LocalDate day, LocalTime start, LocalTime end);
    }

    Repo repo;
    CalendarProvider calendar;

    public buildToDoV2(Repo repo, CalendarProvider calendar) {
        this.repo = repo;
        this.calendar = calendar;
    }


    // ============================================================================
    // makeToDoList - Hauptfunktion, erstellt/aktualisiert ToDoListen für die nächsten 7 Tage.
    // ============================================================================
    public void makeToDoList() {
        LocalDate today = LocalDate.now();
        List<todoList> lists = new ArrayList<>();
        List<LocalDate> days = new ArrayList<>();

        // 1. Für jeden Tag: bestehenden Plan holen oder neuen erstellen
        for (int i = 0; i < 7; i++) {
            LocalDate day = today.plusDays(i);
            days.add(day);
            String weekday = day.getDayOfWeek().toString();

            Map<String, String> scheduleFilter = Map.of("day_of_week", weekday);
            LocalTime start = repo.lookup("config_schedules", scheduleFilter, "start_time");
            LocalTime end = repo.lookup("config_schedules", scheduleFilter, "end_time");

            todoList existing = repo.fetch(Table.TODOS, Map.of("date", day.toString()));

            if (existing != null) {
                // 2. Calendar sync: alte Events raus, neue rein, Überlappungen entplanen
                calendarSync(existing, day, start, end);
                lists.add(existing);
            } else {
                // Neuen leeren Plan mit Calendar-Events erstellen
                todoList plan = new todoList();
                plan.date = day;
                plan.start = start;
                plan.end = end;
                plan.timeSlots = new ArrayList<>();

                List<CalendarEvent> calEvents = calendar.getEventsForDay(day, start, end);
                for (CalendarEvent ev : calEvents) {
                    TimeSlot calSlot = new TimeSlot();
                    calSlot.start = ev.start().isBefore(start) ? start : ev.start();
                    calSlot.end = ev.end().isAfter(end) ? end : ev.end();
                    calSlot.isCalendarEvent = true;
                    calSlot.calendarTitle = ev.title();
                    calSlot.item = null;
                    calSlot.completed = null;
                    plan.timeSlots.add(calSlot);
                }

                lists.add(plan);
            }
        }

        // 3. Loop: Liste jede Iteration komplett neu aufbauen
        //    → Prios sind immer aktuell (REPS_PER_TIME Urgency, Parent-Blockierungen)
        //    → Verdrängte Items tauchen automatisch wieder auf (nicht mehr scheduled)
        Set<Long> skipped = new HashSet<>();
        while (true) {
            List<trackedItem> goals = getItems(days, null);
            goals.removeIf(g -> skipped.contains(g.id));
            if (goals.isEmpty()) break;

            List<PrioritizedItem> sorted = prioritize(goals, today);
            PrioritizedItem top = sorted.get(0);

            boolean placed = placeItem(top, lists);
            if (placed) {
                skipped.clear(); // Landscape hat sich geändert → alles nochmal probieren
            } else {
                skipped.add(top.item().id);
            }
        }

        // 5. Listen schreiben
        for (todoList list : lists) {
            LocalTime maxEnd = list.start;
            if (list.timeSlots != null) {
                for (TimeSlot slot : list.timeSlots) {
                    if (slot.end != null && slot.end.isAfter(maxEnd)) {
                        maxEnd = slot.end;
                    }
                }
            }
            list.end = maxEnd;
            repo.write(list);
        }
    }


    // ============================================================================
    // getItems - Holt relevante Items für einen Zeitraum (mehrere Tage)
    // ============================================================================
    private List<trackedItem> getItems(List<LocalDate> days, Long parentId) {
        List<trackedItem> relevantItems = new ArrayList<>();

        Map<String, String> filters;
        if (parentId != null) {
            filters = Map.of("parent", String.valueOf(parentId), "is_completed", "0");
        } else {
            filters = Map.of("type", "Goal", "is_completed", "0");
        }

        List<Long> openItems = repo.lookups("items", filters, "id");

        for (Long itemID : openItems) {
            trackedItem item = repo.fetch(Table.ITEMS, itemID);

            // Prüfen ob Item an mindestens einem der Tage eingeplant werden kann
            boolean canSchedule = false;
            for (LocalDate day : days) {
                // blockedDays check
                if (item.blockedDays != null && item.blockedDays.contains(day)) {
                    continue;
                }

                // Parent checks
                if (item.parent != null) {
                    trackedItem parent = repo.fetch(Table.ITEMS, item.parent);
                    if (parent != null) {
                        if (parent.scheduled != null && !parent.scheduled.isEmpty()
                            && day.isBefore(Collections.max(parent.scheduled))) {
                            continue;
                        }
                        if (parent.cooldown != 0 && item.lastCompletion != null
                            && day.isBefore(item.lastCompletion.plusDays(parent.cooldown))) {
                            continue;
                        }
                    }
                }

                // Cooldown check (blockedDays deckt Repetitions-Intervall ab)
                if (item.cooldown != 0 && item.lastCompletion != null
                    && day.isBefore(item.lastCompletion.plusDays(item.cooldown))) {
                    continue;
                }

                canSchedule = true;
                break;
            }

            if (canSchedule) {
                // REPS_PER_TIME: so oft hinzufügen wie noch Wiederholungen offen sind
                if (item.repetition != null && item.repetition.type == RepetitionType.REPS_PER_TIME) {
                    int reps = item.remainingReps(days.get(0));
                    for (int r = 0; r < Math.max(1, reps); r++) {
                        relevantItems.add(item);
                    }
                } else {
                    relevantItems.add(item);
                }
            }
        }

        return relevantItems;
    }


    // ============================================================================
    // prioritize - Sortiert nach kombinierter Dringlichkeit
    // ============================================================================
    private List<PrioritizedItem> prioritize(List<trackedItem> items, LocalDate day) {
        List<PrioritizedItem> prioritizedList = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (trackedItem item : items) {
            int priority = item.priority.value + (int)(item.priority.value * (item.overdue(today) * 0.5));

            if (item.repetition != null && item.repetition.type == RepetitionType.REPS_PER_TIME) {
                int remainingReps = item.remainingReps(day);
                if (remainingReps > 0) {
                    double daysPerRemainingRep = (double) item.remainingTime(day) / remainingReps;
                    double normalizedFrequency = Math.min(2.0, 1.0 + (1.0 / daysPerRemainingRep));
                    priority = (int)(priority * normalizedFrequency);
                }
            }

            prioritizedList.add(new PrioritizedItem(item, priority));
        }

        prioritizedList.sort((a, b) -> b.prio() - a.prio());
        return prioritizedList;
    }


    // ============================================================================
    // placeItem - Platziert Item im global besten Slot der nächsten 7 Tage.
    // ============================================================================
    private boolean placeItem(PrioritizedItem pi, List<todoList> lists) {
        trackedItem item = pi.item();
        int duration = item.timeToComplete;
        List<ScoredSlot> scoredSlots = new ArrayList<>();

        // Für jede todoListe mögliche Slots sammeln und bewerten
        for (todoList list : lists) {
            LocalDate day = list.date;

            // Ist der Tag für dieses Item blockiert?
            if (item.blockedDays != null && item.blockedDays.contains(day)) {
                continue;
            }

            // --- Freie Fenster in item-große Slots aufteilen ---
            List<LocalTime[]> freeWindows = findFreeWindows(list);
            for (LocalTime[] window : freeWindows) {
                LocalTime slotStart = window[0];
                LocalTime windowEnd = window[1];

                while (!slotStart.plusMinutes(duration).isAfter(windowEnd)) {
                    LocalTime slotEnd = slotStart.plusMinutes(duration);
                    int score = scoreSlot(pi, slotStart);
                    scoredSlots.add(new ScoredSlot(list, day, slotStart, slotEnd,
                                                   score, null));
                    slotStart = slotStart.plusMinutes(duration);
                }
            }

            // --- Belegte Slots prüfen (Verdrängung möglich?) ---
            if (list.timeSlots != null) {
                for (TimeSlot existing : list.timeSlots) {
                    if (existing.isCalendarEvent != null && existing.isCalendarEvent) continue;
                    if (existing.item == null) continue;

                    long slotMinutes = ChronoUnit.MINUTES.between(existing.start, existing.end);
                    if (slotMinutes < duration) continue;

                    int myScore = scoreSlot(pi, existing.start);

                    // Vergleich mit der slot-adjusted Prio des existierenden Items
                    int existingAdjustedPrio = (existing.adjustedPrio != null)
                        ? existing.adjustedPrio : 0;
                    if (myScore <= existingAdjustedPrio) continue;

                    int score = myScore - existingAdjustedPrio;
                    scoredSlots.add(new ScoredSlot(list, day, existing.start,
                                                   existing.start.plusMinutes(duration),
                                                   score, existing));
                }
            }
        }

        // Nichts gefunden → Item kann nicht platziert werden
        if (scoredSlots.isEmpty()) return false;

        // Besten Slot wählen (höchster Score)
        scoredSlots.sort((a, b) -> b.score() - a.score());
        ScoredSlot best = scoredSlots.get(0);

        // --- Verdrängung durchführen falls nötig ---
        if (best.existingSlot() != null) {
            unPlan(best.existingSlot(), best.list());
        }

        // --- Neuen TimeSlot erstellen und einfügen ---
        TimeSlot newSlot = new TimeSlot();
        newSlot.start = best.start();
        newSlot.end = best.end();
        newSlot.item = item.id;
        newSlot.completed = false;
        newSlot.adjustedPrio = scoreSlot(pi, best.start());

        // Falls Goal: Tasks über die gleiche Pipeline platzieren
        if (item.type == trackedItem.ItemType.GOAL) {
            fillGoalSlot(newSlot, best.day(), item);
        }

        // Slot in Liste einfügen (sortiert nach Startzeit)
        if (best.list().timeSlots == null) {
            best.list().timeSlots = new ArrayList<>();
        }
        int insertIdx = 0;
        for (int i = 0; i < best.list().timeSlots.size(); i++) {
            if (best.list().timeSlots.get(i).start.isAfter(newSlot.start)) break;
            insertIdx = i + 1;
        }
        best.list().timeSlots.add(insertIdx, newSlot);

        // scheduled updaten und blockedDays neu berechnen
        // (Verdrängte Items werden beim nächsten getItems()-Aufruf automatisch wieder aufgenommen)
        item.schedule(best.day(), repo);
        item.blockedDays = item.getBlockedDays();

        return true;
    }


    // ============================================================================
    // fillGoalSlot - Befüllt einen Goal-Slot mit Tasks über die placeItem-Pipeline
    // ============================================================================
    private void fillGoalSlot(TimeSlot goalSlot, LocalDate day, trackedItem goal) {
        // Virtuelle todoList für den Goal-Zeitraum
        todoList virtual = new todoList();
        virtual.date = day;
        virtual.start = goalSlot.start;
        virtual.end = goalSlot.end;
        virtual.timeSlots = new ArrayList<>();

        List<LocalDate> singleDay = List.of(day);
        List<todoList> virtualLists = List.of(virtual);
        Set<Long> skipped = new HashSet<>();

        // Gleiche Rebuild-Logik wie makeToDoList: Liste jede Iteration neu aufbauen
        while (true) {
            List<trackedItem> tasks = getItems(singleDay, goal.id);
            tasks.removeIf(t -> skipped.contains(t.id));
            if (tasks.isEmpty()) break;

            List<PrioritizedItem> sorted = prioritize(tasks, day);
            PrioritizedItem top = sorted.get(0);

            boolean placed = placeItem(top, virtualLists);
            if (placed) {
                skipped.clear();
            } else {
                skipped.add(top.item().id);
            }
        }

        goalSlot.timeSlots = virtual.timeSlots;

        // Goal-Slot Ende auf tatsächliches Ende kappen
        if (virtual.timeSlots != null && !virtual.timeSlots.isEmpty()) {
            LocalTime lastEnd = virtual.start;
            for (TimeSlot ts : virtual.timeSlots) {
                if (ts.end != null && ts.end.isAfter(lastEnd)) {
                    lastEnd = ts.end;
                }
            }
            goalSlot.end = lastEnd;
        }
    }


    // ============================================================================
    // calendarSync - Synchronisiert Calendar-Events und entplant Überlappungen
    // ============================================================================
    private void calendarSync(todoList list, LocalDate day, LocalTime start, LocalTime end) {
        // 1. Alte Calendar-Events entfernen
        if (list.timeSlots != null) {
            list.timeSlots.removeIf(s -> s.isCalendarEvent != null && s.isCalendarEvent);
        }

        // 2. Frische Events holen
        List<CalendarEvent> calEvents = calendar.getEventsForDay(day, start, end);
        if (calEvents.isEmpty()) return;

        if (list.timeSlots == null) list.timeSlots = new ArrayList<>();

        for (CalendarEvent ev : calEvents) {
            TimeSlot calSlot = new TimeSlot();
            calSlot.start = ev.start().isBefore(start) ? start : ev.start();
            calSlot.end = ev.end().isAfter(end) ? end : ev.end();
            calSlot.isCalendarEvent = true;
            calSlot.calendarTitle = ev.title();
            calSlot.item = null;
            calSlot.completed = null;

            // 3. Überlappende Goals entplanen
            List<TimeSlot> overlapping = new ArrayList<>();
            for (TimeSlot slot : list.timeSlots) {
                if (slot.item != null
                    && slot.start.isBefore(calSlot.end)
                    && slot.end.isAfter(calSlot.start)) {
                    overlapping.add(slot);
                }
            }
            for (TimeSlot ol : overlapping) {
                unPlan(ol, list);
            }

            // Einfügen (sortiert nach Startzeit)
            int idx = 0;
            for (int i = 0; i < list.timeSlots.size(); i++) {
                if (list.timeSlots.get(i).start.isAfter(calSlot.start)) break;
                idx = i + 1;
            }
            list.timeSlots.add(idx, calSlot);
        }
    }


    // ============================================================================
    // unPlan - Entfernt ein Item aus seinem Slot, gibt das verdrängte Item zurück.
    // ============================================================================
    private trackedItem unPlan(TimeSlot slot, todoList list) {
        trackedItem displaced = null;
        if (slot.item != null) {
            displaced = repo.fetch(Table.ITEMS, slot.item);

            // scheduled-Datum wieder entfernen
            if (displaced != null && displaced.scheduled != null) {
                displaced.scheduled.remove(list.date);
                displaced.blockedDays = displaced.getBlockedDays();
                repo.write(displaced);
            }
        }

        // Unter-Tasks auch entplanen (falls Goal)
        if (slot.timeSlots != null) {
            for (TimeSlot childSlot : slot.timeSlots) {
                if (childSlot.item != null) {
                    trackedItem childItem = repo.fetch(Table.ITEMS, childSlot.item);
                    if (childItem != null && childItem.scheduled != null) {
                        childItem.scheduled.remove(list.date);
                        childItem.blockedDays = childItem.getBlockedDays();
                        repo.write(childItem);
                    }
                }
            }
        }

        // Slot aus der Liste entfernen
        list.timeSlots.remove(slot);
        return displaced;
    }


    // ============================================================================
    // findFreeWindows - Findet freie Zeitfenster in einer todoList
    // ============================================================================
    private List<LocalTime[]> findFreeWindows(todoList list) {
        List<LocalTime[]> windows = new ArrayList<>();
        LocalTime cursor = list.start;

        if (list.timeSlots == null || list.timeSlots.isEmpty()) {
            windows.add(new LocalTime[]{list.start, list.end});
            return windows;
        }

        // TimeSlots nach Startzeit sortieren (Android-kompatibel, kein Stream.toList())
        List<TimeSlot> sorted = new ArrayList<>(list.timeSlots);
        sorted.sort((a, b) -> a.start.compareTo(b.start));

        for (TimeSlot slot : sorted) {
            if (cursor.isBefore(slot.start)) {
                windows.add(new LocalTime[]{cursor, slot.start});
            }
            cursor = slot.end.isAfter(cursor) ? slot.end : cursor;
        }

        // Lücke am Ende
        if (cursor.isBefore(list.end)) {
            windows.add(new LocalTime[]{cursor, list.end});
        }

        return windows;
    }


    // ============================================================================
    // scoreSlot - Bewertet wie gut ein Slot zeitlich zum Item passt (prefTime).
    // ============================================================================
    private int scoreSlot(PrioritizedItem pi, LocalTime slotStart) {
        if (pi.item().prefTime == null) {
            return pi.prio();
        }

        // positiv = Cursor ist NACH prefTime, negativ = Cursor ist VOR prefTime
        long diff = ChronoUnit.MINUTES.between(pi.item().prefTime, slotStart);
        double normalizedDiff;
        if (diff >= 0) {
            // Auf oder nach prefTime → keine Strafe
            normalizedDiff = 1.0;
        } else {
            // Vor prefTime → Strafe proportional zur Distanz (max 8h)
            normalizedDiff = Math.max(0.0, 1.0 + (diff / 480.0));
        }

        double logPrio = Math.log1p(pi.prio());
        return (int)(logPrio * normalizedDiff * normalizedDiff * 100);
    }
}
