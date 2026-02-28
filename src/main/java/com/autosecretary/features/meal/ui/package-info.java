/**
 * Meal planner UI layer (presentation).
 *
 * <p>Contains the primary meal planner fragment ({@link MealPlannerFragment}) which presents
 * the meal feature to the user. The fragment manages three tabs:
 * <ul>
 *   <li><strong>Week Plan:</strong> view and manage meal recipes scheduled for the coming week
 *   <li><strong>Recipes:</strong> browse available recipes and view recipe details
 *   <li><strong>Stock & Shopping:</strong> manage pantry inventory and shopping list
 * </ul>
 *
 * <p><strong>Architecture:</strong> The fragment uses a presenter pattern to delegate all business
 * logic to the application layer. See {@link com.autosecretary.features.meal.application.MealPlannerPresenter}
 * for details on what the presenter does. The fragment is purely presentational: it inflates layouts,
 * builds dialogs, handles user input, and calls presenter methods to update data.
 *
 * <p><strong>Entry point:</strong> {@link MealPlannerFragment} is the primary entry point. It's
 * instantiated by the app's main activity and displayed in the bottom navigation's "Mahlzeiten" tab.
 */
package com.autosecretary.features.meal.ui;
