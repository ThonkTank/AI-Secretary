package de.thonktank.autosecretary.domain.repository;

import de.thonktank.autosecretary.domain.model.ComboPolicy;

/** Read port for the currently selected combo policy. */
public interface ComboPolicySource {
    ComboPolicy current();

    static ComboPolicySource defaults() { return ComboPolicy::defaults; }
}
