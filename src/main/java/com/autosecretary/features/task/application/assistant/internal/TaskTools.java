package com.autosecretary.features.task.application.assistant.internal;

import static com.autosecretary.features.task.application.assistant.internal.AssistantJson.asString;
import static com.autosecretary.features.task.application.assistant.internal.AssistantJson.optBool;
import static com.autosecretary.features.task.application.assistant.internal.AssistantJson.optDate;
import static com.autosecretary.features.task.application.assistant.internal.AssistantJson.optInt;
import static com.autosecretary.features.task.application.assistant.internal.AssistantJson.optString;
import static com.autosecretary.features.task.application.assistant.internal.AssistantJson.optTime;

import com.autosecretary.features.task.application.assistant.AssistantProposals.PendingProposal;
import com.autosecretary.features.task.application.assistant.AssistantProposals.TaskChangesProposal;
import com.autosecretary.features.task.data.TaskCategoryDao;
import com.autosecretary.features.task.data.TaskDao;
import com.autosecretary.features.task.domain.assistant.ChangeOp;
import com.autosecretary.features.task.domain.assistant.TaskAssistantProposal;
import com.autosecretary.features.task.domain.assistant.TaskSnapshot;
import com.autosecretary.features.task.domain.model.Task;
import com.autosecretary.features.task.domain.model.TaskCategory;
import com.autosecretary.features.task.domain.model.TaskCore;
import com.autosecretary.features.task.domain.model.TaskPrefSlot;
import com.autosecretary.features.task.domain.model.TaskSlot;
import com.autosecretary.shared.ClaudeApiException;
import com.autosecretary.shared.Period;
import com.autosecretary.shared.Priority;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The assistant's task tools: {@code get_tasks} (serialize all categories + tasks, optionally
 * filtered) and {@code propose_task_changes} (parse + validate a category/task change set into a
 * parked {@link TaskChangesProposal}). Task data is read directly through the DAOs — task has no
 * repository interface; its Room POJOs are the domain model. Each tool's wire schema sits directly
 * above the parser it must match.
 */
public final class TaskTools {

    private static final int UPCOMING_SLOT_DAYS = 7;

    private static final String GET_TASKS_DESCRIPTION =
            "Liefert alle Kategorien und Tasks aus der lokalen Datenbank – vollständig inklusive "
            + "Wiederholung (repetition: reps, perPeriod, periodUnit, periodCompletions, carryoverDebt), "
            + "Fortschritt, Streak/History, prefSlots und den nächsten geplanten Slots. Nutze dieses Tool "
            + "für JEDE Frage zu bestehenden Tasks (z. B. \"Welche Morgenroutine-Tasks habe ich und wie "
            + "oft werden sie wiederholt?\"). Optional per titleContains/categoryName vorfiltern; die "
            + "semantische Zuordnung machst du selbst.";
    private static final String GET_TASKS_SCHEMA =
            "{\"type\":\"object\",\"properties\":{"
            + "\"titleContains\":{\"type\":\"string\",\"description\":\"Nur Tasks, deren Titel diesen Text enthält\"},"
            + "\"categoryName\":{\"type\":\"string\",\"description\":\"Nur Tasks dieser Kategorie\"}}}";

