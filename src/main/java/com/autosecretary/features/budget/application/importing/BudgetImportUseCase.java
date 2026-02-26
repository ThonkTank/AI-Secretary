package com.autosecretary.features.budget.application.importing;

import com.autosecretary.features.budget.domain.BudgetImportRepository;
import com.autosecretary.features.budget.domain.RecurringBudgetTransaction;
import com.autosecretary.features.budget.domain.RecurringPatternDetector;
import com.autosecretary.features.budget.domain.RecurringSuggestion;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Importstrecke: Dateiannahme, Parsing, Duplikat-Erkennung via importHash,
 * Batch-Persistierung und abschließende UI-Aktualisierung.
 */
public class BudgetImportUseCase {
    private final BudgetImportRepository repository;
    private final StatementFileParser parser;
    private final ExecutorService executor;
    private final BudgetTransactionMapper mapper;

    public BudgetImportUseCase(BudgetImportRepository repository,
                               StatementFileParser parser,
                               ExecutorService executor) {
        this(repository, parser, executor, new BudgetTransactionMapper());
    }

    BudgetImportUseCase(BudgetImportRepository repository,
                        StatementFileParser parser,
                        ExecutorService executor,
                        BudgetTransactionMapper mapper) {
        this.repository = repository;
        this.parser = parser;
        this.executor = executor;
        this.mapper = mapper;
    }

    public void executeAsync(String accountId,
                             String fileName,
                             byte[] fileBytes,
                             String mimeType,
                             ImportCallback callback) {
        executor.execute(() -> {
            try {
                callback.onProgress("Starte Import...");
                callback.onProgress("Datei wird analysiert...");
                ImportPipelineResult pipelineResult = runImportPipeline(accountId, fileName, fileBytes, mimeType);
                callback.onProgress("Import abgeschlossen.");

                callback.onSuccess(new ImportResult(
                        pipelineResult.totalTransactions(),
                        pipelineResult.newTransactions(),
                        pipelineResult.duplicates(),
                        pipelineResult.autoCategorized(),
                        pipelineResult.recurringSuggestions()
                ));
            } catch (ImportPipelineException e) {
                if (e.importId() != null) {
                    repository.markImportFailed(e.importId(), e.userMessage());
                }
                callback.onError(e.userMessage());
            }
        });
    }

    ImportPipelineResult runImportPipeline(String accountId,
                                           String fileName,
                                           byte[] fileBytes,
                                           String mimeType) {
        String importId = null;
        try {
            String fileHash = sha256(fileBytes);
            BudgetImportRepository.ImportRecord importRecord = repository.createImport(accountId, fileName, fileHash);
            importId = importRecord.id();

            StatementFileParser.ParsedStatement parsed = parser.parse(fileName, fileBytes, mimeType);

            ImportComputation computation = buildTransactions(accountId, importId, parsed.transactions());
            if (!computation.newTransactions.isEmpty()) {
                repository.saveTransactionsBatch(computation.newTransactions.stream().map(mapper::toRecord).toList());
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

            List<RecurringBudgetTransaction> accountTransactions = repository.loadTransactionsForAccount(accountId).stream()
                    .map(mapper::toDomain)
                    .toList();
            List<RecurringSuggestion> suggestions = RecurringPatternDetector.detectPatterns(accountTransactions);

            return new ImportPipelineResult(
                    parsed.transactions().size(),
                    computation.newTransactions.size(),
                    computation.duplicates,
                    computation.autoCategorized,
                    parsed.periodStart(),
                    parsed.periodEnd(),
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
                                                List<StatementFileParser.ParsedTransaction> parsedTransactions) {
        List<RecurringBudgetTransaction> newTransactions = new ArrayList<>();
        int duplicates = 0;
        int autoCategorized = 0;

        for (StatementFileParser.ParsedTransaction parsed : parsedTransactions) {
            String txHash = parsed.importHash();
            if (txHash == null || txHash.isBlank()) {
                txHash = generateTransactionHash(parsed.date(), parsed.amountCents(), parsed.payee());
            }

            if (repository.existsTransactionByImportHash(txHash)) {
                duplicates++;
                continue;
            }

            String categoryId = parsed.categoryId();
            boolean hasAutoCategory = categoryId != null && repository.isKnownCategory(categoryId);

            if (!hasAutoCategory) {
                categoryId = repository.findDefaultCategoryId(parsed.amountCents() > 0);
            } else {
                autoCategorized++;
            }

            RecurringBudgetTransaction tx = new RecurringBudgetTransaction.Builder(
                    accountId,
                    parsed.amountCents(),
                    parsed.date(),
                    categoryId
            )
                    .description(parsed.description())
                    .payee(parsed.payee())
                    .importHash(txHash)
                    .importId(importId)
                    .build();

            newTransactions.add(tx);
        }
        return new ImportComputation(newTransactions, duplicates, autoCategorized);
    }

    private String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "len" + data.length + "_" + (data.length > 0 ? data[0] : 0);
        }
    }

    private String generateTransactionHash(LocalDate date, int amountCents, String payee) {
        String payeePart;
        if (payee == null) {
            payeePart = "";
        } else {
            String trimmed = payee.trim().replace(" ", "");
            payeePart = trimmed.length() > 10 ? trimmed.substring(0, 10) : trimmed;
        }
        return date + "_" + amountCents + "_" + payeePart;
    }

    public interface ImportCallback {
        void onProgress(String message);

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

    record ImportPipelineResult(
            int totalTransactions,
            int newTransactions,
            int duplicates,
            int autoCategorized,
            LocalDate periodStart,
            LocalDate periodEnd,
            List<RecurringSuggestion> recurringSuggestions
    ) {
    }

    private static class ImportPipelineException extends RuntimeException {
        private final String importId;
        private final String userMessage;

        ImportPipelineException(String importId, String userMessage, Throwable cause) {
            super(userMessage, cause);
            this.importId = importId;
            this.userMessage = userMessage;
        }

        String importId() {
            return importId;
        }

        String userMessage() {
            return userMessage;
        }
    }

    private record ImportComputation(List<RecurringBudgetTransaction> newTransactions,
                                     int duplicates,
                                     int autoCategorized) {
    }

    private String safeErrorMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "unbekannte Ursache";
        }
        return message;
    }
}
