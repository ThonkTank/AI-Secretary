package com.autosecretary.features.meal.domain.internal;

import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.features.meal.domain.ShoppingListItem;

import java.util.Objects;

/**
 * Rundet benoetigte Mengen auf Packungsgroessen und trackt Ueberschuss.
 */
public class ShoppingPackagingService {

    public static PackagingResult roundToPackage(double neededAmount, int packageAmount) {
        if (neededAmount <= 0 || packageAmount <= 0) {
            return new PackagingResult(Math.max(0.0, neededAmount), 0.0, 0);
        }
        int packageCount = (int) Math.ceil(neededAmount / packageAmount);
        double roundedAmount = packageCount * packageAmount;
        return new PackagingResult(roundedAmount, Math.max(0.0, roundedAmount - neededAmount), packageCount);
    }

    public static ShoppingListItem createShoppingItem(Ingredient ingredient,
                                                      double neededAmount,
                                                      String periodKey) {
        int packageAmount = resolvePackageAmount(ingredient);
        PackagingResult result = roundToPackage(neededAmount, packageAmount);
        return new ShoppingListItem.Builder(
                Objects.requireNonNullElse(ingredient.id, 0L),
                ingredient.name,
                Math.max(0.0, neededAmount),
                ingredient.defaultUnit
        )
                .excess(result.excessAmount())
                .periodKey(periodKey)
                .foodGroup(ingredient.foodGroup == null ? null : ingredient.foodGroup.label)
                .build();
    }

    private static int resolvePackageAmount(Ingredient ingredient) {
        if (ingredient == null) {
            return 0;
        }
        if (ingredient.storePackages != null && !ingredient.storePackages.isEmpty()) {
            int packageAmount = ingredient.storePackages.get(0).packageAmount;
            if (packageAmount > 0) {
                return packageAmount;
            }
        }
        return Math.max(0, ingredient.gramsPerUnit);
    }

    public record PackagingResult(double roundedAmount, double excessAmount, int packageCount) {}

    private ShoppingPackagingService() {}
}
