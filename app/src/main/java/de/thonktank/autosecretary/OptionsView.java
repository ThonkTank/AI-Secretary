package de.thonktank.autosecretary;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import de.thonktank.autosecretary.data.preferences.UiThemeMode;
import de.thonktank.autosecretary.data.preferences.FocusStepLimit;
import de.thonktank.autosecretary.presentation.options.OptionsAction;
import de.thonktank.autosecretary.presentation.options.OptionsActionSink;
import de.thonktank.autosecretary.presentation.options.OptionsScreenState;
import de.thonktank.autosecretary.update.presentation.UpdateUiState;
import de.thonktank.autosecretary.domain.model.ComboDecayTrigger;
import de.thonktank.autosecretary.domain.model.ComboPolicy;

@SuppressLint("ViewConstructor")
public final class OptionsView extends LinearLayout {
    private final UiStyle style;
    private final TextView heading;
    private final OptionLeaf appearance;
    private final OptionLeaf focusSteps;
    private final OptionLeaf restTimers;
    private final OptionLeaf combos;
    private final OptionLeaf flows;
    private final OptionLeaf calendar;
    private final OptionLeaf updates;
    private final TextView[] themeButtons = new TextView[3];
    private final TextView[] focusStepButtons = new TextView[FocusStepLimit.values().length];
    private final TextView calendarButton;
    private final TextView restTimerLess;
    private final TextView restTimerValue;
    private final TextView restTimerMore;
    private final TextView comboGainLess;
    private final TextView comboGainValue;
    private final TextView comboGainMore;
    private final TextView comboDecayLess;
    private final TextView comboDecayValue;
    private final TextView comboDecayMore;
    private final TextView[] comboTriggerButtons = new TextView[ComboDecayTrigger.values().length];
    private final TextView flowSetupButton;
    private final TextView flowRunsButton;
    private final TextView updateButton;
    private final OptionsActionSink actions;

