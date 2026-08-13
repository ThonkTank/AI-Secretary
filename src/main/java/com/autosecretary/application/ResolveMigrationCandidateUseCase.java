package com.autosecretary.application;

import java.util.List;

/** Atomically applies all explicit decisions and acknowledges only the complete report. */
public final class ResolveMigrationCandidateUseCase {
    private final WorkItemRepository repository;
    private final AppClock clock;

    public ResolveMigrationCandidateUseCase(WorkItemRepository repository, AppClock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void execute(List<MigrationCandidateResolution> resolutions, long reportId) {
        repository.resolveMigrationCandidates(resolutions, reportId, clock.now());
    }
}
