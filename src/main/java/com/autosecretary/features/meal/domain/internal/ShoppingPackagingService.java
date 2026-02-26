package com.autosecretary.features.meal.domain.internal;

import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.features.meal.domain.ShoppingListItem;

/**
 * Rundet benoetigte Mengen auf Packungsgroessen und trackt Ueberschuss.
 */
public class ShoppingPackagingService {

    public PackagingResult roundToPackage(double neededAmount, int packageAmount) {
        if (neededAmount <= 0 || packageAmount <= 0) {
            return new PackagingResult(Math.max(0.0, neededAmount), 0.0, 0);
        }
        int packageCount = (int) Math.ceil(neededAmount / packageAmount);
        double roundedAmount = packageCount * packageAmount;
        return new PackagingResult(roundedAmount, Math.max(0.0, roundedAmount - neededAmount), packageCount);
    }

    public ShoppingListItem createShoppingItem(Ingredient ingredient,
                                               double neededAmount,
                                               String periodKey) {
        int packageAmount = resolvePackageAmount(ingredient);
        PackagingResult result = roundToPackage(neededAmount, packageAmount);
        ShoppingListItem item = new ShoppingListItem.Builder(
                ingredient.id == null ? 0L : ingredient.id,
                ingredient.name,
                Math.max(0.0, neededAmount),
                ingredient.defaultUnit
        )
                .excess(result.excessAmount())
                .periodKey(periodKey)
                .foodGroup(ingredient.foodGroup == null ? null : ingredient.foodGroup.label)
                .build();
        return item;
    }

    private int resolvePackageAmount(Ingredient ingredient) {
        if (ingredient == null) {
            return 0;
        }
        if (ingredient.storePackages != null && !ingredient.storePackages.isEmpty()) {
            Ingredient.StorePackage first = ingredient.storePackages.get(0);
            if (first != null && first.packageAmount > 0) {
                return first.packageAmount;
            }
        }
        return Math.max(0, ingredient.gramsPerUnit);
    }

    public record PackagingResult(double roundedAmount, double excessAmount, int packageCount) {}
}
