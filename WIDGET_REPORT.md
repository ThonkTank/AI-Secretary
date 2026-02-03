# TaskList Widget Report
## Spezifikation für die nächste Implementation (Neuaufbau)

---

## 1. Anforderungen: Was die TaskList leisten MUSS

### 1.1 Feature-Matrix (App = Widget, 100% Parität)

| Feature | Beschreibung | Priorität |
|---------|--------------|-----------|
| **Goal-Header** | Farbiger Header mit Icon (Emoji) und Titel | MUSS |
| **Task-Zeile** | Titel, Dauer, Goal-Farbbalken | MUSS |
| **Checkbox** | Task abhaken / wieder öffnen | MUSS |
| **Completed-State** | Strikethrough, gedämpfte Farbe, anderer Hintergrund | MUSS |
| **Streak-Badge** | "🔥 35" mit Rarity-Färbung (Common→Legendary) | MUSS |
| **Deadline-Anzeige** | "Fällig: dd.MM.yyyy", rot wenn überfällig | MUSS |
| **Timer-Button** | ▶/⏹ zum Starten/Stoppen der Zeitmessung | MUSS |
| **Timer-State** | Visuelles Feedback wenn Timer läuft | MUSS |
| **Kalender-Events** | Blaue Markierung, keine Checkbox, kein Timer | MUSS |
| **Leer-State** | "Keine Aufgaben für heute" wenn Liste leer | MUSS |
| **Live-Updates** | Änderungen sofort sichtbar (nach DB-Änderung) | MUSS |

### 1.2 Visuelle Identität

App und Widget MÜSSEN **pixelgenau identisch** aussehen:
- Identische Layouts (DIESELBEN XML-Dateien)
- Identische Farben aus `colors.xml`
- Identische Dimensionen aus `dimens.xml`
- Identische View-IDs

---

## 2. Kernentscheidung: App passt sich an Widget an

### 2.1 Der Schlüssel-Insight

**Ein RemoteViews-kompatibles Layout funktioniert auch als normales Layout in der App.**

Statt zwei verschiedene Layouts zu pflegen, designen wir EIN Layout, das:
1. RemoteViews-Einschränkungen respektiert
2. In der App ganz normal verwendet werden kann

### 2.2 Was das bedeutet

| Aspekt | Alte Implementierung | Neue Implementierung |
|--------|---------------------|----------------------|
| Layouts | `card_task.xml` + `widget_item_task.xml` | **Ein** `item_task.xml` |
| Container | App: `LinearLayout` + `layout_weight` | **Beide:** `RelativeLayout` |
| Checkbox | App: `CheckBox`, Widget: `ImageView` | **Beide:** `ImageView` |
| IDs | `task_title` vs `widget_task_title` | **Gleiche IDs** |
| Renderer | Zwei separate Build-Methoden | **Ein** `TaskRowRenderer` |

### 2.3 RemoteViews-Kompatible View-Typen

Diese Views funktionieren in RemoteViews UND in der App:

| View-Typ | Verwendung |
|----------|------------|
| `RelativeLayout` | Container (statt LinearLayout mit weights) |
| `LinearLayout` | Nur OHNE nested layout_weight |
| `FrameLayout` | Container |
| `TextView` | Text, Timer-Button |
| `ImageView` | Checkbox, Icons |
| `View` | Farbbalken, Divider |
| `Button` | Buttons |
| `ProgressBar` | Ladeanimation |

**VERBOTEN in RemoteViews:**
- `CheckBox`, `RadioButton`, `Switch`
- `EditText`, `Spinner`
- Nested `layout_weight`
- Custom Views

---

## 3. Einheitliche Layout-Architektur

### 3.1 Verzeichnisstruktur

```
res/layout/
├── item_task.xml           # Task-Zeile (App + Widget)
├── item_goal_header.xml    # Goal-Header (App + Widget)
├── item_calendar.xml       # Kalender-Event (App + Widget)
├── list_tasks.xml          # Container für App (ScrollView + LinearLayout)
└── widget_list.xml         # Container für Widget (ListView)
```

### 3.2 Einheitliches Task-Layout

