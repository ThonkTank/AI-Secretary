package de.thonktank.autosecretary.domain.today;

import de.thonktank.autosecretary.domain.model.*;
import java.time.LocalDate;
import java.util.*;

/** Shared order and visibility for app, widget and movement commands. */
public final class TodayQueue {
    private TodayQueue() { }
    public static final class Entry {
        public final DashboardTask task;
        public final FlowTaskSheet sheet;
        public final TodayPlacement.Kind kind;
        public final String id;
        public final TaskSlot originalSlot;
        public TaskSlot slot;
        private final LocalDate date;
        private final int order;
        private Entry(DashboardTask task, FlowTaskSheet sheet) {
            this.task = task; this.sheet = sheet;
            kind = sheet != null ? TodayPlacement.Kind.FLOW_TASK_SHEET
                    : task.occurrence != null ? TodayPlacement.Kind.OCCURRENCE : TodayPlacement.Kind.TASK;
            id = sheet != null ? sheet.placement.id
                    : task.occurrence != null ? task.occurrence.id : task.task.id.value;
            originalSlot = sheet != null ? sheet.placement.slot : task.displaySlot;
            slot = originalSlot;
            date = sheet != null ? sheet.placement.displayOn
                    : task.occurrence != null ? task.occurrence.scheduledOn : LocalDate.MAX;
            order = sheet != null ? sheet.placement.sortOrder
                    : task.occurrence != null ? task.occurrence.sortOrder : Integer.MAX_VALUE;
        }
        public String key() { return kind.name() + ":" + id; }
    }
    public static int sectionEnd(List<Entry> entries, TaskSlot slot) {
        int insertion = 0;
        int lastInSection = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).slot.rank < slot.rank) insertion = i + 1;
            if (entries.get(i).slot == slot) lastInSection = i;
        }
        return lastInSection < 0 ? insertion : lastInSection + 1;
    }

    public static List<Entry> visible(Dashboard dashboard, LocalDate date) {
        List<Entry> baseline = new ArrayList<>();
        for (DashboardTask task : dashboard.tasks) if (!task.done) baseline.add(new Entry(task, null));
        for (FlowTaskSheet sheet : dashboard.flowTaskSheets) baseline.add(new Entry(null, sheet));
        baseline.sort(Comparator.comparing((Entry e) -> e.date)
                .thenComparingInt(e -> e.slot.rank).thenComparingInt(e -> e.order)
                .thenComparing(e -> e.sheet == null ? e.id : "flow-sheet:" + e.id));
        Map<String, TodayPlacement> placements = new HashMap<>();
        for (TodayPlacement p : dashboard.todayPlacements) placements.put(p.key(), p);
        List<Entry> result = new ArrayList<>();
        List<Entry> positioned = new ArrayList<>();
        for (Entry e : baseline) {
            TodayPlacement p = placements.get(e.key());
            if (p == null) result.add(e);
            else if (!p.displayOn.isAfter(date)) {
                e.slot = p.slot;
                positioned.add(e);
            }
        }
        positioned.sort(Comparator.comparingInt((Entry e) -> placements.get(e.key()).position)
                .thenComparing(Entry::key));
        for (Entry e : positioned) {
            int position = placements.get(e.key()).position;
            if (position == Integer.MAX_VALUE) {
                // A deferred item returns at the end of its original section tomorrow.
                position = sectionEnd(result, e.slot);
            }
            result.add(Math.min(position, result.size()), e);
        }
        return result;
    }
}