    private static final String PROPOSE_TASK_CHANGES_DESCRIPTION =
            "Schlägt Änderungen an Kategorien und Tasks vor (wird dem Nutzer als Diff angezeigt, erst "
            + "nach Bestätigung geschrieben). op ist CREATE/UPDATE/DELETE. Bei UPDATE/DELETE eine echte id "
            + "aus get_tasks verwenden; bei CREATE keine id. Bei UPDATE nur die zu ändernden Felder "
            + "angeben – weggelassene Felder bleiben unverändert. "
            + "priority: LOW/MEDIUM/HIGH/CRITICAL. leisure (bool). "
            + "Wiederholung über repetition:{reps, perPeriod, periodUnit DAY/WEEK/MONTH, completeFirst}. "
            + "Beispiel \"3× pro Woche\" = reps:3, perPeriod:1, periodUnit:WEEK; \"5× pro 2 Wochen\" = "
            + "reps:5, perPeriod:2, periodUnit:WEEK. Für eine einmalige Task ohne Wiederholung reps:0. "
            + "schedulingType: TASK (frei geplant) oder TERMIN (fester Termin – benötigt fixedDate und "
            + "fixedStart, optional fixedEnd oder fixedDuration in Minuten). "
            + "Daten als YYYY-MM-DD: startDate (frühester Planungstag), deadline (Fälligkeit). "
            + "closeOnMiss (bool): bei true wird die Task nach Ablauf/Deadline automatisch geschlossen. "
            + "prefSlots: bevorzugte Zeiten als Liste {days:[1-7, Mo=1, leer=jeder Tag], start:\"HH:MM\"}. "
            + "adaptive (bool) lernt Zeiten aus echten Abschlüssen. minDuration/maxDuration in Minuten, "
            + "cooldown in Tagen. progress:{unit, target, current, resetPerRep, minPerRep, maxPerRep} für "
            + "Fortschritts-Tracking. budgetRequiredCents + budgetAccountId/budgetCategoryId verknüpfen eine "
            + "Ausgabe. Bei langen Listen alle Tasks in EINEM Aufruf.";
    private static final String PROPOSE_TASK_CHANGES_SCHEMA =
            "{\"type\":\"object\",\"properties\":{"
            + "\"categories\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{"
            + "\"op\":{\"type\":\"string\"},\"id\":{\"type\":\"string\"},\"name\":{\"type\":\"string\"},"
            + "\"icon\":{\"type\":\"string\"},\"colorHex\":{\"type\":\"string\"}}}},"
            + "\"tasks\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{"
            + "\"op\":{\"type\":\"string\"},\"id\":{\"type\":\"string\"},\"title\":{\"type\":\"string\"},"
            + "\"description\":{\"type\":\"string\"},\"categoryId\":{\"type\":\"string\"},"
            + "\"priority\":{\"type\":\"string\"},\"leisure\":{\"type\":\"boolean\"},"
            + "\"adaptive\":{\"type\":\"boolean\"},\"minDuration\":{\"type\":\"integer\"},"
            + "\"maxDuration\":{\"type\":\"integer\"},\"cooldown\":{\"type\":\"integer\"},"
            + "\"schedulingType\":{\"type\":\"string\"},\"startDate\":{\"type\":\"string\"},"
            + "\"deadline\":{\"type\":\"string\"},\"closeOnMiss\":{\"type\":\"boolean\"},"
            + "\"fixedDate\":{\"type\":\"string\"},\"fixedStart\":{\"type\":\"string\"},"
            + "\"fixedEnd\":{\"type\":\"string\"},\"fixedDuration\":{\"type\":\"integer\"},"
            + "\"budgetRequiredCents\":{\"type\":\"integer\"},\"budgetAccountId\":{\"type\":\"string\"},"
            + "\"budgetCategoryId\":{\"type\":\"string\"},"
            + "\"repetition\":{\"type\":\"object\",\"properties\":{\"reps\":{\"type\":\"integer\"},"
            + "\"perPeriod\":{\"type\":\"integer\"},\"periodUnit\":{\"type\":\"string\"},"
            + "\"completeFirst\":{\"type\":\"boolean\"}}},"
            + "\"progress\":{\"type\":\"object\",\"properties\":{\"unit\":{\"type\":\"string\"},"
            + "\"target\":{\"type\":\"integer\"},\"current\":{\"type\":\"integer\"},"
            + "\"resetPerRep\":{\"type\":\"boolean\"},\"minPerRep\":{\"type\":\"integer\"},"
            + "\"maxPerRep\":{\"type\":\"integer\"}}},"
            + "\"prefSlots\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{"
            + "\"days\":{\"type\":\"array\",\"items\":{\"type\":\"integer\"}},"
            + "\"start\":{\"type\":\"string\"}}}}}}}}}";

    private final TaskDao taskDao;
    private final TaskCategoryDao categoryDao;
    private final DbCalls db;

    public TaskTools(TaskDao taskDao, TaskCategoryDao categoryDao, DbCalls db) {
        this.taskDao = taskDao;
        this.categoryDao = categoryDao;
        this.db = db;
    }

    public List<AssistantTool> tools() {
        return List.of(
                AssistantTool.read("get_tasks", GET_TASKS_DESCRIPTION, GET_TASKS_SCHEMA,
                        "Prüfe vorhandene Tasks…", this::getTasks),
                AssistantTool.proposal("propose_task_changes", PROPOSE_TASK_CHANGES_DESCRIPTION,
                        PROPOSE_TASK_CHANGES_SCHEMA, AssistantTool.PROGRESS_PROPOSAL, this::proposeTaskChanges));
    }

