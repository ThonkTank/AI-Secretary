package data;

/**
 * ══════════════════════════════════════════════════════════════════════════════
 * TASK ROW CONFIG - Konfiguration für einheitliche Task-Zeilen
 * ══════════════════════════════════════════════════════════════════════════════
 *
 * ZIEL:
 *   Trennt Business-Logik (Deadline-Rot, Streak-Farbe, Timer-State) von der
 *   View-Erstellung. Ermöglicht identische Darstellung in App und Widget.
 *
 * DESIGN:
 *   - Drei Record-Typen: TaskConfig, GoalHeaderConfig, CalendarConfig
 *   - Jeder Record hat eine Factory-Methode (from) die TaskEntry/DisplayRow akzeptiert
 *   - Alle Logik-Entscheidungen (Farben, Visibility, Strikethrough) hier zentralisiert
 *   - Verwendet @ColorRes Annotationen für Resource-IDs
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * RECORDS
 * ──────────────────────────────────────────────────────────────────────────────
 *
 *   TaskConfig:
 *     - slotId: Long                    → ID für Click-Handler (completeSlot/uncompleteSlot)
 *     - title: String                   → Task-Titel
 *     - strikethrough: boolean          → Durchgestrichen wenn completed
 *     - titleColorRes: @ColorRes int    → text_primary oder text_muted
 *     - backgroundColorRes: @ColorRes   → surface oder surface_complete
 *     - goalBarColor: Integer           → Parsed hex color vom Goal (nullable)
 *     - checked: boolean                → Checkbox-State
 *     - durationText: String            → "X min" Anzeige
 *     - showStreak: boolean             → Streak-Badge anzeigen?
 *     - streakText: String              → "🔥 X" (nullable)
 *     - streakColorRes: @ColorRes       → Rarity-Farbe (common→legendary)
 *     - showDeadline: boolean           → Deadline anzeigen?
 *     - deadlineText: String            → "Fällig: dd.MM.yyyy" (nullable)
 *     - deadlineColorRes: @ColorRes     → text_muted oder text_error (überfällig)
 *     - showTimer: boolean              → Timer-Button anzeigen? (false wenn completed)
 *     - timerRunning: boolean           → Timer läuft? (workStart != null)
 *     - timerColorRes: @ColorRes        → accent wenn running, sonst text_secondary
 *
 *   GoalHeaderConfig:
 *     - title: String                   → Goal-Name
 *     - icon: String                    → Emoji (nullable, z.B. "💪")
 *     - backgroundColor: Integer        → Parsed hex color (nullable)
 *
 *   CalendarConfig:
 *     - title: String                   → Event-Titel
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * FACTORY-METHODEN
 * ──────────────────────────────────────────────────────────────────────────────
 *
 *   TaskConfig.from(TaskEntry entry)
 *     → Berechnet alle derived values:
 *       - completed → strikethrough=true, titleColorRes=text_muted, bgRes=surface_complete
 *       - deadline != null && !isAfter(today) → deadlineColorRes=text_error
 *       - currentStreak > 0 → showStreak=true, streakColorRes via TaskListData.getStreakRarityColorRes()
 *       - workStart != null → timerRunning=true, timerColorRes=accent
 *
 *   GoalHeaderConfig.from(GoalHeader header)
 *     → Parsed goalColor via parseColor()
 *
 *   CalendarConfig.from(TaskEntry entry)
 *     → Nur title extrahieren
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * HILFSMETHODEN
 * ──────────────────────────────────────────────────────────────────────────────
 *
 *   private static Integer parseColor(String colorString)
 *     → Parsed "#RRGGBB" String zu Integer, gibt null bei Fehler zurück
 *
 */

import android.graphics.Color;
import androidx.annotation.ColorRes;
import com.autosecretary.R;
import controller.taskTab.TodoManager.TaskEntry;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class TaskRowConfig {

    private static final DateTimeFormatter DL_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // ══════════════════════════════════════════════════════════════════
    // TaskConfig — Konfiguration für Task-Zeilen
    // ══════════════════════════════════════════════════════════════════

    public record TaskConfig(
        Long slotId,
        String title,
        boolean strikethrough,
        @ColorRes int titleColorRes,
        @ColorRes int backgroundColorRes,
        boolean checked,
        String startTimeText,
        String endTimeText,
        boolean showStreak,
        String streakText,
        @ColorRes int streakColorRes,
        boolean showDeadline,
        String deadlineText,
        @ColorRes int deadlineColorRes,
        boolean showRemaining,
        String remainingText,
        boolean showTimer,
        boolean timerRunning,
        @ColorRes int timerColorRes,
        // Progress-Felder
        boolean hasProgress,
        int progressCurrent,
        int progressTarget,
        String progressText,
        boolean progressDoneToday,
        boolean progressFullyDone
    ) {
        public static TaskConfig from(TaskEntry entry) {
            boolean completed = entry.completed();
            boolean hasStreak = entry.currentStreak() > 0;
            boolean hasDeadline = entry.deadline() != null;
            boolean deadlineOverdue = hasDeadline
                && !entry.deadline().isAfter(LocalDate.now());
            boolean timerRunning = entry.workStart() != null;
            boolean hasRemaining = entry.remainingDays() > 0;

            // Progress-Logik
            boolean hasProgress = entry.progressTarget() > 0;
            boolean progressDoneToday = completed && hasProgress;
            boolean progressFullyDone = hasProgress
                && entry.progressCurrent() >= entry.progressTarget();

            // Hintergrund: grün wenn completed ODER progressDoneToday
            boolean showGreenBg = completed || progressDoneToday;

            // Strikethrough: nur bei normalen completed Tasks ODER progressFullyDone
            boolean strikethrough = (completed && !hasProgress) || progressFullyDone;

            // Titel-Farbe: muted wenn strikethrough
            @ColorRes int titleColorRes = strikethrough ? R.color.text_muted : R.color.text_primary;

            // Zeit-Formatierung (Start und Ende separat)
            String startTimeText = entry.start() != null ? entry.start().format(TIME_FMT) : null;
            String endTimeText = entry.end() != null ? entry.end().format(TIME_FMT) : null;

            return new TaskConfig(
                entry.slotId(),
                entry.taskTitle(),
                strikethrough,
                titleColorRes,
                showGreenBg ? R.color.surface_complete : R.color.surface,
                completed,
                startTimeText,
                endTimeText,
                hasStreak,
                hasStreak ? "🔥 " + entry.currentStreak() : null,
                TaskListData.getStreakRarityColorRes(entry.currentStreak()),
                hasDeadline,
                hasDeadline ? "Fällig: " + entry.deadline().format(DL_FMT) : null,
                deadlineOverdue ? R.color.text_error : R.color.text_muted,
                hasRemaining,
                hasRemaining ? "⏱ " + entry.remainingDays() + " Tage" : null,
                !completed,
                timerRunning,
                timerRunning ? R.color.accent : R.color.text_secondary,
                // Progress-Felder
                hasProgress,
                entry.progressCurrent(),
                entry.progressTarget(),
                hasProgress ? entry.progressCurrent() + "/" + entry.progressTarget() : null,
                progressDoneToday,
                progressFullyDone
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
        String title,
        String startTimeText,
        String endTimeText
    ) {
        public static CalendarConfig from(TaskEntry entry) {
            return new CalendarConfig(
                entry.taskTitle(),
                entry.start() != null ? entry.start().format(TIME_FMT) : null,
                entry.end() != null ? entry.end().format(TIME_FMT) : null
            );
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
