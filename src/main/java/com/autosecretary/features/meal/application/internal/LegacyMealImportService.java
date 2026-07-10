package com.autosecretary.features.meal.application.internal;

import android.util.Log;

import com.autosecretary.features.meal.domain.ConsumptionLog;
import com.autosecretary.features.meal.domain.CookingPreferences;
import com.autosecretary.features.meal.domain.HouseholdMember;
import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.features.meal.domain.MealPlan;
import com.autosecretary.features.meal.domain.MealRepository;
import com.autosecretary.features.meal.domain.MealType;
import com.autosecretary.features.meal.domain.PantryItem;
import com.autosecretary.features.meal.domain.PantryRepository;
import com.autosecretary.features.meal.domain.Recipe;
import com.autosecretary.features.meal.domain.RecipeRepository;
import com.autosecretary.features.meal.domain.ShoppingItemStatus;
import com.autosecretary.features.meal.domain.ShoppingListItem;
import com.autosecretary.features.meal.domain.WeeklyFoodTarget;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One-shot import path for legacy meal snapshots.
 *
 * <p>This service is used for migrating meal data from a previous version of AutoSecretary.
 * It processes entities sequentially: ingredients → recipes → meal plans → consumption logs → etc.
 * Failures in one entity type do not stop or roll back other imports.
 *
 * <p><strong>For detailed usage, examples, and data structure documentation, see README.md in this package.</strong>
 *
 * <p>Reference source structures:
 * <ul>
 *     <li>history/migrating/entities/* (legacy data models)</li>
 *     <li>history/migrating/repository/parser/* (legacy parsing examples)</li>
 * </ul>
 *
 * <p>Compatibility rules:
 * <ul>
 *     <li>Enum values are matched case-insensitively; unknown values fall back to entity-specific defaults.</li>
 *     <li>Date values accept ISO-8601, legacy formats (yyyy-MM-dd HH:mm:ss, dd.MM.yyyy, yyyy/MM/dd), and epoch seconds.</li>
 *     <li>Required fields (e.g., "name", "title") cause row rejection if missing or unparseable.</li>
 *     <li>Optional fields are assigned sensible defaults (e.g., FoodGroup.OTHER) if unparseable; null is OK.</li>
 * </ul>
 */
public class LegacyMealImportService {

    public static final String SOURCE_INGREDIENTS = "history/migrating/entities/Ingredient";
    public static final String SOURCE_RECIPES = "history/migrating/entities/Recipe";
    public static final String SOURCE_MEAL_PLANS = "history/migrating/entities/MealPlan";
    public static final String SOURCE_CONSUMPTION = "history/migrating/entities/ConsumptionLog";
    public static final String SOURCE_PANTRY = "history/migrating/entities/PantryItem";
    public static final String SOURCE_SHOPPING = "history/migrating/entities/ShoppingListItem";
    public static final String SOURCE_MEMBERS = "history/migrating/entities/HouseholdMember";
    public static final String SOURCE_PREFERENCES = "history/migrating/entities/CookingPreferences";
    public static final String SOURCE_WEEKLY_TARGETS = "history/migrating/entities/WeeklyFoodTarget";

