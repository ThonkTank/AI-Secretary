package render;

/**
 * ══════════════════════════════════════════════════════════════════════════════
 * TASK ROW RENDERER - Einheitlicher Renderer für App und Widget
 * ══════════════════════════════════════════════════════════════════════════════
 *
 * ZIEL:
 *   Wendet TaskRowConfig auf Views (App) und RemoteViews (Widget) an.
 *   Verwendet DIESELBEN XML-Layouts für beide Systeme.
 *
 * DESIGN:
 *   - Überladene static Methoden: applyTask(View, ...) und applyTask(RemoteViews, ...)
 *   - Identische Logik, nur Android-API-Aufrufe unterscheiden sich:
 *     - View: view.findViewById(), setText(), setBackgroundColor()
 *     - RemoteViews: rv.setTextViewText(), rv.setInt(), rv.setViewVisibility()
 *   - Keine Business-Logik hier - nur reine View-Manipulation
 *   - Strikethrough via SpannableString + StrikethroughSpan (funktioniert in RemoteViews)
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * ÖFFENTLICHE METHODEN
 * ──────────────────────────────────────────────────────────────────────────────
 *
 *   applyTask(View view, TaskConfig cfg, Context ctx)
 *   applyTask(RemoteViews rv, TaskConfig cfg, Context ctx)
 *     1. Hintergrund setzen (backgroundColorRes)
 *     2. Checkbox-Icon oder Progress-Stepper setzen
 *     3. Titel setzen (mit optionalem Strikethrough + titleColorRes)
 *     4. Zeit setzen (Start + Ende, vertikal gestapelt)
 *     5. Meta-Row konfigurieren (Streak + Deadline Visibility/Text/Color)
 *     6. Timer-Button konfigurieren (Symbol + Farbe + Visibility)
 *
 *   applyGoalHeader(View view, GoalHeaderConfig cfg, Context ctx)
 *   applyGoalHeader(RemoteViews rv, GoalHeaderConfig cfg, Context ctx)
 *     1. Icon setzen (Emoji-TextView, GONE wenn null/empty)
 *     2. Titel setzen
 *     3. Hintergrundfarbe setzen (falls nicht null)
 *
 *   applyCalendar(View view, CalendarConfig cfg, Context ctx)
 *   applyCalendar(RemoteViews rv, CalendarConfig cfg, Context ctx)
 *     1. Titel setzen
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * PRIVATE HILFSMETHODEN
 * ──────────────────────────────────────────────────────────────────────────────
 *
 *   applyTitle(TextView tv, String text, boolean strike, int colorRes, Context ctx)
 *   applyTitle(RemoteViews rv, String text, boolean strike, int colorRes, Context ctx)
 *     → Strikethrough via SpannableString wenn strike=true
 *     → Farbe via ContextCompat.getColor()
 *
 *   applyMetaRow(View view, TaskConfig cfg, Context ctx)
 *   applyMetaRow(RemoteViews rv, TaskConfig cfg, Context ctx)
 *     → LinearLayout (meta_row) VISIBLE wenn showStreak || showDeadline
 *     → Streak-TextView: Text, Color, Visibility
 *     → Deadline-TextView: Text, Color, Visibility
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * VIEW-IDS (aus item_task.xml)
 * ──────────────────────────────────────────────────────────────────────────────
 *
 *   R.id.task_row          → Root RelativeLayout
 *   R.id.time_container    → LinearLayout (vertikal, Start+Ende)
 *   R.id.task_time_start   → TextView (Startzeit)
 *   R.id.task_time_end     → TextView (Endzeit)
 *   R.id.control_container → FrameLayout (Checkbox oder Progress)
 *   R.id.task_checkbox     → ImageView
 *   R.id.task_title        → TextView
 *   R.id.task_meta_row     → LinearLayout
 *   R.id.task_streak       → TextView
 *   R.id.task_deadline     → TextView
 *   R.id.task_timer        → TextView (▶/⏹)
 *
 */

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

        // Checkbox vs Progress-Stepper
        ImageView checkbox = view.findViewById(R.id.task_checkbox);
        View progressContainer = view.findViewById(R.id.progress_container);

        if (cfg.hasProgress()) {
            // Progress-Modus: Checkbox verstecken, Stepper zeigen
            checkbox.setVisibility(View.GONE);
            progressContainer.setVisibility(View.VISIBLE);

            // Progress-Text setzen
            TextView progressText = view.findViewById(R.id.progress_text);
            progressText.setText(cfg.progressText());

            // Button-States (visuelles Feedback für disabled)
            TextView btnMinus = view.findViewById(R.id.btn_progress_minus);
            TextView btnPlus = view.findViewById(R.id.btn_progress_plus);
            btnMinus.setAlpha(cfg.progressCurrent() > 0 ? 1.0f : 0.3f);
            btnPlus.setAlpha(cfg.progressCurrent() < cfg.progressTarget() ? 1.0f : 0.3f);
        } else {
            // Normal-Modus: Checkbox zeigen, Stepper verstecken
            checkbox.setVisibility(View.VISIBLE);
            progressContainer.setVisibility(View.GONE);
            checkbox.setImageResource(cfg.checked()
                ? R.drawable.ic_checkbox_checked
                : R.drawable.ic_checkbox_unchecked);
        }

        // Titel
        TextView title = view.findViewById(R.id.task_title);
        applyTitle(title, cfg.title(), cfg.strikethrough(), cfg.titleColorRes(), ctx);

        // Zeit (Start und Ende separat)
        TextView timeStart = view.findViewById(R.id.task_time_start);
        TextView timeEnd = view.findViewById(R.id.task_time_end);
        timeStart.setText(cfg.startTimeText() != null ? cfg.startTimeText() : "");
        timeEnd.setText(cfg.endTimeText() != null ? cfg.endTimeText() : "");

        // Meta-Zeile
        applyMetaRow(view, cfg, ctx);

        // Timer
        TextView timer = view.findViewById(R.id.task_timer);
        if (cfg.showTimer()) {
            timer.setText(cfg.timerRunning() ? "⏹" : "▶");
            timer.setTextColor(ContextCompat.getColor(ctx, cfg.timerColorRes()));
            timer.setVisibility(View.VISIBLE);
        } else {
            timer.setVisibility(View.INVISIBLE);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Task-Zeile — Widget (RemoteViews)
    // ══════════════════════════════════════════════════════════════════

    public static void applyTask(RemoteViews rv, TaskConfig cfg, Context ctx) {
        // Hintergrund
        rv.setInt(R.id.task_row, "setBackgroundColor",
            ContextCompat.getColor(ctx, cfg.backgroundColorRes()));

        // Checkbox vs Progress-Stepper
        if (cfg.hasProgress()) {
            // Progress-Modus: Checkbox verstecken, Stepper zeigen
            rv.setViewVisibility(R.id.task_checkbox, View.GONE);
            rv.setViewVisibility(R.id.progress_container, View.VISIBLE);

            // Progress-Text setzen
            rv.setTextViewText(R.id.progress_text, cfg.progressText());

            // Button-States (Alpha für disabled-Effekt)
            float minusAlpha = cfg.progressCurrent() > 0 ? 1.0f : 0.3f;
            float plusAlpha = cfg.progressCurrent() < cfg.progressTarget() ? 1.0f : 0.3f;
            rv.setFloat(R.id.btn_progress_minus, "setAlpha", minusAlpha);
            rv.setFloat(R.id.btn_progress_plus, "setAlpha", plusAlpha);
        } else {
            // Normal-Modus: Checkbox zeigen, Stepper verstecken
            rv.setViewVisibility(R.id.task_checkbox, View.VISIBLE);
            rv.setViewVisibility(R.id.progress_container, View.GONE);
            rv.setImageViewResource(R.id.task_checkbox,
                cfg.checked() ? R.drawable.ic_checkbox_checked : R.drawable.ic_checkbox_unchecked);
        }

        // Titel
        applyTitle(rv, cfg.title(), cfg.strikethrough(), cfg.titleColorRes(), ctx);

        // Zeit (Start und Ende separat)
        rv.setTextViewText(R.id.task_time_start, cfg.startTimeText() != null ? cfg.startTimeText() : "");
        rv.setTextViewText(R.id.task_time_end, cfg.endTimeText() != null ? cfg.endTimeText() : "");

        // Meta-Zeile
        applyMetaRow(rv, cfg, ctx);

        // Timer
        if (cfg.showTimer()) {
            rv.setTextViewText(R.id.task_timer, cfg.timerRunning() ? "⏹" : "▶");
            rv.setTextColor(R.id.task_timer, ContextCompat.getColor(ctx, cfg.timerColorRes()));
            rv.setViewVisibility(R.id.task_timer, View.VISIBLE);
        } else {
            rv.setViewVisibility(R.id.task_timer, View.INVISIBLE);
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
        // Titel
        TextView title = view.findViewById(R.id.calendar_title);
        title.setText(cfg.title());

        // Zeiten
        TextView timeStart = view.findViewById(R.id.calendar_time_start);
        TextView timeEnd = view.findViewById(R.id.calendar_time_end);
        if (cfg.startTimeText() != null) {
            timeStart.setText(cfg.startTimeText());
        }
        if (cfg.endTimeText() != null) {
            timeEnd.setText(cfg.endTimeText());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Kalender-Event — Widget (RemoteViews)
    // ══════════════════════════════════════════════════════════════════

    public static void applyCalendar(RemoteViews rv, CalendarConfig cfg, Context ctx) {
        // Titel
        rv.setTextViewText(R.id.calendar_title, cfg.title());

        // Zeiten
        if (cfg.startTimeText() != null) {
            rv.setTextViewText(R.id.calendar_time_start, cfg.startTimeText());
        }
        if (cfg.endTimeText() != null) {
            rv.setTextViewText(R.id.calendar_time_end, cfg.endTimeText());
        }
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
        TextView remaining = view.findViewById(R.id.task_remaining);

        boolean showMeta = cfg.showStreak() || cfg.showDeadline() || cfg.showRemaining();
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

        if (cfg.showRemaining()) {
            remaining.setText(cfg.remainingText());
            remaining.setVisibility(View.VISIBLE);
        } else {
            remaining.setVisibility(View.GONE);
        }
    }

    private static void applyMetaRow(RemoteViews rv, TaskConfig cfg, Context ctx) {
        boolean showMeta = cfg.showStreak() || cfg.showDeadline() || cfg.showRemaining();
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

        if (cfg.showRemaining()) {
            rv.setTextViewText(R.id.task_remaining, cfg.remainingText());
            rv.setViewVisibility(R.id.task_remaining, View.VISIBLE);
        } else {
            rv.setViewVisibility(R.id.task_remaining, View.GONE);
        }
    }
}
