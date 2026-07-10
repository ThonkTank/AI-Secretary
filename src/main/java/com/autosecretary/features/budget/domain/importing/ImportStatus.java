package com.autosecretary.features.budget.domain.importing;

/**
 * Lifecycle states of a budget import operation.
 * <p>
 * Typically progresses: PENDING → COMPLETED or PENDING → FAILED.
 * </p>
 * <ul>
 *   <li><strong>PENDING:</strong> Import has started but not yet finished. Parsing or validation is in progress.</li>
 *   <li><strong>COMPLETED:</strong> Import succeeded. All transactions have been parsed and persisted.</li>
 *   <li><strong>FAILED:</strong> Import failed. Parsing, validation, or persistence encountered an error.</li>
 * </ul>
 */
public enum ImportStatus {
    PENDING,
    COMPLETED,
    FAILED
}
