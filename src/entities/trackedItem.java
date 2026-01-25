package entities;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import repository.SQLrepo;
import repository.Table;

/**
 * Task Entity - repräsentiert eine einzelne Aufgabe.
 */
public class trackedItem {

    // Basic Fields
    public ItemType type;                   // Task, Goal, Block oder Project?
    public Long id;                         // Wird zum referenzieren des Items verwendet
    public String title;                    // Human readable reference
    public String description;              // What's this about?
    public Long parent;                     // ID des übergeordneten Items (null = kein Parent)
    public List<Long> children;             // Trackt, falls vorhanden, child IDs für goals und metaGoals
    public LocalDate created;               // Wann das Item erstellt wurde.

    // Completion Logik
    public LocalDate lastCompletion;        // when was the task last completed?
    public int completions;                 // How often hast this task been completed (used to track tasks with multiple completion requirements per timeframe like 3x per week. Resets on repetition)
    public Boolean isCompleted;             // Wurde die Aufgabe erledigt?

    // Info für Planung
    public Repetition repetition;           // Definiert wie nextRepetition berechnet wird
    public Boolean completeFirst;           // Should we wait for completion before repetition or skip if unfulfilled and repeat on schedule?
    public Timeframe nextRepetition;        // Wann wird die Task wieder Offen? Kann Datum sein oder Zeitfenster
    public int timeToComplete;              // Wie lange braucht die Aufgabe um bearbeitet zu werden? bei Goals, wieviel Zeit soll für das Goal eingeplant werden?
    public Priority priority;               // Wie wichtig ist diese Task? Beeinflusst Planung.
    public List<LocalDate> scheduled;       // Alle Daten, an denen das Item momentan eingeplant ist.
    public int cooldown;                    // Wenn die Task für eine bestimmte Zeit nicht wiederholt werden soll nach der letzten completion.

    // History
    public int currentStreak;               // wie oft hintereinander abgeschloßen ohne Verfehlung?
    public int averageStreak;               // durchschnittliche Streak Länge
    public int nrOfStreaks;                 // menge der beendeten Streaks
    public Map<Long, Integer> followUps;    // Tasks, welche direkt vor dieser erledigt wurden (ID und Häufigkeit)
    public LocalTime prefTime;              // Zu welcher Uhrzeit wird die Task für gewöhnlich erledigt? z.B. 14:30
    public int totalCompletions;            // Wie oft wurde die Task erledigt? (all time)
    public int minIntervalDays;             // Mindestabstand zwischen Einplanungen in Tagen (0 = keine Einschränkung)

    // Builder
    public static class Builder {
        private final trackedItem item = new trackedItem();

        public Builder(ItemType type, String title, Priority priority) {
            item.type = type;
            item.title = title;
            item.priority = priority;
        }

        public Builder description(String v) { item.description = v; return this; }
        public Builder parent(Long v) { item.parent = v; return this; }
        public Builder children(List<Long> v) { item.children = v; return this; }
        public Builder created(String v) { item.created = LocalDate.parse(v); return this; }
        public Builder lastCompletion(String v) { item.lastCompletion = LocalDate.parse(v); return this; }
        public Builder completions(int v) { item.completions = v; return this; }
        public Builder isCompleted(boolean v) { item.isCompleted = v; return this; }
        public Builder completeFirst(boolean v) { item.completeFirst = v; return this; }
        public Builder nextRepetition(Timeframe v) { item.nextRepetition = v; return this; }
        public Builder timeToComplete(int v) { item.timeToComplete = v; return this; }
        public Builder scheduled(List<LocalDate> v) { item.scheduled = v; return this; }
        public Builder cooldown(int v) { item.cooldown = v; return this; }
        public Builder prefTime(String v) { item.prefTime = LocalTime.parse(v); return this; }
        public Builder minIntervalDays(int v) { item.minIntervalDays = v; return this; }
        public Builder currentStreak(int v) { item.currentStreak = v; return this; }
        public Builder averageStreak(int v) { item.averageStreak = v; return this; }
        public Builder nrOfStreaks(int v) { item.nrOfStreaks = v; return this; }
        public Builder totalCompletions(int v) { item.totalCompletions = v; return this; }
        public Builder followUps(Map<Long, Integer> v) { item.followUps = v; return this; }

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

        public trackedItem build() { return item; }
    }

