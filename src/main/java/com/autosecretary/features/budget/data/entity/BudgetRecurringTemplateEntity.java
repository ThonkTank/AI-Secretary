package com.autosecretary.features.budget.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;
import com.autosecretary.features.budget.domain.recurring.RecurringType;
import com.autosecretary.features.budget.domain.recurring.RecurringScheduleParams;
import com.autosecretary.features.budget.domain.recurring.RecurringSuggestion;
import com.autosecretary.features.budget.domain.TransactionDirection;

/**
 * Represents a recurring transaction pattern (e.g., monthly salary, weekly grocery spend).
 *
 * Recurring templates are created by the pattern detector when multiple similar transactions
 * from the same payee are detected. They serve two purposes:
 * - Provide suggestions to the user for "scheduled" recurring transactions
 * - Optionally auto-generate expected transactions at upcoming due dates
 *
 * Key fields:
 * - {@code normalizedPayee}: standardized payee name for pattern matching (e.g., "AMAZON")
 * - {@code displayPayee}: original payee text for user display (e.g., "Amazon Prime")
 * - {@code recurringType}: classification (MONTHLY_DAY, INTERVAL, WEEKLY, MONTHLY_LAST)
 * - {@code recurringValue}: scheduling parameter whose meaning depends on recurringType
 * - {@code avgAmountCents}, {@code minAmountCents}, {@code maxAmountCents}: statistical data
 *   from historical transactions, used for variance detection and prediction
 * - {@code active}: user toggle to enable/disable auto-generation; false = hidden from scheduling
 *
 * Invariant: only {@code active} templates contribute to upcoming transaction scheduling.
 */
@Entity(
        tableName = "budget_recurring_template",
        foreignKeys = {
                @ForeignKey(
                        entity = BudgetAccountEntity.class,
                        parentColumns = "id",
                        childColumns = "accountId",
                        onDelete = ForeignKey.RESTRICT,
                        onUpdate = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = BudgetCategoryEntity.class,
                        parentColumns = "id",
                        childColumns = "categoryId",
                        onDelete = ForeignKey.SET_NULL,
                        onUpdate = ForeignKey.CASCADE
                )
        },
        indices = {
                @Index("accountId"),
                @Index("categoryId")
        }
)
public class BudgetRecurringTemplateEntity {
    public record AmountStats(long averageCents, long minimumCents, long maximumCents) {}

    @PrimaryKey
    @NonNull
    public String id = UUID.randomUUID().toString();

    @NonNull
    public String accountId;

    @NonNull
    public String normalizedPayee;

    public String displayPayee;

    public String categoryId;

    /**
     * Average transaction amount in cents, computed from historical occurrences.
     * Used for variance detection and prediction when auto-generating upcoming transactions.
     */
    public long avgAmountCents;

    /**
     * Minimum transaction amount in cents from historical occurrences.
     * Used to detect significant outliers and alert the user to unexpected values.
     */
    public long minAmountCents;

    /**
     * Maximum transaction amount in cents from historical occurrences.
     * Used to detect significant outliers and alert the user to unexpected values.
     */
    public long maxAmountCents;

    @NonNull
    public RecurringType recurringType;

    @NonNull
    @ColumnInfo(name = "type")
    public TransactionDirection direction = TransactionDirection.EXPENSE;

    /**
     * Scheduling parameter whose meaning depends on recurringType.
     * This is a form of primitive obsession: the field serves multiple roles depending on context.
     * Always check recurringType before reading or writing this field:
     * <ul>
     *   <li>MONTHLY_DAY: day of month (1–31)</li>
     *   <li>INTERVAL: interval in days between occurrences</li>
     *   <li>WEEKLY: unused (0); day is stored in {@link #recurringDayOfWeek} instead</li>
     *   <li>MONTHLY_LAST: unused (0); last day of month is computed dynamically</li>
     * </ul>
     */
    public int recurringValue;

    /**
     * Day of week for WEEKLY recurring patterns (Monday, Tuesday, etc.).
     * Only meaningful when recurringType = WEEKLY; null for other types.
     */
    public DayOfWeek recurringDayOfWeek;

    /**
     * The next expected due date for this recurring pattern.
     * Persisted to the database. Advanced after each import by
     * {@code BudgetImportRoomRepository.synchronizeRecurringTemplateState()}, which recomputes
     * this date from the template's schedule (recurringType + recurringValue/recurringDayOfWeek).
     * null if the template has not yet been scheduled.
     */
    public LocalDate nextDue;

    /**
     * User toggle for auto-generation. When true, this pattern contributes to daily
     * scheduling of upcoming transactions. When false, the pattern is retained for
     * historical reference but does not generate new transactions.
     */
    public boolean active = true;

    public BudgetRecurringTemplateEntity(@NonNull String accountId,
                                         @NonNull String normalizedPayee,
                                         @NonNull RecurringType recurringType) {
        this.accountId = accountId;
        this.normalizedPayee = normalizedPayee;
        this.recurringType = recurringType;
    }

    /**
     * Creates a fully-initialized template entity from a recurring suggestion, preventing
     * half-built inserts if fields are added to the entity in the future.
     */
    public static BudgetRecurringTemplateEntity fromSuggestion(@NonNull RecurringSuggestion suggestion,
                                                               @NonNull String accountId,
                                                               @NonNull LocalDate nextDue) {
        BudgetRecurringTemplateEntity entity = new BudgetRecurringTemplateEntity(
                accountId,
                suggestion.normalizedPayee(),
                suggestion.suggestedType()
        );
        entity.displayPayee = suggestion.displayPayee();
        entity.categoryId = suggestion.categoryId();
        entity.applyAmountStats(new AmountStats(
                suggestion.avgAmountCents(),
                suggestion.minAmountCents(),
                suggestion.maxAmountCents()
        ));
        entity.direction = suggestion.direction();
        entity.applySchedule(suggestion.suggestedType(),
                suggestion.suggestedDayOfWeek(),
                suggestion.suggestedValue());
        entity.nextDue = nextDue;
        return entity;
    }

    /** Returns the three amount summary fields as one typed concept. */
    @NonNull
    public AmountStats amountStats() {
        return new AmountStats(avgAmountCents, minAmountCents, maxAmountCents);
    }

    /** Applies all amount summary fields together to avoid partial updates. */
    public void applyAmountStats(@NonNull AmountStats stats) {
        this.avgAmountCents = stats.averageCents();
        this.minAmountCents = stats.minimumCents();
        this.maxAmountCents = stats.maximumCents();
    }

    /**
     * Returns the entity's persisted schedule fields as the domain scheduling carrier used by
     * {@code RecurringTemplateScheduler}. Centralizes the overloaded recurring-value semantics.
     */
    @NonNull
    public RecurringScheduleParams toScheduleParams() {
        return new RecurringScheduleParams(id, nextDue, recurringType, recurringDayOfWeek, recurringValue);
    }

    /**
     * Writes the persisted schedule fields together so callers do not need to coordinate
     * recurringType, recurringDayOfWeek, and recurringValue manually.
     */
    public void applySchedule(@NonNull RecurringType type, DayOfWeek dayOfWeek, int value) {
        this.recurringType = type;
        this.recurringDayOfWeek = dayOfWeek;
        this.recurringValue = value;
    }

}
