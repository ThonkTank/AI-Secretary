package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.Clock;
import de.thonktank.autosecretary.domain.model.ComboDecayEvent;
import de.thonktank.autosecretary.domain.model.ComboDecayTrigger;
import de.thonktank.autosecretary.domain.model.ComboObligation;
import de.thonktank.autosecretary.domain.model.ComboPolicy;
import de.thonktank.autosecretary.domain.model.ComboProgress;
import de.thonktank.autosecretary.domain.model.RewardBooking;
import de.thonktank.autosecretary.domain.repository.ComboPolicySource;
import de.thonktank.autosecretary.domain.repository.TodayRepository;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Books decay only for unresolved genuine schedule dates, never for neutral carry days. */
public final class ApplyComboDecay {
    private final TodayRepository today;
    private final TransactionRunner transactions;
    private final ComboPolicySource policies;
    private final Clock clock;

    public ApplyComboDecay(TodayRepository today, TransactionRunner transactions,
                    Clock clock) {
        this(today, transactions, clock, ComboPolicySource.defaults());
    }

    public ApplyComboDecay(TodayRepository today, TransactionRunner transactions,
                    Clock clock,
                    ComboPolicySource policies) {
        this.today = today;
        this.transactions = transactions;
        this.policies = policies;
        this.clock = clock;
    }

    public boolean execute() {
        return transactions.inTransaction(() -> {
            ComboPolicy policy = policies.current();
            Map<String, List<ComboObligation>> openByOwner = new LinkedHashMap<>();
            for (ComboObligation obligation : today.comboObligations())
                if (obligation.state == ComboObligation.State.OPEN)
                    openByOwner.computeIfAbsent(obligation.ownerId,
                            ignored -> new ArrayList<>()).add(obligation);
            boolean changed = false;
            for (List<ComboObligation> values : openByOwner.values()) {
                values.sort(Comparator.comparing((ComboObligation value) -> value.scheduledOn)
                        .thenComparingInt(value -> value.slot.rank).thenComparing(value -> value.id));
                for (LocalDate eventOn : candidates(values, policy.trigger, clock.today()))
                    changed |= evaluate(values, eventOn, policy);
            }
            return changed;
        });
    }

    private boolean evaluate(List<ComboObligation> values, LocalDate eventOn,
                             ComboPolicy policy) {
        ComboObligation representative = representative(values, eventOn);
        if (representative == null
                || today.comboDecayEvent(representative.ownerId, eventOn) != null)
            return false;
        String bookingId = null;
        ComboProgress current = today.combo(representative.ownerId);
        if (current != null && policy.decayPoints > 0) {
            ComboProgress.Change change = current.change(-policy.decayPoints, eventOn);
            today.putCombo(change.progress);
            if (change.appliedDelta != 0) {
                bookingId = bookingId(representative.ownerId, eventOn);
                today.insertRewardBooking(new RewardBooking(bookingId, bookingId,
                        representative.occurrenceId, null, representative.ownerId,
                        RewardBooking.Kind.COMBO_DECAY,
                        representative.kind == ComboProgress.Kind.TASK
                                ? RewardBooking.Target.HEAD : RewardBooking.Target.VESSEL,
                        0, change.appliedDelta, eventOn, null));
            }
        }
        today.insertComboDecayEvent(new ComboDecayEvent(representative.ownerId,
                eventOn, bookingId));
        return true;
    }

    private static ComboObligation representative(List<ComboObligation> values,
                                                   LocalDate eventOn) {
        for (ComboObligation value : values)
            if (value.scheduledOn.isBefore(eventOn)) return value;
        return null;
    }

    private static Set<LocalDate> candidates(List<ComboObligation> values,
                                             ComboDecayTrigger trigger, LocalDate today) {
        Set<LocalDate> result = new TreeSet<>();
        if (values.isEmpty()) return result;
        if (trigger == ComboDecayTrigger.MISSED_OCCURRENCE) {
            for (ComboObligation value : values) {
                LocalDate event = value.scheduledOn.plusDays(1);
                if (!event.isAfter(today)) result.add(event);
            }
            return result;
        }
        if (trigger == ComboDecayTrigger.NEXT_SCHEDULED_OCCURRENCE) {
            LocalDate previous = null;
            for (ComboObligation value : values) {
                if (previous != null && !value.scheduledOn.equals(previous)
                        && !value.scheduledOn.isAfter(today)) result.add(value.scheduledOn);
                previous = value.scheduledOn;
            }
            return result;
        }
        LocalDate event = values.get(0).scheduledOn.plusDays(1);
        while (!event.isAfter(today)) {
            result.add(event);
            event = event.plusDays(1);
        }
        return result;
    }

    private static String bookingId(String ownerId, LocalDate eventOn) {
        return "combo-decay:" + ownerId + ':' + eventOn;
    }
}
