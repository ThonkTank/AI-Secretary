# src/main/res — Review Backlog

## ~~[platform] Meal tabs: three independent MaterialButtons instead of MaterialButtonToggleGroup~~ — RESOLVED

Resolved: Meal tabs now use `MaterialButtonToggleGroup` with `ToggleButton` style and `addOnButtonCheckedListener`.

---

## [typography] Type scale missing the "lg" step; "xl" label (18sp) is disproportionate

**Location:** `values/dimens.xml:9-12`

**What the visual issue is:** The app text scale is xs=12 / sm=14 / md=16 / xl=18. There is no `text_lg` step. The jump from `text_md` (16sp) to `text_xl` (18sp) is only 2sp yet uses the "xl" suffix, which implies a much larger size. Budget widget card values and nav labels reference `text_xl` and `text_md` respectively; nothing uses a "large" intermediate step.

**Why it hurts the design:** The naming misleads future contributors (xl should feel big, not 2sp over md). It also means there is no natural "lg" slot when a label between body and headline is needed.

**Recommended change:** Rename `text_xl` → `text_lg` and add a true `text_xl` (22sp or 24sp) if a display-size step is ever needed. Update all references.

**Tradeoffs:** Broad rename touching styles.xml and any Java `getDimensionPixelSize(R.dimen.text_xl)` calls; deferred to avoid regression risk.

---

## ~~[a11y] Meal tabs: active tab communicated by color only — no screen-reader selected-state~~ — RESOLVED

Resolved: Meal tabs now use `MaterialButtonToggleGroup` which handles selected-state accessibility automatically.

---

## [consider] `xml/widget_*_info.xml` — `updatePeriodMillis` must be kept in sync with `WidgetConfiguration.WIDGET_UPDATE_PERIOD_MILLIS`

**Path(s) involved:** `xml/widget_task_info.xml:19`, `xml/widget_budget_info.xml:19`

**What makes it hard to read/navigate today:** Both widget XML files hardcode `android:updatePeriodMillis="1800000"` (30 minutes). Android's framework limitation prevents direct Java constant references in XML, so this value must be manually kept in sync with `WidgetConfiguration.WIDGET_UPDATE_PERIOD_MILLIS` in three separate locations. Comments in the XML files and Javadoc in `WidgetConfiguration.java` have been added to document this requirement, mitigating the immediate risk.

**Proposed structural change:** Add a Gradle build task that reads `WidgetConfiguration.WIDGET_UPDATE_PERIOD_MILLIS` and verifies both XML files contain the matching value; fail the build on mismatch.

**Why it reduces mental load:** Turns a silent runtime divergence into a compile-time error. Documents the dependency mechanically so it cannot be overlooked.

**Tradeoffs / risks:** Requires Gradle build script changes outside `src/main/res`; deferred.

---

## ~~[coupling] `budget_colors.xml` references `task_color_*` tokens directly~~ — RESOLVED

Resolved: Core palette tokens were renamed from `task_color_*` to `color_*` (e.g. `color_primary`, `color_on_surface`). Budget colors now alias app-wide tokens. Semantic status colors (`budget_positive`, `budget_negative`, etc.) were further aligned with the nature palette.

---

## ~~[nit] `budget_overview_fragment.xml` month nav uses media-playback system icons~~ — RESOLVED

Resolved: Budget month navigation now uses the shared `include_temporal_nav_bar.xml` with chevron icons, matching the task list's day navigation.

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

**Location:** `drawable/bg_surface_card.xml:7`, `drawable/bg_row.xml:7`, `drawable/task_bg_calendar_row.xml:8`, `drawable/task_editor_selector_background.xml:10`

**What makes it harder than needed:** Four separate drawables each hardcode `android:width="1dp"` for their outline stroke. A named token `outline_stroke_width` in `dimens.xml` would let a single edit update all outlines consistently.

**Note:** `task_dimens.xml` already has `task_editor_input_stroke_width = 1dp` for TextInputLayout; a separate generic token would cover shape drawables.

**Simpler alternative:** Add `<dimen name="outline_stroke_width">1dp</dimen>` to `dimens.xml` and reference it in all four drawables.

**Tradeoff:** The benefit is small since 1dp is a universal convention. Only worthwhile if the app ever needs to adjust stroke width globally (e.g., for a high-contrast accessibility theme). Deferred.
