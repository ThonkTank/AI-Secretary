package com.autosecretary.ai;

import com.autosecretary.application.ai.BulkChange;
import com.autosecretary.application.ai.BulkChangeProposal;
import com.autosecretary.domain.CompletionStats;
import com.autosecretary.domain.Routine;
import com.autosecretary.domain.Step;
import com.autosecretary.domain.Task;
import com.autosecretary.domain.TimePreference;
import com.autosecretary.domain.WorkItem;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compiles a deliberately small set of explicit German commands into typed proposals.
 * It never guesses: unsupported wording returns {@code null} and remains model/parser work.
 */
final class GermanCommandCompiler {
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final Pattern TWO_TASKS = Pattern.compile(
            "^Lege zwei Aufgaben an: (.+?) mit (\\d+) Minuten und (.+?) mit (\\d+) Minuten\\.?$",
            FLAGS);
    private static final Pattern ROUTINE = Pattern.compile(
            "^Lege eine Routine (.+?) alle (\\d+) Tage mit (\\d+) Minuten an, "
                    + "nächste Fälligkeit (\\d{4}-\\d{2}-\\d{2})\\.?$", FLAGS);
    private static final Pattern TASK = Pattern.compile(
            "^Lege eine (flexible )?Aufgabe (.+?) mit (\\d+) Minuten"
                    + "(?: und Deadline (\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}))?"
                    + "(?: am (Morgen|Mittag|Abend))? an\\.?$", FLAGS);
    private static final Pattern RENAME_ITEM = Pattern.compile(
            "^Benenne (?:die vorhandene Aufgabe )?(.+?) in (.+?) um(?: und setze die Dauer auf "
                    + "(\\d+) Minuten)?\\.?$", FLAGS);
    private static final Pattern DURATION = Pattern.compile(
            "^Setze die Dauer der vorhandenen Aufgabe (.+?) auf (\\d+) Minuten\\.?$", FLAGS);
    private static final Pattern PREFERENCE = Pattern.compile(
            "^Plane die vorhandene Aufgabe (.+?) bevorzugt am (Morgen|Mittag|Abend)\\.?$", FLAGS);
    private static final Pattern FLEXIBLE_OFF = Pattern.compile(
            "^Schalte bei der vorhandenen Aufgabe (.+?) flexibles Lernen aus\\.?$", FLAGS);
    private static final Pattern ADD_STEP = Pattern.compile(
            "^Ergänze bei (.+?) einen neuen Schritt (.+?)\\.?$", FLAGS);
    private static final Pattern RENAME_STEP = Pattern.compile(
            "^Benenne den vorhandenen Schritt (.+?) bei (.+?) in (.+?) um"
                    + "(?: und behalte seine ID)?\\.?$", FLAGS);
    private static final Pattern FIRST_STEP = Pattern.compile(
            "^Setze bei (.+?) den vorhandenen Schritt (.+?) an die erste Position"
                    + "(?: und behalte alle Schritt-IDs)?\\.?$", FLAGS);
    private static final Pattern DELETE = Pattern.compile(
            "^Schlage vor, die vorhandene Aufgabe (.+?) zu löschen\\.?$", FLAGS);
    private static final Pattern CADENCE = Pattern.compile(
            "^Ändere die vorhandene Routine (.+?) auf alle (\\d+) Tage\\.?$", FLAGS);
    private static final Pattern DUE = Pattern.compile(
            "^Setze die nächste Fälligkeit der vorhandenen Routine (.+?) auf "
                    + "(\\d{4}-\\d{2}-\\d{2})\\.?$", FLAGS);

