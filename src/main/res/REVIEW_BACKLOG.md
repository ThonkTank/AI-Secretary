# src/main/res — Review Backlog

## [platform] Meal tabs: three independent MaterialButtons instead of MaterialButtonToggleGroup

**Location:** `layout/meal_overview_fragment.xml:28-48`

**What the visual issue is:** Three `MaterialButton` elements are used as tab switchers with no `MaterialButtonToggleGroup` wrapper. All three render identically (filled primary), so there is no visual indicator of which tab is currently active. The task list screen uses `MaterialButtonToggleGroup` with outlined buttons correctly; the meal screen does not.

**Why it hurts the design:** Users cannot see the selected state at a glance. The filled style on all three makes them look like three parallel actions, not a mutually exclusive tab selection. This violates the platform's expected pattern and the app's own established convention.

**Recommended change:** Wrap in `MaterialButtonToggleGroup` with `app:singleSelection="true"` and change button style to `?attr/materialButtonOutlinedStyle`. Update `MealPlannerFragment` to register an `addOnButtonCheckedListener` instead of individual click handlers.

**Tradeoffs:** Requires editing Java code outside `src/main/res`; deferred to next cycle.

---

## [typography] Type scale missing the "lg" step; "xl" label (18sp) is disproportionate

**Location:** `values/dimens.xml:9-12`

**What the visual issue is:** The app text scale is xs=12 / sm=14 / md=16 / xl=18. There is no `text_lg` step. The jump from `text_md` (16sp) to `text_xl` (18sp) is only 2sp yet uses the "xl" suffix, which implies a much larger size. Budget widget card values and nav labels reference `text_xl` and `text_md` respectively; nothing uses a "large" intermediate step.

**Why it hurts the design:** The naming misleads future contributors (xl should feel big, not 2sp over md). It also means there is no natural "lg" slot when a label between body and headline is needed.

**Recommended change:** Rename `text_xl` → `text_lg` and add a true `text_xl` (22sp or 24sp) if a display-size step is ever needed. Update all references.

**Tradeoffs:** Broad rename touching styles.xml and any Java `getDimensionPixelSize(R.dimen.text_xl)` calls; deferred to avoid regression risk.

---

## [a11y] Meal tabs: active tab communicated by color only — no screen-reader selected-state

**Location:** `features/meal/ui/MealPlannerFragment.java` (switchScreen / setTabButtonColor), `layout/meal_overview_fragment.xml:29-51`

**What users struggle with:** `switchScreen()` calls `setTabButtonColor()` which only changes `setTextColor()`. No `setSelected()`, `setChecked()`, or `announceForAccessibility()` is called. A screen-reader user has no way to know which tab is active.

**Recommended change:** In `setTabButtonColor()`, add `button.setSelected(isActive)` and update the button style to respond to the `selected` state (or call `announceForAccessibility` on the newly-active tab).

**Why it improves a11y:** Sighted users see the color change; screen-reader users currently get no feedback when switching tabs.

**Tradeoffs:** Requires editing `MealPlannerFragment.java` outside `src/main/res`; deferred.

---

## [consider] `xml/widget_*_info.xml` — `updatePeriodMillis` must be kept in sync with `WidgetConfiguration.WIDGET_UPDATE_PERIOD_MILLIS`

**Path(s) involved:** `xml/widget_task_info.xml:19`, `xml/widget_budget_info.xml:19`

**What makes it hard to read/navigate today:** Both widget XML files hardcode `android:updatePeriodMillis="1800000"` (30 minutes). Android's framework limitation prevents direct Java constant references in XML, so this value must be manually kept in sync with `WidgetConfiguration.WIDGET_UPDATE_PERIOD_MILLIS` in three separate locations. Comments in the XML files and Javadoc in `WidgetConfiguration.java` have been added to document this requirement, mitigating the immediate risk.

**Proposed structural change:** Add a Gradle build task that reads `WidgetConfiguration.WIDGET_UPDATE_PERIOD_MILLIS` and verifies both XML files contain the matching value; fail the build on mismatch.

**Why it reduces mental load:** Turns a silent runtime divergence into a compile-time error. Documents the dependency mechanically so it cannot be overlooked.

**Tradeoffs / risks:** Requires Gradle build script changes outside `src/main/res`; deferred.

---

## [coupling] `budget_colors.xml` references `task_color_*` tokens directly

**Location:** `values/budget_colors.xml:19-27`

