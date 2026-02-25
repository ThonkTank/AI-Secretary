package com.autosecretary.features.budget.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public final class YearMonthKey {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private YearMonthKey() {
    }

    public static String from(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        return date.format(FORMATTER);
    }
}
