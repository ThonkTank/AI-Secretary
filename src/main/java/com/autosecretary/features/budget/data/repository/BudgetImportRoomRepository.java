package com.autosecretary.features.budget.data.repository;

import com.autosecretary.features.budget.data.dao.BudgetLookupDao;
import com.autosecretary.features.budget.data.dao.BudgetTransactionDao;
import com.autosecretary.features.budget.data.entity.BudgetTransactionEntity;
import com.autosecretary.features.budget.data.dao.BudgetImportDao;
import com.autosecretary.features.budget.data.entity.BudgetImportEntity;
import com.autosecretary.features.budget.data.dao.BudgetRecurringTemplateDao;
import com.autosecretary.features.budget.data.entity.BudgetRecurringTemplateEntity;
import com.autosecretary.features.budget.domain.BudgetImportRepository;
import com.autosecretary.features.budget.domain.importing.ImportCategory;
import com.autosecretary.features.budget.domain.importing.ImportTransactionRecord;
import com.autosecretary.features.budget.domain.importing.ImportTransactionType;
import com.autosecretary.features.budget.domain.recurring.RecurringSuggestion;
import com.autosecretary.features.budget.domain.TransactionDirection;
import com.autosecretary.features.budget.domain.TransactionKind;
import com.autosecretary.features.budget.domain.recurring.TemplateStatusUpdate;
import com.autosecretary.features.budget.domain.recurring.RecurringTemplateScheduler;
import com.autosecretary.features.budget.domain.recurring.RecurringScheduleParams;

import java.time.LocalDate;
import java.util.List;

/**
 * Room-backed implementation of the BudgetImportRepository interface.
 *
 * Manages the import workflow: creates import records, persists imported transactions,
 * detects recurring payment patterns, and synchronizes recurring template state.
 *
 * Import phases: parse file → create ImportRecord → persist transactions → detect patterns →
 * create recurring templates → synchronize state → notify observers.
 *
 * Injected via dependency injection; accessed by the import application service.
 */
public class BudgetImportRoomRepository implements BudgetImportRepository {
    private final BudgetImportDao importDao;
    private final BudgetRecurringTemplateDao templateDao;
    private final BudgetTransactionDao transactionDao;
    private final BudgetLookupDao lookupDao;
    /** Callback invoked after budget data changes. Typically triggers UI refresh or
     *  re-fetch of dependent data (balance, recurring templates, etc.). */
    private final Runnable onBudgetDataUpdated;

    public BudgetImportRoomRepository(BudgetImportDao importDao,
                                       BudgetRecurringTemplateDao templateDao,
                                       BudgetTransactionDao transactionDao,
                                       BudgetLookupDao lookupDao,
                                       Runnable onBudgetDataUpdated) {
        this.importDao = importDao;
        this.templateDao = templateDao;
        this.transactionDao = transactionDao;
        this.lookupDao = lookupDao;
        this.onBudgetDataUpdated = onBudgetDataUpdated;
    }

    @Override
    public ImportRecord createImport(String accountId, String fileName, String fileHash) {
        BudgetImportEntity entity = new BudgetImportEntity(accountId, fileName, fileHash);
        importDao.insert(entity);
        return ImportRecord.pending(entity.id, entity.accountId, entity.fileName, entity.fileHash);
    }

    @Override
    public void markImportCompleted(String importId, int totalTransactions, int importedTransactions,
                                     int autoCategorized, LocalDate periodStart, LocalDate periodEnd) {
        importDao.markCompleted(importId, totalTransactions, importedTransactions,
                autoCategorized, periodStart, periodEnd);
        // Imports may create new transactions that match recurring patterns or complete
        // existing patterns. Synchronize template state to reflect current conditions.
        synchronizeRecurringTemplateState(LocalDate.now());
    }

    @Override
    public void markImportFailed(String importId, String errorMessage) {
        importDao.markFailed(importId, errorMessage);
    }

    @Override
    public boolean existsTransactionByImportHash(String importHash) {
        return transactionDao.existsByImportHash(importHash);
    }

    @Override
    public String findDefaultCategoryId(TransactionDirection direction) {
        return lookupDao.findDefaultCategoryId(direction);
    }

