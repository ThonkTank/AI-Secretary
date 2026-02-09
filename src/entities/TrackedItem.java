package entities;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import repository.Repo;
import repository.Table;

/**
 * Task Entity - repräsentiert eine einzelne Aufgabe.
 *
 * Utility Funktionen:
 * getBlockedDays(): Für alle completions und scheduled Termine cooldown berechnen und Tage blockieren. Wenn complete, Tage bis next repetition blockieren.
 * calcNextRepetition(): Berechnet Beginn der nächsten Periode.
 * update(): Wird einmal pro Tag für ALLE Items aufgerufen. Slot-Items bekommen Completion-Daten,
 *           alle Items bekommen Perioden-Reset, scheduled-Bereinigung und blockedDays-Refresh.
 *
 */
public class TrackedItem {

    // Basic Fields
    public ItemType type;                   // Task, Goal, Block oder Project?
    public Long id;                         // Wird zum referenzieren des Items verwendet
    public String title;                    // Human readable reference
    public String description;              // What's this about?
    public Long parent;                     // ID des übergeordneten Items (null = kein Parent)
    public List<Long> children;             // Trackt, falls vorhanden, child IDs für goals und metaGoals
    public LocalDate created;               // Wann das Item erstellt wurde.
    public LocalDate deadline;               // Fälligkeitsdatum für einmalige Tasks (null = keine Deadline)
    public LocalDate fixedDate;              // Fester Termin: Muss an diesem Tag geplant werden (null = flexibel)
    public LocalTime fixedTime;              // Fester Termin: Muss zu dieser Uhrzeit starten (null = flexibel)

    // Completion Logik
    public LocalDate lastCompletion;        // when was the task last completed?
    public int completions;                 // How often hast this task been completed (used to track tasks with multiple completion requirements per timeframe like 3x per week. Resets on repetition)
    public Boolean isCompleted;             // Wurde die Aufgabe erledigt?

    // Info für Planung
    public Repetition repetition;           // Definiert Wiederholungslogik (Typ, Intervall, Einheit)
    public Boolean completeFirst;           // Should we wait for completion before repetition or skip if unfulfilled and repeat on schedule?
    public int minDurationValue;            // Min-Wert pro Tag (0 = kein Minimum)
    public DurationUnit minDurationUnit;    // In Minuten oder Fortschritts-Einheiten
    public int maxDurationValue;            // Max-Wert pro Tag (0 = kein Maximum)
    public DurationUnit maxDurationUnit;    // In Minuten oder Fortschritts-Einheiten
    public Priority priority;               // Wie wichtig ist diese Task? Beeinflusst Planung.
    public List<LocalDate> scheduled;       // Alle Daten, an denen das Item momentan eingeplant ist.
    public int cooldown;                    // Wenn die Task für eine bestimmte Zeit nicht wiederholt werden soll nach der letzten completion.
    public Set<LocalDate> blockedDays;      // Liste aller von Cooldown oder Completion blockierten Tage.

    // Fortschritt (Zähler)
    public int progressCurrent;             // Aktueller Fortschritt (z.B. 3)
    public int progressTarget;              // Zielwert (z.B. 6), 0 = kein Tracking
    public String progressUnit;             // Einheit (z.B. "Seiten", "Kapitel")
    public boolean progressPerRep;          // True = Progress resets jede Periode
    public int progressLastPeriod;          // Letzter Progress-Wert (für Anzeige)
    public int timePerProgressUnit;         // Durchschnitt Minuten pro Progress-Einheit (0 = nicht gemessen)
    public int progressTimingCount;         // Anzahl Messungen fuer den Durchschnitt

    // History
    public int currentStreak;               // wie oft hintereinander abgeschloßen ohne Verfehlung?
    public int averageStreak;               // durchschnittliche Streak Länge
    public int nrOfStreaks;                 // menge der beendeten Streaks
    public Map<Long, Integer> followUps;    // Tasks, welche direkt vor dieser erledigt wurden (ID und Häufigkeit)
    public List<PrefSlot> prefSlots;        // Bevorzugte Tage + Zeiten (dayKey kontextabhaengig)
    public int totalCompletions;            // Wie oft wurde die Task erledigt? (all time)

    /** Bevorzugter Tag + Uhrzeit. dayKey = DayOfWeek.getValue() (1-7) oder dayOfMonth (1-31). */
    public static record PrefSlot(int dayKey, LocalTime time, int completionCount) {
        /** Wochentag-Slot (dayKey 1-7), completionCount=0. */
        public static PrefSlot weekly(DayOfWeek day, LocalTime time) {
            return new PrefSlot(day.getValue(), time, 0);
        }
        /** Monatstag-Slot (dayKey 1-31), completionCount=0. */
        public static PrefSlot monthly(int dayOfMonth, LocalTime time) {
            if (dayOfMonth < 1 || dayOfMonth > 31)
                throw new IllegalArgumentException("dayOfMonth: " + dayOfMonth);
            return new PrefSlot(dayOfMonth, time, 0);
        }
        /** Validierter Slot mit Completion-Count (fuer Parser/Deserialisierung). */
        public static PrefSlot of(int dayKey, LocalTime time, int completionCount, boolean monthly) {
            if (!TrackedItem.isValidDayKey(dayKey, monthly))
                throw new IllegalArgumentException("dayKey: " + dayKey + " (monthly=" + monthly + ")");
            return new PrefSlot(dayKey, time, completionCount);
        }
    }

