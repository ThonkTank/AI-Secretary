package com.autosecretary.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.autosecretary.presentation.R;
import com.autosecretary.presentation.databinding.ActivityMainBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Owns destination view selection and its leaf transition; state remains in MainViewModel. */
public final class NavigationController {
    private final ActivityMainBinding binding;
    private final Consumer<Surface> selection;
    private Surface current = Surface.TODAY;

    public NavigationController(ActivityMainBinding binding, Consumer<Surface> selection) {
        this.binding = binding;
        this.selection = selection;
        binding.NavToday.setOnClickListener(view -> navigate(Surface.TODAY));
        binding.NavAll.setOnClickListener(view -> navigate(Surface.ALL));
        binding.NavAi.setOnClickListener(view -> navigate(Surface.AI));
    }

    public void render(Surface surface) {
        current = surface;
        boolean today = surface == Surface.TODAY;
        boolean all = surface == Surface.ALL;
        binding.TodayPanel.setVisibility(today ? View.VISIBLE : View.GONE);
        binding.AllPanel.setVisibility(all ? View.VISIBLE : View.GONE);
        binding.AiPanel.setVisibility(surface == Surface.AI ? View.VISIBLE : View.GONE);
        setSelected(binding.NavToday, today);
        setSelected(binding.NavAll, all);
        setSelected(binding.NavAi, surface == Surface.AI);
    }

    private void navigate(Surface target) {
        if (target == current) return;
        if (current == Surface.TODAY && target != Surface.TODAY) {
            animateLeavesOut(() -> selection.accept(target));
        } else {
            selection.accept(target);
        }
    }

    private void animateLeavesOut(Runnable after) {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            after.run();
            return;
        }
        List<View> leaves = new ArrayList<>();
        appendChildren(leaves, current == Surface.ALL
                ? binding.ObligationList : binding.FocusList, 5);
        int count = leaves.size();
        if (count == 0) {
            after.run();
            return;
        }
        for (int index = 0; index < count; index++) {
            View leaf = leaves.get(index);
            leaf.animate().translationX(dp(72 + index * 7))
                    .translationY(dp(110 + index * 15))
                    .alpha(.18f).setStartDelay(index * 38L).setDuration(420L)
                    .setInterpolator(new AccelerateInterpolator())
                    .setListener(index == count - 1 ? new AnimatorListenerAdapter() {
                        @Override public void onAnimationEnd(Animator animation) {
                            for (View moved : leaves) {
                                moved.animate().setListener(null);
                                moved.setTranslationX(0);
                                moved.setTranslationY(0);
                                moved.setAlpha(1f);
                            }
                            after.run();
                        }
                    } : null).start();
        }
    }

    private static void appendChildren(List<View> result, ViewGroup parent, int maximum) {
        for (int index = 0; index < parent.getChildCount() && result.size() < maximum; index++) {
            result.add(parent.getChildAt(index));
        }
    }

    private void setSelected(TextView view, boolean selected) {
        view.setTextColor(ContextCompat.getColor(view.getContext(),
                selected ? R.color.ink_secondary : R.color.marker));
        view.setPaintFlags(selected
                ? view.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG
                : view.getPaintFlags() & ~Paint.UNDERLINE_TEXT_FLAG);
    }

    private int dp(int value) {
        return Math.round(value * binding.getRoot().getResources().getDisplayMetrics().density);
    }
}