    @Override
    public boolean isKnownCategory(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            return false;
        }
        return lookupDao.findCategoryById(categoryId) != null;
    }

    @Override
    public List<ImportCategory> loadActiveCategoriesForImport() {
        return lookupDao.findActiveCategories().stream()
                .map(category -> new ImportCategory(category.id, category.name, category.direction))
                .toList();
    }

    @Override
    public void saveTransactionsBatch(List<ImportTransactionRecord> transactions) {
        transactionDao.insertAll(transactions.stream().map(this::toEntity).toList());
        lookupDao.rebuildAllAccountBalances();
    }

    @Override
    public List<ImportTransactionRecord> loadTransactionsForAccount(String accountId) {
        return transactionDao.findByAccountId(accountId).stream().map(this::toRecord).toList();
    }

    @Override
    public String createRecurringTemplate(RecurringSuggestion suggestion, String accountId,
                                           LocalDate nextDueDate) {
        LocalDate due = nextDueDate != null ? nextDueDate : LocalDate.now();
        BudgetRecurringTemplateEntity entity = BudgetRecurringTemplateEntity.fromSuggestion(
                suggestion,
                accountId,
                due
        );
        templateDao.insert(entity);
        return entity.id;
    }

    @Override
    public void linkTransactionsToTemplate(List<String> transactionIds, String templateId) {
        // Silent no-op if parameters are invalid. Acceptable because some imported
        // transactions may not have matching templates. The operation is idempotent
        // (either the link is made or not), so better to silently skip than throw.
        if (transactionIds == null || transactionIds.isEmpty() || templateId == null || templateId.isBlank()) {
            return;
        }
        transactionDao.updateTemplateIdForTransactions(transactionIds, templateId);
    }

    /**
     * Recomputes {@code nextDue} and {@code active} status for all active recurring templates.
     *
     * For each active template, {@link RecurringTemplateScheduler#computeStatusUpdates} advances
     * {@code nextDue} past {@code referenceDate} according to the template's schedule
     * (recurringType + recurringValue/recurringDayOfWeek). Templates whose nextDue has fallen
     * far in the past are deactivated.
     *
     * Called automatically after every import completes, because new transactions may extend
     * or close recurring patterns. Safe to call at any time; all updates are applied atomically.
     *
     * @param referenceDate the "today" date used to advance nextDue past any missed due dates
     */
    @Override
    public void synchronizeRecurringTemplateState(LocalDate referenceDate) {
        List<BudgetRecurringTemplateEntity> templates = templateDao.findAllActiveTemplates();
        List<RecurringScheduleParams> params = templates.stream()
                .map(t -> new RecurringScheduleParams(
                        t.id, t.nextDue, t.recurringType, t.recurringDayOfWeek, t.recurringValue))
                .toList();
        List<TemplateStatusUpdate> updates = RecurringTemplateScheduler.computeStatusUpdates(params, referenceDate);
        templateDao.updateAllTemplateStatuses(updates);
    }

    @Override
    public void notifyBudgetDataUpdated() {
        onBudgetDataUpdated.run();
    }

    /**
     * Converts domain ImportTransactionRecord to persistence entity for storage.
     *
     * Note: ImportTransactionType (domain concept representing import semantics: STANDARD,
     * TRANSFER) maps to TransactionDirection (INCOME/EXPENSE) and TransactionKind
     * (STANDARD/INTERNAL_TRANSFER). This separation exists because the domain layer
     * cares about import context (what kind of transaction was imported?), while the
     * persistence layer cares about account properties (is it income or expense? is it
     * a transfer between accounts?).
     */
    private BudgetTransactionEntity toEntity(ImportTransactionRecord record) {
        ImportTransactionType type = record.type();
        // Type should never be null during normal operation; this is an invariant check.
        // Throw to surface bugs in the import pipeline early rather than producing
        // invalid entities that would corrupt the database.
        if (type == null) {
            throw new IllegalArgumentException("Transaction type must not be null for record: " + record.id());
        }
        TransactionDirection direction = type.toDirection();
        TransactionKind kind = type.toKind();

        BudgetTransactionEntity entity = new BudgetTransactionEntity(
                record.accountId(),
                record.categoryId(),
                direction,
                record.amountCents(),
                record.bookingDate()
        );
        entity.id = record.id();
        entity.transactionKind = kind;
        entity.note = record.note();
        entity.importHash = record.importHash();
        entity.payee = record.payee();
        entity.importId = record.importId();
        entity.templateId = record.templateId();
        return entity;
    }

    /**
     * Reconstructs domain ImportTransactionRecord from persisted entity.
     *
     * Reverse of {@link #toEntity(ImportTransactionRecord)}. Maps transactionKind
     * and direction back to ImportTransactionType to restore the import context.
     */
    private ImportTransactionRecord toRecord(BudgetTransactionEntity entity) {
        // Reconstruct ImportTransactionType from persisted kind and direction fields
        ImportTransactionType type = switch (entity.transactionKind) {
            case INTERNAL_TRANSFER -> ImportTransactionType.TRANSFER;
            case STANDARD -> ImportTransactionType.fromDirection(entity.direction);
        };
        return new ImportTransactionRecord(
                entity.id,
                entity.accountId,
                entity.categoryId,
                type,
                entity.amountCents,
                entity.bookingDate,
                entity.note,
                entity.importHash,
                entity.payee,
                entity.importId,
                entity.templateId
        );
    }
}
