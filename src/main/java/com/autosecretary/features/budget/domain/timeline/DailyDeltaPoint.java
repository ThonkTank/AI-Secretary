package com.autosecretary.features.budget.domain.timeline;

import java.time.LocalDate;

public record DailyDeltaPoint(LocalDate bucketDate, long deltaCents) {}
