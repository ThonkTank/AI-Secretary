package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.Dashboard;
import de.thonktank.autosecretary.domain.model.DashboardTask;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.OccurrenceStep;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.model.TaskId;
import de.thonktank.autosecretary.domain.model.TaskSchedule;
import de.thonktank.autosecretary.domain.model.TaskSlot;
import de.thonktank.autosecretary.domain.repository.DashboardReadRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.RewardBooking;
import de.thonktank.autosecretary.domain.model.MissedOccurrenceMode;

public final class LoadDashboard {
    private final DashboardReadRepository repository;

    public LoadDashboard(DashboardReadRepository repository) {
        this.repository = repository;
    }

    public Dashboard execute(LocalDate today) {
        Map<TaskId, Task> tasks = new HashMap<>();
        for (Task task : repository.allTasks()) tasks.put(task.id, task);
        TaskSchedule schedule = new TaskSchedule(repository.scheduleEntries());
        List<Occurrence> open = repository.openOccurrences();
        List<Occurrence> completed = repository.completedOccurrences(today);
        List<String> occurrenceIds = new ArrayList<>();
        for (Occurrence occurrence : open) occurrenceIds.add(occurrence.id);
        for (Occurrence occurrence : completed) occurrenceIds.add(occurrence.id);
        Map<String, List<OccurrenceStep>> steps = groupSteps(
                repository.occurrenceStepsFor(occurrenceIds));
        Map<String, List<RewardBooking>> rewards = groupRewards(
                repository.rewardBookings(occurrenceIds));

        List<DashboardTask> result = new ArrayList<>();
        Set<TaskId> included = new HashSet<>();
        open.sort(Comparator.comparing((Occurrence value) -> value.scheduledOn)
                .thenComparingInt(value -> value.sortOrder).thenComparing(value -> value.id));
        Map<String, Integer> openCounts = new HashMap<>();
        for (Occurrence occurrence : open) {
            String key = occurrence.taskId.value + '|' + occurrence.slot.name();
            openCounts.put(key, openCounts.getOrDefault(key, 0) + 1);
        }
        Set<String> accumulatedSlots = new HashSet<>();
        for (Occurrence occurrence : open) {
            Task task = tasks.get(occurrence.taskId);
            if (task == null || task.archived || task.conditionDone) continue;
            String key = occurrence.taskId.value + '|' + occurrence.slot.name();
            if (task.missedOccurrenceMode == MissedOccurrenceMode.ACCUMULATE
                    && !accumulatedSlots.add(key)) continue;
            int backlog = task.missedOccurrenceMode == MissedOccurrenceMode.ACCUMULATE
                    ? Math.max(0, openCounts.getOrDefault(key, 1) - 1) : 0;
            result.add(item(task, occurrence, steps, rewards, false, backlog));
            included.add(task.id);
        }
        for (Task task : tasks.values())
            if (task.ongoing && !task.conditionText.isEmpty() && !task.archived
                    && !task.conditionDone && !included.contains(task.id)) {
                result.add(new DashboardTask(task, null, new ArrayList<>(), false,
                        java.util.Collections.emptyMap(), 0, schedule.primary(task.id).slot));
                included.add(task.id);
            }
        for (Occurrence occurrence : completed) {
            Task task = tasks.get(occurrence.taskId);
            if (task == null) continue;
            result.add(item(task, occurrence, steps, rewards, true, 0));
            included.add(task.id);
        }
        for (Task task : tasks.values())
            if (task.archived && today.equals(task.lastCompletedOn) && !included.contains(task.id))
                result.add(new DashboardTask(task, null, new ArrayList<>(), true,
                        java.util.Collections.emptyMap(), 0, schedule.primary(task.id).slot));
        result.sort(Comparator.comparingInt((DashboardTask item) -> item.done ? 1 : 0)
                .thenComparing(item -> item.occurrence == null
                        ? LocalDate.MAX : item.occurrence.scheduledOn)
                .thenComparingInt(item -> item.occurrence == null
                        ? item.displaySlot.rank : item.occurrence.slot.rank)
                .thenComparingInt(item -> item.occurrence == null
                        ? Integer.MAX_VALUE : item.occurrence.sortOrder)
                .thenComparingLong(item -> item.task.catalogOrder));
        Map<String, ComboProgress> combos = new HashMap<>();
        for (ComboProgress combo : repository.combos()) combos.put(combo.ownerId, combo);
        return new Dashboard(repository.xp(), result, combos);
    }

    private static DashboardTask item(Task task, Occurrence occurrence,
                                      Map<String, List<OccurrenceStep>> steps,
                                      Map<String, List<RewardBooking>> rewards, boolean done,
                                      int backlogCount) {
        List<OccurrenceStep> values = steps.get(occurrence.id);
        Map<String, Integer> stepXp = new HashMap<>();
        int awardedXp = 0;
        List<RewardBooking> bookings = rewards.get(occurrence.id);
        if (bookings != null) for (RewardBooking booking : bookings) {
            if (booking.target == RewardBooking.Target.HEAD) awardedXp += booking.xpDelta;
            else if (booking.occurrenceStepId != null) stepXp.put(booking.occurrenceStepId,
                    stepXp.getOrDefault(booking.occurrenceStepId, 0) + booking.xpDelta);
        }
        return new DashboardTask(task, occurrence, values == null ? new ArrayList<>() : values,
                done, stepXp, awardedXp, occurrence.slot, backlogCount);
    }

    private static Map<String, List<OccurrenceStep>> groupSteps(List<OccurrenceStep> values) {
        Map<String, List<OccurrenceStep>> result = new HashMap<>();
        for (OccurrenceStep step : values)
            result.computeIfAbsent(step.occurrenceId, ignored -> new ArrayList<>()).add(step);
        return result;
    }

    private static Map<String, List<RewardBooking>> groupRewards(List<RewardBooking> values) {
        Map<String, List<RewardBooking>> result = new HashMap<>();
        for (RewardBooking booking : values)
            result.computeIfAbsent(booking.occurrenceId, ignored -> new ArrayList<>()).add(booking);
        return result;
    }
}
