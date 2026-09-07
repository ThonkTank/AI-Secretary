package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.FlowTaskSheetPlacement;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.repository.FlowRepository;
import de.thonktank.autosecretary.domain.repository.TodayRepository;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

/** Moves only a flow sheet's Today placement; runs and resources remain untouched. */
public final class DeferFlowTaskSheet {
    private final FlowRepository flows;
    private final TodayRepository today;
    private final TransactionRunner transactions;
    private final Clock clock;

    public DeferFlowTaskSheet(FlowRepository flows, TodayRepository today,
                              TransactionRunner transactions, Clock clock) {
        this.flows = flows; this.today = today; this.transactions = transactions;
        this.clock = clock;
    }

    public boolean execute(String sheetId) {
        return transactions.inTransaction(() -> {
            FlowTaskSheetPlacement moving = null;
            for (FlowTaskSheetPlacement value : flows.flowTaskSheetPlacements())
                if (value.id.equals(sheetId)) moving = value;
            if (moving == null) return false;
            int last = moving.sortOrder;
            for (Occurrence occurrence : today.openOccurrences(moving.slot))
                last = Math.max(last, occurrence.sortOrder);
            for (FlowTaskSheetPlacement value : flows.flowTaskSheetPlacements())
                if (value.slot == moving.slot) last = Math.max(last, value.sortOrder);
            flows.putFlowTaskSheetPlacement(moving.moveTo(clock.today(), last + 1));
            return true;
        });
    }
}
