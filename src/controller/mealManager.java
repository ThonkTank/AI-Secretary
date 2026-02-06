package controller;

import android.content.Context;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import entities.Account;
import entities.Category;
import entities.ConsumptionLog;
import entities.HouseholdMember;
import entities.Ingredient;
import entities.MealPlan;
import entities.MealSchedule;
import entities.MealType;
import entities.PantryItem;
import entities.Recipe;
import entities.ShoppingListItem;
import entities.StorePackage;
import entities.Transaction;
import entities.WeeklyFoodTarget;
import repository.SQLrepo;
import repository.Table;

/**
 * ══════════════════════════════════════════════════════════════════════════════
 * MEAL MANAGER - UI-Controller für die Ernährungs-Anzeige
 * ══════════════════════════════════════════════════════════════════════════════
 *
 * ZIEL:
 *   Stellt die Schnittstelle zwischen Meal-UI und Datenbank dar.
 *   Lädt Rezepte, Zutaten und Haushaltsmitglieder und konvertiert sie in
 *   UI-freundliche Records.
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * DATENFLUSS
 * ──────────────────────────────────────────────────────────────────────────────
 *
 *   SQLite DB: household_members, ingredients, recipes, meal_plans, etc.
 *       │
 *       ▼ provideX() Methoden
 *   UI Records (RecipeEntry, IngredientEntry, etc.)
 *       │
 *       ▼ UI zeigt Daten an
 *   createRecipe() etc. → DB Update → Listener benachrichtigt
 */
public class mealManager {

    private Context context;
    private SQLrepo repo;
    private MealListener listener;

    public mealManager(Context context) {
        this.context = context;
        this.repo = SQLrepo.getInstance(context);
    }

    // ============================================================================
    // LISTENER INTERFACE
    // ============================================================================

    public interface MealListener {
        void onDataUpdated();
    }

    public void setListener(MealListener listener) {
        this.listener = listener;
    }

    private void notifyListener() {
        if (listener != null) listener.onDataUpdated();
    }

    /**
     * Öffentliche Methode für externe Aufrufe (z.B. generateMealPlan).
     */
    public void notifyListeners() {
        notifyListener();
    }

    // ============================================================================
    // UI RECORDS
    // ============================================================================

    /**
     * Haushaltsmitglied für die Anzeige.
     */
    public record MemberEntry(
        Long id,
        String name,
        int age,
        String gender,
        int dailyCalories,
        String activityLabel,
        boolean isActive
    ) {}

    /**
     * Rezept für die Anzeige.
     */
    public record RecipeEntry(
        Long id,
        String title,
        String mealType,
        int totalTime,
        int calories,
        int servings,
        String tags,
        boolean isFavorite,
        String formattedLastUsed
    ) {}

    /**
     * Zutat für Dropdowns.
     */
    public record IngredientEntry(
        Long id,
        String name,
        String foodGroup,
        String unit,
        int caloriesPer100
    ) {}

    /**
     * Wochenplan-Eintrag für die Anzeige.
     */
    public record MealPlanEntry(
        Long id,
        LocalDate date,
        String dayName,
        String mealType,
        Long recipeId,
        String recipeTitle,
        int servings,
        int calories,
        boolean isCompleted
    ) {}

    /**
     * Fortschritt pro Lebensmittelgruppe.
     */
    public record FoodGroupProgress(
        String group,
        String label,
        String icon,
        int targetGrams,
        int plannedGrams,
        double percent,
        String formatted
    ) {}

    /**
     * Mahlzeiten-Kalender Eintrag für die Anzeige.
     * 7 Tage × 4 Mahlzeiten = 28 Einträge.
     */
    public record ScheduleEntry(
        Long id,
        DayOfWeek day,
        String dayLabel,
        MealType mealType,
        String mealLabel,
        String mealIcon,
        LocalTime time,
        boolean isEnabled,
        String formattedTime
    ) {}

    /**
     * Einkaufslisten-Eintrag für die Anzeige.
     * Gruppiert nach FoodGroup für übersichtliche Listen.
     */
    public record ShoppingEntry(
        Long id,
        Long ingredientId,
        String ingredientName,
        String foodGroup,
        String foodGroupIcon,
        double amount,
        double neededAmount,
        double excessAmount,
        String unit,
        String formattedAmount,
        String formattedExcess,
        String suggestedStore,
        boolean isPurchased,
        int estimatedPriceCents,
        String formattedPrice
    ) {}

    /**
     * Zusammenfassung für den Einkauf.
     */
    public record ShoppingSummary(
        String weekKey,
        String suggestedStore,
        int totalItems,
        int purchasedItems,
        int estimatedTotalCents,
        String formattedTotal,
        boolean isComplete
    ) {}

    /**
     * Vorratsartikel für die Anzeige.
     */
    public record PantryEntry(
        Long id,
        Long ingredientId,
        String name,
        String amount,
        double rawAmount,
        String unit,
        String location,
        String locationIcon,
        PantryItem.StorageLocation locationType,
        String expiryInfo,
        LocalDate expiryDate,
        boolean isExpiringSoon,
        boolean isExpired
    ) {}

    // ============================================================================
    // READ OPERATIONS
    // ============================================================================

