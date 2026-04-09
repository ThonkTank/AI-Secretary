# Meal UI – Developer Guide

## Architecture Overview

The meal UI is a single Android Fragment (`MealPlannerFragment`) that manages three tabs via visibility toggling (not fragment replacement):

- **Week Plan:** Displays scheduled meal plans; allows adding new plans, viewing details, and toggling completion status.
- **Recipes:** Shows available recipes in a list; clicking a recipe displays its full details (description + instructions) in a side panel.
- **Stock & Shopping:** Displays pantry inventory items and shopping list items.

The fragment follows the **presenter pattern**: all business logic is delegated to `MealPlannerPresenter` (in `com.autosecretary.features.meal.application`), which fetches data from the domain/data layers. The fragment is purely presentational.

Meal uses a direct presenter pattern without a ViewModel or `state/` sub-package because the feature's data flow is simpler than task/budget. There is no home-screen widget for the meal feature.

## Key Classes

- **`MealPlannerFragment`:** Entry point. Manages UI state, renders views, and handles user input (button clicks, dialog submissions).
- **`MealPlannerPresenter`:** Application layer; provides data and commands to the fragment. See `features/meal/application/` for details.
- **Domain classes:** `Recipe`, `MealPlan`, `MealType`, `PantryItem`, `ShoppingListItem` (in `features/meal/domain/`).

## How to Add a New Dialog

All dialog creation in this fragment follows a shared pattern. To add a new dialog (e.g., "edit recipe"):

1. **Create a layout resource** (e.g., `res/layout/meal_edit_recipe_dialog.xml`).
2. **Add a new private method** in `MealPlannerFragment`:
   ```java
   private void showEditRecipeDialog() {
       View content = LayoutInflater.from(requireContext())
           .inflate(R.layout.meal_edit_recipe_dialog, null);

       // Bind UI elements from the layout
       EditText titleField = content.findViewById(R.id.EditRecipeTitle);
       // ... other fields ...

       new AlertDialog.Builder(requireContext())
           .setTitle("Edit Recipe")
           .setView(content)
           .setNegativeButton(android.R.string.cancel, null)
           .setPositiveButton("Save", (dialog, which) -> {
               // Extract input values
               String newTitle = titleField.getText().toString();
               // ... other values ...

               // Call presenter to update data
               presenter.updateRecipe(recipeId, newTitle, /* ... */);

               // Re-render affected view
               renderRecipes();
           })
           .show();
   }
   ```

3. **Wire the button:** Add a button in the layout that calls your new method via `setOnClickListener()`.

For a concrete example, see `showPlanDialog()`, `showNeedDialog()`, or `showPantryDialog()`.

## Testing: Manual Verification Steps

1. Launch the app and open the **Meals** tab in the bottom navigation.
2. Under **Recipes**, select a recipe. (If the list is empty, a demo recipe is created automatically.)
3. Under **Week Plan**, tap **+ Plan Recipe** and fill in the date, meal type, and portion count.
4. Mark the new plan entry as done via **Done**, then reset it to **Open** again.
5. Under **Stock & Shopping**, tap **+ Add to Shopping List** to create a shopping list entry.
6. Under **Stock & Shopping**, tap **+ Add Pantry Entry** to create a pantry item with a shelf-life duration.
7. Verify that the expiry information on the pantry entry is calculated correctly based on `ShelfLifeService`.

## Building and Running Locally

To build and test the meal UI:

```bash
# Build the app
./gradlew assembleDebug

# Install to connected device/emulator
./gradlew installDebug
```

See the project's root README for general setup and build instructions.
