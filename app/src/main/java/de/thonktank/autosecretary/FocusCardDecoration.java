package de.thonktank.autosecretary;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import de.thonktank.autosecretary.presentation.today.FocusTaskUiModel;

/** Owns the paper stack, surface and grain layers around focus-card content. */
final class FocusCardDecoration {
    private final UiStyle style;
    private final RewardAnchorRegistry rewardAnchors;
    private final View back;
    private final View middle;
    private final View surface;
    private final WoodGrainView grain;
    private FocusTaskUiModel task;
    private DayPalette palette;

    FocusCardDecoration(Context context, FrameLayout root,
                        RewardAnchorRegistry rewardAnchors) {
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
        surface = new View(context);
        root.addView(surface, new FrameLayout.LayoutParams(0, 0));
        grain = new WoodGrainView(context);
        grain.setLeafClip(10, 64, 10, 64);
        root.addView(grain, new FrameLayout.LayoutParams(0, 0));
    }

    void bind(FocusTaskUiModel task, boolean stacked, boolean compact, DayPalette palette,
              FocusCardView card) {
        this.task = task;
        this.palette = palette;
        card.registerRewardAnchors(rewardAnchors, task);
        back.setVisibility(stacked && !compact ? View.VISIBLE : View.GONE);
        middle.setVisibility(stacked && !compact ? View.VISIBLE : View.GONE);
        back.setBackground(style.leaf(palette.leaf3, style.edge(palette, 3), 8, 56, 8, 56));
        back.setRotation(2.2f);
        style.shadow(back, palette, 5, .75f);
        middle.setBackground(style.leaf(palette.leaf2, style.edge(palette, 2), 56, 8, 56, 8));
        middle.setRotation(-1.5f);
        style.shadow(middle, palette, 5, .75f);
        surface.setBackground(style.leaf(palette.leaf1, style.edge(palette, 1),
                10, 64, 10, 64));
        surface.setRotation(card.getRotation());
        style.shadow(surface, palette, 12, 1f);
        grain.setTranslationZ(style.dp(13));
        grain.setRotation(card.getRotation());
        card.setTranslationZ(style.dp(14));
    }

    void layoutTo(FocusCardView card) {
        int top = card.getTop();
        int bottom = card.getBottom();
        surface.layout(0, top, card.getWidth(), bottom);
        grain.layout(0, top, card.getWidth(), bottom);
        if (task == null || palette == null || card.getWidth() <= 0 || card.getHeight() <= 0)
            return;
        grain.bind(palette, card.grainAnchors(grain, task),
                WoodGrainCoordinates.visibleBounds(grain, card.grainTextViews()));
    }

    WoodGrainView grain() { return grain; }
}
