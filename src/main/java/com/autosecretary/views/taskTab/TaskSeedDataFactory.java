package com.autosecretary.views.taskTab;

import com.autosecretary.constants.Period;
import com.autosecretary.constants.Priority;
import com.autosecretary.database.task.Task;
import com.autosecretary.database.task.TaskPrefSlot;
import com.autosecretary.database.task.TaskPrerequisite;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class TaskSeedDataFactory {

    private TaskSeedDataFactory() {}

    public static List<Task> createDefaultTasks() {
        List<Task> newTasks = new ArrayList<>();
        Task t;
        TaskPrefSlot ps;

        t = new Task("Morgenroutine", 1, 1, Period.DAY, null, 1, LocalTime.of(6, 0), 30);
        t.core.priority = Priority.HIGH;
        t.core.adaptive = true;
        t.core.minDuration = 15;
        t.core.description = "Duschen, Zähneputzen, Anziehen";
        t.core.history.currentStreak = 12;
        t.core.history.completions = 30;
        t.core.history.trackedCompletions = 28;
        t.core.history.totalDuration = 700;
        newTasks.add(t);

        t = new Task("Meditation", 1, 1, Period.DAY, null, 1, LocalTime.of(6, 30), 15);
        t.core.minDuration = 10;
        t.core.history.currentStreak = 7;
        t.core.history.completions = 14;
        newTasks.add(t);

        newTasks.add(new Task("Frühstück", 1, 1, Period.DAY, null, 1, LocalTime.of(7, 0), 20));

        Task sport = new Task("Sport", 3, 1, Period.WEEK, null, 1, LocalTime.of(7, 30), 60);
        sport.core.priority = Priority.HIGH;
        sport.core.minDuration = 30;
        sport.prefSlots.clear();
        ps = new TaskPrefSlot();
        ps.taskId = sport.core.id;
        ps.days = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
        ps.start = LocalTime.of(7, 30);
        sport.prefSlots.add(ps);
        newTasks.add(sport);

        Task aufwaermen = new Task("Aufwärmen", 3, 1, Period.WEEK, null, 1, LocalTime.of(7, 30), 10);
        aufwaermen.core.minDuration = 5;
        aufwaermen.prefSlots.clear();
        ps = new TaskPrefSlot();
        ps.taskId = aufwaermen.core.id;
        ps.days = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
        ps.start = LocalTime.of(7, 30);
        aufwaermen.prefSlots.add(ps);
        sport.children.add(aufwaermen);

        t = new Task("Training", 3, 1, Period.WEEK, null, 1, LocalTime.of(7, 30), 45);
        t.core.priority = Priority.HIGH;
        t.core.minDuration = 20;
        t.prerequisites.add(new TaskPrerequisite(t.core.id, aufwaermen.core.id));
        t.prefSlots.clear();
        ps = new TaskPrefSlot();
        ps.taskId = t.core.id;
        ps.days = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
        ps.start = LocalTime.of(7, 30);
        t.prefSlots.add(ps);
        sport.children.add(t);

        t = new Task("Arbeit", 1, 1, Period.DAY, null, 1, LocalTime.of(9, 0), 120);
        t.core.priority = Priority.HIGH;
        t.core.minDuration = 60;
        t.prefSlots.clear();
        ps = new TaskPrefSlot();
        ps.taskId = t.core.id;
        ps.days = EnumSet.of(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
        );
        ps.start = LocalTime.of(9, 0);
        t.prefSlots.add(ps);
        newTasks.add(t);

        newTasks.add(new Task("Mittagspause", 1, 1, Period.DAY, null, 1, LocalTime.of(12, 0), 30));

        t = new Task("Lesen", 1, 1, Period.DAY, null, 1, LocalTime.of(14, 0), 30);
        t.core.minDuration = 15;
        t.core.progress.unit = "Seiten";
        t.core.progress.target = 300;
        t.core.progress.current = 85;
        t.core.progress.minPerRep = 10;
        t.core.progress.maxPerRep = 30;
        t.core.progress.totalProgress = 85;
        t.core.progress.totalTime = 510;
        newTasks.add(t);

        newTasks.add(new Task("Einkaufen", 1, 1, Period.WEEK, null, 1, LocalTime.of(10, 0), 60));

        t = new Task("Hausarbeit", 1, 1, Period.WEEK, null, 1, LocalTime.of(10, 0), 45);
        t.core.priority = Priority.LOW;
        t.core.minDuration = 20;
        newTasks.add(t);

        t = new Task("Steuererklärung", 1, 1, Period.MONTH, LocalDate.now().plusDays(14), 1, LocalTime.of(10, 0), 90);
        t.core.priority = Priority.HIGH;
        t.core.minDuration = 30;
        t.core.description = "Belege sortieren und Formulare ausfüllen";
        newTasks.add(t);

        t = new Task("Abendspaziergang", 1, 1, Period.DAY, null, 1, LocalTime.of(18, 0), 30);
        t.core.priority = Priority.LOW;
        t.core.minDuration = 15;
        newTasks.add(t);

        t = new Task("Tagebuch", 1, 1, Period.DAY, null, 1, LocalTime.of(21, 0), 15);
        t.core.priority = Priority.LOW;
        t.core.minDuration = 5;
        newTasks.add(t);

        return newTasks;
    }
}