    // Darstellung (nur fuer Goals relevant)
    public String goalIcon;                 // Emoji-Icon fuer Goal-Header (z.B. "💪")
    public String goalColor;                // Hex-Farbcode fuer Goal-Header (z.B. "#FFE53935")

    // Budget-Anforderung (nur fuer Tasks relevant)
    public int budgetRequirementCents;      // 0 = kein Budget, >0 = Kosten in Cents
    public Long budgetAccountId;            // null = beliebiges Konto, sonst spezifisches
    public Long budgetCategoryId;           // FK zu categories für die Auto-Transaction

    // Meal-Task-Verknüpfung
    public Long mealPlanId;                 // FK zu MealPlan (null wenn kein Meal-Task)

    // Unified Chaining (ersetzt requiredPredecessor + isSequence + sequenceDelay)
    public Long predecessor;                // Vorgänger-Task (delay=0: konsekutiv, delay>0: verzögert)
    public int predecessorDelay;            // Minuten Wartezeit nach Vorgänger (0 = konsekutiv am selben Tag)
    public LocalTime lastCompletionTime;    // Uhrzeit der Completion (für präzise Delays)

    // Builder
    public static class Builder {
        private final TrackedItem item = new TrackedItem();

        public Builder(ItemType type, String title, Priority priority) {
            item.type = type;
            item.title = title;
            item.priority = priority;
        }

        public Builder description(String v) { item.description = v; return this; }
        public Builder parent(Long v) { item.parent = v; return this; }
        public Builder children(List<Long> v) { item.children = v; return this; }
        public Builder created(String v) { item.created = LocalDate.parse(v); return this; }
        public Builder deadline(String v) { item.deadline = LocalDate.parse(v); return this; }
        public Builder lastCompletion(String v) { item.lastCompletion = LocalDate.parse(v); return this; }
        public Builder completions(int v) { item.completions = v; return this; }
        public Builder isCompleted(boolean v) { item.isCompleted = v; return this; }
        public Builder completeFirst(boolean v) { item.completeFirst = v; return this; }
        public Builder minDuration(int value, DurationUnit unit) {
            item.minDurationValue = value;
            item.minDurationUnit = unit;
            return this;
        }
        public Builder maxDuration(int value, DurationUnit unit) {
            item.maxDurationValue = value;
            item.maxDurationUnit = unit;
            return this;
        }
        // Convenience für Zeit-basiert:
        public Builder minMinutes(int v) { return minDuration(v, DurationUnit.MINUTES); }
        public Builder maxMinutes(int v) { return maxDuration(v, DurationUnit.MINUTES); }
        // Convenience für Fortschritt-basiert:
        public Builder minProgress(int v) { return minDuration(v, DurationUnit.PROGRESS_UNITS); }
        public Builder maxProgress(int v) { return maxDuration(v, DurationUnit.PROGRESS_UNITS); }
        public Builder scheduled(List<LocalDate> v) { item.scheduled = v; return this; }
        public Builder cooldown(int v) { item.cooldown = v; return this; }
        public Builder prefSlots(List<PrefSlot> v) { item.prefSlots = v; return this; }
        public Builder currentStreak(int v) { item.currentStreak = v; return this; }
        public Builder averageStreak(int v) { item.averageStreak = v; return this; }
        public Builder nrOfStreaks(int v) { item.nrOfStreaks = v; return this; }
        public Builder totalCompletions(int v) { item.totalCompletions = v; return this; }
        public Builder followUps(Map<Long, Integer> v) { item.followUps = v; return this; }
        public Builder progressCurrent(int v) { item.progressCurrent = v; return this; }
        public Builder progressTarget(int v) { item.progressTarget = v; return this; }
        public Builder progressUnit(String v) { item.progressUnit = v; return this; }
        public Builder progressPerRep(boolean v) { item.progressPerRep = v; return this; }
        public Builder timePerProgressUnit(int v) { item.timePerProgressUnit = v; return this; }
        public Builder progressTimingCount(int v) { item.progressTimingCount = v; return this; }
        public Builder goalIcon(String v) { item.goalIcon = v; return this; }
        public Builder goalColor(String v) { item.goalColor = v; return this; }
        public Builder budgetRequirement(int cents) { item.budgetRequirementCents = cents; return this; }
        public Builder budgetAccount(Long id) { item.budgetAccountId = id; return this; }
        public Builder budgetCategory(Long categoryId) { item.budgetCategoryId = categoryId; return this; }
        public Builder mealPlan(Long id) { item.mealPlanId = id; return this; }
        public Builder predecessor(Long v) { item.predecessor = v; return this; }
        public Builder predecessorDelay(int v) { item.predecessorDelay = v; return this; }
        // Convenience: Same-day chain (delay=0)
        public Builder chainAfter(Long v) { return predecessor(v).predecessorDelay(0); }
        // Convenience: Delayed chain
        public Builder delayAfter(Long v, int minutes) { return predecessor(v).predecessorDelay(minutes); }
        // Fester Termin
        public Builder fixedDate(String v) { item.fixedDate = LocalDate.parse(v); return this; }
        public Builder fixedTime(String v) { item.fixedTime = LocalTime.parse(v); return this; }
        public Builder fixedAppointment(String date, String time) {
            item.fixedDate = LocalDate.parse(date);
            item.fixedTime = LocalTime.parse(time);
            return this;
        }

