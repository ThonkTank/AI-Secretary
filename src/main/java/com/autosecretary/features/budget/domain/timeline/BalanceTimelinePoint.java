package com.autosecretary.features.budget.domain.timeline;

import java.time.LocalDate;

public record BalanceTimelinePoint(LocalDate date, long balanceCents) {}
