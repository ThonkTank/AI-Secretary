package com.autosecretary.features.budget.application;

import com.autosecretary.features.budget.domain.BudgetRepository;

import java.time.LocalDate;

/**
 * Creates and updates internal transfers between budget accounts.
 *
 * <p><strong>Key difference from transactions:</strong>
 * A transfer moves money between two of the user's own accounts (e.g., checking → savings).
 * It creates a paired debit/credit entry: money leaves the source account and enters the target
 * account on the same date. Transactions, by contrast, represent external flows (income/expense).
 *
 * <p><strong>Validation:</strong>
 * <ul>
 *   <li>Source and target accounts must be different</li>
 *   <li>Amount must be positive (no negative transfers)</li>
 *   <li>Booking date must be provided</li>
 * </ul>
 *
 * <p>Returns a Result with either success or a user-facing error message (in German).
 * Both create() and update() methods are available.
 *
 * @see Result for success/error response structure
 */
public class CreateTransferUseCase {
    private final BudgetRepository repository;

    public CreateTransferUseCase(BudgetRepository repository) {
        this.repository = repository;
    }

    public Result execute(String sourceAccountId,
                          String targetAccountId,
                          long amountCents,
                          LocalDate bookingDate,
                          String note) {
        Result validation = validateTransferInput(sourceAccountId, targetAccountId, amountCents, bookingDate);
        if (!validation.success) {
            return validation;
        }

        repository.createTransfer(sourceAccountId, targetAccountId, amountCents, bookingDate, note);
        return Result.ok();
    }

    public Result update(String transactionId,
                         String sourceAccountId,
                         String targetAccountId,
                         long amountCents,
                         LocalDate bookingDate,
                         String note) {
        if (transactionId == null) {
            return Result.error("Ungültige Überweisung.");
        }
        Result validation = validateTransferInput(sourceAccountId, targetAccountId, amountCents, bookingDate);
        if (!validation.success) {
            return validation;
        }
        boolean updated = repository.updateTransfer(transactionId, sourceAccountId, targetAccountId,
                amountCents, bookingDate, note);
        if (!updated) {
            return Result.error("Überweisung ist unvollständig und konnte nicht aktualisiert werden.");
        }
        return Result.ok();
    }

    private Result validateTransferInput(String sourceAccountId,
                                         String targetAccountId,
                                         long amountCents,
                                         LocalDate bookingDate) {
        if (sourceAccountId == null || targetAccountId == null) {
            return Result.error("Bitte Quelle und Ziel wählen.");
        }
        if (sourceAccountId.equals(targetAccountId)) {
            return Result.error("Quell- und Zielkonto müssen unterschiedlich sein.");
        }
        if (amountCents <= 0) {
            return Result.error("Betrag muss größer als 0 sein.");
        }
        if (bookingDate == null) {
            return Result.error("Datum fehlt.");
        }
        return Result.ok();
    }

    public record Result(boolean success, String errorMessage) {
        public static Result ok() {
            return new Result(true, null);
        }

        public static Result error(String message) {
            return new Result(false, message);
        }
    }
}
