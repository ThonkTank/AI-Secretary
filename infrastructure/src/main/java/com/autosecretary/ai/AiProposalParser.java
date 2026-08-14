package com.autosecretary.ai;

import com.autosecretary.application.ai.BulkChange;
import com.autosecretary.application.ai.BulkChangeProposal;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Validates untrusted model JSON and maps it to immutable, revision-aware proposals. */
final class AiProposalParser {
    BulkChangeProposal parse(
            String raw, List<WorkItem> current, LocalDateTime now) throws Exception {
        JSONObject root = new JSONObject(extractJsonObject(raw));
        JSONArray actions = root.optJSONArray("actions");
        if (actions == null) throw new IllegalArgumentException("Actions-Array fehlt");
        if (actions.length() > 100) throw new IllegalArgumentException("Zu viele KI-Änderungen");
        Map<String, WorkItem> existing = new HashMap<>();
        for (WorkItem item : current) existing.put(item.id(), item);

        List<BulkChange> changes = new ArrayList<>();
        Set<String> changedTargets = new HashSet<>();
        for (int index = 0; index < actions.length(); index++) {
            JSONObject action = actions.getJSONObject(index);
            String type = action.optString("type", "").toLowerCase(java.util.Locale.ROOT);
            if ("delete".equals(type)) {
                String id = action.optString("id");
                WorkItem found = existing.get(id);
                if (found == null) throw new IllegalArgumentException("Unbekannte Lösch-ID");
                if (!changedTargets.add(id)) throw new IllegalArgumentException("Doppelte Änderungs-ID");
                changes.add(new BulkChange(UUID.randomUUID().toString(), BulkChange.Type.DELETE,
                        id, found.revision(), null, "Löschen: " + found.title()));
                continue;
            }
            if (!"add".equals(type) && !"update".equals(type)) {
                throw new IllegalArgumentException("Unbekannte Aktion: " + type);
            }
            WorkItem source = "update".equals(type)
                    ? existing.get(action.optString("id")) : null;
            if ("update".equals(type) && source == null) {
                throw new IllegalArgumentException("Unbekannte Änderungs-ID");
            }
            if (source != null && !changedTargets.add(source.id())) {
                throw new IllegalArgumentException("Doppelte Änderungs-ID");
            }
            WorkItem item = buildItem(source, action, now);
            changes.add(new BulkChange(UUID.randomUUID().toString(),
                    "add".equals(type) ? BulkChange.Type.ADD : BulkChange.Type.UPDATE,
                    item.id(), item.revision(), item,
                    ("add".equals(type) ? "Neu: " : "Ändern: ") + describe(item)));
        }
        return new BulkChangeProposal(root.optString("summary", "Vorgeschlagene Änderungen"), changes);
    }