    public OptionsView(Context context, OptionsActionSink actions) {
        super(context);
        this.actions = actions;
        style = new UiStyle(context);
        setOrientation(VERTICAL);
        setPadding(style.dp(60), style.dp(18), style.dp(22), style.dp(26));
        heading = style.serif(context.getString(R.string.options_title), 20, 0, true, 300);
        addView(heading);
        LinearLayout themes = new LinearLayout(context);
        String[] labels = {context.getString(R.string.theme_auto), context.getString(R.string.theme_light),
                context.getString(R.string.theme_dark)};
        UiThemeMode[] modes = UiThemeMode.values();
        for (int i = 0; i < themeButtons.length; i++) {
            TextView button = style.sans(labels[i], 15, 0, true);
            button.setGravity(Gravity.CENTER);
            button.setMinHeight(style.dp(48));
            button.setPadding(style.dp(14), 0, style.dp(14), 0);
            UiThemeMode mode = modes[i];
            button.setOnClickListener(view ->
                    actions.emit(OptionsAction.themeSelected(mode)));
            LayoutParams params = new LayoutParams(-2, style.dp(48));
            params.setMargins(0, 0, style.dp(8), 0);
            themes.addView(button, params);
            themeButtons[i] = button;
        }
        appearance = new OptionLeaf(context, R.string.options_appearance,
                R.string.options_appearance_description, themes);
        addLeaf(appearance);

        EditorFlowLayout focusLimits = new EditorFlowLayout(context);
        FocusStepLimit[] limits = FocusStepLimit.values();
        for (int i = 0; i < limits.length; i++) {
            FocusStepLimit limit = limits[i];
            String label = limit.automatic() ? context.getString(R.string.focus_steps_auto)
                    : String.valueOf(limit.maximumFollowingSteps);
            TextView button = style.sans(label, 15, 0, true);
            button.setGravity(Gravity.CENTER);
            button.setMinWidth(style.dp(48));
            button.setMinHeight(style.dp(48));
            button.setPadding(style.dp(14), 0, style.dp(14), 0);
            button.setTag(limit);
            AccessibilityRoles.button(button);
            button.setOnClickListener(view ->
                    actions.emit(OptionsAction.focusStepLimitSelected(limit)));
            focusLimits.addView(button, new android.view.ViewGroup.LayoutParams(
                    -2, style.dp(48)));
            focusStepButtons[i] = button;
        }
        focusSteps = new OptionLeaf(context, R.string.options_focus_steps,
                R.string.options_focus_steps_description, focusLimits);
        addLeaf(focusSteps);

        LinearLayout restTimerActions = new LinearLayout(context);
        restTimerActions.setGravity(Gravity.CENTER_VERTICAL);
        restTimerLess = outlineButton(context.getString(R.string.timer_less));
        restTimerLess.setContentDescription(context.getString(R.string.timer_less_description));
        restTimerValue = style.sans("60 s", 17, 0, true);
        restTimerValue.setGravity(Gravity.CENTER);
        restTimerValue.setMinWidth(style.dp(96));
        restTimerMore = outlineButton(context.getString(R.string.timer_more));
        restTimerMore.setContentDescription(context.getString(R.string.timer_more_description));
        restTimerActions.addView(restTimerLess, new LayoutParams(style.dp(56), style.dp(48)));
        restTimerActions.addView(restTimerValue, new LayoutParams(style.dp(96), style.dp(48)));
        restTimerActions.addView(restTimerMore, new LayoutParams(style.dp(56), style.dp(48)));
        restTimers = new OptionLeaf(context, R.string.options_rest_timer,
                R.string.options_rest_timer_description, restTimerActions);
        addLeaf(restTimers);

        LinearLayout comboActions = new LinearLayout(context);
        comboActions.setOrientation(VERTICAL);
        LinearLayout gainRow = new LinearLayout(context);
        gainRow.setGravity(Gravity.CENTER_VERTICAL);
        comboGainLess = outlineButton("−");
        comboGainValue = style.sans("+2", 17, 0, true);
        comboGainValue.setGravity(Gravity.CENTER);
        comboGainMore = outlineButton("+");
        gainRow.addView(style.sans(context.getString(R.string.combo_gain), 15, 0, true),
                new LayoutParams(0, -2, 1));
        gainRow.addView(comboGainLess, new LayoutParams(style.dp(48), style.dp(48)));
        gainRow.addView(comboGainValue, new LayoutParams(style.dp(58), style.dp(48)));
        gainRow.addView(comboGainMore, new LayoutParams(style.dp(48), style.dp(48)));
        comboActions.addView(gainRow, new LayoutParams(-1, -2));
        LinearLayout decayRow = new LinearLayout(context);
        decayRow.setGravity(Gravity.CENTER_VERTICAL);
        comboDecayLess = outlineButton("−");
        comboDecayValue = style.sans("−1", 17, 0, true);
        comboDecayValue.setGravity(Gravity.CENTER);
        comboDecayMore = outlineButton("+");
        decayRow.addView(style.sans(context.getString(R.string.combo_decay), 15, 0, true),
                new LayoutParams(0, -2, 1));
        decayRow.addView(comboDecayLess, new LayoutParams(style.dp(48), style.dp(48)));
        decayRow.addView(comboDecayValue, new LayoutParams(style.dp(58), style.dp(48)));
        decayRow.addView(comboDecayMore, new LayoutParams(style.dp(48), style.dp(48)));
        LayoutParams decayParams = new LayoutParams(-1, -2);
        decayParams.setMargins(0, style.dp(6), 0, style.dp(10));
        comboActions.addView(decayRow, decayParams);
        EditorFlowLayout triggers = new EditorFlowLayout(context);
        int[] triggerLabels = {R.string.combo_trigger_missed,
                R.string.combo_trigger_daily, R.string.combo_trigger_next};
        for (int i = 0; i < ComboDecayTrigger.values().length; i++) {
            TextView button = style.sans(context.getString(triggerLabels[i]), 14, 0, true);
            button.setGravity(Gravity.CENTER);
            button.setPadding(style.dp(14), 0, style.dp(14), 0);
            button.setMinHeight(style.dp(48));
            triggers.addView(button, new android.view.ViewGroup.LayoutParams(-2, style.dp(48)));
            comboTriggerButtons[i] = button;
        }
        comboActions.addView(triggers, new LayoutParams(-1, -2));
        combos = new OptionLeaf(context, R.string.options_combos,
                R.string.options_combos_description, comboActions);
        addLeaf(combos);

        flowSetupButton = outlineButton(context.getString(R.string.flow_setup_open));
        flowSetupButton.setOnClickListener(view ->
                actions.emit(OptionsAction.openFlowSetupSelected()));
        flowRunsButton = outlineButton(context.getString(R.string.flow_runs_manage));
        flowRunsButton.setOnClickListener(view ->
                actions.emit(OptionsAction.openFlowRunsSelected()));
        EditorFlowLayout flowActions = new EditorFlowLayout(context);
        flowActions.addView(flowSetupButton,
                new android.view.ViewGroup.LayoutParams(-2, style.dp(48)));
        flowActions.addView(flowRunsButton,
                new android.view.ViewGroup.LayoutParams(-2, style.dp(48)));
        flows = new OptionLeaf(context, R.string.flow_setup_options_title,
                R.string.flow_setup_options_description, flowActions);
        addLeaf(flows);

        calendarButton = outlineButton(context.getString(R.string.calendar_grant));
        calendarButton.setOnClickListener(view ->
                actions.emit(OptionsAction.calendarPermissionSelected()));
        LinearLayout calendarActions = new LinearLayout(context);
        calendarActions.addView(calendarButton, new LayoutParams(-2, style.dp(48)));
        calendar = new OptionLeaf(context, R.string.options_calendar,
                R.string.calendar_missing, calendarActions);
        addLeaf(calendar);

        updateButton = new TextView(context);
        updateButton.setText(R.string.check_updates);
        updateButton.setGravity(Gravity.CENTER);
        updateButton.setMinHeight(style.dp(52));
        updateButton.setPadding(style.dp(28), 0, style.dp(28), 0);
        updateButton.setTypeface(style.sansBold);
        updateButton.setTextSize(17);
        updateButton.setOnClickListener(view ->
                actions.emit(OptionsAction.manualUpdateSelected()));
        LinearLayout updateActions = new LinearLayout(context);
        updateActions.addView(updateButton, new LayoutParams(-2, style.dp(52)));
        updates = new OptionLeaf(context, R.string.options_updates,
                R.string.installed_version, updateActions);
        addLeaf(updates);
    }

