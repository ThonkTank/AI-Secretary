package de.thonktank.autosecretary;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends ComponentActivity {
    public static final String CONFIRM_TASK = "confirm_task";
    private static final String RELEASES_URL = "https://github.com/ThonkTank/AI-Secretary/releases/latest";
    private LinearLayout content;
    private TaskViewModel viewModel;
    private final int ink = Color.rgb(32, 48, 39), muted = Color.rgb(102, 117, 107);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); buildScreen();
        viewModel = new ViewModelProvider(this, new TaskViewModel.Factory(this)).get(TaskViewModel.class);
        viewModel.state().observe(this, this::render);
        viewModel.errors().observe(this, message -> { if (message != null) new AlertDialog.Builder(this).setMessage(message).setPositiveButton("Okay", null).show(); });
        if (DatabaseProvider.wasReset(this)) new AlertDialog.Builder(this).setTitle("Neuer, stabiler Start").setMessage("Die Testdaten der ersten Version wurden bewusst zurückgesetzt. Neue Aufgaben werden jetzt zuverlässig lokal gespeichert.").setPositiveButton("Verstanden", (d, w) -> DatabaseProvider.acknowledgeReset(this)).show();
        String confirmTask = getIntent().getStringExtra(CONFIRM_TASK); if (confirmTask != null) confirmClose(confirmTask, "dieses Vorhaben");
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this); content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(20), dp(20), dp(20), dp(32)); content.setBackgroundColor(Color.rgb(247, 246, 240)); scroll.addView(content); setContentView(scroll);
    }
    private void render(DashboardState state) {
        content.removeAllViews(); LinearLayout header = row();
        header.addView(text("Auto Secretary", 29, ink, true), new LinearLayout.LayoutParams(0, -2, 1)); Button updates = new Button(this); updates.setText("Updates"); updates.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)))); header.addView(updates); content.addView(header);
        TextView xp = text(state.xp + " XP  ·  jeder Schritt zählt", 14, muted, false); xp.setPadding(0, 0, 0, dp(16)); content.addView(xp);
        Button add = new Button(this); add.setText("+ Aufgabe anlegen"); add.setOnClickListener(v -> showCreateDialog()); content.addView(add, match());
        if (state.tasks.isEmpty()) { TextView empty = text("Gerade ist nichts offen. Das ist auch gut.", 19, ink, false); empty.setGravity(Gravity.CENTER); empty.setPadding(0, dp(48), 0, dp(12)); content.addView(empty); TextView hint = text("Lege nur die eine Sache an, die dir gerade helfen würde.", 14, muted, false); hint.setGravity(Gravity.CENTER); content.addView(hint); TaskWidgetProvider.updateAll(this); return; }
        section("JETZT"); showTask(state.tasks.get(0), true);
        if (state.tasks.size() > 1) { section("DANACH"); showTask(state.tasks.get(1), false); }
        if (state.tasks.size() > 2) { section("HEUTE"); String slot = ""; for (int i = 2; i < Math.min(state.tasks.size(), 6); i++) { TaskSnapshot item = state.tasks.get(i); if (!slot.equals(item.slot)) { TextView label = text(item.slot, 13, muted, true); label.setPadding(0, dp(10), 0, dp(2)); content.addView(label); slot = item.slot; } showTask(item, false); } if (state.tasks.size() > 6) { TextView more = text((state.tasks.size() - 6) + " weitere – sie laufen nicht weg.", 14, muted, false); more.setPadding(0, dp(8), 0, 0); content.addView(more); } }
        TaskWidgetProvider.updateAll(this);
    }
    private void showTask(TaskSnapshot item, boolean prominent) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(14), dp(12), dp(14), dp(10)); card.setBackgroundColor(Color.WHITE); LinearLayout.LayoutParams params = match(); params.setMargins(0, dp(5), 0, dp(5)); content.addView(card, params);
        card.addView(text(item.title, prominent ? 21 : 17, ink, true)); TextView action = text(item.nextAction, prominent ? 18 : 15, muted, false); action.setPadding(0, dp(5), 0, dp(4)); card.addView(action);
        if (item.remainingSteps > 1) card.addView(text(item.remainingSteps + " Schritte offen · " + (item.remainingSteps - 1) + " weitere", 13, muted, false));
        LinearLayout actions = row(); actions.setPadding(0, dp(5), 0, 0);
        if (!item.occurrenceId.isEmpty()) { Button done = new Button(this); done.setText("Abhaken"); done.setOnClickListener(v -> viewModel.complete(item.occurrenceId)); actions.addView(done, new LinearLayout.LayoutParams(0, -2, 1)); Button later = new Button(this); later.setText("Später"); later.setOnClickListener(v -> viewModel.defer(item.occurrenceId)); LinearLayout.LayoutParams laterParams = new LinearLayout.LayoutParams(0, -2, 1); laterParams.setMargins(dp(6), 0, 0, 0); actions.addView(later, laterParams); }
        if (item.terminalCondition) { Button close = new Button(this); close.setText("Bedingung erfüllt"); close.setOnClickListener(v -> confirmClose(item.taskId, item.title)); actions.addView(close, new LinearLayout.LayoutParams(0, -2, 1)); }
        card.addView(actions);
    }
    private void confirmClose(String taskId, String title) { new AlertDialog.Builder(this).setTitle("Vorhaben abschließen?").setMessage("„" + title + "“ wird geschlossen. Das kannst du in diesem Stand noch nicht wiederherstellen.").setNegativeButton("Abbrechen", null).setPositiveButton("Abschließen", (d, w) -> viewModel.close(taskId)).show(); }
    private void showCreateDialog() {
        ScrollView scroll = new ScrollView(this); LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(22), dp(8), dp(22), dp(8)); scroll.addView(form);
        EditText name = input("Name der Aufgabe, z. B. Morgenroutine"); form.addView(name); form.addView(label("BEVORZUGTE TAGESZEIT")); Spinner slot = spinner(new String[]{TaskSlots.MORNING, TaskSlots.MIDDAY, TaskSlots.EVENING, TaskSlots.LATER}); form.addView(slot);
        form.addView(label("WIEDERHOLUNG")); Spinner repeat = spinner(new String[]{"Einmalig", "Täglich", "Alle N Tage", "Wochentage"}); form.addView(repeat); EditText interval = input("Bei „Alle N Tage“: Abstand, z. B. 2"); interval.setInputType(2); form.addView(interval);
        form.addView(label("WOCHENTAGE")); LinearLayout days = row(); CheckBox[] boxes = new CheckBox[7]; String[] labels = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"}; for (int i = 0; i < 7; i++) { boxes[i] = new CheckBox(this); boxes[i].setText(labels[i]); days.addView(boxes[i]); } form.addView(days);
        EditText steps = input("Schritte – einer pro Zeile"); steps.setMinLines(3); steps.setGravity(Gravity.TOP); form.addView(steps); CheckBox ongoing = new CheckBox(this); ongoing.setText("Fortlaufendes Vorhaben (bleibt offen)"); form.addView(ongoing); EditText condition = input("Erledigungskondition, z. B. „Praktikum angenommen“"); form.addView(condition);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Neue Aufgabe").setView(scroll).setNegativeButton("Abbrechen", null).setPositiveButton("Anlegen", null).create(); dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> { if (name.getText().toString().trim().isEmpty()) { name.setError("Ein kurzer Name reicht."); return; } int every = 2; try { every = Integer.parseInt(interval.getText().toString()); } catch (Exception ignored) { } boolean[] selected = new boolean[7]; for (int i = 0; i < 7; i++) selected[i] = boxes[i].isChecked(); String value = String.valueOf(repeat.getSelectedItem()); String recurrence = "Einmalig".equals(value) ? "ONCE" : "Täglich".equals(value) ? "DAILY" : "Alle N Tage".equals(value) ? "INTERVAL" : "WEEKDAYS"; List<String> stepLines = new ArrayList<>(Arrays.asList(steps.getText().toString().split("\\n"))); viewModel.create(name.getText().toString(), String.valueOf(slot.getSelectedItem()), recurrence, every, ScheduleCalculator.weekdayMask(selected), stepLines, ongoing.isChecked(), condition.getText().toString()); dialog.dismiss(); })); dialog.show();
    }
    private void section(String value) { TextView section = text(value, 12, muted, true); section.setPadding(0, dp(22), 0, dp(2)); content.addView(section); }
    private TextView label(String value) { TextView label = text(value, 12, muted, true); label.setPadding(0, dp(14), 0, dp(2)); return label; }
    private EditText input(String hint) { EditText input = new EditText(this); input.setHint(hint); input.setTextColor(ink); input.setHintTextColor(muted); return input; }
    private Spinner spinner(String[] items) { Spinner spinner = new Spinner(this); spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items)); return spinner; }
    private LinearLayout row() { LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); return row; }
    private TextView text(String value, int size, int color, boolean bold) { TextView text = new TextView(this); text.setText(value); text.setTextSize(size); text.setTextColor(color); if (bold) text.setTypeface(null, Typeface.BOLD); return text; }
    private LinearLayout.LayoutParams match() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
