package activities.inApp.ernaehrungTab;

/**
 * Callback-Interface fuer Sub-Tab → Parent Benachrichtigungen.
 * Wird von Sub-Tabs (WeekPlanTab, RecipesTab, etc.) verwendet,
 * um MealPlanView ueber Datenaenderungen zu informieren.
 */
@FunctionalInterface
public interface MealTabListener {
    void onDataChanged();
}
