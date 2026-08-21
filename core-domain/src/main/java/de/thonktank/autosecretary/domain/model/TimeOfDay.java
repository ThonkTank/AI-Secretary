package de.thonktank.autosecretary.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public enum TimeOfDay {
    MORNING(1 << 0, TaskSlot.MORNING),
    MIDDAY(1 << 1, TaskSlot.MIDDAY),
    EVENING(1 << 2, TaskSlot.EVENING),
    NIGHT(1 << 3, TaskSlot.LATER);

    public static final int ALL_MASK = MORNING.bit | MIDDAY.bit | EVENING.bit | NIGHT.bit;

    public final int bit;
    public final TaskSlot slot;

    TimeOfDay(int bit, TaskSlot slot) {
        this.bit = bit;
        this.slot = slot;
    }

    public static TimeOfDay fromSlot(TaskSlot slot) {
        if (slot == TaskSlot.MORNING) return MORNING;
        if (slot == TaskSlot.MIDDAY) return MIDDAY;
        if (slot == TaskSlot.EVENING) return EVENING;
        return NIGHT;
    }

    public static List<TaskSlot> slots(int mask) {
        if ((mask & ~ALL_MASK) != 0) throw new IllegalArgumentException("Unknown time-of-day bits");
        List<TaskSlot> result = new ArrayList<>();
        for (TimeOfDay value : values()) if ((mask & value.bit) != 0) result.add(value.slot);
        return Collections.unmodifiableList(result);
    }

    public static TaskSlot earliestSlot(int mask, TaskSlot fallback) {
        List<TaskSlot> slots = slots(mask);
        return slots.isEmpty() ? fallback : slots.get(0);
    }
}
