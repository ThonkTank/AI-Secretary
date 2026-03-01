package com.autosecretary.features.budget.domain.recurring;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Domain-level scheduling parameters for a recurring budget template.
 * <p>
 * Decouples {@link RecurringTemplateScheduler} from the Room entity, so scheduling logic
 * stays in the domain layer with no Android/Room dependency.
 * <p>
 * Field semantics depend on {@code recurringType}:
 * <ul>
 *   <li><b>MONTHLY_DAY</b> — {@code recurringValue} is the day of month (1–31).
 *       {@code recurringDayOfWeek} is ignored (null).</li>
 *   <li><b>MONTHLY_LAST</b> — both {@code recurringValue} and {@code recurringDayOfWeek} are ignored.
 *       The scheduler always targets the last day of the month.</li>
 *   <li><b>WEEKLY</b> — {@code recurringDayOfWeek} is the target day of week (non-null).
 *       {@code recurringValue} is ignored.</li>
 *   <li><b>INTERVAL</b> — {@code recurringValue} is the interval in days (≥1).
 *       {@code recurringDayOfWeek} is ignored (null).</li>
 * </ul>
 *
 * @param id                Template UUID (used to correlate with {@code TemplateStatusUpdate} output).
 * @param nextDue           The currently stored next-due date (may be in the past; scheduler advances it).
 * @param recurringType     Which of the four schedule patterns applies.
 * @param recurringDayOfWeek Non-null only for {@code WEEKLY}; the target day of week.
 * @param recurringValue    Type-dependent integer (day of month for {@code MONTHLY_DAY},
 *                          interval days for {@code INTERVAL}, unused for others).
 */
public record RecurringScheduleParams(
        String id,
        LocalDate nextDue,
        RecurringType recurringType,
        DayOfWeek recurringDayOfWeek,
        int recurringValue
) {}