```xml
<!-- res/layout/item_task.xml -->
<?xml version="1.0" encoding="utf-8"?>
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/task_row"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:paddingStart="@dimen/card_padding_h"
    android:paddingEnd="@dimen/card_padding_h"
    android:paddingTop="@dimen/card_padding_v"
    android:paddingBottom="@dimen/card_padding_v"
    android:background="@color/surface">

    <!-- Goal-Farbbalken (links, volle Höhe) -->
    <View
        android:id="@+id/goal_color_bar"
        android:layout_width="@dimen/goal_bar_width"
        android:layout_height="match_parent"
        android:layout_alignParentStart="true"
        android:layout_alignTop="@id/task_checkbox"
        android:layout_alignBottom="@id/task_meta_row"
        android:background="@color/header_bg" />

    <!-- Checkbox (ImageView, nicht CheckBox!) -->
    <ImageView
        android:id="@+id/task_checkbox"
        android:layout_width="@dimen/checkbox_size"
        android:layout_height="@dimen/checkbox_size"
        android:layout_toEndOf="@id/goal_color_bar"
        android:layout_marginStart="@dimen/padding_small"
        android:scaleType="centerInside"
        android:src="@drawable/ic_checkbox_unchecked"
        android:contentDescription="@string/checkbox" />

    <!-- Timer-Button (rechts außen) -->
    <TextView
        android:id="@+id/task_timer"
        android:layout_width="@dimen/timer_button_size"
        android:layout_height="@dimen/timer_button_size"
        android:layout_alignParentEnd="true"
        android:layout_centerVertical="true"
        android:gravity="center"
        android:textSize="@dimen/sp_icon"
        android:textColor="@color/text_secondary" />

    <!-- Dauer (vor Timer) -->
    <TextView
        android:id="@+id/task_duration"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_toStartOf="@id/task_timer"
        android:layout_alignBaseline="@id/task_title"
        android:layout_marginEnd="@dimen/padding_small"
        android:textSize="@dimen/sp_small"
        android:textColor="@color/text_muted" />

    <!-- Titel (füllt Platz zwischen Checkbox und Dauer) -->
    <TextView
        android:id="@+id/task_title"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_toEndOf="@id/task_checkbox"
        android:layout_toStartOf="@id/task_duration"
        android:layout_marginStart="@dimen/padding_small"
        android:layout_marginEnd="@dimen/padding_small"
        android:textSize="@dimen/sp_body"
        android:textStyle="bold"
        android:textColor="@color/text_primary"
        android:maxLines="2"
        android:ellipsize="end" />

    <!-- Meta-Zeile (unter Titel) -->
    <LinearLayout
        android:id="@+id/task_meta_row"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_below="@id/task_title"
        android:layout_toEndOf="@id/task_checkbox"
        android:layout_marginStart="@dimen/padding_small"
        android:layout_marginTop="@dimen/padding_tiny"
        android:orientation="horizontal"
        android:visibility="gone">

        <!-- Streak-Badge -->
        <TextView
            android:id="@+id/task_streak"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textSize="@dimen/sp_small"
            android:textStyle="bold"
            android:visibility="gone" />

        <!-- Deadline -->
        <TextView
            android:id="@+id/task_deadline"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/padding_medium"
            android:textSize="@dimen/sp_small"
            android:textColor="@color/text_muted"
            android:visibility="gone" />

    </LinearLayout>

</RelativeLayout>
```

### 3.3 Einheitliches Goal-Header-Layout

```xml
<!-- res/layout/item_goal_header.xml -->
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/goal_header_root"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center_vertical"
    android:paddingStart="@dimen/card_padding_h"
    android:paddingEnd="@dimen/card_padding_h"
    android:paddingTop="@dimen/header_padding_v"
    android:paddingBottom="@dimen/header_padding_v"
    android:background="@color/header_bg">

    <!-- Icon (Emoji) -->
    <TextView
        android:id="@+id/goal_icon"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textSize="@dimen/sp_icon"
        android:layout_marginEnd="@dimen/padding_small"
        android:visibility="gone" />

    <!-- Titel -->
    <TextView
        android:id="@+id/goal_title"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textSize="@dimen/sp_title"
        android:textStyle="bold"
        android:textColor="@color/text_on_header" />

</LinearLayout>
```

### 3.4 Einheitliches Kalender-Event-Layout

```xml
<!-- res/layout/item_calendar.xml -->
<?xml version="1.0" encoding="utf-8"?>
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/calendar_row"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:paddingStart="@dimen/card_padding_h"
    android:paddingEnd="@dimen/card_padding_h"
    android:paddingTop="@dimen/card_padding_v"
    android:paddingBottom="@dimen/card_padding_v"
    android:background="@color/calendar_event_bg">

    <!-- Farbbalken (Accent) -->
    <View
        android:id="@+id/calendar_color_bar"
        android:layout_width="@dimen/goal_bar_width"
        android:layout_height="match_parent"
        android:layout_alignParentStart="true"
        android:background="@color/accent" />

    <!-- Titel -->
    <TextView
        android:id="@+id/calendar_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_toEndOf="@id/calendar_color_bar"
        android:layout_marginStart="@dimen/padding_small"
        android:textSize="@dimen/sp_body"
        android:textColor="@color/accent" />

</RelativeLayout>
```

