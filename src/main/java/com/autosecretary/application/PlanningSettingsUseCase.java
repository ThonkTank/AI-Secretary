package com.autosecretary.application;

import com.autosecretary.domain.PlanningSettings;

/** Application boundary for reading and validating persisted planning preferences. */
public final class PlanningSettingsUseCase {
    private final PlanningSettingsRepository repository;

    public PlanningSettingsUseCase(PlanningSettingsRepository repository) {
        this.repository = repository;
    }

    public PlanningSettings load() { return repository.load(); }

    public void save(PlanningSettings settings) { repository.save(settings); }
}
