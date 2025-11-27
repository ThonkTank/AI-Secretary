package com.secretary.features.dreams.presentation.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.secretary.R
import com.secretary.features.dreams.domain.model.Milestone
import com.secretary.features.dreams.domain.model.MilestoneInterdependency
import com.secretary.features.dreams.domain.service.InterdependencyService
import com.secretary.features.dreams.domain.service.LevelProgressionService
import com.secretary.features.dreams.presentation.adapter.MilestoneListAdapter
import com.secretary.features.dreams.presentation.components.SelfRegulationCard
import com.secretary.features.dreams.presentation.dialog.PrerequisiteWarningDialog
import com.secretary.features.dreams.presentation.viewmodel.DreamDetailViewModel
import com.secretary.features.dreams.presentation.viewmodel.DreamDetailViewModelFactory
import com.secretary.shared.database.TaskDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fragment displaying detailed view of a single Dream.
 * Dream-to-Task Feature - Presentation Layer
 *
 * Shows:
 * - Dream header with level badge and XP progress bar
 * - Milestone list with individual progress tracking
 * - FAB for adding new milestones
 * - Interdependency indicators (future enhancement)
 *
 * Part of Wave 4: Dream Detail View
 * Related: DreamDetailViewModel, MilestoneListAdapter
 */
class DreamDetailFragment : Fragment(), PrerequisiteWarningDialog.Callback {