    public void bind(OptionsScreenState state, String version) {
        DayPalette palette = state.palette;
        UiThemeMode mode = state.themeMode;
        FocusStepLimit focusStepLimit = state.focusStepLimit;
        int restTimerDefaultSeconds = state.restTimerDefaultSeconds;
        ComboPolicy comboPolicy = state.comboPolicy;
        CalendarPermissionStatus permission = state.calendarPermission;
        CalendarUiState calendarState = state.calendar;
        UpdateUiState updateState = state.update;
        heading.setTextColor(palette.accent);
        appearance.bind(palette, getContext().getString(R.string.options_appearance_description));
        UiThemeMode[] modes = UiThemeMode.values();
        for (int i = 0; i < themeButtons.length; i++) {
            boolean selected = modes[i] == mode;
            TextView button = themeButtons[i];
            button.setTextColor(selected ? palette.accentText : palette.ink2);
            GradientDrawable background = style.pill(selected ? palette.accent : Color.TRANSPARENT, 24);
            if (!selected) background.setStroke(style.dp(1), palette.dot);
            button.setBackground(background);
        }
        focusSteps.bind(palette,
                getContext().getString(R.string.options_focus_steps_description));
        FocusStepLimit[] limits = FocusStepLimit.values();
        for (int i = 0; i < focusStepButtons.length; i++) {
            boolean selected = limits[i] == focusStepLimit;
            TextView button = focusStepButtons[i];
            button.setSelected(selected);
            button.setTextColor(selected ? palette.accentText : palette.ink2);
            GradientDrawable background = style.pill(
                    selected ? palette.accent : Color.TRANSPARENT, 24);
            if (!selected) background.setStroke(style.dp(1), palette.dot);
            button.setBackground(background);
        }
        restTimers.bind(palette, getContext().getString(R.string.options_rest_timer_description));
        restTimerValue.setText(formatSeconds(restTimerDefaultSeconds));
        restTimerValue.setTextColor(palette.ink);
        restTimerLess.setEnabled(restTimerDefaultSeconds > 15);
        restTimerLess.setAlpha(restTimerDefaultSeconds > 15 ? 1f : .45f);
        restTimerLess.setOnClickListener(view -> actions.emit(
                OptionsAction.restTimerDefaultChanged(
                        Math.max(1, restTimerDefaultSeconds - 15))));
        restTimerMore.setOnClickListener(view -> actions.emit(
                OptionsAction.restTimerDefaultChanged(restTimerDefaultSeconds + 15)));
        bindOutline(restTimerLess, palette);
        bindOutline(restTimerMore, palette);
        bindCombos(palette, comboPolicy);
        flows.bind(palette, getContext().getString(R.string.flow_setup_options_description));
        bindOutline(flowSetupButton, palette);
        bindOutline(flowRunsButton, palette);
        boolean granted = permission == CalendarPermissionStatus.GRANTED;
        boolean settings = permission == CalendarPermissionStatus.DENIED_TO_SETTINGS;
        int calendarDescription = granted ? R.string.calendar_granted : R.string.calendar_missing;
        if (calendarState.status == CalendarUiState.Status.PROVIDER_UNAVAILABLE)
            calendarDescription = R.string.calendar_provider_unavailable;
        else if (calendarState.status == CalendarUiState.Status.ERROR)
            calendarDescription = R.string.calendar_error;
        calendar.bind(palette, getContext().getString(calendarDescription));
        calendarButton.setText(granted || settings
                ? R.string.open_app_settings : R.string.calendar_grant);
        bindOutline(calendarButton, palette);
        updates.bind(palette, updateDescription(version, updateState));
        bindUpdateButton(updateState);
        updateButton.setTextColor(palette.accentText);
        updateButton.setBackground(style.pill(palette.accent, 26));
        updateButton.setElevation(style.dp(5));
    }

