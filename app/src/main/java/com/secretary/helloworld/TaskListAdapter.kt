package com.secretary.helloworld

import android.app.Activity
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.*

/**
 * ListView adapter for task display.
 * Phase 4.5.3 Wave 6: Converted to Kotlin
 *
 * Displays tasks with:
 * - Checkbox for completion status
 * - Title and description
 * - Category, priority, streaks, due dates, recurrence info
 * - Edit and delete buttons
 */
class TaskListAdapter(
    private val context: Activity,
    private val taskList: MutableList<Task>,
    private val listener: TaskActionListener
) : BaseAdapter() {

    // ========== Callback Interface ==========

    /**
     * Callback interface for task actions
     */
    interface TaskActionListener {
        fun onTaskCheckChanged(task: Task, isChecked: Boolean)
        fun onTaskEdit(task: Task)
        fun onTaskDelete(task: Task)
        fun onTasksChanged()
    }

    // ========== ViewHolder Pattern ==========

    /**
     * ViewHolder for efficient view recycling
     */
    private class ViewHolder(
        val checkBox: CheckBox,
        val titleTextView: TextView,
        val descriptionTextView: TextView,
        val infoTextView: TextView,
        val editButton: ImageButton,
        val deleteButton: ImageButton
    )

    // ========== BaseAdapter Overrides ==========

    override fun getCount(): Int = taskList.size

    override fun getItem(position: Int): Task = taskList[position]

    override fun getItemId(position: Int): Long = taskList[position].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View
        val holder: ViewHolder

        if (convertView == null) {
            view = LayoutInflater.from(context).inflate(R.layout.task_list_item, parent, false)
            holder = ViewHolder(
                checkBox = view.findViewById(R.id.taskCheckBox),
                titleTextView = view.findViewById(R.id.taskTitleText),
                descriptionTextView = view.findViewById(R.id.taskDescriptionText),
                infoTextView = view.findViewById(R.id.taskPriorityText),
                editButton = view.findViewById(R.id.editTaskButton),
                deleteButton = view.findViewById(R.id.deleteTaskButton)
            )
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        val task = getItem(position)
        bindTask(holder, task)
        setupClickListeners(holder, task)

        return view
    }

    // ========== Task Binding ==========

    /**
     * Bind task data to ViewHolder views
     */
    private fun bindTask(holder: ViewHolder, task: Task) {
        // Checkbox
        holder.checkBox.isChecked = task.isCompleted

        // Title with strike-through for completed tasks
        holder.titleTextView.text = task.title
        holder.titleTextView.paintFlags = if (task.isCompleted) {
            holder.titleTextView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            holder.titleTextView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }

        // Description
        if (!task.description.isNullOrBlank()) {
            holder.descriptionTextView.text = task.description
            holder.descriptionTextView.visibility = View.VISIBLE
        } else {
            holder.descriptionTextView.visibility = View.GONE
        }

        // Info text (category, priority, streaks, due date, recurrence)
        holder.infoTextView.text = buildInfoText(task)
    }

    /**
     * Build info text line with category, priority, streaks, dates, etc.
     */
    private fun buildInfoText(task: Task): String {
        val parts = mutableListOf<String>()

        // Category
        if (task.category.isNotBlank()) {
            parts.add(task.category)
        }

        // Priority
        val priorityText = when (task.priority) {
            0 -> "Low"
            1 -> "Medium"
            2 -> "High"
            3 -> "Urgent"
            else -> "?"
        }
        parts.add("Priority: $priorityText")

        // Current streak
        if (task.currentStreak > 0) {
            parts.add("🔥 ${task.currentStreak} days")
        }

        // Due date
        if (task.dueDate > 0) {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            parts.add("Due: ${dateFormat.format(Date(task.dueDate))}")
        }

        // Recurrence info
        if (task.recurrenceType != Task.RECURRENCE_NONE) {
            parts.add("🔁 ${getRecurrenceText(task)}")
        }

        return parts.joinToString(" | ")
    }

    /**
     * Get human-readable recurrence text
     */
    private fun getRecurrenceText(task: Task): String {
        val unit = when (task.recurrenceUnit) {
            Task.UNIT_DAY -> if (task.recurrenceAmount == 1) "day" else "days"
            Task.UNIT_WEEK -> if (task.recurrenceAmount == 1) "week" else "weeks"
            Task.UNIT_MONTH -> if (task.recurrenceAmount == 1) "month" else "months"
            Task.UNIT_YEAR -> if (task.recurrenceAmount == 1) "year" else "years"
            else -> "?"
        }

        return when (task.recurrenceType) {
            Task.RECURRENCE_INTERVAL -> "Every ${task.recurrenceAmount} $unit"
            Task.RECURRENCE_FREQUENCY -> "${task.recurrenceAmount} times per $unit"
            else -> ""
        }
    }

    // ========== Click Listeners ==========

    /**
     * Setup click listeners for task actions
     */
    private fun setupClickListeners(holder: ViewHolder, task: Task) {
        // Checkbox change listener
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            listener.onTaskCheckChanged(task, isChecked)
        }

        // Edit button
        holder.editButton.setOnClickListener {
            listener.onTaskEdit(task)
        }

        // Delete button
        holder.deleteButton.setOnClickListener {
            listener.onTaskDelete(task)
        }
    }

    // ========== Data Management ==========

    /**
     * Update task list and refresh UI
     */
    fun updateTasks(newTasks: List<Task>) {
        taskList.clear()
        taskList.addAll(newTasks)
        notifyDataSetChanged()
        listener.onTasksChanged()
    }

    /**
     * Clear all tasks
     */
    fun clear() {
        taskList.clear()
        notifyDataSetChanged()
    }
}
