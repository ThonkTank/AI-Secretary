# Resource Values (res/values/)

String, color, dimension, and style definitions used throughout the app.

## File Inventory

| File | Scope | Contents |
|------|-------|----------|
| `strings.xml` | App-wide | App name, navigation labels, settings, update strings |
| `task_strings.xml` | Task feature | Edit dialogs, row actions, timer, deadline/streak labels, validation |
| `budget_strings.xml` | Budget feature | Transaction dialogs, import/export, recurring patterns, chart, widget |
| `meal_strings.xml` | Meal feature | Tabs, plan/recipe/stock labels, dialog titles, input hints |
| `colors.xml` | App-wide | Launcher icon colors, transparency constant |
| `task_colors.xml` | Task feature | Nature-inspired palette, Material tokens, urgency/streak/widget colors |
| `budget_colors.xml` | Budget feature | Semantic status colors, chart colors, widget colors |
| `dimens.xml` | App-wide | Spacing scale (xxs–xl) and text scale (xs–xl) |
| `task_dimens.xml` | Task feature | Editor, row, widget, corner radius dimensions |
| `budget_dimens.xml` | Budget feature | Card, divider, limit bar, chart dimensions |
| `styles.xml` | App-wide | AppTheme, component styles (buttons, inputs, spinners), text appearances |

## Naming Conventions

### Strings
Keys are **English** identifiers; values are **German** translations.
Pattern: `[feature]_[context]_[element]` — e.g. `task_edit_delete_confirm`, `budget_dialog_amount_hint`.

### Colors
- **Material tokens:** `[feature]_color_[role]` — e.g. `task_color_primary`, `task_color_on_surface`
- **Semantic purpose:** `[feature]_[domain]_[status]` — e.g. `task_urgency_overdue`, `budget_positive`
- **Component-scoped:** `[feature]_[component]_[role]` — e.g. `task_widget_text_primary`

Reference: [Material Color Roles](https://m3.material.io/styles/color/the-color-system/color-roles)

### Dimensions
- **App-wide spacing:** `spacing_[size]` — xxs (2dp), xs (4dp), sm (8dp), md (12dp), lg (16dp), xl (24dp)
- **App-wide text:** `text_[size]` — xs (12sp), sm (14sp), md (16sp), xl (18sp)
- **Feature-specific:** `[feature]_[component]_[property]` — e.g. `task_row_min_height`, `budget_card_corner_radius`

### Styles
Pattern: `[Widget|TextAppearance].[Namespace].[Component].[Variant]`
Always inherit from Material parents: `Widget.MaterialComponents.*`, `TextAppearance.MaterialComponents.*`.

## Adding New Resources

1. **App-wide** resources go in the generic file (`strings.xml`, `colors.xml`, `dimens.xml`, `styles.xml`).
2. **Feature-specific** resources go in feature-prefixed files (`task_strings.xml`, `budget_colors.xml`, etc.).
3. Follow the naming conventions above.
4. Reference in Java: `getString(R.string.key)`, `ContextCompat.getColor(ctx, R.color.key)`
5. Reference in XML: `@string/key`, `@color/key`, `@dimen/key`, `@style/StyleName`

## Further Reading

- [Android String Resources](https://developer.android.com/guide/topics/resources/string-resource)
- [Android Color Resources](https://developer.android.com/guide/topics/resources/more-resources#Color)
- [Material Design Color System](https://m3.material.io/styles/color)
- [Material Components for Android — Theming](https://material.io/develop/android/theming/color)