---

## 4. Einheitliche Renderer-Architektur

### 4.1 Zwei-Schichten-Modell

```
┌─────────────────────────────────────────────────────────────────────────┐
│  SCHICHT 1: Gemeinsame Logik                                            │
│  ───────────────────────────                                            │
│                                                                         │
│  TaskListData.java (existiert)                                          │
│    • DisplayRow sealed interface                                        │
│    • fromEntries() — Daten-Transformation                               │
│    • getStreakRarityColorRes()                                          │
│                                                                         │
│  TaskRowRenderer.java (NEU)                                             │
│    • applyToView(View, TaskRowConfig) — für App                         │
│    • applyToRemoteViews(RemoteViews, TaskRowConfig) — für Widget        │
│    • Gemeinsame Hilfsmethoden                                           │
│                                                                         │
│  TaskRowConfig.java (NEU)                                               │
│    • Records für alle Konfigurationen                                   │
│    • Factory-Methoden (forTaskEntry, forGoalHeader, etc.)               │
└────────────────────────────────┬────────────────────────────────────────┘
                                 │
                 ┌───────────────┴───────────────┐
                 ▼                               ▼
┌────────────────────────────────┐ ┌────────────────────────────────────┐
│  SCHICHT 2a: App-Integration   │ │  SCHICHT 2b: Widget-Integration    │
│  ─────────────────────────     │ │  ───────────────────────────       │
│  taskList.java                 │ │  TaskWidgetFactory.java            │
│    • LayoutInflater            │ │    • RemoteViews                   │
│    • TaskRowRenderer.apply...  │ │    • TaskRowRenderer.apply...      │
│    • View.setOnClickListener() │ │    • setOnClickFillInIntent()      │
└────────────────────────────────┘ └────────────────────────────────────┘
```

### 4.2 TaskRowConfig (Konfiguration)

```java
// src/data/TaskRowConfig.java

package data;

import android.graphics.Color;
import androidx.annotation.ColorRes;
import com.autosecretary.R;
import controller.todoManager.TaskEntry;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class TaskRowConfig {

    private static final DateTimeFormatter DL_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    // ══════════════════════════════════════════════════════════════════
    // TaskConfig — Konfiguration für Task-Zeilen
    // ══════════════════════════════════════════════════════════════════

    public record TaskConfig(
        Long slotId,
        String title,
        boolean strikethrough,
        @ColorRes int titleColorRes,
        @ColorRes int backgroundColorRes,
        Integer goalBarColor,
        boolean checked,
        String durationText,
        boolean showStreak,
        String streakText,
        @ColorRes int streakColorRes,
        boolean showDeadline,
        String deadlineText,
        @ColorRes int deadlineColorRes,
        boolean showTimer,
        boolean timerRunning,
        @ColorRes int timerColorRes
    ) {
        public static TaskConfig from(TaskEntry entry) {
            boolean completed = entry.completed();
            boolean hasStreak = entry.currentStreak() > 0;
            boolean hasDeadline = entry.deadline() != null;
            boolean deadlineOverdue = hasDeadline
                && !entry.deadline().isAfter(LocalDate.now());
            boolean timerRunning = entry.workStart() != null;

            Integer goalBarColor = parseColor(entry.goalColor());

            return new TaskConfig(
                entry.slotId(),
                entry.taskTitle(),
                completed,
                completed ? R.color.text_muted : R.color.text_primary,
                completed ? R.color.surface_complete : R.color.surface,
                goalBarColor,
                completed,
                entry.timeToComplete() + " min",
                hasStreak,
                hasStreak ? "🔥 " + entry.currentStreak() : null,
                TaskListData.getStreakRarityColorRes(entry.currentStreak()),
                hasDeadline,
                hasDeadline ? "Fällig: " + entry.deadline().format(DL_FMT) : null,
                deadlineOverdue ? R.color.text_error : R.color.text_muted,
                !completed,
                timerRunning,
                timerRunning ? R.color.accent : R.color.text_secondary
            );
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // GoalHeaderConfig — Konfiguration für Goal-Header
    // ══════════════════════════════════════════════════════════════════

    public record GoalHeaderConfig(
        String title,
        String icon,
        Integer backgroundColor
    ) {
        public static GoalHeaderConfig from(TaskListData.GoalHeader header) {
            return new GoalHeaderConfig(
                header.title(),
                header.icon(),
                parseColor(header.color())
            );
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // CalendarConfig — Konfiguration für Kalender-Events
    // ══════════════════════════════════════════════════════════════════

    public record CalendarConfig(
        String title
    ) {
        public static CalendarConfig from(TaskEntry entry) {
            return new CalendarConfig(entry.taskTitle());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Hilfsmethoden
    // ══════════════════════════════════════════════════════════════════

    private static Integer parseColor(String colorString) {
        if (colorString == null || colorString.isEmpty()) return null;
        try {
            return Color.parseColor(colorString);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
```

