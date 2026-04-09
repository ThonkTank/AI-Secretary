# Review Backlog — app/

## Open (deferred — tradeoffs favour current approach)

### [consider] `BackgroundTask` / `FileProducingTask` private functional interfaces in SettingsController
**File:** `settings/SettingsController.java:51–72`
**Why complex:** Two private `@FunctionalInterface` declarations add ~25 lines of ceremony for what are effectively `BooleanSupplier` and `Supplier<File>` from `java.util.function`.
**Simpler alternative:** Replace both with standard library types; remove the interface declarations entirely.
**Tradeoff:** The named types (`BackgroundTask`, `FileProducingTask`) are more semantically descriptive than `BooleanSupplier`/`Supplier<File>`, and the Javadoc on each explains the contract clearly. Marginal improvement; semantic clarity favours keeping them.

### [consider] `OPTION_*` integer constants must mirror array order in SettingsController
**File:** `settings/SettingsController.java:38–41, 153–157`
**Why complex:** Four `private static final int` constants (0–3) must stay in sync with the string array in `showSettingsMenu()`. A silent mismatch would dispatch to the wrong handler.
**Simpler alternative:** Drop the constants; use inline indices or an `enum`. Or restructure as a list of (label, action) pairs.
**Tradeoff:** Named constants do clarify intent in the `switch`. Low risk of divergence given the file is small and self-contained.
