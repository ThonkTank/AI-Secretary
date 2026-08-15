package de.thonktank.autosecretary;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Small local-first store. The complete personal state lives in SharedPreferences and therefore
 * survives normal APK updates. */
public final class TaskRepository {
    public static final String SLOT_MORNING = "Morgen";
    public static final String SLOT_MIDDAY = "Mittag";
    public static final String SLOT_EVENING = "Abend";
    public static final String SLOT_LATER = "Später";
    private static final String PREFS = "jetzt_state";
    private static final String TASKS = "tasks";
    private static final String XP = "xp";
    private final SharedPreferences prefs;

    public TaskRepository(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public int getXp() { return prefs.getInt(XP, 0); }
    public void addXp(int amount) { prefs.edit().putInt(XP, getXp() + amount).apply(); }

    public JSONArray all() {
        try { return new JSONArray(prefs.getString(TASKS, "[]")); }
        catch (JSONException e) { return new JSONArray(); }
    }

    private void save(JSONArray tasks) { prefs.edit().putString(TASKS, tasks.toString()).apply(); }

    public JSONObject create(String title, String slot, String repeat, int interval, JSONArray weekdays,
                             JSONArray steps, boolean ongoing, String condition, boolean reminder) {
        JSONObject task = new JSONObject();
        try {
            task.put("id", UUID.randomUUID().toString());
            task.put("title", title.trim());
            task.put("slot", slot);
            task.put("repeat", repeat);
            task.put("interval", Math.max(1, interval));
            task.put("weekdays", weekdays);
            task.put("steps", steps);
            task.put("ongoing", ongoing);
            task.put("condition", condition.trim());
            task.put("conditionDone", false);
            task.put("archived", false);
            task.put("created", today());
            task.put("lastCompleted", "");
            task.put("cycleCompleted", "");
            task.put("reminder", reminder);
            task.put("routineLevel", 1);
            task.put("routineStreak", 0);
        } catch (JSONException ignored) { }
        JSONArray tasks = all(); tasks.put(task); save(tasks);
        return task;
    }

    public List<JSONObject> activeTasks() {
        JSONArray tasks = all();
        List<JSONObject> result = new ArrayList<>();
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            if (task != null && !task.optBoolean("archived") && isActive(task)) result.add(task);
        }
        Collections.sort(result, new Comparator<JSONObject>() {
            @Override public int compare(JSONObject a, JSONObject b) {
                int slot = Integer.compare(slotRank(a.optString("slot")), slotRank(b.optString("slot")));
                if (slot != 0) return slot;
                return a.optString("title").compareToIgnoreCase(b.optString("title"));
            }
        });
        // "Später" moves a task down one place for each press, only for today.
        String today = today();
        for (JSONObject task : new ArrayList<>(result)) {
            if (today.equals(task.optString("snoozeDate"))) {
                int from = result.indexOf(task);
                int to = Math.min(result.size() - 1, from + task.optInt("snoozeCount", 0));
                result.remove(from); result.add(to, task);
            }
        }
        return result;
    }

    private boolean isActive(JSONObject task) {
        if (task.optBoolean("ongoing")) {
            if (task.optBoolean("conditionDone")) return false;
            if (!allStepsDone(task)) return true;
            if (!task.optString("condition").isEmpty()) return true;
            return isRecurringDue(task, task.optString("cycleCompleted"));
        }
        String repeat = task.optString("repeat", "Einmalig");
        if ("Einmalig".equals(repeat)) return true;
        return isRecurringDue(task, task.optString("lastCompleted"));
    }

    private boolean isRecurringDue(JSONObject task, String lastDate) {
        if (lastDate == null || lastDate.isEmpty()) return true;
        LocalDate last = parseDate(lastDate);
        LocalDate now = LocalDate.now();
        String repeat = task.optString("repeat", "Einmalig");
        if ("Täglich".equals(repeat)) return last.isBefore(now);
        if ("Alle N Tage".equals(repeat)) return ChronoUnit.DAYS.between(last, now) >= task.optInt("interval", 2);
        if ("Wochentage".equals(repeat)) {
            JSONArray days = task.optJSONArray("weekdays");
            if (containsDay(days, now.getDayOfWeek())) return true;
            // Carry a missed target day gently forward until it is done.
            for (LocalDate d = last.plusDays(1); d.isBefore(now); d = d.plusDays(1))
                if (containsDay(days, d.getDayOfWeek())) return true;
        }
        return false;
    }

    private static boolean containsDay(JSONArray days, DayOfWeek day) {
        if (days == null) return false;
        for (int i = 0; i < days.length(); i++) if (days.optInt(i) == day.getValue()) return true;
        return false;
    }

