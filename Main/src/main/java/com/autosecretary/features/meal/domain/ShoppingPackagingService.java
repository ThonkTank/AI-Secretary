package com.autosecretary.features.meal.domain;


/**
 * Stateless service that rounds shopping amounts up to the nearest full package size,
 * tracking any excess that results from buying whole packages.
 *
 * <p>Example: if you need 450 g of pasta and the smallest available package is 500 g,
 * {@link #roundToPackage} returns: {@code roundedAmount=500, excessAmount=50, packageCount=1}.
 * The excess is stored on {@link ShoppingListItem} so the UI can show "(+50 g excess)".
 *
 * <p>Usage: call static methods directly — do not instantiate this class.
 *
 * <p>See also {@link #createShoppingItem} for the higher-level factory that combines
 * packaging rounding with {@link ShoppingListItem} construction.
 */
public class ShoppingPackagingService {

    /**
     * Rounds {@code neededAmount} up to the nearest multiple of {@code packageAmount}.
     *
     * <p>If either argument is {@code <= 0}, no rounding is applied: the result is
     * {@code roundedAmount = max(0, neededAmount)}, {@code excessAmount = 0}, {@code packageCount = 0}.
     *
     * @param neededAmount  actual amount required (in whatever unit the ingredient uses)
     * @param packageAmount standard package size in the same unit (e.g. 500 for a 500 g bag)
     * @return a {@link PackagingResult} with the rounded amount, excess, and number of packages
     */
    public static PackagingResult roundToPackage(double neededAmount, int packageAmount) {
        if (neededAmount <= 0 || packageAmount <= 0) {
            return new PackagingResult(Math.max(0.0, neededAmount), 0.0, 0);
        }
        int packageCount = (int) Math.ceil(neededAmount / packageAmount);
        double roundedAmount = packageCount * packageAmount;
        return new PackagingResult(roundedAmount, Math.max(0.0, roundedAmount - neededAmount), packageCount);
    }

    /**
     * Creates a {@link ShoppingListItem} for an ingredient, automatically applying package-size
     * rounding via {@link #roundToPackage}.
     *
     * <p>The package size is resolved from {@code ingredient.storePackages} (first entry with a
     * positive {@code packageAmount}), falling back to {@code ingredient.gramsPerUnit}. If no
     * valid package size can be determined, no rounding is applied.
     *
     * @param ingredient   the ingredient to shop for (must not be null)
     * @param neededAmount the raw amount needed (before rounding); negative values are treated as 0
     * @param periodKey    ISO date string identifying the shopping day (e.g. {@code "2026-02-14"})
     * @return a new {@link ShoppingListItem} with the rounded amount and excess filled in
     */
    public static ShoppingListItem createShoppingItem(Ingredient ingredient,
                                                      double neededAmount,
                                                      String periodKey) {
        int packageAmount = resolvePackageAmount(ingredient);
        PackagingResult result = roundToPackage(neededAmount, packageAmount);
        return new ShoppingListItem.Builder(
                ingredient.id,
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
        if (ingredient.storePackages != null && !ingredient.storePackages.isEmpty()) {
            // Prefer a package whose unit matches the ingredient's default unit so that the
            // rounding is meaningful (e.g. don't round 450 g against a "1 piece" package).
            for (Ingredient.StorePackage pkg : ingredient.storePackages) {
                if (ingredient.defaultUnit != null && ingredient.defaultUnit.equals(pkg.unit)
                        && pkg.packageAmount > 0) {
                    return pkg.packageAmount;
                }
            }
            // Fall back to first package with a valid size, regardless of unit.
            int packageAmount = ingredient.storePackages.get(0).packageAmount;
            if (packageAmount > 0) {
                return packageAmount;
            }
        }
        return Math.max(0, ingredient.gramsPerUnit);
    }

    /**
     * Immutable result of a package-rounding calculation.
     *
     * @param roundedAmount amount to buy (rounded up to nearest full package)
     * @param excessAmount  leftover after buying full packages ({@code roundedAmount - neededAmount})
     * @param packageCount  number of packages to buy (0 when no rounding was applied)
     */
    public record PackagingResult(double roundedAmount, double excessAmount, int packageCount) {}

    private ShoppingPackagingService() {}
}