    // ---- get_tasks ------------------------------------------------------------

    private String getTasks(JSONObject input) {
        String titleContains = optString(input, "titleContains");
        String categoryName = optString(input, "categoryName");
        List<Task> tasks = db.call(taskDao::readAll);
        List<TaskCategory> categories = db.call(categoryDao::readAll);
        try {
            Map<String, String> categoryNameById = new HashMap<>();
            JSONArray categoriesJson = new JSONArray();
            for (TaskCategory category : categories) {
                categoryNameById.put(category.id, category.name);
                categoriesJson.put(new JSONObject().put("id", category.id).put("name", category.name));
            }
            String filterCategoryId = categoryName == null ? null : categoryIdForName(categories, categoryName);
            if (categoryName != null && filterCategoryId == null) {
                // The requested category name matches no category → no tasks (not "all tasks").
                return new JSONObject().put("categories", categoriesJson)
                        .put("tasks", new JSONArray()).toString();
            }
            String titleNeedle = titleContains == null ? null : titleContains.toLowerCase(Locale.ROOT);

            JSONArray tasksJson = new JSONArray();
            LocalDate today = LocalDate.now();
            for (Task task : tasks) {
                if (filterCategoryId != null && !filterCategoryId.equals(task.core.categoryId)) {
                    continue;
                }
                if (titleNeedle != null && (task.core.title == null
                        || !task.core.title.toLowerCase(Locale.ROOT).contains(titleNeedle))) {
                    continue;
                }
                tasksJson.put(taskJson(task, categoryNameById, today));
            }
            return new JSONObject().put("categories", categoriesJson).put("tasks", tasksJson).toString();
        } catch (JSONException e) {
            throw new ClaudeApiException("Task-Daten konnten nicht serialisiert werden: " + e.getMessage(), e);
        }
    }

    /** The id of the category whose name matches {@code name}, or {@code null} if none does. */
    private static String categoryIdForName(List<TaskCategory> categories, String name) {
        String needle = name.trim().toLowerCase(Locale.ROOT);
        for (TaskCategory category : categories) {
            if (category.name != null && category.name.trim().toLowerCase(Locale.ROOT).equals(needle)) {
                return category.id;
            }
        }
        return null;
    }

    private JSONObject taskJson(Task task, Map<String, String> categoryNameById, LocalDate today)
            throws JSONException {
        TaskCore core = task.core;
        JSONObject json = new JSONObject();
        json.put("id", core.id);
        json.put("title", core.title);
        json.put("description", core.description);
        json.put("categoryName", core.categoryId != null ? categoryNameById.get(core.categoryId) : null);
        json.put("priority", core.priority != null ? core.priority.name() : null);
        json.put("leisure", core.leisure);
        json.put("completed", core.completed);
        json.put("schedulingType", core.schedulingType != null ? core.schedulingType.name() : null);
        json.put("fixedDate", asString(core.fixedDate));
        json.put("fixedStart", asString(core.fixedStart));
        json.put("fixedEnd", asString(core.fixedEnd));
        json.put("fixedDuration", core.fixedDuration);
        json.put("startDate", asString(core.startDate));
        json.put("deadline", asString(core.deadline));
        json.put("cooldown", core.cooldown);
        json.put("minDuration", core.minDuration);
        json.put("maxDuration", core.maxDuration);
        json.put("closeOnMiss", core.closeOnMiss);
        json.put("adaptive", core.adaptive);
        json.put("budgetRequiredCents", core.budgetRequiredCents);
        json.put("budgetAccountId", core.budgetAccountId);
        json.put("budgetCategoryId", core.budgetCategoryId);

        TaskCore.Repetition rep = core.repetition;
        json.put("repetition", new JSONObject()
                .put("reps", rep.reps)
                .put("perPeriod", rep.perPeriod)
                .put("periodUnit", rep.periodUnit != null ? rep.periodUnit.name() : null)
                .put("periodCompletions", rep.periodCompletions)
                .put("carryoverDebt", rep.carryoverDebt)
                .put("completeFirst", rep.completeFirst)
                .put("periodStart", asString(rep.periodStart)));

        TaskCore.Progress progress = core.progress;
        json.put("progress", new JSONObject()
                .put("unit", progress.unit)
                .put("target", progress.target)
                .put("current", progress.current)
                .put("resetPerRep", progress.resetPerRep)
                .put("minPerRep", progress.minPerRep)
                .put("maxPerRep", progress.maxPerRep));

        TaskCore.History history = core.history;
        json.put("history", new JSONObject()
                .put("completions", history.completions)
                .put("currentStreak", history.currentStreak)
                .put("averageDurationMinutes", history.averageDuration()));

        JSONArray prefSlots = new JSONArray();
        for (TaskPrefSlot prefSlot : task.prefSlots) {
            prefSlots.put(new JSONObject()
                    .put("days", daysJson(prefSlot.days))
                    .put("start", asString(prefSlot.start)));
        }
        json.put("prefSlots", prefSlots);

        JSONArray upcoming = new JSONArray();
        LocalDate horizon = today.plusDays(UPCOMING_SLOT_DAYS);
        for (TaskSlot slot : task.slots) {
            if (slot.day == null || slot.day.isBefore(today) || slot.day.isAfter(horizon)) {
                continue;
            }
            upcoming.put(new JSONObject()
                    .put("day", asString(slot.day))
                    .put("start", asString(slot.start))
                    .put("end", asString(slot.end))
                    .put("completed", slot.completed));
        }
        json.put("upcomingSlots", upcoming);
        return json;
    }

