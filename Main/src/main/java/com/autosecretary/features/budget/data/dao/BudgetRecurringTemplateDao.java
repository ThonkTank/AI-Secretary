package com.autosecretary.features.budget.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.time.LocalDate;
import java.util.List;

import com.autosecretary.features.budget.data.entity.BudgetRecurringTemplateEntity;
import com.autosecretary.features.budget.domain.recurring.TemplateStatusUpdate;

/**
 * DAO for recurring transaction templates (patterns detected from transaction history).
 *
 * Stores and queries recurring transaction templates (e.g., "Netflix subscription $15.99 monthly").
 * These templates are detected by RecurringPatternDetector and can be used to suggest
 * or auto-book upcoming recurring transactions.
 *
 * A template is active if the user has confirmed it (and not since archived it). The nextDue
 * field tracks when the next instance should occur (used for scheduling suggestions).
 */
@Dao
public interface BudgetRecurringTemplateDao {
    String ACTIVE_EXPENSE_IN_RANGE_FILTER =
            "active = 1 AND type = 'EXPENSE' AND nextDue BETWEEN :fromDate AND :toDate";

    /**
     * Inserts or replaces a recurring template.
     *
     * If a template with the same ID already exists, it is updated.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void write(BudgetRecurringTemplateEntity template);

    /**
     * Retrieves active EXPENSE templates for a specific account with nextDue in the given date range.
     *
     * Used to suggest upcoming recurring transactions to the user.
     *
     * @param accountId the account UUID
     * @param fromDate earliest nextDue (inclusive)
     * @param toDate latest nextDue (inclusive)
     * @return list of matching templates (may be empty)
     */
    @Query("SELECT * FROM budget_recurring_template "
            + "WHERE accountId = :accountId "
            + "AND " + ACTIVE_EXPENSE_IN_RANGE_FILTER)
    List<BudgetRecurringTemplateEntity> readActiveExpenseTemplatesForAccountInRange(String accountId, LocalDate fromDate, LocalDate toDate);

    /**
     * Retrieves active EXPENSE templates across all active accounts with nextDue in the given date range.
     *
     * Excludes templates from archived accounts. Used to generate system-wide recurring transaction
     * suggestions or auto-booking operations.
     *
     * @param fromDate earliest nextDue (inclusive)
     * @param toDate latest nextDue (inclusive)
     * @return list of matching templates (may be empty)
     */
    @Query("SELECT * FROM budget_recurring_template "
            + "WHERE accountId IN (SELECT id FROM budget_account WHERE archived = 0) "
            + "AND " + ACTIVE_EXPENSE_IN_RANGE_FILTER)
    List<BudgetRecurringTemplateEntity> readActiveExpenseTemplatesForActiveAccountsInRange(LocalDate fromDate, LocalDate toDate);

    /**
     * Retrieves all active recurring templates (all directions, all accounts).
     *
     * @return list of all active templates
     */
    @Query("SELECT * FROM budget_recurring_template WHERE active = 1")
    List<BudgetRecurringTemplateEntity> readAllActiveTemplates();

    /**
     * Updates the nextDue date and active status of a single template.
     *
     * @param templateId the template UUID
     * @param nextDue the next scheduled occurrence date
     * @param active true to keep active, false to archive
     */
    @Query("UPDATE budget_recurring_template SET nextDue = :nextDue, active = :active WHERE id = :templateId")
    void writeNextDueAndStatus(String templateId, LocalDate nextDue, boolean active);

    /**
     * Atomically updates the status and nextDue date for a batch of recurring templates.
     *
     * All updates are applied in a single database transaction: if any update fails,
     * all are rolled back. Use when template state changes must be synchronized
     * (e.g., after import completes and recurring patterns are re-detected).
     *
     * @param updates list of template status updates
     */
    @Transaction
    default void updateAllTemplateStatuses(List<TemplateStatusUpdate> updates) {
        for (TemplateStatusUpdate u : updates) {
            writeNextDueAndStatus(u.id(), u.nextDue(), u.active());
        }
    }
}
