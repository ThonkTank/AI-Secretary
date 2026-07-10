package com.autosecretary.database;

import androidx.room.TypeConverter;

import com.autosecretary.shared.Period;
import com.autosecretary.shared.Priority;
import com.autosecretary.features.meal.domain.HouseholdMember;
import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.shared.MealType;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.task.data.TaskCore;
import com.autosecretary.features.task.data.TaskSlot;
import com.autosecretary.features.budget.data.entity.BudgetAccountEntity;
import com.autosecretary.features.budget.domain.TransactionKind;
import com.autosecretary.features.budget.domain.importing.ImportStatus;
import com.autosecretary.features.budget.domain.recurring.RecurringType;
import com.autosecretary.features.budget.domain.TransactionDirection;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Room {@code @TypeConverter} methods for converting Java objects to/from SQLite-storable types.
 * <p>
 * SQLite natively stores only: NULL, INTEGER, REAL, TEXT, BLOB. Java objects like {@code LocalDate},
 * enums, and {@code Set<DayOfWeek>} need conversion. Room calls these methods automatically when
 * reading/writing entities.
 * </p>
 * <p>
 * <strong>Pattern:</strong> Each convertible type has two methods:
 * <ul>
 *   <li>{@code from*()} — converts Java → SQLite-storable (e.g., {@code LocalDate} → ISO string)</li>
 *   <li>{@code to*()} — converts SQLite-storable → Java (e.g., string → {@code LocalDate})</li>
 * </ul>
 * Both handle null values gracefully.
 * </p>
 * <p>
 * <strong>Covered types:</strong> LocalDate, LocalDateTime, LocalTime, DayOfWeek, Priority, Period,
 * and enum types from features (SchedulingType, TransactionDirection, ImportStatus, etc.),
 * plus Set&lt;DayOfWeek&gt; (stored as comma-separated string).
 * </p>
 * <p>
 * <strong>Further reading:</strong>
 * <a href="https://developer.android.com/training/data-storage/room/referencing-data#type-converters">
 * Room Type Converters documentation
 * </a>
 * </p>
 */
public class Converters {
    private Converters() {}

    // Generic helpers to reduce boilerplate in converter pairs.
    private static <T> String serialize(T obj) {
        return obj != null ? obj.toString() : null;
    }

    private static <T> T deserialize(String value, Function<String, T> parser) {
        return value != null ? parser.apply(value) : null;
    }

    private static <E extends Enum<E>> String fromEnum(E e) {
        return e != null ? e.name() : null;
    }

    private static <E extends Enum<E>> E toEnum(String value, Class<E> enumClass) {
        return value != null ? Enum.valueOf(enumClass, value) : null;
    }

