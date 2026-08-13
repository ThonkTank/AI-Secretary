package com.autosecretary.ui.ai;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.autosecretary.R;
import com.autosecretary.databinding.DialogAiBinding;
import com.autosecretary.domain.WorkItem;
import com.autosecretary.ui.MainViewModel;

import java.util.List;

public final class AiInstructionDialogFragment extends DialogFragment {
    public static final String TAG = "ai-instruction";
    private static final String INSTRUCTION = "instruction";
    private DialogAiBinding binding;
    public interface Host {
        MainViewModel mainViewModel();
        AiViewModel aiViewModel();
    }

    @NonNull
    @Override public Dialog onCreateDialog(Bundle state) {
        Host host = (Host) requireActivity();
        binding = DialogAiBinding.inflate(requireActivity().getLayoutInflater());
        if (state != null) binding.AiInstruction.setText(state.getString(INSTRUCTION, ""));
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.ai_bulk_title)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.create_preview, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String command = binding.AiInstruction.getText().toString().trim();
                    if (command.isEmpty()) {
                        binding.AiInstruction.setError("Anweisung fehlt");
                        return;
                    }
                    List<WorkItem> items = host.mainViewModel().state().getValue() == null
                            || host.mainViewModel().state().getValue().dashboard() == null
                            ? List.of() : host.mainViewModel().state().getValue()
                            .dashboard().workItems();
                    host.aiViewModel().propose(command, items);
                    dialog.dismiss();
                }));
        return dialog;
    }

    @Override public void onSaveInstanceState(@NonNull Bundle outState) {
        if (binding != null) {
            outState.putString(INSTRUCTION, binding.AiInstruction.getText().toString());
        }
        super.onSaveInstanceState(outState);
    }

    @Override public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
