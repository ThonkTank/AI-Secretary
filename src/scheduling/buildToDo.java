package scheduling;

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
     * DESIGN:
     *   - Items werden intelligent über die gesamte Woche verteilt (globale Slot-Bewertung)
     *   - placeItem bewertet ALLE Slots aller 7 Tage gleichzeitig und wählt den global besten
     *   - Verdrängung: Höher-priorisierte Items können niedriger-priorisierte verdrängen
     *   - Slot-adjusted Prio wird im TimeSlot persistiert für fairen Verdrängungsvergleich
     *   - Kein Split zwischen augment/create: alles läuft über dieselbe Pipeline
     *   - Goal-Tasks werden über placeItem platziert
     *
     * TODO: Aufgaben mit mehreren Wiederholungen pro Tag ermöglichen.
     * 
     * ──────────────────────────────────────────────────────────────────────────────
     * ALGORITHMUS-ABLAUF
     * ──────────────────────────────────────────────────────────────────────────────
     *  private List allSlots
     * 
     *  planWeek() {
     *      todoLists = alle todolists der nächsten 7 Tage aus db, evtl neue wenn ein Tag noch keine hat
     *      todoLists = calendarSync(todoLists)
     *      allSlots = aggregateSlots(todoLists)
     *      fillSlots(goal, null, allSlots)
     *
     *      für alle Goal-Slots in allSlots
     *          fillGoalTasks(goalSlot, list) //Tasks innerhalb Goal-Grenzen neu platzieren
     *
     *      für jede todoListe in todoLists
     *          mit aktualisierten Slots updaten
     *      return todoLists
     * }
     * 
     * calendarSync(todoLists) {
     *      für jede todoList in todoLists
     *          entferne alte calendar events
     *          hole aktuelle kalendar events
     *          unplan(überlappende Tasks)
     *      return aktualisierte todoLists
     * }
     * 
     * fillSlots(typ, parent, list) {
     *      loopen bis nichtsmehr platziert werden kann
     *          relevantTasks = getItems(typ, parent)
     *          scoredItems = prioritizeItems(relevantTasks)
     *          Für alle scoredItems
     *              match <item, slot> = tryMatch(scoredItems, list)
     *          if match != null
     *              assignSlot(match, list)
     *          else
     *              break
     * }
     * 
     * aggregateSlots(todoLists) {
     *      für jede todoListe in todoLists:
     *          emptyWindows = Leere Zeitfenster finden
     *          für alle windows in emptyWindows
     *              zu einem Slot machen (wenn am Montag von 14-16 nichts geplant ist wird das ein 2 Stunden slot)
     *              allSlots.add(this)
     *          für alle befüllten Slots
     *              allSlots.add(this)
     *      allSlots chronologisch sortieren (Datum, dann Uhrzeit)
     * }
     *
     * fillGoalTasks(goalSlot, list) {
     *      virtual = todoList mit goalSlot-Grenzen und bestehenden Sub-Slots
     *      taskSlots = aggregateSlots(virtual)
     *      fillSlots(Task, goalSlot.item, taskSlots)
     *      goalSlot.timeSlots = virtual.timeSlots
     *      goalSlot.end auf Ende des tatsächlichen Inhalts setzen
     * }
     * 
     * getItems(typ, parent) {
     *      Für alle Items in db des gesuchten typs mit passendem parent wenn parent != null:
     *          blockedDays mit kommenden 7 Tagen vergleichen
     *          wenn item = goal && item.parent != null
     *              item.parent.blockedDays mit kommenden 7 Tagen vergleichen.
     *          wenn mind. einer der 7 Tage nicht in blocked days:
     *          relevantItems.add(item) //für reps_Per_Time so oft wie nötig wiederholen
     *      return relevantItems
     * }
     * 
     * prioritize(relevantItems) {
     *      Für jedes Item:
     *      1. Basisdringlichkeit = priority.value + (priority.value * item.overdue(today) * 0.5)
     *      2. Für RepsPerTimeRepetition zusätzlich:
     *         → daysPerRemainingRep = item.remainingTime() / item.remainingReps() //berechnet automatisch übrige Zeit in periode, rechnet bereits blockierte/scheduled Tage raus)
     *         → normalizedFrequency = min(2.0, 1.0 + (1.0 / daysPerRemainingRep))
     *         → priority *= normalizedFrequency
     *      3. Sortiere nach Dringlichkeit (höchste zuerst)
     *      Return: List<PrioritizedItem> (record mit item und prio)
     * }
     * 
     * tryMatch(itemList, slotList) {
     *      Für jedes Item in itemList
     *          parentItem = Goal→Project Parent holen (einmal pro Item)
     *          für jeden slot in slotList:
     *              blockedDays-Prüfung: item.blockedDays und parentItem.blockedDays gegen slot.day
     *              slotCoverage = min(1.0, slotMinuten / requiredTime) // 1.0 wenn genug Platz, sonst proportional reduziert
     *              diff = Minuten zwischen prefTime und slot.start //wenn slot größer als benötigte zeit, bestmögliche startzeit innerhalb des slots verwenden statt slot.start
     *              Wenn diff >= 0 (auf/nach prefTime): normalizedDiff = 1.0 (keine Strafe)
     *              Wenn diff < 0 (vor prefTime): normalizedDiff = max(0.0, 1.0 + diff/480)
     *              adjustedPrio = log1p(prio) * normalizedDiff² * slotCoverage * 100
     *              wenn slot besetzt
     *                  adjustedPrio - slot.prio
     *          bestSlot = slotList.maxPrio
     *          Wenn bestSlot.prio > 0
     *              match = <item, bestSlot
     *              break
     *      Return: match
     * }
     * 
     * assignSlot(item, Slot, list) {
     *      wenn slot bereits belegt
     *          unplan(Slot)
     *      TaskSlots = alle sub-slots im slot + ggf freie 
     *      wenn Slot zu groß für item
     *          slot.start zu bester start zeit für Item machen
     *      Slot mit allen relevanten goal Daten befüllen
     *      Wenn item.type = goal
     *          fillSlots(Task, slot.item, taskSlots)
     *      slot.end auf ende des tatsächlichen Inhalts setzen
     *      ggf. neue freie Slots in list erstellen, für jetzt freie Zeit
     *      item.schedule(day)
     * }
     * 
     * unplanSlot(slot) {
     *      entplant item aus slot
     * }
     * 
     * ──────────────────────────────────────────────────────────────────────
     * BLOCKEDDAYS-DESIGN
     * ──────────────────────────────────────────────────────────────────────────────
     *
     *  Jedes Item hat eigene blockedDays (berechnet von trackedItem.getBlockedDays()):
     *    1. Cooldown-Fenster ±N Tage um lastCompletion und jedes scheduled-Datum
     *    2. Alle Tage zwischen lastCompletion und calcNextRepetition() (nicht für REPS_PER_TIME)
     *
     *  blockedDays wird automatisch neu berechnet bei:
     *    - schedule()  → Item eingeplant (+ Parent-Propagation für Goal → Project)
     *    - unPlan()    → Item verdrängt
     *    - update()    → Tagesabschluss durch cleanToDo
     *
     *  Goals erben NICHT parent.blockedDays. Stattdessen prüfen getItems() und tryMatch()
     *  separat parent.blockedDays (nur für Goal mit Project-Parent).
     *
     */

    public record PrioritizedItem(trackedItem item, int prio) {}
    record SlotCandidate(TimeSlot slot, todoList list, TimeSlot displaceable) {}
    record Match(PrioritizedItem item, SlotCandidate slot) {}

    /** Liefert Kalender-Events für einen Tag. Abstrahiert CalendarReader für Testbarkeit. */
    @FunctionalInterface
    public interface CalendarProvider {
        List<CalendarEvent> getEventsForDay(LocalDate day, LocalTime start, LocalTime end);
    }

    Repo repo;
    CalendarProvider calendar;

    public buildToDo(Repo repo, CalendarProvider calendar) {
        this.repo = repo;
        this.calendar = calendar;
    }


    // ============================================================================
    // planWeek - Hauptfunktion, erstellt/aktualisiert ToDoListen für die nächsten 7 Tage.
    // ============================================================================
    public List<todoList> planWeek() {
        LocalDate today = LocalDate.now();
        List<todoList> lists = new ArrayList<>();

        // 1. todoLists = alle todolists der nächsten 7 Tage aus db, evtl neue wenn ein Tag noch keine hat
        for (int i = 0; i < 7; i++) {
            LocalDate day = today.plusDays(i);
            String weekday = day.getDayOfWeek().toString();

            Map<String, String> scheduleFilter = Map.of("day_of_week", weekday);
            LocalTime start = repo.lookup("config_schedules", scheduleFilter, "start_time");
            LocalTime end = repo.lookup("config_schedules", scheduleFilter, "end_time");

            todoList existing = repo.fetch(Table.TODOS, Map.of("date", day.toString()));

            if (existing != null) {
                calendarSync(existing, day, start, end);
                lists.add(existing);
            } else {
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
                    plan.timeSlots.add(calSlot);
                }

                lists.add(plan);
            }
        }

        // 2. allSlots = aggregateSlots(todoLists)
        List<SlotCandidate> allSlots = aggregateSlots(lists);

        // 3. fillSlots(goal, null, allSlots) - Goals in freie Slots platzieren
        fillSlots(trackedItem.ItemType.GOAL, null, allSlots);

        // 4. Für alle befüllten Goal-Slots: Tasks innerhalb der Goal-Grenzen neu platzieren
        for (SlotCandidate sc : new ArrayList<>(allSlots)) {
            if (sc.slot().item != null) {
                fillGoalTasks(sc.slot(), sc.list());
            }
        }

        // 5. todoLists mit aktualisierten Slots updaten und persistieren
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

        return lists;
    }


    // ============================================================================
    // getItems - Holt relevante Items eines Typs, optional gefiltert nach Parent.
    //   Berechnet intern die kommenden 7 Tage und prüft blockedDays.
    // ============================================================================
    private List<trackedItem> getItems(trackedItem.ItemType typ, Long parent) {
        List<trackedItem> relevantItems = new ArrayList<>();
        LocalDate today = LocalDate.now();

        // Kommende 7 Tage berechnen
        List<LocalDate> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            days.add(today.plusDays(i));
        }

        // Filter nach Typ und optional nach Parent
        Map<String, String> filters;
        if (parent != null) {
            filters = Map.of("type", typ.name(), "parent", String.valueOf(parent), "is_completed", "0");
        } else {
            filters = Map.of("type", typ.name(), "is_completed", "0");
        }

        List<Long> openItems = repo.lookups("items", filters, "id");

        for (Long itemID : openItems) {
            trackedItem item = repo.fetch(Table.ITEMS, itemID);

            // Prüfen ob Item an mindestens einem der 7 Tage eingeplant werden kann
            boolean canSchedule = false;
            for (LocalDate day : days) {
                if (!item.isBlockedOn(day, repo)) {
                    canSchedule = true;
                    break;
                }
            }

            if (canSchedule) {
                relevantItems.add(item);
            }
        }

        return relevantItems;
    }


    // ============================================================================
    // prioritize - Sortiert nach kombinierter Dringlichkeit
    // ============================================================================
    private List<PrioritizedItem> prioritize(List<trackedItem> items) {
        List<PrioritizedItem> prioritizedList = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (trackedItem item : items) {
            int priority = item.priority.value + (int)(item.priority.value * (item.overdue(today) * 0.5));

            if (item.repetition != null && item.repetition.type == RepetitionType.REPS_PER_TIME) {
                int remainingReps = item.remainingReps(today);
                if (remainingReps > 0) {
                    double daysPerRemainingRep = (double) item.remainingTime(today) / remainingReps;
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
    // tryMatch - Iteriert priorisierte Items und findet den besten Slot für das
    //   höchstpriorisierte Item, das platziert werden kann.
    //   Bewertet prefTime-Passung (log. Score), Slot-Abdeckung (zu kurze Slots
    //   werden proportional bestraft) und Verdrängung besetzter Slots.
    //   Gibt null zurück wenn kein Item platzierbar ist.
    // ============================================================================
    private Match tryMatch(List<PrioritizedItem> itemList, List<SlotCandidate> slotList) {
        for (PrioritizedItem pi : itemList) {
            trackedItem item = pi.item();
            int duration = item.timeToComplete;

            SlotCandidate bestCandidate = null;
            int bestScore = 0; // Muss > 0 sein für gültigen Match
            LocalTime bestEffectiveStart = null;
            int bestAdjustedPrio = 0;

            for (SlotCandidate c : slotList) {
                // blockedDays-Prüfung pro Slot-Tag
                if (item.isBlockedOn(c.list().date, repo)) continue;

                TimeSlot slt = c.slot();
                long slotMinutes = ChronoUnit.MINUTES.between(slt.start, slt.end);

                // Tasks müssen komplett reinpassen, Goals dürfen partiell eingeplant werden
                if (item.type == trackedItem.ItemType.TASK && slotMinutes < duration) continue;

                // Slot-Abdeckung: wenn Slot kürzer als benötigte Zeit (nur Goals),
                // Prio proportional reduzieren (z.B. 50% der Zeit → 50% der Prio)
                double slotCoverage = Math.min(1.0, (double) slotMinutes / duration);

                // Effektive Startzeit: wenn Slot größer als benötigte Zeit,
                // bestmögliche Startzeit innerhalb des Slots verwenden
                LocalTime effectiveStart = slt.start;
                if (slotMinutes > duration && pi.item().prefTime != null) {
                    LocalTime latestStart = slt.end.minusMinutes(duration);
                    if (pi.item().prefTime.isBefore(slt.start)) {
                        effectiveStart = slt.start;
                    } else if (pi.item().prefTime.isAfter(latestStart)) {
                        effectiveStart = latestStart;
                    } else {
                        effectiveStart = pi.item().prefTime;
                    }
                }

                // Score: log1p(prio) * normalizedDiff² * slotCoverage * 100
                int adjustedPrio;
                if (pi.item().prefTime == null) {
                    adjustedPrio = (int)(Math.log1p(pi.prio()) * slotCoverage * 100);
                } else {
                    long diff = ChronoUnit.MINUTES.between(pi.item().prefTime, effectiveStart);
                    double normalizedDiff = (diff >= 0) ? 1.0
                            : Math.max(0.0, 1.0 + (diff / 480.0));
                    adjustedPrio = (int)(Math.log1p(pi.prio()) * normalizedDiff * normalizedDiff * slotCoverage * 100);
                }

                // Verdrängung: adjustedPrio muss höher sein als belegter Slot
                int rankingScore = adjustedPrio;
                if (c.displaceable() != null) {
                    int existingPrio = (c.displaceable().adjustedPrio != null)
                            ? c.displaceable().adjustedPrio : 0;
                    if (adjustedPrio <= existingPrio) continue;
                    rankingScore = adjustedPrio - existingPrio;
                }

                if (rankingScore > bestScore) {
                    bestScore = rankingScore;
                    bestCandidate = c;
                    bestEffectiveStart = effectiveStart;
                    bestAdjustedPrio = adjustedPrio;
                }
            }

            // Erstes Item mit gültigem Match → sofort zurückgeben
            if (bestCandidate != null) {
                bestCandidate.slot().adjustedPrio = bestAdjustedPrio;
                return new Match(pi, bestCandidate);
            }
        }
        return null;
    }


    // ============================================================================
    // aggregateSlots - Sammelt alle Slots einer Liste.
    //   Für jede todoList: freie Lücken + belegte Slots aufnehmen, chronologisch sortieren.
    // ============================================================================
    private List<SlotCandidate> aggregateSlots(List<todoList> lists) {
        List<SlotCandidate> allSlots = new ArrayList<>();
        for (todoList list : lists) {
            // Freie Lücken
            List<LocalTime[]> freeWindows = findFreeWindows(list);
            for (LocalTime[] window : freeWindows) {
                TimeSlot freeSlot = new TimeSlot();
                freeSlot.start = window[0];
                freeSlot.end = window[1];
                allSlots.add(new SlotCandidate(freeSlot, list, null));
            }

            // Befüllte Slots als verdrängbar aufnehmen (keine Calendar-Events)
            if (list.timeSlots != null) {
                for (TimeSlot slot : list.timeSlots) {
                    if (slot.item != null && (slot.isCalendarEvent == null || !slot.isCalendarEvent)) {
                        allSlots.add(new SlotCandidate(slot, list, slot));
                    }
                }
            }
        }

        // Chronologisch sortieren (Datum zuerst, dann Uhrzeit)
        allSlots.sort((a, b) -> {
            int dateCompare = a.list().date.compareTo(b.list().date);
            if (dateCompare != 0) return dateCompare;
            return a.slot().start.compareTo(b.slot().start);
        });
        return allSlots;
    }


    // ============================================================================
    // fillSlots - Füllt Slots iterativ: holt Items, priorisiert, matcht und weist zu.
    //   Loopt bis kein Item mehr platziert werden kann.
    // ============================================================================
    private void fillSlots(trackedItem.ItemType typ, Long parent, List<SlotCandidate> slotList) {
        while (true) {
            List<trackedItem> relevantTasks = getItems(typ, parent);
            if (relevantTasks.isEmpty()) break;

            List<PrioritizedItem> scoredItems = prioritize(relevantTasks);
            Match match = tryMatch(scoredItems, slotList);

            if (match != null) {
                assignSlot(match.item().item(), match.slot(), slotList);
            } else {
                break;
            }
        }
    }


    // ============================================================================
    // fillGoalTasks - Füllt Tasks innerhalb eines Goal-Slots (scoped auf Goal-Grenzen).
    //   Erstellt virtuelle todoList mit den Goal-Grenzen, aggregiert Sub-Slots und
    //   platziert Tasks nur innerhalb dieses Bereichs.
    // ============================================================================
    private void fillGoalTasks(TimeSlot goalSlot, todoList list) {
        todoList virtual = new todoList();
        virtual.date = list.date;
        virtual.start = goalSlot.start;
        virtual.end = goalSlot.end;
        virtual.timeSlots = (goalSlot.timeSlots != null)
                ? new ArrayList<>(goalSlot.timeSlots) : new ArrayList<>();

        List<SlotCandidate> taskSlots = aggregateSlots(List.of(virtual));
        fillSlots(trackedItem.ItemType.TASK, goalSlot.item, taskSlots);

        goalSlot.timeSlots = virtual.timeSlots;

        // goalSlot.end auf Ende des tatsächlichen Inhalts setzen
        if (goalSlot.timeSlots != null && !goalSlot.timeSlots.isEmpty()) {
            LocalTime actualEnd = goalSlot.start;
            for (TimeSlot ts : goalSlot.timeSlots) {
                if (ts.end != null && ts.end.isAfter(actualEnd)) {
                    actualEnd = ts.end;
                }
            }
            goalSlot.end = actualEnd;
        }
    }


    // ============================================================================
    // assignSlot - Weist ein Item einem Slot zu, befüllt Goals mit Tasks,
    //   erstellt freie Slots für übrige Zeit und plant das Item ein.
    // ============================================================================
    private void assignSlot(trackedItem item, SlotCandidate candidate, List<SlotCandidate> slotList) {
        TimeSlot slot = candidate.slot();
        todoList list = candidate.list();

        // Originale Grenzen merken
        LocalTime originalStart = slot.start;
        LocalTime originalEnd = slot.end;

        // 1. Wenn Slot bereits belegt → unplan
        if (candidate.displaceable() != null) {
            unPlan(candidate.displaceable(), list);
        }

        // 2. Wenn Slot zu groß für Item → beste Startzeit berechnen
        long slotMinutes = ChronoUnit.MINUTES.between(originalStart, originalEnd);
        if (slotMinutes > item.timeToComplete && item.prefTime != null) {
            LocalTime latestStart = originalEnd.minusMinutes(item.timeToComplete);
            if (item.prefTime.isBefore(originalStart)) {
                slot.start = originalStart;
            } else if (item.prefTime.isAfter(latestStart)) {
                slot.start = latestStart;
            } else {
                slot.start = item.prefTime;
            }
        }
        // End-Zeit: auf Slot-Grenze begrenzen falls Slot kürzer als Item
        long actualDuration = Math.min(item.timeToComplete, slotMinutes);
        slot.end = slot.start.plusMinutes(actualDuration);

        // 3. Slot mit Item-Daten befüllen
        slot.item = item.id;
        slot.completed = false;

        // 4. Wenn Goal → Tasks innerhalb des Goal-Slots platzieren
        if (item.type == trackedItem.ItemType.GOAL) {
            fillGoalTasks(slot, list);
        }

        // 5. Slot zur todoList hinzufügen (sortiert nach Startzeit)
        if (list.timeSlots == null) {
            list.timeSlots = new ArrayList<>();
        }
        int idx = 0;
        for (int i = 0; i < list.timeSlots.size(); i++) {
            if (list.timeSlots.get(i).start.isAfter(slot.start)) break;
            idx = i + 1;
        }
        list.timeSlots.add(idx, slot);

        // 6. Neue freie Slots für jetzt freie Zeit erstellen
        if (originalStart.isBefore(slot.start)) {
            TimeSlot freeBefore = new TimeSlot();
            freeBefore.start = originalStart;
            freeBefore.end = slot.start;
            slotList.add(new SlotCandidate(freeBefore, list, null));
        }
        if (slot.end.isBefore(originalEnd)) {
            TimeSlot freeAfter = new TimeSlot();
            freeAfter.start = slot.end;
            freeAfter.end = originalEnd;
            slotList.add(new SlotCandidate(freeAfter, list, null));
        }

        // Verwendeten SlotCandidate aus der Liste entfernen
        slotList.remove(candidate);

        // 7. Item einplanen
        item.schedule(list.date, repo);
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
}
