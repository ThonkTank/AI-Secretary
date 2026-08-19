package de.thonktank.autosecretary.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RepetitionProgressCodec {
    private RepetitionProgressCodec() { }

    public static String encode(List<Integer> values) {
        if (values == null || values.isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        for (Integer value : values) {
            if (value == null || value < 0)
                throw new IllegalArgumentException("Confirmed repetitions must not be negative");
            if (result.length() > 0) result.append(',');
            result.append(value);
        }
        return result.toString();
    }

    public static List<Integer> decode(String stored) {
        if (stored == null || stored.isEmpty()) return Collections.emptyList();
        if (!stored.matches("(?:0|[1-9][0-9]*)(?:,(?:0|[1-9][0-9]*))*"))
            throw new IllegalArgumentException("Invalid stored repetition progress");
        List<Integer> values = new ArrayList<>();
        for (String part : stored.split(",", -1)) {
            try {
                values.add(Integer.parseInt(part));
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("Invalid stored repetition progress", error);
            }
        }
        return Collections.unmodifiableList(values);
    }
}
