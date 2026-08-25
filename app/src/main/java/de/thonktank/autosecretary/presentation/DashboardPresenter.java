package de.thonktank.autosecretary.presentation;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.presentation.today.TodayUiModel;
import de.thonktank.autosecretary.domain.usecase.LoadDashboard;
import de.thonktank.autosecretary.domain.usecase.MaterializeDueOccurrences;
import de.thonktank.autosecretary.domain.usecase.ApplyComboDecay;
import de.thonktank.autosecretary.domain.usecase.SettlePreviousPartialOccurrences;
import de.thonktank.autosecretary.domain.usecase.ActivateReadyFlows;
import de.thonktank.autosecretary.domain.model.Dashboard;

import java.time.LocalDate;

public final class DashboardPresenter {
    private final Clock clock;
    private final LoadDashboard loadDashboard;
    private final MaterializeDueOccurrences materializeDue;
    private final DashboardUiMapper mapper;
    private final ApplyComboDecay decay;
    private final SettlePreviousPartialOccurrences settlement;
    private final ActivateReadyFlows activateReadyFlows;
    private final Runnable rescheduleWake;

    public DashboardPresenter(Clock clock, LoadDashboard loadDashboard,
                              MaterializeDueOccurrences materializeDue,
                              DashboardUiMapper mapper) {
        this(clock, loadDashboard, materializeDue, mapper, null, null, null, null);
    }

    public DashboardPresenter(Clock clock, LoadDashboard loadDashboard,
                              MaterializeDueOccurrences materializeDue,
                              DashboardUiMapper mapper, ApplyComboDecay decay) {
        this(clock, loadDashboard, materializeDue, mapper, decay, null, null, null);
    }

    public DashboardPresenter(Clock clock, LoadDashboard loadDashboard,
                              MaterializeDueOccurrences materializeDue,
                              DashboardUiMapper mapper, ApplyComboDecay decay,
                              SettlePreviousPartialOccurrences settlement) {
        this(clock, loadDashboard, materializeDue, mapper, decay, settlement, null, null);
    }

    public DashboardPresenter(Clock clock, LoadDashboard loadDashboard,
                              MaterializeDueOccurrences materializeDue,
                              DashboardUiMapper mapper, ApplyComboDecay decay,
                              ActivateReadyFlows activateReadyFlows,
                              Runnable rescheduleWake) {
        this(clock, loadDashboard, materializeDue, mapper, decay, null,
                activateReadyFlows, rescheduleWake);
    }

    public DashboardPresenter(Clock clock, LoadDashboard loadDashboard,
                              MaterializeDueOccurrences materializeDue,
                              DashboardUiMapper mapper, ApplyComboDecay decay,
                              SettlePreviousPartialOccurrences settlement,
                              ActivateReadyFlows activateReadyFlows,
                              Runnable rescheduleWake) {
        this.clock = clock;
        this.loadDashboard = loadDashboard;
        this.materializeDue = materializeDue;
        this.mapper = mapper;
        this.decay = decay;
        this.settlement = settlement;
        this.activateReadyFlows = activateReadyFlows;
        this.rescheduleWake = rescheduleWake == null ? () -> { } : rescheduleWake;
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
        boolean changed = settlement != null && settlement.execute();
        changed = materializeDue.execute() || changed;
        changed = (decay != null && decay.execute()) || changed;
        changed = (activateReadyFlows != null && activateReadyFlows.execute()) || changed;
        rescheduleWake.run();
        return changed;
    }
}