    private void bindCombos(DayPalette palette, ComboPolicy policy) {
        combos.bind(palette, getContext().getString(R.string.options_combos_description));
        comboGainValue.setText("+" + policy.gainPoints);
        comboDecayValue.setText("−" + policy.decayPoints);
        comboGainValue.setTextColor(palette.ink);
        comboDecayValue.setTextColor(palette.ink);
        comboGainLess.setEnabled(policy.gainPoints > 0);
        comboDecayLess.setEnabled(policy.decayPoints > 0);
        comboGainLess.setAlpha(policy.gainPoints > 0 ? 1f : .45f);
        comboDecayLess.setAlpha(policy.decayPoints > 0 ? 1f : .45f);
        comboGainLess.setOnClickListener(view -> emitPolicy(
                Math.max(0, policy.gainPoints - 1), policy.decayPoints, policy.trigger));
        comboGainMore.setOnClickListener(view -> emitPolicy(
                policy.gainPoints + 1, policy.decayPoints, policy.trigger));
        comboDecayLess.setOnClickListener(view -> emitPolicy(
                policy.gainPoints, Math.max(0, policy.decayPoints - 1), policy.trigger));
        comboDecayMore.setOnClickListener(view -> emitPolicy(
                policy.gainPoints, policy.decayPoints + 1, policy.trigger));
        bindOutline(comboGainLess, palette); bindOutline(comboGainMore, palette);
        bindOutline(comboDecayLess, palette); bindOutline(comboDecayMore, palette);
        ComboDecayTrigger[] values = ComboDecayTrigger.values();
        for (int i = 0; i < values.length; i++) {
            ComboDecayTrigger trigger = values[i];
            boolean selected = trigger == policy.trigger;
            TextView button = comboTriggerButtons[i];
            button.setTextColor(selected ? palette.accentText : palette.ink2);
            GradientDrawable background = style.pill(
                    selected ? palette.accent : Color.TRANSPARENT, 24);
            if (!selected) background.setStroke(style.dp(1), palette.dot);
            button.setBackground(background);
            button.setOnClickListener(view -> emitPolicy(
                    policy.gainPoints, policy.decayPoints, trigger));
        }
    }

