package com.autosecretary.features.budget.application;

import com.autosecretary.features.budget.domain.BudgetTransaction;
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

    public BudgetImportUseCase(BudgetImportRepository repository,
                               StatementFileParser parser,
                               ExecutorService executor) {
        this.repository = repository;
        this.parser = parser;
        this.executor = executor;
    }

    public void executeAsync(Long accountId,
                             String fileName,
                             byte[] fileBytes,
                             String mimeType,
                             ImportCallback callback) {
        executor.execute(() -> {
            Long importId = null;
            try {
                callback.onProgress("Prüfe Datei...");
                String fileHash = sha256(fileBytes);
                BudgetImportRepository.ImportRecord imp = repository.createImport(accountId, fileName, fileHash);
                importId = imp.id();

                callback.onProgress("Parse Datei...");
                StatementFileParser.ParsedStatement parsed = parser.parse(fileName, fileBytes, mimeType);

                callback.onProgress("Verarbeite Transaktionen...");
                ImportComputation computation = buildTransactions(accountId, imp.id(), parsed.transactions());
                if (!computation.newTransactions.isEmpty()) {
                    repository.saveTransactionsBatch(computation.newTransactions);
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

                callback.onProgress("Suche wiederkehrende Muster...");
                List<BudgetTransaction> accountTransactions = repository.loadTransactionsForAccount(accountId);
                List<RecurringSuggestion> suggestions = RecurringPatternDetector.detectPatterns(accountTransactions);

                callback.onSuccess(new ImportResult(
                        parsed.transactions().size(),
                        computation.newTransactions.size(),
                        computation.duplicates,
                        suggestions
                ));
            } catch (Exception e) {
                if (importId != null) {
                    repository.markImportFailed(importId, e.getMessage());
                }
                callback.onError(e.getMessage());
            }
        });
    }

    private ImportComputation buildTransactions(Long accountId,
                                                Long importId,
                                                List<StatementFileParser.ParsedTransaction> parsedTransactions) {
        List<BudgetTransaction> newTransactions = new ArrayList<>();
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

            Long categoryId = parsed.categoryId();
            if (categoryId == null) {
                categoryId = repository.findDefaultCategoryId(parsed.amountCents() > 0);
            } else {
                autoCategorized++;
            }

            BudgetTransaction tx = new BudgetTransaction.Builder(
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
            List<RecurringSuggestion> recurringSuggestions
    ) {
    }

    private record ImportComputation(List<BudgetTransaction> newTransactions,
                                     int duplicates,
                                     int autoCategorized) {
    }
}