**What the coupling is:** Four budget semantic color tokens alias task-feature tokens:
- `budget_chart_line` → `@color/task_color_primary`
- `budget_divider` → `@color/task_color_outline`
- `budget_widget_header_text` → `@color/task_color_on_primary`
- `budget_widget_card_text` → `@color/task_color_on_surface`

The budget feature has a direct named dependency on the task feature's color palette. If the task palette ever diverges, budget widget colors change silently.

**Why it matters:** The `task_color_*` namespace implies task-feature ownership, but these tokens serve as the effective app-wide theme palette (they're mapped to `colorPrimary` etc. in `styles.xml`). The naming creates false scoping — future contributors may hesitate to change `task_color_primary` fearing it only affects tasks, unaware the budget widget inherits from it too.

**Recommended change:** Move the core palette tokens (`task_color_primary`, `task_color_on_primary`, `task_color_on_surface`, `task_color_outline`, etc.) to `colors.xml` with app-wide names (e.g., `color_primary`). Keep `task_color_*` only for task-specific semantic colors (urgency, streak, progress).

**Tradeoffs:** Large rename touching `styles.xml`, multiple color files, and all Java callers of `R.color.task_color_*`; deferred to avoid regression risk.

---

## [nit] `budget_overview_fragment.xml` month nav uses media-playback system icons

**Location:** `layout/budget_overview_fragment.xml:113,129`

**What the inconsistency is:** `BudgetMonthPrevButton` and `BudgetMonthNextButton` use `@android:drawable/ic_media_previous` and `@android:drawable/ic_media_next` — icons designed for audio/video media controls. The task list screen navigates days using `<` and `>` text characters in `TextViews`.

**Why it matters:** The media playback icons convey the wrong semantic: a user who reads the content description (which is correct — "Vorheriger Monat") would be fine, but the visual metaphor is inconsistent with the app's other navigation affordances.

**Recommended change:** Replace with `@android:drawable/ic_media_rew` / `@android:drawable/ic_media_ff` (still system, but calendar-adjacent), or custom arrow drawables, or switch to text-based TextViews matching the task-list pattern. Lowest-effort: add `ic_nav_prev_24.xml` and `ic_nav_next_24.xml` vector drawables.

**Tradeoffs:** Purely cosmetic; content descriptions are already correct. Deferred — low impact.

---

## [low] No backup exclusion rules — financial DB exposed via ADB backup and Auto Backup

**Location:** `AndroidManifest.xml` (no `android:allowBackup`, no `android:dataExtractionRules`, no `android:fullBackupContent`)

**Vulnerability type:** Sensitive data backup exposure

**Attack scenario:** An attacker with physical access to the device runs `adb backup com.autosecretary` (requires user PIN confirmation) or leverages a compromised Google account to access Auto Backup data. The Room database containing all financial transactions is included by default. On Android 12+, a rooted device can extract backup data without user confirmation.

**Recommended fix:**
- For API 31+ (Android 12+): add `src/main/res/xml/data_extraction_rules.xml` excluding the database and SharedPreferences, then reference via `android:dataExtractionRules="@xml/data_extraction_rules"` in `<application>`.
- For API 26–30: add `android:fullBackupContent="@xml/backup_rules"` pointing to a similar exclusion file.
- Alternatively set `android:allowBackup="false"` to opt out entirely (loses cloud backup for users).

**Why it closes the attack vector:** Android honors the exclusion rules even for Auto Backup, preventing financial data from being copied to Google Drive or extracted via ADB.

**Tradeoffs:** Requires manifest + new XML file changes; users lose cloud backup of financial data unless the rules selectively allow non-sensitive items. Deferred pending product decision on whether user-facing backup is a desired feature.

---

## [consider] Stroke width `1dp` hardcoded in four drawable files

**Location:** `drawable/bg_surface_card.xml:7`, `drawable/task_bg_row.xml:7`, `drawable/task_bg_calendar_row.xml:8`, `drawable/task_editor_selector_background.xml:10`

**What makes it harder than needed:** Four separate drawables each hardcode `android:width="1dp"` for their outline stroke. A named token `outline_stroke_width` in `dimens.xml` would let a single edit update all outlines consistently.

**Note:** `task_dimens.xml` already has `task_editor_input_stroke_width = 1dp` for TextInputLayout; a separate generic token would cover shape drawables.

**Simpler alternative:** Add `<dimen name="outline_stroke_width">1dp</dimen>` to `dimens.xml` and reference it in all four drawables.

**Tradeoff:** The benefit is small since 1dp is a universal convention. Only worthwhile if the app ever needs to adjust stroke width globally (e.g., for a high-contrast accessibility theme). Deferred.
