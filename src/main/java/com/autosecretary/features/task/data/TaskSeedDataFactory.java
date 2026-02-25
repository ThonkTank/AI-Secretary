package com.autosecretary.features.task.data;

import com.autosecretary.shared.Period;
import com.autosecretary.shared.Priority;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Creates default demo tasks for first-run DB seeding.
 * Called by {@code RegenerateScheduleUseCase} when the database is empty.
 */
public final class TaskSeedDataFactory {

    private TaskSeedDataFactory() {}

    public static List<Task> createDefaultTasks() {
        List<Task> newTasks = new ArrayList<>();
        Task t;

        // 1. Morgenroutine (Parent) — HIGH, adaptive, history
        //    L0: Morgenroutine
        //    ├── L1: Duschen
        //    │   └── L2: Haare föhnen
        //    ├── L1: Zähneputzen
        //    └── L1: Frühstück
        //        └── L2: Abspülen
        Task morgen = new Task("Morgenroutine", 1, 1, Period.DAY, null, 1, LocalTime.of(6, 0), 60);
        morgen.core.priority = Priority.HIGH;
        morgen.core.adaptive = true;
        morgen.core.minDuration = 30;
        morgen.core.history.currentStreak = 12;
        morgen.core.history.completions = 30;
        morgen.core.history.trackedCompletions = 28;
        morgen.core.history.totalDuration = 700;
        newTasks.add(morgen);

        // 1a. Duschen (L1 Kind von Morgenroutine)
        Task duschen = new Task("Duschen", 1, 1, Period.DAY, null, 1, LocalTime.of(6, 0), 15);
        duschen.core.minDuration = 5;
        morgen.children.add(duschen);

        // 1b. Haare föhnen (L2 Kind von Duschen — Sub-Sub-Task)
        t = new Task("Haare föhnen", 1, 1, Period.DAY, null, 1, LocalTime.of(6, 0), 5);
        t.core.minDuration = 5;
        duschen.children.add(t);

        // 1c. Zähneputzen (L1 Kind von Morgenroutine)
        t = new Task("Zähneputzen", 1, 1, Period.DAY, null, 1, LocalTime.of(6, 0), 5);
        t.core.minDuration = 5;
        morgen.children.add(t);

        // 1d. Frühstück (L1 Kind von Morgenroutine)
        Task fruehstueck = new Task("Frühstück", 1, 1, Period.DAY, null, 1, LocalTime.of(6, 0), 25);
        fruehstueck.core.minDuration = 10;
        morgen.children.add(fruehstueck);

        // 1e. Abspülen (L2 Kind von Frühstück — Sub-Sub-Task)
        t = new Task("Abspülen", 1, 1, Period.DAY, null, 1, LocalTime.of(6, 0), 10);
        t.core.minDuration = 5;
        fruehstueck.children.add(t);

        // 3. Meditation — MEDIUM, all days, 07:00, history
        t = new Task("Meditation", 1, 1, Period.DAY, null, 1, LocalTime.of(7, 0), 15);
        t.core.minDuration = 10;
        t.core.history.currentStreak = 7;
        t.core.history.completions = 14;
        newTasks.add(t);

        // 4. Sport (parent) — MEDIUM, 3/week, Mon/Wed/Fri
        Task sport = new Task("Sport", 3, 1, Period.WEEK, null, 1, LocalTime.of(7, 30), 60);
        sport.core.priority = Priority.MEDIUM;
        sport.core.minDuration = 30;
        sport.prefSlots.clear();
        sport.prefSlots.add(TaskPrefSlotFactory.create(sport.core.id, EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), LocalTime.of(7, 30)));
        newTasks.add(sport);

