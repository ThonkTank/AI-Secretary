package test;

import repository.Repo;
import repository.Table;
import entities.trackedItem;
import entities.todoList;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-Memory Repo für Tests. Kein Android, kein SQLite.
 */
public class MockRepo implements Repo {

    public Map<Long, trackedItem> items = new HashMap<>();
    public Map<String, todoList> todos = new HashMap<>(); // key = date string
    public Map<String, LocalTime[]> schedules = new HashMap<>(); // key = day_of_week → [start, end]
    private long nextId = 1;

    // === Config Schedules Setup ===
    public void addSchedule(String dayOfWeek, String start, String end) {
        schedules.put(dayOfWeek, new LocalTime[]{LocalTime.parse(start), LocalTime.parse(end)});
    }

    // === Repo Interface ===

    @Override
    @SuppressWarnings("unchecked")
    public <T> T lookup(String table, Map<String, String> filters, String column) {
        List<T> results = lookups(table, filters, column);
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> lookups(String table, Map<String, String> filters, String column) {
        List<T> results = new ArrayList<>();

        if (table.equals("config_schedules")) {
            String dow = filters.get("day_of_week");
            LocalTime[] sched = schedules.get(dow);
            if (sched != null) {
                if (column.equals("start_time")) results.add((T) sched[0]);
                else if (column.equals("end_time")) results.add((T) sched[1]);
            }
            return results;
        }

        if (table.equals("items")) {
            for (trackedItem item : items.values()) {
                if (matchesFilters(item, filters)) {
                    if (column.equals("id")) results.add((T) item.id);
                }
            }
            return results;
        }

        return results;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T fetch(Table<T> table, long id) {
        if (table.name().equals("items")) return (T) items.get(id);
        if (table.name().equals("todos")) {
            for (todoList t : todos.values()) {
                if (t.id != null && t.id == id) return (T) t;
            }
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T fetch(Table<T> table, Map<String, String> filters) {
        if (table.name().equals("todos")) {
            String date = filters.get("date");
            if (date != null) return (T) todos.get(date);
        }
        if (table.name().equals("items")) {
            List<Long> ids = lookups("items", filters, "id");
            if (!ids.isEmpty()) return (T) items.get(ids.get(0));
        }
        return null;
    }

    @Override
    public void write(Object entity) {
        if (entity instanceof trackedItem item) {
            if (item.id == null) item.id = nextId++;
            items.put(item.id, item);
        } else if (entity instanceof todoList list) {
            if (list.id == null) list.id = nextId++;
            if (list.date != null) todos.put(list.date.toString(), list);
        }
    }

    // === Filter-Matching ===
    private boolean matchesFilters(trackedItem item, Map<String, String> filters) {
        for (Map.Entry<String, String> f : filters.entrySet()) {
            switch (f.getKey()) {
                case "type":
                    if (!item.type.name().equalsIgnoreCase(f.getValue())) return false;
                    break;
                case "is_completed":
                    boolean expected = !f.getValue().equals("0");
                    if (item.isCompleted == null && expected) return false;
                    if (item.isCompleted != null && item.isCompleted != expected) return false;
                    break;
                case "parent":
                    long parentId = Long.parseLong(f.getValue());
                    if (item.parent == null || item.parent != parentId) return false;
                    break;
                default:
                    break;
            }
        }
        return true;
    }
}