    static String extractJsonObject(String value) {
        String candidate = stripCodeFence(value);
        int start = candidate.indexOf('{');
        if (start < 0) return candidate;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = start; index < candidate.length(); index++) {
            char current = candidate.charAt(index);
            if (inString) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == '"') inString = false;
                continue;
            }
            if (current == '"') inString = true;
            else if (current == '{') depth++;
            else if (current == '}' && --depth == 0) return candidate.substring(start, index + 1);
        }
        return candidate.substring(start);
    }

    private static WorkItem buildItem(
            WorkItem source, JSONObject action, LocalDateTime now) throws Exception {
        boolean routine = action.has("kind")
                ? "ROUTINE".equals(action.getString("kind")) : source instanceof Routine;
        if (action.has("kind") && !"ROUTINE".equals(action.getString("kind"))
                && !"TASK".equals(action.getString("kind"))) {
            throw new IllegalArgumentException("Unbekannter Work-Item-Typ");
        }
        String id = source == null ? UUID.randomUUID().toString() : source.id();
        String title = action.has("title") ? action.getString("title").trim()
                : source == null ? "" : source.title();
        int duration = action.has("durationMinutes") ? action.getInt("durationMinutes")
                : source == null ? 30 : source.durationMinutes();
        LocalDateTime deadline = action.has("deadline")
                ? action.isNull("deadline") ? null : LocalDateTime.parse(action.getString("deadline"))
                : source == null ? null : source.deadlineAt();
        TimePreference preference = action.has("timePreference")
                ? action.isNull("timePreference") ? null
                : TimePreference.valueOf(action.getString("timePreference"))
                : source == null ? null : source.timePreference();
        boolean flexible = action.has("flexible") ? action.getBoolean("flexible")
                : source == null || source.flexible();
        List<Step> steps = action.has("steps")
                ? parseSteps(action.getJSONArray("steps"), source == null ? List.of() : source.steps())
                : source == null ? List.of() : source.steps();
        LocalDateTime createdAt = source == null ? now : source.createdAt();
        CompletionStats stats = source == null ? CompletionStats.empty() : source.stats();
        long revision = source == null ? 0 : source.revision();
        if (routine) {
            if (!(source instanceof Routine)
                    && (!action.has("cadenceDays") || !action.has("nextDueDate")
                    || action.isNull("nextDueDate"))) {
                throw new IllegalArgumentException(
                        "Neue Routinen benötigen Kadenz und nächste Fälligkeit");
            }
            int cadence = action.has("cadenceDays") ? action.getInt("cadenceDays")
                    : source instanceof Routine value ? value.cadenceDays() : 1;
            LocalDate nextDue = action.has("nextDueDate")
                    ? action.isNull("nextDueDate") ? null
                    : LocalDate.parse(action.getString("nextDueDate"))
                    : source instanceof Routine value ? value.nextDueDate() : now.toLocalDate();
            return new Routine(id, title, duration, deadline, preference, flexible, steps,
                    createdAt, cadence, nextDue, stats, revision);
        }
        if ((action.has("cadenceDays") && action.getInt("cadenceDays") != 0)
                || (action.has("nextDueDate") && !action.isNull("nextDueDate"))) {
            throw new IllegalArgumentException("Aufgaben dürfen keine Routinenfelder enthalten");
        }
        boolean completed = source instanceof Task task && task.completed();
        return new Task(id, title, duration, deadline, preference, flexible, steps,
                createdAt, completed, stats, revision);
    }

    private static List<Step> parseSteps(JSONArray source, List<Step> previousSteps) throws Exception {
        Map<String, Step> previousById = new HashMap<>();
        for (Step previous : previousSteps) previousById.put(previous.id(), previous);
        Set<String> usedIds = new HashSet<>();
        List<Step> result = new ArrayList<>();
        for (int index = 0; index < source.length(); index++) {
            JSONObject encoded = source.getJSONObject(index);
            EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
            JSONArray encodedDays = encoded.optJSONArray("days");
            if (encodedDays != null) {
                for (int day = 0; day < encodedDays.length(); day++) {
                    days.add(DayOfWeek.valueOf(encodedDays.getString(day)));
                }
            }
            String id = encoded.optString("id", "").trim();
            if (id.isEmpty()) id = UUID.randomUUID().toString();
            else if (!previousById.containsKey(id)) {
                throw new IllegalArgumentException("Unbekannte Schritt-ID");
            }
            if (!usedIds.add(id)) throw new IllegalArgumentException("Doppelte Schritt-ID");
            result.add(new Step(id, encoded.getString("title"), days, index));
        }
        return result;
    }

    private static String describe(WorkItem item) {
        if (!(item instanceof Routine routine)) return item.title();
        return item.title() + " · alle " + routine.cadenceDays() + " Tage · "
                + item.steps().size() + " Schritte";
    }

    private static String stripCodeFence(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (!trimmed.startsWith("```")) return trimmed;
        int firstBreak = trimmed.indexOf('\n');
        int closing = trimmed.lastIndexOf("```");
        return firstBreak >= 0 && closing > firstBreak
                ? trimmed.substring(firstBreak + 1, closing).trim() : trimmed;
    }
}