### 4.3 TaskRowRenderer (Einheitlicher Renderer)

```java
// src/render/TaskRowRenderer.java

package render;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StrikethroughSpan;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.autosecretary.R;
import data.TaskRowConfig.*;

/**
 * Einheitlicher Renderer für Task-Zeilen.
 * Verwendet DIESELBEN Layouts für App und Widget.
 */
public class TaskRowRenderer {

    // ══════════════════════════════════════════════════════════════════
    // Task-Zeile — App (View)
    // ══════════════════════════════════════════════════════════════════

    public static void applyTask(View view, TaskConfig cfg, Context ctx) {
        // Hintergrund
        view.setBackgroundColor(ContextCompat.getColor(ctx, cfg.backgroundColorRes()));

        // Goal-Farbbalken
        View colorBar = view.findViewById(R.id.goal_color_bar);
        if (cfg.goalBarColor() != null) {
            colorBar.setBackgroundColor(cfg.goalBarColor());
        }

        // Checkbox (ImageView)
        ImageView checkbox = view.findViewById(R.id.task_checkbox);
        checkbox.setImageResource(cfg.checked()
            ? R.drawable.ic_checkbox_checked
            : R.drawable.ic_checkbox_unchecked);

        // Titel
        TextView title = view.findViewById(R.id.task_title);
        applyTitle(title, cfg.title(), cfg.strikethrough(), cfg.titleColorRes(), ctx);

        // Dauer
        TextView duration = view.findViewById(R.id.task_duration);
        duration.setText(cfg.durationText());

        // Meta-Zeile
        applyMetaRow(view, cfg, ctx);

        // Timer
        TextView timer = view.findViewById(R.id.task_timer);
        if (cfg.showTimer()) {
            timer.setText(cfg.timerRunning() ? "⏹" : "▶");
            timer.setTextColor(ContextCompat.getColor(ctx, cfg.timerColorRes()));
            timer.setVisibility(View.VISIBLE);
        } else {
            timer.setVisibility(View.GONE);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Task-Zeile — Widget (RemoteViews)
    // ══════════════════════════════════════════════════════════════════

    public static void applyTask(RemoteViews rv, TaskConfig cfg, Context ctx) {
        // Hintergrund
        rv.setInt(R.id.task_row, "setBackgroundColor",
            ContextCompat.getColor(ctx, cfg.backgroundColorRes()));

        // Goal-Farbbalken
        if (cfg.goalBarColor() != null) {
            rv.setInt(R.id.goal_color_bar, "setBackgroundColor", cfg.goalBarColor());
        }

        // Checkbox (ImageView)
        rv.setImageViewResource(R.id.task_checkbox,
            cfg.checked() ? R.drawable.ic_checkbox_checked : R.drawable.ic_checkbox_unchecked);

        // Titel
        applyTitle(rv, cfg.title(), cfg.strikethrough(), cfg.titleColorRes(), ctx);

        // Dauer
        rv.setTextViewText(R.id.task_duration, cfg.durationText());

        // Meta-Zeile
        applyMetaRow(rv, cfg, ctx);

        // Timer
        if (cfg.showTimer()) {
            rv.setTextViewText(R.id.task_timer, cfg.timerRunning() ? "⏹" : "▶");
            rv.setTextColor(R.id.task_timer, ContextCompat.getColor(ctx, cfg.timerColorRes()));
            rv.setViewVisibility(R.id.task_timer, View.VISIBLE);
        } else {
            rv.setViewVisibility(R.id.task_timer, View.GONE);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Goal-Header — App (View)
    // ══════════════════════════════════════════════════════════════════

    public static void applyGoalHeader(View view, GoalHeaderConfig cfg, Context ctx) {
        // Icon
        TextView icon = view.findViewById(R.id.goal_icon);
        if (cfg.icon() != null && !cfg.icon().isEmpty()) {
            icon.setText(cfg.icon());
            icon.setVisibility(View.VISIBLE);
        } else {
            icon.setVisibility(View.GONE);
        }

        // Titel
        TextView title = view.findViewById(R.id.goal_title);
        title.setText(cfg.title());

        // Hintergrundfarbe
        if (cfg.backgroundColor() != null) {
            view.setBackgroundColor(cfg.backgroundColor());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Goal-Header — Widget (RemoteViews)
    // ══════════════════════════════════════════════════════════════════

    public static void applyGoalHeader(RemoteViews rv, GoalHeaderConfig cfg, Context ctx) {
        // Icon
        if (cfg.icon() != null && !cfg.icon().isEmpty()) {
            rv.setTextViewText(R.id.goal_icon, cfg.icon());
            rv.setViewVisibility(R.id.goal_icon, View.VISIBLE);
        } else {
            rv.setViewVisibility(R.id.goal_icon, View.GONE);
        }

        // Titel
        rv.setTextViewText(R.id.goal_title, cfg.title());

        // Hintergrundfarbe
        if (cfg.backgroundColor() != null) {
            rv.setInt(R.id.goal_header_root, "setBackgroundColor", cfg.backgroundColor());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Kalender-Event — App (View)
    // ══════════════════════════════════════════════════════════════════

    public static void applyCalendar(View view, CalendarConfig cfg, Context ctx) {
        TextView title = view.findViewById(R.id.calendar_title);
        title.setText(cfg.title());
    }

    // ══════════════════════════════════════════════════════════════════
    // Kalender-Event — Widget (RemoteViews)
    // ══════════════════════════════════════════════════════════════════

    public static void applyCalendar(RemoteViews rv, CalendarConfig cfg, Context ctx) {
        rv.setTextViewText(R.id.calendar_title, cfg.title());
    }

    // ══════════════════════════════════════════════════════════════════
    // Private Hilfsmethoden
    // ══════════════════════════════════════════════════════════════════

    private static void applyTitle(TextView tv, String text, boolean strike, int colorRes, Context ctx) {
        if (strike) {
            SpannableString struck = new SpannableString(text);
            struck.setSpan(new StrikethroughSpan(), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            tv.setText(struck);
        } else {
            tv.setText(text);
        }
        tv.setTextColor(ContextCompat.getColor(ctx, colorRes));
    }

    private static void applyTitle(RemoteViews rv, String text, boolean strike, int colorRes, Context ctx) {
        if (strike) {
            SpannableString struck = new SpannableString(text);
            struck.setSpan(new StrikethroughSpan(), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            rv.setTextViewText(R.id.task_title, struck);
        } else {
            rv.setTextViewText(R.id.task_title, text);
        }
        rv.setTextColor(R.id.task_title, ContextCompat.getColor(ctx, colorRes));
    }

    private static void applyMetaRow(View view, TaskConfig cfg, Context ctx) {
        LinearLayout metaRow = view.findViewById(R.id.task_meta_row);
        TextView streak = view.findViewById(R.id.task_streak);
        TextView deadline = view.findViewById(R.id.task_deadline);

        boolean showMeta = cfg.showStreak() || cfg.showDeadline();
        metaRow.setVisibility(showMeta ? View.VISIBLE : View.GONE);

        if (cfg.showStreak()) {
            streak.setText(cfg.streakText());
            streak.setTextColor(ContextCompat.getColor(ctx, cfg.streakColorRes()));
            streak.setVisibility(View.VISIBLE);
        } else {
            streak.setVisibility(View.GONE);
        }

        if (cfg.showDeadline()) {
            deadline.setText(cfg.deadlineText());
            deadline.setTextColor(ContextCompat.getColor(ctx, cfg.deadlineColorRes()));
            deadline.setVisibility(View.VISIBLE);
        } else {
            deadline.setVisibility(View.GONE);
        }
    }

    private static void applyMetaRow(RemoteViews rv, TaskConfig cfg, Context ctx) {
        boolean showMeta = cfg.showStreak() || cfg.showDeadline();
        rv.setViewVisibility(R.id.task_meta_row, showMeta ? View.VISIBLE : View.GONE);

        if (cfg.showStreak()) {
            rv.setTextViewText(R.id.task_streak, cfg.streakText());
            rv.setTextColor(R.id.task_streak, ContextCompat.getColor(ctx, cfg.streakColorRes()));
            rv.setViewVisibility(R.id.task_streak, View.VISIBLE);
        } else {
            rv.setViewVisibility(R.id.task_streak, View.GONE);
        }

        if (cfg.showDeadline()) {
            rv.setTextViewText(R.id.task_deadline, cfg.deadlineText());
            rv.setTextColor(R.id.task_deadline, ContextCompat.getColor(ctx, cfg.deadlineColorRes()));
            rv.setViewVisibility(R.id.task_deadline, View.VISIBLE);
        } else {
            rv.setViewVisibility(R.id.task_deadline, View.GONE);
        }
    }
}
```

