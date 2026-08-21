package de.thonktank.autosecretary.ui.today;

import de.thonktank.autosecretary.*;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.TextView;

/** Sentence-case text action with the design-system's independent underline color. */
public final class TextLinkView extends TextView {
    private final Paint underline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final UiStyle style;

    TextLinkView(Context context) {
        super(context);
        style = new UiStyle(context);
        setTypeface(style.sans);
        setTextSize(17);
        setGravity(android.view.Gravity.CENTER);
        setIncludeFontPadding(false);
        setMinWidth(style.dp(48));
        setMinHeight(style.dp(48));
        underline.setStrokeWidth(style.dp(1));
    }

    void bind(int textColor, int underlineColor) {
        setTextColor(textColor);
        underline.setColor(underlineColor);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getPaint().measureText(getText().toString());
        float left = (getWidth() - width) * .5f;
        float baseline = getHeight() * .5f - (getPaint().ascent() + getPaint().descent()) * .5f;
        canvas.drawLine(left, baseline + style.dp(4), left + width, baseline + style.dp(4), underline);
    }
}
