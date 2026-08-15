package de.thonktank.autosecretary;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String RELEASES_URL = "https://github.com/ThonkTank/AI-Secretary/releases/latest";
    private LinearLayout content;
    private TaskRepository store;
    private final int ink = Color.rgb(32, 48, 39);
    private final int muted = Color.rgb(102, 117, 107);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new TaskRepository(this);
        buildScreen();
    }
    @Override protected void onResume() { super.onResume(); if (content != null) refresh(); }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(20), dp(20), dp(20), dp(32));
        content.setBackgroundColor(Color.rgb(247, 246, 240)); scroll.addView(content); setContentView(scroll); refresh();
    }

    private void refresh() {
        content.removeAllViews();
        LinearLayout header = row();
        TextView title = text("Auto Secretary", 32, ink, true); header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button updates = new Button(this); updates.setText("Updates"); updates.setOnClickListener(v -> openUpdates()); header.addView(updates);
        content.addView(header);
        TextView xp = text(store.getXp() + " XP  ·  jeder Schritt zählt", 14, muted, false); xp.setPadding(0, 0, 0, dp(16)); content.addView(xp);

        Button add = new Button(this); add.setText("+ Aufgabe anlegen"); add.setOnClickListener(v -> showCreateDialog()); content.addView(add, match());
        List<JSONObject> tasks = store.activeTasks();
        if (tasks.isEmpty()) {
            TextView empty = text("Gerade ist nichts offen. Das ist auch gut.", 19, ink, false); empty.setGravity(Gravity.CENTER); empty.setPadding(0, dp(48), 0, dp(12)); content.addView(empty);
            TextView hint = text("Lege nur die eine Sache an, die dir gerade helfen würde.", 14, muted, false); hint.setGravity(Gravity.CENTER); content.addView(hint);
            TaskWidgetProvider.updateAll(this); return;
        }
        section("JETZT"); showTask(tasks.get(0), true);
        if (tasks.size() > 1) { section("DANACH"); showTask(tasks.get(1), false); }
        if (tasks.size() > 2) {
            section("HEUTE");
            String previousSlot = "";
            for (int i = 2; i < Math.min(tasks.size(), 6); i++) {
                JSONObject task = tasks.get(i);
                String slot = task.optString("slot");
                if (!slot.equals(previousSlot)) { TextView slotLabel = text(slot, 13, muted, true); slotLabel.setPadding(0, dp(10), 0, dp(2)); content.addView(slotLabel); previousSlot = slot; }
                showTask(task, false);
            }
            if (tasks.size() > 6) { TextView more = text((tasks.size() - 6) + " weitere – sie laufen nicht weg.", 14, muted, false); more.setPadding(0, dp(8), 0, 0); content.addView(more); }
        }
        TaskWidgetProvider.updateAll(this);
    }

    private void showTask(JSONObject task, boolean prominent) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(14), dp(12), dp(14), dp(10));
        card.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams cardParams = match(); cardParams.setMargins(0, dp(5), 0, dp(5)); content.addView(card, cardParams);
        TextView taskName = text(task.optString("title"), prominent ? 21 : 17, ink, true); card.addView(taskName);
        TextView action = text(store.nextAction(task), prominent ? 18 : 15, muted, false); action.setPadding(0, dp(5), 0, dp(4)); card.addView(action);
        int remaining = store.remainingSteps(task);
        if (remaining > 1) card.addView(text(remaining + " Schritte offen · " + (remaining - 1) + " weitere", 13, muted, false));
        else if (task.optBoolean("ongoing")) card.addView(text("Fortlaufendes Vorhaben", 13, muted, false));
        LinearLayout actions = row(); actions.setPadding(0, dp(5), 0, 0);
        boolean conditionReady = task.optBoolean("ongoing") && remaining == 0 && !task.optString("condition").isEmpty();
        Button done = new Button(this); done.setText(conditionReady ? "Bedingung erfüllt" : "Abhaken");
        done.setOnClickListener(v -> { if (conditionReady) store.fulfilCondition(task.optString("id")); else store.completeNextStep(task.optString("id")); refresh(); });
        actions.addView(done, new LinearLayout.LayoutParams(0, -2, 1));
        Button later = new Button(this); later.setText("Später"); later.setOnClickListener(v -> { store.later(task.optString("id")); refresh(); });
        LinearLayout.LayoutParams laterParams = new LinearLayout.LayoutParams(0, -2, 1); laterParams.setMargins(dp(6), 0, 0, 0); actions.addView(later, laterParams);
        card.addView(actions);
    }

    private void showCreateDialog() {
        ScrollView scroll = new ScrollView(this); LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(22), dp(8), dp(22), dp(8)); scroll.addView(form);
        EditText name = input("Name der Aufgabe, z. B. Morgenroutine"); form.addView(name);
        form.addView(label("BEVORZUGTE TAGESZEIT"));
        Spinner slot = spinner(new String[]{TaskRepository.SLOT_MORNING, TaskRepository.SLOT_MIDDAY, TaskRepository.SLOT_EVENING, TaskRepository.SLOT_LATER}); form.addView(slot);
        form.addView(label("WIEDERHOLUNG"));
        Spinner repeat = spinner(new String[]{"Einmalig", "Täglich", "Alle N Tage", "Wochentage"}); form.addView(repeat);
        EditText interval = input("Bei „Alle N Tage“: Abstand, z. B. 2"); interval.setInputType(2); form.addView(interval);
        form.addView(label("WOCHENTAGE"));
        LinearLayout days = row(); final CheckBox[] dayBoxes = new CheckBox[7]; String[] dayNames = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};
        for (int i = 0; i < 7; i++) { dayBoxes[i] = new CheckBox(this); dayBoxes[i].setText(dayNames[i]); days.addView(dayBoxes[i]); } form.addView(days);
        EditText steps = input("Schritte – einer pro Zeile"); steps.setMinLines(3); steps.setGravity(Gravity.TOP); form.addView(steps);
        CheckBox ongoing = new CheckBox(this); ongoing.setText("Fortlaufendes Vorhaben (bleibt offen)"); form.addView(ongoing);
        EditText condition = input("Erledigungskondition, z. B. „Praktikum angenommen“"); form.addView(condition);
        CheckBox reminder = new CheckBox(this); reminder.setText("Sanfte Erinnerung zur Tageszeit"); form.addView(reminder);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Neue Aufgabe").setView(scroll).setNegativeButton("Abbrechen", null).setPositiveButton("Anlegen", null).create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (name.getText().toString().trim().isEmpty()) { name.setError("Ein kurzer Name reicht."); return; }
            JSONArray stepArray = new JSONArray();
            for (String line : steps.getText().toString().split("\\n")) if (!line.trim().isEmpty()) {
                JSONObject step = new JSONObject(); try { step.put("text", line.trim()); step.put("done", false); } catch (Exception ignored) {} stepArray.put(step);
            }
            JSONArray selectedDays = new JSONArray(); for (int i = 0; i < dayBoxes.length; i++) if (dayBoxes[i].isChecked()) selectedDays.put(i + 1);
            int every = 2; try { every = Integer.parseInt(interval.getText().toString()); } catch (Exception ignored) { }
            JSONObject task = store.create(name.getText().toString(), String.valueOf(slot.getSelectedItem()), String.valueOf(repeat.getSelectedItem()), every, selectedDays, stepArray, ongoing.isChecked(), condition.getText().toString(), reminder.isChecked());
            if (reminder.isChecked()) { requestNotificationsIfNeeded(); ReminderScheduler.schedule(this, task.optString("id"), task.optString("title"), task.optString("slot")); }
            dialog.dismiss(); refresh();
        }));
        dialog.show();
    }

    private void requestNotificationsIfNeeded() { if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 42); }
    private void openUpdates() { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL))); }
    private void section(String value) { TextView section = text(value, 12, muted, true); section.setPadding(0, dp(22), 0, dp(2)); content.addView(section); }
    private TextView label(String value) { TextView label = text(value, 12, muted, true); label.setPadding(0, dp(14), 0, dp(2)); return label; }
    private EditText input(String hint) { EditText input = new EditText(this); input.setHint(hint); input.setTextColor(ink); input.setHintTextColor(muted); return input; }
    private Spinner spinner(String[] items) { Spinner spinner = new Spinner(this); spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items)); return spinner; }
    private LinearLayout row() { LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); return row; }
    private TextView text(String value, int size, int color, boolean bold) { TextView text = new TextView(this); text.setText(value); text.setTextSize(size); text.setTextColor(color); if (bold) text.setTypeface(null, Typeface.BOLD); return text; }
    private LinearLayout.LayoutParams match() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
