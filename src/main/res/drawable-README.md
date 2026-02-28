# Drawables (res/drawable/)

Vector icons and shape backgrounds used throughout the app.

## Naming Convention

| Prefix | Scope | Purpose | Example |
|--------|-------|---------|---------|
| `bg_*` | App-wide | Background shapes shared across features | `bg_forest_gradient` (window/widget bg) |
| `ic_*` | App-wide | Vector icons (toolbar, navigation, launcher) | `ic_settings_24` |
| `task_bg_*` | Task feature | Background shapes for task UI surfaces | `task_bg_row` |
| `task_editor_*` | Task feature | Task editor-specific drawables | `task_editor_selector_background` |

Size suffix `_24` on icons indicates 24dp intrinsic size (Material standard).

## Drawable Inventory

### App-wide backgrounds
- **`bg_forest_gradient`** — Vertical green gradient; app window background, task/budget widget background
- **`bg_surface_card`** — Rounded card with outline; used for elevated card surfaces (toolbar, editor panels)

### Icons
- **`ic_budget_24`** — Bottom-nav icon for the Budget tab
- **`ic_launcher_foreground`** — Adaptive launcher icon foreground (calendar + checkmark)
- **`ic_launcher_monochrome`** — Monochrome variant of launcher icon (must stay in sync with foreground; see file comments)
- **`ic_settings_24`** — Settings gear icon (toolbar menu)
- **`ic_task_edit_24`** — Pencil/edit icon (task row edit button)
- **`ic_tasks_24`** — Bottom-nav icon for the Tasks tab

### Task feature backgrounds
- **`task_bg_calendar_chip`** — Small rounded chip behind calendar labels in task rows
- **`task_bg_calendar_row`** — Row background for calendar-view task items (set programmatically in `ListRowAdapter`)
- **`task_bg_day_nav_chip`** — Pill-shaped chip for the day navigation selector in the task list header
- **`task_bg_row`** — Default row background for task list items
- **`task_editor_selector_background`** — Inset rounded field background for selector inputs in the task editor

## Further Reading

- [Android Drawable Resources](https://developer.android.com/guide/topics/resources/drawable-resource)
- [Android Vector Drawables](https://developer.android.com/develop/ui/views/graphics/vector-drawable-resources)
- [Material Symbols & Icons](https://fonts.google.com/icons)
