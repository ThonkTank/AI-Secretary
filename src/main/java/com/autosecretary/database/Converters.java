package com.autosecretary.database;

import androidx.room.TypeConverter;

import com.autosecretary.constants.Period;
import com.autosecretary.constants.Priority;
import com.autosecretary.features.budget.data.BudgetTransactionEntity;
import com.autosecretary.features.budget.data.Import;
import com.autosecretary.features.budget.data.legacy.Account;
import com.autosecretary.features.budget.data.legacy.Transaction;

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
    public static String fromPeriod(Period period) {
        return period != null ? period.name() : null;
    }

    @TypeConverter
    public static Period toPeriod(String value) {
        return value != null ? Period.valueOf(value) : null;
    }

    @TypeConverter
    public static String fromAccountType(Account.AccountType type) {
        return type != null ? type.name() : null;
    }

    @TypeConverter
    public static Account.AccountType toAccountType(String value) {
        return value != null ? Account.AccountType.valueOf(value) : null;
    }

    @TypeConverter
    public static String fromRecurringType(Transaction.RecurringType type) {
        return type != null ? type.name() : null;
    }

    @TypeConverter
    public static Transaction.RecurringType toRecurringType(String value) {
        return value != null ? Transaction.RecurringType.valueOf(value) : null;
    }

    @TypeConverter
    public static String fromRepUnit(Transaction.RepUnits unit) {
        return unit != null ? unit.name() : null;
    }

    @TypeConverter
    public static Transaction.RepUnits toRepUnit(String value) {
        return value != null ? Transaction.RepUnits.valueOf(value) : null;
    }

    @TypeConverter
    public static String fromImportStatus(Import.ImportStatus status) {
        return status != null ? status.name() : null;
    }

    @TypeConverter
    public static Import.ImportStatus toImportStatus(String value) {
        return value != null ? Import.ImportStatus.valueOf(value) : null;
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
    public static String fromDaySet(Set<DayOfWeek> days) {
        return days != null ? days.stream().map(DayOfWeek::name).collect(Collectors.joining(",")) : null;
    }

    @TypeConverter
    public static Set<DayOfWeek> toDaySet(String value) {
        return value != null ? Arrays.stream(value.split(",")).map(DayOfWeek::valueOf).collect(Collectors.toSet()) : null;
    }
}
