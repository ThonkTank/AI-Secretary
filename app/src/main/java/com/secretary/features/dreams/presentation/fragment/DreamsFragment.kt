package com.secretary.features.dreams.presentation.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.secretary.R
import com.secretary.features.dreams.presentation.adapter.DreamListAdapter
import com.secretary.features.dreams.presentation.viewmodel.DreamsViewModel
import com.secretary.features.dreams.presentation.viewmodel.DreamsViewModelFactory
import com.secretary.shared.database.TaskDatabase

/**
 * Fragment displaying list of user's dreams with level indicators.
 * Shows dreams as cards with progress bars and level badges.
 */
class DreamsFragment : Fragment() {

    private lateinit var viewModel: DreamsViewModel
    private lateinit var adapter: DreamListAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var fabAddDream: FloatingActionButton

    companion object {
        fun newInstance() = DreamsFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dreams, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews(view)
        setupViewModel()
        setupAdapter()
        observeData()
    }

    private fun setupViews(view: View) {
        recyclerView = view.findViewById(R.id.dreamsRecyclerView)
        emptyStateText = view.findViewById(R.id.emptyStateText)
        fabAddDream = view.findViewById(R.id.fabAddDream)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        fabAddDream.setOnClickListener {
            // TODO: Open CreateDreamActivity (Wave 4)
            // For now, show toast
            android.widget.Toast.makeText(context, "Create Dream - Coming in Wave 4", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupViewModel() {
        val database = TaskDatabase.getDatabase(requireContext())
        val factory = DreamsViewModelFactory(database.dreamDao(), database.milestoneDao())
        viewModel = ViewModelProvider(this, factory)[DreamsViewModel::class.java]
    }

    private fun setupAdapter() {
        adapter = DreamListAdapter { dream ->
            // Click handler - open dream detail
            // TODO: Navigate to DreamDetailFragment (Wave 4)
            android.widget.Toast.makeText(context, "Dream: ${dream.title} - Detail coming in Wave 4", android.widget.Toast.LENGTH_SHORT).show()
        }
        recyclerView.adapter = adapter
    }

    private fun observeData() {
        viewModel.dreams.observe(viewLifecycleOwner) { dreams ->
            adapter.submitList(dreams)

            if (dreams.isEmpty()) {
                emptyStateText.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                emptyStateText.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadDreams()
    }
}