    //Subklassen
    public static class Repetition {
        public RepetitionType type;
        public RepUnits unit;
        public int value;              // interval (INTERVAL), requiredCompletions (REPS_PER_TIME), dayOfMonth (DAY_OF_TIME/MONTH)
        public DayOfWeek dayOfWeek;    // nur für DAY_OF_TIME/WEEK
    }
    
    public static enum ItemType {
        TASK, GOAL, BLOCK, PROJECT
    }

    public static enum RepetitionType {
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

    // ============== BUSINESS LOGIK ==============

    // ============================================================================
    // UPDATE - Wird von cleanToDo aufgerufen, wenn Task in der gestrigen Liste war.
    // ============================================================================
    /**
     * Zentraler Entry-Point für Tagesabschluss-Logik.
     * Wird von cleanToDo für jeden Slot der gestrigen Liste aufgerufen.
     *
     * @param completed   Hat der User den Slot als erledigt markiert?
     * @param workStart   Timer-Start (null wenn Timer nicht genutzt)
     * @param workEnd     Timer-Ende (null wenn Timer nicht genutzt)
     * @param day         Tag des Slots (gestern)
     * @param repo        Zum Persistieren der Änderungen
     */
    public void update(boolean completed, LocalTime workStart, LocalTime workEnd,
                       LocalDate day, SQLrepo repo) {
        if (completed) {
            // Completion tracking
            this.completions++;
            this.lastCompletion = day;

            if (this.type == ItemType.TASK) {
                // PrefTime aus workStart ableiten
                updatePrefTime(workStart);

                // TimeToComplete: aus Timer-Daten ableiten (laufender Durchschnitt)
                if (workStart != null && workEnd != null) {
                    int actualMinutes = (int) java.time.temporal.ChronoUnit.MINUTES.between(workStart, workEnd);
                    if (actualMinutes > 0) {
                        updateTimeToComplete(actualMinutes);
                    }
                }
            }

        } else {
            // Nicht erledigt und überfällig → Streak reset wenn !completeFirst
            if ((this.completeFirst == null || !this.completeFirst) && overdue(day) > 0) {
                resetStreak();
                this.completions = 0;
            }
        }

        repo.write(this);
    }

    /**
     * Aktualisiert die bevorzugte Zeit (laufender Durchschnitt).
     *
     * Formel: neuerDurchschnitt = (alter × anzahl + neu) / (anzahl + 1)
     */
    private void updatePrefTime(LocalTime workStart) {
        if (this.prefTime == null || this.totalCompletions == 0) {
            this.prefTime = workStart;
            this.totalCompletions = 1;
            return;
        }

        int altInMinuten = this.prefTime.getHour() * 60 + this.prefTime.getMinute();
        int neuInMinuten = workStart.getHour() * 60 + workStart.getMinute();

        int neuerDurchschnitt = (altInMinuten * this.totalCompletions + neuInMinuten)
                                / (this.totalCompletions + 1);

        this.prefTime = LocalTime.of(neuerDurchschnitt / 60, neuerDurchschnitt % 60);
        this.totalCompletions++;
    }

    /**
     * Aktualisiert timeToComplete (laufender Durchschnitt aus Timer-Daten).
     */
    private void updateTimeToComplete(int actualMinutes) {
        if (this.totalCompletions <= 1) {
            this.timeToComplete = actualMinutes;
            return;
        }
        this.timeToComplete = (this.timeToComplete * (this.totalCompletions - 1) + actualMinutes)
                              / this.totalCompletions;
    }

    /**
     * Prüft ob genug Completions für die aktuelle Periode erreicht sind.
     */
    public boolean checkCompletion(LocalDate day) {
        if (this.repetition == null) {
            return this.lastCompletion != null;
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
            return true;
        }

        return false;
    }

    private void resetStreak() {
        this.nrOfStreaks += 1;
        this.averageStreak = ((this.averageStreak * (this.nrOfStreaks - 1)) + this.currentStreak) / this.nrOfStreaks;
        this.currentStreak = 0;
    }

