package com.secretary.features.dreams.presentation.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.secretary.R
import com.secretary.features.dreams.domain.model.Milestone

/**
 * Dialog warning user about unmet milestone prerequisites.
 * Dream-to-Task Feature - Presentation Layer
 *
 * Shows list of incomplete prerequisites and allows user to:
 * - Go back and complete prerequisites first
 * - Continue anyway (soft warning, not blocking)
 *
 * Part of Milestone Dependencies UI feature
 */
class PrerequisiteWarningDialog : DialogFragment() {

    companion object {
        private const val ARG_MILESTONE_ID = "milestone_id"
        private const val ARG_UNMET_PREREQ_TITLES = "unmet_prerequisite_titles"
        private const val ARG_UNMET_PREREQ_PROGRESS = "unmet_prerequisite_progress"

        /**
         * Create a new instance of PrerequisiteWarningDialog.
         *
         * @param milestoneId The ID of the milestone being completed
         * @param unmetPrerequisites List of incomplete prerequisite milestones
         * @return A new PrerequisiteWarningDialog instance
         */
        fun newInstance(
            milestoneId: Long,
            unmetPrerequisites: List<Milestone>
        ): PrerequisiteWarningDialog {
            val titles = ArrayList(unmetPrerequisites.map { it.title })
            val progress = ArrayList(unmetPrerequisites.map { it.getProgressString() })

            return PrerequisiteWarningDialog().apply {
                arguments = Bundle().apply {
                    putLong(ARG_MILESTONE_ID, milestoneId)
                    putStringArrayList(ARG_UNMET_PREREQ_TITLES, titles)
                    putStringArrayList(ARG_UNMET_PREREQ_PROGRESS, progress)
                }
            }
        }
    }

    /**
     * Callback interface for dialog result.
     */
    interface Callback {
        /**
         * Called when user chooses to complete milestone despite unmet prerequisites.
         *
         * @param milestoneId The milestone ID to complete
         */
        fun onContinueAnyway(milestoneId: Long)

        /**
         * Called when user chooses to go back.
         */
        fun onGoBack()
    }

    private var callback: Callback? = null
    private var milestoneId: Long = 0
    private lateinit var prereqTitles: List<String>
    private lateinit var prereqProgress: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        milestoneId = arguments?.getLong(ARG_MILESTONE_ID) ?: 0
        prereqTitles = arguments?.getStringArrayList(ARG_UNMET_PREREQ_TITLES) ?: emptyList()
        prereqProgress = arguments?.getStringArrayList(ARG_UNMET_PREREQ_PROGRESS) ?: emptyList()

        // Get callback from parent fragment
        callback = parentFragment as? Callback
            ?: activity as? Callback
            ?: throw IllegalStateException("Parent must implement PrerequisiteWarningDialog.Callback")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_prerequisite_warning, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup prerequisites list
        val recyclerView = view.findViewById<RecyclerView>(R.id.unmetPrereqsList)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = UnmetPrereqAdapter(prereqTitles, prereqProgress)

        // Setup button click handlers
        view.findViewById<Button>(R.id.btnGoBack).setOnClickListener {
            callback?.onGoBack()
            dismiss()
        }

        view.findViewById<Button>(R.id.btnContinueAnyway).setOnClickListener {
            callback?.onContinueAnyway(milestoneId)
            dismiss()
        }
    }

    /**
     * Simple adapter to display unmet prerequisites in a list.
     */
    private class UnmetPrereqAdapter(
        private val titles: List<String>,
        private val progress: List<String>
    ) : RecyclerView.Adapter<UnmetPrereqAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(
                android.R.layout.simple_list_item_2,
                parent,
                false
            )
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(titles[position], progress[position])
        }

        override fun getItemCount(): Int = titles.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val text1: TextView = itemView.findViewById(android.R.id.text1)
            private val text2: TextView = itemView.findViewById(android.R.id.text2)

            fun bind(title: String, progressText: String) {
                text1.text = "• $title"
                text2.text = progressText
                text2.setTextColor(0xFF757575.toInt())
            }
        }
    }
}
