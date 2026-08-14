package com.autosecretary.application;

import com.autosecretary.domain.PlanningSettings;

public interface PlanningSettingsRepository {
    PlanningSettings load();
    void save(PlanningSettings settings);
}
