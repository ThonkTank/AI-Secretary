package com.autosecretary.ui.migration;

import android.app.Dialog;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

public final class LegacyImportDialogFragment extends DialogFragment {
    public static final String TAG = "legacy-import";

    public interface Host {
        LegacyImportViewModel legacyImportViewModel();
        void onLegacyImportReady();
    }

    private LegacyImportViewModel viewModel;
    private final ActivityResultLauncher<String[]> picker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                android.content.Context app = requireContext().getApplicationContext();
                viewModel.importArchive(() -> app.getContentResolver().openInputStream(uri));
            });

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle state) {
        setCancelable(false);
        viewModel = ((Host) requireActivity()).legacyImportViewModel();
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Daten aus Build 4 übernehmen")
                .setMessage("Vor dem ersten Start muss entschieden werden, ob ein mit der ADB-Brücke "
                        + "exportiertes Build-4-Archiv vorhanden ist. Der Import funktioniert nur in "
                        + "eine vollständig leere Datenbank.")
                .setPositiveButton("Build-4-Archiv auswählen", null)
                .setNegativeButton("Keine Altdaten vorhanden", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view ->
                    picker.launch(new String[]{"application/zip", "application/octet-stream"}));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(view ->
                    viewModel.chooseEmptyDatabase());
        });
        viewModel.state().observe(this, value -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!value.busy());
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(!value.busy());
            if (value.busy()) {
                dialog.setMessage("Archiv wird geprüft und atomar vorbereitet …");
            } else if (value.error() != null) {
                dialog.setMessage("Import abgelehnt: " + value.error()
                        + "\n\nDie bestehende Installation wurde nicht verändert.");
            } else if (value.ready()) {
                ((Host) requireActivity()).onLegacyImportReady();
                dismissAllowingStateLoss();
            }
        });
        return dialog;
    }
}
