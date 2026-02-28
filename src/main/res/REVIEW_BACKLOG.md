# Review Backlog — res/

## Open Issues

### [friction] Meal layouts use hardcoded dp/sp values and inline hex colors throughout (layout/)
- **Path:** All `layout/meal_*.xml` files
- **What a novice misunderstands:** Budget and task layouts consistently use `@dimen`/`@color`/`@style` tokens. Meal layouts use raw values (`12dp`, `24sp`, `#22FFFFFF`). A newcomer will not know which pattern to follow when adding new layouts.
- **Recommended fix:** Extract shared values to `@dimen` and `@color` resources to match budget/task convention.
- **Why:** Reduces confusion about which convention is "correct."
- **Note:** The layout README (at `res/layout-README.md`) documents this as a known quality-tier difference.
