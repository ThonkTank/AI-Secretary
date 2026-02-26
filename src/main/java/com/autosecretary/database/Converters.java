package com.autosecretary.database;

import androidx.room.TypeConverter;

import com.autosecretary.shared.Period;
import com.autosecretary.shared.Priority;
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.budget.data.entity.BudgetAccount;
import com.autosecretary.features.budget.data.entity.BudgetTransactionEntity;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class Converters {
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
    public static String fromPeriod(Period period) {
        return period != null ? period.name() : null;
    }

    @TypeConverter
    public static Period toPeriod(String value) {
        return value != null ? Period.valueOf(value) : null;
    }

    @TypeConverter
    public static String fromBudgetTransactionType(BudgetTransactionEntity.TransactionType type) {
        return type != null ? type.name() : null;
    }

    @TypeConverter
    public static BudgetTransactionEntity.TransactionType toBudgetTransactionType(String value) {
        return value != null ? BudgetTransactionEntity.TransactionType.valueOf(value) : null;
    }


    @TypeConverter
    public static String fromBudgetTransactionKind(BudgetTransactionEntity.TransactionKind kind) {
        return kind != null ? kind.name() : null;
    }

    @TypeConverter
    public static BudgetTransactionEntity.TransactionKind toBudgetTransactionKind(String value) {
        return value != null ? BudgetTransactionEntity.TransactionKind.valueOf(value) : null;
    }
    @TypeConverter
    public static String fromBudgetAccountType(BudgetAccount.AccountType type) {
        return type != null ? type.name() : null;
    }

    @TypeConverter
    public static BudgetAccount.AccountType toBudgetAccountType(String value) {
        return value != null ? BudgetAccount.AccountType.valueOf(value) : null;
    }

    @TypeConverter
    public static String fromDaySet(Set<DayOfWeek> days) {
        return days != null ? days.stream().map(DayOfWeek::name).collect(Collectors.joining(",")) : null;
    }

    @TypeConverter
    public static Set<DayOfWeek> toDaySet(String value) {
        return value != null ? Arrays.stream(value.split(",")).map(DayOfWeek::valueOf).collect(Collectors.toSet()) : null;
    }
}
