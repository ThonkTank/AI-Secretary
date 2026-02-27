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
 * Creates default demo tasks for first-run DB seeding.
 * Called by {@code RegenerateScheduleUseCase} when the database is empty.
 */
public final class TaskSeedDataFactory {

    private TaskSeedDataFactory() {}

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
        Task morgen = new Task("Morgenroutine", 1, 1, Period.DAY, null, 1, LocalTime.of(6, 0), 60);
        morgen.core.priority = Priority.HIGH;
        morgen.core.adaptive = true;
        morgen.core.minDuration = 30;
        morgen.core.history.currentStreak = 12;
        morgen.core.history.completions = 30;
        morgen.core.history.trackedCompletions = 28;
        morgen.core.history.totalDuration = 700;
        group.add(morgen);

        Task duschen = new Task("Duschen", 1, 1, Period.DAY, null, 1, LocalTime.of(6, 0), 15);
        duschen.core.minDuration = 5;
        morgen.children.add(duschen);

        Task haare = new Task("Haare föhnen", 1, 1, Period.DAY, null, 1, LocalTime.of(6, 0), 5);
        haare.core.minDuration = 5;
        duschen.children.add(haare);

        Task zaehne = new Task("Zähneputzen", 1, 1, Period.DAY, null, 1, LocalTime.of(6, 0), 5);
        zaehne.core.minDuration = 5;
        morgen.children.add(zaehne);

        Task fruehstueck = new Task("Frühstück", 1, 1, Period.DAY, null, 1, LocalTime.of(6, 0), 25);
        fruehstueck.core.minDuration = 10;
        morgen.children.add(fruehstueck);

        Task abspuelen = new Task("Abspülen", 1, 1, Period.DAY, null, 1, LocalTime.of(6, 0), 10);
        abspuelen.core.minDuration = 5;
        fruehstueck.children.add(abspuelen);

        // Meditation — MEDIUM, all days, 07:00, history
        Task meditation = new Task("Meditation", 1, 1, Period.DAY, null, 1, LocalTime.of(7, 0), 15);
        meditation.core.minDuration = 10;
        meditation.core.history.currentStreak = 7;
        meditation.core.history.completions = 14;
        group.add(meditation);