    // ============================================================================
    // CALCNEXTREPETITION - Berechnet das nächste Wiederholungsdatum basierend auf dem Repetition-Typ.
    // ============================================================================
    /**
     * @return Timeframe für die nächste Wiederholung
     */
    private Timeframe calcNextRepetition(LocalDate day) {
        
        if (this.repetition == null) {
            return null;  // Einmalige Aufgabe
        }
        
        Repetition rep = this.repetition;
        return switch (rep.type) {
            case DAY_OF_TIME -> {
                if (rep.unit == RepUnits.WEEK) {
                    yield new Timeframe(day.with(TemporalAdjusters.next(rep.dayOfWeek)));
                } else {
                    LocalDate thisMonth = day.withDayOfMonth(Math.min(rep.value, day.lengthOfMonth()));
                    if (thisMonth.isAfter(day)) {
                        yield new Timeframe(thisMonth);
                    } else {
                        LocalDate nextMonth = day.plusMonths(1).withDayOfMonth(
                            Math.min(rep.value, day.plusMonths(1).lengthOfMonth()));
                        yield new Timeframe(nextMonth);
                    }
                }
            }
            case REPS_PER_TIME -> {
                if (rep.unit == RepUnits.WEEK) {
                    LocalDate nextMonday = day.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
                    yield new Timeframe(nextMonday, nextMonday.plusDays(6));
                } else {
                    LocalDate firstOfMonth = day.plusMonths(1).withDayOfMonth(1);
                    yield new Timeframe(firstOfMonth, firstOfMonth.withDayOfMonth(firstOfMonth.lengthOfMonth()));
                }
            }
            case INTERVAL -> {
                LocalDate next = switch (rep.unit) {
                    case DAY -> day.plusDays(rep.value);
                    case WEEK -> day.plusWeeks(rep.value);
                    case MONTH -> day.plusMonths(rep.value);
                };
                yield new Timeframe(next);
            }
        };
    }

    // ============================================================================
    // SCHEDULE - Updated die Task in der Datenbank mit neuem scheduled date
    // ============================================================================
    /**
     * Updated schedule mit neuem datum
     * berechnet und updated nextRepetition mit calcNextRepetition(day)
     * 
     * Nutzt SQLrepo.write zum schreiben
     * @param day
     */
    public void schedule(LocalDate day, SQLrepo repo) {
        if (this.scheduled == null) {
            this.scheduled = new ArrayList<>();
        }
        if (!this.scheduled.contains(day)) {
            this.scheduled.add(day);
        }

        this.nextRepetition = calcNextRepetition(day);
        repo.write(this);

        // Falls Goal mit Parent, auch Parent's scheduled updaten
        if (this.type == ItemType.GOAL && this.parent != null) {
            trackedItem parentItem = repo.fetch(Table.ITEMS, this.parent);
            if (parentItem != null) {
                if (parentItem.scheduled == null) {
                    parentItem.scheduled = new ArrayList<>();
                }
                if (!parentItem.scheduled.contains(day)) {
                    parentItem.scheduled.add(day);
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
    // REMAININGTIME - Berechnet die verbleibende Zeit für RepsPerTimeRepetition items in Tagen.
    // ============================================================================
    /**
     * Beispiel: "3x pro Woche", heute ist Mittwoch
     *           → Woche endet Sonntag → 4 Tage verbleibend
     *
     * @return Verbleibende Tage in der aktuellen Periode (0 wenn keine RepsPerTimeRepetition)
     */
    public int remainingTime(LocalDate day) {
        if (this.repetition == null || this.repetition.type != RepetitionType.REPS_PER_TIME) {
            return 0;
        }
        return switch (this.repetition.unit) {
            case DAY -> 1;
            case WEEK -> {
                LocalDate endOfWeek = day.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
                yield (int) java.time.temporal.ChronoUnit.DAYS.between(day, endOfWeek) + 1;
            }
            case MONTH -> {
                LocalDate endOfMonth = day.withDayOfMonth(day.lengthOfMonth());
                yield (int) java.time.temporal.ChronoUnit.DAYS.between(day, endOfMonth) + 1;
            }
        };
    }

    // ============================================================================
    // REMAININGREPS -  Berechnet die verbleibenden benötigten Completions für RepsPerTimeRepetition items.
    // ============================================================================
    /**
     * Nur relevant für RepsPerTimeRepetition Tasks.
     *
     * Beispiel: "3x pro Woche", bereits 1x erledigt
     *           → 3 - 1 = 2 verbleibende Completions
     *
     * @return Verbleibende Completions (0 wenn keine RepsPerTimeRepetition oder bereits erfüllt)
     */
    public int remainingReps(LocalDate day) {
        if (this.repetition == null || this.repetition.type != RepetitionType.REPS_PER_TIME) {
            return 0;
        }
        return Math.max(0, this.repetition.value - this.completions);
    }

}
