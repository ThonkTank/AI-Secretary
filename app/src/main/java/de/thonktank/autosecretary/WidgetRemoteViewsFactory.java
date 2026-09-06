package de.thonktank.autosecretary;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StrikethroughSpan;
import android.view.View;
import android.widget.RemoteViews;

import de.thonktank.autosecretary.widget.WidgetUiModel;

public final class WidgetRemoteViewsFactory {
    private static final int[] STEP_ROWS = {R.id.widget_step_row_1, R.id.widget_step_row_2,
            R.id.widget_step_row_3};
    private static final int[] STEP_DOTS = {R.id.widget_step_dot_1, R.id.widget_step_dot_2,
            R.id.widget_step_dot_3};
    private static final int[] STEP_TEXTS = {R.id.widget_step_text_1, R.id.widget_step_text_2,
            R.id.widget_step_text_3};
    private static final int[] STEP_SUBTITLES = {R.id.widget_step_subtitle_1,
            R.id.widget_step_subtitle_2, R.id.widget_step_subtitle_3};
    private static final int[] PROGRESS = {R.id.widget_progress_1, R.id.widget_progress_2,
            R.id.widget_progress_3};

    private final Context context;
    private final WidgetForestCache forests;

    WidgetRemoteViewsFactory(Context context, WidgetForestCache forests) {
        this.context = context.getApplicationContext();
        this.forests = forests;
        forests.setDensity(context.getResources().getDisplayMetrics().density);
    }

    public RemoteViews create(WidgetUiModel model) {
        RemoteViews views = new RemoteViews(context.getPackageName(), layout(model.size));
        bindChrome(views, model);
        bindHeader(views, model);
        switch (model.size) {
            case SMALL:
                bindSmall(views, model);
                break;
            case WIDE:
                bindWide(views, model);
                break;
            case TALL:
                bindTall(views, model);
                break;
            case LARGE:
                bindLarge(views, model);
                break;
        }
        return views;
    }

    private void bindChrome(RemoteViews views, WidgetUiModel model) {
        views.setImageViewBitmap(R.id.widget_forest, forests.get(model.size, model.palette));
        PendingIntent open = openApp();
        views.setOnClickPendingIntent(R.id.widget_root, open);
        views.setOnClickPendingIntent(R.id.widget_title, open);
        views.setContentDescription(R.id.widget_root,
                context.getString(R.string.widget_open_dashboard));
    }

    private void bindHeader(RemoteViews views, WidgetUiModel model) {
        views.setTextViewText(R.id.widget_marker, model.marker);
        views.setTextColor(R.id.widget_marker,
                model.overdue ? model.palette.bad : model.palette.accent);
        views.setTextViewText(R.id.widget_title, model.title);
        views.setTextColor(R.id.widget_title, model.palette.ink);
    }

    private void bindSmall(RemoteViews views, WidgetUiModel model) {
        views.setViewVisibility(R.id.widget_progress, model.empty ? View.GONE : View.VISIBLE);
        if (!model.empty) {
            for (int i = 0; i < PROGRESS.length; i++) {
                boolean done = i < model.progress.size() && model.progress.get(i);
                views.setImageViewBitmap(PROGRESS[i], forests.progress(done, model.palette));
            }
        }
        bindPrimaryAction(views, model);
    }

    private void bindWide(RemoteViews views, WidgetUiModel model) {
        views.setViewVisibility(R.id.widget_steps, model.empty ? View.GONE : View.VISIBLE);
        if (!model.empty) bindSteps(views, model);
        boolean showMore = model.additionalStepCount > 0;
        views.setViewVisibility(R.id.widget_more, showMore ? View.VISIBLE : View.GONE);
        if (showMore) views.setTextViewText(R.id.widget_more,
                context.getResources().getQuantityString(R.plurals.widget_more_steps,
                        model.additionalStepCount, model.additionalStepCount));
        bindPrimaryAction(views, model);
    }

    private void bindTall(RemoteViews views, WidgetUiModel model) {
        views.setViewVisibility(R.id.widget_steps, model.empty ? View.GONE : View.VISIBLE);
        if (!model.empty) bindSteps(views, model);
        bindPrimaryAction(views, model);
        bindCalendar(views, model);
    }

    private void bindLarge(RemoteViews views, WidgetUiModel model) {
        views.setViewVisibility(R.id.widget_steps, model.empty ? View.GONE : View.VISIBLE);
        if (!model.empty) bindSteps(views, model);
        boolean showAfter = model.afterTitle != null;
        views.setViewVisibility(R.id.widget_after_leaf, showAfter ? View.VISIBLE : View.GONE);
        if (showAfter) {
            views.setTextViewText(R.id.widget_after_title, model.afterTitle);
            views.setTextColor(R.id.widget_after_title, model.palette.ink);
            views.setImageViewBitmap(R.id.widget_after_background,
                    forests.leaf(286, 62, false, model.palette));
        }
        bindCalendar(views, model);
        views.setViewVisibility(R.id.widget_add_target, model.showAdd ? View.VISIBLE : View.GONE);
        if (model.showAdd) {
            views.setTextColor(R.id.widget_add, model.palette.lightText);
            views.setImageViewBitmap(R.id.widget_add_background, forests.addButton(model.palette));
            views.setOnClickPendingIntent(R.id.widget_add, openEditor());
            views.setContentDescription(R.id.widget_add,
                    context.getString(R.string.content_add_task));
        }
    }