---

## 5. Vereinfachte App- und Widget-Integration

### 5.1 App (taskList.java)

```java
// src/activities/generic/taskList.java

public class taskList implements TodoListener {

    public void render() {
        container.removeAllViews();
        List<DisplayRow> rows = TaskListData.fromEntries(manager.provideList());

        for (DisplayRow row : rows) {
            if (row instanceof GoalHeader header) {
                View view = inflater.inflate(R.layout.item_goal_header, container, false);
                TaskRowRenderer.applyGoalHeader(view, GoalHeaderConfig.from(header), context);
                container.addView(view);
            }
            else if (row instanceof TaskItem item) {
                View view = inflater.inflate(R.layout.item_task, container, false);
                TaskConfig cfg = TaskConfig.from(item.entry());
                TaskRowRenderer.applyTask(view, cfg, context);

                // Listener (App-spezifisch)
                view.findViewById(R.id.task_checkbox).setOnClickListener(v -> {
                    if (cfg.checked()) manager.uncompleteSlot(cfg.slotId());
                    else manager.completeSlot(cfg.slotId());
                });

                view.findViewById(R.id.task_timer).setOnClickListener(v -> {
                    if (cfg.timerRunning()) manager.stopTimer(cfg.slotId());
                    else manager.startTimer(cfg.slotId());
                });

                container.addView(view);
            }
            else if (row instanceof CalendarEvent event) {
                View view = inflater.inflate(R.layout.item_calendar, container, false);
                TaskRowRenderer.applyCalendar(view, CalendarConfig.from(event.entry()), context);
                container.addView(view);
            }
        }
    }
}
```