    private static JSONArray daysJson(Set<DayOfWeek> days) {
        JSONArray array = new JSONArray();
        if (days != null) {
            for (DayOfWeek day : days) {
                array.put(day.name());
            }
        }
        return array;
    }

    // ---- propose_task_changes -------------------------------------------------

    private PendingProposal proposeTaskChanges(JSONObject input) {
        TaskAssistantProposal proposal = new TaskAssistantProposal(
                parseCategoryChanges(input.optJSONArray("categories")),
                parseTaskChanges(input.optJSONArray("tasks")));

        List<Task> tasks = db.call(taskDao::readAll);
        List<TaskCategory> categories = db.call(categoryDao::readAll);
        Set<String> taskIds = new HashSet<>();
        List<TaskSnapshot.TaskInfo> taskInfos = new ArrayList<>();
        for (Task task : tasks) {
            taskIds.add(task.core.id);
            taskInfos.add(new TaskSnapshot.TaskInfo(task.core.id, task.core.title, task.core.categoryId,
                    task.core.priority != null ? task.core.priority.name() : null, task.core.leisure));
        }
        Set<String> categoryIds = new HashSet<>();
        List<TaskSnapshot.CategoryInfo> categoryInfos = new ArrayList<>();
        for (TaskCategory category : categories) {
            categoryIds.add(category.id);
            categoryInfos.add(new TaskSnapshot.CategoryInfo(
                    category.id, category.name, category.icon, category.colorHex));
        }
        validateTaskProposal(proposal, taskIds, categoryIds);
        return new TaskChangesProposal(new TaskSnapshot(categoryInfos, taskInfos), proposal);
    }

    private static void validateTaskProposal(TaskAssistantProposal proposal,
                                             Set<String> taskIds, Set<String> categoryIds) {
        for (TaskAssistantProposal.CategoryChange change : proposal.categoryChanges()) {
            if (change.op() == ChangeOp.CREATE) {
                if (change.name() == null) {
                    throw new IllegalArgumentException("Neue Kategorie ohne Namen im Vorschlag.");
                }
            } else if (change.id() == null || !categoryIds.contains(change.id())) {
                throw new IllegalArgumentException("Unbekannte Kategorie-id im Vorschlag: " + change.id());
            }
        }
        for (TaskAssistantProposal.TaskChange change : proposal.taskChanges()) {
            if (change.op() == ChangeOp.CREATE) {
                if (change.title() == null) {
                    throw new IllegalArgumentException("Neue Task ohne Titel im Vorschlag.");
                }
                if (change.schedulingType() == TaskCore.SchedulingType.TERMIN && change.fixedDate() == null) {
                    throw new IllegalArgumentException(
                            "Neuer Termin ohne fixedDate im Vorschlag: " + change.title());
                }
            } else if (change.id() == null || !taskIds.contains(change.id())) {
                throw new IllegalArgumentException("Unbekannte Task-id im Vorschlag: " + change.id());
            }
        }
    }