    private void emitPolicy(int gain, int decay, ComboDecayTrigger trigger) {
        actions.emit(OptionsAction.comboPolicySelected(new ComboPolicy(gain, decay, trigger)));
    }

    private String updateDescription(String installed, UpdateUiState state) {
        if (state == null || state.status == UpdateUiState.Status.IDLE)
            return getContext().getString(R.string.installed_version, installed);
        if (state.status == UpdateUiState.Status.CHECKING)
            return getContext().getString(R.string.update_checking, installed);
        if (state.status == UpdateUiState.Status.CURRENT)
            return getContext().getString(R.string.update_current, installed);
        if (state.status == UpdateUiState.Status.AVAILABLE)
            return getContext().getString(R.string.update_available,
                    state.update.versionName, installed);
        if (state.status == UpdateUiState.Status.DOWNLOADING)
            return getContext().getString(R.string.update_downloading,
                    state.update.versionName, state.progress);
        if (state.status == UpdateUiState.Status.READY)
            return getContext().getString(R.string.update_ready, state.update.versionName);
        return state.message == null ? getContext().getString(R.string.error_update_check)
                : state.message;
    }

    private void bindUpdateButton(UpdateUiState state) {
        boolean busy = state != null && (state.status == UpdateUiState.Status.CHECKING
                || state.status == UpdateUiState.Status.DOWNLOADING);
        updateButton.setEnabled(!busy);
        updateButton.setAlpha(busy ? .65f : 1f);
        if (busy) updateButton.setText(R.string.update_busy);
        else if (state != null && state.status == UpdateUiState.Status.AVAILABLE)
            updateButton.setText(getContext().getString(
                    R.string.download_update, state.update.versionName));
        else if (state != null && state.status == UpdateUiState.Status.READY)
            updateButton.setText(R.string.install_update);
        else updateButton.setText(R.string.check_updates);
    }

    private void addLeaf(OptionLeaf leaf) {
        LayoutParams params = new LayoutParams(-1, -2);
        params.setMargins(0, style.dp(14), 0, 0);
        addView(leaf, params);
    }

    private TextView outlineButton(String text) {
        TextView button = style.sans(text, 16, 0, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(style.dp(22), 0, style.dp(22), 0);
        button.setMinHeight(style.dp(48));
        return button;
    }

    private void bindOutline(TextView button, DayPalette palette) {
        button.setTextColor(palette.ink2);
        GradientDrawable background = style.pill(Color.TRANSPARENT, 24);
        background.setStroke(style.dp(1), palette.dot);
        button.setBackground(background);
    }

    private static String formatSeconds(int seconds) {
        int minutes = seconds / 60;
        int remainder = seconds % 60;
        return minutes == 0 ? seconds + " s"
                : remainder == 0 ? minutes + " min"
                : minutes + ":" + (remainder < 10 ? "0" : "") + remainder;
    }

    private final class OptionLeaf extends LinearLayout {
        private final TextView title;
        private final TextView subtitle;

        OptionLeaf(Context context, int titleText, int subtitleText, android.view.View actions) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(style.dp(24), style.dp(18), style.dp(24), style.dp(18));
            setElevation(style.dp(6));
            title = style.serif(context.getString(titleText), 23, 0, false, 400);
            addView(title);
            subtitle = style.sans(context.getString(subtitleText), 15, 0, false);
            LayoutParams subtitleParams = new LayoutParams(-1, -2);
            subtitleParams.setMargins(0, style.dp(5), 0, 0);
            addView(subtitle, subtitleParams);
            LayoutParams actionParams = new LayoutParams(-1, -2);
            actionParams.setMargins(0, style.dp(14), 0, 0);
            addView(actions, actionParams);
        }

        void bind(DayPalette palette, String subtitleText) {
            setBackground(style.leaf(palette.leaf2, style.edge(palette, 2), 56, 8, 56, 8));
            title.setTextColor(palette.ink);
            subtitle.setText(subtitleText);
            subtitle.setTextColor(palette.hint);
        }
    }
}
