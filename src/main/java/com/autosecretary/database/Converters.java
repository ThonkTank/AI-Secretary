package com.autosecretary.database;

import androidx.room.TypeConverter;

import com.autosecretary.shared.Period;
import com.autosecretary.shared.Priority;
import com.autosecretary.features.meal.domain.MealType;
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.data.TaskSlot;
import com.autosecretary.features.budget.data.entity.BudgetAccount;
import com.autosecretary.features.budget.domain.TransactionKind;
import com.autosecretary.features.budget.domain.importing.ImportStatus;
import com.autosecretary.features.budget.domain.RecurringBudgetTransaction;
import com.autosecretary.features.budget.domain.TransactionDirection;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class Converters {
    private Converters() {}

    @TypeConverter
    public static String fromLocalDate(LocalDate date) {
        return date != null ? date.toString() : null;
    }

    @TypeConverter
    public static LocalDate toLocalDate(String value) {
        return value != null ? LocalDate.parse(value) : null;
    }

    @TypeConverter
    public static String fromLocalDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.toString() : null;
    }

    @TypeConverter
    public static LocalDateTime toLocalDateTime(String value) {
        return value != null ? LocalDateTime.parse(value) : null;
    }

    @TypeConverter
    public static String fromLocalTime(LocalTime time) {
        return time != null ? time.toString() : null;
    }

    @TypeConverter
    public static LocalTime toLocalTime(String value) {
        return value != null ? LocalTime.parse(value) : null;
    }

    @TypeConverter
    public static String fromDayOfWeek(DayOfWeek day) {
        return day != null ? day.name() : null;
    }

    @TypeConverter
    public static DayOfWeek toDayOfWeek(String value) {
        return value != null ? DayOfWeek.valueOf(value) : null;
    }

    @TypeConverter
    public static String fromPriority(Priority prio) {
        return prio != null ? prio.name() : null;
    }

    @TypeConverter
    public static Priority toPriority(String value) {
        return value != null ? Priority.valueOf(value) : null;
    }


    @TypeConverter
    public static String fromSchedulingType(TaskCore.SchedulingType schedulingType) {
        return schedulingType != null ? schedulingType.name() : null;
    }

    @TypeConverter
    public static TaskCore.SchedulingType toSchedulingType(String value) {
        return value != null ? TaskCore.SchedulingType.valueOf(value) : null;
    }

    @TypeConverter
    public static String fromDisplacementGroupType(TaskSlot.DisplacementGroupType type) {
        return type != null ? type.name() : null;
    }

    @TypeConverter
    public static TaskSlot.DisplacementGroupType toDisplacementGroupType(String value) {
        return value != null ? TaskSlot.DisplacementGroupType.valueOf(value) : null;
    }

    @TypeConverter
    public static String fromMealType(MealType mealType) {
        return mealType != null ? mealType.name() : null;
    }

    @TypeConverter
    public static MealType toMealType(String value) {
        return value != null ? MealType.valueOf(value) : null;
    }

    @TypeConverter
    public static String fromPeriod(Period period) {
        return period != null ? period.name() : null;
    }

    @TypeConverter
    public static Period toPeriod(String value) {
        return value != null ? Period.valueOf(value) : null;
    }

    @TypeConverter
    public static String fromTransactionDirection(TransactionDirection type) {
        return type != null ? type.name() : null;
    }

    @TypeConverter
    public static TransactionDirection toTransactionDirection(String value) {
        return value != null ? TransactionDirection.valueOf(value) : null;
    }

    @TypeConverter
    public static String fromTransactionKind(TransactionKind kind) {
        return kind != null ? kind.name() : null;
    }

    @TypeConverter
    public static TransactionKind toTransactionKind(String value) {
        return value != null ? TransactionKind.valueOf(value) : null;
    }

    @TypeConverter
    public static String fromAccountType(BudgetAccount.AccountType type) {
        return type != null ? type.name() : null;
    }

    @TypeConverter
    public static BudgetAccount.AccountType toAccountType(String value) {
        return value != null ? BudgetAccount.AccountType.valueOf(value) : null;
    }

    @TypeConverter
    public static String fromImportStatus(ImportStatus status) {
        return status != null ? status.name() : null;
    }

    @TypeConverter
    public static ImportStatus toImportStatus(String value) {
        return value != null ? ImportStatus.valueOf(value) : null;
    }

    @TypeConverter
    public static String fromRecurringType(RecurringBudgetTransaction.RecurringType type) {
        return type != null ? type.name() : null;
    }

    @TypeConverter
    public static RecurringBudgetTransaction.RecurringType toRecurringType(String value) {
        return value != null ? RecurringBudgetTransaction.RecurringType.valueOf(value) : null;
    }

    @TypeConverter
    public static String fromYearMonth(YearMonth yearMonth) {
        return yearMonth != null ? yearMonth.toString() : null;
    }

    @TypeConverter
    public static YearMonth toYearMonth(String value) {
        return value != null ? YearMonth.parse(value) : null;
    }

    @TypeConverter
    public static String fromDaySet(Set<DayOfWeek> days) {
        if (days == null || days.isEmpty()) return null;
        return days.stream().map(DayOfWeek::name).collect(Collectors.joining(","));
    }

    @TypeConverter
    public static Set<DayOfWeek> toDaySet(String value) {
        if (value == null || value.isEmpty()) return null;
        return Arrays.stream(value.split(",")).map(DayOfWeek::valueOf).collect(Collectors.toSet());
    }
}
