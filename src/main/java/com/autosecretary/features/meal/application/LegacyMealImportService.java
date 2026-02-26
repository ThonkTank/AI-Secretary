package com.autosecretary.features.meal.application;

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
 * <p>Reference source structures:
 * <ul>
 *     <li>history/migrating/entities/*</li>
 *     <li>history/migrating/repository/parser/*</li>
 * </ul>
 *
 * <p>Compatibility rules:
 * <ul>
 *     <li>Enum values are matched case-insensitively; unknown values fall back to caller defaults.</li>
 *     <li>Date values accept ISO-8601 plus legacy parser formats (yyyy-MM-dd HH:mm:ss, dd.MM.yyyy).</li>
 *     <li>Optional fields keep null when empty/unparseable; required fields reject the row.</li>
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

    private void importIngredients(List<Map<String, Object>> rows, LegacyImportReport report) {
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            String name = asString(row.get("name"));
            if (name == null || name.isBlank()) {
                report.addFailure(SOURCE_INGREDIENTS, i, "missing required field: name");
                continue;
            }
            Ingredient ingredient = new Ingredient();
            ingredient.id = asLong(row.get("id"));
            ingredient.name = name;
            ingredient.foodGroup = asEnum(Ingredient.FoodGroup.class, row.get("food_group"), Ingredient.FoodGroup.OTHER);
            ingredient.defaultUnit = asString(row.get("default_unit"));
            ingredient.gramsPerUnit = asInt(row.get("grams_per_unit"), 1);
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
            report.markMigrated(SOURCE_INGREDIENTS);
        }
    }

    private void importRecipes(List<Map<String, Object>> rows, LegacyImportReport report) {
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            String title = asString(row.get("title"));
            if (title == null || title.isBlank()) {
                report.addFailure(SOURCE_RECIPES, i, "missing required field: title");
                continue;
            }
            Recipe recipe = new Recipe();
            recipe.id = asLong(row.get("id"));
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
            report.markMigrated(SOURCE_RECIPES);
        }
    }

    private void importMealPlans(List<Map<String, Object>> rows, LegacyImportReport report) {
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            LocalDate date = asDate(row.get("date"));
            MealType mealType = asEnum(MealType.class, row.get("meal_type"), null);
            long recipeId = asInt(row.get("recipe_id"), 0);
            if (date == null || mealType == null || recipeId <= 0) {
                report.addFailure(SOURCE_MEAL_PLANS, i, "required fields invalid: date/meal_type/recipe_id");
                continue;
            }
            MealPlan mealPlan = new MealPlan();
            mealPlan.id = asLong(row.get("id"));
            mealPlan.date = date;
            mealPlan.mealType = mealType;
            mealPlan.recipeId = recipeId;
            mealPlan.plannedServings = asInt(row.get("planned_servings"), 0);
            mealPlan.isCompleted = asBoolean(row.get("is_completed"), false);
            mealPlan.actualServings = asInt(row.get("actual_servings"), 0);
            mealPlan.completedAt = asDateTime(row.get("completed_at"));
            mealPlan.itemId = asLong(row.get("item_id"));
            mealPlan.recipeTitle = asString(row.get("recipe_title"));
            mealPlan.estimatedCalories = asInt(row.get("estimated_calories"), 0);
            mealRepository.saveMealPlan(mealPlan);
            report.markMigrated(SOURCE_MEAL_PLANS);
        }
    }

    private void importConsumption(List<Map<String, Object>> rows, LegacyImportReport report) {
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            LocalDate date = asDate(row.get("date"));
            long itemId = asInt(row.get("item_id"), 0);
            long memberId = asInt(row.get("member_id"), 0);
            if (date == null || itemId <= 0 || memberId <= 0) {
                report.addFailure(SOURCE_CONSUMPTION, i, "required fields invalid: date/item_id/member_id");
                continue;
            }
            ConsumptionLog log = new ConsumptionLog();
            log.id = asLong(row.get("id"));
            log.date = date;
            log.itemId = itemId;
            log.memberId = memberId;
            log.recipeId = asInt(row.get("recipe_id"), 0);
            log.servingsConsumed = asDouble(row.get("servings_consumed"), 0.0);
            log.calories = asInt(row.get("calories"), 0);
            log.protein = asInt(row.get("protein"), 0);
            log.carbs = asInt(row.get("carbs"), 0);
            log.fat = asInt(row.get("fat"), 0);
            mealRepository.saveConsumptionLog(log);
            report.markMigrated(SOURCE_CONSUMPTION);
        }
    }

    private void importPantry(List<Map<String, Object>> rows, LegacyImportReport report) {
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            long ingredientId = asInt(row.get("ingredient_id"), 0);
            if (ingredientId <= 0) {
                report.addFailure(SOURCE_PANTRY, i, "missing required field: ingredient_id");
                continue;
            }

            PantryItem item = new PantryItem();
            item.id = asLong(row.get("id"));
            item.ingredientId = ingredientId;
            item.ingredientName = asString(row.get("ingredient_name"));
            item.amount = asDouble(row.get("amount"), 0.0);
            item.unit = asString(row.get("unit"));
            item.purchaseDate = asDate(row.get("purchase_date"));
            item.expiryDate = asDate(row.get("expiry_date"));
            item.location = asEnum(PantryItem.StorageLocation.class, row.get("location"), PantryItem.StorageLocation.PANTRY);

            pantryRepository.savePantryItem(item);
            report.markMigrated(SOURCE_PANTRY);
        }
    }

    private void importShopping(List<Map<String, Object>> rows, LegacyImportReport report) {
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            String periodKey = asString(row.get("period_key"));
            long ingredientId = asInt(row.get("ingredient_id"), 0);
            if (periodKey == null || periodKey.isBlank() || ingredientId <= 0) {
                report.addFailure(SOURCE_SHOPPING, i, "required fields invalid: period_key/ingredient_id");
                continue;
            }

            ShoppingListItem item = new ShoppingListItem();
            item.id = asLong(row.get("id"));
            item.periodKey = periodKey;
            item.ingredientId = ingredientId;
            item.ingredientName = asString(row.get("ingredient_name"));
            item.amount = asDouble(row.get("amount"), 0.0);
            item.neededAmount = asDouble(row.get("needed_amount"), 0.0);
            item.excessAmount = asDouble(row.get("excess_amount"), 0.0);
            item.unit = asString(row.get("unit"));
            item.foodGroupLabel = asString(row.get("food_group_label"));
            item.suggestedStore = asString(row.get("suggested_store"));
            item.isPurchased = asBoolean(row.get("is_purchased"), false);
            item.estimatedPriceCents = asInt(row.get("estimated_price_cents"), 0);

            pantryRepository.saveShoppingListItem(item);
            report.markMigrated(SOURCE_SHOPPING);
        }
    }

    private void importMembers(List<Map<String, Object>> rows, LegacyImportReport report) {
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            String name = asString(row.get("name"));
            if (name == null || name.isBlank()) {
                report.addFailure(SOURCE_MEMBERS, i, "missing required field: name");
                continue;
            }

            HouseholdMember member = new HouseholdMember();
            member.id = asLong(row.get("id"));
            member.name = name;
            member.birthYear = asInt(row.get("birth_year"), LocalDate.now().getYear());
            member.gender = asEnum(HouseholdMember.Gender.class, row.get("gender"), HouseholdMember.Gender.OTHER);
            member.heightCm = asInt(row.get("height_cm"), 170);
            member.weightKg = asInt(row.get("weight_kg"), 70);
            member.targetWeightKg = asInt(row.get("target_weight_kg"), member.weightKg);
            member.activityLevel = asEnum(HouseholdMember.ActivityLevel.class, row.get("activity_level"), HouseholdMember.ActivityLevel.MODERATE);
            member.isActive = asBoolean(row.get("is_active"), true);

            mealRepository.saveHouseholdMember(member);
            report.markMigrated(SOURCE_MEMBERS);
        }
    }

    private void importPreferences(List<Map<String, Object>> rows, LegacyImportReport report) {
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            CookingPreferences p = new CookingPreferences();
            p.id = asLong(row.get("id"));
            p.maxBreakfastCooking = asInt(row.get("max_breakfast_cooking"), p.maxBreakfastCooking);
            p.maxLunchCooking = asInt(row.get("max_lunch_cooking"), p.maxLunchCooking);
            p.maxDinnerCooking = asInt(row.get("max_dinner_cooking"), p.maxDinnerCooking);
            p.maxSnackCooking = asInt(row.get("max_snack_cooking"), p.maxSnackCooking);
            p.breakfastCookingDays = parseDaysOfWeek(asString(row.get("breakfast_cooking_days")));
            p.lunchCookingDays = parseDaysOfWeek(asString(row.get("lunch_cooking_days")));
            p.dinnerCookingDays = parseDaysOfWeek(asString(row.get("dinner_cooking_days")));
            p.snackCookingDays = parseDaysOfWeek(asString(row.get("snack_cooking_days")));
            p.quickPrepMaxMinutes = asInt(row.get("quick_prep_max_minutes"), p.quickPrepMaxMinutes);

            mealRepository.saveCookingPreferences(p);
            report.markMigrated(SOURCE_PREFERENCES);
        }
    }

    private void importWeeklyTargets(List<Map<String, Object>> rows, LegacyImportReport report) {
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            String periodKey = asString(row.get("period_key"));
            if (periodKey == null || periodKey.isBlank()) {
                report.addFailure(SOURCE_WEEKLY_TARGETS, i, "missing required field: period_key");
                continue;
            }

            WeeklyFoodTarget t = new WeeklyFoodTarget();
            t.id = asLong(row.get("id"));
            t.periodKey = periodKey;
            t.grainGrams = asInt(row.get("grain_grams"), 0);
            t.potatoGrams = asInt(row.get("potato_grams"), 0);
            t.vegetableGrams = asInt(row.get("vegetable_grams"), 0);
            t.fruitGrams = asInt(row.get("fruit_grams"), 0);
            t.dairyGrams = asInt(row.get("dairy_grams"), 0);
            t.meatGrams = asInt(row.get("meat_grams"), 0);
            t.fishGrams = asInt(row.get("fish_grams"), 0);
            t.eggGrams = asInt(row.get("egg_grams"), 0);
            t.fatGrams = asInt(row.get("fat_grams"), 0);
            t.legumeGrams = asInt(row.get("legume_grams"), 0);
            t.nutGrams = asInt(row.get("nut_grams"), 0);
            t.grainPlanned = asInt(row.get("grain_planned"), 0);
            t.potatoPlanned = asInt(row.get("potato_planned"), 0);
            t.vegetablePlanned = asInt(row.get("vegetable_planned"), 0);
            t.fruitPlanned = asInt(row.get("fruit_planned"), 0);
            t.dairyPlanned = asInt(row.get("dairy_planned"), 0);
            t.meatPlanned = asInt(row.get("meat_planned"), 0);
            t.fishPlanned = asInt(row.get("fish_planned"), 0);
            t.eggPlanned = asInt(row.get("egg_planned"), 0);
            t.fatPlanned = asInt(row.get("fat_planned"), 0);
            t.legumePlanned = asInt(row.get("legume_planned"), 0);
            t.nutPlanned = asInt(row.get("nut_planned"), 0);

            mealRepository.saveWeeklyFoodTarget(t);
            report.markMigrated(SOURCE_WEEKLY_TARGETS);
        }
    }

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
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try { return LocalDate.parse(value, formatter); } catch (DateTimeParseException ignored) { }
        }
        return null;
    }

    private static LocalDateTime asDateTime(Object raw) {
        if (raw == null) return null;
        if (raw instanceof LocalDateTime dateTime) return dateTime;
        String value = raw.toString().trim();
        if (value.isEmpty()) return null;
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try { return LocalDateTime.parse(value, formatter); } catch (DateTimeParseException ignored) { }
        }
        return null;
    }

    private static String asString(Object raw) {
        return raw == null ? null : raw.toString();
    }

    private static Long asLong(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number n) return n.longValue();
        try { return Long.parseLong(raw.toString().trim()); } catch (NumberFormatException ignored) { return null; }
    }

    private static int asInt(Object raw, int fallback) {
        if (raw == null) return fallback;
        if (raw instanceof Number n) return n.intValue();
        try { return Integer.parseInt(raw.toString().trim()); } catch (NumberFormatException ignored) { return fallback; }
    }

    private static double asDouble(Object raw, double fallback) {
        if (raw == null) return fallback;
        if (raw instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(raw.toString().trim()); } catch (NumberFormatException ignored) { return fallback; }
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
            Long ingredientId = asLong(parts[0]);
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
            Long memberId = asLong(parts[0]);
            if (memberId == null || memberId <= 0) continue;
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

        public Map<String, Integer> migratedBySource() {
            return Map.copyOf(migratedBySource);
        }

        public List<ImportFailure> failures() {
            return List.copyOf(failures);
        }
    }

    public record ImportFailure(String source, int rowIndex, String reason) {
    }
}
