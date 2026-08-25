package de.thonktank.autosecretary.domain.model;

/** Defines whether genuine missed dates collapse into one obligation or remain a queue. */
public enum MissedOccurrenceMode {
    COLLAPSE,
    ACCUMULATE
}