    /**
     * Liefert alle aktiven Haushaltsmitglieder.
     */
    public List<MemberEntry> provideMembers() {
        List<MemberEntry> result = new ArrayList<>();
        List<HouseholdMember> members = repo.fetchAll(Table.HOUSEHOLD_MEMBERS);

        for (HouseholdMember m : members) {
            if (!m.isActive) continue;

            int age = m.getAge();
            String activityLabel = m.activityLevel.label;

            result.add(new MemberEntry(
                m.id,
                m.name,
                age,
                m.gender.name(),
                m.calculateTDEE(),
                activityLabel,
                m.isActive
            ));
        }
        return result;
    }

    /**
     * Liefert alle Rezepte.
     */
    public List<RecipeEntry> provideAllRecipes() {
        return provideRecipes(null);
    }

    /**
     * Liefert Rezepte, optional gefiltert nach Mahlzeit-Typ.
     */
    public List<RecipeEntry> provideRecipes(MealType filter) {
        List<RecipeEntry> result = new ArrayList<>();
        List<Recipe> recipes = repo.fetchAll(Table.RECIPES);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        for (Recipe r : recipes) {
            if (filter != null && r.mealType != filter) continue;

            String mealTypeLabel = switch (r.mealType) {
                case BREAKFAST -> "Frühstück";
                case LUNCH -> "Mittagessen";
                case DINNER -> "Abendessen";
                case SNACK -> "Snack";
            };

            String lastUsed = r.lastUsed != null ? fmt.format(r.lastUsed) : "Nie";

            result.add(new RecipeEntry(
                r.id,
                r.title,
                mealTypeLabel,
                r.getTotalTime(),
                r.totalCalories,
                r.servings,
                r.tags,
                r.isFavorite,
                lastUsed
            ));
        }
        return result;
    }

    /**
     * Liefert alle Zutaten für Dropdowns.
     */
    public List<IngredientEntry> provideIngredients() {
        List<IngredientEntry> result = new ArrayList<>();
        List<Ingredient> ingredients = repo.fetchAll(Table.INGREDIENTS);

        for (Ingredient ing : ingredients) {
            String groupLabel = switch (ing.foodGroup) {
                case GRAIN -> "Getreide";
                case VEGETABLE -> "Gemüse";
                case FRUIT -> "Obst";
                case DAIRY -> "Milch";
                case MEAT -> "Fleisch";
                case FISH -> "Fisch";
                case EGG -> "Eier";
                case FAT -> "Fette";
                case LEGUME -> "Hülsenfrüchte";
                case NUT -> "Nüsse";
                case POTATO -> "Kartoffeln";
                case OTHER -> "Sonstiges";
            };

            result.add(new IngredientEntry(
                ing.id,
                ing.name,
                groupLabel,
                ing.defaultUnit,
                ing.caloriesPer100
            ));
        }
        return result;
    }

    /**
     * Liefert Wochenplan für eine Woche.
     */
    public List<MealPlanEntry> provideMealPlan(LocalDate weekStart) {
        List<MealPlanEntry> result = new ArrayList<>();
        List<MealPlan> plans = repo.fetchAll(Table.MEAL_PLANS);

        LocalDate weekEnd = weekStart.plusDays(6);
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN);

