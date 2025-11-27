package com.secretary.features.dreams.presentation.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.button.MaterialButtonToggleGroup
import com.secretary.R
import com.secretary.features.dreams.presentation.components.SelfRegulationCard
import com.secretary.features.dreams.presentation.viewmodel.DreamAnalyticsViewModel
import com.secretary.features.dreams.presentation.viewmodel.DreamAnalyticsViewModelFactory
import com.secretary.shared.database.TaskDatabase

/**
 * Fragment displaying Dream Analytics Dashboard.
 * Dream Analytics Feature - Presentation Layer
 *
 * Shows:
 * - Dream header with level and total XP
 * - Gap analysis card (SOLL-IST)
 * - XP history line chart with time range filtering
 * - Stats cards (milestones completed, current streak)
 * - Streak insights
 * - Strategy recommendations
 *
 * Part of Phase 5.2: Dream Analytics
 * Related: DreamAnalyticsViewModel, DreamAnalyticsDao
 */
class DreamAnalyticsFragment : Fragment() {

    companion object {
        private const val ARG_DREAM_ID = "dream_id"

        /**
         * Create a new instance of DreamAnalyticsFragment for a specific dream.
         *
         * @param dreamId The ID of the dream to analyze
         * @return A new DreamAnalyticsFragment instance
         */
        fun newInstance(dreamId: Long): DreamAnalyticsFragment {
            return DreamAnalyticsFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_DREAM_ID, dreamId)
                }
            }
        }
    }

    private var dreamId: Long = 0
    private lateinit var viewModel: DreamAnalyticsViewModel

    // Views
    private lateinit var tvDreamTitle: TextView
    private lateinit var tvLevelValue: TextView
    private lateinit var tvXpValue: TextView
    private lateinit var selfRegulationCard: SelfRegulationCard
    private lateinit var chartXpHistory: LineChart
    private lateinit var tvNoChartData: TextView
    private lateinit var toggleTimeRange: MaterialButtonToggleGroup
    private lateinit var tvMilestonesCount: TextView
    private lateinit var tvStreakDays: TextView
    private lateinit var tvStreakInsight: TextView
    private lateinit var tvStrategyRecommendations: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dreamId = arguments?.getLong(ARG_DREAM_ID) ?: 0
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dream_analytics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        tvDreamTitle = view.findViewById(R.id.tv_dream_title)
        tvLevelValue = view.findViewById(R.id.tv_level_value)
        tvXpValue = view.findViewById(R.id.tv_xp_value)
        selfRegulationCard = view.findViewById(R.id.self_regulation_card)
        chartXpHistory = view.findViewById(R.id.chart_xp_history)
        tvNoChartData = view.findViewById(R.id.tv_no_chart_data)
        toggleTimeRange = view.findViewById(R.id.toggle_time_range)
        tvMilestonesCount = view.findViewById(R.id.tv_milestones_count)
        tvStreakDays = view.findViewById(R.id.tv_streak_days)
        tvStreakInsight = view.findViewById(R.id.tv_streak_insight)
        tvStrategyRecommendations = view.findViewById(R.id.tv_strategy_recommendations)

        // Initialize ViewModel
        setupViewModel()

        // Setup chart
        setupChart()

        // Setup time range toggle
        setupTimeRangeToggle()

        // Observe data
        observeViewModel()
    }

    /**
     * Initialize ViewModel with DAOs
     */
    private fun setupViewModel() {
        val database = TaskDatabase.getDatabase(requireContext())
        val factory = DreamAnalyticsViewModelFactory(
            dreamId = dreamId,
            dreamDao = database.dreamDao(),
            milestoneDao = database.milestoneDao(),
            analyticsDao = database.dreamAnalyticsDao()
        )
        viewModel = ViewModelProvider(this, factory)[DreamAnalyticsViewModel::class.java]
    }

    /**
     * Configure chart appearance and behavior
     */
    private fun setupChart() {
        chartXpHistory.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            setDragEnabled(true)
            setScaleEnabled(true)
            setPinchZoom(true)
            setDrawGridBackground(false)

            // X Axis
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setDrawGridLines(false)
                textColor = resources.getColor(android.R.color.darker_gray, null)
            }

            // Left Y Axis
            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = resources.getColor(android.R.color.darker_gray, null)
                gridLineWidth = 0.5f
                textColor = resources.getColor(android.R.color.darker_gray, null)
            }

            // Right Y Axis (disabled)
            axisRight.isEnabled = false

            // Legend
            legend.isEnabled = false
        }
    }

    /**
     * Setup time range toggle button listeners
     */
    private fun setupTimeRangeToggle() {
        toggleTimeRange.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            val timeRange = when (checkedId) {
                R.id.btn_range_7d -> DreamAnalyticsViewModel.TimeRange.DAYS_7
                R.id.btn_range_30d -> DreamAnalyticsViewModel.TimeRange.DAYS_30
                R.id.btn_range_all -> DreamAnalyticsViewModel.TimeRange.ALL
                else -> return@addOnButtonCheckedListener
            }

            viewModel.setTimeRange(timeRange)
        }
    }

    /**
     * Observe ViewModel LiveData and update UI
     */
    private fun observeViewModel() {
        // Dream data
        viewModel.dream.observe(viewLifecycleOwner) { dream ->
            if (dream != null) {
                tvDreamTitle.text = dream.title
                tvLevelValue.text = getString(R.string.level_display, dream.currentLevel)
                tvXpValue.text = getString(R.string.xp_format, dream.totalXp)
            }
        }

        // Chart data
        viewModel.chartData.observe(viewLifecycleOwner) { lineData ->
            if (lineData != null) {
                chartXpHistory.data = lineData
                chartXpHistory.invalidate() // Refresh chart
            }
        }

        // Chart visibility
        viewModel.hasData.observe(viewLifecycleOwner) { hasData ->
            if (hasData) {
                chartXpHistory.visibility = View.VISIBLE
                tvNoChartData.visibility = View.GONE
            } else {
                chartXpHistory.visibility = View.GONE
                tvNoChartData.visibility = View.VISIBLE
            }
        }

        // Stats
        viewModel.milestonesCompleted.observe(viewLifecycleOwner) { count ->
            tvMilestonesCount.text = count.toString()
        }

        viewModel.streakDays.observe(viewLifecycleOwner) { days ->
            tvStreakDays.text = days.toString()
        }

        // Streak insight
        viewModel.streakInsight.observe(viewLifecycleOwner) { insight ->
            tvStreakInsight.text = insight
        }

        // Strategy recommendations
        viewModel.strategyRecommendations.observe(viewLifecycleOwner) { recommendations ->
            tvStrategyRecommendations.text = recommendations
        }

        // Self-regulation state
        viewModel.selfRegulationState.observe(viewLifecycleOwner) { state ->
            if (state != null) {
                selfRegulationCard.setState(state)
                selfRegulationCard.visibility = View.VISIBLE
            } else {
                selfRegulationCard.visibility = View.GONE
            }
        }
    }
}
