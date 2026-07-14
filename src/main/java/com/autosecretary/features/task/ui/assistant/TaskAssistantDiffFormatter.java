package com.autosecretary.features.task.ui.assistant;

import android.content.Context;

import com.autosecretary.R;
import com.autosecretary.features.task.domain.assistant.ChangeOp;
import com.autosecretary.features.task.domain.assistant.TaskAssistantProposal;
import com.autosecretary.features.task.domain.assistant.TaskAssistantProposal.CategoryChange;
import com.autosecretary.features.task.domain.assistant.TaskAssistantProposal.RepetitionChange;
import com.autosecretary.features.task.domain.assistant.TaskAssistantProposal.TaskChange;
import com.autosecretary.features.task.domain.assistant.TaskSnapshot;
import com.autosecretary.features.task.domain.model.TaskCore;
import com.autosecretary.shared.Period;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders an assistant task proposal into a human-readable old→new diff string against the snapshot
 * it was based on. The op markers (＋ / ✎ / －) are language-neutral; the German labels come from
 * resources. Reused by the assistant screen and its tests.
 */
final class TaskAssistantDiffFormatter {
    private static final char OP_CREATE = '＋';
    private static final char OP_UPDATE = '✎';
    private static final char OP_DELETE = '－';
    private static final String ARROW = " → ";

    private TaskAssistantDiffFormatter() {
    }

    static String format(Context context, TaskSnapshot snapshot, TaskAssistantProposal proposal) {
        if (proposal.isEmpty()) {
            return context.getString(R.string.task_assistant_no_changes);
        }
        Map<String, TaskSnapshot.CategoryInfo> categoriesById = new HashMap<>();
        for (TaskSnapshot.CategoryInfo category : snapshot.categories()) {
            categoriesById.put(category.id(), category);
        }
        Map<String, TaskSnapshot.TaskInfo> tasksById = new HashMap<>();
        for (TaskSnapshot.TaskInfo task : snapshot.tasks()) {
            tasksById.put(task.id(), task);
        }

        StringBuilder sb = new StringBuilder();
        String categoryLabel = context.getString(R.string.task_assistant_diff_category);
        String taskLabel = context.getString(R.string.task_assistant_diff_task);
        for (CategoryChange change : proposal.categoryChanges()) {
            appendCategoryChange(sb, categoryLabel, change, categoriesById);
        }
        for (TaskChange change : proposal.taskChanges()) {
            appendTaskChange(context, sb, taskLabel, change, tasksById, categoriesById);
        }
        return sb.toString().trim();
    }

    private static void appendCategoryChange(StringBuilder sb, String label, CategoryChange change,
                                             Map<String, TaskSnapshot.CategoryInfo> categoriesById) {
        TaskSnapshot.CategoryInfo prior = change.id() != null ? categoriesById.get(change.id()) : null;
        String priorName = prior != null ? prior.name() : change.id();
        if (change.op() == ChangeOp.CREATE) {
            sb.append(OP_CREATE).append(' ').append(label).append(' ')
                    .append(labelWithIcon(change.icon(), change.name())).append('\n');
        } else if (change.op() == ChangeOp.UPDATE) {
            sb.append(OP_UPDATE).append(' ').append(label).append(' ').append(priorName);
            if (change.name() != null) sb.append(ARROW).append(change.name());
            sb.append('\n');
        } else {
            sb.append(OP_DELETE).append(' ').append(label).append(' ').append(priorName).append('\n');
        }
    }

    private static void appendTaskChange(Context context, StringBuilder sb, String label, TaskChange change,
                                         Map<String, TaskSnapshot.TaskInfo> tasksById,
                                         Map<String, TaskSnapshot.CategoryInfo> categoriesById) {
        TaskSnapshot.TaskInfo prior = change.id() != null ? tasksById.get(change.id()) : null;
        String priorTitle = prior != null ? prior.title() : change.id();
        if (change.op() == ChangeOp.CREATE) {
            sb.append(OP_CREATE).append(' ').append(label).append(' ').append(change.title());
            appendTaskAttributes(context, sb, change, categoriesById);
            sb.append('\n');
        } else if (change.op() == ChangeOp.UPDATE) {
            sb.append(OP_UPDATE).append(' ').append(label).append(' ').append(priorTitle);
            if (change.title() != null) sb.append(ARROW).append(change.title());
            appendTaskAttributes(context, sb, change, categoriesById);
            sb.append('\n');
        } else {
            sb.append(OP_DELETE).append(' ').append(label).append(' ').append(priorTitle).append('\n');
        }
    }

    private static void appendTaskAttributes(Context context, StringBuilder sb, TaskChange change,
                                             Map<String, TaskSnapshot.CategoryInfo> categoriesById) {
        if (change.categoryId() != null) {
            TaskSnapshot.CategoryInfo category = categoriesById.get(change.categoryId());
            sb.append(" [").append(category != null ? category.name() : change.categoryId()).append(']');
        }
        if (change.priority() != null) {
            sb.append(" [").append(change.priority().name()).append(']');
        }
        if (Boolean.TRUE.equals(change.leisure())) {
            sb.append(' ').append(context.getString(R.string.task_assistant_diff_leisure));
        }
        appendRepetition(context, sb, change.repetition());
        if (change.schedulingType() == TaskCore.SchedulingType.TERMIN && change.fixedDate() != null) {
            String time = change.fixedStart() != null ? " " + change.fixedStart() : "";
            sb.append(' ').append(context.getString(
                    R.string.task_assistant_diff_termin, change.fixedDate(), time));
        }
        if (change.deadline() != null) {
            sb.append(' ').append(context.getString(
                    R.string.task_assistant_diff_deadline, change.deadline()));
        }
        if (change.startDate() != null) {
            sb.append(' ').append(context.getString(
                    R.string.task_assistant_diff_start, change.startDate()));
        }
        if (change.prefSlots() != null && !change.prefSlots().isEmpty()) {
            sb.append(' ').append(context.getString(
                    R.string.task_assistant_diff_pref_slots, change.prefSlots().size()));
        }
    }

    private static void appendRepetition(Context context, StringBuilder sb, RepetitionChange rep) {
        if (rep == null || rep.reps() == null || rep.reps() <= 0) {
            return;
        }
        sb.append(" [").append(rep.reps()).append('×');
        String unit = periodLabel(context, rep.periodUnit());
        if (unit != null) {
            sb.append('/');
            if (rep.perPeriod() != null && rep.perPeriod() > 1) {
                sb.append(rep.perPeriod()).append(' ');
            }
            sb.append(unit);
        }
        sb.append(']');
    }

    private static String periodLabel(Context context, Period period) {
        if (period == null) {
            return null;
        }
        return switch (period) {
            case DAY -> context.getString(R.string.task_assistant_period_day);
            case WEEK -> context.getString(R.string.task_assistant_period_week);
            case MONTH -> context.getString(R.string.task_assistant_period_month);
        };
    }

    private static String labelWithIcon(String icon, String name) {
        return (icon != null ? icon + " " : "") + (name != null ? name : "");
    }
}