    companion object {
        private const val ARG_DREAM_ID = "dream_id"

        /**
         * Create a new instance of DreamDetailFragment for a specific dream.
         *
         * @param dreamId The ID of the dream to display
         * @return A new DreamDetailFragment instance
         */
        fun newInstance(dreamId: Long): DreamDetailFragment {
            return DreamDetailFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_DREAM_ID, dreamId)
                }
            }
        }
    }

    private var dreamId: Long = 0
    private lateinit var viewModel: DreamDetailViewModel
    private lateinit var adapter: MilestoneListAdapter
    private val levelService = LevelProgressionService()
    private val interdependencyService = InterdependencyService()

    // Cached data for prerequisite checking
    private var allMilestones: List<Milestone> = emptyList()
    private var allInterdependencies: List<MilestoneInterdependency> = emptyList()

    // Views
    private lateinit var titleText: TextView
    private lateinit var levelBadge: TextView
    private lateinit var xpProgressBar: ProgressBar
    private lateinit var xpText: TextView
    private lateinit var descriptionText: TextView
    private lateinit var selfRegulationCard: SelfRegulationCard
    private lateinit var milestonesRecyclerView: RecyclerView
    private lateinit var emptyMilestonesText: TextView
    private lateinit var fabAddMilestone: FloatingActionButton
    private lateinit var btnAnalytics: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dreamId = arguments?.getLong(ARG_DREAM_ID) ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dream_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews(view)
        setupViewModel()
        setupAdapter()
        observeData()
    }

    /**
     * Initialize all view references and set up initial state.
     */
    private fun setupViews(view: View) {
        titleText = view.findViewById(R.id.dreamDetailTitle)
        levelBadge = view.findViewById(R.id.dreamDetailLevelBadge)
        xpProgressBar = view.findViewById(R.id.dreamDetailXpProgress)
        xpText = view.findViewById(R.id.dreamDetailXpText)
        descriptionText = view.findViewById(R.id.dreamDetailDescription)
        selfRegulationCard = view.findViewById(R.id.selfRegulationCard)
        milestonesRecyclerView = view.findViewById(R.id.milestonesRecyclerView)
        emptyMilestonesText = view.findViewById(R.id.emptyMilestonesText)
        fabAddMilestone = view.findViewById(R.id.fabAddMilestone)
        btnAnalytics = view.findViewById(R.id.btnAnalytics)

        milestonesRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        fabAddMilestone.setOnClickListener {
            // TODO: Show AddMilestoneDialog (Wave 5)
            Toast.makeText(context, "Add Milestone - Coming soon", Toast.LENGTH_SHORT).show()
        }

        btnAnalytics.setOnClickListener {
            openAnalytics()
        }
    }

    /**
     * Initialize ViewModel with DAO dependencies.
     */
    private fun setupViewModel() {
        val database = TaskDatabase.getDatabase(requireContext())
        val factory = DreamDetailViewModelFactory(
            dreamId = dreamId,
            dreamDao = database.dreamDao(),
            milestoneDao = database.milestoneDao()
        )
        viewModel = ViewModelProvider(this, factory)[DreamDetailViewModel::class.java]
    }

    /**
     * Initialize RecyclerView adapter with click handlers.
     */
    private fun setupAdapter() {
        adapter = MilestoneListAdapter(
            onMilestoneClick = { milestone ->
                // TODO: Open milestone detail or edit dialog (Wave 6)
                Toast.makeText(context, "Milestone: ${milestone.title}", Toast.LENGTH_SHORT).show()
            },
            onCompleteMilestone = { milestone ->
                handleMilestoneCompletion(milestone)
            }
        )
        milestonesRecyclerView.adapter = adapter
    }

    /**
     * Handle milestone completion with prerequisite checking.
     * Shows warning dialog if prerequisites are unmet, otherwise completes directly.
     */
    private fun handleMilestoneCompletion(milestone: Milestone) {
        if (milestone.status == Milestone.Status.COMPLETED) {
            return // Already completed
        }

        // Check for unmet prerequisites
        val unmetPrereqs = interdependencyService.getUnmetPrerequisites(
            milestone.id,
            allMilestones,
            allInterdependencies
        )

        if (unmetPrereqs.isNotEmpty()) {
            // Show warning dialog
            PrerequisiteWarningDialog.newInstance(milestone.id, unmetPrereqs)
                .show(childFragmentManager, "prerequisite_warning")
        } else {
            // No prerequisites or all met, proceed with completion
            completeMilestone(milestone.id)
        }
    }

    /**
     * Complete the milestone (called after prerequisite check or user override).
     */
    private fun completeMilestone(milestoneId: Long) {
        // TODO: Implement actual milestone completion logic
        // This should call the existing MilestoneCompletionDialog or UseCase
        Toast.makeText(context, "Completing milestone $milestoneId", Toast.LENGTH_SHORT).show()
    }

    /**
     * Open Dream Analytics screen
     */
    private fun openAnalytics() {
        val analyticsFragment = DreamAnalyticsFragment.newInstance(dreamId)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, analyticsFragment)
            .addToBackStack(null)
            .commit()
    }

    /**
     * Set up LiveData observers for dream and milestone data.
     */
    private fun observeData() {
        viewModel.dream.observe(viewLifecycleOwner) { dream ->
            if (dream != null) {
                titleText.text = dream.title
                levelBadge.text = "Lvl ${dream.currentLevel}"
                descriptionText.text = dream.description ?: ""
                descriptionText.visibility = if (dream.description.isNullOrBlank()) View.GONE else View.VISIBLE

                // XP Progress
                val progress = levelService.getLevelProgress(dream.totalXp)
                xpProgressBar.progress = (progress * 100).toInt()

                val currentLevelXp = levelService.getXPForLevel(dream.currentLevel)
                val nextLevelXp = levelService.getXPForLevel(dream.currentLevel + 1)
                val xpInCurrentLevel = dream.totalXp - currentLevelXp
                val xpNeededForLevel = nextLevelXp - currentLevelXp
                xpText.text = "$xpInCurrentLevel/$xpNeededForLevel XP to Level ${dream.currentLevel + 1}"
            }
        }

        viewModel.milestones.observe(viewLifecycleOwner) { milestones ->
            allMilestones = milestones
            adapter.submitList(milestones)

            // Load interdependencies and update prerequisite indicators
            loadInterdependenciesAndUpdateUI()

            if (milestones.isEmpty()) {
                emptyMilestonesText.visibility = View.VISIBLE
                milestonesRecyclerView.visibility = View.GONE
            } else {
                emptyMilestonesText.visibility = View.GONE
                milestonesRecyclerView.visibility = View.VISIBLE
            }
        }

        viewModel.selfRegulationState.observe(viewLifecycleOwner) { state ->
            state?.let {
                selfRegulationCard.setState(it)
                selfRegulationCard.visibility = View.VISIBLE
            } ?: run {
                selfRegulationCard.visibility = View.GONE
            }
        }
    }

    /**
     * Load interdependencies from database and update UI with prerequisite info.
     */
    private fun loadInterdependenciesAndUpdateUI() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Load all interdependencies
                val database = TaskDatabase.getDatabase(requireContext())
                val interdependencyDao = database.milestoneInterdependencyDao()
                val entities = interdependencyDao.getInterdependenciesForMilestone(dreamId) // Get all for this dream's milestones

                // Convert to domain models
                allInterdependencies = entities.map { entity ->
                    MilestoneInterdependency(
                        sourceMilestoneId = entity.sourceMilestoneId,
                        targetMilestoneId = entity.targetMilestoneId,
                        impactRating = entity.impactRating,
                        description = entity.description,
                        createdAt = entity.createdAt
                    )
                }

                // Calculate prerequisite info for each milestone
                val prereqMap = mutableMapOf<Long, MilestoneListAdapter.PrerequisiteInfo>()
                for (milestone in allMilestones) {
                    val prerequisites = interdependencyService.getPrerequisites(
                        milestone.id,
                        allMilestones,
                        allInterdependencies
                    )
                    val unmetPrereqs = prerequisites.filter {
                        it.status != Milestone.Status.COMPLETED
                    }

                    if (prerequisites.isNotEmpty()) {
                        prereqMap[milestone.id] = MilestoneListAdapter.PrerequisiteInfo(
                            totalCount = prerequisites.size,
                            unmetCount = unmetPrereqs.size
                        )
                    }
                }

                // Update adapter on main thread
                withContext(Dispatchers.Main) {
                    adapter.setPrerequisites(prereqMap)
                }
            } catch (e: Exception) {
                // Log error but don't crash
                e.printStackTrace()
            }
        }
    }

    /**
     * PrerequisiteWarningDialog callback - user chose to continue anyway.
     */
    override fun onContinueAnyway(milestoneId: Long) {
        completeMilestone(milestoneId)
    }

    /**
     * PrerequisiteWarningDialog callback - user chose to go back.
     */
    override fun onGoBack() {
        // Nothing to do, dialog already dismissed
    }

    /**
     * Reload data when fragment resumes (e.g., after editing).
     */
    override fun onResume() {
        super.onResume()
        viewModel.loadData()
    }
}
