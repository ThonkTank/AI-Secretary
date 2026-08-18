package de.thonktank.autosecretary;

import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import de.thonktank.autosecretary.domain.model.RewardReceipt;

/** Owns reward flight rendering; the Activity only supplies state and acknowledgement. */
public final class RewardAnimator {
    private final FrameLayout root;
    private final HeaderView header;
    private final RewardAnchorRegistry anchors;
    private String activeId;

    public RewardAnimator(FrameLayout root, HeaderView header, RewardAnchorRegistry anchors) {
        this.root = root; this.header = header; this.anchors = anchors;
    }

    public void play(RewardEffect effect, DayPalette palette, int topInset,
                     Runnable acknowledged) {
        if (effect == null || effect.id.equals(activeId)) return;
        activeId = effect.id;
        boolean head = effect.target == RewardReceipt.Target.HEAD;
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) {
            if (effect.signedXp > 0 && head) header.playRewardGlint(palette);
            finish(acknowledged);
            return;
        }
        UiStyle style = new UiStyle(root.getContext());
        TextView value = style.serif(effect.signedXp + " XP", 17, palette.light, true, 300);
        value.setSingleLine(true);
        root.addView(value, new FrameLayout.LayoutParams(-2, -2));
        View sourceView = anchors.find(effect.source);
        View targetView = head ? anchors.find(RewardAnchorKey.head())
                : anchors.firstVisible(RewardAnchorKey.Kind.VESSEL);
        float[] source = center(sourceView, head ? root.getWidth() * .77f
                : root.getWidth() * .30f, head ? topInset + style.dp(180)
                : topInset + style.dp(245));
        float[] target = center(targetView, head ? root.getWidth() * .67f
                : root.getWidth() * .77f, head ? topInset + style.dp(46)
                : topInset + style.dp(180));
        float fromX = source[0], fromY = source[1], toX = target[0], toY = target[1];
        if (effect.signedXp < 0) {
            float swapX = fromX; fromX = toX; toX = swapX;
            float swapY = fromY; fromY = toY; toY = swapY;
        }
        value.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        float halfWidth = value.getMeasuredWidth() / 2f;
        float halfHeight = value.getMeasuredHeight() / 2f;
        value.setX(fromX - halfWidth); value.setY(fromY - halfHeight);
        value.setScaleX(.92f); value.setScaleY(.92f);
        value.animate().x(toX - halfWidth).y(toY - halfHeight).alpha(0f)
                .scaleX(.7f).scaleY(.7f).setDuration(palette.motion.rewardFlightDurationMs)
                .setInterpolator(new android.view.animation.PathInterpolator(.2f, .7f, .3f, 1f))
                .withEndAction(() -> {
                    root.removeView(value);
                    if (effect.signedXp > 0 && head) header.playRewardGlint(palette);
                    finish(acknowledged);
                });
    }

    private void finish(Runnable acknowledged) {
        activeId = null;
        acknowledged.run();
    }

    private float[] center(View view, float fallbackX, float fallbackY) {
        if (view == null || view.getWidth() == 0 || view.getHeight() == 0)
            return new float[]{fallbackX, fallbackY};
        int[] rootLocation = new int[2]; int[] viewLocation = new int[2];
        root.getLocationOnScreen(rootLocation); view.getLocationOnScreen(viewLocation);
        return new float[]{viewLocation[0] - rootLocation[0] + view.getWidth() / 2f,
                viewLocation[1] - rootLocation[1] + view.getHeight() / 2f};
    }
}
