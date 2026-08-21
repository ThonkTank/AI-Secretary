package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.repository.RewardLedgerRepository;

import java.util.Objects;

public final class ApplyComboDecay {
    private final RewardLedgerRepository repository;
    private final Clock clock;
    public ApplyComboDecay(RewardLedgerRepository repository, Clock clock) {
        this.repository = repository; this.clock = clock;
    }
    public boolean execute() {
        return repository.inTransaction(() -> {
            boolean changed = false;
            for (ComboProgress current : repository.combos()) {
                ComboProgress settled = current.settle(clock.today());
                if (settled.points != current.points
                        || !Objects.equals(settled.settledThroughOn, current.settledThroughOn)) {
                    repository.putCombo(settled);
                    changed = true;
                }
            }
            return changed;
        });
    }
}
