package com.autosecretary.features.budget.application.importing;

import com.autosecretary.features.budget.domain.BudgetImportRepository;
import com.autosecretary.features.budget.domain.importing.ImportTransactionType;
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
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Orchestrates the end-to-end import pipeline: parse → deduplicate → map → persist → detect patterns.
 *
 * <p>When the user uploads a bank statement (CSV or PDF):
 * <ol>
 *   <li>Delegates file parsing to {@link StatementFileParser} (CSV locally, PDF via Claude API)</li>
 *   <li>Deduplicates using {@code importHash} to prevent re-importing the same file</li>
 *   <li>Maps/enriches transactions with account context and category resolution</li>
 *   <li>Persists to database via {@link BudgetImportRepository}</li>
 *   <li>Runs pattern detection to suggest recurring transactions (best-effort; failures don't fail the import)</li>
 * </ol>
 *
 * <p>Runs asynchronously on a background executor; results via callback.
 * Callback receives either success (with import summary + recurring suggestions) or error.
 *
 * @see README.md for the full import pipeline documentation
 * @see BudgetImportRepository for database integration
 * @see StatementFileParser for parsing details
 */
public class BudgetImportUseCase {
    private static final String TAG = "BudgetImportUseCase";
    private static final int PAYEE_PREFIX_MAX_LENGTH = 10;

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

    private ImportResult runImportPipeline(String accountId,
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

            repository.markImportCompleted(importId, new BudgetImportRepository.CompletionData(
                    parsed.transactions().size(),
                    computation.newTransactions.size(),
                    computation.recognizedCategories,
                    parsed.periodStart(),
                    parsed.periodEnd()
            ));

            // Pattern detection runs after import is fully committed.
            // Failures here must not mark the import as failed.
            List<RecurringSuggestion> suggestions = detectRecurringPatternsForAccount(accountId);

            return new ImportResult(
                    parsed.transactions().size(),
                    computation.newTransactions.size(),
                    computation.duplicates,
                    computation.recognizedCategories,
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

    /**
     * Enriches parsed transactions: deduplicates, resolves categories, builds domain objects ready for persistence.
     *
     * <p>Pre-loads known import hashes, active category IDs, and default category IDs in batch
     * queries before the loop to avoid per-transaction DB round-trips.
     *
     * <p>For each transaction:
     * <ol>
     *   <li>Computes an importHash (from file metadata or generates a fallback fingerprint)</li>
     *   <li>Checks if this hash already exists → skip if duplicate</li>
     *   <li>Resolves the category (uses provided category if known, else assigns default)</li>
     *   <li>Builds a {@link RecurringBudgetTransaction} for persistence</li>
     * </ol>
     *
     * @param accountId target account for all transactions
     * @param importId batch ID grouping these transactions (for history/audit)
     * @param parsedTransactions output from {@link StatementFileParser}
     * @return computation result with new transactions, duplicate count, auto-categorized count
     */
    private ImportComputation buildTransactions(String accountId,
                                                String importId,
                                                List<ParsedTransaction> parsedTransactions) {
        // Batch-load lookup data before the loop: 3 queries total instead of up to 3×N.
        Set<String> knownHashes = repository.findImportHashesForAccount(accountId);
        Set<String> activeCategoryIds = repository.findActiveCategoryIds();
        String defaultIncomeCategoryId = repository.findDefaultCategoryId(TransactionDirection.INCOME);
        String defaultExpenseCategoryId = repository.findDefaultCategoryId(TransactionDirection.EXPENSE);

        List<RecurringBudgetTransaction> newTransactions = new ArrayList<>();
        int duplicates = 0;
        int recognizedCategories = 0;

        for (ParsedTransaction parsed : parsedTransactions) {
            // If the parser extracted an importHash (e.g., from PDF header), use it for deduplication.
            // Otherwise, generate a fingerprint from date+amount+payee for duplicate detection.
            // The fingerprint is less reliable than a parser-provided hash, but provides a fallback
            // for formats (like CSV) that don't include an explicit deduplication key.
            String importHash = parsed.importHash();
            String txHash = (importHash != null && !importHash.isBlank())
                    ? importHash
                    : buildTransactionFingerprint(parsed.bookingDate(), parsed.amountCents(), parsed.payee());

            // Check if a transaction with this hash was already imported (avoid duplicates if the user re-imports the same file).
            if (knownHashes.contains(txHash)) {
                duplicates++;
                continue;
            }

            // Resolve category and track recognition.
            boolean isKnown = parsed.categoryId() != null && activeCategoryIds.contains(parsed.categoryId());
            TransactionDirection direction = TransactionDirection.fromAmountCents(parsed.amountCents());
            String categoryId = isKnown
                    ? parsed.categoryId()
                    : (direction == TransactionDirection.INCOME ? defaultIncomeCategoryId : defaultExpenseCategoryId);

            newTransactions.add(RecurringBudgetTransaction.forImport(
                    /* id= */ null,
                    accountId,
                    parsed.amountCents(),
                    parsed.bookingDate(),
                    categoryId,
                    parsed.note(),
                    parsed.payee(),
                    txHash,
                    importId,
                    /* parentRecurringId= */ null
            ));

            if (isKnown) {
                recognizedCategories++;
            }
        }
        return new ImportComputation(newTransactions, duplicates, recognizedCategories);
    }

    /**
     * Computes the SHA-256 hash of the given byte array.
     *
     * @param data the data to hash (must not be null)
     * @return the hex-encoded SHA-256 hash
     * @throws IllegalArgumentException if data is null
     * @throws IllegalStateException if SHA-256 algorithm is unavailable
     */
    private static String sha256(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Generates a deduplication fingerprint from transaction metadata (date, amount, payee).
     * Used when the statement file doesn't provide an explicit {@code importHash}.
     *
     * <p>Normalizes payee by removing spaces ("John Doe" → "JohnDoe") to match similar payee names,
     * then takes the first 10 characters to avoid hash collisions on very long payees while remaining
     * human-readable for debugging.
     *
     * <p><strong>WARNING:</strong> This is a heuristic and not collision-proof. For high-precision
     * deduplication, prefer an explicit importHash from the statement file itself.
     *
     * @param date transaction booking date
     * @param amountCents signed transaction amount
     * @param payee transaction payee (may be null)
     * @return fingerprint formatted as "date_amountCents_payeePrefix"; e.g., "2024-01-15_-5000_Amazon"
     */
    private static String buildTransactionFingerprint(LocalDate date, long amountCents, String payee) {
        String normalized = payee != null ? payee.trim().replace(" ", "") : "";
        String payeePart = normalized.substring(0, Math.min(PAYEE_PREFIX_MAX_LENGTH, normalized.length()));
        return date + "_" + amountCents + "_" + payeePart;
    }

    /**
     * Detects recurring patterns in transactions for a given account (best-effort; failures are logged and ignored).
     *
     * <p>Loads all transactions from the account, filters out transfers (which are internal account movements,
     * not income/expense), and analyzes them for recurring patterns. Failures do not affect the import result.
     *
     * @param accountId the account to analyze
     * @return list of recurring suggestions, or empty list if detection fails
     */
    private List<RecurringSuggestion> detectRecurringPatternsForAccount(String accountId) {
        try {
            // Exclude TRANSFER records: BudgetTransactionMapper.toDomain() throws on them
            // by design (transfers are internal account movements, not income/expense).
            List<RecurringBudgetTransaction> accountTransactions = repository.findTransactionsForAccount(accountId).stream()
                    .filter(r -> r.type() != ImportTransactionType.TRANSFER)
                    .map(BudgetTransactionMapper::toDomain)
                    .toList();
            return RecurringPatternDetector.detectPatterns(accountTransactions);
        } catch (Exception e) {
            Log.w(TAG, "Pattern detection failed after import, suggestions skipped", e);
            return List.of();
        }
    }

    public interface ImportCallback {
        void onSuccess(ImportResult result);

        void onError(String errorMessage);
    }

    public record ImportResult(
            int totalTransactions,
            int newTransactions,
            int duplicates,
            int recognizedCategories,
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
                                     int recognizedCategories) {
    }

    private static String safeErrorMessage(Exception exception) {
        String message = exception.getMessage();
        return (message == null || message.isBlank()) ? "unbekannte Ursache" : message;
    }
}
