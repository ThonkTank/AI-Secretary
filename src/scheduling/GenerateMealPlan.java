package scheduling;

import static activities.generic.DateTimeHelper.getWeekKey;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import controller.MealManager;
import entities.CalendarEvent;
import entities.CookingPreferences;
import entities.HouseholdMember;
import entities.Ingredient;
import entities.MealPlan;
import entities.MealSchedule;
import entities.MealType;
import entities.PantryItem;
import entities.Recipe;
import entities.RecipeRating;
import entities.WeeklyFoodTarget;
import entities.TrackedItem;
import repository.Repo;
import repository.Table;

/**
 * Automatischer Wochenplan-Generator für Meal Planning.
 *
 * 5-Schritte-Algorithmus:
 * 1. calculateWeeklyTarget() - DGE-basierte Nährwertbedürfnisse
 * 2. calculateMealCalories() - TDEE-basierte Kalorienverteilung
 * 3. planCookingSessions() - Koch-Sessions basierend auf Präferenzen
 * 4. scoreRecipe() + selectBestRecipe() - Rezeptauswahl nach 7 Kriterien
 * 5. generateWeekPlan() - Hauptfunktion mit Task-Erstellung
 */
public class GenerateMealPlan {

    /** Functional Interface für Kalender-Zugriff (analog zu buildToDo). */
    @FunctionalInterface
    public interface CalendarProvider {
        List<CalendarEvent> getEventsForDay(LocalDate day);
    }

    private final Repo repo;
    private final MealManager mealMgr;
    private final CalendarProvider calendar;

    // Cache für Ingredient-Lookups
    private Map<Long, Ingredient> ingredientCache;

    public GenerateMealPlan(Repo repo, MealManager mealMgr, CalendarProvider calendar) {
        this.repo = repo;
        this.mealMgr = mealMgr;
        this.calendar = calendar;
    }

    // ============== RECORDS ==============

    /**
     * Repräsentiert eine geplante Koch-Session.
     */
    record CookingSession(
        LocalDate cookingDate,
        MealType mealType,
        List<LocalDate> coversDates,  // Tage die durch dieses Kochen abgedeckt werden
        boolean allowsElaborateCooking
    ) {}

    /**
     * Ergebnis der Skalierbarkeits-Prüfung.
     */
    record ScalabilityResult(
        boolean isValid,
        double score,
        String reason
    ) {}

    // ============== SCHRITT 1: WEEKLY TARGET ==============

    /**
     * Berechnet Wochenbedarf pro FoodGroup, berücksichtigt Überschüsse/Defizite.
     */
    public WeeklyFoodTarget calculateWeeklyTarget(LocalDate weekStart) {
        List<HouseholdMember> members = getActiveMembers();
        String weekKey = getWeekKey(weekStart);
        WeeklyFoodTarget base = WeeklyFoodTarget.calculate(weekKey, members);

        // Letzte Woche laden
        String lastWeekKey = getWeekKey(weekStart.minusWeeks(1));
        WeeklyFoodTarget lastWeek = repo.fetch(Table.WEEKLY_FOOD_TARGETS,
            Map.of("week_key", lastWeekKey));

        if (lastWeek != null) {
            // Defizit addieren, Überschuss abziehen (max 30% Korrektur)
            for (Ingredient.FoodGroup group : Ingredient.FoodGroup.values()) {
                if (group == Ingredient.FoodGroup.OTHER) continue;

                int planned = lastWeek.getPlannedFor(group);
                int target = base.getTargetFor(group);
                if (target == 0) continue;

                int delta = planned - target;
                int maxCorrection = target / 3;
                int correction = Math.max(-maxCorrection, Math.min(maxCorrection, -delta));
                adjustTarget(base, group, correction);
            }
        }

        return base;
    }

    // ============== SCHRITT 2: MEAL CALORIES ==============

    /**
     * TDEE-basierte Kalorienverteilung pro Mahlzeit.
     */
    public int calculateMealCalories(DayOfWeek day, MealType mealType,
                                      List<HouseholdMember> members) {
        int householdTDEE = 0;
        for (HouseholdMember m : members) {
            if (m.isActive) {
                householdTDEE += m.calculateTDEE();
            }
        }

        double share = switch(mealType) {
            case BREAKFAST -> 0.20;
            case LUNCH -> 0.35;
            case DINNER -> 0.35;
            case SNACK -> 0.10;
        };

        return (int)(householdTDEE * share);
    }

