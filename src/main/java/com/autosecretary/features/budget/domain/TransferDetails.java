package com.autosecretary.features.budget.domain;

import java.time.LocalDate;

/**
 * Encapsulates the details of a transfer: source and target accounts,
 * amount in cents, booking date, and optional note.
 * Used to reduce parameter count in transfer creation/update methods.
 */
public record TransferDetails(String sourceAccountId, String targetAccountId,
                               long amountCents, LocalDate bookingDate, String note) {}
