package com.autosecretary.features.task.application.internal.budget;

import com.autosecretary.features.budget.domain.BudgetImportRepository;
import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.budget.domain.TransactionDirection;
import com.autosecretary.features.budget.domain.importing.ImportTransactionRecord;
import com.autosecretary.features.budget.domain.importing.ImportTransactionType;
import com.autosecretary.features.task.application.assistant.AssistantProposals.TransactionDraft;
import com.autosecretary.features.task.application.assistant.AssistantProposals.TransactionImportProposal;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Runs a confirmed {@link TransactionImportProposal} through the same import pipeline the budget
 * feature uses for CSV/PDF files: create an import session, deduplicate against known fingerprints,
 * apply category fallbacks, batch-persist, and mark the session completed (or failed). Depends only
 * on the budget {@code domain} repositories.
 *
 * <p><strong>Threading:</strong> {@link #execute} and {@link #duplicatePreview} touch Room and must
 * be called on the {@code dbExecutor}.
 *
 * <p><strong>Fingerprint contract:</strong> {@link #fingerprint} is a deliberate copy of
 * {@code BudgetImportUseCase.buildTransactionFingerprint} (signed amount, space-stripped 10-char
 * payee prefix). The two MUST stay identical so a chat import and a file import of the same
 * statement dedupe against each other — keep both in sync if either changes.
 */
public class AssistantTransactionImportExecutor {

    private static final int PAYEE_PREFIX_MAX_LENGTH = 10;

    /** Result of an import run, for the German confirmation summary. */
    public record ImportOutcome(int total, int imported, int duplicates, int autoCategorized) {
    }

    private final BudgetImportRepository importRepository;
    private final BudgetRepository budgetRepository;

    public AssistantTransactionImportExecutor(BudgetImportRepository importRepository,
                                              BudgetRepository budgetRepository) {
        this.importRepository = importRepository;
        this.budgetRepository = budgetRepository;
    }

    /** Resolves the proposal's account id, falling back to the default active account (null if none). */
    public String resolveAccountId(String accountIdOrNull) {
        if (accountIdOrNull != null && !accountIdOrNull.isBlank()) {
            return accountIdOrNull;
        }
        return budgetRepository.findDefaultActiveAccountId();
    }

    /** Counts how many drafts already exist for the resolved account (best-effort card preview). */
    public int duplicatePreview(String accountIdOrNull, List<TransactionDraft> drafts) {
        String accountId = resolveAccountId(accountIdOrNull);
        if (accountId == null) {
            return 0;
        }
        Set<String> known = importRepository.findImportHashesForAccount(accountId);
        int duplicates = 0;
        for (TransactionDraft draft : drafts) {
            if (known.contains(fingerprint(draft.date(), draft.signedAmountCents(), draft.payee()))) {
                duplicates++;
            }
        }
        return duplicates;
    }

    public ImportOutcome execute(TransactionImportProposal proposal) {
        String accountId = resolveAccountId(proposal.accountId());
        if (accountId == null) {
            throw new IllegalArgumentException("Kein Konto vorhanden, in das importiert werden kann.");
        }
        BudgetImportRepository.ImportRecord importRecord =
                importRepository.createImport(accountId, proposal.fileName(), proposal.fileHash());
        try {
            Set<String> known = importRepository.findImportHashesForAccount(accountId);
            Set<String> activeCategoryIds = importRepository.findActiveCategoryIds();
            String defaultIncomeCategoryId = importRepository.findDefaultCategoryId(TransactionDirection.INCOME);
            String defaultExpenseCategoryId = importRepository.findDefaultCategoryId(TransactionDirection.EXPENSE);

            List<ImportTransactionRecord> toSave = new ArrayList<>();
            int duplicates = 0;
            int autoCategorized = 0;

            for (TransactionDraft draft : proposal.transactions()) {
                String hash = fingerprint(draft.date(), draft.signedAmountCents(), draft.payee());
                if (known.contains(hash)) {
                    duplicates++;
                    continue;
                }
                TransactionDirection direction = TransactionDirection.fromAmountCents(draft.signedAmountCents());
                boolean knownCategory = draft.categoryId() != null && activeCategoryIds.contains(draft.categoryId());
                String categoryId = knownCategory
                        ? draft.categoryId()
                        : (direction == TransactionDirection.INCOME ? defaultIncomeCategoryId : defaultExpenseCategoryId);
                toSave.add(new ImportTransactionRecord(
                        new ImportTransactionRecord.TransactionData(
                                UUID.randomUUID().toString(),
                                accountId,
                                categoryId,
                                ImportTransactionType.fromDirection(direction),
                                Math.abs(draft.signedAmountCents()),
                                draft.date(),
                                draft.note(),
                                draft.payee()),
                        new ImportTransactionRecord.ImportMetadata(hash, importRecord.id(), null)));
                if (knownCategory) {
                    autoCategorized++;
                }
            }

            if (!toSave.isEmpty()) {
                importRepository.saveTransactionsBatch(toSave);
            }
            importRepository.markImportCompleted(importRecord.id(), new BudgetImportRepository.CompletionData(
                    proposal.transactions().size(), toSave.size(), autoCategorized,
                    proposal.periodStart(), proposal.periodEnd()));
            return new ImportOutcome(proposal.transactions().size(), toSave.size(), duplicates, autoCategorized);
        } catch (RuntimeException e) {
            importRepository.markImportFailed(importRecord.id(), e.getMessage());
            throw e;
        }
    }

    /**
     * Deduplication fingerprint: {@code date_signedAmountCents_payeePrefix} (payee trimmed,
     * spaces removed, first 10 chars). Keep identical to
     * {@code BudgetImportUseCase.buildTransactionFingerprint}.
     */
    static String fingerprint(LocalDate date, long signedAmountCents, String payee) {
        String normalized = payee != null ? payee.trim().replace(" ", "") : "";
        String payeePart = normalized.substring(0, Math.min(PAYEE_PREFIX_MAX_LENGTH, normalized.length()));
        return date + "_" + signedAmountCents + "_" + payeePart;
    }
}