    // ============== SCHRITT 3: COOKING SESSIONS ==============

    /**
     * Plant Koch-Sessions basierend auf Präferenzen und Kalender.
     */
    public List<CookingSession> planCookingSessions(MealType mealType,
                                                     int maxCookingPerWeek,
                                                     LocalDate weekStart) {
        CookingPreferences prefs = loadCookingPreferences();
        List<CookingSession> sessions = new ArrayList<>();

        // Kalender-Events für die Woche laden
        Map<LocalDate, List<CalendarEvent>> events = loadCalendarEvents(weekStart);

        // Verfügbare Koch-Tage ermitteln (keine ganztägigen Events)
        List<LocalDate> availableDays = new ArrayList<>();

        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            boolean canCook = prefs == null || prefs.canCookOn(date.getDayOfWeek(), mealType);

            if (canCook && !hasBlockingEvent(events.get(date))) {
                availableDays.add(date);
            }
        }

        if (availableDays.isEmpty()) {
            // Fallback: Alle Tage erlauben
            for (int i = 0; i < 7; i++) {
                availableDays.add(weekStart.plusDays(i));
            }
        }

        // Sessions gleichmäßig über die Woche verteilen
        int sessionsNeeded = Math.min(maxCookingPerWeek, availableDays.size());
        if (sessionsNeeded == 0) sessionsNeeded = 1;

        int daysPerSession = 7 / sessionsNeeded;
        int remainder = 7 % sessionsNeeded;

        int dayIndex = 0;
        for (int s = 0; s < sessionsNeeded; s++) {
            // Wie viele Tage deckt diese Session ab?
            int coverage = daysPerSession + (s < remainder ? 1 : 0);

            // Koch-Tag ist der erste verfügbare Tag in diesem Segment
            LocalDate cookingDate = findBestCookingDay(availableDays, weekStart, dayIndex, dayIndex + coverage);

            List<LocalDate> coversDates = new ArrayList<>();
            for (int d = 0; d < coverage && dayIndex + d < 7; d++) {
                coversDates.add(weekStart.plusDays(dayIndex + d));
            }

            // Aufwendiges Kochen erlaubt? (Nicht an Werktagen mit Events)
            boolean allowsElaborate = isWeekend(cookingDate) ||
                !hasAfternoonEvent(events.get(cookingDate));

            sessions.add(new CookingSession(cookingDate, mealType, coversDates, allowsElaborate));
            dayIndex += coverage;
        }

