package com.secretary.features.tasks.presentation.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.secretary.R
import com.secretary.features.tasks.presentation.activity.TaskActivity

/**
 * Tasks Fragment - displays task summary and quick access to Task Manager.
 *
 * Part of Wave 3 (Dream-to-Task feature) - BottomNav integration.
 * This fragment provides a simplified view with a button to open the full TaskActivity.
 *
 * Future: Will display inline task list with filtering capabilities.
 */
class TasksFragment : Fragment() {

    companion object {
        fun newInstance() = TasksFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_tasks, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup "Open Task Manager" button
        val openTasksButton = view.findViewById<Button>(R.id.openTasksButton)
        openTasksButton.setOnClickListener {
            val intent = Intent(requireContext(), TaskActivity::class.java)
            startActivity(intent)
        }

        // TODO: Load and display task summary stats
        val taskSummaryText = view.findViewById<TextView>(R.id.taskSummaryText)
        taskSummaryText.text = "Task summary coming soon..."
    }
}
