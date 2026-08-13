package com.autosecretary.ui.editor;

import com.autosecretary.domain.CompletionStats;
import com.autosecretary.domain.Routine;
import com.autosecretary.domain.Step;
import com.autosecretary.domain.Task;
import com.autosecretary.domain.TimePreference;
import com.autosecretary.domain.WorkItem;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Immutable editor inputs and validation errors, suitable for SavedStateHandle. */
public record ObligationEditorState(
        boolean routine,
        String id,
        String existingId,
        String titleInput,
        String durationInput,
        String deadlineInput,
        String timePreferenceInput,
        boolean flexible,
        List<StepEditorState> steps,
        String createdAtInput,
        boolean completed,
        String cadenceInput,
        String nextDueInput,
        int currentStreak,
        int bestStreak,
        int totalCompletions,
        long revision,
        Map<String, String> errors) implements java.io.Serializable {
    public static final DateTimeFormatter INPUT_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public ObligationEditorState {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        titleInput = value(titleInput);
        durationInput = value(durationInput);
        deadlineInput = value(deadlineInput);
        timePreferenceInput = value(timePreferenceInput);
        steps = List.copyOf(steps == null ? List.of() : steps);
        createdAtInput = value(createdAtInput);
        cadenceInput = value(cadenceInput);
        nextDueInput = value(nextDueInput);
        errors = Map.copyOf(errors == null ? Map.of() : errors);
    }

    public static ObligationEditorState initial(
            boolean routine, WorkItem existing, LocalDateTime now) {
        if (existing == null) {
            return new ObligationEditorState(routine, UUID.randomUUID().toString(), null, "", "30",
                    "", "", true, List.of(), now.toString(), false,
                    routine ? "1" : "", routine ? now.toLocalDate().toString() : "",
                    0, 0, 0, 0, Map.of());
        }
        boolean isRoutine = existing instanceof Routine;
        List<StepEditorState> stepStates = existing.steps().stream()
                .map(StepEditorState::from)
                .collect(java.util.stream.Collectors.toList());
        return new ObligationEditorState(isRoutine, existing.id(), existing.id(), existing.title(),
                Integer.toString(existing.durationMinutes()), formatDeadline(existing.deadlineAt()),
                existing.timePreference() == null ? "" : existing.timePreference().name(),
                existing.flexible(), stepStates, existing.createdAt().toString(),
                existing instanceof Task task && task.completed(),
                isRoutine ? Integer.toString(((Routine) existing).cadenceDays()) : "",
                isRoutine ? ((Routine) existing).nextDueDate().toString() : "",
                existing.stats().currentStreak(), existing.stats().bestStreak(),
                existing.stats().totalCompletions(), existing.revision(), Map.of());
    }

    public ObligationEditorState edit(
            String title,
            String duration,
            String deadline,
            String timePreference,
            boolean flexibleValue,
            String cadence,
            String nextDue,
            List<StepEditorState> stepValues) {
        return new ObligationEditorState(routine, id, existingId, title, duration, deadline,
                timePreference, flexibleValue, stepValues, createdAtInput, completed, cadence,
                nextDue, currentStreak, bestStreak, totalCompletions, revision, Map.of());
    }

    public ObligationEditorState addStep() {
        List<StepEditorState> result = new ArrayList<>(steps);
        result.add(StepEditorState.empty());
        return edit(titleInput, durationInput, deadlineInput, timePreferenceInput, flexible,
                cadenceInput, nextDueInput, result);
    }

    public ObligationEditorState removeStep(String stepId) {
        return edit(titleInput, durationInput, deadlineInput, timePreferenceInput, flexible,
                cadenceInput, nextDueInput,
                steps.stream().filter(step -> !step.id().equals(stepId))
                        .collect(java.util.stream.Collectors.toList()));
    }

    public ObligationEditorState moveStep(String stepId, int delta) {
        List<StepEditorState> result = new ArrayList<>(steps);
        int source = -1;
        for (int index = 0; index < result.size(); index++) {
            if (result.get(index).id().equals(stepId)) source = index;
        }
        if (source < 0) return this;
        int target = Math.max(0, Math.min(result.size() - 1, source + delta));
        if (source != target) {
            StepEditorState value = result.remove(source);
            result.add(target, value);
        }
        return edit(titleInput, durationInput, deadlineInput, timePreferenceInput, flexible,
                cadenceInput, nextDueInput, result);
    }

    public ObligationEditorState validated(LocalDateTime now) {
        Map<String, String> problems = new LinkedHashMap<>();
        if (titleInput.trim().isEmpty()) problems.put("title", "Titel fehlt");
        parseInt(durationInput, 5, 480, "Dauer", "duration", problems);
        if (routine) {
            parseInt(cadenceInput, 1, 365, "Rhythmus", "cadence", problems);
            try { LocalDate.parse(nextDueInput.trim()); }
            catch (RuntimeException error) {
                problems.put("nextDue", "Datum bitte als JJJJ-MM-TT eingeben");
            }
        } else if (!deadlineInput.trim().isEmpty()) {
            try {
                LocalDateTime deadline = parseDeadline(deadlineInput);
                if (deadline.isBefore(now)) problems.put("deadline",
                        "Die Deadline liegt in der Vergangenheit");
            } catch (RuntimeException error) {
                problems.put("deadline", "Deadline: JJJJ-MM-TT oder JJJJ-MM-TT HH:mm");
            }
        }
        List<StepEditorState> checkedSteps = steps.stream()
                .map(StepEditorState::validated)
                .collect(java.util.stream.Collectors.toList());
        if (checkedSteps.stream().anyMatch(step -> !step.valid())) {
            problems.put("steps", "Bitte fehlerhafte Schritte korrigieren");
        }
        return new ObligationEditorState(routine, id, existingId, titleInput, durationInput,
                deadlineInput, timePreferenceInput, flexible, checkedSteps, createdAtInput,
                completed, cadenceInput, nextDueInput, currentStreak, bestStreak,
                totalCompletions, revision, problems);
    }

    public boolean valid() {
        return errors.isEmpty() && steps.stream().allMatch(StepEditorState::valid);
    }

    public WorkItem toWorkItem() {
        if (!valid()) throw new IllegalStateException("Editorzustand wurde nicht validiert");
        List<Step> domainSteps = new ArrayList<>();
        for (int index = 0; index < steps.size(); index++) {
            domainSteps.add(steps.get(index).toStep(index));
        }
        CompletionStats stats = new CompletionStats(
                currentStreak, bestStreak, totalCompletions);
        LocalDateTime createdAt = LocalDateTime.parse(createdAtInput);
        TimePreference preference = timePreferenceInput.isBlank()
                ? null : TimePreference.valueOf(timePreferenceInput);
        if (routine) {
            return new Routine(id, titleInput.trim(), Integer.parseInt(durationInput.trim()), null,
                    preference, flexible, domainSteps, createdAt,
                    Integer.parseInt(cadenceInput.trim()), LocalDate.parse(nextDueInput.trim()),
                    stats, revision);
        }
        LocalDateTime deadline = deadlineInput.isBlank() ? null : parseDeadline(deadlineInput);
        return new Task(id, titleInput.trim(), Integer.parseInt(durationInput.trim()), deadline,
                preference, flexible, domainSteps, createdAt, completed, stats, revision);
    }

    private static int parseInt(
            String raw, int min, int max, String label, String key, Map<String, String> errors) {
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (RuntimeException error) {
            errors.put(key, label + ": " + min + "–" + max);
            return min;
        }
    }

    private static LocalDateTime parseDeadline(String raw) {
        String value = raw.trim();
        if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return LocalDate.parse(value).atTime(23, 59);
        }
        return LocalDateTime.parse(value, INPUT_DATE_TIME);
    }

    private static String formatDeadline(LocalDateTime value) {
        if (value == null) return "";
        if (value.toLocalTime().equals(java.time.LocalTime.of(23, 59))) {
            return value.toLocalDate().toString();
        }
        return value.format(INPUT_DATE_TIME);
    }

    private static String value(String raw) { return raw == null ? "" : raw; }
}
