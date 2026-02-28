package com.autosecretary.features.task.application.internal;

import com.autosecretary.shared.Period;
import com.autosecretary.shared.Priority;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import com.autosecretary.features.task.data.Task;
import com.autosecretary.features.task.data.TaskPrefSlotFactory;
import com.autosecretary.features.task.data.TaskPrerequisite;

/**
 * Creates demo tasks for first-run database seeding (fresh install with no data).
 *
 * <p>Called by {@link com.autosecretary.features.task.application.RegenerateScheduleUseCase}
 * when the task database is found to be empty. After the initial seed, this factory is never
 * invoked again — user-created tasks replace the demo data over time.
 *
 * <p><strong>German task names:</strong> All task titles are in German because the app's
 * user-facing language is German throughout (see CLAUDE.md conventions). This is intentional.
 *
 * <p>Tasks are grouped by time-of-day category (morning, sport, daytime, deadline, evening)
 * and demonstrate all key scheduling features: tree hierarchies, prerequisites, adaptive timing,
 * progress tracking, and priority levels.
 */
public final class TaskSeedDataFactory {

    // Schedule time anchors
    private static final LocalTime TIME_6_AM = LocalTime.of(6, 0);
    private static final LocalTime TIME_7_AM = LocalTime.of(7, 0);
    private static final LocalTime TIME_7_30_AM = LocalTime.of(7, 30);
    private static final LocalTime TIME_8_AM = LocalTime.of(8, 0);
    private static final LocalTime TIME_9_AM = LocalTime.of(9, 0);
    private static final LocalTime TIME_10_AM = LocalTime.of(10, 0);
    private static final LocalTime TIME_12_PM = LocalTime.of(12, 0);
    private static final LocalTime TIME_1_30_PM = LocalTime.of(13, 30);
    private static final LocalTime TIME_2_30_PM = LocalTime.of(14, 30);
    private static final LocalTime TIME_4_30_PM = LocalTime.of(16, 30);
    private static final LocalTime TIME_5_PM = LocalTime.of(17, 0);
    private static final LocalTime TIME_5_30_PM = LocalTime.of(17, 30);
    private static final LocalTime TIME_6_PM = LocalTime.of(18, 0);
    private static final LocalTime TIME_7_PM = LocalTime.of(19, 0);
    private static final LocalTime TIME_7_30_PM = LocalTime.of(19, 30);
    private static final LocalTime TIME_8_PM = LocalTime.of(20, 0);
    private static final LocalTime TIME_8_30_PM = LocalTime.of(20, 30);

