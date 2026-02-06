package controller;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executors;

import entities.Category;
import entities.Import;
import entities.Transaction;

/**
 * Orchestriert den Import-Workflow:
 * 1. Datei hashen (Duplikat-Check auf Dateiebene)
 * 2. Import-Entity anlegen (PENDING)
 * 3. Claude API aufrufen
 * 4. Transaktionen erstellen (mit Duplikat-Check)
 * 5. Import-Entity aktualisieren (COMPLETED/FAILED)
 */
public class ImportProcessor {

    private static final String TAG = "ImportProcessor";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Context context;
    private final BudgetManager manager;

    public ImportProcessor(Context context) {
        this.context = context;
        this.manager = new BudgetManager(context);
    }

    // ============== CALLBACK INTERFACE ==============

    public interface ImportCallback {
        void onProgress(String message);
        void onSuccess(ImportResult result);
        void onError(String errorMessage);
    }

    public record ImportResult(
        int totalTransactions,
        int newTransactions,
        int duplicates,
        long processingTimeMs,
        List<RecurringPatternDetector.RecurringCandidate> recurringCandidates
    ) {}

    // ============== MAIN ENTRY POINT ==============

    /**
     * Startet den asynchronen Import-Prozess.
     *
     * @param accountId Ziel-Konto für die Transaktionen
     * @param fileName Originaler Dateiname (für Anzeige)
     * @param fileBytes Datei-Inhalt
     * @param mimeType z.B. "application/pdf"
     * @param apiKey Claude API Key
     * @param callback Callback für Progress/Result/Error
     */
    public void processImportAsync(Long accountId, String fileName, byte[] fileBytes,
                                   String mimeType, String apiKey, ImportCallback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                ImportResult result = processImportSync(accountId, fileName, fileBytes, mimeType, apiKey, callback);
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                Log.e(TAG, "Import failed", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    // ============== INTERNAL PROCESSING ==============

    private ImportResult processImportSync(Long accountId, String fileName, byte[] fileBytes,
                                            String mimeType, String apiKey, ImportCallback callback)
            throws Exception {

        long startTime = System.currentTimeMillis();

        // 1. Datei hashen
        postProgress(callback, "Prüfe auf Duplikate...");
        String fileHash = sha256(fileBytes);

        // 2. Import-Entity anlegen
        postProgress(callback, "Initialisiere Import...");
        Import imp = new Import.Builder(accountId, fileName, fileHash).build();
        imp.markProcessing();
        manager.saveImport(imp);

        // 3. Kategorien laden für Prompt
        List<Category> categories = manager.provideAllCategories();

        // 4. Claude API aufrufen
        postProgress(callback, "Analysiere mit Claude API...");
        ClaudeApiClient client = new ClaudeApiClient();

        // Synchroner Wrapper um den async Call
        final ClaudeApiClient.ParsedStatementResult[] apiResult = new ClaudeApiClient.ParsedStatementResult[1];
        final String[] apiError = new String[1];
        final Object lock = new Object();

        client.parseStatementAsync(apiKey, fileBytes, mimeType, categories, new ClaudeApiClient.ApiCallback() {
            @Override
            public void onSuccess(ClaudeApiClient.ParsedStatementResult result) {
                synchronized (lock) {
                    apiResult[0] = result;
                    lock.notify();
                }
            }

            @Override
            public void onError(String errorMessage) {
                synchronized (lock) {
                    apiError[0] = errorMessage;
                    lock.notify();
                }
            }
        });

        // Auf API-Response warten
        synchronized (lock) {
            while (apiResult[0] == null && apiError[0] == null) {
                lock.wait();
            }
        }

        if (apiError[0] != null) {
            imp.markFailed(apiError[0]);
            manager.saveImport(imp);
            throw new Exception(apiError[0]);
        }

        ClaudeApiClient.ParsedStatementResult parsed = apiResult[0];

        // 5. Claude-Metadata speichern
        imp.setClaudeMetadata(
            "claude-sonnet-4-20250514",
            parsed.promptTokens(),
            parsed.responseTokens(),
            (int) parsed.processingTimeMs(),
            null  // JSON nicht speichern, spart Platz
        );

        // Periode setzen
        if (parsed.periodStart() != null) {
            try {
                imp.periodStart = LocalDate.parse(parsed.periodStart());
            } catch (Exception ignored) {}
        }
        if (parsed.periodEnd() != null) {
            try {
                imp.periodEnd = LocalDate.parse(parsed.periodEnd());
            } catch (Exception ignored) {}
        }

        // 6. Transaktionen verarbeiten
        postProgress(callback, "Erstelle Transaktionen...");
        int totalTx = parsed.transactions().size();
        int newTx = 0;
        int duplicates = 0;
        int autoCategorized = 0;

        for (ClaudeApiClient.ParsedTransaction ptx : parsed.transactions()) {
            // Hash generieren falls nicht vorhanden
            String txHash = ptx.hash();
            if (txHash == null || txHash.isEmpty()) {
                txHash = generateTransactionHash(ptx.date(), ptx.amountCents(), ptx.payee());
            }

            // Duplikat-Check
            if (manager.isDuplicateTransaction(txHash)) {
                duplicates++;
                continue;
            }

            // Transaktion erstellen
            boolean isIncome = ptx.amountCents() > 0;
            Long categoryId = ptx.categoryId();

            // Kategorie validieren (muss existieren und isIncome muss stimmen)
            if (categoryId != null) {
                Category cat = manager.getRepo().fetch(repository.Table.CATEGORIES, categoryId);
                if (cat == null || cat.isIncome != isIncome) {
                    // Fallback auf Standard-Kategorie
                    categoryId = getDefaultCategoryId(isIncome);
                }
                autoCategorized++;
            } else {
                categoryId = getDefaultCategoryId(isIncome);
            }

            Transaction tx = new Transaction.Builder(accountId, ptx.amountCents(), ptx.date(), categoryId)
                .description(ptx.description())
                .payee(ptx.payee())
                .importHash(txHash)
                .importId(imp.id)
                .build();

            manager.createTransactionQuiet(tx);
            newTx++;
        }

        // 7. Import abschließen
        imp.markCompleted(totalTx, newTx, autoCategorized);
        manager.saveImport(imp);

        // Listener benachrichtigen (einmalig nach Batch)
        manager.notifyDataUpdated();

        // 8. Pattern-Erkennung für wiederkehrende Transaktionen
        postProgress(callback, "Suche wiederkehrende Muster...");
        List<Transaction> allAccountTx = manager.getAllTransactionsForAccount(accountId);
        List<RecurringPatternDetector.RecurringCandidate> candidates =
            RecurringPatternDetector.detectPatterns(allAccountTx);

        long processingTime = System.currentTimeMillis() - startTime;
        return new ImportResult(totalTx, newTx, duplicates, processingTime, candidates);
    }

    // ============== HELPER METHODS ==============

    private void postProgress(ImportCallback callback, String message) {
        mainHandler.post(() -> callback.onProgress(message));
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
            // Fallback: Länge + erstes/letztes Byte
            return "len" + data.length + "_" + (data.length > 0 ? data[0] : 0);
        }
    }

    private String generateTransactionHash(LocalDate date, int amountCents, String payee) {
        String payeePart = payee != null && payee.length() > 10
            ? payee.substring(0, 10)
            : (payee != null ? payee : "");
        return date.toString() + "_" + amountCents + "_" + payeePart.replace(" ", "");
    }

    private Long getDefaultCategoryId(boolean isIncome) {
        // "Sonstiges Einkommen" (ID 4) oder "Sonstiges" (ID 20) als Fallback
        // Diese IDs entsprechen den Standard-Kategorien aus seedTestData
        // Besser: Aus DB laden mit Fallback
        List<Category> cats = manager.provideAllCategories();
        for (Category c : cats) {
            if (c.isIncome == isIncome && c.name.startsWith("Sonstiges")) {
                return c.id;
            }
        }
        // Absoluter Fallback: erste passende Kategorie
        for (Category c : cats) {
            if (c.isIncome == isIncome) {
                return c.id;
            }
        }
        return null;
    }
}
