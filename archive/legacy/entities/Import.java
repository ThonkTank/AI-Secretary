package entities;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Kontoauszug-Import mit Claude API Metadata.
 * Für spätere Integration mit Claude für automatische Kategorisierung.
 */
public class Import {

    public Long id;
    public Long accountId;                   // FK zu Account
    public String fileName;                  // Originaler Dateiname
    public String fileHash;                  // SHA256 des Inhalts (Duplikat-Check)
    public LocalDateTime importDate;

    // Zeitraum der importierten Daten
    public LocalDate periodStart;
    public LocalDate periodEnd;

    // Statistiken
    public int totalTransactions;            // Anzahl importierter Transaktionen
    public int newTransactions;              // Davon neu (nicht Duplikat)
    public int autoCategorized;              // Von Claude kategorisiert

    // Claude API Metadata
    public String claudeModel;               // Verwendetes Modell (z.B. "claude-sonnet-4-20250514")
    public Integer claudePromptTokens;       // Token-Verbrauch
    public Integer claudeResponseTokens;
    public Integer processingTimeMs;         // Verarbeitungszeit

    public String rawContent;                // Original-CSV/PDF-Text (für Re-Parsing)
    public String parsedJson;                // Claude's strukturierte Antwort (JSON)

    public ImportStatus status;
    public String errorMessage;              // Fehlermeldung bei FAILED

    public enum ImportStatus {
        PENDING,      // Hochgeladen, wartet auf Verarbeitung
        PROCESSING,   // Wird von Claude verarbeitet
        COMPLETED,    // Erfolgreich importiert
        FAILED        // Fehler bei Verarbeitung
    }

    // ============== BUILDER ==============

    public static class Builder {
        private final Import imp = new Import();

        public Builder(Long accountId, String fileName, String fileHash) {
            imp.accountId = accountId;
            imp.fileName = fileName;
            imp.fileHash = fileHash;
            imp.importDate = LocalDateTime.now();
            imp.status = ImportStatus.PENDING;
            imp.totalTransactions = 0;
            imp.newTransactions = 0;
            imp.autoCategorized = 0;
        }

        public Builder periodStart(LocalDate v) { imp.periodStart = v; return this; }
        public Builder periodEnd(LocalDate v) { imp.periodEnd = v; return this; }
        public Builder rawContent(String v) { imp.rawContent = v; return this; }

        public Import build() { return imp; }
    }

    // ============== STATUS MANAGEMENT ==============

    public void markProcessing() {
        this.status = ImportStatus.PROCESSING;
    }

    public void markCompleted(int total, int newTx, int autoCat) {
        this.status = ImportStatus.COMPLETED;
        this.totalTransactions = total;
        this.newTransactions = newTx;
        this.autoCategorized = autoCat;
    }

    public void markFailed(String error) {
        this.status = ImportStatus.FAILED;
        this.errorMessage = error;
    }

    public void setClaudeMetadata(String model, int promptTokens, int responseTokens, int timeMs, String json) {
        this.claudeModel = model;
        this.claudePromptTokens = promptTokens;
        this.claudeResponseTokens = responseTokens;
        this.processingTimeMs = timeMs;
        this.parsedJson = json;
    }
}
