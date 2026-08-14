package com.autosecretary.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Central invariant and copy helpers for the immutable work-item graph. */
public final class WorkItems {
    private WorkItems() { }

    static String requireId(String value) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty()) throw new IllegalArgumentException("Work-Item-ID fehlt");
        try { UUID.fromString(result); }
        catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Work-Item-ID ist keine UUID", error);
        }
        return result;
    }

    static String requireTitle(String value) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty()) throw new IllegalArgumentException("Titel fehlt");
        return result;
    }

    static int requireDuration(int value) {
        if (value < 5 || value > 480) {
            throw new IllegalArgumentException("Dauer muss zwischen 5 und 480 Minuten liegen");
        }
        return value;
    }

    static List<Step> normalizedSteps(List<Step> source) {
        if (source == null || source.isEmpty()) return List.of();
        List<Step> result = new ArrayList<>(source);
        result.sort(Comparator.comparingInt(Step::position));
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < result.size(); index++) {
            Step step = result.get(index);
            if (!ids.add(step.id())) throw new IllegalArgumentException("Doppelte Schritt-ID");
            if (step.position() != index) {
                result.set(index, new Step(step.id(), step.title(), step.days(), index));
            }
        }
        return List.copyOf(result);
    }
}
