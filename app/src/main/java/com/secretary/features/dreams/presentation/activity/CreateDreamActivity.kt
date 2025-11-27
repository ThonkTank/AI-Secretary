package com.secretary.features.dreams.presentation.activity

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.secretary.R
import com.secretary.features.dreams.data.repository.DreamRepositoryImpl
import com.secretary.features.dreams.data.repository.MilestoneRepositoryImpl
import com.secretary.features.dreams.domain.usecase.CreateDreamUseCase
import com.secretary.features.dreams.domain.usecase.CreateMilestoneUseCase
import com.secretary.shared.database.TaskDatabase
import kotlinx.coroutines.launch

/**
 * 3-Step Wizard for creating a new Dream.
 *
 * Step 1: Basic Info (title, description, icon, color)
 * Step 2: Initial Milestones (optional, 1-3 milestones)
 * Step 3: Summary & Create
 */
class CreateDreamActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CreateDreamActivity"

        // Preset colors for dream customization
        val PRESET_COLORS = listOf(
            0xFF6200EE.toInt(),  // Purple
            0xFF03DAC5.toInt(),  // Teal
            0xFFFF5722.toInt(),  // Deep Orange
            0xFF4CAF50.toInt(),  // Green
            0xFF2196F3.toInt(),  // Blue
            0xFFE91E63.toInt(),  // Pink
            0xFFFF9800.toInt(),  // Orange
            0xFF9C27B0.toInt()   // Deep Purple
        )
    }

    // Current wizard step (0-2)
    private var currentStep = 0

    // Data collected during wizard
    private var dreamTitle = ""
    private var dreamDescription: String? = null
    private var selectedColor = PRESET_COLORS[0]
    private var selectedIconName: String? = null
    private val pendingMilestones = mutableListOf<MilestoneData>()

    // Views
    private lateinit var stepIndicator: TextView
    private lateinit var stepContainer: FrameLayout
    private lateinit var prevButton: Button
    private lateinit var nextButton: Button

    // Use cases
    private lateinit var createDreamUseCase: CreateDreamUseCase
    private lateinit var createMilestoneUseCase: CreateMilestoneUseCase

    data class MilestoneData(
        var title: String,
        var description: String? = null,
        var targetXp: Long = 1000
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_dream)

        setupDependencies()
        setupViews()
        showStep(0)
    }

    private fun setupDependencies() {
        val database = TaskDatabase.getDatabase(this)
        val dreamRepository = DreamRepositoryImpl(database.dreamDao())
        val milestoneRepository = MilestoneRepositoryImpl(database.milestoneDao())

        createDreamUseCase = CreateDreamUseCase(dreamRepository)
        createMilestoneUseCase = CreateMilestoneUseCase(milestoneRepository, dreamRepository)
    }

    private fun setupViews() {
        stepIndicator = findViewById(R.id.stepIndicator)
        stepContainer = findViewById(R.id.stepContainer)
        prevButton = findViewById(R.id.prevButton)
        nextButton = findViewById(R.id.nextButton)

        prevButton.setOnClickListener {
            if (currentStep > 0) {
                showStep(currentStep - 1)
            } else {
                finish()
            }
        }

        nextButton.setOnClickListener {
            if (validateCurrentStep()) {
                saveCurrentStepData()
                if (currentStep < 2) {
                    showStep(currentStep + 1)
                } else {
                    createDream()
                }
            }
        }
    }

    private fun showStep(step: Int) {
        currentStep = step
        stepIndicator.text = "Step ${step + 1} of 3"

        // Update buttons
        prevButton.text = if (step == 0) "Cancel" else "Back"
        nextButton.text = if (step == 2) "Create Dream" else "Next"

        // Inflate step layout
        stepContainer.removeAllViews()
        val stepLayout = when (step) {
            0 -> layoutInflater.inflate(R.layout.wizard_step_basic, stepContainer, false)
            1 -> layoutInflater.inflate(R.layout.wizard_step_milestones, stepContainer, false)
            2 -> layoutInflater.inflate(R.layout.wizard_step_summary, stepContainer, false)
            else -> return
        }
        stepContainer.addView(stepLayout)

        // Populate step
        when (step) {
            0 -> populateBasicStep(stepLayout)
            1 -> populateMilestonesStep(stepLayout)
            2 -> populateSummaryStep(stepLayout)
        }
    }

    private fun populateBasicStep(view: View) {
        val titleInput = view.findViewById<EditText>(R.id.dreamTitleInput)
        val descriptionInput = view.findViewById<EditText>(R.id.dreamDescriptionInput)
        val colorGrid = view.findViewById<GridLayout>(R.id.colorGrid)

        // Restore previous data
        titleInput.setText(dreamTitle)
        descriptionInput.setText(dreamDescription ?: "")

        // Setup color selection
        colorGrid.removeAllViews()
        PRESET_COLORS.forEachIndexed { index, color ->
            val colorView = View(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 80
                    height = 80
                    setMargins(8, 8, 8, 8)
                }
                setBackgroundColor(color)
                setOnClickListener {
                    selectedColor = color
                    updateColorSelection(colorGrid, index)
                }
            }
            colorGrid.addView(colorView)
        }

        // Pre-select first color
        updateColorSelection(colorGrid, PRESET_COLORS.indexOf(selectedColor).takeIf { it >= 0 } ?: 0)
    }

    private fun updateColorSelection(grid: GridLayout, selectedIndex: Int) {
        for (i in 0 until grid.childCount) {
            val child = grid.getChildAt(i)
            child.alpha = if (i == selectedIndex) 1.0f else 0.5f
        }
    }

    private fun populateMilestonesStep(view: View) {
        val milestonesContainer = view.findViewById<LinearLayout>(R.id.milestonesContainer)
        val addMilestoneButton = view.findViewById<Button>(R.id.addMilestoneButton)

        // Show existing milestones
        refreshMilestonesList(milestonesContainer)

        addMilestoneButton.setOnClickListener {
            if (pendingMilestones.size < 3) {
                showAddMilestoneDialog { milestone ->
                    pendingMilestones.add(milestone)
                    refreshMilestonesList(milestonesContainer)
                }
            } else {
                Toast.makeText(this, "Maximum 3 milestones in wizard", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshMilestonesList(container: LinearLayout) {
        container.removeAllViews()
        pendingMilestones.forEachIndexed { index, milestone ->
            val itemView = layoutInflater.inflate(android.R.layout.simple_list_item_2, container, false)
            itemView.findViewById<TextView>(android.R.id.text1).text = milestone.title
            itemView.findViewById<TextView>(android.R.id.text2).text = "${milestone.targetXp} XP target"
            itemView.setOnLongClickListener {
                pendingMilestones.removeAt(index)
                refreshMilestonesList(container)
                true
            }
            container.addView(itemView)
        }

        if (pendingMilestones.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "No milestones yet. Tap + to add."
                setPadding(16, 16, 16, 16)
            }
            container.addView(emptyText)
        }
    }

    private fun showAddMilestoneDialog(onAdd: (MilestoneData) -> Unit) {
        val titleInput = EditText(this).apply {
            hint = "Milestone title"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val xpInput = EditText(this).apply {
            hint = "Target XP (e.g., 1000)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("1000")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
            addView(titleInput)
            addView(xpInput)
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Add Milestone")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val title = titleInput.text.toString().trim()
                val xp = xpInput.text.toString().toLongOrNull() ?: 1000
                if (title.isNotBlank()) {
                    onAdd(MilestoneData(title, null, xp))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun populateSummaryStep(view: View) {
        val summaryTitle = view.findViewById<TextView>(R.id.summaryTitle)
        val summaryDescription = view.findViewById<TextView>(R.id.summaryDescription)
        val summaryMilestones = view.findViewById<TextView>(R.id.summaryMilestones)
        val summaryColorPreview = view.findViewById<View>(R.id.summaryColorPreview)

        summaryTitle.text = dreamTitle
        summaryDescription.text = dreamDescription ?: "(No description)"
        summaryColorPreview.setBackgroundColor(selectedColor)

        val milestonesText = if (pendingMilestones.isEmpty()) {
            "No initial milestones (can add later)"
        } else {
            pendingMilestones.joinToString("\n") { "• ${it.title} (${it.targetXp} XP)" }
        }
        summaryMilestones.text = milestonesText
    }

    private fun validateCurrentStep(): Boolean {
        return when (currentStep) {
            0 -> {
                val titleInput = stepContainer.findViewById<EditText>(R.id.dreamTitleInput)
                val title = titleInput?.text?.toString()?.trim() ?: ""
                if (title.isBlank()) {
                    Toast.makeText(this, "Please enter a dream title", Toast.LENGTH_SHORT).show()
                    false
                } else true
            }
            else -> true
        }
    }

    private fun saveCurrentStepData() {
        when (currentStep) {
            0 -> {
                dreamTitle = stepContainer.findViewById<EditText>(R.id.dreamTitleInput)?.text?.toString()?.trim() ?: ""
                dreamDescription = stepContainer.findViewById<EditText>(R.id.dreamDescriptionInput)?.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            }
        }
    }

    private fun createDream() {
        lifecycleScope.launch {
            try {
                // Create dream
                val result = createDreamUseCase(
                    title = dreamTitle,
                    description = dreamDescription,
                    iconName = selectedIconName,
                    color = selectedColor
                )

                result.onSuccess { dreamId ->
                    // Create milestones
                    pendingMilestones.forEach { milestone ->
                        createMilestoneUseCase(
                            dreamId = dreamId,
                            title = milestone.title,
                            description = milestone.description,
                            targetXp = milestone.targetXp
                        )
                    }

                    Toast.makeText(this@CreateDreamActivity, "Dream created!", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }.onFailure { error ->
                    Toast.makeText(this@CreateDreamActivity, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@CreateDreamActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