    BulkChangeProposal compile(
            String instruction,
            List<WorkItem> current,
            LocalDateTime now) {
        if (instruction == null) return null;
        String command = instruction.trim();
        if (command.isEmpty()) return null;
        if (containsIgnoreCase(command, "nur dann etwas, wenn du sicher bist")
                && containsIgnoreCase(command, "irgendwie")) {
            return new BulkChangeProposal("Keine eindeutige Änderung erkannt", List.of());
        }

        Matcher match = TWO_TASKS.matcher(command);
        if (match.matches()) {
            Task first = newTask(match.group(1), number(match, 2), null, null, true, now);
            Task second = newTask(match.group(3), number(match, 4), null, null, true, now);
            return proposal("Zwei Aufgaben anlegen", add(first), add(second));
        }
        match = ROUTINE.matcher(command);
        if (match.matches()) {
            Routine routine = new Routine(UUID.randomUUID().toString(), clean(match.group(1)),
                    number(match, 3), null, null, true, List.of(), now,
                    number(match, 2), LocalDate.parse(match.group(4)),
                    CompletionStats.empty(), 0);
            return proposal("Routine anlegen", add(routine));
        }
        match = TASK.matcher(command);
        if (match.matches()) {
            TimePreference preference = preference(match.group(5));
            LocalDateTime deadline = match.group(4) == null
                    ? null : LocalDateTime.parse(match.group(4));
            Task task = newTask(match.group(2), number(match, 3), deadline, preference,
                    true, now);
            return proposal("Aufgabe anlegen", add(task));
        }

        match = RENAME_STEP.matcher(command);
        if (match.matches()) {
            WorkItem item = uniqueItem(current, match.group(2));
            Step target = uniqueStep(item, match.group(1));
            String newTitle = clean(match.group(3));
            List<Step> steps = item.steps().stream().map(step -> step.id().equals(target.id())
                    ? new Step(step.id(), newTitle, step.days(), step.position())
                    : step).collect(Collectors.toList());
            return proposal("Schritt umbenennen", update(copy(item, item.title(),
                    item.durationMinutes(), item.timePreference(), item.flexible(), steps,
                    item instanceof Routine routine ? routine.cadenceDays() : 0,
                    item instanceof Routine routine ? routine.nextDueDate() : null)));
        }
        match = FIRST_STEP.matcher(command);
        if (match.matches()) {
            WorkItem item = uniqueItem(current, match.group(1));
            Step target = uniqueStep(item, match.group(2));
            List<Step> ordered = new ArrayList<>(item.steps());
            ordered.removeIf(step -> step.id().equals(target.id()));
            ordered.add(0, target);
            List<Step> normalized = new ArrayList<>();
            for (int index = 0; index < ordered.size(); index++) {
                Step step = ordered.get(index);
                normalized.add(new Step(step.id(), step.title(), step.days(), index));
            }
            return proposal("Schritt verschieben", update(copy(item, item.title(),
                    item.durationMinutes(), item.timePreference(), item.flexible(), normalized,
                    item instanceof Routine routine ? routine.cadenceDays() : 0,
                    item instanceof Routine routine ? routine.nextDueDate() : null)));
        }
        match = ADD_STEP.matcher(command);
        if (match.matches()) {
            WorkItem item = uniqueItem(current, match.group(1));
            List<Step> steps = new ArrayList<>(item.steps());
            steps.add(new Step(UUID.randomUUID().toString(), clean(match.group(2)),
                    java.util.Set.of(), steps.size()));
            return proposal("Schritt ergänzen", update(copy(item, item.title(),
                    item.durationMinutes(), item.timePreference(), item.flexible(), steps,
                    item instanceof Routine routine ? routine.cadenceDays() : 0,
                    item instanceof Routine routine ? routine.nextDueDate() : null)));
        }

        match = RENAME_ITEM.matcher(command);
        if (match.matches()) {
            WorkItem item = uniqueItem(current, match.group(1));
            int duration = match.group(3) == null ? item.durationMinutes() : number(match, 3);
            return proposal("Aufgabe ändern", update(copy(item, clean(match.group(2)), duration,
                    item.timePreference(), item.flexible(), item.steps(),
                    item instanceof Routine routine ? routine.cadenceDays() : 0,
                    item instanceof Routine routine ? routine.nextDueDate() : null)));
        }
        match = DURATION.matcher(command);
        if (match.matches()) {
            WorkItem item = uniqueItem(current, match.group(1));
            return proposal("Dauer ändern", update(copy(item, item.title(), number(match, 2),
                    item.timePreference(), item.flexible(), item.steps(),
                    item instanceof Routine routine ? routine.cadenceDays() : 0,
                    item instanceof Routine routine ? routine.nextDueDate() : null)));
        }
        match = PREFERENCE.matcher(command);
        if (match.matches()) {
            WorkItem item = uniqueItem(current, match.group(1));
            return proposal("Zeitpräferenz ändern", update(copy(item, item.title(),
                    item.durationMinutes(), preference(match.group(2)), item.flexible(), item.steps(),
                    item instanceof Routine routine ? routine.cadenceDays() : 0,
                    item instanceof Routine routine ? routine.nextDueDate() : null)));
        }
        match = FLEXIBLE_OFF.matcher(command);
        if (match.matches()) {
            WorkItem item = uniqueItem(current, match.group(1));
            return proposal("Flexibilität ändern", update(copy(item, item.title(),
                    item.durationMinutes(), item.timePreference(), false, item.steps(),
                    item instanceof Routine routine ? routine.cadenceDays() : 0,
                    item instanceof Routine routine ? routine.nextDueDate() : null)));
        }
        match = DELETE.matcher(command);
        if (match.matches()) {
            return proposal("Aufgabe löschen", delete(uniqueItem(current, match.group(1))));
        }
        match = CADENCE.matcher(command);
        if (match.matches()) {
            WorkItem found = uniqueItem(current, match.group(1));
            if (!(found instanceof Routine routine)) throw new IllegalArgumentException(
                    "Die genannte Arbeit ist keine Routine");
            return proposal("Kadenz ändern", update(copy(routine, routine.title(),
                    routine.durationMinutes(), routine.timePreference(), routine.flexible(),
                    routine.steps(), number(match, 2), routine.nextDueDate())));
        }
        match = DUE.matcher(command);
        if (match.matches()) {
            WorkItem found = uniqueItem(current, match.group(1));
            if (!(found instanceof Routine routine)) throw new IllegalArgumentException(
                    "Die genannte Arbeit ist keine Routine");
            return proposal("Fälligkeit ändern", update(copy(routine, routine.title(),
                    routine.durationMinutes(), routine.timePreference(), routine.flexible(),
                    routine.steps(), routine.cadenceDays(), LocalDate.parse(match.group(2)))));
        }
        return null;
    }

