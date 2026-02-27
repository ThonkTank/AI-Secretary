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
import com.autosecretary.features.budget.domain.RecurringSuggestion;
import com.autosecretary.features.budget.domain.TransactionDirection;
import com.autosecretary.features.budget.domain.TransactionKind;
import com.autosecretary.features.budget.domain.TemplateStatusUpdate;
import com.autosecretary.features.budget.domain.RecurringTemplateScheduler;
import com.autosecretary.features.budget.domain.RecurringScheduleParams;

import java.time.LocalDate;
import java.util.List;

/**
 * Room-Implementierung der BudgetImportRepository-Schnittstelle.
 */
public class BudgetImportRoomRepository implements BudgetImportRepository {
    private final BudgetImportDao importDao;
    private final BudgetRecurringTemplateDao templateDao;
    private final BudgetTransactionDao transactionDao;
    private final BudgetLookupDao lookupDao;
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
        if (transactionIds == null || transactionIds.isEmpty() || templateId == null || templateId.isBlank()) {
            return;
        }
        transactionDao.updateTemplateIdForTransactions(transactionIds, templateId);
    }

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

    private BudgetTransactionEntity toEntity(ImportTransactionRecord record) {
        ImportTransactionType type = record.type();
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

    private ImportTransactionRecord toRecord(BudgetTransactionEntity entity) {
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
