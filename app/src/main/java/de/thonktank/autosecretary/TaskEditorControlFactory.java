package de.thonktank.autosecretary;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared construction and styling for controls used on both task-editor surfaces. */
final class TaskEditorControlFactory {
    interface StringListener { void accept(String value); }
    interface IntegerListener { void accept(Integer value); }
    interface IntListener { void accept(int value); }

    private final Context context;
    private final UiStyle style;
    private final DayPalette palette;

    TaskEditorControlFactory(Context context, UiStyle style, DayPalette palette) {
        this.context = context;
        this.style = style;
        this.palette = palette;
    }

    EditorFlowLayout flow() { return new EditorFlowLayout(context); }

    EditText input(int hint, String value, boolean multiline, float size, boolean serif,
                   StringListener listener) {
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setText(value);
        input.setTextSize(size);
        input.setTextColor(palette.ink);
        input.setHintTextColor(palette.dot);
        input.setTypeface(serif ? style.serif : style.sans);
        input.setPadding(0, style.dp(2), 0, style.dp(6));
        input.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        input.setSingleLine(!multiline);
        input.setSelection(input.length());
        input.addTextChangedListener(watcher(listener));
        return input;
    }

    EditText numberField(Integer value, IntegerListener listener) {
        EditText input = new EditText(context);
        input.setText(value == null || value <= 0 ? "" : String.valueOf(value));
        input.setTextSize(23);
        input.setTypeface(style.serif);
        input.setTextColor(palette.ink);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setBackgroundTintList(ColorStateList.valueOf(palette.accent));
        input.addTextChangedListener(watcher(text -> listener.accept(parseInteger(text))));
        return input;
    }

    LinearLayout numberInput(Integer value, int unit, IntegerListener listener,
                             String focusTag) {
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        EditText number = numberField(value, listener);
        number.setTag(focusTag);
        wrapper.addView(number, new LinearLayout.LayoutParams(-1, style.dp(45)));
        wrapper.addView(style.sans(context.getString(unit), 14, palette.hint, false));
        return wrapper;
    }

    LinearLayout dayPicker(int mask, IntListener listener) {
        LinearLayout row = new LinearLayout(context);
        int[] labels = {R.string.day_mon, R.string.day_tue, R.string.day_wed,
                R.string.day_thu, R.string.day_fri, R.string.day_sat, R.string.day_sun};
        for (int index = 0; index < labels.length; index++) {
            final int bit = 1 << index;
            boolean selected = (mask & bit) != 0;
            TextView day = style.sans(context.getString(labels[index]), 14,
                    selected ? palette.accentText : palette.ink, selected);
            day.setGravity(Gravity.CENTER);
            day.setMinHeight(style.dp(48));
            GradientDrawable background = style.pill(selected ? palette.accent
                    : Color.TRANSPARENT, 19);
            if (!selected) background.setStroke(style.dp(1), palette.dot);
            day.setBackground(background);
            day.setOnClickListener(view -> listener.accept(mask ^ bit));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, style.dp(48), 1);
            if (index > 0) params.setMargins(style.dp(8), 0, 0, 0);
            row.addView(day, params);
        }
        return row;
    }

    void addChip(EditorFlowLayout row, int label, boolean selected, Runnable action) {
        String text = context.getString(label);
        TextView chip = style.sans(text, 15, selected ? palette.accentText : palette.ink, selected);
        chip.setGravity(Gravity.CENTER);
        chip.setMinHeight(style.dp(48));
        chip.setPadding(style.dp(16), 0, style.dp(16), 0);
        GradientDrawable background = style.pill(selected ? palette.accent
                : Color.TRANSPARENT, 24);
        if (!selected) background.setStroke(style.dp(1), palette.dot);
        chip.setBackground(background);
        chip.setOnClickListener(view -> action.run());
        chip.setContentDescription(context.getString(selected
                ? R.string.a11y_editor_chip_selected : R.string.a11y_editor_chip, text));
        row.addView(chip, new ViewGroup.LayoutParams(-2, style.dp(48)));
    }

    TextView errorView(int resource) { return errorView(context.getString(resource)); }

    TextView errorView(String value) {
        TextView error = style.serif(value, 14, palette.bad, true, 300);
        error.setPadding(style.dp(12), style.dp(9), style.dp(12), style.dp(9));
        GradientDrawable background = style.pill(UiStyle.alpha(palette.bad, .10f), 10);
        background.setStroke(style.dp(1), UiStyle.alpha(palette.bad, .34f));
        error.setBackground(background);
        error.setLayoutParams(params(-1, -2, 0, 7, 0, 0));
        return error;
    }

    static Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try { return Integer.parseInt(value.trim()); }
        catch (NumberFormatException error) { return 0; }
    }

    private static TextWatcher watcher(StringListener listener) {
        return new TextWatcher() {
            private int selection;
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                selection = start + count;
            }
            @Override public void afterTextChanged(Editable s) {
                Selection.setSelection(s, Math.max(0, Math.min(selection, s.length())));
                listener.accept(s.toString());
            }
        };
    }

    private static LinearLayout.LayoutParams params(int width, int height, int left, int top,
                                                     int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(left, top, right, bottom);
        return params;
    }
}