    private static WorkItem uniqueItem(List<WorkItem> current, String title) {
        List<WorkItem> matches = current.stream()
                .filter(item -> item.title().equalsIgnoreCase(clean(title)))
                .collect(Collectors.toList());
        if (matches.size() != 1) throw new IllegalArgumentException(
                "Arbeitstitel ist nicht eindeutig: " + clean(title));
        return matches.get(0);
    }

    private static Step uniqueStep(WorkItem item, String title) {
        List<Step> matches = item.steps().stream()
                .filter(step -> step.title().equalsIgnoreCase(clean(title)))
                .collect(Collectors.toList());
        if (matches.size() != 1) throw new IllegalArgumentException(
                "Schritttitel ist nicht eindeutig: " + clean(title));
        return matches.get(0);
    }

    private static Task newTask(String title, int duration, LocalDateTime deadline,
            TimePreference preference, boolean flexible, LocalDateTime now) {
        return new Task(UUID.randomUUID().toString(), clean(title), duration, deadline,
                preference, flexible, List.of(), now, false, CompletionStats.empty(), 0);
    }

    private static WorkItem copy(WorkItem source, String title, int duration,
            TimePreference preference, boolean flexible, List<Step> steps,
            int cadenceDays, LocalDate due) {
        if (source instanceof Routine routine) {
            return new Routine(routine.id(), title, duration, null, preference, flexible, steps,
                    routine.createdAt(), cadenceDays, due, routine.stats(), routine.revision());
        }
        Task task = (Task) source;
        return new Task(task.id(), title, duration, task.deadlineAt(), preference, flexible, steps,
                task.createdAt(), task.completed(), task.stats(), task.revision());
    }

    private static BulkChange add(WorkItem item) {
        return new BulkChange(UUID.randomUUID().toString(), BulkChange.Type.ADD,
                item.id(), 0, item, "Neu: " + item.title());
    }

    private static BulkChange update(WorkItem item) {
        return new BulkChange(UUID.randomUUID().toString(), BulkChange.Type.UPDATE,
                item.id(), item.revision(), item, "Ändern: " + item.title());
    }

    private static BulkChange delete(WorkItem item) {
        return new BulkChange(UUID.randomUUID().toString(), BulkChange.Type.DELETE,
                item.id(), item.revision(), null, "Löschen: " + item.title());
    }

    private static BulkChangeProposal proposal(String summary, BulkChange... changes) {
        return new BulkChangeProposal(summary, List.of(changes));
    }

    private static int number(Matcher match, int group) {
        return Integer.parseInt(match.group(group));
    }

    private static TimePreference preference(String value) {
        if (value == null) return null;
        return switch (value.toLowerCase(Locale.GERMAN)) {
            case "morgen" -> TimePreference.MORNING;
            case "mittag" -> TimePreference.MIDDAY;
            case "abend" -> TimePreference.EVENING;
            default -> throw new IllegalArgumentException("Unbekannte Zeitpräferenz");
        };
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("[.]$", "").trim();
    }

    private static boolean containsIgnoreCase(String value, String part) {
        return value.toLowerCase(Locale.GERMAN).contains(part.toLowerCase(Locale.GERMAN));
    }
}