    private static <E extends Enum<E>> String fromEnumSet(Set<E> values) {
        if (values == null || values.isEmpty()) return null;
        return values.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    private static <E extends Enum<E>> Set<E> toEnumSet(String value, Class<E> enumClass) {
        if (value == null || value.isEmpty()) return EnumSet.noneOf(enumClass);
        return Arrays.stream(value.split(","))
                .map(s -> Enum.valueOf(enumClass, s))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(enumClass)));
    }

    @TypeConverter
    public static String fromLocalDate(LocalDate date) {
        return serialize(date);
    }

    @TypeConverter
    public static LocalDate toLocalDate(String value) {
        return deserialize(value, LocalDate::parse);
    }

    @TypeConverter
    public static String fromLocalDateTime(LocalDateTime dateTime) {
        return serialize(dateTime);
    }

    @TypeConverter
    public static LocalDateTime toLocalDateTime(String value) {
        return deserialize(value, LocalDateTime::parse);
    }

    @TypeConverter
    public static String fromLocalTime(LocalTime time) {
        return serialize(time);
    }

    @TypeConverter
    public static LocalTime toLocalTime(String value) {
        return deserialize(value, LocalTime::parse);
    }

    @TypeConverter
    public static String fromDayOfWeek(DayOfWeek day) {
        return fromEnum(day);
    }

    @TypeConverter
    public static DayOfWeek toDayOfWeek(String value) {
        return toEnum(value, DayOfWeek.class);
    }

    @TypeConverter
    public static String fromPriority(Priority prio) {
        return fromEnum(prio);
    }

    @TypeConverter
    public static Priority toPriority(String value) {
        return toEnum(value, Priority.class);
    }


    @TypeConverter
    public static String fromSchedulingType(TaskCore.SchedulingType schedulingType) {
        return fromEnum(schedulingType);
    }

    @TypeConverter
    public static TaskCore.SchedulingType toSchedulingType(String value) {
        return toEnum(value, TaskCore.SchedulingType.class);
    }

    @TypeConverter
    public static String fromDisplacementGroupType(TaskSlot.DisplacementGroupType type) {
        return fromEnum(type);
    }

    @TypeConverter
    public static TaskSlot.DisplacementGroupType toDisplacementGroupType(String value) {
        return toEnum(value, TaskSlot.DisplacementGroupType.class);
    }

    @TypeConverter
    public static String fromMealType(MealType mealType) {
        return fromEnum(mealType);
    }

    @TypeConverter
    public static MealType toMealType(String value) {
        return toEnum(value, MealType.class);
    }

    @TypeConverter
    public static String fromPeriod(Period period) {
        return fromEnum(period);
    }

    @TypeConverter
    public static Period toPeriod(String value) {
        return toEnum(value, Period.class);
    }

    @TypeConverter
    public static String fromTransactionDirection(TransactionDirection type) {
        return fromEnum(type);
    }

    @TypeConverter
    public static TransactionDirection toTransactionDirection(String value) {
        return toEnum(value, TransactionDirection.class);
    }

    @TypeConverter
    public static String fromTransactionKind(TransactionKind kind) {
        return fromEnum(kind);
    }

    @TypeConverter
    public static TransactionKind toTransactionKind(String value) {
        return toEnum(value, TransactionKind.class);
    }

    @TypeConverter
    public static String fromAccountType(BudgetAccountEntity.AccountType type) {
        return fromEnum(type);
    }

    @TypeConverter
    public static BudgetAccountEntity.AccountType toAccountType(String value) {
        return toEnum(value, BudgetAccountEntity.AccountType.class);
    }

    @TypeConverter
    public static String fromImportStatus(ImportStatus status) {
        return fromEnum(status);
    }

    @TypeConverter
    public static ImportStatus toImportStatus(String value) {
        return toEnum(value, ImportStatus.class);
    }

    @TypeConverter
    public static String fromRecurringType(RecurringType type) {
        return fromEnum(type);
    }

    @TypeConverter
    public static RecurringType toRecurringType(String value) {
        return toEnum(value, RecurringType.class);
    }

    @TypeConverter
    public static String fromYearMonth(YearMonth yearMonth) {
        return serialize(yearMonth);
    }

    @TypeConverter
    public static YearMonth toYearMonth(String value) {
        return deserialize(value, YearMonth::parse);
    }

    @TypeConverter
    public static String fromDaySet(Set<DayOfWeek> days) { return fromEnumSet(days); }

    @TypeConverter
    public static Set<DayOfWeek> toDaySet(String value) { return toEnumSet(value, DayOfWeek.class); }

    // --- Meal feature converters ---

    @TypeConverter
    public static String fromFoodGroup(Ingredient.FoodGroup value) {
        return fromEnum(value);
    }

    @TypeConverter
    public static Ingredient.FoodGroup toFoodGroup(String value) {
        return toEnum(value, Ingredient.FoodGroup.class);
    }

    @TypeConverter
    public static String fromStorageLocation(PantryItem.StorageLocation value) {
        return fromEnum(value);
    }

    @TypeConverter
    public static PantryItem.StorageLocation toStorageLocation(String value) {
        return toEnum(value, PantryItem.StorageLocation.class);
    }

    @TypeConverter
    public static String fromGender(HouseholdMember.Gender value) {
        return fromEnum(value);
    }

    @TypeConverter
    public static HouseholdMember.Gender toGender(String value) {
        return toEnum(value, HouseholdMember.Gender.class);
    }

    @TypeConverter
    public static String fromActivityLevel(HouseholdMember.ActivityLevel value) {
        return fromEnum(value);
    }

    @TypeConverter
    public static HouseholdMember.ActivityLevel toActivityLevel(String value) {
        return toEnum(value, HouseholdMember.ActivityLevel.class);
    }

    @TypeConverter
    public static String fromScalingPrecision(Recipe.ScalingPrecision value) {
        return fromEnum(value);
    }

    @TypeConverter
    public static Recipe.ScalingPrecision toScalingPrecision(String value) {
        return toEnum(value, Recipe.ScalingPrecision.class);
    }

    @TypeConverter
    public static String fromPrepEffort(Recipe.PrepEffort value) {
        return fromEnum(value);
    }

    @TypeConverter
    public static Recipe.PrepEffort toPrepEffort(String value) {
        return toEnum(value, Recipe.PrepEffort.class);
    }

    @TypeConverter
    public static String fromMealTypeSet(Set<MealType> types) { return fromEnumSet(types); }

    @TypeConverter
    public static Set<MealType> toMealTypeSet(String value) { return toEnumSet(value, MealType.class); }
}
