package com.autosecretary.features.budget.application.importing;

import com.autosecretary.features.budget.domain.BudgetImportRepository;
import com.autosecretary.features.budget.domain.importing.ParsedStatement;
import com.autosecretary.features.budget.domain.importing.ParsedTransaction;
import com.autosecretary.features.budget.domain.recurring.RecurringBudgetTransaction;
import com.autosecretary.features.budget.domain.recurring.RecurringPatternDetector;
import com.autosecretary.features.budget.domain.recurring.RecurringSuggestion;
import com.autosecretary.features.budget.domain.TransactionDirection;

import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Importstrecke: Dateiannahme, Parsing, Duplikat-Erkennung via importHash,
 * Batch-Persistierung und abschließende UI-Aktualisierung.
 */
public class BudgetImportUseCase {
    private static final String TAG = "BudgetImportUseCase";

    private final BudgetImportRepository repository;
    private final StatementFileParser parser;
    private final ExecutorService executor;

    public BudgetImportUseCase(BudgetImportRepository repository,
                               StatementFileParser parser,
                               ExecutorService executor) {
        this.repository = repository;
        this.parser = parser;
        this.executor = executor;
    }

    public void executeAsync(String accountId,
                             String fileName,
                             byte[] fileBytes,
                             String mimeType,
                             ImportCallback callback) {
        executor.execute(() -> {
            try {
                ImportResult result = runImportPipeline(accountId, fileName, fileBytes, mimeType);
                callback.onSuccess(result);
            } catch (ImportPipelineException e) {
                if (e.importId() != null) {
                    repository.markImportFailed(e.importId(), e.getMessage());
                }
                callback.onError(e.getMessage());
            }
        });
    }

    ImportResult runImportPipeline(String accountId,
                                   String fileName,
                                   byte[] fileBytes,
                                   String mimeType) {
        String importId = null;
        try {
            String fileHash = sha256(fileBytes);
            BudgetImportRepository.ImportRecord importRecord = repository.createImport(accountId, fileName, fileHash);
            importId = importRecord.id();

            ParsedStatement parsed = parser.parse(fileName, fileBytes, mimeType);

            ImportComputation computation = buildTransactions(accountId, importId, parsed.transactions());
            if (!computation.newTransactions.isEmpty()) {
                repository.saveTransactionsBatch(computation.newTransactions.stream().map(BudgetTransactionMapper::toRecord).toList());
            }

            repository.markImportCompleted(
                    importId,
                    parsed.transactions().size(),
                    computation.newTransactions.size(),
                    computation.autoCategorized,
                    parsed.periodStart(),
                    parsed.periodEnd()
            );

            repository.notifyBudgetDataUpdated();

            // Pattern detection runs after import is fully committed.
            // Failures here must not mark the import as failed.
            List<RecurringSuggestion> suggestions;
            try {
                List<RecurringBudgetTransaction> accountTransactions = repository.loadTransactionsForAccount(accountId).stream()
                        .map(BudgetTransactionMapper::toDomain)
                        .toList();
                suggestions = RecurringPatternDetector.detectPatterns(accountTransactions);
            } catch (Exception e) {
                Log.w(TAG, "Pattern detection failed after import, suggestions skipped", e);
                suggestions = List.of();
            }

            return new ImportResult(
                    parsed.transactions().size(),
                    computation.newTransactions.size(),
                    computation.duplicates,
                    computation.autoCategorized,
                    suggestions
            );
        } catch (IllegalArgumentException e) {
            throw new ImportPipelineException(importId,
                    "Validierungsfehler beim Import: " + safeErrorMessage(e),
                    e);
        } catch (Exception e) {
            throw new ImportPipelineException(importId,
                    "Technischer Fehler beim Import: " + safeErrorMessage(e),
                    e);
        }
    }

    private ImportComputation buildTransactions(String accountId,
                                                String importId,
                                                List<ParsedTransaction> parsedTransactions) {
        List<RecurringBudgetTransaction> newTransactions = new ArrayList<>();
        int duplicates = 0;
        int autoCategorized = 0;

        for (ParsedTransaction parsed : parsedTransactions) {
            String txHash = parsed.importHash();
            if (txHash == null || txHash.isBlank()) {
                txHash = buildTransactionFingerprint(parsed.bookingDate(), parsed.amountCents(), parsed.payee());
            }

            if (repository.existsTransactionByImportHash(txHash)) {
                duplicates++;
                continue;
            }

            String categoryId = parsed.categoryId();
            boolean categoryKnown = categoryId != null && repository.isKnownCategory(categoryId);

            if (categoryKnown) {
                autoCategorized++;
            } else {
                categoryId = repository.findDefaultCategoryId(
                        TransactionDirection.fromAmountCents(parsed.amountCents()));
            }

            newTransactions.add(RecurringBudgetTransaction.forImport(
                    null,
                    accountId,
                    parsed.amountCents(),
                    parsed.bookingDate(),
                    categoryId,
                    parsed.note(),
                    parsed.payee(),
                    txHash,
                    importId,
                    null
            ));
        }
        return new ImportComputation(newTransactions, duplicates, autoCategorized);
    }

    private static String sha256(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String buildTransactionFingerprint(LocalDate date, long amountCents, String payee) {
        String normalized = payee != null ? payee.trim().replace(" ", "") : "";
        String payeePart = normalized.substring(0, Math.min(10, normalized.length()));
        return date + "_" + amountCents + "_" + payeePart;
    }

    public interface ImportCallback {
        void onSuccess(ImportResult result);

        void onError(String errorMessage);
    }

    public record ImportResult(
            int totalTransactions,
            int newTransactions,
            int duplicates,
            int autoCategorized,
            List<RecurringSuggestion> recurringSuggestions
    ) {
    }

    private static class ImportPipelineException extends RuntimeException {
        private final String importId;

        ImportPipelineException(String importId, String userMessage, Throwable cause) {
            super(userMessage, cause);
            this.importId = importId;
        }

        String importId() {
            return importId;
        }
    }

    private record ImportComputation(List<RecurringBudgetTransaction> newTransactions,
                                     int duplicates,
                                     int autoCategorized) {
    }

    private static String safeErrorMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "unbekannte Ursache";
        }
        return message;
    }
}
