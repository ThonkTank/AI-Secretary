package de.thonktank.autosecretary;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import de.thonktank.autosecretary.domain.model.FlowDelayPolicy;

/** Small integer duration prompt shared by flow completion and run adjustment. */
public final class FlowDurationDialog {
    public interface Listener { void selected(long delayMillis); }

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60L * MINUTE;
    private static final long DAY = 24L * HOUR;

    public static void show(Context context, String title, long proposedMillis,
                            Listener listener) {
        Unit initial = Unit.bestFor(proposedMillis);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(24 * context.getResources().getDisplayMetrics().density);
        content.setPadding(padding, 0, padding, 0);
        TextView hint = new TextView(context);
        hint.setText(R.string.flow_delay_prompt_hint);
        hint.setTextSize(16);
        content.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        EditText value = new EditText(context);
        value.setInputType(InputType.TYPE_CLASS_NUMBER);
        value.setSingleLine(true);
        value.setText(String.valueOf(initial.value(proposedMillis)));
        value.setSelectAllOnFocus(true);
        row.addView(value, new LinearLayout.LayoutParams(0, -2, 1));
        Spinner unit = new Spinner(context);
        ArrayAdapter<String> units = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{context.getString(R.string.flow_unit_minutes),
                        context.getString(R.string.flow_unit_hours),
                        context.getString(R.string.flow_unit_days)});
        unit.setAdapter(units);
        unit.setSelection(initial.ordinal());
        row.addView(unit, new LinearLayout.LayoutParams(-2, -2));
        content.addView(row, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.flow_delay_confirm, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    Long parsed = parse(value.getText().toString(), Unit.values()[
                            unit.getSelectedItemPosition()]);
                    if (parsed == null) {
                        value.setError(context.getString(R.string.flow_delay_invalid));
                        return;
                    }
                    dialog.dismiss();
                    listener.selected(parsed);
                }));
        dialog.show();
    }

    static Long parse(String raw, Unit unit) {
        try {
            long value = Long.parseLong(raw.trim());
            if (value < 0L || value > FlowDelayPolicy.MAX_DELAY_MILLIS / unit.millis)
                return null;
            return value * unit.millis;
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    enum Unit {
        MINUTES(MINUTE), HOURS(HOUR), DAYS(DAY);

        final long millis;
        Unit(long millis) { this.millis = millis; }

        long value(long durationMillis) {
            return durationMillis == 0L ? 0L : (durationMillis + millis - 1L) / millis;
        }

        static Unit bestFor(long durationMillis) {
            if (durationMillis >= DAY && durationMillis % DAY == 0L) return DAYS;
            if (durationMillis >= HOUR && durationMillis % HOUR == 0L) return HOURS;
            return MINUTES;
        }
    }

    private FlowDurationDialog() { }
}