### 5.2 Widget (TaskWidgetFactory.java)

```java
// src/activities/widget/TaskWidgetFactory.java

public class TaskWidgetFactory implements RemoteViewsService.RemoteViewsFactory {

    @Override
    public RemoteViews getViewAt(int position) {
        DisplayRow row = rows.get(position);

        if (row instanceof GoalHeader header) {
            RemoteViews rv = new RemoteViews(pkg, R.layout.item_goal_header);
            TaskRowRenderer.applyGoalHeader(rv, GoalHeaderConfig.from(header), context);
            return rv;
        }
        else if (row instanceof TaskItem item) {
            RemoteViews rv = new RemoteViews(pkg, R.layout.item_task);
            TaskConfig cfg = TaskConfig.from(item.entry());
            TaskRowRenderer.applyTask(rv, cfg, context);

            // Fill-In Intents (Widget-spezifisch)
            Intent checkIntent = new Intent()
                .putExtra("action", "toggle")
                .putExtra("slot_id", cfg.slotId());
            rv.setOnClickFillInIntent(R.id.task_checkbox, checkIntent);

            Intent timerIntent = new Intent()
                .putExtra("action", "timer")
                .putExtra("slot_id", cfg.slotId());
            rv.setOnClickFillInIntent(R.id.task_timer, timerIntent);

            return rv;
        }
        else if (row instanceof CalendarEvent event) {
            RemoteViews rv = new RemoteViews(pkg, R.layout.item_calendar);
            TaskRowRenderer.applyCalendar(rv, CalendarConfig.from(event.entry()), context);
            return rv;
        }

        return null;
    }
}
```

---

## 6. Änderungen an der App für Widget-Kompatibilität

### 6.1 Was an der App geändert werden MUSS

| Änderung | Alt | Neu | Grund |
|----------|-----|-----|-------|
| **Layout-Container** | `LinearLayout` + `layout_weight` | `RelativeLayout` + `toEndOf/toStartOf` | RemoteViews unterstützt kein nested weight |
| **Checkbox** | `CheckBox` Widget | `ImageView` + Drawable | CheckBox nicht in RemoteViews erlaubt |
| **Strikethrough** | `Paint.STRIKE_THRU_TEXT_FLAG` | `SpannableString` + `StrikethroughSpan` | Paint-Flags nicht in RemoteViews nutzbar |
| **Layout-IDs** | `task_title` vs `widget_task_title` | Einheitliche IDs | Gleicher Renderer für beide |
| **Layout-Dateien** | `card_task.xml` + `widget_item_task.xml` | Ein `item_task.xml` | Wartbarkeit |