        public Builder repetition(RepetitionType type, int value, RepUnits unit) {
            item.repetition = new Repetition();
            item.repetition.type = type;
            item.repetition.value = value;
            item.repetition.unit = unit;
            return this;
        }

        public Builder repetition(RepetitionType type, int value, RepUnits unit, DayOfWeek dow) {
            repetition(type, value, unit);
            item.repetition.dayOfWeek = dow;
            return this;
        }

        public Builder noRepetition() {
            item.repetition = new Repetition();
            item.repetition.type = RepetitionType.NONE;
            item.repetition.unit = RepUnits.DAY;
            item.repetition.value = 0;
            return this;
        }

        public TrackedItem build() { return item; }
    }

    //Subklassen
    public static class Repetition {
        public RepetitionType type;
        public RepUnits unit;
        public int value;              // interval (INTERVAL), requiredCompletions (REPS_PER_TIME), dayOfMonth (DAY_OF_TIME/MONTH)
        public DayOfWeek dayOfWeek;    // nur für DAY_OF_TIME/WEEK
    }
    
    public static enum ItemType {
        TASK, GOAL, PROJECT
    }

    public static enum RepetitionType {
        NONE,           // Einmalig, keine Wiederholung
        DAY_OF_TIME,    // "jeden Freitag" oder "jeden 10."
        REPS_PER_TIME,  // "3x pro Woche"
        INTERVAL        // "alle 2 Wochen"
    }

    public static enum RepUnits {
        DAY(1), 
        WEEK(7), 
        MONTH(30);

        public final int value;
        
        RepUnits(int value) {
            this.value = value;
        }
    }

    public static enum Priority {
        LOW(100),            // Kann bei Bedarf übersprungen werden, wenn kein Platz ist
        MODERATE(200),       // Keine besondere Logik
        HIGH(400),           // Wird bevorzugt
        CRITICAL(100000);        // Muss baldmöglichst eingeplant werden

        public final int value;

        Priority(int value) {
            this.value = value;
        }
    }

    public static enum DurationUnit {
        MINUTES,          // Dauer in Minuten
        PROGRESS_UNITS    // Dauer in Fortschritts-Einheiten (z.B. Seiten, Kapitel)
    }


    // Zeitraum, in dem eine Task bearbeitet werden sollte.
    public static class Timeframe {
        public LocalDate start;
        public LocalDate end;    // null = nur ein Datum
        
        // Konstruktoren für beide Fälle
        public Timeframe(LocalDate exactDate) {
            this.start = exactDate;
            this.end = null;
        }
        
        public Timeframe(LocalDate start, LocalDate end) {
            this.start = start;
            this.end = end;
        }
        
        // Timeframe liegt VOR dem Datum
        public boolean isBefore(LocalDate date) {
            LocalDate reference = (end != null) ? end : start;
            return reference.isBefore(date);
        }

        // Timeframe liegt NACH dem Datum  
        public boolean isAfter(LocalDate date) {
            return start.isAfter(date);
        }

        // Datum liegt im Timeframe
        public boolean contains(LocalDate date) {
            if (end == null) return date.isEqual(start);
            return !date.isBefore(start) && !date.isAfter(end);
        }
    }

    // ============== UTILITY ==============

