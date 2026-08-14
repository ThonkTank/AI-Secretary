package com.autosecretary.data;

import com.autosecretary.domain.CompletionStats;
import com.autosecretary.domain.Routine;
import com.autosecretary.domain.Step;
import com.autosecretary.domain.Task;
import com.autosecretary.domain.TimePreference;
import com.autosecretary.domain.WorkItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Versioned JSON is used only for the bounded undo journal, never as queryable core data. */
final class WorkItemJson {
    JSONObject encode(WorkItem item) throws Exception {
        JSONObject result = new JSONObject()
                .put("schema", 1)
                .put("id", item.id())
                .put("kind", item instanceof Routine ? "ROUTINE" : "TASK")
                .put("title", item.title())
                .put("durationMinutes", item.durationMinutes())
                .put("deadlineAt", item.deadlineAt() == null ? JSONObject.NULL : item.deadlineAt())
                .put("timePreference", item.timePreference() == null
                        ? JSONObject.NULL : item.timePreference().name())
                .put("flexible", item.flexible())
                .put("createdAt", item.createdAt())
                .put("currentStreak", item.stats().currentStreak())
                .put("bestStreak", item.stats().bestStreak())
                .put("totalCompletions", item.stats().totalCompletions())
                .put("revision", item.revision());
        if (item instanceof Task task) {
            result.put("completed", task.completed());
        } else {
            Routine routine = (Routine) item;
            result.put("cadenceDays", routine.cadenceDays());
            result.put("nextDueDate", routine.nextDueDate());
        }
        JSONArray steps = new JSONArray();
        for (Step step : item.steps()) {
            JSONArray days = new JSONArray();
            for (DayOfWeek day : step.days()) days.put(day.name());
            steps.put(new JSONObject().put("id", step.id()).put("title", step.title())
                    .put("position", step.position()).put("days", days));
        }
        result.put("steps", steps);
        return result;
    }

    WorkItem decode(JSONObject source) throws Exception {
        if (source.optInt("schema", -1) != 1) {
            throw new IllegalArgumentException("Unbekannte Undo-Schemaversion");
        }
        List<Step> steps = new ArrayList<>();
        JSONArray encodedSteps = source.getJSONArray("steps");
        for (int index = 0; index < encodedSteps.length(); index++) {
            JSONObject encoded = encodedSteps.getJSONObject(index);
            EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
            JSONArray encodedDays = encoded.getJSONArray("days");
            for (int day = 0; day < encodedDays.length(); day++) {
                days.add(DayOfWeek.valueOf(encodedDays.getString(day)));
            }
            steps.add(new Step(encoded.getString("id"), encoded.getString("title"), days,
                    encoded.getInt("position")));
        }
        CompletionStats stats = new CompletionStats(source.getInt("currentStreak"),
                source.getInt("bestStreak"), source.getInt("totalCompletions"));
        LocalDateTime deadline = source.isNull("deadlineAt") ? null
                : LocalDateTime.parse(source.getString("deadlineAt"));
        TimePreference preference = source.isNull("timePreference") ? null
                : TimePreference.valueOf(source.getString("timePreference"));
        if ("ROUTINE".equals(source.getString("kind"))) {
            return new Routine(source.getString("id"), source.getString("title"),
                    source.getInt("durationMinutes"), deadline, preference,
                    source.getBoolean("flexible"), steps,
                    LocalDateTime.parse(source.getString("createdAt")),
                    source.getInt("cadenceDays"), LocalDate.parse(source.getString("nextDueDate")),
                    stats, source.getLong("revision"));
        }
        return new Task(source.getString("id"), source.getString("title"),
                source.getInt("durationMinutes"), deadline, preference,
                source.getBoolean("flexible"), steps,
                LocalDateTime.parse(source.getString("createdAt")),
                source.getBoolean("completed"), stats, source.getLong("revision"));
    }
}
