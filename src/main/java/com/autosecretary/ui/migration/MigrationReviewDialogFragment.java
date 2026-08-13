package com.autosecretary.ui.migration;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.autosecretary.application.MigrationCandidate;
import com.autosecretary.application.MigrationCandidateResolution;
import com.autosecretary.application.MigrationReview;
import com.autosecretary.ui.MainViewModel;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Makes every lossy legacy candidate an explicit user decision. */
public final class MigrationReviewDialogFragment extends DialogFragment {
    public static final String TAG = "migration-review";
    private static final String CHOICES = "choices";
    private static final String CADENCES = "cadences";

    public interface Host {
        MainViewModel mainViewModel();
        void shareMigrationBackup();
    }

    private final List<DecisionRow> rows = new ArrayList<>();

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle state) {
        MainViewModel viewModel = ((Host) requireActivity()).mainViewModel();
        MigrationReview review = requireReview(viewModel);
        rows.clear();
        int[] savedChoices = state == null ? null : state.getIntArray(CHOICES);
        ArrayList<String> savedCadences = state == null
                ? null : state.getStringArrayList(CADENCES);
        ScrollView scroll = new ScrollView(requireContext());
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        content.setPadding(padding, padding, padding, padding);
        scroll.addView(content);

        TextView summary = new TextView(requireContext());
        summary.setText(review.importedItems() + " Aufgaben/Routinen und "
                + review.importedCompletions() + " Abschlüsse wurden übernommen."
                + "\n\nVor der Übernahme wurde das unveränderte Original als "
                + "Wiederherstellungsbackup gesichert."
                + warningSummary(review.warningsJson())
                + (review.candidates().isEmpty() ? ""
                : "\n\nFür nicht eindeutig abbildbare Regeln ist eine Entscheidung erforderlich."));
        content.addView(summary);

        for (MigrationCandidate candidate : review.candidates()) {
            TextView label = new TextView(requireContext());
            label.setPadding(0, dp(18), 0, dp(4));
            label.setText("• " + candidate.title() + " — " + reason(candidate.reason())
                    + "\n  " + candidate.legacySummary());
            content.addView(label);

            Spinner choice = new Spinner(requireContext());
            choice.setAdapter(new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_spinner_dropdown_item,
                    new String[]{"Bitte wählen", "Als Aufgabe", "Routine alle N Tage", "Verwerfen"}));
            content.addView(choice);
            EditText cadence = new EditText(requireContext());
            cadence.setHint("N Tage");
            cadence.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            cadence.setVisibility(View.GONE);
            content.addView(cadence);
            choice.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                                     int position, long id) {
                    cadence.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
            });
            int rowIndex = rows.size();
            if (savedCadences != null && rowIndex < savedCadences.size()) {
                cadence.setText(savedCadences.get(rowIndex));
            }
            if (savedChoices != null && rowIndex < savedChoices.length) {
                choice.setSelection(savedChoices[rowIndex]);
            }
            rows.add(new DecisionRow(candidate, choice, cadence));
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Datenübernahme abgeschlossen")
                .setView(scroll)
                .setPositiveButton(review.candidates().isEmpty()
                        ? "Bericht bestätigen" : "Entscheidungen anwenden", null)
                .setNeutralButton("Backup exportieren", null)
                .setNegativeButton("Später", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(
                    view -> ((Host) requireActivity()).shareMigrationBackup());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                List<MigrationCandidateResolution> resolutions = resolutions();
                if (resolutions == null) return;
                viewModel.resolveMigrationCandidates(resolutions, review.id());
                dialog.dismiss();
            });
        });
        return dialog;
    }

    @Override public void onSaveInstanceState(@NonNull Bundle outState) {
        int[] choices = new int[rows.size()];
        ArrayList<String> cadences = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            choices[index] = rows.get(index).choice().getSelectedItemPosition();
            cadences.add(rows.get(index).cadence().getText().toString());
        }
        outState.putIntArray(CHOICES, choices);
        outState.putStringArrayList(CADENCES, cadences);
        super.onSaveInstanceState(outState);
    }

    private List<MigrationCandidateResolution> resolutions() {
        List<MigrationCandidateResolution> result = new ArrayList<>();
        for (DecisionRow row : rows) {
            int selected = row.choice().getSelectedItemPosition();
            if (selected == 0) {
                ((TextView) row.choice().getSelectedView()).setError("Entscheidung fehlt");
                return null;
            }
            if (selected == 1) result.add(MigrationCandidateResolution.task(row.candidate().id()));
            if (selected == 3) result.add(MigrationCandidateResolution.discard(row.candidate().id()));
            if (selected == 2) {
                try {
                    int days = Integer.parseInt(row.cadence().getText().toString().trim());
                    if (days < 1 || days > 365) throw new NumberFormatException();
                    result.add(MigrationCandidateResolution.routine(row.candidate().id(), days));
                } catch (RuntimeException error) {
                    row.cadence().setError("1–365 Tage");
                    return null;
                }
            }
        }
        return result;
    }

    private MigrationReview requireReview(MainViewModel viewModel) {
        if (viewModel.state().getValue() == null
                || viewModel.state().getValue().dashboard() == null
                || viewModel.state().getValue().dashboard().migrationReview() == null) {
            throw new IllegalStateException("Migrationsbericht fehlt");
        }
        return viewModel.state().getValue().dashboard().migrationReview();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String reason(String value) {
        return switch (value) {
            case "FIXED_APPOINTMENT_UNSUPPORTED" -> "fester Termin";
            case "COMPLEX_RECURRENCE_UNSUPPORTED" -> "komplexe Wiederholungsregel";
            case "ROUTINE_DEADLINE_UNSUPPORTED" -> "Wiederholung mit End-Deadline";
            case "CORRUPT_LEGACY_CORE_UNSUPPORTED" -> "widersprüchliche Kerndaten";
            case "CORRUPT_PROTOTYPE_UNSUPPORTED" -> "widersprüchliche Preview-Kerndaten";
            default -> "nicht unterstützte Altdaten";
        };
    }

    private static String warningSummary(String encoded) {
        try {
            JSONObject warnings = new JSONObject(encoded == null ? "{}" : encoded);
            List<String> keys = new ArrayList<>();
            var iterator = warnings.keys();
            while (iterator.hasNext()) keys.add(iterator.next());
            keys.sort(String::compareTo);
            if (keys.isEmpty()) return "";
            StringBuilder result = new StringBuilder("\n\nNur im Backup verbleiben:");
            for (String key : keys) {
                result.append("\n• ").append(warnings.optInt(key)).append(" ")
                        .append(warningLabel(key));
            }
            return result.toString();
        } catch (Exception error) {
            return "\n\nDetails zu nicht übernommenen Altdaten stehen im Migrationsbericht.";
        }
    }

    private static String warningLabel(String key) {
        return switch (key) {
            case "DISCARDED_DESCRIPTIONS" -> "Beschreibungen";
            case "DISCARDED_PRIORITIES" -> "Prioritäten";
            case "DISCARDED_START_BOUNDARIES" -> "Startgrenzen";
            case "DISCARDED_APPOINTMENTS" -> "feste Termine";
            case "DISCARDED_PROGRESS_MODELS" -> "Fortschrittsmodelle";
            case "DISCARDED_DURATION_RANGES" -> "Dauerbereiche";
            case "DISCARDED_COOLDOWNS" -> "Cooldown-Regeln";
            case "DISCARDED_MISS_POLICIES" -> "Verfallregeln";
            case "DISCARDED_COMPLETION_AGGREGATES" -> "alte Dauer-/Streak-Aggregate";
            case "DISCARDED_GOAL_APPEARANCE" -> "Darstellungsangaben";
            case "DISCARDED_BUDGET_LINKS" -> "Aufgaben-Budgetverknüpfungen";
            case "DISCARDED_MEAL_LINKS" -> "Aufgaben-Mahlzeitenverknüpfungen";
            case "DISCARDED_RELATIONSHIPS" -> "Aufgabenbeziehungen";
            case "DISCARDED_PREREQUISITES" -> "Voraussetzungen";
            case "DISCARDED_PLANNED_SLOTS" -> "alte Planungsslots";
            case "DISCARDED_PLANNED_MEALS" -> "geplante Mahlzeiten";
            case "DISCARDED_SCHEDULE_CONFIG" -> "alte Arbeitszeitregeln";
            case "DISCARDED_TRANSITION_STATS" -> "alte Übergangsstatistiken";
            case "DISCARDED_TASK_CATEGORIES" -> "Aufgabenkategorien";
            case "DISCARDED_CATEGORY_WINDOWS" -> "Kategoriefenster";
            case "DISCARDED_PREFERENCE_DAYS" -> "bevorzugte Wochentage";
            case "DISCARDED_AMBIGUOUS_TIME_PREFERENCES" ->
                    "nicht eindeutige Zeitpräferenzen";
            case "DISCARDED_BUDGET_RECORDS" -> "Budgetdatensätze";
            case "DISCARDED_MEAL_RECORDS" -> "Mahlzeitendatensätze";
            case "CORRUPT_STEPS_SKIPPED" -> "beschädigte Schrittlisten";
            case "CORRUPT_STEP_COMPLETIONS_SKIPPED" ->
                    "beschädigte Schritt-Abschlussbelege";
            case "CORRUPT_COMPLETIONS_SKIPPED" -> "beschädigte Abschlussbelege";
            case "QUARANTINED_PROTOTYPE_ITEMS" ->
                    "widersprüchliche Preview-Aufgaben zur manuellen Entscheidung";
            case "QUARANTINED_CORRUPT_CORE_ITEMS" ->
                    "beschädigte Build-4-Aufgaben zur manuellen Entscheidung";
            default -> key;
        };
    }

    private record DecisionRow(MigrationCandidate candidate, Spinner choice, EditText cadence) { }
}
