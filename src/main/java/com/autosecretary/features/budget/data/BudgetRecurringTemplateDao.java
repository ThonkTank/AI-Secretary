package com.autosecretary.features.budget.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.time.LocalDate;
import java.util.List;

@Dao
public interface BudgetRecurringTemplateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(BudgetRecurringTemplateEntity template);

    @Query("SELECT * FROM budget_recurring_template WHERE accountId = :accountId AND active = 1")
    List<BudgetRecurringTemplateEntity> findActiveTemplates(String accountId);


    @Query("SELECT * FROM budget_recurring_template WHERE accountId = :accountId AND active = 1 AND avgAmountCents < 0 AND nextDue BETWEEN :fromDate AND :toDate")
    List<BudgetRecurringTemplateEntity> findActiveExpenseTemplatesForAccountInRange(String accountId, LocalDate fromDate, LocalDate toDate);

    @Query("""
            SELECT t.*
            FROM budget_recurring_template t
            INNER JOIN budget_account a ON a.id = t.accountId
            WHERE t.active = 1
              AND a.archived = 0
              AND t.avgAmountCents < 0
              AND t.nextDue BETWEEN :fromDate AND :toDate
            """)
    List<BudgetRecurringTemplateEntity> findActiveExpenseTemplatesForActiveAccountsInRange(LocalDate fromDate, LocalDate toDate);

    @Query("SELECT * FROM budget_recurring_template WHERE active = 1")
    List<BudgetRecurringTemplateEntity> findAllActiveTemplates();

    @Query("UPDATE budget_recurring_template SET nextDue = :nextDue, active = :active WHERE id = :templateId")
    void updateNextDueAndStatus(String templateId, LocalDate nextDue, boolean active);
}
