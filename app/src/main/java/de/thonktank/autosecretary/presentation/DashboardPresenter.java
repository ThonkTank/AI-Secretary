package de.thonktank.autosecretary.presentation;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.DashboardState;
import de.thonktank.autosecretary.domain.usecase.LoadDashboard;
import de.thonktank.autosecretary.domain.usecase.MaterializeDueOccurrences;

import java.time.LocalDate;

public final class DashboardPresenter {
    private final Clock clock;
    private final LoadDashboard loadDashboard;
    private final MaterializeDueOccurrences materializeDue;
    private final DashboardUiMapper mapper;

    public DashboardPresenter(Clock clock, LoadDashboard loadDashboard,
                              MaterializeDueOccurrences materializeDue,
                              DashboardUiMapper mapper) {
        this.clock = clock;
        this.loadDashboard = loadDashboard;
        this.materializeDue = materializeDue;
        this.mapper = mapper;
    }

    public DashboardState load() {
        LocalDate today = clock.today();
        return mapper.map(loadDashboard.execute(today), today);
    }

    public DashboardState refresh() {
        materializeDue.execute();
        return load();
    }
}
