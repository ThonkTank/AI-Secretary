package com.autosecretary.ui.ai;

import android.app.Dialog;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.autosecretary.presentation.R;
import com.autosecretary.presentation.databinding.DialogAiBinding;
import com.autosecretary.domain.WorkItem;
import com.autosecretary.ui.FeatureViewModels;
import com.autosecretary.ui.MainViewModel;

import java.util.List;

/** One-shot local-AI wish surface; it intentionally has no chat transcript. */
public final class AiInstructionDialogFragment extends DialogFragment {
    public static final String TAG = "ai-instruction";
    private static final String INSTRUCTION = "instruction";
    private DialogAiBinding binding;

    @NonNull
    @Override public Dialog onCreateDialog(Bundle state) {
        MainViewModel mainViewModel = FeatureViewModels.main(this);
        AiViewModel aiViewModel = FeatureViewModels.ai(this);
        binding = DialogAiBinding.inflate(requireActivity().getLayoutInflater());
        if (state != null) binding.AiInstruction.setText(state.getString(INSTRUCTION, ""));
        Dialog dialog = new Dialog(requireContext(), R.style.WaldEditorDialog);
        dialog.setContentView(binding.getRoot());
        binding.AiCancel.setOnClickListener(view -> dialog.dismiss());
        binding.AiExampleOne.setOnClickListener(view -> useExample(binding.AiExampleOne.getText()));
        binding.AiExampleTwo.setOnClickListener(view -> useExample(binding.AiExampleTwo.getText()));
        binding.AiExampleThree.setOnClickListener(view -> useExample(binding.AiExampleThree.getText()));
        binding.AiSubmit.setOnClickListener(view -> {
            String command = binding.AiInstruction.getText().toString().trim();
            if (command.isEmpty()) {
                binding.AiInstruction.setError("Ein Wunsch fehlt noch.");
                return;
            }
            List<WorkItem> items = mainViewModel.workItems();
            aiViewModel.propose(command, items);
            dialog.dismiss();
        });
        return dialog;
    }

    @Override public void onStart() {
        super.onStart();
        Window window = requireDialog().getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private void useExample(CharSequence example) {
        binding.AiInstruction.setText(example);
        binding.AiInstruction.setSelection(binding.AiInstruction.length());
    }

    @Override public void onSaveInstanceState(@NonNull Bundle outState) {
        if (binding != null) outState.putString(
                INSTRUCTION, binding.AiInstruction.getText().toString());
        super.onSaveInstanceState(outState);
    }

    @Override public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
