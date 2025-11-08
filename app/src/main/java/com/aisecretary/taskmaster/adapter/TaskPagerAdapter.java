package com.aisecretary.taskmaster.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.aisecretary.taskmaster.fragments.TaskBasisFragment;
import com.aisecretary.taskmaster.fragments.TaskDetailsFragment;
import com.aisecretary.taskmaster.fragments.TaskRecurrenceFragment;

/**
 * TaskPagerAdapter - ViewPager2 Adapter for AddTask Tabs
 *
 * Manages the three tab fragments: Basis, Wiederholung, Details
 */
public class TaskPagerAdapter extends FragmentStateAdapter {

    private TaskBasisFragment basisFragment;
    private TaskRecurrenceFragment recurrenceFragment;
    private TaskDetailsFragment detailsFragment;

    public TaskPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);

        // Create fragment instances
        basisFragment = new TaskBasisFragment();
        recurrenceFragment = new TaskRecurrenceFragment();
        detailsFragment = new TaskDetailsFragment();
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: return basisFragment;
            case 1: return recurrenceFragment;
            case 2: return detailsFragment;
            default: return basisFragment;
        }
    }

    @Override
    public int getItemCount() {
        return 3; // Three tabs
    }

    // Public getters for fragment instances (to access data)
    public TaskBasisFragment getBasisFragment() {
        return basisFragment;
    }

    public TaskRecurrenceFragment getRecurrenceFragment() {
        return recurrenceFragment;
    }

    public TaskDetailsFragment getDetailsFragment() {
        return detailsFragment;
    }
}