    private void bindSteps(RemoteViews views, WidgetUiModel model) {
        for (int i = 0; i < STEP_ROWS.length; i++) {
            boolean visible = i < model.steps.size();
            views.setViewVisibility(STEP_ROWS[i], visible ? View.VISIBLE : View.GONE);
            if (!visible) continue;
            de.thonktank.autosecretary.widget.WidgetStepUiModel step = model.steps.get(i);
            views.setImageViewBitmap(STEP_DOTS[i], forests.dew(step.done, model.palette));
            views.setTextViewText(STEP_TEXTS[i], step.done ? strike(step.title) : step.title);
            views.setTextColor(STEP_TEXTS[i], step.done ? model.palette.done : model.palette.ink);
            boolean hasSubtitle = !step.subtitle.isEmpty();
            views.setViewVisibility(STEP_SUBTITLES[i], hasSubtitle ? View.VISIBLE : View.GONE);
            if (hasSubtitle) {
                views.setTextViewText(STEP_SUBTITLES[i],
                        step.done ? strike(step.subtitle) : step.subtitle);
                views.setTextColor(STEP_SUBTITLES[i], step.done
                        ? model.palette.done : model.palette.muted);
            }
            views.setOnClickPendingIntent(STEP_DOTS[i], step.requiresApp
                    ? openApp() : toggleStep(step.id));
            String fullLabel = step.title + (hasSubtitle ? ", " + step.subtitle : "");
            views.setContentDescription(STEP_DOTS[i],
                    context.getString(R.string.widget_toggle_step, fullLabel));
        }
    }

    private void bindPrimaryAction(RemoteViews views, WidgetUiModel model) {
        views.setViewVisibility(R.id.widget_action_target,
                model.primaryAction == WidgetUiModel.PrimaryAction.NONE ? View.GONE : View.VISIBLE);
        if (model.primaryAction == WidgetUiModel.PrimaryAction.NONE) return;
        views.setTextViewText(R.id.widget_action, model.primaryActionLabel);
        views.setTextColor(R.id.widget_action, model.palette.accentText);
        views.setImageViewBitmap(R.id.widget_action_background,
                forests.button(model.size, model.palette));
        PendingIntent action;
        if (model.primaryAction == WidgetUiModel.PrimaryAction.OPEN_APP) action = openApp();
        else if (model.primaryAction == WidgetUiModel.PrimaryAction.OPEN_EDITOR) action = openEditor();
        else if (model.primaryAction == WidgetUiModel.PrimaryAction.CONFIRM_CLOSE)
            action = confirmClose(model.primaryActionId, model.taskTitle);
        else action = complete(model.primaryActionId);
        views.setOnClickPendingIntent(R.id.widget_action, action);
        views.setContentDescription(R.id.widget_action,
                model.primaryAction == WidgetUiModel.PrimaryAction.CONFIRM_CLOSE
                        ? context.getString(R.string.widget_confirm_close, model.taskTitle)
                        : model.primaryActionLabel);
    }

    private void bindCalendar(RemoteViews views, WidgetUiModel model) {
        boolean show = model.calendar != null;
        views.setViewVisibility(R.id.widget_calendar_leaf, show ? View.VISIBLE : View.GONE);
        if (!show) return;
        views.setTextViewText(R.id.widget_calendar_time, model.calendar.time);
        views.setTextViewText(R.id.widget_calendar_title, model.calendar.title);
        views.setTextColor(R.id.widget_calendar_time, model.palette.calendarInk);
        views.setTextColor(R.id.widget_calendar_title, model.palette.calendarInk);
        int width = model.size == WidgetSizeClassifier.Size.TALL ? 230 : 286;
        views.setImageViewBitmap(R.id.widget_calendar_background,
                forests.leaf(width, 84, true, model.palette));
    }

    private PendingIntent complete(String occurrenceId) {
        return broadcast(TaskActionReceiver.COMPLETE, TaskActionReceiver.EXTRA_OCCURRENCE_ID,
                occurrenceId);
    }

    private PendingIntent toggleStep(String stepId) {
        return broadcast(TaskActionReceiver.TOGGLE_STEP, TaskActionReceiver.EXTRA_STEP_ID, stepId);
    }

    private PendingIntent broadcast(String action, String key, String value) {
        Intent intent = new Intent(context, TaskActionReceiver.class).setAction(action)
                .putExtra(key, value);
        return PendingIntent.getBroadcast(context, (action + value).hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent openApp() {
        return PendingIntent.getActivity(context, 1, new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent openEditor() {
        Intent intent = new Intent(context, MainActivity.class).putExtra(MainActivity.OPEN_EDITOR, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 2, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent confirmClose(String taskId, String title) {
        Intent intent = new Intent(context, MainActivity.class)
                .putExtra(MainActivity.CONFIRM_TASK, taskId)
                .putExtra(MainActivity.CONFIRM_TASK_TITLE, title)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, ("confirm" + taskId).hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static int layout(WidgetSizeClassifier.Size size) {
        switch (size) {
            case SMALL: return R.layout.task_widget;
            case WIDE: return R.layout.task_widget_wide;
            case TALL: return R.layout.task_widget_tall;
            case LARGE: return R.layout.task_widget_large;
            default: throw new IllegalArgumentException("Unknown widget size " + size);
        }
    }

    private static SpannableString strike(String text) {
        SpannableString result = new SpannableString(text);
        result.setSpan(new StrikethroughSpan(), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return result;
    }

}
