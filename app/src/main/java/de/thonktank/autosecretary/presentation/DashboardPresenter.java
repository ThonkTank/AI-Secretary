package de.thonktank.autosecretary.presentation;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.TodayUiModel;
import de.thonktank.autosecretary.domain.usecase.LoadDashboard;
import de.thonktank.autosecretary.domain.usecase.MaterializeDueOccurrences;
import de.thonktank.autosecretary.domain.usecase.ApplyComboDecay;
import de.thonktank.autosecretary.domain.model.Dashboard;

import java.time.LocalDate;

public final class DashboardPresenter {
    public static final class Refresh {
        public final TodayUiModel dashboard;
        public final boolean persistedChanges;

        private Refresh(TodayUiModel dashboard, boolean persistedChanges) {
            this.dashboard = dashboard;
            this.persistedChanges = persistedChanges;
        }
    }

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
        return mapper.map(loadDomain(today), today);
    }

    public TodayUiModel refresh() {
        return refreshWithChanges().dashboard;
    }

    public Refresh refreshWithChanges() {
        LocalDate today = clock.today();
        boolean changed = prepareDomain();
        return new Refresh(mapper.map(loadDomain(today), today), changed);
    }

    public Dashboard loadDomain(LocalDate today) {
        return loadDashboard.execute(today);
    }

    public Dashboard refreshDomain(LocalDate today) {
        prepareDomain();
        return loadDomain(today);
    }

    private boolean prepareDomain() {
        boolean changed = decay != null && decay.execute();
        return materializeDue.execute() || changed;
    }
}
