package com.autosecretary.features.task.application;

import java.time.LocalDate;

public interface TaskBudgetProvider {
    long getAvailableBudgetCents(LocalDate day);
}