        return group;
    }

    // -------------------------------------------------------------------------
    // Sport group: Sport (tree with Aufwärmen + Training + Dehnen)
    // -------------------------------------------------------------------------

    private static List<Task> createSportGroup() {
        List<Task> group = new ArrayList<>();
        EnumSet<DayOfWeek> mwf = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);

        // Sport (parent) — MEDIUM, 3/week, Mon/Wed/Fri
        Task sport = new Task("Sport", 3, 1, Period.WEEK, null, 1, LocalTime.of(7, 30), 60);
        sport.core.priority = Priority.MEDIUM;
        sport.core.minDuration = 30;
        sport.prefSlots.clear();
        sport.prefSlots.add(TaskPrefSlotFactory.create(sport.core.id, mwf, LocalTime.of(7, 30)));
        group.add(sport);

        // Aufwärmen (child of Sport)
        Task aufwaermen = new Task("Aufwärmen", 3, 1, Period.WEEK, null, 1, LocalTime.of(7, 30), 10);
        aufwaermen.core.minDuration = 5;
        aufwaermen.prefSlots.clear();
        aufwaermen.prefSlots.add(TaskPrefSlotFactory.create(aufwaermen.core.id, mwf, LocalTime.of(7, 30)));
        sport.children.add(aufwaermen);

        // Training (child of Sport) — prereq: Aufwärmen
        Task training = new Task("Training", 3, 1, Period.WEEK, null, 1, LocalTime.of(7, 30), 45);
        training.core.priority = Priority.MEDIUM;
        training.core.minDuration = 20;
        training.prerequisites.add(new TaskPrerequisite(training.core.id, aufwaermen.core.id));
        training.prefSlots.clear();
        training.prefSlots.add(TaskPrefSlotFactory.create(training.core.id, mwf, LocalTime.of(7, 30)));
        sport.children.add(training);

        // Dehnen (child of Training)
        Task dehnen = new Task("Dehnen", 3, 1, Period.WEEK, null, 1, LocalTime.of(7, 30), 10);
        dehnen.core.minDuration = 5;
        dehnen.prefSlots.clear();
        dehnen.prefSlots.add(TaskPrefSlotFactory.create(dehnen.core.id, mwf, LocalTime.of(7, 30)));
        training.children.add(dehnen);

        return group;
    }

    // -------------------------------------------------------------------------
    // Daytime tasks: Arbeit, Mittagspause, Spanisch, Lesen, Einkaufen, Wohnung
    // -------------------------------------------------------------------------

    private static List<Task> createDaytimeTasks() {
        List<Task> group = new ArrayList<>();

        // Arbeit — MEDIUM, Mon-Fri, 180min
        Task arbeit = new Task("Arbeit", 1, 1, Period.DAY, null, 1, LocalTime.of(9, 0), 180);
        arbeit.core.priority = Priority.MEDIUM;
        arbeit.core.minDuration = 60;
        arbeit.prefSlots.clear();
        arbeit.prefSlots.add(TaskPrefSlotFactory.create(arbeit.core.id,
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                LocalTime.of(9, 0)));
        group.add(arbeit);

        // Mittagspause — MEDIUM, all days, 12:00
        Task mittagspause = new Task("Mittagspause", 1, 1, Period.DAY, null, 1, LocalTime.of(12, 0), 30);
        mittagspause.core.minDuration = 15;
        group.add(mittagspause);

        // Spanisch lernen — MEDIUM, all days, 13:30, progress tracking
        Task spanisch = new Task("Spanisch lernen", 1, 1, Period.DAY, null, 1, LocalTime.of(13, 30), 30);
        spanisch.core.minDuration = 15;
        spanisch.core.progress.unit = "Lektionen";
        spanisch.core.progress.target = 50;
        spanisch.core.progress.current = 12;
        spanisch.core.progress.minPerRep = 1;
        spanisch.core.progress.maxPerRep = 2;
        spanisch.core.progress.totalProgress = 12;
        spanisch.core.progress.totalTime = 360;
        group.add(spanisch);

        // Lesen — LOW, all days, 14:30, progress tracking
        Task lesen = new Task("Lesen", 1, 1, Period.DAY, null, 1, LocalTime.of(14, 30), 30);
        lesen.core.priority = Priority.LOW;
        lesen.core.minDuration = 15;
        lesen.core.progress.unit = "Seiten";
        lesen.core.progress.target = 300;
        lesen.core.progress.current = 85;
        lesen.core.progress.minPerRep = 10;
        lesen.core.progress.maxPerRep = 30;
        lesen.core.progress.totalProgress = 85;
        lesen.core.progress.totalTime = 510;
        group.add(lesen);

        // Einkaufen — MEDIUM, Saturday only
        Task einkaufen = new Task("Einkaufen", 1, 1, Period.WEEK, null, 1, LocalTime.of(10, 0), 60);
        einkaufen.core.minDuration = 30;
        einkaufen.prefSlots.clear();
        einkaufen.prefSlots.add(TaskPrefSlotFactory.create(einkaufen.core.id, EnumSet.of(DayOfWeek.SATURDAY), LocalTime.of(10, 0)));
        group.add(einkaufen);

        // Wohnung aufräumen — LOW, all days
        Task aufraumen = new Task("Wohnung aufräumen", 1, 1, Period.WEEK, null, 1, LocalTime.of(10, 0), 45);
        aufraumen.core.priority = Priority.LOW;
        aufraumen.core.minDuration = 20;
        group.add(aufraumen);

        return group;
    }

    // -------------------------------------------------------------------------
    // Deadline tasks: Steuererklärung, Zahnarzttermin, Notfallplan
    // -------------------------------------------------------------------------

    private static List<Task> createDeadlineTasks() {
        List<Task> group = new ArrayList<>();

        // Steuererklärung — HIGH, deadline +7 days
        Task steuer = new Task("Steuererklärung", 1, 1, Period.MONTH, LocalDate.now().plusDays(7), 1, LocalTime.of(10, 0), 90);
        steuer.core.priority = Priority.HIGH;
        steuer.core.minDuration = 30;
        steuer.core.description = "Belege sortieren und Formulare ausfüllen";
        group.add(steuer);

        // Zahnarzttermin — HIGH, deadline +3 days
        Task zahnarzt = new Task("Zahnarzttermin", 1, 1, Period.MONTH, LocalDate.now().plusDays(3), 1, LocalTime.of(9, 0), 30);
        zahnarzt.core.priority = Priority.HIGH;
        zahnarzt.core.minDuration = 15;
        zahnarzt.core.description = "Termin beim Zahnarzt";
        group.add(zahnarzt);

        // Notfallplan aktualisieren — CRITICAL, overdue deadline, closeOnMiss=false
        Task notfallplan = new Task("Notfallplan aktualisieren", 1, 1, Period.MONTH, LocalDate.now().minusDays(2), 1, LocalTime.of(16, 0), 30);
        notfallplan.core.priority = Priority.CRITICAL;
        notfallplan.core.closeOnMiss = false;
        notfallplan.core.minDuration = 10;
        notfallplan.core.description = "Kontaktliste und Eskalationspfade prüfen";
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
        Task spaziergang = new Task("Abendspaziergang", 1, 1, Period.WEEK, null, 2, LocalTime.of(17, 0), 45);
        spaziergang.core.priority = Priority.LOW;
        spaziergang.core.minDuration = 20;
        spaziergang.prefSlots.clear();
        spaziergang.prefSlots.add(TaskPrefSlotFactory.create(spaziergang.core.id, EnumSet.of(DayOfWeek.SUNDAY), LocalTime.of(17, 0)));
        group.add(spaziergang);

        // Podcast hören — MEDIUM, 3x daily (repsPerDay=3), spread across day
        Task podcast = new Task("Podcast hören", 3, 1, Period.DAY, null, 1, LocalTime.of(8, 0), 20);
        podcast.core.minDuration = 10;
        podcast.prefSlots.clear();
        podcast.prefSlots.add(TaskPrefSlotFactory.create(podcast.core.id, EnumSet.allOf(DayOfWeek.class), LocalTime.of(8, 0)));
        podcast.prefSlots.add(TaskPrefSlotFactory.create(podcast.core.id, EnumSet.allOf(DayOfWeek.class), LocalTime.of(13, 0)));
        podcast.prefSlots.add(TaskPrefSlotFactory.create(podcast.core.id, EnumSet.allOf(DayOfWeek.class), LocalTime.of(18, 0)));
        group.add(podcast);

        // Japanisch lernen — MEDIUM, daily, 19:00, progress resetPerRep=true
        Task japanisch = new Task("Japanisch lernen", 1, 1, Period.DAY, null, 1, LocalTime.of(19, 0), 30);
        japanisch.core.minDuration = 10;
        japanisch.core.progress.unit = "Vokabeln";
        japanisch.core.progress.resetPerRep = true;
        japanisch.core.progress.target = 20;
        japanisch.core.progress.current = 5;
        japanisch.core.progress.minPerRep = 5;
        japanisch.core.progress.maxPerRep = 20;
        japanisch.core.progress.totalProgress = 5;
        japanisch.core.progress.totalTime = 30;
        group.add(japanisch);

        // Fitnessplan schreiben — MEDIUM, daily, 19:30, very short (5min)
        Task fitnessplan = new Task("Fitnessplan schreiben", 1, 1, Period.DAY, null, 1, LocalTime.of(19, 30), 5);
        fitnessplan.core.minDuration = 5;
        group.add(fitnessplan);

        // Abendroutine (Parent) — MEDIUM, daily, 20:00
        //   ├── Tagebuch schreiben (adaptive)
        //   │   └── Notizen ordnen
        //   └── Hautpflege
        Task abend = new Task("Abendroutine", 1, 1, Period.DAY, null, 1, LocalTime.of(20, 0), 40);
        abend.core.minDuration = 20;
        group.add(abend);

        Task tagebuch = new Task("Tagebuch schreiben", 1, 1, Period.DAY, null, 1, LocalTime.of(20, 0), 20);
        tagebuch.core.adaptive = true;
        tagebuch.core.minDuration = 10;
        abend.children.add(tagebuch);

        Task notizen = new Task("Notizen ordnen", 1, 1, Period.DAY, null, 1, LocalTime.of(20, 0), 10);
        notizen.core.minDuration = 5;
        tagebuch.children.add(notizen);

        Task hautpflege = new Task("Hautpflege", 1, 1, Period.DAY, null, 1, LocalTime.of(20, 0), 10);
        hautpflege.core.minDuration = 5;
        abend.children.add(hautpflege);

        // Wochenbericht — HIGH, bi-weekly (perPeriod=2), 20:30
        Task wochenbericht = new Task("Wochenbericht", 1, 2, Period.WEEK, null, 1, LocalTime.of(20, 30), 45);
        wochenbericht.core.priority = Priority.HIGH;
        wochenbericht.core.minDuration = 20;
        wochenbericht.core.description = "Erledigtes und offene Punkte zusammenfassen";
        group.add(wochenbericht);

        // Wäsche-Paar: Wäsche waschen → Wäsche aufhängen (prereq, 45min gap)
        Task waescheWaschen = new Task("Wäsche waschen", 2, 1, Period.WEEK, null, 1, LocalTime.of(16, 30), 15);
        waescheWaschen.core.minDuration = 10;
        group.add(waescheWaschen);

        Task waescheAufhaengen = new Task("Wäsche aufhängen", 2, 1, Period.WEEK, null, 1, LocalTime.of(17, 30), 10);
        waescheAufhaengen.core.minDuration = 5;
        waescheAufhaengen.core.adaptive = true;
        waescheAufhaengen.prerequisites.add(new TaskPrerequisite(waescheAufhaengen.core.id, waescheWaschen.core.id, 45));
        group.add(waescheAufhaengen);

        return group;
    }
}
