package repository;

import entities.TrackedItem;
import entities.TodoList;
import entities.Account;
import entities.Transaction;
import entities.BudgetLimit;
import entities.Import;
import entities.Category;
import entities.HouseholdMember;
import entities.CookingPreferences;
import entities.RecipeRating;
import entities.Ingredient;
import entities.Recipe;
import entities.MealPlan;
import entities.MealSchedule;
import entities.ShoppingListItem;
import entities.PantryItem;
import entities.ConsumptionLog;
import entities.WeeklyFoodTarget;
import entities.StorePackage;

/**
 * Typisierte Tabellen-Referenz für typsichere fetch()-Aufrufe.
 *
 * Verwendung:
 *   TrackedItem item = repo.fetch(Table.ITEMS, 5);
 *   TodoList list = repo.fetch(Table.TODOS, Map.of("date", "2026-01-23"));
 */
public class Table<T> {

    public static final Table<TrackedItem> ITEMS = new Table<>("items");
    public static final Table<TodoList> TODOS = new Table<>("todos");

    // Budget-Tabellen
    public static final Table<Account> ACCOUNTS = new Table<>("accounts");
    public static final Table<Transaction> TRANSACTIONS = new Table<>("transactions");
    public static final Table<BudgetLimit> BUDGET_LIMITS = new Table<>("budget_limits");
    public static final Table<Import> IMPORTS = new Table<>("imports");
    public static final Table<Category> CATEGORIES = new Table<>("categories");

    // Meal-Planning-Tabellen
    public static final Table<HouseholdMember> HOUSEHOLD_MEMBERS = new Table<>("household_members");
    public static final Table<CookingPreferences> COOKING_PREFERENCES = new Table<>("cooking_preferences");
    public static final Table<RecipeRating> RECIPE_RATINGS = new Table<>("recipe_ratings");
    public static final Table<Ingredient> INGREDIENTS = new Table<>("ingredients");
    public static final Table<Recipe> RECIPES = new Table<>("recipes");
    public static final Table<MealPlan> MEAL_PLANS = new Table<>("meal_plans");
    public static final Table<MealSchedule> MEAL_SCHEDULES = new Table<>("meal_schedules");
    public static final Table<ShoppingListItem> SHOPPING_LIST_ITEMS = new Table<>("shopping_list_items");
    public static final Table<PantryItem> PANTRY_ITEMS = new Table<>("pantry_items");
    public static final Table<ConsumptionLog> CONSUMPTION_LOGS = new Table<>("consumption_logs");
    public static final Table<WeeklyFoodTarget> WEEKLY_FOOD_TARGETS = new Table<>("weekly_food_targets");
    public static final Table<StorePackage> STORE_PACKAGES = new Table<>("store_packages");

    private final String name;

    private Table(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }
}