    /**
     * Prüft ob dieses Item an einem bestimmten Tag blockiert ist.
     * Berücksichtigt eigene blockedDays UND (für Goals) die blockedDays des Projekt-Parents.
     */
    public boolean isBlockedOn(LocalDate day, Repo repo) {
        if (this.blockedDays != null && this.blockedDays.contains(day)) return true;
        if (this.type == ItemType.GOAL && this.parent != null) {
            TrackedItem parentItem = repo.fetch(Table.ITEMS, this.parent);
            if (parentItem != null && parentItem.type == ItemType.PROJECT
                && parentItem.blockedDays != null && parentItem.blockedDays.contains(day)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Prüft ob dieses Item ein fester Termin ist (fixedDate + fixedTime gesetzt).
     * Feste Termine werden exakt zur angegebenen Zeit eingeplant und können nicht verdrängt werden.
     */
    public boolean isFixedAppointment() {
        return fixedDate != null && fixedTime != null;
    }

    /** Ob Praeferenz-Slots gesetzt sind. */
    public boolean hasPrefSlots() {
        return prefSlots != null && !prefSlots.isEmpty();
    }

    /**
     * Bestimmt ob ein Wiederholungs-Modus monatliche Tage (1-31) statt Wochentage (1-7) nutzt.
     * Nur REPS_PER_TIME und DAY_OF_TIME mit MONTH-Unit verwenden Monatstage.
     * INTERVAL + MONTH (z.B. "alle 2 Monate") nutzt weiterhin Wochentage.
     */
    public static boolean isMonthlyDayMode(RepetitionType type, RepUnits unit) {
        return unit == RepUnits.MONTH
            && (type == RepetitionType.REPS_PER_TIME || type == RepetitionType.DAY_OF_TIME);
    }

    /** Ob dieses Item monatliche Tage (1-31) statt Wochentage (1-7) fuer PrefSlots nutzt. */
    public boolean isMonthlyRepetition() {
        if (repetition == null) return false;
        return isMonthlyDayMode(repetition.type, repetition.unit);
    }

    /** Validiert dayKey-Range. Weekly: 1-7, Monthly: 1-31. */
    public static boolean isValidDayKey(int dayKey, boolean monthly) {
        if (monthly) return dayKey >= 1 && dayKey <= 31;
        return dayKey >= 1 && dayKey <= 7;
    }

    /**
     * Gibt den PrefSlot-Key fuer ein Datum zurueck.
     * Monatliche Items: dayOfMonth (1-31). Sonst: dayOfWeek (1-7).
     */
    public int getPrefDayKey(LocalDate date) {
        return isMonthlyRepetition() ? date.getDayOfMonth() : date.getDayOfWeek().getValue();
    }

    /** PrefSlot fuer einen dayKey (oder null). */
    public PrefSlot getPrefSlotForKey(int dayKey) {
        if (prefSlots == null) return null;
        for (PrefSlot s : prefSlots) {
            if (s.dayKey() == dayKey) return s;
        }
        return null;
    }

    /** PrefSlot fuer ein Datum (oder null). */
    public PrefSlot getPrefSlotForDate(LocalDate date) {
        return getPrefSlotForKey(getPrefDayKey(date));
    }

    /** Convenience: PrefSlot fuer einen Wochentag. */
    public PrefSlot getPrefSlotForDay(DayOfWeek day) {
        return getPrefSlotForKey(day.getValue());
    }

    /**
     * Tagesgewichtung: Anteil der Completions an diesem dayKey relativ zu allen Slots.
     * Gibt 0.0-1.0 zurueck. Bei gleicher Verteilung ≈ 1/N.
     */
    public double getDayKeyWeight(int dayKey) {
        if (prefSlots == null || prefSlots.isEmpty()) return 0.0;
        int total = 0;
        int dayCount = 0;
        for (PrefSlot s : prefSlots) {
            total += s.completionCount();
            if (s.dayKey() == dayKey) dayCount = s.completionCount();
        }
        if (total == 0) return 1.0 / prefSlots.size();
        return (double) dayCount / total;
    }

    /** Tagesgewichtung fuer ein Datum. */
    public double getDayKeyWeightForDate(LocalDate date) {
        return getDayKeyWeight(getPrefDayKey(date));
    }

    /** Convenience: Tagesgewichtung fuer Wochentag. */
    public double getDayWeight(DayOfWeek day) {
        return getDayKeyWeight(day.getValue());
    }

    /**
     * Prüft ob dieses Item basierend auf Predecessor-Status bereit zur Planung ist.
     * - Kein predecessor: immer ready
     * - delay=0: ready (buildChains() behandelt konsekutive Platzierung)
     * - delay>0: ready wenn predecessor scheduled ODER completed
     */
    public boolean isPredecessorReady(Repo repo) {
        if (this.predecessor == null) return true;

        // delay=0 items werden von buildChains() gruppiert
        if (this.predecessorDelay == 0) return true;

        TrackedItem pred = repo.fetch(Table.ITEMS, this.predecessor);
        if (pred == null) return true;

        // Ready wenn Vorgänger scheduled ODER completed
        boolean predScheduled = pred.scheduled != null && !pred.scheduled.isEmpty();
        boolean predCompleted = pred.lastCompletion != null;

        return predScheduled || predCompleted;
    }

    /**
     * Berechnet den frühesten Startzeitpunkt basierend auf Predecessor + Delay.
     * @return LocalDateTime oder null wenn kein Constraint
     */
    public java.time.LocalDateTime getEarliestStart(Repo repo) {
        if (this.predecessor == null || this.predecessorDelay <= 0) return null;

        TrackedItem pred = repo.fetch(Table.ITEMS, this.predecessor);
        if (pred == null) return null;

        // Referenzpunkt: Completion oder erstes Scheduled-Datum
        LocalDate refDate = null;
        LocalTime refTime = LocalTime.of(8, 0);  // Default 08:00

        if (pred.lastCompletion != null) {
            refDate = pred.lastCompletion;
            refTime = pred.lastCompletionTime != null ? pred.lastCompletionTime : refTime;
        } else if (pred.scheduled != null && !pred.scheduled.isEmpty()) {
            refDate = pred.scheduled.get(0);  // Erstes geplantes Datum
        }

        if (refDate == null) return null;

        return java.time.LocalDateTime.of(refDate, refTime).plusMinutes(this.predecessorDelay);
    }

    // ============== BUSINESS LOGIK ==============

    // ============================================================================
    // UPDATE - Wird von cleanToDo für ALLE Items aufgerufen (täglich um 00:00).
    // ============================================================================
    /**
     * Zentraler Entry-Point für Tagesabschluss-Logik.
     * Wird von cleanToDo aufgerufen — für Slot-Items MIT Slot-Daten,
     * für alle übrigen Items mit completed=null (nur "immer"-Updates).
     *
     * Slot-basierte Updates (wenn completed != null):
     *   - Completion(s), prefSlot, timePerProgressUnit, progress, streak, followUps
     *
     * "Immer"-Updates (für alle Items):
     *   - isCompleted: Perioden-Reset für wiederkehrende Items
     *   - scheduled: vergangene Daten entfernen
     *   - blockedDays: neu berechnen
     *
     * @param completed       true=erledigt, false=nicht erledigt, null=kein Slot (nur Refresh)
     * @param workStart       Timer-Start (null wenn Timer nicht genutzt)
     * @param workEnd         Timer-Ende (null wenn Timer nicht genutzt)
     * @param previousItemId  ID des vorher erledigten Items für followUp-Tracking (null wenn keins)
     * @param progressDelta   Fortschrittsänderung aus dem Slot (+/- vom UI, null wenn keine)
     * @param day             Referenztag (Slot-Tag oder heute)
     * @param repo            Zum Persistieren der Änderungen
     */
    public void update(Boolean completed, LocalTime workStart, LocalTime workEnd,
                       Long previousItemId, Integer progressDelta, LocalDate day, Repo repo) {

        // ========== Slot-basierte Updates (nur wenn Slot-Daten vorhanden) ==========
        if (completed != null && completed) {
            // Progress aus progressDelta anwenden (statt auto-increment)
            // WICHTIG: progressDelta kommt aus dem Slot und wurde dort während des Tages gesammelt
            if (progressDelta != null && progressDelta > 0 && this.progressTarget > 0) {
                this.progressCurrent = Math.min(
                    this.progressCurrent + progressDelta,
                    this.progressTarget
                );
            }

            // Completions inkrementieren (vor checkCompletion, damit der Zähler stimmt)
            this.completions++;

            // Completion tracking (setzt isCompleted, lastCompletion, streak)
            checkCompletion(day);

            // lastCompletionTime für präzise Sequence-Delays
            this.lastCompletionTime = workStart != null ? workStart : LocalTime.now();

            if (this.type == ItemType.TASK) {
                // PrefSlot aus Tag + workStart ableiten (Wochentag oder Monatstag je nach RepType)
                updatePrefSlot(day, workStart != null ? workStart : LocalTime.now());

                // TimeToComplete: aus Timer-Daten ableiten (laufender Durchschnitt pro Einheit)
                if (workStart != null && workEnd != null) {
                    int actualMinutes = (int) java.time.temporal.ChronoUnit.MINUTES.between(workStart, workEnd);
                    if (actualMinutes > 0) {
                        updateTimePerUnit(actualMinutes, progressDelta);
                    }
                }
            }

            // followUps tracken
            if (previousItemId != null) {
                if (this.followUps == null) this.followUps = new java.util.LinkedHashMap<>();
                this.followUps.merge(previousItemId, 1, Integer::sum);
            }

        } else if (completed != null && !completed) {
            // Nicht erledigt und überfällig → Streak reset wenn !completeFirst
            if ((this.completeFirst == null || !this.completeFirst) && overdue(day) > 0) {
                resetStreak();
                this.completions = 0;
            }
        }

        // ========== "Immer" Updates (für alle Items) ==========
        LocalDate today = LocalDate.now();

        // isCompleted prüfen: Perioden-Reset für wiederkehrende Items
        // SKIP reset wenn completeFirst=true und Item überfällig ist
        if (this.isCompleted != null && this.isCompleted
                && this.repetition != null
                && this.repetition.type != RepetitionType.NONE
                && this.lastCompletion != null
                && !isCompleteFirstBlocked(today)) {
            LocalDate nextRep = calcNextRepetition(this.lastCompletion);
            if (nextRep != null && !today.isBefore(nextRep)) {
                this.isCompleted = false;
                this.completions = 0;

                // Progress-Reset für ALLE wiederkehrenden Items mit Progress-Tracking
                if (this.progressTarget > 0) {
                    this.progressLastPeriod = this.progressCurrent;
                    this.progressCurrent = 0;
                }
            }
        }

        // scheduled bereinigen: vergangene Daten entfernen
        if (this.scheduled != null) {
            this.scheduled.removeIf(d -> d.isBefore(today));
        }

        // blockedDays neu berechnen
        this.blockedDays = getBlockedDays();

        repo.write(this);
    }

    /**
     * Aktualisiert den bevorzugten Tag + Uhrzeit (laufender Durchschnitt).
     * Nutzt getPrefDayKey() fuer kontextabhaengigen Key (Wochentag oder Monatstag).
     */
    private void updatePrefSlot(LocalDate day, LocalTime workStart) {
        if (this.prefSlots == null) this.prefSlots = new ArrayList<>();
        boolean monthly = isMonthlyRepetition();
        int key = getPrefDayKey(day);
        if (!isValidDayKey(key, monthly)) return;
        PrefSlot existing = getPrefSlotForKey(key);
        if (existing != null) {
            int newCount = existing.completionCount() + 1;
            int avgMinutes = (existing.time().toSecondOfDay() / 60 * existing.completionCount()
                              + workStart.toSecondOfDay() / 60) / newCount;
            this.prefSlots.set(this.prefSlots.indexOf(existing),
                PrefSlot.of(key, LocalTime.of(avgMinutes / 60, avgMinutes % 60), newCount, monthly));
        } else {
            this.prefSlots.add(PrefSlot.of(key, workStart, 1, monthly));
        }
        this.totalCompletions++;
    }

    /**
     * Aktualisiert timePerProgressUnit (laufender Durchschnitt).
     *
     * Einheitliche Logik: Tasks ohne Progress-Tracking = 1 Einheit pro Completion.
     * Mit Progress-Tracking: progressDelta Einheiten pro Completion.
     */
    private void updateTimePerUnit(int actualMinutes, Integer progressDelta) {
        // Einheiten bestimmen: explizites Progress oder implizit 1 pro Completion
        int units = (this.progressTarget > 0 && progressDelta != null && progressDelta > 0)
            ? progressDelta
            : 1;  // Tasks ohne Progress-Tracking = 1 Einheit pro Completion

        int minutesPerUnit = actualMinutes / units;

        if (this.progressTimingCount == 0) {
            this.timePerProgressUnit = minutesPerUnit;
            this.progressTimingCount = 1;
        } else {
            this.timePerProgressUnit =
                (this.timePerProgressUnit * this.progressTimingCount + minutesPerUnit)
                / (this.progressTimingCount + 1);
            this.progressTimingCount++;
        }
    }

    /**
     * Berechnet geschaetzte Zeit fuer vollstaendige Erledigung.
     * - Mit Progress-Tracking: timePerProgressUnit × verbleibende Einheiten
     * - Ohne Progress-Tracking: timePerProgressUnit × 1
     * @return 0 wenn keine Messung vorhanden
     */
    public int getEstimatedTime() {
        if (this.timePerProgressUnit <= 0) {
            return 0;  // Kein Schaetzwert vorhanden
        }
        if (this.progressTarget > 0) {
            int remainingUnits = this.progressTarget - this.progressCurrent;
            return this.timePerProgressUnit * Math.max(1, remainingUnits);
        }
        // Ohne Progress = 1 Einheit pro Completion
        return this.timePerProgressUnit;
    }

    /**
     * Berechnet Minimum in Minuten.
     * Bei PROGRESS_UNITS: Wert × timePerProgressUnit
     */
    public int getMinDurationMinutes() {
        if (minDurationValue <= 0) return 0;
        if (minDurationUnit == DurationUnit.PROGRESS_UNITS) {
            return minDurationValue * Math.max(1, timePerProgressUnit);
        }
        return minDurationValue;
    }

    /**
     * Berechnet Maximum in Minuten.
     * Bei PROGRESS_UNITS: Wert × timePerProgressUnit
     */
    public int getMaxDurationMinutes() {
        if (maxDurationValue <= 0) return Integer.MAX_VALUE;
        if (maxDurationUnit == DurationUnit.PROGRESS_UNITS) {
            return maxDurationValue * Math.max(1, timePerProgressUnit);
        }
        return maxDurationValue;
    }

    /**
     * Berechnet die Slot-Dauer fuer Einplanung.
     * Clamps estimated auf [min, max].
     */
    public int getSlotDuration() {
        int estimated = getEstimatedTime();
        int min = getMinDurationMinutes();
        int max = getMaxDurationMinutes();

        // Ohne Schaetzung: Minimum oder Max als Fallback
        if (estimated <= 0) {
            if (min > 0) return min;
            return Math.min(max, 30); // 30 min Default
        }

        // Clamp zu [min, max]
        if (estimated < min) return min;
        if (estimated > max) return max;
        return estimated;
    }

    /**
     * Berechnet erwarteten Fortschritt fuer gegebene Zeit.
     * @return Anzahl Einheiten (oder 1 wenn kein Progress-Tracking)
     */
    public int expectedProgress(int availableMinutes) {
        if (this.timePerProgressUnit > 0 && this.progressTarget > 0) {
            return availableMinutes / this.timePerProgressUnit;
        }
        return 1;  // Ohne Progress = 1 Completion
    }

    /**
     * Prüft ob genug Completions für die aktuelle Periode erreicht sind.
     */
    public boolean checkCompletion(LocalDate day) {
        if (this.repetition == null) {
            return this.lastCompletion != null;
        }

        // Einmalige Aufgabe: sofort als erledigt markieren
        if (this.repetition.type == RepetitionType.NONE) {
            this.isCompleted = true;
            this.lastCompletion = day;
            this.currentStreak += 1;
            return true;
        }

        int required = 1;
        if (this.repetition.type == RepetitionType.REPS_PER_TIME) {
            required = this.repetition.value;
        }

        if (this.completions >= required) {
            this.isCompleted = true;
            this.completions = 0;
            this.lastCompletion = day;
            this.currentStreak += 1;

            // Offener Progress hält Item aktiv (nicht als completed markieren)
            if (this.progressTarget > 0 && this.progressCurrent < this.progressTarget) {
                this.isCompleted = false;
            }

            return true;
        }

        return false;
    }

    private void resetStreak() {
        this.nrOfStreaks += 1;
        this.averageStreak = ((this.averageStreak * (this.nrOfStreaks - 1)) + this.currentStreak) / this.nrOfStreaks;
        this.currentStreak = 0;
    }

    /**
     * Prüft ob dieses Item wegen completeFirst blockiert ist.
     * Gibt true zurück wenn: completeFirst=true UND überfällig UND nicht erledigt.
     * In diesem Zustand wird das Item behandelt als wäre es noch in der vorherigen Periode.
     */
    private boolean isCompleteFirstBlocked(LocalDate day) {
        if (this.completeFirst == null || !this.completeFirst) {
            return false;
        }
        if (overdue(day) <= 0) {
            return false;
        }
        if (this.isCompleted != null && this.isCompleted) {
            return false;
        }
        return true;
    }

    public Set<LocalDate> getBlockedDays() {
    // 1. Cooldown nach lastCompletion und scheduled
    Set<LocalDate> blockedDays = new HashSet<>();
     
    // Sammle alle Start-Daten für Blockierungen
    List<LocalDate> blockedStarts = new ArrayList<>();
     
    if (this.lastCompletion != null) {
        blockedStarts.add(this.lastCompletion);
    }
     
    if (this.scheduled != null) {
        blockedStarts.addAll(this.scheduled);
    }
    
    // Für jeden Start: Cooldown-Fenster VOR und NACH dem Termin blockieren
    for (LocalDate start : blockedStarts) {
        for (int i = -this.cooldown; i <= this.cooldown; i++) {
            blockedDays.add(start.plusDays(i));
        }
    }        
    
    return blockedDays;
    }

    // ============================================================================
    // CALCNEXTREPETITION - Berechnet das nächste Wiederholungsdatum basierend auf dem Repetition-Typ.
    // ============================================================================
    /**
     * @return LocalDate für die nächste Wiederholung
     */
    private LocalDate calcNextRepetition(LocalDate day) {

        if (this.repetition == null) {
            return null;  // Einmalige Aufgabe
        }

        Repetition rep = this.repetition;
        return switch (rep.type) {
            case NONE -> null;
            case DAY_OF_TIME -> {
                if (rep.unit == RepUnits.WEEK) {
                    yield day.with(TemporalAdjusters.next(rep.dayOfWeek));
                } else {
                    LocalDate thisMonth = day.withDayOfMonth(Math.min(rep.value, day.lengthOfMonth()));
                    if (thisMonth.isAfter(day)) {
                        yield thisMonth;
                    } else {
                        yield day.plusMonths(1).withDayOfMonth(
                            Math.min(rep.value, day.plusMonths(1).lengthOfMonth()));
                    }
                }
            }
            case REPS_PER_TIME -> {
                if (rep.unit == RepUnits.WEEK) {
                    yield day.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
                } else {
                    yield day.plusMonths(1).withDayOfMonth(1);
                }
            }
            case INTERVAL -> {
                yield switch (rep.unit) {
                    case DAY -> day.plusDays(rep.value);
                    case WEEK -> day.plusWeeks(rep.value);
                    case MONTH -> day.plusMonths(rep.value);
                };
            }
        };
    }

    // ============================================================================
    // SCHEDULE - Updated die Task in der Datenbank mit neuem scheduled date
    // ============================================================================
    /**
     * Updated schedule mit neuem datum.
     * Nutzt SQLrepo.write zum schreiben.
     * @param day
     */
    public void schedule(LocalDate day, Repo repo) {
        if (this.scheduled == null) {
            this.scheduled = new ArrayList<>();
        }
        if (!this.scheduled.contains(day)) {
            this.scheduled.add(day);
        }
        this.blockedDays = getBlockedDays();
        repo.write(this);

        // Falls Goal mit Parent, auch Parent's scheduled + blockedDays updaten
        if (this.type == ItemType.GOAL && this.parent != null) {
            TrackedItem parentItem = repo.fetch(Table.ITEMS, this.parent);
            if (parentItem != null) {
                if (parentItem.scheduled == null) {
                    parentItem.scheduled = new ArrayList<>();
                }
                if (!parentItem.scheduled.contains(day)) {
                    parentItem.scheduled.add(day);
                    parentItem.blockedDays = parentItem.getBlockedDays();
                    repo.write(parentItem);
                }
            }
        }
    }

    // ============================================================================
    // FREQUENCY - Berechnet die Wiederholungsfrequenz in Tagen.
    // ============================================================================
    /**
     * Wandelt den repetitionType zu einer einheitlichen Tagesanzahl um.
     *
     * @return Frequenz in Tagen (wie oft sollte die Task pro X Tage erledigt werden)
     *         0 wenn keine Repetition definiert ist
     */
    public int frequency() {
        if (this.repetition == null) {
            return 0;  // Einmalige Aufgabe
        }

        Repetition rep = this.repetition;
        return switch (rep.type) {
            case NONE -> 0;
            case INTERVAL -> rep.value * rep.unit.value;
            case REPS_PER_TIME -> rep.unit.value / Math.max(1, rep.value);
            case DAY_OF_TIME -> rep.unit.value;
        };
    }

    // ============================================================================
    // OVERDUE - Berechnet wie viele Wiederholungen verpasst wurden..
    // ============================================================================
    /**
     * Überfälligkeit = Tage seit letzter Erledigung / Frequenz
     *
     * Beispiel: Task soll alle 7 Tage erledigt werden, letzte Erledigung vor 21 Tagen
     *           → 21 / 7 = 3 verpasste Wiederholungen
     *
     * @return Anzahl der verpassten Wiederholungen (0 wenn nicht überfällig)
     */
    public int overdue(LocalDate day) {
        int freq = frequency();

        // Keine Wiederholung oder Frequenz 0 → nicht überfällig
        if (freq == 0) {
            return 0;
        }

        // lastCompletion oder created
        LocalDate referenceDate;
        if (this.lastCompletion != null) {
            referenceDate = this.lastCompletion;
        } else {
            referenceDate = this.created;
        }

        // Tage seit Referenzdatum berechnen
        long daysSinceReference = java.time.temporal.ChronoUnit.DAYS.between(referenceDate, day);

        // Verpasste Wiederholungen berechnen
        int missedRepetitions = (int) (daysSinceReference / freq);

        return Math.max(0, missedRepetitions);
    }

    // ============================================================================
    // REMAININGTIME - Verbleibende Tage bis zur nächsten relevanten Deadline
    // ============================================================================
    /**
     * Deadline → Tage bis Deadline.
     * REPS_PER_TIME → Tage in Periode minus eingeplante.
     * Progress-only → 7 Tage Fallback.
     */
    public int remainingTime(LocalDate day) {
        if (this.deadline != null) {
            return (int) Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(day, this.deadline));
        }

        if (this.repetition != null && this.repetition.type == RepetitionType.REPS_PER_TIME) {
            LocalDate periodEnd = switch (this.repetition.unit) {
                case DAY -> day;
                case WEEK -> day.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
                case MONTH -> day.withDayOfMonth(day.lengthOfMonth());
            };
            int daysInPeriod = (int) java.time.temporal.ChronoUnit.DAYS.between(day, periodEnd) + 1;
            int scheduledInPeriod = countScheduledInPeriod(day, periodEnd);
            return Math.max(0, daysInPeriod - scheduledInPeriod);
        }

        if (this.progressTarget > 0 && this.progressCurrent < this.progressTarget) {
            return 7;
        }

        return 0;
    }

    // ============================================================================
    // WORK - Verbleibende Arbeit (reps * progress, je min 1)
    // ============================================================================
    /**
     * Kombinierte verbleibende Arbeit aus Reps und Progress.
     * Reps + Progress → reps * units.  Nur eins → das eine.  Nur Deadline → 1.  Keins → 0.
     */
    public int work(LocalDate day) {
        int reps = (this.repetition != null && this.repetition.type == RepetitionType.REPS_PER_TIME)
                ? remainingRepsInPeriod(day) : 0;
        int units = (this.progressTarget > 0) ? this.progressTarget - this.progressCurrent : 0;

        if (reps <= 0 && units <= 0 && this.deadline == null) return 0;
        return Math.max(reps, 1) * Math.max(units, 1);
    }

    private int remainingRepsInPeriod(LocalDate day) {
        if (this.repetition == null || this.repetition.type != RepetitionType.REPS_PER_TIME) {
            return 0;
        }

        LocalDate periodEnd = switch (this.repetition.unit) {
            case DAY -> day;
            case WEEK -> day.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            case MONTH -> day.withDayOfMonth(day.lengthOfMonth());
        };

        int scheduledInPeriod = countScheduledInPeriod(day, periodEnd);
        return Math.max(0, this.repetition.value - (this.completions + scheduledInPeriod));
    }

    /**
     * Hilfsmethode: Zählt bereits geplante Termine zwischen day und periodEnd (inklusiv).
     */
    private int countScheduledInPeriod(LocalDate day, LocalDate periodEnd) {
        if (this.scheduled == null || this.scheduled.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (LocalDate scheduledDate : this.scheduled) {
            if (!scheduledDate.isBefore(day) && !scheduledDate.isAfter(periodEnd)) {
                count++;
            }
        }
        return count;
    }

    // ============================================================================
    // SCOREFOLLOW - Berechnet Prio-Boost basierend auf historischen FollowUp-Mustern
    // ============================================================================
    /**
     * Berechnet einen Prio-Boost basierend auf historischen FollowUp-Mustern.
     *
     * @param predecessorId ID des Items das direkt vor diesem platziert ist
     * @return Boost als Faktor (z.B. 0.15 = 15% Boost), 0.0 wenn keine relevanten Daten
     */
    public double scoreFollow(Long predecessorId) {
        if (predecessorId == null || followUps == null || followUps.isEmpty()) {
            return 0.0;
        }

        // 1. Anzahl der Follows von diesem Predecessor
        int predecessorCount = followUps.getOrDefault(predecessorId, 0);

        // 2. Schwelle: Mindestens 5 mal gefolgt
        if (predecessorCount < 5) {
            return 0.0;
        }

        // 3. Total aller FollowUps berechnen
        int totalFollowUps = 0;
        for (Integer count : followUps.values()) {
            totalFollowUps += count;
        }

        // 4. Anteil berechnen
        double percentage = (double) predecessorCount / totalFollowUps;

        // 5. Schwelle: Mindestens 5% aller FollowUps
        if (percentage < 0.05) {
            return 0.0;
        }

        // 6. Boost = Anteil (z.B. 20% der Follows → 0.20 Boost)
        return percentage;
    }

}