### 6.2 Was NICHT geändert wird

| Aspekt | Warum |
|--------|-------|
| `todoManager` | Bleibt Single Source of Truth |
| `TaskListData` | Bewährte Daten-Transformation |
| Farben/Dimensionen | Bereits in XML, funktioniert |
| Business-Logik | Wird nur in Config-Layer verschoben |

### 6.3 Custom Checkbox-Drawables

Da `CheckBox` nicht verfügbar ist, brauchen wir eigene Drawables:

```
res/drawable/
├── ic_checkbox_unchecked.xml   # Leeres Quadrat
└── ic_checkbox_checked.xml     # Quadrat mit Häkchen
```

```xml
<!-- res/drawable/ic_checkbox_unchecked.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:strokeWidth="2"
        android:strokeColor="@color/text_secondary"
        android:fillColor="@android:color/transparent"
        android:pathData="M3,3h18v18H3z" />
</vector>

<!-- res/drawable/ic_checkbox_checked.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:strokeWidth="2"
        android:strokeColor="@color/accent"
        android:fillColor="@color/accent_light"
        android:pathData="M3,3h18v18H3z" />
    <path
        android:strokeWidth="2"
        android:strokeColor="@color/white"
        android:pathData="M6,12l4,4l8,-8" />
</vector>
```

---

## 7. Dateistruktur (Neu)

```
src/
├── data/
│   ├── TaskListData.java        # Beibehalten (Daten-Transformation)
│   └── TaskRowConfig.java       # NEU (Config-Records)
├── render/
│   └── TaskRowRenderer.java     # NEU (Einheitlicher Renderer)
├── activities/
│   ├── generic/
│   │   └── taskList.java        # Vereinfacht (nutzt Renderer)
│   └── widget/
│       ├── TaskWidgetProvider.java   # + Timer-Action
│       ├── TaskWidgetFactory.java    # Vereinfacht (nutzt Renderer)
│       └── ...
└── controller/
    └── todoManager.java         # Unverändert

res/layout/
├── item_task.xml                # NEU (Einheitlich)
├── item_goal_header.xml         # NEU (Einheitlich)
├── item_calendar.xml            # NEU (Einheitlich)
├── list_tasks.xml               # App-Container
└── widget_list.xml              # Widget-Container

res/drawable/
├── ic_checkbox_unchecked.xml    # NEU
└── ic_checkbox_checked.xml      # NEU
```

---

## 8. Implementierungs-Checkliste

### Phase 1: Grundlagen
- [ ] Einheitliche Layouts erstellen (`item_task.xml`, `item_goal_header.xml`, `item_calendar.xml`)
- [ ] Checkbox-Drawables erstellen
- [ ] `TaskRowConfig.java` erstellen
- [ ] `TaskRowRenderer.java` erstellen

### Phase 2: App-Migration
- [ ] `taskList.java` auf neue Layouts + Renderer umstellen
- [ ] `CheckBox` → `ImageView` mit OnClickListener
- [ ] Strikethrough auf SpannableString umstellen
- [ ] Testen: Alle Features funktionieren noch

### Phase 3: Widget-Migration
- [ ] `TaskWidgetFactory.java` auf neue Layouts + Renderer umstellen
- [ ] `TaskWidgetProvider.java` um Timer-Action erweitern
- [ ] Testen: Alle Features funktionieren

### Phase 4: Verifikation
- [ ] Screenshots: App und Widget nebeneinander vergleichen
- [ ] Feature-Test: Checkbox, Timer, Streak, Deadline in beiden
- [ ] Edge Cases: Leere Liste, lange Titel, überfällige Deadline

---

## 9. Zusammenfassung

### Das Kernprinzip

**Nicht das Widget an die App anpassen, sondern die App an das Widget.**

RemoteViews ist das restriktivere System. Ein RemoteViews-kompatibles Layout funktioniert überall. Indem wir die App auf RemoteViews-kompatible Layouts umstellen, können wir:

1. **Dieselben XML-Layouts** verwenden
2. **Dieselben View-IDs** verwenden
3. **Denselben Renderer** verwenden (mit Überladung für View/RemoteViews)
4. **100% visuelle Identität** garantieren
5. **Wartung halbieren** (eine Änderung wirkt auf beide)

### Code-Reduktion

