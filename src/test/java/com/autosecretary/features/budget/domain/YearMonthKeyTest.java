package com.autosecretary.features.budget.domain;

import org.junit.Assert;
import org.junit.Test;

import java.time.LocalDate;

public class YearMonthKeyTest {

    @Test
    public void from_formatsSingleDigitMonthAndRejectsNull() {
        Assert.assertEquals("2025-01", YearMonthKey.from(LocalDate.of(2025, 1, 5)));

        NullPointerException ex = Assert.assertThrows(NullPointerException.class, () -> YearMonthKey.from(null));
        Assert.assertEquals("date must not be null", ex.getMessage());
    }
}