    // Day-of-week patterns
    private static final EnumSet<DayOfWeek> WEEKDAYS = EnumSet.of(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
    private static final EnumSet<DayOfWeek> MON_WED_FRI = EnumSet.of(
        DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
    private static final EnumSet<DayOfWeek> SATURDAY_ONLY = EnumSet.of(DayOfWeek.SATURDAY);
    private static final EnumSet<DayOfWeek> SUNDAY_ONLY = EnumSet.of(DayOfWeek.SUNDAY);
    private static final EnumSet<DayOfWeek> ALL_DAYS = EnumSet.allOf(DayOfWeek.class);

    private TaskSeedDataFactory() {}

    /**
     * Fluent builder for seed tasks, eliminating repetitive field-setting patterns.
     */
    private static class TaskBuilder {
        private final Task task;

        TaskBuilder(String title, int reps, int perPeriod, Period period, LocalDate deadline,
                    int cooldown, LocalTime startTime, int maxDuration) {
            this.task = new Task(title, reps, perPeriod, period, deadline, cooldown, startTime, maxDuration);
        }

        TaskBuilder priority(Priority p) {
            task.core.priority = p;
            return this;
        }

        TaskBuilder minDuration(int minutes) {
            task.core.minDuration = minutes;
            return this;
        }

        TaskBuilder description(String desc) {
            task.core.description = desc;
            return this;
        }

        TaskBuilder adaptive(boolean isAdaptive) {
            task.core.adaptive = isAdaptive;
            return this;
        }

        TaskBuilder currentStreak(int streak) {
            task.core.history.currentStreak = streak;
            return this;
        }

        TaskBuilder completions(int count, int tracked, int totalMinutes) {
            task.core.history.completions = count;
            task.core.history.trackedCompletions = tracked;
            task.core.history.totalDuration = totalMinutes;
            return this;
        }

        TaskBuilder progress(String unit, int target, int current, int minPerRep, int maxPerRep,
                            int totalProgress, int totalTime) {
            task.core.progress.unit = unit;
            task.core.progress.target = target;
            task.core.progress.current = current;
            task.core.progress.minPerRep = minPerRep;
            task.core.progress.maxPerRep = maxPerRep;
            task.core.progress.totalProgress = totalProgress;
            task.core.progress.totalTime = totalTime;
            return this;
        }

        TaskBuilder resetPerRep(boolean resetPerRep) {
            task.core.progress.resetPerRep = resetPerRep;
            return this;
        }

        TaskBuilder prefSlots(EnumSet<DayOfWeek> days, LocalTime time) {
            task.prefSlots.clear();
            task.prefSlots.add(TaskPrefSlotFactory.create(task.core.id, days, time));
            return this;
        }

        TaskBuilder prefSlots(LocalTime time1, LocalTime time2, LocalTime time3) {
            // For tasks with multiple pref slots across the same days (e.g., Podcast at 8am, 1pm, 6pm)
            task.prefSlots.clear();
            task.prefSlots.add(TaskPrefSlotFactory.create(task.core.id, ALL_DAYS, time1));
            task.prefSlots.add(TaskPrefSlotFactory.create(task.core.id, ALL_DAYS, time2));
            task.prefSlots.add(TaskPrefSlotFactory.create(task.core.id, ALL_DAYS, time3));
            return this;
        }

        TaskBuilder addChild(Task child) {
            task.children.add(child);
            return this;
        }

        TaskBuilder addPrerequisite(Task prerequisiteTask) {
            task.prerequisites.add(new TaskPrerequisite(task.core.id, prerequisiteTask.core.id));
            return this;
        }

        TaskBuilder addPrerequisiteWithGap(Task prerequisiteTask, int gapMinutes) {
            task.prerequisites.add(new TaskPrerequisite(task.core.id, prerequisiteTask.core.id, gapMinutes));
            return this;
        }

        TaskBuilder closeOnMiss(boolean close) {
            task.core.closeOnMiss = close;
            return this;
        }

        Task build() {
            return task;
        }
    }

    public static List<Task> createDefaultTasks() {
        List<Task> tasks = new ArrayList<>();
        tasks.addAll(createMorningGroup());
        tasks.addAll(createSportGroup());
        tasks.addAll(createDaytimeTasks());
        tasks.addAll(createDeadlineTasks());
        tasks.addAll(createEveningGroup());
        return tasks;
    }

    // -------------------------------------------------------------------------
    // Morning group: Morgenroutine (tree) + Meditation
    // -------------------------------------------------------------------------

    private static List<Task> createMorningGroup() {
        List<Task> group = new ArrayList<>();

        // Morgenroutine (Parent) — HIGH, adaptive, history
        //   ├── Duschen
        //   │   └── Haare föhnen
        //   ├── Zähneputzen
        //   └── Frühstück
        //       └── Abspülen
        Task morgen = new TaskBuilder("Morgenroutine", 1, 1, Period.DAY, null, 1, TIME_6_AM, 60)
            .priority(Priority.HIGH)
            .adaptive(true)
            .minDuration(30)
            .completions(30, 28, 700)
            .currentStreak(12)
            .build();
        group.add(morgen);

        Task duschen = new TaskBuilder("Duschen", 1, 1, Period.DAY, null, 1, TIME_6_AM, 15)
            .minDuration(5)
            .build();
        morgen.children.add(duschen);

        Task haare = new TaskBuilder("Haare föhnen", 1, 1, Period.DAY, null, 1, TIME_6_AM, 5)
            .minDuration(5)
            .build();
        duschen.children.add(haare);

        Task zaehne = new TaskBuilder("Zähneputzen", 1, 1, Period.DAY, null, 1, TIME_6_AM, 5)
            .minDuration(5)
            .build();
        morgen.children.add(zaehne);

        Task fruehstueck = new TaskBuilder("Frühstück", 1, 1, Period.DAY, null, 1, TIME_6_AM, 25)
            .minDuration(10)
            .build();
        morgen.children.add(fruehstueck);

        Task abspuelen = new TaskBuilder("Abspülen", 1, 1, Period.DAY, null, 1, TIME_6_AM, 10)
            .minDuration(5)
            .build();
        fruehstueck.children.add(abspuelen);

        // Meditation — MEDIUM, all days, 07:00, history
        Task meditation = new TaskBuilder("Meditation", 1, 1, Period.DAY, null, 1, TIME_7_AM, 15)
            .minDuration(10)
            .completions(14, 14, 0)
            .currentStreak(7)
            .build();
        group.add(meditation);

        return group;
    }

    // -------------------------------------------------------------------------
    // Sport group: Sport (tree with Aufwärmen + Training + Dehnen)
    // -------------------------------------------------------------------------

    private static List<Task> createSportGroup() {
        List<Task> group = new ArrayList<>();

        // Sport (parent) — MEDIUM, 3/week, Mon/Wed/Fri
        Task sport = new TaskBuilder("Sport", 3, 1, Period.WEEK, null, 1, TIME_7_30_AM, 60)
            .priority(Priority.MEDIUM)
            .minDuration(30)
            .prefSlots(MON_WED_FRI, TIME_7_30_AM)
            .build();
        group.add(sport);

        // Aufwärmen (child of Sport)
        Task aufwaermen = new TaskBuilder("Aufwärmen", 3, 1, Period.WEEK, null, 1, TIME_7_30_AM, 10)
            .minDuration(5)
            .prefSlots(MON_WED_FRI, TIME_7_30_AM)
            .build();
        sport.children.add(aufwaermen);

        // Training (child of Sport) — prereq: Aufwärmen
        Task training = new TaskBuilder("Training", 3, 1, Period.WEEK, null, 1, TIME_7_30_AM, 45)
            .priority(Priority.MEDIUM)
            .minDuration(20)
            .prefSlots(MON_WED_FRI, TIME_7_30_AM)
            .addPrerequisite(aufwaermen)
            .build();
        sport.children.add(training);

        // Dehnen (child of Training)
        Task dehnen = new TaskBuilder("Dehnen", 3, 1, Period.WEEK, null, 1, TIME_7_30_AM, 10)
            .minDuration(5)
            .prefSlots(MON_WED_FRI, TIME_7_30_AM)
            .build();
        training.children.add(dehnen);

        return group;
    }

    // -------------------------------------------------------------------------
    // Daytime tasks: Arbeit, Mittagspause, Spanisch, Lesen, Einkaufen, Wohnung
    // -------------------------------------------------------------------------

    private static List<Task> createDaytimeTasks() {
        List<Task> group = new ArrayList<>();

        // Arbeit — MEDIUM, Mon-Fri, 180min
        Task arbeit = new TaskBuilder("Arbeit", 1, 1, Period.DAY, null, 1, TIME_9_AM, 180)
            .priority(Priority.MEDIUM)
            .minDuration(60)
            .prefSlots(WEEKDAYS, TIME_9_AM)
            .build();
        group.add(arbeit);

        // Mittagspause — MEDIUM, all days, 12:00
        Task mittagspause = new TaskBuilder("Mittagspause", 1, 1, Period.DAY, null, 1, TIME_12_PM, 30)
            .minDuration(15)
            .build();
        group.add(mittagspause);

        // Spanisch lernen — MEDIUM, all days, 13:30, progress tracking
        Task spanisch = new TaskBuilder("Spanisch lernen", 1, 1, Period.DAY, null, 1, TIME_1_30_PM, 30)
            .minDuration(15)
            .progress("Lektionen", 50, 12, 1, 2, 12, 360)
            .build();
        group.add(spanisch);

        // Lesen — LOW, all days, 14:30, progress tracking
        Task lesen = new TaskBuilder("Lesen", 1, 1, Period.DAY, null, 1, TIME_2_30_PM, 30)
            .priority(Priority.LOW)
            .minDuration(15)
            .progress("Seiten", 300, 85, 10, 30, 85, 510)
            .build();
        group.add(lesen);

        // Einkaufen — MEDIUM, Saturday only
        Task einkaufen = new TaskBuilder("Einkaufen", 1, 1, Period.WEEK, null, 1, TIME_10_AM, 60)
            .minDuration(30)
            .prefSlots(SATURDAY_ONLY, TIME_10_AM)
            .build();
        group.add(einkaufen);

        // Wohnung aufräumen — LOW, all days
        Task aufraumen = new TaskBuilder("Wohnung aufräumen", 1, 1, Period.WEEK, null, 1, TIME_10_AM, 45)
            .priority(Priority.LOW)
            .minDuration(20)
            .build();
        group.add(aufraumen);

        return group;
    }

    // -------------------------------------------------------------------------
    // Deadline tasks: Steuererklärung, Zahnarzttermin, Notfallplan
    // -------------------------------------------------------------------------

    private static List<Task> createDeadlineTasks() {
        List<Task> group = new ArrayList<>();

        // Steuererklärung — HIGH, deadline +7 days
        Task steuer = new TaskBuilder("Steuererklärung", 1, 1, Period.MONTH, LocalDate.now().plusDays(7), 1, TIME_10_AM, 90)
            .priority(Priority.HIGH)
            .minDuration(30)
            .description("Belege sortieren und Formulare ausfüllen")
            .build();
        group.add(steuer);

        // Zahnarzttermin — HIGH, deadline +3 days
        Task zahnarzt = new TaskBuilder("Zahnarzttermin", 1, 1, Period.MONTH, LocalDate.now().plusDays(3), 1, TIME_9_AM, 30)
            .priority(Priority.HIGH)
            .minDuration(15)
            .description("Termin beim Zahnarzt")
            .build();
        group.add(zahnarzt);

        // Notfallplan aktualisieren — CRITICAL, overdue deadline, closeOnMiss=false
        Task notfallplan = new TaskBuilder("Notfallplan aktualisieren", 1, 1, Period.MONTH, LocalDate.now().minusDays(2), 1, TIME_4_30_PM, 30)
            .priority(Priority.CRITICAL)
            .closeOnMiss(false)
            .minDuration(10)
            .description("Kontaktliste und Eskalationspfade prüfen")
            .build();
        group.add(notfallplan);

        return group;
    }

    // -------------------------------------------------------------------------
    // Evening group: Abendspaziergang, Podcast, Japanisch, Fitnessplan,
    //                Abendroutine (tree), Wochenbericht, Wäsche (pair)
    // -------------------------------------------------------------------------

    private static List<Task> createEveningGroup() {
        List<Task> group = new ArrayList<>();

        // Abendspaziergang — LOW, Sunday only, cooldown=2
        Task spaziergang = new TaskBuilder("Abendspaziergang", 1, 1, Period.WEEK, null, 2, TIME_5_PM, 45)
            .priority(Priority.LOW)
            .minDuration(20)
            .prefSlots(SUNDAY_ONLY, TIME_5_PM)
            .build();
        group.add(spaziergang);

        // Podcast hören — MEDIUM, 3x daily (repsPerDay=3), spread across day
        Task podcast = new TaskBuilder("Podcast hören", 3, 1, Period.DAY, null, 1, TIME_8_AM, 20)
            .minDuration(10)
            .prefSlots(TIME_8_AM, TIME_1_30_PM, TIME_6_PM)
            .build();
        group.add(podcast);

        // Japanisch lernen — MEDIUM, daily, 19:00, progress resetPerRep=true
        Task japanisch = new TaskBuilder("Japanisch lernen", 1, 1, Period.DAY, null, 1, TIME_7_PM, 30)
            .minDuration(10)
            .progress("Vokabeln", 20, 5, 5, 20, 5, 30)
            .resetPerRep(true)
            .build();
        group.add(japanisch);

        // Fitnessplan schreiben — MEDIUM, daily, 19:30, very short (5min)
        Task fitnessplan = new TaskBuilder("Fitnessplan schreiben", 1, 1, Period.DAY, null, 1, TIME_7_30_PM, 5)
            .minDuration(5)
            .build();
        group.add(fitnessplan);

        // Abendroutine (Parent) — MEDIUM, daily, 20:00
        //   ├── Tagebuch schreiben (adaptive)
        //   │   └── Notizen ordnen
        //   └── Hautpflege
        Task abend = new TaskBuilder("Abendroutine", 1, 1, Period.DAY, null, 1, TIME_8_PM, 40)
            .minDuration(20)
            .build();
        group.add(abend);

        Task tagebuch = new TaskBuilder("Tagebuch schreiben", 1, 1, Period.DAY, null, 1, TIME_8_PM, 20)
            .adaptive(true)
            .minDuration(10)
            .build();
        abend.children.add(tagebuch);

        Task notizen = new TaskBuilder("Notizen ordnen", 1, 1, Period.DAY, null, 1, TIME_8_PM, 10)
            .minDuration(5)
            .build();
        tagebuch.children.add(notizen);

        Task hautpflege = new TaskBuilder("Hautpflege", 1, 1, Period.DAY, null, 1, TIME_8_PM, 10)
            .minDuration(5)
            .build();
        abend.children.add(hautpflege);

        // Wochenbericht — HIGH, bi-weekly (perPeriod=2), 20:30
        Task wochenbericht = new TaskBuilder("Wochenbericht", 1, 2, Period.WEEK, null, 1, TIME_8_30_PM, 45)
            .priority(Priority.HIGH)
            .minDuration(20)
            .description("Erledigtes und offene Punkte zusammenfassen")
            .build();
        group.add(wochenbericht);

        // Wäsche-Paar: Wäsche waschen → Wäsche aufhängen (prereq, 45min gap)
        Task waescheWaschen = new TaskBuilder("Wäsche waschen", 2, 1, Period.WEEK, null, 1, TIME_4_30_PM, 15)
            .minDuration(10)
            .build();
        group.add(waescheWaschen);

        Task waescheAufhaengen = new TaskBuilder("Wäsche aufhängen", 2, 1, Period.WEEK, null, 1, TIME_5_30_PM, 10)
            .minDuration(5)
            .adaptive(true)
            .addPrerequisiteWithGap(waescheWaschen, 45)
            .build();
        group.add(waescheAufhaengen);

        return group;
    }
}
