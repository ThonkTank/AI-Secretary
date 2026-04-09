# Review Backlog — meal/ui

## Open Issues (Deferred / Lower Priority)

### [responsive] Tab button layout may be cramped on very narrow screens
- **File**: `src/main/res/layout/meal_overview_fragment.xml` (lines 24-49)
- **Issue**: Three tab buttons with `layout_weight="1"` equally divide the screen width. On very narrow phones (e.g., 280dp), button text may wrap or overflow beyond button boundaries.
- **Why it hurts design**: Wrapped text looks amateurish; very narrow buttons reduce visual comfort.
- **Recommended change**: For extreme cases (< 300dp width):
  - Create a layout variant (e.g., `layout-sw300dp/`) with scrollable tab layout or stacked vertical buttons
  - Or migrate to Material Design `TabLayout` widget for responsive tab behavior
  - Current minHeight (48dp) handles normal/large screens well
- **Tradeoffs**: Low priority — affects only devices narrower than ~300dp (rare). Requires layout variants or widget migration.
- **Note**: Tab width at 280dp with 3 buttons ~ 93dp per button, acceptable for typical tab text length. Deferred pending real-world testing.

### [friction] Recipe detail pane discovery not obvious
- **File**: `src/main/res/layout/meal_overview_fragment.xml` (lines 92-113), `MealPlannerFragment.java` (lines 199-208)
- **What users struggle with**: The recipe detail pane shows placeholder text "Rezept wahlen, um Details anzuzeigen" (Choose recipe to show details), but the interaction pattern isn't visually obvious. First-time users may not discover that clicking a recipe button updates the detail pane without trial-and-error.
- **Why it hurts discoverability**: The relationship between recipe list and detail pane is implicit, not explicit. Users need to guess or discover the interaction.
- **Recommended change**: Add a subtle visual affordance to guide user attention:
  - Highlight/scroll to first recipe button on tab switch to draw attention
  - Or add a small instruction label or icon between the recipe list and detail pane
  - Or use subtle animation when recipe button is clicked to confirm interaction worked
- **Tradeoffs**: Adds slight layout or animation complexity. Low priority if UX testing shows users discover this quickly through exploration.

### [platform] Tab buttons use manual color management instead of Material state colors
- **File**: `MealPlannerFragment.java` (lines 155-160)
- **Issue**: Tab active/inactive state is set via `setTextColor()` in Java, bypassing Material component state management. The task feature uses `MaterialButtonToggleGroup` for equivalent tab switching. Manual color management means no pressed/focused state styling and no theme responsiveness.
- **Why it hurts design**: Tab buttons lack the standard Material pressed/focused visual feedback that other controls in the app provide. Active tab state is text-color-only with no background or indicator change.
- **Recommended change**: Replace the three `MaterialButton` tabs with a `MaterialButtonToggleGroup` (as used in `task_list_fragment.xml`) and remove the manual `setTextColor()` calls.
- **Tradeoffs**: Moderate refactor touching both XML layout and Java fragment. Functional behavior is correct as-is. Deferred in favor of smaller consistency fixes.
