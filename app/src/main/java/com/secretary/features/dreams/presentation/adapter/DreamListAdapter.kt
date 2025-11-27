package com.secretary.features.dreams.presentation.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.secretary.R
import com.secretary.features.dreams.domain.model.Dream
import com.secretary.features.dreams.domain.service.LevelProgressionService

/**
 * RecyclerView adapter for dream list.
 * Displays dreams as cards with:
 * - Title
 * - Level badge (e.g., "Lvl 5")
 * - XP progress bar
 * - Color indicator
 */
class DreamListAdapter(
    private val onDreamClick: (Dream) -> Unit
) : ListAdapter<Dream, DreamListAdapter.DreamViewHolder>(DreamDiffCallback()) {

    private val levelService = LevelProgressionService()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DreamViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dream, parent, false)
        return DreamViewHolder(view)
    }

    override fun onBindViewHolder(holder: DreamViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DreamViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: CardView = itemView.findViewById(R.id.dreamCard)
        private val colorIndicator: View = itemView.findViewById(R.id.colorIndicator)
        private val titleText: TextView = itemView.findViewById(R.id.dreamTitle)
        private val levelBadge: TextView = itemView.findViewById(R.id.levelBadge)
        private val xpProgressBar: ProgressBar = itemView.findViewById(R.id.xpProgressBar)
        private val xpText: TextView = itemView.findViewById(R.id.xpText)
        private val descriptionText: TextView = itemView.findViewById(R.id.dreamDescription)

        fun bind(dream: Dream) {
            // Color indicator
            try {
                colorIndicator.setBackgroundColor(dream.color)
            } catch (e: Exception) {
                colorIndicator.setBackgroundColor(Color.parseColor("#6200EE"))
            }

            // Title
            titleText.text = dream.title

            // Level badge
            levelBadge.text = "Lvl ${dream.currentLevel}"

            // XP Progress
            val progress = levelService.getLevelProgress(dream.totalXp)
            xpProgressBar.progress = (progress * 100).toInt()

            val currentLevelXp = levelService.getXPForLevel(dream.currentLevel)
            val nextLevelXp = levelService.getXPForLevel(dream.currentLevel + 1)
            val xpInCurrentLevel = dream.totalXp - currentLevelXp
            val xpNeededForLevel = nextLevelXp - currentLevelXp

            xpText.text = "${xpInCurrentLevel}/${xpNeededForLevel} XP"

            // Description (optional)
            if (dream.description.isNullOrBlank()) {
                descriptionText.visibility = View.GONE
            } else {
                descriptionText.visibility = View.VISIBLE
                descriptionText.text = dream.description
            }

            // Click handler
            cardView.setOnClickListener { onDreamClick(dream) }
        }
    }

    class DreamDiffCallback : DiffUtil.ItemCallback<Dream>() {
        override fun areItemsTheSame(oldItem: Dream, newItem: Dream): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Dream, newItem: Dream): Boolean {
            return oldItem == newItem
        }
    }
}