        return sessions;
    }

    // ============== SCHRITT 4a: RECIPE SCORING ==============

    /**
     * Bewertet Rezept nach 7 gewichteten Kriterien.
     */
    public double scoreRecipe(Recipe recipe, Map<Ingredient.FoodGroup, Integer> need,
                              List<PantryItem> pantry, Map<Long, Double> committed,
                              CookingSession session, List<HouseholdMember> members) {
        double score = 0;

        if (recipe.ingredients == null || recipe.ingredients.isEmpty()) {
            return 0;  // Rezepte ohne Zutaten nicht bewerten
        }

        // A) Nahrungsgruppen-Bedürfnisse (Gewicht: 10x)
        for (Recipe.RecipeIngredient ing : recipe.ingredients) {
            Ingredient ingredient = getIngredient(ing.ingredientId());
            if (ingredient == null) continue;

            int grams = ingredient.getFoodGroupGrams(ing.amount(), ing.unit());
            Ingredient.FoodGroup group = ingredient.foodGroup;
            int remaining = need.getOrDefault(group, 0);

            if (remaining > 0) {
                score += Math.min(grams, remaining) * 10;
            } else {
                score -= grams * 0.5;  // Malus für Übererfüllung
            }
        }

        // B) Skalierbarkeit (Gewicht: 5x)
        ScalabilityResult scalability = checkScalability(recipe, session, committed);
        if (!scalability.isValid()) return -1;
        score += scalability.score() * 5;

        // C) Variety — Abwechslung (Multiplikator)
        if (recipe.lastUsed != null) {
            long daysSince = ChronoUnit.DAYS.between(recipe.lastUsed, LocalDate.now());
            if (daysSince < 7) score *= 0.1;
            else if (daysSince < 14) score *= 0.5;
            else if (daysSince < 21) score *= 0.8;
        }

        // D) Aufwand passend (Multiplikator)
        if (session.allowsElaborateCooking() &&
            recipe.prepEffort == Recipe.PrepEffort.SIGNIFICANT) {
            score *= 1.2;
        } else if (!session.allowsElaborateCooking() &&
                   recipe.prepEffort == Recipe.PrepEffort.SIGNIFICANT) {
            score *= 0.3;
        }

        // E) Bewertungen aller Mitglieder (Gewicht: 3x)
        double avgRating = getAverageRating(recipe.id, members);
        double minRating = getMinRating(recipe.id, members);
        score += (avgRating * 0.7 + minRating * 0.3) * 3;

        // F) Verderbliche Pantry-Items aufbrauchen (Bonus)
        for (PantryItem item : pantry) {
            if (recipeUsesIngredient(recipe, item.ingredientId)) {
                if (item.expiryDate != null &&
                    item.expiryDate.isBefore(LocalDate.now().plusDays(3))) {
                    score += 50;  // Großer Bonus für bald ablaufende Items
                }
            }
        }

        // G) Verderbliche Zutaten bis Wochenende (Malus für späte Woche)
        if (session.cookingDate().getDayOfWeek().getValue() >= 5) {  // Fr-So
            for (Recipe.RecipeIngredient ing : recipe.ingredients) {
                Ingredient ingredient = getIngredient(ing.ingredientId());
                if (ingredient != null && ingredient.isPerishable) {
                    score -= 10;  // Leichter Malus
                }
            }
        }

        return score;
    }

    // ============== SCHRITT 4b: SCALABILITY CHECK ==============

    /**
     * Prüft ob Rezept für die Session skalierbar ist.
     */
    public ScalabilityResult checkScalability(Recipe recipe, CookingSession session,
                                               Map<Long, Double> committed) {
        int householdSize = getActiveMembers().size();
        if (householdSize == 0) householdSize = 1;

        int neededServings = session.coversDates().size() * householdSize;

        // Min/Max Servings prüfen
        if (neededServings < recipe.minServings) {
            return new ScalabilityResult(false, 0, "Zu wenig Portionen");
        }
        if (neededServings > recipe.maxServings) {
            return new ScalabilityResult(false, 0, "Zu viele Portionen");
        }

        // Skalierungsfaktor berechnen
        double scaleFactor = (double) neededServings / recipe.servings;

        if (recipe.scalingPrecision == Recipe.ScalingPrecision.EXACT) {
            // Bei EXACT: Nur ganzzahlige Skalierung erlaubt
            if (Math.abs(scaleFactor - Math.round(scaleFactor)) > 0.01) {
                return new ScalabilityResult(false, 0, "Ungerade Skalierung bei EXACT");
            }
        }

        // WholeUnit-Zutaten prüfen
        double wasteScore = 0;
        if (recipe.ingredients != null) {
            for (Recipe.RecipeIngredient ing : recipe.ingredients) {
                Ingredient ingredient = getIngredient(ing.ingredientId());
                if (ingredient != null && ingredient.isWholeUnit) {
                    double scaled = ing.amount() * scaleFactor;
                    double alreadyCommitted = committed.getOrDefault(ing.ingredientId(), 0.0);
                    double total = scaled + alreadyCommitted;
                    double waste = total - Math.floor(total);
                    wasteScore += waste;
                }
            }
        }

        // Score: weniger Verschwendung = besser
        double score = 100 - (wasteScore * 20);
        return new ScalabilityResult(true, Math.max(0, score), null);
    }

    // ============== SCHRITT 5: HAUPTFUNKTION ==============

    /**
     * Hauptfunktion: Generiert kompletten Wochenplan + Tasks.
     */
    public void generateWeekPlan(LocalDate weekStart) {
        // Cache initialisieren
        ingredientCache = new HashMap<>();

        // === SCHRITT 1: Nährwertbedürfnisse ===
        WeeklyFoodTarget target = calculateWeeklyTarget(weekStart);
        Map<Ingredient.FoodGroup, Integer> remainingNeed = target.toRemainingMap();

        // === SCHRITT 2: Kalorien pro Mahlzeit ===
        List<HouseholdMember> members = getActiveMembers();

        // === SCHRITT 3: Koch-Sessions planen ===
        CookingPreferences prefs = loadCookingPreferences();

        Map<MealType, List<CookingSession>> sessions = new EnumMap<>(MealType.class);
        for (MealType type : MealType.values()) {
            if (type == MealType.SNACK) {
                // Snacks werden nicht gekocht
                sessions.put(type, List.of());
            } else {
                int maxCooking = prefs != null ? prefs.getMaxCookingFor(type) : 3;
                sessions.put(type, planCookingSessions(type, maxCooking, weekStart));
            }
        }

        // === SCHRITT 4: Rezepte auswählen ===
        List<MealPlan> allMeals = new ArrayList<>();
        List<Recipe> recipes = loadAllRecipes();
        List<PantryItem> pantry = loadPantry();
        Map<Long, Double> committedIngredients = new HashMap<>();

        for (MealType type : MealType.values()) {
            if (type == MealType.SNACK) continue;

            for (CookingSession session : sessions.get(type)) {
                Recipe best = selectBestRecipe(recipes, remainingNeed, pantry,
                                               committedIngredients, session, members);
                if (best == null) continue;

                int servings = calculateServings(best, session, members.size());

                // MealPlan für jeden Tag der Session
                for (LocalDate date : session.coversDates()) {
                    MealPlan meal = new MealPlan();
                    meal.date = date;
                    meal.mealType = type;
                    meal.recipeId = best.id;
                    meal.recipeTitle = best.title;

                    int dailyServings = servings / session.coversDates().size();
                    meal.plannedServings = dailyServings > 0 ? dailyServings : 1;
                    meal.estimatedCalories = best.totalCalories / best.servings * meal.plannedServings;

                    allMeals.add(meal);
                    repo.write(meal);
                }

                // Tracking aktualisieren
                subtractFromNeed(remainingNeed, best, servings);
                trackCommittedIngredients(committedIngredients, best, servings);

                // Rezept als "verwendet" markieren
                best.lastUsed = session.cookingDate();
                best.usageCount = best.usageCount + 1;
                repo.write(best);
            }
        }

        // === SCHRITT 5: Tasks erstellen ===
        createMealTasks(allMeals);

        // WeeklyFoodTarget speichern mit aktualisierter Planned-Info
        target.weekKey = getWeekKey(weekStart);
        updatePlannedInTarget(target, remainingNeed);
        repo.write(target);

        // Listener benachrichtigen
        if (mealMgr != null) {
            mealMgr.notifyListeners();
        }

        // === SCHRITT 6: Einkaufsliste generieren ===
        if (mealMgr != null) {
            mealMgr.generateShoppingList(weekStart);
        }
    }

    /**
     * Wählt das beste Rezept basierend auf Score.
     */
    private Recipe selectBestRecipe(List<Recipe> recipes,
                                     Map<Ingredient.FoodGroup, Integer> need,
                                     List<PantryItem> pantry,
                                     Map<Long, Double> committed,
                                     CookingSession session,
                                     List<HouseholdMember> members) {
        Recipe best = null;
        double bestScore = -1;

        for (Recipe recipe : recipes) {
            // MealType-Filter
            if (recipe.mealType != null && recipe.mealType != session.mealType()) {
                continue;
            }

            double score = scoreRecipe(recipe, need, pantry, committed, session, members);
            if (score > bestScore) {
                bestScore = score;
                best = recipe;
            }
        }

        return best;
    }

    /**
     * Berechnet optimale Portionszahl für Session.
     */
    private int calculateServings(Recipe recipe, CookingSession session, int householdSize) {
        if (householdSize == 0) householdSize = 1;
        int needed = session.coversDates().size() * householdSize;

        // Auf Rezept-Grenzen beschränken
        int servings = Math.max(recipe.minServings,
                               Math.min(recipe.maxServings, needed));

        // Bei EXACT-Rezepten auf Vielfache der Basis-Portionen runden
        if (recipe.scalingPrecision == Recipe.ScalingPrecision.EXACT) {
            int baseServings = recipe.servings;
            servings = (int)(Math.round((double) servings / baseServings) * baseServings);
            servings = Math.max(baseServings, servings); // Mindestens Basis-Portionen
        }

        return servings;
    }

    // ============== TASK CREATION ==============

    /**
     * Erstellt trackedItems für alle Mahlzeiten.
     * Nutzt das bestehende fixedAppointment-System.
     */
    private void createMealTasks(List<MealPlan> meals) {
        for (MealPlan meal : meals) {
            List<MealSchedule> slots = getMealSchedules(meal.date.getDayOfWeek(), meal.mealType);
            if (slots.isEmpty()) continue;

            // Ersten verfuegbaren Slot nutzen
            MealSchedule schedule = slots.get(0);

            String icon = getMealIcon(meal.mealType);
            String title = icon + " " + meal.recipeTitle;

            // Task mit fixedAppointment erstellen
            TrackedItem task = new TrackedItem.Builder(
                    TrackedItem.ItemType.TASK,
                    title,
                    TrackedItem.Priority.CRITICAL)
                .noRepetition()
                .fixedAppointment(meal.date.toString(),
                                  schedule.scheduledTime.toString())
                .maxMinutes(schedule.durationMinutes)
                .mealPlan(meal.id)
                .build();

            repo.write(task);
        }
    }

    private String getMealIcon(MealType type) {
        return switch(type) {
            case BREAKFAST -> "🍳";
            case LUNCH -> "🍽️";
            case DINNER -> "🍲";
            case SNACK -> "🍎";
        };
    }

    // ============== HELPER METHODS ==============

    private List<HouseholdMember> getActiveMembers() {
        List<HouseholdMember> active = new ArrayList<>();
        for (HouseholdMember m : repo.fetchAll(Table.HOUSEHOLD_MEMBERS)) {
            if (m != null && m.isActive) {
                active.add(m);
            }
        }
        return active;
    }

    private Ingredient getIngredient(Long id) {
        if (id == null) return null;
        return ingredientCache.computeIfAbsent(id,
            k -> repo.fetch(Table.INGREDIENTS, k));
    }

    private CookingPreferences loadCookingPreferences() {
        return repo.fetch(Table.COOKING_PREFERENCES, 1L);
    }

    private List<Recipe> loadAllRecipes() {
        return repo.fetchAll(Table.RECIPES);
    }

    private List<PantryItem> loadPantry() {
        return repo.fetchAll(Table.PANTRY_ITEMS);
    }

    private List<MealSchedule> getMealSchedules(DayOfWeek day, MealType mealType) {
        List<MealSchedule> result = new ArrayList<>();
        List<MealSchedule> all = repo.fetchAll(Table.MEAL_SCHEDULES);
        for (MealSchedule ms : all) {
            if (ms.dayOfWeek == day && ms.mealType == mealType) {
                result.add(ms);
            }
        }
        MealSchedule.sortByTime(result);
        return result;
    }

    private Map<LocalDate, List<CalendarEvent>> loadCalendarEvents(LocalDate weekStart) {
        Map<LocalDate, List<CalendarEvent>> events = new HashMap<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            events.put(date, calendar.getEventsForDay(date));
        }
        return events;
    }

    private boolean hasBlockingEvent(List<CalendarEvent> events) {
        if (events == null) return false;
        return events.stream().anyMatch(e ->
            e.start().equals(LocalTime.MIN) &&
            (e.end().equals(LocalTime.MAX) || e.end().equals(LocalTime.of(23, 59))));
    }

    private boolean hasAfternoonEvent(List<CalendarEvent> events) {
        if (events == null) return false;
        LocalTime afternoon = LocalTime.of(14, 0);
        return events.stream().anyMatch(e ->
            e.start().isBefore(LocalTime.of(18, 0)) &&
            e.end().isAfter(afternoon));
    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }

    private LocalDate findBestCookingDay(List<LocalDate> available, LocalDate weekStart,
                                          int startIdx, int endIdx) {
        // Ersten verfügbaren Tag im Bereich finden
        for (LocalDate date : available) {
            int dayOfWeek = (int) ChronoUnit.DAYS.between(weekStart, date);
            if (dayOfWeek >= startIdx && dayOfWeek < endIdx) {
                return date;
            }
        }
        // Fallback: erster Tag im Segment
        return weekStart.plusDays(startIdx);
    }

    private double getAverageRating(Long recipeId, List<HouseholdMember> members) {
        if (members.isEmpty()) return 3.0;

        double sum = 0;
        int count = 0;
        for (HouseholdMember m : members) {
            RecipeRating rating = repo.fetch(Table.RECIPE_RATINGS,
                Map.of("recipe_id", String.valueOf(recipeId),
                       "member_id", String.valueOf(m.id)));
            if (rating != null) {
                sum += rating.rating;
                count++;
            }
        }
        return count > 0 ? sum / count : 3.0;
    }

    private double getMinRating(Long recipeId, List<HouseholdMember> members) {
        if (members.isEmpty()) return 3.0;

        double min = 5.0;
        boolean found = false;
        for (HouseholdMember m : members) {
            RecipeRating rating = repo.fetch(Table.RECIPE_RATINGS,
                Map.of("recipe_id", String.valueOf(recipeId),
                       "member_id", String.valueOf(m.id)));
            if (rating != null) {
                min = Math.min(min, rating.rating);
                found = true;
            }
        }
        return found ? min : 3.0;
    }

    private boolean recipeUsesIngredient(Recipe recipe, Long ingredientId) {
        if (recipe.ingredients == null) return false;
        return recipe.ingredients.stream()
            .anyMatch(ing -> ingredientId.equals(ing.ingredientId()));
    }

    // ============== TARGET/NEED HELPERS ==============

    private void adjustTarget(WeeklyFoodTarget target, Ingredient.FoodGroup group, int adjustment) {
        switch(group) {
            case GRAIN -> target.grainGrams += adjustment;
            case VEGETABLE -> target.vegetableGrams += adjustment;
            case FRUIT -> target.fruitGrams += adjustment;
            case DAIRY -> target.dairyGrams += adjustment;
            case MEAT -> target.meatGrams += adjustment;
            case FISH -> target.fishGrams += adjustment;
            case EGG -> target.eggGrams += adjustment;
            case FAT -> target.fatGrams += adjustment;
            case LEGUME -> target.legumeGrams += adjustment;
            case NUT -> target.nutGrams += adjustment;
            case POTATO -> target.potatoGrams += adjustment;
            case OTHER -> {} // Keine Anpassung für OTHER
        }
    }

    private void subtractFromNeed(Map<Ingredient.FoodGroup, Integer> need,
                                   Recipe recipe, int servings) {
        if (recipe.ingredients == null) return;

        double scaleFactor = (double) servings / recipe.servings;

        for (Recipe.RecipeIngredient ing : recipe.ingredients) {
            Ingredient ingredient = getIngredient(ing.ingredientId());
            if (ingredient == null) continue;

            int grams = (int)(ingredient.getFoodGroupGrams(ing.amount(), ing.unit()) * scaleFactor);
            Ingredient.FoodGroup group = ingredient.foodGroup;

            need.computeIfPresent(group, (k, v) -> Math.max(0, v - grams));
        }
    }

    private void trackCommittedIngredients(Map<Long, Double> committed,
                                            Recipe recipe, int servings) {
        if (recipe.ingredients == null) return;

        double scaleFactor = (double) servings / recipe.servings;

        for (Recipe.RecipeIngredient ing : recipe.ingredients) {
            double amount = ing.amount() * scaleFactor;
            committed.merge(ing.ingredientId(), amount, Double::sum);
        }
    }

    private void updatePlannedInTarget(WeeklyFoodTarget target,
                                        Map<Ingredient.FoodGroup, Integer> remaining) {
        // Geplant = Ziel - Verbleibend
        target.grainPlanned = target.grainGrams - remaining.getOrDefault(Ingredient.FoodGroup.GRAIN, 0);
        target.vegetablePlanned = target.vegetableGrams - remaining.getOrDefault(Ingredient.FoodGroup.VEGETABLE, 0);
        target.fruitPlanned = target.fruitGrams - remaining.getOrDefault(Ingredient.FoodGroup.FRUIT, 0);
        target.dairyPlanned = target.dairyGrams - remaining.getOrDefault(Ingredient.FoodGroup.DAIRY, 0);
        target.meatPlanned = target.meatGrams - remaining.getOrDefault(Ingredient.FoodGroup.MEAT, 0);
        target.fishPlanned = target.fishGrams - remaining.getOrDefault(Ingredient.FoodGroup.FISH, 0);
        target.eggPlanned = target.eggGrams - remaining.getOrDefault(Ingredient.FoodGroup.EGG, 0);
        target.fatPlanned = target.fatGrams - remaining.getOrDefault(Ingredient.FoodGroup.FAT, 0);
        target.legumePlanned = target.legumeGrams - remaining.getOrDefault(Ingredient.FoodGroup.LEGUME, 0);
        target.nutPlanned = target.nutGrams - remaining.getOrDefault(Ingredient.FoodGroup.NUT, 0);
        target.potatoPlanned = target.potatoGrams - remaining.getOrDefault(Ingredient.FoodGroup.POTATO, 0);
    }
}
