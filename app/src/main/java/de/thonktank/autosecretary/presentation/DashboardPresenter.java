package de.thonktank.autosecretary.presentation;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;
import de.thonktank.autosecretary.domain.usecase.LoadDashboard;
import de.thonktank.autosecretary.domain.usecase.MaterializeDueOccurrences;
import de.thonktank.autosecretary.domain.usecase.ApplyComboDecay;
import de.thonktank.autosecretary.domain.model.Dashboard;

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
        return load(clock.today());
    }

    public TodayUiModel load(LocalDate today) {
        return mapper.map(loadDomain(today), today);
    }

    public Dashboard loadDomain(LocalDate today) {
        return loadDashboard.execute(today);
    }

    public boolean prepare() {
        boolean changed = materializeDue.execute();
        return (decay != null && decay.execute()) || changed;
    }
}
