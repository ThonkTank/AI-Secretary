package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.*;
import de.thonktank.autosecretary.domain.repository.TodayRepository;
import de.thonktank.autosecretary.domain.today.TodayQueue;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;
import java.time.LocalDate;
import java.util.List;

/** Serializes a complete visible queue so every item type shares the same ordering rules. */
public final class MoveTodayItem {
    public enum Action { LATER, FIRST }
    private final TodayRepository repository;
    private final LoadDashboard dashboard;
    private final TransactionRunner transactions;
    private final Clock clock;
    public MoveTodayItem(TodayRepository repository, LoadDashboard dashboard,
                         TransactionRunner transactions, Clock clock) {
        this.repository = repository; this.dashboard = dashboard;
        this.transactions = transactions; this.clock = clock;
    }
    public boolean execute(TodayPlacement.Kind kind, String id, Action action) {
        return transactions.inTransaction(() -> {
            LocalDate date = clock.today();
            List<TodayQueue.Entry> entries = TodayQueue.visible(dashboard.execute(date), date);
            TodayQueue.Entry moving = null;
            for (TodayQueue.Entry e : entries) if (e.kind == kind && e.id.equals(id)) moving = e;
            if (moving == null) return false;
            if (action == Action.FIRST && entries.get(0) == moving) return true;
            TaskSlot slot = moving.slot;
            if (action == Action.FIRST) {
                moving.slot = entries.get(0).slot;
                entries.remove(moving); entries.add(0, moving);
            } else {
                int last = -1;
                for (int i = 0; i < entries.size(); i++) if (entries.get(i).slot == slot) last = i;
                boolean atEnd = entries.indexOf(moving) == last;
                entries.remove(moving);
                if (atEnd && slot == TaskSlot.LATER) {
                    repository.putTodayPlacement(new TodayPlacement(kind, id, date.plusDays(1),
                            moving.originalSlot, Integer.MAX_VALUE));
                } else {
                    if (atEnd) slot = TaskSlot.values()[slot.ordinal() + 1];
                    moving.slot = slot;
                    int insertion = 0;
                    int lastInSection = -1;
                    for (int i = 0; i < entries.size(); i++) {
                        if (entries.get(i).slot.rank < slot.rank) insertion = i + 1;
                        if (entries.get(i).slot == slot) lastInSection = i;
                    }
                    if (lastInSection >= 0) insertion = lastInSection + 1;
                    entries.add(insertion, moving);
                }
            }
            for (int i = 0; i < entries.size(); i++) {
                TodayQueue.Entry e = entries.get(i);
                repository.putTodayPlacement(new TodayPlacement(e.kind, e.id, date, e.slot, i));
            }
            return true;
        });
    }
}
