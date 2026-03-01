# Color State Lists — `res/color/`

This directory contains **ColorStateList** XML files — colors that change based on view state
(enabled, pressed, checked, selected). They are different from the static color definitions
in `values/*_colors.xml`.

## When to use `color/*.xml` vs `values/*_colors.xml`

| Situation | Use |
|-----------|-----|
| Color is always the same solid value | `values/*_colors.xml` |
| Color must change when a view is pressed, disabled, selected, or checked | `color/*.xml` (ColorStateList) |

Reference the ColorStateList with `@color/filename` — the same syntax as a regular color.
Android resolves the correct state automatically at runtime.

See: https://developer.android.com/guide/topics/resources/color-list-resource

## Files in `color/`

| File | Used by | State behavior |
|------|---------|----------------|
| `task_button_text_color.xml` | `Widget.AutoSecretary.TaskEdit.DayPickerButton`, `Widget.AutoSecretary.BottomNavigation` | disabled → 50% primary; checked → on_primary; default → primary |
| `task_edit_day_button_background_tint.xml` | `Widget.AutoSecretary.TaskEdit.DayPickerButton` | disabled → 8% primary; checked → primary; default → transparent |
| `task_edit_day_button_stroke_color.xml` | `Widget.AutoSecretary.TaskEdit.DayPickerButton` | disabled → 25% primary; checked → primary; default → outline |

The color values referenced here (`task_color_primary`, `task_color_on_primary`, etc.) are
defined in `values/task_colors.xml`. The disabled alpha variants
(`task_color_primary_disabled_8`, `_25`, `_50`) are pre-computed alpha-composites of the
primary color — see the comment block in `task_colors.xml` for the calculation method.

## Adding a new ColorStateList

1. Create a new XML file in `res/color/` following the naming convention `[feature]_[component]_[property].xml`.
2. Use `<selector xmlns:android="http://schemas.android.com/apk/res/android">` as the root.
3. Order state items from most specific to least specific — Android matches the first item whose state conditions all hold.
4. Document the new file in the table above and reference it from the relevant style in `values/styles.xml`.
