# Review Backlog — res

## Open Issues

---

### [consider] Hardcoded `android:textSize` in `budget_widget.xml`

**File:** `layout/budget_widget.xml:15, 31, 38, 55`

**Current code:**
```xml
android:textSize="15sp"
android:textSize="12sp"
android:textSize="18sp"
android:textSize="16sp"
```

**Proposed alternative:** Use `android:textAppearance="@style/TextAppearance.MaterialComponents.*"` to match every other text-bearing view in the app.

**Why it's clearer:** The rest of the codebase delegates all text sizing to `TextAppearance` styles, making scale and theme changes trivial. These four raw `sp` values in the widget stand alone and require manual synchronisation. Note: widget views have some limitations with Material styles — verify rendering if applying.

---

### [consider] 6-digit hex in `budget_colors.xml` vs 8-digit ARGB in `task_colors.xml`

**File:** `values/budget_colors.xml:4–11`

**Current code:**
```xml
<color name="budget_positive">#4CAF50</color>
<color name="budget_negative">#F44336</color>
```

**Proposed alternative:**
```xml
<color name="budget_positive">#FF4CAF50</color>
<color name="budget_negative">#FFF44336</color>
```
(and all remaining budget colors)

**Why it's clearer:** `task_colors.xml` uses 8-digit ARGB format throughout. When a reader scans both files, 6-digit hex requires a separate mental step ("oh right, 6-digit means alpha=FF") that the 8-digit format makes explicit. Consistency across the two files removes that ambiguity.

---