| Vorher | Nachher |
|--------|---------|
| `card_task.xml` (App) | `item_task.xml` (beide) |
| `widget_item_task.xml` (Widget) | — |
| `buildTaskCard()` in taskList.java | `TaskRowRenderer.applyTask(View)` |
| `buildTaskRow()` in TaskWidgetFactory.java | `TaskRowRenderer.applyTask(RemoteViews)` |

Die Business-Logik (Deadline-Rot, Streak-Farbe, etc.) existiert nur noch EINMAL in `TaskRowConfig`.

---

## 10. App-spezifische Features (nicht im Widget)

### Replan-Button
- Button "Neu planen" im App-Container (`view_task_list.xml`)
- Ruft `manager.replanToday()` auf
- Löscht aktuellen Plan und generiert neu
- **Nicht im Widget** (Widget hat nur Refresh-Button für Daten-Reload)

---

## 11. Widget-Integration und Refresh

### notifyWidgetUpdate() — Statische Refresh-Methode

Zentrale Methode zum Aktualisieren aller Widget-Instanzen:

```java
public static void notifyWidgetUpdate(Context context) {
    AppWidgetManager manager = AppWidgetManager.getInstance(context);
    int[] ids = manager.getAppWidgetIds(
        new ComponentName(context, TaskWidgetProvider.class));
    if (ids != null && ids.length > 0) {
        manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list);
    }
}
```

**Aufrufer:**
- `DailyPlanningReceiver` nach Mitternachts-Planung
- `onReceive()` nach Checkbox-Toggle
- `onReceive()` nach Refresh-Button-Klick

### WidgetRefreshApp — Custom Application-Klasse

Registriert BroadcastReceiver für `ACTION_USER_PRESENT` (Geräte-Unlock):

```java
public class WidgetRefreshApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                TaskWidgetProvider.notifyWidgetUpdate(context);
            }
        }, new IntentFilter(Intent.ACTION_USER_PRESENT),
        Context.RECEIVER_NOT_EXPORTED);
    }
}
```

**Manifest-Registrierung:**
```xml
<application
    android:name="activities.widget.WidgetRefreshApp"
    ...>
```

### Custom Actions

```java
private static final String ACTION_TOGGLE = "com.autosecretary.widget.ACTION_TOGGLE";
private static final String ACTION_REFRESH = "com.autosecretary.widget.ACTION_REFRESH";
```

### Widget-Header-Interaktion

- **Header-Klick:** Öffnet die App (mainActivity) via PendingIntent
- **Refresh-Button (↻):** Manueller Widget-Refresh via ACTION_REFRESH Broadcast

### PendingIntent-Strategie

| Element | Flag | Grund |
|---------|------|-------|
| Checkbox Fill-In | `FLAG_MUTABLE` | Fill-In Extras erforderlich |
| Header/Refresh | `FLAG_IMMUTABLE` | Direkte Launches |

---

## 12. Styling-Details

### Abstände

- **Goal-Header margin-top:** 12dp (erster Header nach Button/vorherigem Block)
- **Card margin-top:** 1dp zwischen Task-Cards

### Timer-Button bei completed Tasks

- Farbe: `text_muted` (grau)
- OnClickListener: nicht gesetzt (deaktiviert)
- Visuell erkennbar als "nicht interaktiv"

### Completed-State (vereinheitlicht)

| Aspekt | Implementierung |
|--------|-----------------|
| Strikethrough | `SpannableString` + `StrikethroughSpan` |
| Textfarbe | `text_muted` |
| Hintergrund | `surface_complete` |

**Hinweis:** Die bisherige Widget-Implementierung verwendete Prefix "✓ " statt Strikethrough. Die neue Implementierung soll `StrikethroughSpan` für beide verwenden.

### Deadline-Format

- Einheitlich: `dd.MM.yyyy` (z.B. "Fällig: 15.02.2026")
- Überfällig: Textfarbe `text_error` (rot)

---

## 13. Widget-Provider Initialisierung

**Wichtig:** Vor `completeSlot()`/`uncompleteSlot()` muss `provideList()` aufgerufen werden, um den Manager zu initialisieren:

```java
// In onReceive() bei ACTION_TOGGLE:
todoManager manager = new todoManager(context);
manager.provideList();  // Initialisiert den Manager (lädt Plan aus DB)

if (wasCompleted) {
    manager.uncompleteSlot(slotId);
} else {
    manager.completeSlot(slotId);
}

notifyWidgetUpdate(context);
```

---

*Report erstellt: 2026-02-03*
*Aktualisiert: 2026-02-03 (fehlende Details vor Aufräumung ergänzt)*
*Für die nächste Implementation von Grund auf*
