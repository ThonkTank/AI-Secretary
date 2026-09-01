package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceState;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.OccurrenceKind;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.repository.TodayRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Rolls stale open work into a carry-forward plan and marks its source historical. */
final class OccurrenceCarryForward {
    Result collect(TodayRepository repository, LocalDate today,
                   List<Occurrence> history,
                   Map<String, List<OccurrenceStep>> stepsByOccurrence) {
        List<Occurrence> values = new ArrayList<>(history);
        Map<TaskSlot, Occurrence> open = latestOpen(values);
        Map<TaskSlot, List<OccurrenceStep>> carry = new HashMap<>();
        Map<TaskSlot, String> origins = new HashMap<>();
        boolean changed = false;

        for (Map.Entry<TaskSlot, Occurrence> entry : new ArrayList<>(open.entrySet())) {
            Occurrence occurrence = entry.getValue();
            if (!occurrence.scheduledOn.isBefore(today)) continue;
            List<OccurrenceStep> existingSteps = stepsByOccurrence.getOrDefault(occurrence.id,
                    Collections.emptyList());
            List<OccurrenceStep> unfinished = unfinished(existingSteps);
            if (!unfinished.isEmpty() || existingSteps.isEmpty()) {
                carry.put(entry.getKey(), unfinished);
                origins.put(entry.getKey(), occurrence.id);
            }
            repository.updateOccurrence(occurrence.missed());
            open.remove(entry.getKey());
            changed = true;
        }

        for (TaskSlot slot : slotsWithCarryCandidates(values, open.keySet(), today)) {
            if (carry.containsKey(slot) || open.containsKey(slot)) continue;
            Occurrence latest = latest(values, slot);
            if (latest == null || !latest.scheduledOn.isBefore(today)) continue;
            List<OccurrenceStep> unfinished = unfinished(stepsByOccurrence.getOrDefault(
                    latest.id, Collections.emptyList()));
            if (!unfinished.isEmpty()) {
                carry.put(slot, unfinished);
                origins.put(slot, latest.id);
            }
        }
        return new Result(open, carry, origins, changed);
    }

    private static List<OccurrenceStep> unfinished(List<OccurrenceStep> steps) {
        List<OccurrenceStep> result = new ArrayList<>();
        for (OccurrenceStep step : steps) if (!step.done) result.add(step);
        return result;
    }

    private static Map<TaskSlot, Occurrence> latestOpen(List<Occurrence> values) {
        Map<TaskSlot, Occurrence> result = new HashMap<>();
        for (Occurrence value : values) if (value.state == OccurrenceState.OPEN
                && value.kind != OccurrenceKind.FLOW_SHEET) {
            Occurrence current = result.get(value.slot);
            if (current == null || value.scheduledOn.isAfter(current.scheduledOn))
                result.put(value.slot, value);
        }
        return result;
    }

    private static Occurrence latest(List<Occurrence> values, TaskSlot slot) {
        return values.stream().filter(value -> value.slot == slot
                        && value.kind != OccurrenceKind.FLOW_SHEET)
                .max(Comparator.comparing((Occurrence value) -> value.scheduledOn)
                        .thenComparing(value -> value.state == OccurrenceState.OPEN ? 1 : 0))
                .orElse(null);
    }

    private static Set<TaskSlot> slotsWithCarryCandidates(List<Occurrence> values,
                                                           Set<TaskSlot> openSlots,
                                                           LocalDate today) {
        Set<TaskSlot> result = new HashSet<>();
        for (Occurrence value : values)
            if (value.kind != OccurrenceKind.FLOW_SHEET && !openSlots.contains(value.slot)
                    && value.scheduledOn.isBefore(today))
                result.add(value.slot);
        return result;
    }

    static int carryOrder(List<Occurrence> values, TaskSlot slot) {
        Occurrence value = latest(values, slot);
        return value == null ? 0 : value.sortOrder;
    }

    static final class Result {
        final Map<TaskSlot, Occurrence> open;
        final Map<TaskSlot, List<OccurrenceStep>> carry;
        final Map<TaskSlot, String> originOccurrenceIds;
        final boolean changed;

        Result(Map<TaskSlot, Occurrence> open, Map<TaskSlot, List<OccurrenceStep>> carry,
               Map<TaskSlot, String> originOccurrenceIds, boolean changed) {
            this.open = open;
            this.carry = carry;
            this.originOccurrenceIds = originOccurrenceIds;
            this.changed = changed;
        }
    }
}