    public void completeNextStep(String id) {
        mutate(id, new Mutator() {
            @Override public void change(JSONObject task) throws JSONException {
                prepareForNewCycle(task);
                JSONArray steps = task.optJSONArray("steps");
                if (steps == null || steps.length() == 0) {
                    if (task.optBoolean("ongoing")) { task.put("cycleCompleted", today()); reward(task); }
                    else finishTask(task);
                    return;
                }
                for (int i = 0; i < steps.length(); i++) {
                    JSONObject step = steps.optJSONObject(i);
                    if (step != null && !step.optBoolean("done")) { step.put("done", true); break; }
                }
                if (allStepsDone(task) && !task.optBoolean("ongoing")) finishTask(task);
                else if (allStepsDone(task)) task.put("cycleCompleted", today());
            }
        });
    }

    public void fulfilCondition(String id) {
        mutate(id, new Mutator() {
            @Override public void change(JSONObject task) throws JSONException {
                task.put("conditionDone", true); task.put("archived", true); reward(task);
            }
        });
    }

    public void later(String id) {
        mutate(id, new Mutator() {
            @Override public void change(JSONObject task) throws JSONException {
                if (!today().equals(task.optString("snoozeDate"))) { task.put("snoozeDate", today()); task.put("snoozeCount", 0); }
                task.put("snoozeCount", task.optInt("snoozeCount", 0) + 1);
            }
        });
    }

    private void prepareForNewCycle(JSONObject task) throws JSONException {
        if (allStepsDone(task) && isActive(task)) resetSteps(task);
    }

    private void finishTask(JSONObject task) throws JSONException {
        reward(task);
        if ("Einmalig".equals(task.optString("repeat", "Einmalig"))) task.put("archived", true);
        else { task.put("lastCompleted", today()); resetSteps(task); }
    }

    private void reward(JSONObject task) throws JSONException {
        addXp(10);
        if (!"Einmalig".equals(task.optString("repeat", "Einmalig"))) {
            LocalDate previous = task.optString("lastCompleted").isEmpty() ? null : parseDate(task.optString("lastCompleted"));
            int streak = previous != null && ChronoUnit.DAYS.between(previous, LocalDate.now()) <= 2
                    ? task.optInt("routineStreak", 0) + 1 : 1;
            task.put("routineStreak", streak);
            task.put("routineLevel", Math.max(task.optInt("routineLevel", 1), 1 + streak / 5));
        }
    }

    private static void resetSteps(JSONObject task) throws JSONException {
        JSONArray steps = task.optJSONArray("steps");
        if (steps == null) return;
        for (int i = 0; i < steps.length(); i++) { JSONObject step = steps.optJSONObject(i); if (step != null) step.put("done", false); }
    }

    private static boolean allStepsDone(JSONObject task) {
        JSONArray steps = task.optJSONArray("steps");
        if (steps == null || steps.length() == 0) return true;
        for (int i = 0; i < steps.length(); i++) {
            JSONObject step = steps.optJSONObject(i);
            if (step != null && !step.optBoolean("done")) return false;
        }
        return true;
    }

    public String nextAction(JSONObject task) {
        JSONArray steps = task.optJSONArray("steps");
        if (steps != null) for (int i = 0; i < steps.length(); i++) {
            JSONObject step = steps.optJSONObject(i);
            if (step != null && !step.optBoolean("done")) return step.optString("text");
        }
        if (task.optBoolean("ongoing") && !task.optString("condition").isEmpty()) return task.optString("condition");
        return "Als erledigt markieren";
    }

    public int remainingSteps(JSONObject task) {
        int open = 0; JSONArray steps = task.optJSONArray("steps");
        if (steps != null) for (int i = 0; i < steps.length(); i++) {
            JSONObject step = steps.optJSONObject(i);
            if (step != null && !step.optBoolean("done")) open++;
        }
        return open;
    }

    private void mutate(String id, Mutator mutator) {
        JSONArray tasks = all();
        try {
            for (int i = 0; i < tasks.length(); i++) {
                JSONObject task = tasks.optJSONObject(i);
                if (task != null && id.equals(task.optString("id"))) { mutator.change(task); break; }
            }
        } catch (JSONException ignored) { }
        save(tasks);
    }

    private interface Mutator { void change(JSONObject task) throws JSONException; }
    private static String today() { return LocalDate.now().toString(); }
    private static LocalDate parseDate(String value) { try { return LocalDate.parse(value); } catch (Exception e) { return LocalDate.now().minusYears(1); } }
    public static int slotRank(String slot) { if (SLOT_MORNING.equals(slot)) return 0; if (SLOT_MIDDAY.equals(slot)) return 1; if (SLOT_EVENING.equals(slot)) return 2; return 3; }
}
