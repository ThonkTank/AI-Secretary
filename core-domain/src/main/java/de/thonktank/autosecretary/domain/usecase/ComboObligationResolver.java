package de.thonktank.autosecretary.domain.usecase;

import de.thonktank.autosecretary.domain.model.ComboObligation;
import de.thonktank.autosecretary.domain.model.MissedOccurrenceMode;
import de.thonktank.autosecretary.domain.model.Occurrence;
import de.thonktank.autosecretary.domain.model.Task;
import de.thonktank.autosecretary.domain.repository.TodayRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Resolves attendance obligations independently from the points awarded for that attendance. */
final class ComboObligationResolver {
    private final TodayRepository repository;

    ComboObligationResolver(TodayRepository repository) {
        this.repository = repository;
    }

    void resolve(String ownerId, Task task, Occurrence occurrence, LocalDate date) {
        if (task == null || occurrence == null) return;
        List<ComboObligation> matches = open(ownerId, occurrence);
        if (matches.isEmpty()) return;
        if (task.missedOccurrenceMode == MissedOccurrenceMode.ACCUMULATE) {
            ComboObligation selected = null;
            for (ComboObligation value : matches)
                if (value.occurrenceId.equals(occurrence.id)) { selected = value; break; }
            if (selected == null) selected = matches.get(0);
            repository.updateComboObligation(selected.resolve(date));
            return;
        }
        for (ComboObligation value : matches)
            repository.updateComboObligation(value.resolve(date));
    }

    void reopen(String ownerId, Occurrence occurrence, LocalDate date) {
        if (occurrence == null) return;
        for (ComboObligation value : repository.comboObligations())
            if (value.ownerId.equals(ownerId) && value.slot == occurrence.slot
                    && value.occurrenceId.equals(occurrence.id)
                    && value.state == ComboObligation.State.RESOLVED
                    && date.equals(value.resolvedOn))
                repository.updateComboObligation(value.reopen());
    }

    private List<ComboObligation> open(String ownerId, Occurrence occurrence) {
        List<ComboObligation> result = new ArrayList<>();
        for (ComboObligation value : repository.comboObligations())
            if (value.ownerId.equals(ownerId) && value.slot == occurrence.slot
                    && value.state == ComboObligation.State.OPEN)
                result.add(value);
        result.sort(Comparator.comparing((ComboObligation value) -> value.scheduledOn)
                .thenComparing(value -> value.id));
        return result;
    }
}
