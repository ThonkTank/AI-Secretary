package com.autosecretary.domain;

import java.util.Comparator;
import java.util.List;

public record PlanningResult(List<PlanAssignment> assignments, List<PlanConflict> conflicts) {
    public PlanningResult {
        assignments = List.copyOf(assignments.stream()
                .sorted(Comparator.comparing(PlanAssignment::start))
                .collect(java.util.stream.Collectors.toList()));
        conflicts = List.copyOf(conflicts);
    }
}
