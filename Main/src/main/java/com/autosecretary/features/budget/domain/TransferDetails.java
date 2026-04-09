package com.autosecretary.features.budget.domain;

import java.time.LocalDate;

/**
 * Encapsulates the details of a transfer: source and target accounts,
 * amount in cents, booking date, and optional note.
 * Used to reduce parameter count in transfer creation/update methods.
 *
 * <p>{@code amountCents} is always a <b>positive absolute value</b>. The data layer
 * automatically debits the source account and credits the target account by this amount;
 * do not negate it before passing it here.
 */
public record TransferDetails(String sourceAccountId, String targetAccountId,
                               long amountCents, LocalDate bookingDate, String note) {}