        // 5. Warm-up (child of Sport) — MEDIUM, Mon/Wed/Fri
        Task aufwaermen = new Task("Aufwärmen", 3, 1, Period.WEEK, null, 1, LocalTime.of(7, 30), 10);
        aufwaermen.core.minDuration = 5;
        aufwaermen.prefSlots.clear();
        aufwaermen.prefSlots.add(TaskPrefSlotFactory.create(aufwaermen.core.id, EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), LocalTime.of(7, 30)));
        sport.children.add(aufwaermen);

        // 6. Training (child of Sport) — MEDIUM, prereq: Warm-up
        //    L1: Training
        //    └── L2: Dehnen
        Task training = new Task("Training", 3, 1, Period.WEEK, null, 1, LocalTime.of(7, 30), 45);
        training.core.priority = Priority.MEDIUM;
        training.core.minDuration = 20;
        training.prerequisites.add(new TaskPrerequisite(training.core.id, aufwaermen.core.id));
        training.prefSlots.clear();
        training.prefSlots.add(TaskPrefSlotFactory.create(training.core.id, EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), LocalTime.of(7, 30)));
        sport.children.add(training);

        // 6a. Dehnen (L2 Kind von Training — Sub-Sub-Task)
        t = new Task("Dehnen", 3, 1, Period.WEEK, null, 1, LocalTime.of(7, 30), 10);
        t.core.minDuration = 5;
        t.prefSlots.clear();
        t.prefSlots.add(TaskPrefSlotFactory.create(t.core.id, EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), LocalTime.of(7, 30)));
        training.children.add(t);

        // 7. Work — MEDIUM, Mon-Fri, 180min
        t = new Task("Arbeit", 1, 1, Period.DAY, null, 1, LocalTime.of(9, 0), 180);
        t.core.priority = Priority.MEDIUM;
        t.core.minDuration = 60;
        t.prefSlots.clear();
        t.prefSlots.add(TaskPrefSlotFactory.create(t.core.id, EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), LocalTime.of(9, 0)));
        newTasks.add(t);

        // 8. Lunch break — MEDIUM, all days, 12:00
        t = new Task("Mittagspause", 1, 1, Period.DAY, null, 1, LocalTime.of(12, 0), 30);
        t.core.minDuration = 15;
        newTasks.add(t);

        // 9. Learn Spanish — MEDIUM, all days, 13:30, progress tracking
        t = new Task("Spanisch lernen", 1, 1, Period.DAY, null, 1, LocalTime.of(13, 30), 30);
        t.core.minDuration = 15;
        t.core.progress.unit = "Lektionen";
        t.core.progress.target = 50;
        t.core.progress.current = 12;
        t.core.progress.minPerRep = 1;
        t.core.progress.maxPerRep = 2;
        t.core.progress.totalProgress = 12;
        t.core.progress.totalTime = 360;
        newTasks.add(t);

        // 10. Reading — LOW, all days, 14:30, progress tracking
        t = new Task("Lesen", 1, 1, Period.DAY, null, 1, LocalTime.of(14, 30), 30);
        t.core.priority = Priority.LOW;
        t.core.minDuration = 15;
        t.core.progress.unit = "Seiten";
        t.core.progress.target = 300;
        t.core.progress.current = 85;
        t.core.progress.minPerRep = 10;
        t.core.progress.maxPerRep = 30;
        t.core.progress.totalProgress = 85;
        t.core.progress.totalTime = 510;
        newTasks.add(t);

        // 11. Grocery shopping — MEDIUM, Saturday only
        t = new Task("Einkaufen", 1, 1, Period.WEEK, null, 1, LocalTime.of(10, 0), 60);
        t.core.minDuration = 30;
        t.prefSlots.clear();
        t.prefSlots.add(TaskPrefSlotFactory.create(t.core.id, EnumSet.of(DayOfWeek.SATURDAY), LocalTime.of(10, 0)));
        newTasks.add(t);

        // 12. Clean apartment — LOW, all days
        t = new Task("Wohnung aufräumen", 1, 1, Period.WEEK, null, 1, LocalTime.of(10, 0), 45);
        t.core.priority = Priority.LOW;
        t.core.minDuration = 20;
        newTasks.add(t);

        // 13. Tax return — HIGH, deadline +7 days
        t = new Task("Steuererklärung", 1, 1, Period.MONTH, LocalDate.now().plusDays(7), 1, LocalTime.of(10, 0), 90);
        t.core.priority = Priority.HIGH;
        t.core.minDuration = 30;
        t.core.description = "Belege sortieren und Formulare ausfüllen";
        newTasks.add(t);

        // 14. Dentist appointment — HIGH, deadline +3 days
        t = new Task("Zahnarzttermin", 1, 1, Period.MONTH, LocalDate.now().plusDays(3), 1, LocalTime.of(9, 0), 30);
        t.core.priority = Priority.HIGH;
        t.core.minDuration = 15;
        t.core.description = "Termin beim Zahnarzt";
        newTasks.add(t);

        // --- Evening tasks & edge cases (16:00–21:00) ---

        // 15. Update emergency plan — CRITICAL, overdue deadline, closeOnMiss=false
        t = new Task("Notfallplan aktualisieren", 1, 1, Period.MONTH, LocalDate.now().minusDays(2), 1, LocalTime.of(16, 0), 30);
        t.core.priority = Priority.CRITICAL;
        t.core.closeOnMiss = false;
        t.core.minDuration = 10;
        t.core.description = "Kontaktliste und Eskalationspfade prüfen";
        newTasks.add(t);

        // 16. Evening walk — LOW, Sunday only, cooldown=2
        t = new Task("Abendspaziergang", 1, 1, Period.WEEK, null, 2, LocalTime.of(17, 0), 45);
        t.core.priority = Priority.LOW;
        t.core.minDuration = 20;
        t.prefSlots.clear();
        t.prefSlots.add(TaskPrefSlotFactory.create(t.core.id, EnumSet.of(DayOfWeek.SUNDAY), LocalTime.of(17, 0)));
        newTasks.add(t);

        // 17. Listen to podcast — MEDIUM, 3x daily (repsPerDay=3), spread across day
        t = new Task("Podcast hören", 3, 1, Period.DAY, null, 1, LocalTime.of(8, 0), 20);
        t.core.minDuration = 10;
        t.prefSlots.clear();
        t.prefSlots.add(TaskPrefSlotFactory.create(t.core.id, EnumSet.allOf(DayOfWeek.class), LocalTime.of(8, 0)));
        t.prefSlots.add(TaskPrefSlotFactory.create(t.core.id, EnumSet.allOf(DayOfWeek.class), LocalTime.of(13, 0)));
        t.prefSlots.add(TaskPrefSlotFactory.create(t.core.id, EnumSet.allOf(DayOfWeek.class), LocalTime.of(18, 0)));
        newTasks.add(t);

        // 18. Learn Japanese — MEDIUM, daily, 19:00, progress resetPerRep=true
        t = new Task("Japanisch lernen", 1, 1, Period.DAY, null, 1, LocalTime.of(19, 0), 30);
        t.core.minDuration = 10;
        t.core.progress.unit = "Vokabeln";
        t.core.progress.resetPerRep = true;
        t.core.progress.target = 20;
        t.core.progress.current = 5;
        t.core.progress.minPerRep = 5;
        t.core.progress.maxPerRep = 20;
        t.core.progress.totalProgress = 5;
        t.core.progress.totalTime = 30;
        newTasks.add(t);

        // 19. Write fitness plan — MEDIUM, daily, 19:30, very short (5min)
        t = new Task("Fitnessplan schreiben", 1, 1, Period.DAY, null, 1, LocalTime.of(19, 30), 5);
        t.core.minDuration = 5;
        newTasks.add(t);

        // 20. Abendroutine (Parent) — MEDIUM, daily, 20:00
        //    L0: Abendroutine
        //    ├── L1: Tagebuch schreiben (adaptive)
        //    │   └── L2: Notizen ordnen
        //    └── L1: Hautpflege
        Task abend = new Task("Abendroutine", 1, 1, Period.DAY, null, 1, LocalTime.of(20, 0), 40);
        abend.core.minDuration = 20;
        newTasks.add(abend);

        // 20a. Tagebuch schreiben (L1 Kind von Abendroutine) — adaptive
        Task tagebuch = new Task("Tagebuch schreiben", 1, 1, Period.DAY, null, 1, LocalTime.of(20, 0), 20);
        tagebuch.core.adaptive = true;
        tagebuch.core.minDuration = 10;
        abend.children.add(tagebuch);

        // 20b. Notizen ordnen (L2 Kind von Tagebuch — Sub-Sub-Task)
        t = new Task("Notizen ordnen", 1, 1, Period.DAY, null, 1, LocalTime.of(20, 0), 10);
        t.core.minDuration = 5;
        tagebuch.children.add(t);

        // 20c. Hautpflege (L1 Kind von Abendroutine)
        t = new Task("Hautpflege", 1, 1, Period.DAY, null, 1, LocalTime.of(20, 0), 10);
        t.core.minDuration = 5;
        abend.children.add(t);

        // 21. Weekly report — HIGH, bi-weekly (perPeriod=2), 20:30
        t = new Task("Wochenbericht", 1, 2, Period.WEEK, null, 1, LocalTime.of(20, 30), 45);
        t.core.priority = Priority.HIGH;
        t.core.minDuration = 20;
        t.core.description = "Erledigtes und offene Punkte zusammenfassen";
        newTasks.add(t);

        // 22a. Do laundry — MEDIUM, 2x/week, 16:30
        Task waescheWaschen = new Task("Wäsche waschen", 2, 1, Period.WEEK, null, 1, LocalTime.of(16, 30), 15);
        waescheWaschen.core.minDuration = 10;
        newTasks.add(waescheWaschen);

        // 22b. Hang laundry — MEDIUM, 2x/week, 17:30, prereq: Do laundry (45min gap for wash cycle)
        t = new Task("Wäsche aufhängen", 2, 1, Period.WEEK, null, 1, LocalTime.of(17, 30), 10);
        t.core.minDuration = 5;
        t.core.adaptive = true;
        t.prerequisites.add(new TaskPrerequisite(t.core.id, waescheWaschen.core.id, 45));
        newTasks.add(t);

        return newTasks;
    }
}
