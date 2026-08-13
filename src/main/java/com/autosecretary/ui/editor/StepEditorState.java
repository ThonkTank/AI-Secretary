package com.autosecretary.ui.editor;

import com.autosecretary.domain.Step;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Immutable raw editor row. The id survives inserts, renames and moves. */
public record StepEditorState(
        String id,
        String titleInput,
        String daysInput,
        String titleError,
        String daysError) implements java.io.Serializable {
    private static final Map<String, DayOfWeek> DAYS = new LinkedHashMap<>();
    private static final Map<DayOfWeek, String> LABELS = new LinkedHashMap<>();

    static {
        String[] labels = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};
        DayOfWeek[] values = DayOfWeek.values();
        for (int index = 0; index < values.length; index++) {
            DAYS.put(labels[index].toLowerCase(Locale.GERMAN), values[index]);
            DAYS.put(values[index].name().toLowerCase(Locale.ROOT), values[index]);
            LABELS.put(values[index], labels[index]);
        }
    }

    public StepEditorState {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        titleInput = titleInput == null ? "" : titleInput;
        daysInput = daysInput == null ? "" : daysInput;
    }

    public static StepEditorState empty() {
        return new StepEditorState(UUID.randomUUID().toString(), "", "", null, null);
    }

    public static StepEditorState from(Step step) {
        String days = step.days().stream().sorted().map(LABELS::get)
                .reduce((left, right) -> left + "," + right).orElse("");
        return new StepEditorState(step.id(), step.title(), days, null, null);
    }

    public StepEditorState edit(String title, String days) {
        return new StepEditorState(id, title, days, null, null);
    }

    public StepEditorState validated() {
        String titleProblem = titleInput.trim().isEmpty() ? "Schritttitel fehlt" : null;
        String daysProblem = null;
        try { parseDays(daysInput); }
        catch (IllegalArgumentException error) { daysProblem = error.getMessage(); }
        return new StepEditorState(id, titleInput, daysInput, titleProblem, daysProblem);
    }

    public boolean valid() { return titleError == null && daysError == null; }

    public Step toStep(int position) {
        return new Step(id, titleInput.trim(), parseDays(daysInput), position);
    }

    private static Set<DayOfWeek> parseDays(String raw) {
        if (raw == null || raw.trim().isEmpty()) return Set.of();
        EnumSet<DayOfWeek> result = EnumSet.noneOf(DayOfWeek.class);
        for (String token : raw.split(",")) {
            String normalized = token.trim().toLowerCase(Locale.GERMAN);
            DayOfWeek day = DAYS.get(normalized);
            if (day == null) throw new IllegalArgumentException(
                    "Wochentage: Mo,Di,Mi,Do,Fr,Sa,So");
            result.add(day);
        }
        return result;
    }
}
