package de.thonktank.autosecretary.domain.repository;

import java.time.LocalDate;
import java.util.List;

import de.thonktank.autosecretary.domain.model.ComboDecayEvent;
import de.thonktank.autosecretary.domain.model.ComboObligation;

/** Persistence capability for genuine scheduled combo obligations and decay idempotency. */
public interface ComboObligationRepository {
    List<ComboObligation> comboObligations();
    void insertComboObligations(List<ComboObligation> obligations);
    void updateComboObligation(ComboObligation obligation);
    ComboDecayEvent comboDecayEvent(String ownerId, LocalDate eventOn);
    void insertComboDecayEvent(ComboDecayEvent event);
}
