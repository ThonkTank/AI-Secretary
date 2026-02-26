package com.autosecretary.features.budget.domain.timeline;

import java.time.YearMonth;

public record MonthlyDeltaPoint(YearMonth yearMonth, long deltaCents) {}
