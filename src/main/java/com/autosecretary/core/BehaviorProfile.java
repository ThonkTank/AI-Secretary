package com.autosecretary.core;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Small, explainable summary of observed completion time and A→B ordering. */
public final class BehaviorProfile {
    private final Map<String, Integer> medianMinute = new HashMap<>();
    private final Map<String, Integer> transitionCounts = new HashMap<>();
    private String lastCompletedToday;

    public static BehaviorProfile from(List<Completion> source, LocalDate today) {
        BehaviorProfile profile = new BehaviorProfile();
        List<Completion> completions = source.stream()
                .sorted(Comparator.comparing(Completion::completedAt))
                .collect(Collectors.toList());
        Map<String, List<Integer>> minutesByObligation = new HashMap<>();

        Completion previous = null;
        for (Completion completion : completions) {
            minutesByObligation.computeIfAbsent(completion.obligationId(), ignored -> new ArrayList<>())
                    .add(completion.completedAt().getHour() * 60 + completion.completedAt().getMinute());
            if (previous != null
                    && previous.completedAt().toLocalDate().equals(completion.completedAt().toLocalDate())) {
                profile.transitionCounts.merge(
                        previous.obligationId() + "→" + completion.obligationId(), 1, Integer::sum);
            }
            if (completion.completedAt().toLocalDate().equals(today)) {
                profile.lastCompletedToday = completion.obligationId();
            }
            previous = completion;
        }

        for (Map.Entry<String, List<Integer>> entry : minutesByObligation.entrySet()) {
            List<Integer> values = entry.getValue().stream().sorted().collect(Collectors.toList());
            profile.medianMinute.put(entry.getKey(), values.get(values.size() / 2));
        }
        return profile;
    }

    public int learnedMinute(String obligationId) {
        return medianMinute.getOrDefault(obligationId, 12 * 60);
    }

    public boolean hasLearnedMinute(String obligationId) {
        return medianMinute.containsKey(obligationId);
    }

    public int transitionStrength(String obligationId) {
        if (lastCompletedToday == null) {
            return 0;
        }
        return transitionCounts.getOrDefault(lastCompletedToday + "→" + obligationId, 0);
    }
}