        for (MealPlan mp : plans) {
            if (mp.date.isBefore(weekStart) || mp.date.isAfter(weekEnd)) continue;

            String mealTypeLabel = switch (mp.mealType) {
                case BREAKFAST -> "Frühstück";
                case LUNCH -> "Mittagessen";
                case DINNER -> "Abendessen";
                case SNACK -> "Snack";
            };

            result.add(new MealPlanEntry(
                mp.id,
                mp.date,
                dayFmt.format(mp.date),
                mealTypeLabel,
                mp.recipeId,
                mp.recipeTitle,
                mp.plannedServings,
                mp.estimatedCalories,
                mp.isCompleted
            ));
        }
        return result;
    }

    /**
     * Liefert Fortschritt pro Lebensmittelgruppe für eine Woche.
     */
    public List<FoodGroupProgress> provideFoodGroupProgress(LocalDate weekStart) {
        List<FoodGroupProgress> result = new ArrayList<>();

        // WeekKey berechnen (z.B. "2026-W06")
        WeekFields weekFields = WeekFields.of(Locale.GERMANY);
        int week = weekStart.get(weekFields.weekOfWeekBasedYear());
        int year = weekStart.get(weekFields.weekBasedYear());
        String weekKey = String.format("%d-W%02d", year, week);

        // WeeklyFoodTarget laden oder berechnen
        WeeklyFoodTarget target = repo.fetch(Table.WEEKLY_FOOD_TARGETS, Map.of("week_key", weekKey));

        if (target == null) {
            // Noch kein Target für diese Woche - leere Balken zeigen
            target = calculateWeeklyTarget(weekKey);
        }

        // FoodGroups mit Icons
        record GroupInfo(String group, String label, String icon, int target, int planned) {}
        List<GroupInfo> groups = List.of(
            new GroupInfo("GRAIN", "Getreide", "🌾", target.grainGrams, target.grainPlanned),
            new GroupInfo("POTATO", "Kartoffeln", "🥔", target.potatoGrams, target.potatoPlanned),
            new GroupInfo("VEGETABLE", "Gemüse", "🥬", target.vegetableGrams, target.vegetablePlanned),
            new GroupInfo("FRUIT", "Obst", "🍎", target.fruitGrams, target.fruitPlanned),
            new GroupInfo("DAIRY", "Milch", "🥛", target.dairyGrams, target.dairyPlanned),
            new GroupInfo("MEAT", "Fleisch", "🥩", target.meatGrams, target.meatPlanned),
            new GroupInfo("FISH", "Fisch", "🐟", target.fishGrams, target.fishPlanned),
            new GroupInfo("EGG", "Eier", "🥚", target.eggGrams, target.eggPlanned),
            new GroupInfo("LEGUME", "Hülsen", "🫘", target.legumeGrams, target.legumePlanned),
            new GroupInfo("NUT", "Nüsse", "🥜", target.nutGrams, target.nutPlanned)
        );

        for (GroupInfo g : groups) {
            double percent = g.target > 0 ? (double) g.planned / g.target * 100 : 0;
            String formatted = formatGrams(g.planned) + "/" + formatGrams(g.target);

            result.add(new FoodGroupProgress(
                g.group,
                g.label,
                g.icon,
                g.target,
                g.planned,
                Math.min(percent, 100),
                formatted
            ));
        }

        return result;
    }

    private String formatGrams(int grams) {
        if (grams >= 1000) {
            return String.format("%.1f kg", grams / 1000.0);
        }
        return grams + "g";
    }

    /**
     * Liefert alle Mahlzeiten-Zeiten (7 Tage × 4 Mahlzeiten = 28 Einträge).
     */
    public List<ScheduleEntry> provideSchedule() {
        List<ScheduleEntry> result = new ArrayList<>();
        List<MealSchedule> schedules = repo.fetchAll(Table.MEAL_SCHEDULES);

        for (MealSchedule ms : schedules) {
            String dayLabel = switch (ms.dayOfWeek) {
                case MONDAY -> "Mo";
                case TUESDAY -> "Di";
                case WEDNESDAY -> "Mi";
                case THURSDAY -> "Do";
                case FRIDAY -> "Fr";
                case SATURDAY -> "Sa";
                case SUNDAY -> "So";
            };

            result.add(new ScheduleEntry(
                ms.id,
                ms.dayOfWeek,
                dayLabel,
                ms.mealType,
                ms.mealType.label,
                ms.mealType.icon,
                ms.scheduledTime,
                ms.isEnabled,
                ms.getFormattedTime()
            ));
        }
        return result;
    }

    /**
     * Liefert die geplante Zeit für eine Mahlzeit.
     * @return LocalTime oder null wenn nicht geplant
     */
    public LocalTime getScheduledTime(DayOfWeek day, MealType type) {
        List<MealSchedule> schedules = repo.fetchAll(Table.MEAL_SCHEDULES);
        for (MealSchedule ms : schedules) {
            if (ms.dayOfWeek == day && ms.mealType == type && ms.isEnabled) {
                return ms.scheduledTime;
            }
        }
        return null;
    }

    /**
     * Prüft ob eine Mahlzeit für den Tag geplant ist.
     */
    public boolean isMealScheduled(DayOfWeek day, MealType type) {
        return getScheduledTime(day, type) != null;
    }

    /**
     * Liefert alle Vorratsartikel, optional gefiltert nach Lagerort.
     * Sortiert nach Ablaufdatum (bald ablaufende zuerst).
     */
    public List<PantryEntry> providePantry(PantryItem.StorageLocation filter) {
        List<PantryEntry> result = new ArrayList<>();
        List<PantryItem> items = repo.fetchAll(Table.PANTRY_ITEMS);

        // Nach Ablaufdatum sortieren (bald ablaufende zuerst)
        items.sort((a, b) -> {
            if (a.expiryDate == null && b.expiryDate == null) return 0;
            if (a.expiryDate == null) return 1;
            if (b.expiryDate == null) return -1;
            return a.expiryDate.compareTo(b.expiryDate);
        });

        for (PantryItem p : items) {
            if (filter != null && p.location != filter) continue;

            String locationIcon = switch (p.location) {
                case FRIDGE -> "🧊";
                case FREEZER -> "❄️";
                case PANTRY -> "🏠";
            };

            result.add(new PantryEntry(
                p.id,
                p.ingredientId,
                p.ingredientName,
                p.getFormattedAmount(),
                p.amount,
                p.unit,
                p.location.label,
                locationIcon,
                p.location,
                p.getExpiryInfo(),
                p.expiryDate,
                p.isExpiringSoon(),
                p.isExpired()
            ));
        }
        return result;
    }

    /**
     * Berechnet Wochenbedarf basierend auf Haushaltsmitgliedern.
     */
    private WeeklyFoodTarget calculateWeeklyTarget(String weekKey) {
        WeeklyFoodTarget target = new WeeklyFoodTarget();
        target.weekKey = weekKey;

        List<HouseholdMember> members = repo.fetchAll(Table.HOUSEHOLD_MEMBERS);
        for (HouseholdMember m : members) {
            if (!m.isActive) continue;

            double factor = m.getFoodFactor();

            target.grainGrams += (int) (Ingredient.FoodGroup.GRAIN.weeklyGramsPerAdult * factor);
            target.potatoGrams += (int) (Ingredient.FoodGroup.POTATO.weeklyGramsPerAdult * factor);
            target.vegetableGrams += (int) (Ingredient.FoodGroup.VEGETABLE.weeklyGramsPerAdult * factor);
            target.fruitGrams += (int) (Ingredient.FoodGroup.FRUIT.weeklyGramsPerAdult * factor);
            target.dairyGrams += (int) (Ingredient.FoodGroup.DAIRY.weeklyGramsPerAdult * factor);
            target.meatGrams += (int) (Ingredient.FoodGroup.MEAT.weeklyGramsPerAdult * factor);
            target.fishGrams += (int) (Ingredient.FoodGroup.FISH.weeklyGramsPerAdult * factor);
            target.eggGrams += (int) (Ingredient.FoodGroup.EGG.weeklyGramsPerAdult * factor);
            target.fatGrams += (int) (Ingredient.FoodGroup.FAT.weeklyGramsPerAdult * factor);
            target.legumeGrams += (int) (Ingredient.FoodGroup.LEGUME.weeklyGramsPerAdult * factor);
            target.nutGrams += (int) (Ingredient.FoodGroup.NUT.weeklyGramsPerAdult * factor);
        }

        return target;
    }

    // ============================================================================
    // WRITE OPERATIONS
    // ============================================================================

    /**
     * Erstellt ein neues Rezept.
     */
    public void createRecipe(Recipe recipe) {
        // Nährwerte berechnen
        calculateRecipeNutrition(recipe);
        repo.write(recipe);
        notifyListener();
    }

    /**
     * Aktualisiert ein Rezept.
     */
    public void updateRecipe(Recipe recipe) {
        calculateRecipeNutrition(recipe);
        repo.write(recipe);
        notifyListener();
    }

    /**
     * Berechnet die Gesamtnährwerte eines Rezepts basierend auf den Zutaten.
     */
    private void calculateRecipeNutrition(Recipe recipe) {
        int totalCalories = 0;
        int totalProtein = 0;
        int totalCarbs = 0;
        int totalFat = 0;

        if (recipe.ingredients != null) {
            for (Recipe.RecipeIngredient ri : recipe.ingredients) {
                Ingredient ing = getIngredient(ri.ingredientId());
                if (ing == null) continue;

                // Umrechnung: amount in unit → Gramm
                double grams;
                if ("g".equals(ri.unit()) || "ml".equals(ri.unit())) {
                    grams = ri.amount();
                } else {
                    // Unit-basiert (z.B. "Stück")
                    grams = ri.amount() * ing.gramsPerUnit;
                }

                // Nährwerte pro 100g → tatsächliche Menge
                double factor = grams / 100.0;
                totalCalories += (int) (ing.caloriesPer100 * factor);
                totalProtein += (int) (ing.proteinPer100 * factor);
                totalCarbs += (int) (ing.carbsPer100 * factor);
                totalFat += (int) (ing.fatPer100 * factor);
            }
        }

        recipe.totalCalories = totalCalories;
        recipe.totalProtein = totalProtein;
        recipe.totalCarbs = totalCarbs;
        recipe.totalFat = totalFat;
    }

    /**
     * Löscht ein Rezept.
     */
    public void deleteRecipe(Long id) {
        repo.delete(Table.RECIPES, id);
        notifyListener();
    }

    /**
     * Schaltet Favorit-Status um.
     */
    public void toggleFavorite(Long recipeId) {
        Recipe recipe = repo.fetch(Table.RECIPES, recipeId);
        if (recipe != null) {
            recipe.isFavorite = !recipe.isFavorite;
            repo.write(recipe);
            notifyListener();
        }
    }

    /**
     * Erstellt ein neues Haushaltsmitglied.
     */
    public void createMember(HouseholdMember member) {
        repo.write(member);
        notifyListener();
    }

    /**
     * Aktualisiert ein Haushaltsmitglied.
     */
    public void updateMember(HouseholdMember member) {
        repo.write(member);
        notifyListener();
    }

    /**
     * Löscht ein Haushaltsmitglied.
     */
    public void deleteMember(Long id) {
        repo.delete(Table.HOUSEHOLD_MEMBERS, id);
        notifyListener();
    }

    /**
     * Lädt ein einzelnes Haushaltsmitglied per ID.
     */
    public HouseholdMember getMember(Long id) {
        return repo.fetch(Table.HOUSEHOLD_MEMBERS, id);
    }

    /**
     * Aktualisiert einen Schedule-Slot (Zeit ändern oder aktivieren/deaktivieren).
     */
    public void updateSchedule(Long scheduleId, LocalTime time, boolean enabled) {
        MealSchedule schedule = repo.fetch(Table.MEAL_SCHEDULES, scheduleId);
        if (schedule != null) {
            schedule.scheduledTime = time;
            schedule.isEnabled = enabled;
            repo.write(schedule);
            notifyListener();
        }
    }

    // ============================================================================
    // PANTRY CRUD
    // ============================================================================

    /**
     * Fügt einen Artikel zum Vorrat hinzu.
     */
    public void addToPantry(Long ingredientId, double amount, String unit,
                            PantryItem.StorageLocation location, LocalDate expiryDate) {
        Ingredient ing = getIngredient(ingredientId);
        if (ing == null) return;

        PantryItem item = new PantryItem.Builder(ingredientId, ing.name, amount, unit)
            .location(location)
            .expiryDate(expiryDate)
            .build();

        repo.write(item);
        notifyListener();
    }

    /**
     * Aktualisiert einen Vorratsartikel.
     */
    public void updatePantryItem(PantryItem item) {
        repo.write(item);
        notifyListener();
    }

    /**
     * Passt die Menge eines Vorratsartikels an (für Stepper).
     * Löscht den Artikel automatisch wenn die Menge ≤ 0 wird.
     */
    public void adjustPantryAmount(Long itemId, double delta) {
        PantryItem item = repo.fetch(Table.PANTRY_ITEMS, itemId);
        if (item == null) return;

        item.amount += delta;
        if (item.amount <= 0) {
            repo.delete(Table.PANTRY_ITEMS, itemId);
        } else {
            repo.write(item);
        }
        notifyListener();
    }

    /**
     * Löscht einen Vorratsartikel.
     */
    public void deletePantryItem(Long id) {
        repo.delete(Table.PANTRY_ITEMS, id);
        notifyListener();
    }

    /**
     * Lädt einen Vorratsartikel per ID.
     */
    public PantryItem getPantryItem(Long id) {
        return repo.fetch(Table.PANTRY_ITEMS, id);
    }

    // ============================================================================
    // MEAL PLAN CRUD
    // ============================================================================

    /**
     * Erstellt einen neuen Wochenplan-Eintrag.
     * Setzt automatisch recipeTitle und estimatedCalories aus dem Rezept.
     */
    public void createMealPlan(MealPlan plan) {
        // Rezept-Daten denormalisieren
        if (plan.recipeId != null) {
            Recipe recipe = repo.fetch(Table.RECIPES, plan.recipeId);
            if (recipe != null) {
                plan.recipeTitle = recipe.title;
                plan.estimatedCalories = recipe.totalCalories * plan.plannedServings / recipe.servings;
            }
        }
        repo.write(plan);
        notifyListener();
    }

    /**
     * Aktualisiert einen Wochenplan-Eintrag.
     */
    public void updateMealPlan(MealPlan plan) {
        // Rezept-Daten denormalisieren
        if (plan.recipeId != null) {
            Recipe recipe = repo.fetch(Table.RECIPES, plan.recipeId);
            if (recipe != null) {
                plan.recipeTitle = recipe.title;
                plan.estimatedCalories = recipe.totalCalories * plan.plannedServings / recipe.servings;
            }
        }
        repo.write(plan);
        notifyListener();
    }

    /**
     * Löscht einen Wochenplan-Eintrag.
     */
    public void deleteMealPlan(Long id) {
        repo.delete(Table.MEAL_PLANS, id);
        notifyListener();
    }

    /**
     * Lädt einen Wochenplan-Eintrag per ID.
     */
    public MealPlan getMealPlan(Long id) {
        return repo.fetch(Table.MEAL_PLANS, id);
    }

    /**
     * Findet einen Wochenplan-Eintrag für Datum + Mahlzeit-Typ.
     */
    public MealPlan findMealPlan(LocalDate date, MealType mealType) {
        List<MealPlan> plans = repo.fetchAll(Table.MEAL_PLANS);
        for (MealPlan mp : plans) {
            if (mp.date.equals(date) && mp.mealType == mealType) {
                return mp;
            }
        }
        return null;
    }

    // ============================================================================
    // MEAL COMPLETION
    // ============================================================================

    /**
     * Markiert Mahlzeit als abgeschlossen und reduziert Vorrat.
     * Wird von todoManager.completeSlot() aufgerufen wenn ein Meal-Task abgehakt wird.
     *
     * @param mealPlanId ID des MealPlan-Eintrags
     * @param actualServings Tatsächlich gegessene Portionen (0 = geplante Portionen)
     */
    public void completeMeal(Long mealPlanId, int actualServings) {
        if (mealPlanId == null) return;

        MealPlan meal = repo.fetch(Table.MEAL_PLANS, mealPlanId);
        if (meal == null || meal.isCompleted) return;

        // Mahlzeit als erledigt markieren
        meal.isCompleted = true;
        meal.actualServings = actualServings > 0 ? actualServings : meal.plannedServings;
        meal.completedAt = LocalDateTime.now();
        repo.write(meal);

        // Vorrat reduzieren
        Recipe recipe = repo.fetch(Table.RECIPES, meal.recipeId);
        if (recipe != null && recipe.ingredients != null) {
            double portionFactor = (double) meal.actualServings / recipe.servings;

            for (Recipe.RecipeIngredient ing : recipe.ingredients) {
                double needed = ing.amount() * portionFactor;
                consumeFromPantry(ing.ingredientId(), needed, ing.unit());
            }
        }

        // ConsumptionLog erstellen
        logConsumption(meal);

        notifyListener();
    }

    /**
     * Reduziert den Vorrat einer Zutat.
     * Sucht passende PantryItems (FIFO nach Ablaufdatum) und reduziert deren Menge.
     */
    private void consumeFromPantry(Long ingredientId, double amount, String unit) {
        if (ingredientId == null || amount <= 0) return;

        // Alle Pantry-Items für diese Zutat laden, sortiert nach Ablaufdatum
        List<PantryItem> items = repo.fetchAll(Table.PANTRY_ITEMS);
        List<PantryItem> matching = new ArrayList<>();

        for (PantryItem p : items) {
            if (ingredientId.equals(p.ingredientId) && unit.equals(p.unit)) {
                matching.add(p);
            }
        }

        // Nach Ablaufdatum sortieren (null = später, damit zuerst ablaufende zuerst verbraucht werden)
        matching.sort((a, b) -> {
            if (a.expiryDate == null && b.expiryDate == null) return 0;
            if (a.expiryDate == null) return 1;
            if (b.expiryDate == null) return -1;
            return a.expiryDate.compareTo(b.expiryDate);
        });

        // Menge reduzieren
        double remaining = amount;
        for (PantryItem item : matching) {
            if (remaining <= 0) break;

            if (item.amount <= remaining) {
                // Item komplett verbraucht
                remaining -= item.amount;
                repo.delete(Table.PANTRY_ITEMS, item.id);
            } else {
                // Item teilweise verbraucht
                item.amount -= remaining;
                remaining = 0;
                repo.write(item);
            }
        }
        // Wenn remaining > 0, dann war nicht genug Vorrat da - ist OK, ignorieren
    }

    /**
     * Erstellt einen ConsumptionLog-Eintrag für die Mahlzeit.
     * Trackt Nährwerte pro Haushaltsmitglied.
     */
    private void logConsumption(MealPlan meal) {
        Recipe recipe = repo.fetch(Table.RECIPES, meal.recipeId);
        if (recipe == null) return;

        // Nährwerte für die gegessenen Portionen
        double factor = (double) meal.actualServings / recipe.servings;
        int calories = (int) (recipe.totalCalories * factor);
        int protein = (int) (recipe.totalProtein * factor);
        int carbs = (int) (recipe.totalCarbs * factor);
        int fat = (int) (recipe.totalFat * factor);

        // Pro Haushaltsmitglied einen Log-Eintrag erstellen
        List<HouseholdMember> members = repo.fetchAll(Table.HOUSEHOLD_MEMBERS);
        int activeCount = 0;
        for (HouseholdMember m : members) {
            if (m.isActive) activeCount++;
        }
        if (activeCount == 0) activeCount = 1;

        // Nährwerte gleichmäßig auf Mitglieder verteilen
        int caloriesPerMember = calories / activeCount;
        int proteinPerMember = protein / activeCount;
        int carbsPerMember = carbs / activeCount;
        int fatPerMember = fat / activeCount;

        for (HouseholdMember m : members) {
            if (!m.isActive) continue;

            ConsumptionLog log = new ConsumptionLog();
            log.date = meal.date;
            log.memberId = m.id;
            log.recipeId = meal.recipeId;
            log.servingsConsumed = Math.max(1, meal.actualServings / activeCount);
            log.calories = caloriesPerMember;
            log.protein = proteinPerMember;
            log.carbs = carbsPerMember;
            log.fat = fatPerMember;
            repo.write(log);
        }
    }

    // ============================================================================
    // SHOPPING LIST
    // ============================================================================

    /**
     * Liefert Einkaufsliste für eine Woche, gruppiert nach FoodGroup.
     */
    public List<ShoppingEntry> provideShoppingList(String weekKey) {
        List<ShoppingEntry> result = new ArrayList<>();
        List<ShoppingListItem> items = repo.fetchAll(Table.SHOPPING_LIST_ITEMS);

        for (ShoppingListItem item : items) {
            if (!weekKey.equals(item.weekKey)) continue;

            // FoodGroup und Icon aus Ingredient laden
            String foodGroup = item.foodGroupLabel != null ? item.foodGroupLabel : "Sonstiges";
            String foodGroupIcon = "📦";
            if (item.ingredientId != null) {
                Ingredient ing = getIngredient(item.ingredientId);
                if (ing != null) {
                    foodGroup = ing.foodGroup.label;
                    foodGroupIcon = getFoodGroupIcon(ing.foodGroup);
                }
            }

            // Geschätzter Preis aus StorePackage
            int estimatedPrice = estimatePrice(item.ingredientId, item.amount, item.suggestedStore);

            result.add(new ShoppingEntry(
                item.id,
                item.ingredientId,
                item.ingredientName,
                foodGroup,
                foodGroupIcon,
                item.amount,
                item.neededAmount,
                item.excessAmount,
                item.unit,
                item.getFormattedAmount(),
                item.getFormattedExcess(),
                item.suggestedStore,
                item.isPurchased,
                estimatedPrice,
                estimatedPrice > 0 ? formatCents(estimatedPrice) : ""
            ));
        }

        // Nach FoodGroup sortieren
        result.sort((a, b) -> a.foodGroup().compareTo(b.foodGroup()));
        return result;
    }

    /**
     * Liefert Zusammenfassung für den Einkauf.
     */
    public ShoppingSummary provideShoppingSummary(String weekKey) {
        List<ShoppingListItem> items = repo.fetchAll(Table.SHOPPING_LIST_ITEMS);

        int total = 0;
        int purchased = 0;
        int totalCents = 0;
        String suggestedStore = null;

        for (ShoppingListItem item : items) {
            if (!weekKey.equals(item.weekKey)) continue;
            total++;
            if (item.isPurchased) purchased++;
            if (suggestedStore == null && item.suggestedStore != null) {
                suggestedStore = item.suggestedStore;
            }
            totalCents += estimatePrice(item.ingredientId, item.amount, item.suggestedStore);
        }

        return new ShoppingSummary(
            weekKey,
            suggestedStore != null ? suggestedStore : "Nicht ermittelt",
            total,
            purchased,
            totalCents,
            formatCents(totalCents),
            total > 0 && total == purchased
        );
    }

    /**
     * Generiert Einkaufsliste aus MealPlans.
     *
     * Algorithmus:
     * 1. Zutaten aus allen MealPlans der Woche aggregieren
     * 2. Vorrat (Pantry) abziehen
     * 3. Bevorzugten Laden ermitteln
     * 4. Auf Packungsgrößen aufrunden
     * 5. ShoppingListItems erstellen
     */
    public void generateShoppingList(LocalDate weekStart) {
        String weekKey = getWeekKey(weekStart);

        // Alte Einträge löschen
        deleteShoppingListForWeek(weekKey);

        // 1. Zutaten aus MealPlans aggregieren
        Map<Long, Double> needed = new HashMap<>();  // ingredientId -> amount
        Map<Long, String> units = new HashMap<>();   // ingredientId -> unit
        Map<Long, String> names = new HashMap<>();   // ingredientId -> name

        List<MealPlan> plans = repo.fetchAll(Table.MEAL_PLANS);
        LocalDate weekEnd = weekStart.plusDays(6);

        for (MealPlan plan : plans) {
            if (plan.date.isBefore(weekStart) || plan.date.isAfter(weekEnd)) continue;
            if (plan.isCompleted) continue;

            Recipe recipe = repo.fetch(Table.RECIPES, plan.recipeId);
            if (recipe == null || recipe.ingredients == null) continue;

            double scaleFactor = (double) plan.plannedServings / recipe.servings;

            for (Recipe.RecipeIngredient ri : recipe.ingredients) {
                double amount = ri.amount() * scaleFactor;
                needed.merge(ri.ingredientId(), amount, Double::sum);
                units.putIfAbsent(ri.ingredientId(), ri.unit());
                names.putIfAbsent(ri.ingredientId(), ri.ingredientName());
            }
        }

        // 2. Vorrat abziehen
        List<PantryItem> pantry = repo.fetchAll(Table.PANTRY_ITEMS);
        for (PantryItem item : pantry) {
            if (item.ingredientId == null) continue;
            String neededUnit = units.get(item.ingredientId);
            if (neededUnit == null || !neededUnit.equals(item.unit)) continue;

            Double n = needed.get(item.ingredientId);
            if (n != null) {
                double remaining = n - item.amount;
                if (remaining <= 0) {
                    needed.remove(item.ingredientId);
                } else {
                    needed.put(item.ingredientId, remaining);
                }
            }
        }

        if (needed.isEmpty()) return;

        // 3. Bevorzugten Laden ermitteln
        String suggestedStore = determinePreferredStore(needed.keySet());

        // 4+5. Packungsgrößen aufrunden und Items erstellen
        for (Map.Entry<Long, Double> entry : needed.entrySet()) {
            Long ingredientId = entry.getKey();
            double neededAmount = entry.getValue();
            String unit = units.get(ingredientId);
            String name = names.get(ingredientId);

            // Beste Packung finden
            double finalAmount = neededAmount;
            double excess = 0;

            StorePackage pkg = findBestPackage(ingredientId, suggestedStore, unit);
            if (pkg != null) {
                int packagesNeeded = pkg.calculatePackagesNeeded(neededAmount);
                finalAmount = pkg.getTotalAmount(packagesNeeded);
                excess = pkg.getExcess(neededAmount);
            } else {
                // Keine Packung bekannt - auf ganze Einheiten aufrunden
                Ingredient ing = getIngredient(ingredientId);
                if (ing != null && ing.isWholeUnit) {
                    finalAmount = Math.ceil(neededAmount);
                    excess = finalAmount - neededAmount;
                }
            }

            // FoodGroup für Sortierung
            Ingredient ing = getIngredient(ingredientId);
            String foodGroupLabel = ing != null ? ing.foodGroup.label : "Sonstiges";

            ShoppingListItem item = new ShoppingListItem.Builder(weekKey, name, finalAmount, unit)
                .ingredientId(ingredientId)
                .foodGroup(foodGroupLabel)
                .neededAmount(neededAmount)
                .excessAmount(excess)
                .suggestedStore(suggestedStore)
                .build();

            repo.write(item);
        }

        notifyListener();
    }

    /**
     * Ermittelt den bevorzugten Laden basierend auf StorePackage-Historie.
     *
     * Scoring:
     * - Coverage: Wie viele Zutaten hat der Laden? (10 Punkte pro Zutat)
     * - Recency: Wann wurde zuletzt dort eingekauft? (max 30 Punkte, abnehmend)
     */
    public String determinePreferredStore(Set<Long> ingredientIds) {
        if (ingredientIds.isEmpty()) return null;

        List<StorePackage> packages = repo.fetchAll(Table.STORE_PACKAGES);
        Map<String, Double> storeScores = new HashMap<>();

        LocalDate today = LocalDate.now();

        for (StorePackage pkg : packages) {
            if (!ingredientIds.contains(pkg.ingredientId)) continue;

            String store = pkg.storeName;
            double score = storeScores.getOrDefault(store, 0.0);

            // Coverage Score: 10 Punkte pro gefundener Zutat
            score += 10;

            // Recency Score: 30 Punkte max, abnehmend
            if (pkg.lastPurchased != null) {
                long daysSince = ChronoUnit.DAYS.between(pkg.lastPurchased, today);
                double recencyScore = Math.max(0, 30 - daysSince);
                score += recencyScore;
            }

            storeScores.put(store, score);
        }

        // Höchsten Score finden
        return storeScores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    /**
     * Schließt Einkauf ab:
     * 1. Alle Items als gekauft markieren
     * 2. In Vorrat übernehmen
     * 3. Transaktion erstellen
     * 4. StorePackage lastPurchased aktualisieren
     */
    public void finishShopping(String weekKey, Long accountId, int totalPriceCents) {
        List<ShoppingListItem> items = repo.fetchAll(Table.SHOPPING_LIST_ITEMS);
        LocalDate today = LocalDate.now();
        String store = null;

        for (ShoppingListItem item : items) {
            if (!weekKey.equals(item.weekKey)) continue;
            if (item.isPurchased) continue;

            // 1. Als gekauft markieren
            item.markPurchased();
            repo.write(item);

            if (store == null) store = item.suggestedStore;

            // 2. In Vorrat übernehmen
            if (item.ingredientId != null) {
                Ingredient ing = getIngredient(item.ingredientId);
                PantryItem pantry = new PantryItem.Builder(
                    item.ingredientId,
                    item.ingredientName,
                    item.amount,
                    item.unit
                )
                    .purchaseDate(today)
                    .expiryDate(ing != null && ing.shelfLifeDays > 0
                        ? today.plusDays(ing.shelfLifeDays) : null)
                    .location(ing != null && ing.requiresRefrigeration
                        ? PantryItem.StorageLocation.FRIDGE
                        : PantryItem.StorageLocation.PANTRY)
                    .build();

                repo.write(pantry);
            }

            // 4. StorePackage lastPurchased aktualisieren
            if (item.suggestedStore != null && item.ingredientId != null) {
                updateStorePackageLastPurchased(item.ingredientId, item.suggestedStore, today);
            }
        }

        // 3. Transaktion erstellen (falls Betrag > 0)
        if (totalPriceCents > 0 && accountId != null) {
            createShoppingTransaction(accountId, totalPriceCents, store, weekKey);
        }

        notifyListener();
    }

    /**
     * Einzelnes Item als gekauft/ungekauft markieren.
     */
    public void toggleShoppingItemPurchased(Long itemId) {
        ShoppingListItem item = repo.fetch(Table.SHOPPING_LIST_ITEMS, itemId);
        if (item != null) {
            item.togglePurchased();
            repo.write(item);
            notifyListener();
        }
    }

    // ============== SHOPPING HELPER ==============

    private String getWeekKey(LocalDate weekStart) {
        WeekFields weekFields = WeekFields.of(Locale.GERMANY);
        int week = weekStart.get(weekFields.weekOfWeekBasedYear());
        int year = weekStart.get(weekFields.weekBasedYear());
        return String.format("%d-W%02d", year, week);
    }

    private void deleteShoppingListForWeek(String weekKey) {
        List<ShoppingListItem> items = repo.fetchAll(Table.SHOPPING_LIST_ITEMS);
        for (ShoppingListItem item : items) {
            if (weekKey.equals(item.weekKey)) {
                repo.delete(Table.SHOPPING_LIST_ITEMS, item.id);
            }
        }
    }

    private StorePackage findBestPackage(Long ingredientId, String preferredStore, String unit) {
        List<StorePackage> packages = repo.fetchAll(Table.STORE_PACKAGES);

        // Erst im bevorzugten Laden suchen
        for (StorePackage pkg : packages) {
            if (ingredientId.equals(pkg.ingredientId) &&
                (unit == null || unit.equals(pkg.unit)) &&
                preferredStore != null && preferredStore.equals(pkg.storeName)) {
                return pkg;
            }
        }

        // Sonst irgendeine passende Packung
        for (StorePackage pkg : packages) {
            if (ingredientId.equals(pkg.ingredientId) && (unit == null || unit.equals(pkg.unit))) {
                return pkg;
            }
        }

        return null;
    }

    private int estimatePrice(Long ingredientId, double amount, String store) {
        if (ingredientId == null) return 0;

        StorePackage pkg = findBestPackage(ingredientId, store, null);
        if (pkg == null || pkg.priceCents <= 0) return 0;

        int packagesNeeded = pkg.calculatePackagesNeeded(amount);
        return packagesNeeded * pkg.priceCents;
    }

    private void updateStorePackageLastPurchased(Long ingredientId, String store, LocalDate date) {
        List<StorePackage> packages = repo.fetchAll(Table.STORE_PACKAGES);
        for (StorePackage pkg : packages) {
            if (ingredientId.equals(pkg.ingredientId) && store.equals(pkg.storeName)) {
                pkg.lastPurchased = date;
                repo.write(pkg);
            }
        }
    }

    private void createShoppingTransaction(Long accountId, int amountCents, String store, String weekKey) {
        // Lebensmittel-Kategorie finden
        Long categoryId = null;
        List<Category> categories = repo.fetchAll(Table.CATEGORIES);
        for (Category cat : categories) {
            if ("Lebensmittel".equals(cat.name) && !cat.isIncome) {
                categoryId = cat.id;
                break;
            }
        }

        Transaction tx = new Transaction.Builder(accountId, -amountCents, LocalDate.now(), categoryId)
            .description("Wocheneinkauf " + weekKey)
            .payee(store)
            .build();

        repo.write(tx);

        // Kontostand aktualisieren
        Account account = repo.fetch(Table.ACCOUNTS, accountId);
        if (account != null) {
            account.currentBalanceCents += tx.amountCents;
            repo.write(account);
        }
    }

    private String getFoodGroupIcon(Ingredient.FoodGroup group) {
        return switch (group) {
            case GRAIN -> "🌾";
            case POTATO -> "🥔";
            case VEGETABLE -> "🥬";
            case FRUIT -> "🍎";
            case DAIRY -> "🥛";
            case MEAT -> "🥩";
            case FISH -> "🐟";
            case EGG -> "🥚";
            case FAT -> "🧈";
            case LEGUME -> "🫘";
            case NUT -> "🥜";
            case OTHER -> "📦";
        };
    }

    private String formatCents(int cents) {
        return String.format(Locale.GERMANY, "%.2f EUR", cents / 100.0);
    }

    // ============================================================================
    // HELPER
    // ============================================================================

    /**
     * Lädt eine einzelne Zutat per ID.
     */
    public Ingredient getIngredient(Long id) {
        return repo.fetch(Table.INGREDIENTS, id);
    }

    /**
     * Lädt ein einzelnes Rezept per ID.
     */
    public Recipe getRecipe(Long id) {
        return repo.fetch(Table.RECIPES, id);
    }
}