    private static final String TAG = "LegacyMealImport";
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    );
    private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    );

    private static final int DEFAULT_HEIGHT_CM = 170;  // Adult average
    private static final int DEFAULT_WEIGHT_KG = 70;   // Adult average

    private final RecipeRepository recipeRepository;
    private final MealRepository mealRepository;
    private final PantryRepository pantryRepository;
    private final AtomicBoolean hasRun = new AtomicBoolean(false);

    public LegacyMealImportService(RecipeRepository recipeRepository,
                                   MealRepository mealRepository,
                                   PantryRepository pantryRepository) {
        this.recipeRepository = recipeRepository;
        this.mealRepository = mealRepository;
        this.pantryRepository = pantryRepository;
    }

    /**
     * Imports legacy meal data in a single pass.
     *
     * <p><strong>This method is idempotent and can only succeed on the first call.</strong>
     * Subsequent calls return immediately with a failure report.
     *
     * @param sourceRows Map of entity type → list of row data. Keys are SOURCE_* constants (e.g., SOURCE_INGREDIENTS).
     *                   Each inner Map contains column keys and values for a single entity.
     *                   See README.md for detailed structure and example.
     * @return LegacyImportReport with per-entity-type success counts and a list of validation failures.
     *         Failures indicate rows that could not be processed, but do not prevent other rows/types from importing.
     *
     * <p>Processing order:
     * <ol>
     *     <li>Ingredients (required for recipes)</li>
     *     <li>Recipes</li>
     *     <li>Meal Plans</li>
     *     <li>Consumption Logs</li>
     *     <li>Pantry Items</li>
     *     <li>Shopping List Items</li>
     *     <li>Household Members</li>
     *     <li>Cooking Preferences</li>
     *     <li>Weekly Food Targets</li>
     * </ol>
     *
     * <p><strong>Error semantics:</strong>
     * <ul>
     *     <li>Validation failures (e.g., missing required field) are logged and recorded in the report; the row is skipped.</li>
     *     <li>No transactional rollback occurs; rows processed before a failure remain in the database.</li>
     *     <li>Repository exceptions (e.g., IO errors) propagate to the caller.</li>
     * </ul>
     */
    public LegacyImportReport importOnce(Map<String, List<Map<String, Object>>> sourceRows) {
        if (!hasRun.compareAndSet(false, true)) {
            LegacyImportReport report = new LegacyImportReport();
            report.addFailure("import", -1, "import already executed");
            return report;
        }

        LegacyImportReport report = new LegacyImportReport();
        importIngredients(sourceRows.getOrDefault(SOURCE_INGREDIENTS, List.of()), report);
        importRecipes(sourceRows.getOrDefault(SOURCE_RECIPES, List.of()), report);
        importMealPlans(sourceRows.getOrDefault(SOURCE_MEAL_PLANS, List.of()), report);
        importConsumption(sourceRows.getOrDefault(SOURCE_CONSUMPTION, List.of()), report);
        importPantry(sourceRows.getOrDefault(SOURCE_PANTRY, List.of()), report);
        importShopping(sourceRows.getOrDefault(SOURCE_SHOPPING, List.of()), report);
        importMembers(sourceRows.getOrDefault(SOURCE_MEMBERS, List.of()), report);
        importPreferences(sourceRows.getOrDefault(SOURCE_PREFERENCES, List.of()), report);
        importWeeklyTargets(sourceRows.getOrDefault(SOURCE_WEEKLY_TARGETS, List.of()), report);
        return report;
    }

    /**
     * Imports ingredients from legacy snapshot.
     *
     * <p><strong>Required fields:</strong> "name" must be present and non-blank, else the row is rejected.
     * <p><strong>Optional fields:</strong> All other fields are populated with sensible defaults if missing or unparseable:
     * <ul>
     *     <li>"food_group" → Ingredient.FoodGroup.OTHER (case-insensitive enum match)</li>
     *     <li>"default_unit" → null (string, can be empty)</li>
     *     <li>"grams_per_unit", "*_per_100" (calories, protein, carbs, fat, fiber), "shelf_life_days" → 0</li>
     *     <li>Boolean fields (requires_refrigeration, is_whole_unit, is_perishable) → false</li>
     * </ul>
     *
     * <p>This pattern is replicated for all other entity types (recipes, meal plans, etc.). Differences are noted in each method.
     */
    private void importIngredients(List<Map<String, Object>> rows, LegacyImportReport report) {
        importRows(SOURCE_INGREDIENTS, rows, report, (row, idx) -> {
            // Required field: name
            String name = asString(row.get("name"));
            if (name == null) {
                report.addFailure(SOURCE_INGREDIENTS, idx, "missing required field: name");
                return false;
            }

            Ingredient ingredient = new Ingredient();
            ingredient.id = asIdString(row.get("id"));
            ingredient.name = name;
            // Optional fields with fallbacks:
            ingredient.foodGroup = asEnum(Ingredient.FoodGroup.class, row.get("food_group"), Ingredient.FoodGroup.OTHER);
            ingredient.defaultUnit = asString(row.get("default_unit"));
            ingredient.gramsPerUnit = asInt(row.get("grams_per_unit"), 1); // 1, not 0: zero grams/unit would break scaling
            ingredient.caloriesPer100 = asInt(row.get("calories_per_100"), 0);
            ingredient.proteinPer100 = asInt(row.get("protein_per_100"), 0);
            ingredient.carbsPer100 = asInt(row.get("carbs_per_100"), 0);
            ingredient.fatPer100 = asInt(row.get("fat_per_100"), 0);
            ingredient.fiberPer100 = asInt(row.get("fiber_per_100"), 0);
            ingredient.shelfLifeDays = asInt(row.get("shelf_life_days"), 0);
            ingredient.requiresRefrigeration = asBoolean(row.get("requires_refrigeration"), false);
            ingredient.isWholeUnit = asBoolean(row.get("is_whole_unit"), false);
            ingredient.isPerishable = asBoolean(row.get("is_perishable"), false);
            recipeRepository.saveIngredient(ingredient);
            return true;
        });
    }

    /**
     * Imports recipes from legacy snapshot.
     *
     * <p><strong>Required fields:</strong> "title" must be present and non-blank, else the row is rejected.
     * <p><strong>Optional fields:</strong> See importIngredients for general pattern. Notable defaults:
     * <ul>
     *     <li>"servings", "min_servings", "max_servings" → numeric defaults (2, 1, 8)</li>
     *     <li>"scaling_precision" → Recipe.ScalingPrecision.ROUGH</li>
     *     <li>"prep_effort" → Recipe.PrepEffort.MEDIUM</li>
     *     <li>"ingredients_data", "ratings_data" → parsed from semicolon/comma-separated strings</li>
     * </ul>
     */
    private void importRecipes(List<Map<String, Object>> rows, LegacyImportReport report) {
        importRows(SOURCE_RECIPES, rows, report, (row, idx) -> {
            // Required field: title
            String title = asString(row.get("title"));
            if (title == null) {
                report.addFailure(SOURCE_RECIPES, idx, "missing required field: title");
                return false;
            }
            Recipe recipe = new Recipe();
            recipe.id = asIdString(row.get("id"));
            recipe.title = title;
            recipe.description = asString(row.get("description"));
            recipe.instructions = asString(row.get("instructions"));
            recipe.mealTypes = parseMealTypes(asString(row.get("meal_types")));
            recipe.prepTimeMinutes = asInt(row.get("prep_time_minutes"), 0);
            recipe.cookTimeMinutes = asInt(row.get("cook_time_minutes"), 0);
            recipe.servings = asInt(row.get("servings"), 2);
            recipe.minServings = asInt(row.get("min_servings"), 1);
            recipe.maxServings = asInt(row.get("max_servings"), 8);
            recipe.scalingPrecision = asEnum(Recipe.ScalingPrecision.class, row.get("scaling_precision"), Recipe.ScalingPrecision.ROUGH);
            recipe.prepEffort = asEnum(Recipe.PrepEffort.class, row.get("prep_effort"), Recipe.PrepEffort.MEDIUM);
            recipe.ingredients = parseRecipeIngredients(asString(row.get("ingredients_data")));
            recipe.tags = asString(row.get("tags"));
            recipe.lastUsed = asDate(row.get("last_used"));
            recipe.usageCount = asInt(row.get("usage_count"), 0);
            recipe.isFavorite = asBoolean(row.get("is_favorite"), false);
            recipe.totalCalories = asInt(row.get("total_calories"), 0);
            recipe.totalProtein = asInt(row.get("total_protein"), 0);
            recipe.totalCarbs = asInt(row.get("total_carbs"), 0);
            recipe.totalFat = asInt(row.get("total_fat"), 0);
            recipe.shelfLifeDays = asInt(row.get("shelf_life_days"), 0);
            recipe.ratings = parseRatings(asString(row.get("ratings_data")));
            recipeRepository.saveRecipe(recipe);
            return true;
        });
    }

    private void importMealPlans(List<Map<String, Object>> rows, LegacyImportReport report) {
        importRows(SOURCE_MEAL_PLANS, rows, report, (row, idx) -> {
            LocalDate date = asDate(row.get("date"));
            MealType mealType = asEnum(MealType.class, row.get("meal_type"), null);
            String recipeId = asIdString(row.get("recipe_id"));
            if (date == null || mealType == null || recipeId == null) {
                report.addFailure(SOURCE_MEAL_PLANS, idx, "required fields invalid: date/meal_type/recipe_id");
                return false;
            }
            MealPlan mealPlan = new MealPlan();
            mealPlan.id = asIdString(row.get("id"));
            mealPlan.date = date;
            mealPlan.mealType = mealType;
            mealPlan.recipeId = recipeId;
            mealPlan.plannedServings = asInt(row.get("planned_servings"), 0);
            mealPlan.isCompleted = asBoolean(row.get("is_completed"), false);
            mealPlan.actualServings = asInt(row.get("actual_servings"), 0);
            mealPlan.completedAt = asDateTime(row.get("completed_at"));
            mealPlan.itemId = asIdString(row.get("item_id"));
            mealPlan.recipeTitle = asString(row.get("recipe_title"));
            mealPlan.estimatedCalories = asInt(row.get("estimated_calories"), 0);
            mealRepository.saveMealPlan(mealPlan);
            return true;
        });
    }

    private void importConsumption(List<Map<String, Object>> rows, LegacyImportReport report) {
        importRows(SOURCE_CONSUMPTION, rows, report, (row, idx) -> {
            LocalDate date = asDate(row.get("date"));
            // itemId and memberId may be null ("unassigned" sentinels used by
            // TaskMealIntegrationService). Only date == null is a hard rejection.
            if (date == null) {
                report.addFailure(SOURCE_CONSUMPTION, idx, "required fields invalid: date");
                return false;
            }
            ConsumptionLog log = new ConsumptionLog();
            log.id = asIdString(row.get("id"));
            log.date = date;
            log.itemId = asIdString(row.get("item_id"));
            log.memberId = asIdString(row.get("member_id"));
            log.recipeId = asIdString(row.get("recipe_id"));
            log.servingsConsumed = asDouble(row.get("servings_consumed"), 0.0);
            log.calories = asInt(row.get("calories"), 0);
            log.protein = asInt(row.get("protein"), 0);
            log.carbs = asInt(row.get("carbs"), 0);
            log.fat = asInt(row.get("fat"), 0);
            mealRepository.saveConsumptionLog(log);
            return true;
        });
    }

    private void importPantry(List<Map<String, Object>> rows, LegacyImportReport report) {
        importRows(SOURCE_PANTRY, rows, report, (row, idx) -> {
            String ingredientId = asIdString(row.get("ingredient_id"));
            if (ingredientId == null) {
                report.addFailure(SOURCE_PANTRY, idx, "missing required field: ingredient_id");
                return false;
            }

            PantryItem item = new PantryItem();
            item.id = asIdString(row.get("id"));
            item.ingredientId = ingredientId;
            item.ingredientName = asString(row.get("ingredient_name"));
            item.amount = asDouble(row.get("amount"), 0.0);
            item.unit = asString(row.get("unit"));
            item.purchaseDate = asDate(row.get("purchase_date"));
            item.expiryDate = asDate(row.get("expiry_date"));
            item.location = asEnum(PantryItem.StorageLocation.class, row.get("location"), PantryItem.StorageLocation.PANTRY);

            pantryRepository.savePantryItem(item);
            return true;
        });
    }

    private void importShopping(List<Map<String, Object>> rows, LegacyImportReport report) {
        importRows(SOURCE_SHOPPING, rows, report, (row, idx) -> {
            String periodKey = asString(row.get("period_key"));
            String ingredientId = asIdString(row.get("ingredient_id"));
            if (periodKey == null || ingredientId == null) {
                report.addFailure(SOURCE_SHOPPING, idx, "required fields invalid: period_key/ingredient_id");
                return false;
            }

            ShoppingListItem item = new ShoppingListItem();
            item.id = asIdString(row.get("id"));
            item.periodKey = periodKey;
            item.ingredientId = ingredientId;
            item.ingredientName = asString(row.get("ingredient_name"));
            item.amount = asDouble(row.get("amount"), 0.0);
            item.neededAmount = asDouble(row.get("needed_amount"), 0.0);
            item.excessAmount = asDouble(row.get("excess_amount"), 0.0);
            item.unit = asString(row.get("unit"));
            item.foodGroupLabel = asString(row.get("food_group_label"));
            item.suggestedStore = asString(row.get("suggested_store"));
            item.status = asBoolean(row.get("is_purchased"), false)
                    ? ShoppingItemStatus.DONE
                    : ShoppingItemStatus.OPEN;
            item.estimatedPriceCents = asInt(row.get("estimated_price_cents"), 0);

            pantryRepository.saveShoppingListItem(item);
            return true;
        });
    }

    private void importMembers(List<Map<String, Object>> rows, LegacyImportReport report) {
        importRows(SOURCE_MEMBERS, rows, report, (row, idx) -> {
            String name = asString(row.get("name"));
            if (name == null) {
                report.addFailure(SOURCE_MEMBERS, idx, "missing required field: name");
                return false;
            }

            HouseholdMember member = new HouseholdMember();
            member.id = asIdString(row.get("id"));
            member.name = name;
            member.birthYear = asInt(row.get("birth_year"), LocalDate.now().getYear());
            member.gender = asEnum(HouseholdMember.Gender.class, row.get("gender"), HouseholdMember.Gender.OTHER);
            member.heightCm = asInt(row.get("height_cm"), DEFAULT_HEIGHT_CM);
            member.weightKg = asInt(row.get("weight_kg"), DEFAULT_WEIGHT_KG);
            member.targetWeightKg = asInt(row.get("target_weight_kg"), member.weightKg);
            member.activityLevel = asEnum(HouseholdMember.ActivityLevel.class, row.get("activity_level"), HouseholdMember.ActivityLevel.MODERATE);
            member.isActive = asBoolean(row.get("is_active"), true);

            mealRepository.saveHouseholdMember(member);
            return true;
        });
    }

    private void importPreferences(List<Map<String, Object>> rows, LegacyImportReport report) {
        importRows(SOURCE_PREFERENCES, rows, report, (row, idx) -> {
            CookingPreferences prefs = new CookingPreferences();
            prefs.id = asIdString(row.get("id"));
            // Use explicit 0 defaults for numeric fields (not implicit uninitialized values)
            prefs.maxBreakfastCooking = asInt(row.get("max_breakfast_cooking"), 0);
            prefs.maxLunchCooking = asInt(row.get("max_lunch_cooking"), 0);
            prefs.maxDinnerCooking = asInt(row.get("max_dinner_cooking"), 0);
            prefs.maxSnackCooking = asInt(row.get("max_snack_cooking"), 0);
            prefs.breakfastCookingDays = parseDaysOfWeek(asString(row.get("breakfast_cooking_days")));
            prefs.lunchCookingDays = parseDaysOfWeek(asString(row.get("lunch_cooking_days")));
            prefs.dinnerCookingDays = parseDaysOfWeek(asString(row.get("dinner_cooking_days")));
            prefs.snackCookingDays = parseDaysOfWeek(asString(row.get("snack_cooking_days")));
            prefs.quickPrepMaxMinutes = asInt(row.get("quick_prep_max_minutes"), 0);

            mealRepository.saveCookingPreferences(prefs);
            return true;
        });
    }

    private void importWeeklyTargets(List<Map<String, Object>> rows, LegacyImportReport report) {
        importRows(SOURCE_WEEKLY_TARGETS, rows, report, (row, idx) -> {
            String periodKey = asString(row.get("period_key"));
            if (periodKey == null) {
                report.addFailure(SOURCE_WEEKLY_TARGETS, idx, "missing required field: period_key");
                return false;
            }

            WeeklyFoodTarget target = new WeeklyFoodTarget();
            target.id = asIdString(row.get("id"));
            target.periodKey = periodKey;
            target.setTargetFor(Ingredient.FoodGroup.GRAIN,     asInt(row.get("grain_grams"), 0));
            target.setTargetFor(Ingredient.FoodGroup.POTATO,    asInt(row.get("potato_grams"), 0));
            target.setTargetFor(Ingredient.FoodGroup.VEGETABLE, asInt(row.get("vegetable_grams"), 0));
            target.setTargetFor(Ingredient.FoodGroup.FRUIT,     asInt(row.get("fruit_grams"), 0));
            target.setTargetFor(Ingredient.FoodGroup.DAIRY,     asInt(row.get("dairy_grams"), 0));
            target.setTargetFor(Ingredient.FoodGroup.MEAT,      asInt(row.get("meat_grams"), 0));
            target.setTargetFor(Ingredient.FoodGroup.FISH,      asInt(row.get("fish_grams"), 0));
            target.setTargetFor(Ingredient.FoodGroup.EGG,       asInt(row.get("egg_grams"), 0));
            target.setTargetFor(Ingredient.FoodGroup.FAT,       asInt(row.get("fat_grams"), 0));
            target.setTargetFor(Ingredient.FoodGroup.LEGUME,    asInt(row.get("legume_grams"), 0));
            target.setTargetFor(Ingredient.FoodGroup.NUT,       asInt(row.get("nut_grams"), 0));
            target.setPlannedFor(Ingredient.FoodGroup.GRAIN,     asInt(row.get("grain_planned"), 0));
            target.setPlannedFor(Ingredient.FoodGroup.POTATO,    asInt(row.get("potato_planned"), 0));
            target.setPlannedFor(Ingredient.FoodGroup.VEGETABLE, asInt(row.get("vegetable_planned"), 0));
            target.setPlannedFor(Ingredient.FoodGroup.FRUIT,     asInt(row.get("fruit_planned"), 0));
            target.setPlannedFor(Ingredient.FoodGroup.DAIRY,     asInt(row.get("dairy_planned"), 0));
            target.setPlannedFor(Ingredient.FoodGroup.MEAT,      asInt(row.get("meat_planned"), 0));
            target.setPlannedFor(Ingredient.FoodGroup.FISH,      asInt(row.get("fish_planned"), 0));
            target.setPlannedFor(Ingredient.FoodGroup.EGG,       asInt(row.get("egg_planned"), 0));
            target.setPlannedFor(Ingredient.FoodGroup.FAT,       asInt(row.get("fat_planned"), 0));
            target.setPlannedFor(Ingredient.FoodGroup.LEGUME,    asInt(row.get("legume_planned"), 0));
            target.setPlannedFor(Ingredient.FoodGroup.NUT,       asInt(row.get("nut_planned"), 0));

            mealRepository.saveWeeklyFoodTarget(target);
            return true;
        });
    }

    /**
     * Generic row import handler that applies entity-specific logic to each row.
     *
     * @param source Identifies the import source (for error reporting)
     * @param rows List of raw row data
     * @param report Report to capture successes/failures
     * @param handler Handles each row; returns true if successfully migrated, false if not
     */
    private void importRows(String source, List<Map<String, Object>> rows, LegacyImportReport report,
                           RowHandler handler) {
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            if (handler.handle(row, i)) {
                report.markMigrated(source);
            }
        }
    }

    /**
     * Functional interface for handling individual import rows.
     */
    @FunctionalInterface
    private interface RowHandler {
        /**
         * Process a single row.
         *
         * @param row Raw row data
         * @param rowIndex Index for error reporting
         * @return true if successfully migrated, false if validation failed
         */
        boolean handle(Map<String, Object> row, int rowIndex);
    }

    // --- Legacy-only parsing helpers ---
    // These are NOT shared with MapperSupport in meal.data.internal because the contracts diverge:
    //   asEnum     — case-insensitive matching + Log.w on unknown value (vs. Enum.valueOf strict + exception)
    //   asDate     — multi-format + epoch-seconds support for old DB exports (vs. ISO-only in MapperSupport)
    //   asDateTime — same multi-format rationale as asDate
    //   asLong/asInt/asDouble — catch NumberFormatException and return fallback (vs. MapperSupport propagating the exception)
    //   asBoolean  — returns caller's fallback for unrecognized strings (vs. MapperSupport returning false via Boolean.parseBoolean)
    // Sharing them would either relax the data layer's guarantees or add legacy complexity there.
    private static <E extends Enum<E>> E asEnum(Class<E> type, Object raw, E fallback) {
        if (raw == null) return fallback;
        String value = raw.toString().trim();
        if (value.isEmpty()) return fallback;
        for (E e : type.getEnumConstants()) {
            if (e.name().equalsIgnoreCase(value)) return e;
        }
        Log.w(TAG, "Unknown enum value '" + value + "' for " + type.getSimpleName());
        return fallback;
    }

    private static LocalDate asDate(Object raw) {
        if (raw == null) return null;
        if (raw instanceof LocalDate date) return date;
        if (raw instanceof Number number) {
            return LocalDateTime.ofEpochSecond(number.longValue(), 0, java.time.ZoneOffset.UTC).toLocalDate();
        }
        String value = raw.toString().trim();
        if (value.isEmpty()) return null;
        return tryParseLocalDate(value);
    }

    private static LocalDateTime asDateTime(Object raw) {
        if (raw == null) return null;
        if (raw instanceof LocalDateTime dateTime) return dateTime;
        String value = raw.toString().trim();
        if (value.isEmpty()) return null;
        return tryParseLocalDateTime(value);
    }

    private static LocalDate tryParseLocalDate(String value) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private static LocalDateTime tryParseLocalDateTime(String value) {
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private static String asString(Object raw) {
        if (raw == null) return null;
        String s = raw.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Long asLong(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(raw.toString().trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static long asLong(Object raw, long fallback) {
        Long v = asLong(raw);
        return v != null ? v : fallback;
    }

    /**
     * Converts a raw legacy ID value (Number or String) to a String ID.
     * Numeric values greater than 0 are converted via {@link String#valueOf}.
     * Non-positive numbers and blank strings return null (sentinel for "no ID").
     */
    private static String asIdString(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number n) {
            long v = n.longValue();
            return v > 0 ? String.valueOf(v) : null;
        }
        String s = raw.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static int asInt(Object raw, int fallback) {
        if (raw == null) return fallback;
        if (raw instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double asDouble(Object raw, double fallback) {
        if (raw == null) return fallback;
        if (raw instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(raw.toString().trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean asBoolean(Object raw, boolean fallback) {
        if (raw == null) return fallback;
        if (raw instanceof Boolean b) return b;
        if (raw instanceof Number n) return n.intValue() != 0;
        String value = raw.toString().trim().toLowerCase();
        if ("1".equals(value) || "true".equals(value)) return true;
        if ("0".equals(value) || "false".equals(value)) return false;
        return fallback;
    }

    private static EnumSet<MealType> parseMealTypes(String raw) {
        EnumSet<MealType> result = EnumSet.noneOf(MealType.class);
        if (raw == null || raw.isBlank()) return result;
        for (String token : raw.split(",")) {
            MealType mealType = asEnum(MealType.class, token, null);
            if (mealType != null) result.add(mealType);
        }
        return result;
    }

    private static List<Recipe.RecipeIngredient> parseRecipeIngredients(String raw) {
        List<Recipe.RecipeIngredient> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) return result;
        for (String entry : raw.split(";")) {
            String[] parts = entry.split("\\|", 4);
            if (parts.length < 4) continue;
            String ingredientId = asIdString(parts[0]);
            result.add(new Recipe.RecipeIngredient(ingredientId, parts[1], asDouble(parts[2], 0.0), parts[3]));
        }
        return result;
    }

    private static List<Recipe.MemberRating> parseRatings(String raw) {
        List<Recipe.MemberRating> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) return result;
        for (String entry : raw.split(",")) {
            String[] parts = entry.split("\\|", 2);
            if (parts.length < 2) continue;
            String memberId = asIdString(parts[0]);
            if (memberId == null) continue;
            result.add(new Recipe.MemberRating(memberId, asInt(parts[1], 3)));
        }
        return result;
    }

    private static EnumSet<DayOfWeek> parseDaysOfWeek(String raw) {
        EnumSet<DayOfWeek> result = EnumSet.noneOf(DayOfWeek.class);
        if (raw == null || raw.isBlank()) return result;
        for (String token : raw.split(",")) {
            DayOfWeek day = asEnum(DayOfWeek.class, token, null);
            if (day != null) result.add(day);
        }
        return result;
    }

    /**
     * Report of a single import run.
     *
     * <p>The report is filled incrementally during {@link #importOnce} and becomes effectively
     * read-only once that call returns — external callers receive defensive copies via
     * {@link #migratedBySource()} and {@link #failures()}.
     *
     * <p>Callers should inspect both success counts and failures to decide whether the import is acceptable.
     */
    public static final class LegacyImportReport {
        private final Map<String, Integer> migratedBySource = new HashMap<>();
        private final List<ImportFailure> failures = new ArrayList<>();

        private void markMigrated(String source) {
            migratedBySource.put(source, migratedBySource.getOrDefault(source, 0) + 1);
        }

        private void addFailure(String source, int rowIndex, String reason) {
            ImportFailure failure = new ImportFailure(source, rowIndex, reason);
            failures.add(failure);
            Log.w(TAG, "Unmigratable row source=" + source + " row=" + rowIndex + " reason=" + reason);
        }

        /**
         * Returns per-entity-type success counts.
         *
         * <p>Example: {SOURCE_INGREDIENTS: 150, SOURCE_RECIPES: 42, SOURCE_MEAL_PLANS: 200, ...}
         *
         * @return Unmodifiable map of entity type → count of successfully migrated rows.
         */
        public Map<String, Integer> migratedBySource() {
            return Map.copyOf(migratedBySource);
        }

        /**
         * Returns all validation failures encountered during import.
         *
         * <p>Each failure indicates a row that could not be processed (e.g., missing required field, parsing error).
         * Rows that succeeded are NOT included. Failures do NOT prevent subsequent rows or entity types from importing.
         *
         * <p><strong>How to interpret failures:</strong>
         * <ul>
         *     <li>If failures list is empty, the import was successful (all rows processed).</li>
         *     <li>If failures list is non-empty, some rows were skipped due to validation errors (see {@link ImportFailure#reason()}).</li>
         *     <li>The caller should decide whether the partial import is acceptable, or retry with corrected source data.</li>
         * </ul>
         *
         * @return Unmodifiable list of failures.
         */
        public List<ImportFailure> failures() {
            return List.copyOf(failures);
        }
    }

    /**
     * A single validation failure during import.
     *
     * @param source Entity type identifier (one of SOURCE_* constants).
     * @param rowIndex 0-based index in the source list (for debugging/correction).
     * @param reason Human-readable validation error message (e.g., "missing required field: name", "required fields invalid: date/meal_type/recipe_id").
     */
    public record ImportFailure(String source, int rowIndex, String reason) {
    }
}
