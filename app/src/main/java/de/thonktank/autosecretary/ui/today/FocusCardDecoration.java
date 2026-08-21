package de.thonktank.autosecretary.ui.today;

import de.thonktank.autosecretary.*;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;
import de.thonktank.autosecretary.ui.leaf.LeafShape;
import de.thonktank.autosecretary.ui.leaf.LeafSurface;

/** Owns the paper stack, surface and grain layers around focus-card content. */
final class FocusCardDecoration {
    private final UiStyle style;
    private final RewardAnchorRegistry rewardAnchors;
    private final View back;
    private final View middle;
    private final LeafSurface surface;

    FocusCardDecoration(Context context, FrameLayout root,
                        RewardAnchorRegistry rewardAnchors, FocusCardView card) {
        style = new UiStyle(context);
        this.rewardAnchors = rewardAnchors;
        back = new View(context);
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(-1,
                style.dimen(R.dimen.focus_stack_back_height));
        backParams.setMargins(style.dp(18), style.dp(34), style.dp(4), 0);
        root.addView(back, backParams);
        middle = new View(context);
        FrameLayout.LayoutParams middleParams = new FrameLayout.LayoutParams(-1,
                style.dimen(R.dimen.focus_stack_middle_height));
        middleParams.setMargins(style.dp(8), style.dp(18), style.dp(12), 0);
        root.addView(middle, middleParams);
        surface = new LeafSurface(context, new LeafShape(10, 64, 10, 64));
        surface.setRotation(-.7f);
        surface.front().addView(card, new FrameLayout.LayoutParams(-1, -2));
        root.addView(surface, new FrameLayout.LayoutParams(-1, -2));
    }

    void bind(FocusTaskUiModel task, boolean stacked, boolean compact, DayPalette palette,
              FocusCardView card) {
        card.registerRewardAnchors(rewardAnchors, task);
        back.setVisibility(stacked && !compact ? View.VISIBLE : View.GONE);
        middle.setVisibility(stacked && !compact ? View.VISIBLE : View.GONE);
        back.setBackground(style.leaf(palette.leaf3, style.edge(palette, 3), 8, 56, 8, 56));
        back.setRotation(2.2f);
        style.shadow(back, palette, 5, .75f);
        middle.setBackground(style.leaf(palette.leaf2, style.edge(palette, 2), 56, 8, 56, 8));
        middle.setRotation(-1.5f);
        style.shadow(middle, palette, 5, .75f);
        surface.bindSurface(palette, palette.leaf1, style.edge(palette, 1), 12, 1f);
        surface.setGrainSpec(card.grainSpec(task));
    }

    LeafSurface surface() { return surface; }
}
