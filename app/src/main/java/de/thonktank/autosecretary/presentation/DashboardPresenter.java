package de.thonktank.autosecretary.presentation;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.TodayUiModel;
import de.thonktank.autosecretary.domain.usecase.LoadDashboard;
import de.thonktank.autosecretary.domain.usecase.MaterializeDueOccurrences;
import de.thonktank.autosecretary.domain.usecase.ApplyComboDecay;

import java.time.LocalDate;

public final class DashboardPresenter {
    private final Clock clock;
    private final LoadDashboard loadDashboard;
    private final MaterializeDueOccurrences materializeDue;
    private final DashboardUiMapper mapper;
    private final ApplyComboDecay decay;

    public DashboardPresenter(Clock clock, LoadDashboard loadDashboard,
                              MaterializeDueOccurrences materializeDue,
                              DashboardUiMapper mapper) {
        this(clock, loadDashboard, materializeDue, mapper, null);
    }

    public DashboardPresenter(Clock clock, LoadDashboard loadDashboard,
                              MaterializeDueOccurrences materializeDue,
                              DashboardUiMapper mapper, ApplyComboDecay decay) {
        this.clock = clock;
        this.loadDashboard = loadDashboard;
        this.materializeDue = materializeDue;
        this.mapper = mapper;
        this.decay = decay;
    }

    public TodayUiModel load() {
        LocalDate today = clock.today();
        return mapper.map(loadDashboard.execute(today), today);
    }

    public TodayUiModel refresh() {
        if (decay != null) decay.execute();
        materializeDue.execute();
        return load();
    }
}
