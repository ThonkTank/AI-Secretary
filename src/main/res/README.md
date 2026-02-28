# Android Resources (src/main/res/)

This directory contains Android app resources: layouts, drawables, icons, and resource value files (strings, colors, dimensions, styles).

## Resource Organization

- **values/** — String, color, dimension, and style definitions (see [values-README.md](values-README.md) for details)
- **layout/** — XML layout files for Activities, Fragments, Dialogs
- **drawable/** — Vector drawables, shape definitions, state lists (see [drawable-README.md](drawable-README.md) for naming conventions and inventory)
- **color/** — Color state list definitions
- **menu/** — Menu and navigation action definitions
- **mipmap-\*/** — App launcher icons
- **xml/** — XML resource files (file paths configuration, widget info)

## For Developers

### Adding UI Text

Edit the appropriate string file in `values/`:
- **App-wide strings** → `values/strings.xml`
- **Task feature** → `values/task_strings.xml`
- **Budget feature** → `values/budget_strings.xml`
- **Meal feature** → `values/meal_strings.xml`

Reference in code: `getString(R.string.task_edit_dialog_title)`

### Adding Colors

Edit the appropriate color file in `values/`:
- **App-wide colors** → `values/colors.xml`
- **Task colors** → `values/task_colors.xml`
- **Budget colors** → `values/budget_colors.xml`

Use semantic names (task_urgency_overdue, not hex values). See `values/README.md` for naming conventions.

### Adding Dimensions / Layout Metrics

Edit `values/dimens.xml` for app-wide tokens (spacing scale, text sizes) or feature-specific files:
- `values/task_dimens.xml`
- `values/budget_dimens.xml`

### Adding Styles

Edit `values/styles.xml`. Follow Material Design naming: `Widget.AISecretary.[Component].[Variant]` or `TextAppearance.[Namespace].[Component]`.

Always inherit from Material parents: `Widget.MaterialComponents.*`, `TextAppearance.MaterialComponents.*`.

## Design Token System

AutoSecretary uses a design token system for consistency:

- **Spacing scale**: spacing_xxs through spacing_xl (2dp to 24dp)
- **Text scale**: text_xs through text_xl (12sp to 18sp)
- **Semantic colors**: task_urgency_overdue, task_deadline_soon, budget_positive, etc.
- **Material tokens**: task_color_primary, task_color_on_surface, etc.

This ensures visual consistency and makes updates (e.g., "change all primary colors") simple: update one file, not hundreds.

## Localization

All string keys are **English** (e.g., `app_name`, `task_edit_dialog_title`); all values are **German** (e.g., "AutoSecretary", "Aufgabe bearbeiten").

This is Android convention: keys are stable identifiers; values are translated per language.

To add a German string:
1. Add to `values/strings.xml` (app-wide) or `values/[feature]_strings.xml`
2. Use key naming: `[feature]_[context]_[element]` (e.g., `task_edit_delete_confirm`)
3. Reference in Java: `getString(R.string.task_edit_delete_confirm)`
4. Reference in XML: `android:text="@string/task_edit_delete_confirm"`

For multi-language support in the future: create `values-es/`, `values-fr/`, etc., and translate only the `<string>` values (keys stay English).

## Further Reading

- [Android Resource Overview](https://developer.android.com/guide/topics/resources/overview)
- [Material Design Color System](https://m3.material.io/styles/color)
- [Material Components for Android](https://material.io/develop/android)
- [Android Localization](https://developer.android.com/guide/topics/resources/localization)
