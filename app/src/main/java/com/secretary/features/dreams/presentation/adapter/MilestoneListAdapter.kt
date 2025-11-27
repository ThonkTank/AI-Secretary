package com.secretary.features.dreams.presentation.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.secretary.R
import com.secretary.features.dreams.domain.model.Milestone

/**
 * RecyclerView adapter for displaying milestone items.
 * Dream-to-Task Feature - Presentation Layer
 *
 * Displays milestones with:
 * - Title and optional description
 * - XP progress bar and text
 * - Status badge (Active/Completed)
 * - Prerequisite indicator (if any)
 * - Complete button for active milestones
 *
 * Uses DiffUtil for efficient list updates and provides callbacks for item interactions.
 *
 * Part of Wave 4: Dream Detail View
 * Related: DreamDetailFragment, Milestone domain model
 */
class MilestoneListAdapter(
    private val onMilestoneClick: (Milestone) -> Unit,
    private val onCompleteMilestone: (Milestone) -> Unit
) : ListAdapter<Milestone, MilestoneListAdapter.MilestoneViewHolder>(MilestoneDiffCallback()) {

    /**
     * Data class holding prerequisite information for a milestone.
     */
    data class PrerequisiteInfo(
        val totalCount: Int,
        val unmetCount: Int
    )

    private var prerequisiteMap: Map<Long, PrerequisiteInfo> = emptyMap()

    /**
     * Update prerequisite information for milestones.
     * Call this method after submitList to update prerequisite indicators.
     *
     * @param prereqMap Map of milestone ID to prerequisite info
     */
    fun setPrerequisites(prereqMap: Map<Long, PrerequisiteInfo>) {
        prerequisiteMap = prereqMap
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MilestoneViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_milestone, parent, false)
        return MilestoneViewHolder(view)
    }

    override fun onBindViewHolder(holder: MilestoneViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * ViewHolder for milestone items.
     * Handles binding milestone data to views and setting up click listeners.
     */
    inner class MilestoneViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.milestoneTitle)
        private val descriptionText: TextView = itemView.findViewById(R.id.milestoneDescription)
        private val xpProgressBar: ProgressBar = itemView.findViewById(R.id.milestoneXpProgress)
        private val xpText: TextView = itemView.findViewById(R.id.milestoneXpText)
        private val statusBadge: TextView = itemView.findViewById(R.id.milestoneStatusBadge)
        private val prereqIndicator: TextView = itemView.findViewById(R.id.milestonePrereqIndicator)
        private val completeButton: Button = itemView.findViewById(R.id.milestoneCompleteButton)

        /**
         * Bind milestone data to views.
         *
         * @param milestone The milestone to display
         */
        fun bind(milestone: Milestone) {
            titleText.text = milestone.title

            // Description
            if (milestone.description.isNullOrBlank()) {
                descriptionText.visibility = View.GONE
            } else {
                descriptionText.visibility = View.VISIBLE
                descriptionText.text = milestone.description
            }

            // XP Progress
            val progressPercent = if (milestone.targetXp > 0) {
                ((milestone.currentXp.toFloat() / milestone.targetXp) * 100).toInt().coerceIn(0, 100)
            } else 0
            xpProgressBar.progress = progressPercent
            xpText.text = "${milestone.currentXp}/${milestone.targetXp} XP"

            // Status
            when (milestone.status) {
                Milestone.Status.COMPLETED -> {
                    statusBadge.text = "✓ Completed"
                    statusBadge.setTextColor(Color.parseColor("#4CAF50"))
                    completeButton.visibility = View.GONE
                    xpProgressBar.progress = 100
                }
                Milestone.Status.ACTIVE -> {
                    statusBadge.text = "In Progress"
                    statusBadge.setTextColor(Color.parseColor("#2196F3"))
                    completeButton.visibility = View.VISIBLE
                    completeButton.text = "Complete"
                }
            }

            // Prerequisite Indicator
            val prereqInfo = prerequisiteMap[milestone.id]
            if (prereqInfo != null && prereqInfo.totalCount > 0) {
                prereqIndicator.visibility = View.VISIBLE

                // Format text
                val text = if (prereqInfo.totalCount == 1) {
                    itemView.context.getString(R.string.prereq_indicator_single)
                } else {
                    itemView.context.getString(R.string.prereq_indicator_plural, prereqInfo.totalCount)
                }
                prereqIndicator.text = "🔗 $text"

                // Color: Blue if all met, Orange if any unmet
                val color = if (prereqInfo.unmetCount > 0) {
                    Color.parseColor("#FF9800") // Orange for unmet
                } else {
                    Color.parseColor("#4CAF50") // Green for met
                }
                prereqIndicator.setTextColor(color)
            } else {
                prereqIndicator.visibility = View.GONE
            }

            // Click handlers
            itemView.setOnClickListener { onMilestoneClick(milestone) }
            completeButton.setOnClickListener { onCompleteMilestone(milestone) }
        }
    }

    /**
     * DiffUtil callback for efficient list updates.
     * Determines which items have changed to minimize UI updates.
     */
    class MilestoneDiffCallback : DiffUtil.ItemCallback<Milestone>() {
        override fun areItemsTheSame(oldItem: Milestone, newItem: Milestone): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Milestone, newItem: Milestone): Boolean {
            return oldItem == newItem
        }
    }
}