    private static List<TaskAssistantProposal.CategoryChange> parseCategoryChanges(JSONArray array) {
        List<TaskAssistantProposal.CategoryChange> changes = new ArrayList<>();
        if (array == null) {
            return changes;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject entry = array.optJSONObject(i);
            ChangeOp op = parseOp(entry);
            if (op == null) {
                continue;
            }
            changes.add(new TaskAssistantProposal.CategoryChange(op,
                    optString(entry, "id"), optString(entry, "name"),
                    optString(entry, "icon"), optString(entry, "colorHex")));
        }
        return changes;
    }

    private static List<TaskAssistantProposal.TaskChange> parseTaskChanges(JSONArray array) {
        List<TaskAssistantProposal.TaskChange> changes = new ArrayList<>();
        if (array == null) {
            return changes;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject entry = array.optJSONObject(i);
            ChangeOp op = parseOp(entry);
            if (op == null) {
                continue;
            }
            changes.add(new TaskAssistantProposal.TaskChange(op,
                    optString(entry, "id"), optString(entry, "title"), optString(entry, "description"),
                    optString(entry, "categoryId"), parsePriority(optString(entry, "priority")),
                    optBool(entry, "leisure"), optBool(entry, "adaptive"),
                    optInt(entry, "minDuration"), optInt(entry, "maxDuration"), optInt(entry, "cooldown"),
                    parseSchedulingType(optString(entry, "schedulingType")),
                    optDate(entry, "startDate"), optDate(entry, "deadline"), optBool(entry, "closeOnMiss"),
                    optDate(entry, "fixedDate"), optTime(entry, "fixedStart"), optTime(entry, "fixedEnd"),
                    optInt(entry, "fixedDuration"),
                    parseRepetition(entry.optJSONObject("repetition")),
                    parseProgress(entry.optJSONObject("progress")),
                    optInt(entry, "budgetRequiredCents"), optString(entry, "budgetAccountId"),
                    optString(entry, "budgetCategoryId"),
                    parsePrefSlots(entry.optJSONArray("prefSlots"))));
        }
        return changes;
    }

    private static TaskAssistantProposal.RepetitionChange parseRepetition(JSONObject obj) {
        if (obj == null) {
            return null;
        }
        return new TaskAssistantProposal.RepetitionChange(optInt(obj, "reps"), optInt(obj, "perPeriod"),
                parsePeriod(optString(obj, "periodUnit")), optBool(obj, "completeFirst"));
    }

    private static TaskAssistantProposal.ProgressChange parseProgress(JSONObject obj) {
        if (obj == null) {
            return null;
        }
        return new TaskAssistantProposal.ProgressChange(optInt(obj, "target"), optInt(obj, "current"),
                optString(obj, "unit"), optBool(obj, "resetPerRep"),
                optInt(obj, "minPerRep"), optInt(obj, "maxPerRep"));
    }

    private static List<TaskAssistantProposal.PrefSlotChange> parsePrefSlots(JSONArray array) {
        if (array == null) {
            return null;
        }
        List<TaskAssistantProposal.PrefSlotChange> slots = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject entry = array.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            slots.add(new TaskAssistantProposal.PrefSlotChange(
                    parseDays(entry.optJSONArray("days")), optTime(entry, "start")));
        }
        return slots;
    }

    /** Accepts ISO day numbers (1–7, Mon=1) or day names ("MONDAY") — the read side emits names. */
    private static Set<DayOfWeek> parseDays(JSONArray array) {
        if (array == null) {
            return null;
        }
        Set<DayOfWeek> days = new LinkedHashSet<>();
        for (int i = 0; i < array.length(); i++) {
            DayOfWeek day = parseDay(array.opt(i));
            if (day != null) {
                days.add(day);
            }
        }
        return days;
    }

    private static DayOfWeek parseDay(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            int n = number.intValue();
            return (n >= 1 && n <= 7) ? DayOfWeek.of(n) : null;
        }
        String raw = value.toString().trim();
        try {
            return DayOfWeek.of(Integer.parseInt(raw)); // "1".."7"
        } catch (NumberFormatException notANumber) {
            try {
                return DayOfWeek.valueOf(raw.toUpperCase(Locale.ROOT)); // "MONDAY"
            } catch (IllegalArgumentException notADay) {
                return null;
            }
        }
    }

    private static Period parsePeriod(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Period.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static TaskCore.SchedulingType parseSchedulingType(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return TaskCore.SchedulingType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ChangeOp parseOp(JSONObject entry) {
        String raw = optString(entry, "op");
        if (raw == null) {
            return null;
        }
        try {
            return ChangeOp.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Priority parsePriority(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Priority.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
