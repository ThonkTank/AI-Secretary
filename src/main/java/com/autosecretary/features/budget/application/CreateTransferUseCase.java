package com.autosecretary.features.budget.application;

import com.autosecretary.features.budget.domain.BudgetRepository;

import java.time.LocalDate;

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
