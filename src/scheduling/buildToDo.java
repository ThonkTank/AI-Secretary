package scheduling;

import repository.Repo;
import repository.Table;
import entities.Account;
import entities.CalendarEvent;
import entities.Transaction;
import entities.todoList;
import entities.trackedItem;
import entities.todoList.TimeSlot;
import entities.trackedItem.RepetitionType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class buildToDo {

    // ============================================================================
    // SCHEDULING-KONSTANTEN
    // ============================================================================

    /** Sehr hohe Prioritaet fuer feste Termine - uebertrifft alle normalen Tasks */
    private static final int FIXED_APPOINTMENT_PRIORITY = 10_000_000;

    /** Zeitfenster fuer PrefTime-Penalty in Minuten (8-Stunden-Arbeitstag) */
    private static final int PREF_TIME_WINDOW_MINUTES = 480;

    /** Bonus pro Item in einer Kette, um laengere Ketten zu bevorzugen */
    private static final int CHAIN_LENGTH_BONUS_PER_ITEM = 50;

    /** Skalierungsfaktor fuer log1p-basierte Score-Berechnung */
    private static final int SCORE_SCALE_FACTOR = 100;

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
     *   - Goal-Tasks werden über placeItem platziert.
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
     * prioritize {
     * int work = verbleibende Arbeit (reps*deadline je min 1)
     * LocalDate untill = nächste relevante deadline (bei repeating tasks ende der wiederholenden Periode, oder deadline etc.)
     * remainingTime = Tage bis untill
     * 
     * Basisdringlichkeit = priority.value + (priority.value * item.overdue(today) * 0.5)
     *      2. Für RepsPerTimeRepetition zusätzlich:
     *         → daysPerWork = item.remainingTime() / item.work() //berechnet automatisch übrige Zeit in periode, rechnet bereits blockierte/scheduled Tage raus)
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

    /**
     * Eine Kette von Items die durch requiredPredecessor verbunden sind.
     * Einzelne Items ohne Chain sind Listen mit einem Element.
     */
    private record TaskChain(List<trackedItem> tasks, int prio) {}

    record SlotCandidate(TimeSlot slot, todoList list, TimeSlot displaceable) {}

    /**
     * Match-Ergebnis: Welche Items der Kette passen, wo sie platziert werden.
     * toDisplace enthält ALLE zu verdrängenden Slots (inkl. anderer Chain-Mitglieder für atomare Verdrängung).
     * adjustedPrio = Summe der slot-spezifischen Prioritäten aller fittingTasks.
     */
    private record ChainMatch(
        List<trackedItem> fittingTasks,  // Items die platziert werden
        LocalTime startTime,              // Startzeit
        todoList targetList,              // Ziel-Tag
        Set<TimeSlot> toDisplace,         // Alle zu verdrängenden Slots (atomar)
        int adjustedPrio                  // Gesamtscore der Chain
    ) {}

    /** Liefert Kalender-Events für einen Tag. Abstrahiert CalendarReader für Testbarkeit. */
    @FunctionalInterface
    public interface CalendarProvider {
        List<CalendarEvent> getEventsForDay(LocalDate day, LocalTime start, LocalTime end);
    }

    /**
     * Repräsentiert einen Scheduling-Konflikt für feste Termine.
     * Wird gesammelt und kann über getConflicts() abgefragt werden.
     */
    public record SchedulingConflict(
        Long itemId,
        String itemTitle,
        LocalDate conflictDate,
        String reason  // "FIXED_OVERLAP", "CALENDAR_OVERLAP", "DAY_BOUNDS"
    ) {}

    Repo repo;
    CalendarProvider calendar;

    // Budget-Tracking: Akkumuliert Budget während Scheduling-Loop
    private int committedBudgetCents = 0;
    // Budget-Cache: Basis-Budget wird einmal pro planWeek() berechnet
    private int cachedBaseBudget = -1;
    // Konflikt-Tracking: Feste Termine die nicht eingeplant werden konnten
    private List<SchedulingConflict> conflicts = new ArrayList<>();

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

        // Reset Budget-Tracking für neuen Durchlauf
        committedBudgetCents = 0;
        // Cache Basis-Budget einmalig (ohne accountId = alle aktiven Konten)
        cachedBaseBudget = controller.budgetManager.calculateFreeBudget(repo, null, today);
        // Reset Konflikt-Tracking für neuen Durchlauf
        conflicts = new ArrayList<>();

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

        // Cache zurücksetzen für nächsten Durchlauf
        cachedBaseBudget = -1;

        return lists;
    }

    /** Liefert Scheduling-Konflikte aus dem letzten planWeek()-Durchlauf. */
    public List<SchedulingConflict> getConflicts() {
        return conflicts;
    }

    // ============================================================================
    // getFreeBudgetCents - Berechnet verfügbares Budget für Scheduling
    // ============================================================================
    /**
     * Berechnet das freie Budget unter Berücksichtigung:
     * 1. Aktueller Kontostand (oder spezifisches Konto)
     * 2. Abzug wiederkehrender Ausgaben der nächsten 7 Tage (pessimistisch)
     * 3. Abzug bereits committeter Budget-Tasks in diesem Durchlauf
     *
     * Nutzt die gemeinsame Berechnung aus budgetManager.calculateFreeBudget()
     *
     * @param accountId Spezifisches Konto (null = alle aktiven mit includeInTotal)
     * @param today Referenzdatum
     * @return Freies Budget in Cents (min 0)
     */
    private int getFreeBudgetCents(Long accountId, LocalDate today) {
        // Nutze Cache für null-Account (alle Konten), sonst frisch berechnen
        int baseBudget = (accountId == null && cachedBaseBudget >= 0)
            ? cachedBaseBudget
            : controller.budgetManager.calculateFreeBudget(repo, accountId, today);

        // Scheduling-spezifisch: Bereits committete Budget-Tasks abziehen
        return Math.max(0, baseBudget - committedBudgetCents);
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

            // Delayed items (delay>0) überspringen wenn Predecessor nicht ready
            // delay=0 items werden von buildChains() gruppiert
            if (item.predecessor != null && item.predecessorDelay > 0) {
                if (!item.isPredecessorReady(repo)) {
                    continue;
                }
            }

            // Feste Termine: Nur wenn fixedDate im 7-Tage-Fenster liegt
            if (item.isFixedAppointment()) {
                if (!days.contains(item.fixedDate)) continue;
                // Keine weitere blockedDays-Prüfung - feste Termine MÜSSEN eingeplant werden
                relevantItems.add(item);
                continue;
            }

            // Prüfen ob Item an mindestens einem der 7 Tage eingeplant werden kann
            boolean canSchedule = false;
            for (LocalDate day : days) {
                if (!item.isBlockedOn(day, repo)) {
                    canSchedule = true;
                    break;
                }
            }

            if (!canSchedule) {
                continue;
            }

            // Budget-Check: Skip wenn Budget nicht ausreicht
            if (item.budgetRequirementCents > 0) {
                int freeBudget = getFreeBudgetCents(item.budgetAccountId, today);
                if (freeBudget < item.budgetRequirementCents) {
                    continue;  // Task nicht einplanbar wegen Budget
                }
            }

            relevantItems.add(item);
        }

        return relevantItems;
    }


    // ============================================================================
    // buildChains - Gruppiert Items zu Ketten basierend auf predecessor (delay=0)
    // ============================================================================
    /**
     * Gruppiert Items zu Ketten basierend auf predecessor mit delay=0.
     * Items mit delay>0 werden NICHT verkettet (haben Zeit-Constraints).
     * Items ohne Predecessor/Successor werden als Einzelketten behandelt.
     * Kette bekommt die SUMME der Prios aller Mitglieder.
     */
    private List<TaskChain> buildChains(List<trackedItem> items) {
        LocalDate today = LocalDate.now();

        Map<Long, trackedItem> byId = new HashMap<>();
        for (trackedItem item : items) {
            byId.put(item.id, item);
        }

        // Finde für jedes Item seinen Nachfolger (nur delay=0 für Same-Day-Chains!)
        Map<Long, Long> successorOf = new HashMap<>();
        for (trackedItem item : items) {
            if (item.predecessor != null
                && item.predecessorDelay == 0  // Nur delay=0 verketten
                && byId.containsKey(item.predecessor)) {
                successorOf.put(item.predecessor, item.id);
            }
        }

        Set<Long> processed = new HashSet<>();
        List<TaskChain> chains = new ArrayList<>();

        // Feste Termine zuerst als hochpriorisierte Einzelketten
        for (trackedItem item : items) {
            if (item.isFixedAppointment()) {
                chains.add(new TaskChain(List.of(item), FIXED_APPOINTMENT_PRIORITY));
                processed.add(item.id);
            }
        }

        for (trackedItem item : items) {
            if (processed.contains(item.id)) continue;

            // Finde Ketten-Kopf (kein Predecessor oder Predecessor nicht in Liste)
            trackedItem head = item;
            while (head.predecessor != null
                   && head.predecessorDelay == 0
                   && byId.containsKey(head.predecessor)) {
                head = byId.get(head.predecessor);
            }

            // Baue Kette: head → successor → successor...
            List<trackedItem> chain = new ArrayList<>();
            trackedItem current = head;
            int totalPrio = 0;

            while (current != null) {
                chain.add(current);
                processed.add(current.id);

                // Prio berechnen (wie in alter prioritize())
                int itemPrio = current.priority.value +
                              (int)(current.priority.value * current.overdue(today) * 0.5);

                // Deadline/Work-Boost
                if (current.deadline != null && ChronoUnit.DAYS.between(today, current.deadline) <= 0) {
                    itemPrio = (int)(itemPrio * 3.0);
                } else {
                    int work = current.work(today);
                    int time = current.remainingTime(today);
                    if (work > 0 && time > 0) {
                        double frequency = Math.min(2.0, 1.0 + (double) work / time);
                        itemPrio = (int)(itemPrio * frequency);
                    }
                }

                totalPrio += itemPrio;

                // Nächstes Item in Kette
                Long successorId = successorOf.get(current.id);
                current = (successorId != null) ? byId.get(successorId) : null;
            }

            chains.add(new TaskChain(chain, totalPrio));
        }

        // Nach Prio sortieren (höchste zuerst)
        chains.sort((a, b) -> b.prio() - a.prio());
        return chains;
    }


    // ============================================================================
    // tryMatchChain - Findet die beste (Startpunkt × Länge)-Kombination für Chains
    // ============================================================================
    /**
     * Evaluiert alle möglichen (Startpunkt × Länge)-Kombinationen für alle Chains.
     * Für jede Kombination: netScore = Summe(Chain-Item-Scores) - Summe(verdrängte Scores).
     * Chains werden nur platziert wenn netScore > 0.
     *
     * WICHTIG: Atomare Chain-Verdrängung - wenn ein Slot zu einer Chain gehört,
     * werden ALLE Slots dieser Chain in die Verdrängungsberechnung einbezogen.
     */
    private ChainMatch tryMatchChain(List<TaskChain> chains, List<SlotCandidate> slotList) {
        // Sammle alle möglichen Startpunkte pro Tag
        Map<todoList, List<LocalTime>> startsByDay = collectStartPointsByDay(slotList);

        ChainMatch globalBest = null;
        int globalBestScore = 0;  // Muss > 0 sein

        for (TaskChain chain : chains) {
            if (chain.tasks().isEmpty()) continue;

            trackedItem firstItem = chain.tasks().get(0);

            // Feste Termine: NUR exakten Tag und Zeit evaluieren
            if (firstItem.isFixedAppointment()) {
                // Finde die todoList für den festen Tag
                todoList targetList = null;
                for (todoList list : startsByDay.keySet()) {
                    if (list.date.equals(firstItem.fixedDate)) {
                        targetList = list;
                        break;
                    }
                }
                if (targetList == null) continue;  // Tag nicht im Plan

                LocalTime fixedStart = firstItem.fixedTime;
                LocalTime endTime = fixedStart.plusMinutes(firstItem.getSlotDuration());

                // Tagesgrenzen prüfen
                if (exceedsDayBounds(fixedStart, endTime, targetList)) {
                    conflicts.add(new SchedulingConflict(
                        firstItem.id, firstItem.title, firstItem.fixedDate, "DAY_BOUNDS"));
                    continue;
                }

                // Calendar-Event-Konflikt prüfen
                if (overlapsCalendarEvent(fixedStart, endTime, targetList)) {
                    conflicts.add(new SchedulingConflict(
                        firstItem.id, firstItem.title, firstItem.fixedDate, "CALENDAR_OVERLAP"));
                    continue;
                }

                // Überlappende Slots finden
                Set<TimeSlot> overlapping = findOverlappingSlots(fixedStart, endTime, targetList);

                // Prüfen ob ein überlappender Slot auch ein fester Termin ist
                boolean conflictsWithFixed = false;
                for (TimeSlot slot : overlapping) {
                    if (slot.item != null) {
                        trackedItem existing = repo.fetch(Table.ITEMS, slot.item);
                        if (existing != null && existing.isFixedAppointment()) {
                            conflictsWithFixed = true;
                            break;
                        }
                    }
                }
                if (conflictsWithFixed) {
                    conflicts.add(new SchedulingConflict(
                        firstItem.id, firstItem.title, firstItem.fixedDate, "FIXED_OVERLAP"));
                    continue;  // Kann anderen festen Termin nicht verdrängen
                }

                // Erweitere auf volle Chains
                Set<TimeSlot> toDisplace = expandToFullChains(overlapping, targetList);

                // Fester Termin mit sehr hoher Prio
                if (FIXED_APPOINTMENT_PRIORITY > globalBestScore) {
                    globalBestScore = FIXED_APPOINTMENT_PRIORITY;
                    globalBest = new ChainMatch(List.of(firstItem), fixedStart, targetList, toDisplace, FIXED_APPOINTMENT_PRIORITY);
                }
                continue;  // Keine weiteren Kombinationen für diesen festen Termin
            }

            // Für jeden Tag (normale Items)
            for (Map.Entry<todoList, List<LocalTime>> entry : startsByDay.entrySet()) {
                todoList list = entry.getKey();
                LocalDate day = list.date;

                // BlockedDays-Prüfung für ersten Task
                if (firstItem.isBlockedOn(day, repo)) continue;

                // Für jeden möglichen Startpunkt
                for (LocalTime startTime : entry.getValue()) {

                    // Für jede mögliche Chain-Länge (1 bis max)
                    for (int chainLen = 1; chainLen <= chain.tasks().size(); chainLen++) {
                        List<trackedItem> fitting = chain.tasks().subList(0, chainLen);

                        // Prüfe ob alle Items an diesem Tag nicht geblockt sind
                        boolean anyBlocked = false;
                        for (trackedItem item : fitting) {
                            if (item.isBlockedOn(day, repo)) {
                                anyBlocked = true;
                                break;
                            }
                        }
                        if (anyBlocked) break;  // Kürzere Längen auch nicht möglich

                        // Berechne Gesamtdauer (respektiert timeToSchedule als Obergrenze)
                        int totalDuration = 0;
                        for (trackedItem item : fitting) {
                            totalDuration += item.getSlotDuration();
                        }

                        LocalTime endTime = startTime.plusMinutes(totalDuration);

                        // Prüfe Tagesgrenzen
                        if (exceedsDayBounds(startTime, endTime, list)) continue;

                        // Prüfe Calendar-Event-Überlappung
                        if (overlapsCalendarEvent(startTime, endTime, list)) continue;

                        // Prüfe Predecessor-Delay Constraint für verzögerte Items (delay > 0)
                        if (firstItem.predecessorDelay > 0) {
                            java.time.LocalDateTime slotStart = java.time.LocalDateTime.of(day, startTime);
                            java.time.LocalDateTime earliestStart = firstItem.getEarliestStart(repo);
                            if (earliestStart != null && slotStart.isBefore(earliestStart)) continue;
                        }

                        // Finde alle überlappenden Slots
                        Set<TimeSlot> overlapping = findOverlappingSlots(startTime, endTime, list);

                        // Erweitere auf volle Chains (atomare Verdrängung)
                        Set<TimeSlot> toDisplace = expandToFullChains(overlapping, list);

                        // Berechne Chain-Score (Summe der Item-Scores)
                        int gainPrio = 0;
                        LocalTime cursor = startTime;
                        Long precedingItemId = findPrecedingItemAt(startTime, list);

                        for (int i = 0; i < fitting.size(); i++) {
                            trackedItem item = fitting.get(i);
                            // FollowUp-Boost nur für erstes Item
                            Long precId = (i == 0) ? precedingItemId : null;
                            gainPrio += calculateItemScore(item, cursor, precId);
                            cursor = cursor.plusMinutes(item.getSlotDuration());
                        }

                        // Bonus für längere Ketten
                        gainPrio += fitting.size() * CHAIN_LENGTH_BONUS_PER_ITEM;

                        // Berechne Verdrängungskosten
                        int lossPrio = sumAdjustedPrios(toDisplace);

                        // Net-Score
                        int netScore = gainPrio - lossPrio;

                        // Nur wenn Gewinn > Verlust
                        if (netScore > globalBestScore) {
                            globalBestScore = netScore;
                            globalBest = new ChainMatch(fitting, startTime, list, toDisplace, gainPrio);
                        }
                    }
                }
            }
        }

        return globalBest;
    }

    // ============================================================================
    // Hilfsmethoden für Multi-Längen-Evaluation
    // ============================================================================

    /**
     * Sammelt alle möglichen Startpunkte pro Tag.
     * Startpunkte sind: Beginn freier Fenster + Beginn belegter Slots (für Verdrängung).
     */
    private Map<todoList, List<LocalTime>> collectStartPointsByDay(List<SlotCandidate> slotList) {
        Map<todoList, Set<LocalTime>> startsByDay = new HashMap<>();

        for (SlotCandidate c : slotList) {
            startsByDay.computeIfAbsent(c.list(), k -> new HashSet<>()).add(c.slot().start);
        }

        // In sortierte Listen umwandeln
        Map<todoList, List<LocalTime>> result = new HashMap<>();
        for (Map.Entry<todoList, Set<LocalTime>> entry : startsByDay.entrySet()) {
            List<LocalTime> sorted = new ArrayList<>(entry.getValue());
            sorted.sort(LocalTime::compareTo);
            result.put(entry.getKey(), sorted);
        }
        return result;
    }

    /**
     * Findet alle Slots die mit dem Zeitfenster überlappen (gleicher Tag).
     */
    private Set<TimeSlot> findOverlappingSlots(LocalTime start, LocalTime end, todoList list) {
        Set<TimeSlot> overlapping = new HashSet<>();
        if (list.timeSlots == null) return overlapping;

        for (TimeSlot slot : list.timeSlots) {
            // Überlappung: slot.start < end && slot.end > start
            if (slot.start.isBefore(end) && slot.end.isAfter(start)) {
                // Nur Item-Slots, keine Calendar-Events
                if (slot.item != null && (slot.isCalendarEvent == null || !slot.isCalendarEvent)) {
                    overlapping.add(slot);
                }
            }
        }
        return overlapping;
    }

    /**
     * Erweitert überlappende Slots um alle Chain-Mitglieder (atomare Verdrängung).
     * Wenn ein Slot eine chainId hat, werden ALLE Slots mit dieser chainId hinzugefügt.
     */
    private Set<TimeSlot> expandToFullChains(Set<TimeSlot> overlapping, todoList list) {
        Set<TimeSlot> expanded = new HashSet<>(overlapping);
        Set<Long> chainIds = new HashSet<>();

        // Sammle alle chainIds
        for (TimeSlot slot : overlapping) {
            if (slot.chainId != null) {
                chainIds.add(slot.chainId);
            }
        }

        // Füge alle Slots mit diesen chainIds hinzu
        if (!chainIds.isEmpty() && list.timeSlots != null) {
            for (TimeSlot slot : list.timeSlots) {
                if (slot.chainId != null && chainIds.contains(slot.chainId)) {
                    expanded.add(slot);
                }
            }
        }

        return expanded;
    }

    /**
     * Berechnet die adjustedPrio für ein einzelnes Item basierend auf seiner Startzeit.
     * Berücksichtigt prefTime-Matching mit quadratischer Penalty.
     */
    private int calculateItemScore(trackedItem item, LocalTime itemStart, Long precedingItemId) {
        // Basis: log1p(itemPrio) * SCORE_SCALE_FACTOR
        int basePrio = item.priority.value;
        double score = Math.log1p(basePrio) * SCORE_SCALE_FACTOR;

        // PrefTime-Matching
        if (item.prefTime != null) {
            long diff = ChronoUnit.MINUTES.between(item.prefTime, itemStart);
            double normalizedDiff = (diff >= 0) ? 1.0
                    : Math.max(0.0, 1.0 + (diff / (double) PREF_TIME_WINDOW_MINUTES));
            score *= normalizedDiff * normalizedDiff;
        }

        // FollowUp-Boost (nur wenn precedingItemId gegeben)
        if (precedingItemId != null) {
            double followBoost = item.scoreFollow(precedingItemId);
            if (followBoost > 0) {
                score *= (1.0 + followBoost);
            }
        }

        return (int) score;
    }

    /**
     * Summiert die adjustedPrio aller Slots.
     */
    private int sumAdjustedPrios(Set<TimeSlot> slots) {
        int sum = 0;
        for (TimeSlot slot : slots) {
            if (slot.adjustedPrio != null) {
                sum += slot.adjustedPrio;
            }
        }
        return sum;
    }

    /**
     * Prüft ob ein Zeitfenster in Calendar-Events hineinragt.
     */
    private boolean overlapsCalendarEvent(LocalTime start, LocalTime end, todoList list) {
        if (list.timeSlots == null) return false;
        for (TimeSlot slot : list.timeSlots) {
            if (slot.isCalendarEvent != null && slot.isCalendarEvent) {
                if (slot.start.isBefore(end) && slot.end.isAfter(start)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Prüft ob ein Zeitfenster über das Tagesende hinausgeht.
     */
    private boolean exceedsDayBounds(LocalTime start, LocalTime end, todoList list) {
        return start.isBefore(list.start) || end.isAfter(list.end);
    }

    /**
     * Findet die Item-ID im Slot direkt vor einer gegebenen Startzeit (gleicher Tag).
     */
    private Long findPrecedingItemAt(LocalTime startTime, todoList list) {
        if (list.timeSlots == null) return null;

        Long precedingItem = null;
        LocalTime latestEndBefore = null;

        for (TimeSlot slot : list.timeSlots) {
            if (slot.item == null) continue;
            if (!slot.end.isBefore(startTime) && !slot.end.equals(startTime)) continue;

            if (latestEndBefore == null || slot.end.isAfter(latestEndBefore)) {
                latestEndBefore = slot.end;
                precedingItem = slot.item;
            }
        }

        return precedingItem;
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
    // fillSlots - Füllt Slots iterativ: baut Ketten, matcht und weist zu.
    //   Loopt bis keine Kette mehr platziert werden kann.
    // ============================================================================
    private void fillSlots(trackedItem.ItemType typ, Long parent, List<SlotCandidate> slotList) {
        while (true) {
            List<trackedItem> items = getItems(typ, parent);
            if (items.isEmpty()) break;

            // Gruppiere zu Ketten (ersetzt prioritize())
            List<TaskChain> chains = buildChains(items);
            if (chains.isEmpty()) break;

            // Finde besten Slot für höchstpriorisierte Kette (ersetzt tryMatch())
            // HINWEIS: parent-Parameter nicht mehr nötig, isBlockedOn() handled GOAL→PROJECT
            ChainMatch match = tryMatchChain(chains, slotList);
            if (match == null) break;

            // Platziere Kette (ersetzt assignSlot())
            assignChain(match, slotList);
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
    // assignChain - Platziert alle Items einer gematchten Kette nacheinander
    // ============================================================================
    /**
     * Platziert alle Items einer gematchten Kette an der berechneten Startzeit.
     * Verdrängt alle Slots in toDisplace atomar (inkl. Chain-Mitglieder).
     * Setzt chainId auf alle platzierten Slots für atomare Verdrängung in der Zukunft.
     */
    private void assignChain(ChainMatch match, List<SlotCandidate> slotList) {
        todoList list = match.targetList();

        // 1. Alle zu verdrängenden Slots atomar entfernen
        for (TimeSlot slot : match.toDisplace()) {
            unPlan(slot, list);
            // Aus slotList entfernen (als displaceable und als freier Slot)
            slotList.removeIf(c -> c.slot() == slot || c.displaceable() == slot);
        }

        // 2. Chain-ID bestimmen (ID des ersten Items, nur bei echten Ketten)
        Long chainId = (match.fittingTasks().size() > 1)
                       ? match.fittingTasks().get(0).id
                       : null;

        // 3. Items platzieren
        LocalTime cursor = match.startTime();
        List<TimeSlot> newSlots = new ArrayList<>();

        for (trackedItem item : match.fittingTasks()) {
            TimeSlot itemSlot = new TimeSlot();
            itemSlot.start = cursor;
            itemSlot.end = cursor.plusMinutes(item.getSlotDuration());
            itemSlot.item = item.id;
            itemSlot.chainId = chainId;
            itemSlot.adjustedPrio = match.adjustedPrio();
            itemSlot.completed = false;

            newSlots.add(itemSlot);

            // Wenn Goal → Tasks innerhalb platzieren
            if (item.type == trackedItem.ItemType.GOAL) {
                fillGoalTasks(itemSlot, list);
            }

            // Item als scheduled markieren
            item.schedule(list.date, repo);

            cursor = itemSlot.end;
        }

        // 4. Alle neuen Slots zur todoList hinzufügen (sortiert)
        if (list.timeSlots == null) list.timeSlots = new ArrayList<>();
        for (TimeSlot newSlot : newSlots) {
            int idx = 0;
            for (int i = 0; i < list.timeSlots.size(); i++) {
                if (list.timeSlots.get(i).start.isAfter(newSlot.start)) break;
                idx = i + 1;
            }
            list.timeSlots.add(idx, newSlot);

            // SlotList aktualisieren (als belegt markieren)
            slotList.add(new SlotCandidate(newSlot, list, newSlot));
        }

        // 5. Budget als "committed" markieren für diesen Scheduling-Durchlauf
        for (trackedItem item : match.fittingTasks()) {
            if (item.budgetRequirementCents > 0) {
                committedBudgetCents += item.budgetRequirementCents;
            }
        }

        // 6. slotList neu aufbauen für diesen Tag (freie Fenster aktualisieren)
        regenerateFreeSlots(list, slotList);
    }

    /**
     * Aktualisiert die freien Slot-Fenster für eine todoList in der slotList.
     * Entfernt alte freie Slots für diese Liste und fügt neue basierend auf findFreeWindows() hinzu.
     */
    private void regenerateFreeSlots(todoList list, List<SlotCandidate> slotList) {
        // Alte freie Slots für diese Liste entfernen
        slotList.removeIf(c -> c.list() == list && c.displaceable() == null);

        // Neue freie Fenster hinzufügen
        List<LocalTime[]> freeWindows = findFreeWindows(list);
        for (LocalTime[] window : freeWindows) {
            TimeSlot freeSlot = new TimeSlot();
            freeSlot.start = window[0];
            freeSlot.end = window[1];
            slotList.add(new SlotCandidate(freeSlot, list, null));
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
}
