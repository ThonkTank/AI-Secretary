package com.autosecretary.features.task.ui.assistant;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.autosecretary.R;
import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.shared.ClaudeApiKeyStore;
import com.autosecretary.shared.ClaudeModelStore;
import com.autosecretary.shared.ContentDocumentReader;
import com.autosecretary.shared.ContentDocumentReader.DocumentContents;
import com.autosecretary.shared.ui.SimpleTextWatcher;
import com.autosecretary.shared.ui.UiConstants;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * Multi-domain assistant tab (nav_assistant). The user chats with Claude, optionally attaching a
 * PDF/txt/md file, and can pick a model and toggle the thinking display. Any change the assistant
 * proposes appears as a card the user confirms with "Übernehmen"; task changes stay undoable via
 * "Rückgängig".
 *
 * <p>The whole screen renders from one {@link AssistantUiState} observed off
 * {@link TaskAssistantViewModel} (activity-scoped, so its chat history survives tab switches). The
 * history is a RecyclerView backed by {@link AssistantHistoryAdapter}; only changed exchanges rebind.
 * When no Claude API key is configured, the screen shows a hint pointing to settings instead of
 * erroring. The task list refreshes itself on show, so applying/undoing a task change here needs no
 * cross-ViewModel wiring.
 */
public class TaskAssistantFragment extends Fragment {

    private static final String[] ATTACH_MIME_TYPES =
            {"application/pdf", "text/plain", "text/markdown", "application/octet-stream"};
    private static final int MAX_ATTACHMENT_BYTES = 8 * 1024 * 1024;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Registered in a field initializer so it is ready before onStart (Android requirement).
    private final ActivityResultLauncher<String[]> attachmentLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onAttachmentPicked);

    private TaskAssistantViewModel viewModel;
    private ContentDocumentReader contentDocumentReader;
    private ExecutorService ioExecutor;
    private ClaudeModelStore modelStore;
    private ClaudeApiKeyStore apiKeyStore;
    private AssistantHistoryAdapter historyAdapter;
    private int lastExchangeCount;

    private RecyclerView historyView;
    private View emptyState;
    private TextView emptyStateTitle;
    private TextView emptyStateSubtitle;
    private EditText instructionView;
    private Button sendButton;
    private ProgressBar loadingView;
    private Button undoButton;
    private Button newChatButton;
    private Button attachButton;
    private Spinner modelSpinner;
    private SwitchMaterial thinkingSwitch;
    private View attachmentChip;
    private TextView attachmentName;
    private Button attachmentClear;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.task_assistant_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        AutoSecretaryApplication app = AutoSecretaryApplication.from(requireContext());
        viewModel = new ViewModelProvider(requireActivity(), app.getTaskAssistantViewModelFactory())
                .get(TaskAssistantViewModel.class);
        contentDocumentReader = app.getContentDocumentReader();
        ioExecutor = app.getIoExecutor();
        modelStore = new ClaudeModelStore(requireContext());
        apiKeyStore = new ClaudeApiKeyStore(requireContext());

        historyView = view.findViewById(R.id.AssistantHistory);
        emptyState = view.findViewById(R.id.AssistantEmptyState);
        emptyStateTitle = view.findViewById(R.id.EmptyStateTitle);
        emptyStateSubtitle = view.findViewById(R.id.EmptyStateSubtitle);
        instructionView = view.findViewById(R.id.AssistantInstruction);
        sendButton = view.findViewById(R.id.AssistantProposeButton);
        loadingView = view.findViewById(R.id.AssistantLoading);
        undoButton = view.findViewById(R.id.AssistantUndoButton);
        newChatButton = view.findViewById(R.id.AssistantNewChatButton);
        attachButton = view.findViewById(R.id.AssistantAttachButton);
        modelSpinner = view.findViewById(R.id.AssistantModelSpinner);
        thinkingSwitch = view.findViewById(R.id.AssistantThinkingSwitch);
        attachmentChip = view.findViewById(R.id.AssistantAttachmentChip);
        attachmentName = view.findViewById(R.id.AssistantAttachmentName);
        attachmentClear = view.findViewById(R.id.AssistantAttachmentClear);

        historyAdapter = new AssistantHistoryAdapter(this::confirmProposal);
        historyView.setLayoutManager(new LinearLayoutManager(requireContext()));
        historyView.setAdapter(historyAdapter);

        sendButton.setOnClickListener(v -> sendMessage());
        undoButton.setOnClickListener(v -> undoLast());
        newChatButton.setOnClickListener(v -> startNewChat());
        attachButton.setOnClickListener(v -> attachmentLauncher.launch(ATTACH_MIME_TYPES));
        attachmentClear.setOnClickListener(v -> viewModel.clearPendingAttachment());
        instructionView.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                viewModel.setDraftText(s.toString());
            }
        });

        setupModelSpinner();
        thinkingSwitch.setChecked(viewModel.isThinkingEnabled());
        thinkingSwitch.setOnCheckedChangeListener((button, checked) -> viewModel.setThinkingEnabled(checked));
        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
    }

    @Override
    public void onResume() {
        super.onResume();
        // The user may configure the key in settings and return without the fragment being recreated.
        AssistantUiState state = viewModel.getState().getValue();
        if (state != null) {
            render(state);
        }
    }

    // ---- Model picker ------------------------------------------------------

    private void setupModelSpinner() {
        rebuildModelSpinner();
        modelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String value = String.valueOf(parent.getItemAtPosition(position));
                if (getString(R.string.task_assistant_model_custom).equals(value)) {
                    showCustomModelDialog();
                } else if (!value.equals(modelStore.getModel())) {
                    modelStore.setModel(value);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void rebuildModelSpinner() {
        String current = modelStore.getModel();
        List<String> items = new ArrayList<>();
        for (String model : getResources().getStringArray(R.array.task_assistant_models)) {
            items.add(model);
        }
        if (!items.contains(current)) {
            items.add(current);
        }
        items.add(getString(R.string.task_assistant_model_custom));

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(requireContext(), R.layout.task_assistant_spinner_item, items);
        adapter.setDropDownViewResource(R.layout.task_assistant_spinner_item);
        modelSpinner.setAdapter(adapter);
        modelSpinner.setSelection(Math.max(0, items.indexOf(current)));
    }

    private void showCustomModelDialog() {
        EditText input = new EditText(requireContext());
        input.setHint(R.string.task_assistant_model_custom_hint);
        input.setText(modelStore.getModel());
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.task_assistant_model_custom_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (!value.isEmpty()) {
                        modelStore.setModel(value);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener(dialog -> rebuildModelSpinner())
                .show();
    }

    // ---- Key + empty state -------------------------------------------------

    private boolean hasApiKey() {
        return apiKeyStore.getApiKey() != null;
    }

    private void refreshChrome(AssistantUiState state) {
        boolean hasKey = hasApiKey();
        boolean idle = !state.sending();
        instructionView.setEnabled(hasKey && idle);
        attachButton.setEnabled(hasKey && idle);
        newChatButton.setEnabled(idle);
        // Undo is a header icon shown only when there is a task change to undo.
        boolean canUndo = hasKey && state.hasUndo();
        undoButton.setVisibility(canUndo ? View.VISIBLE : View.GONE);
        undoButton.setEnabled(canUndo && idle);
        setLoading(hasKey, state.sending());
        updateEmptyState(hasKey, !state.exchanges().isEmpty());
    }

    private void updateEmptyState(boolean hasKey, boolean hasHistory) {
        if (!hasKey) {
            emptyStateTitle.setText(R.string.task_assistant_no_key_title);
            emptyStateSubtitle.setText(R.string.task_assistant_no_key_subtitle);
        } else {
            emptyStateTitle.setText(R.string.task_assistant_onboarding_title);
            emptyStateSubtitle.setText(R.string.task_assistant_onboarding_subtitle);
        }
        boolean showEmpty = !hasHistory;
        emptyState.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        historyView.setVisibility(showEmpty ? View.GONE : View.VISIBLE);
    }

    // ---- Attachments -------------------------------------------------------

    private void onAttachmentPicked(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        ioExecutor.execute(() -> {
            try {
                DocumentContents contents = contentDocumentReader.read(uri);
                mainHandler.post(() -> onAttachmentRead(contents));
            } catch (IOException e) {
                mainHandler.post(() -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(),
                                R.string.task_assistant_attachment_read_error, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void onAttachmentRead(DocumentContents contents) {
        if (!isSupportedAttachment(contents)) {
            if (isAdded()) {
                Toast.makeText(requireContext(), R.string.task_assistant_attachment_invalid, Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (contents.bytes().length > MAX_ATTACHMENT_BYTES) {
            if (isAdded()) {
                Toast.makeText(requireContext(), R.string.task_assistant_attachment_too_large, Toast.LENGTH_LONG).show();
            }
            return;
        }
        viewModel.setPendingAttachment(contents.displayName(), contents.mimeType(), contents.bytes());
    }

    private static boolean isSupportedAttachment(DocumentContents contents) {
        String name = contents.displayName() != null ? contents.displayName().toLowerCase(Locale.ROOT) : "";
        if (name.endsWith(".pdf") || name.endsWith(".txt") || name.endsWith(".md")) {
            return true;
        }
        String mime = contents.mimeType() != null ? contents.mimeType().toLowerCase(Locale.ROOT) : "";
        return mime.equals("application/pdf") || mime.startsWith("text/");
    }

    // ---- Sending -----------------------------------------------------------

    private void sendMessage() {
        if (!hasApiKey()) {
            AssistantUiState state = viewModel.getState().getValue();
            if (state != null) {
                refreshChrome(state);
            }
            return;
        }
        if (viewModel.isSending()) {
            Toast.makeText(requireContext(), R.string.task_assistant_send_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }
        String text = instructionView.getText().toString().trim();
        viewModel.setDraftText(text);
        if (text.isEmpty() && !viewModel.hasPendingAttachment()) {
            Toast.makeText(requireContext(), R.string.task_assistant_empty_instruction, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!viewModel.sendDraft()) {
            Toast.makeText(requireContext(), R.string.task_assistant_send_in_progress, Toast.LENGTH_SHORT).show();
        }
    }

    private void startNewChat() {
        if (!viewModel.newChat()) {
            Toast.makeText(requireContext(), R.string.task_assistant_send_in_progress, Toast.LENGTH_SHORT).show();
        }
    }

    private void undoLast() {
        viewModel.undoLast(undone -> {
            if (!isAdded()) {
                return;
            }
            Toast.makeText(requireContext(),
                    undone ? R.string.task_assistant_undone : R.string.task_assistant_nothing_to_undo,
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void confirmProposal(long exchangeId, int proposalIndex) {
        viewModel.confirm(exchangeId, proposalIndex, summary -> {
            if (isAdded()) {
                Toast.makeText(requireContext(), summary, Toast.LENGTH_SHORT).show();
            }
        }, this::showError);
    }

    private void showError(String message) {
        if (!isAdded()) {
            return;
        }
        Toast.makeText(requireContext(),
                message != null ? message : getString(R.string.task_assistant_no_changes),
                Toast.LENGTH_LONG).show();
    }

    // ---- Rendering ---------------------------------------------------------

    private void render(AssistantUiState state) {
        if (!isAdded()) {
            return;
        }
        renderInputState(state);
        historyAdapter.submit(state.exchanges());
        refreshChrome(state);
        if (state.exchanges().size() != lastExchangeCount) {
            lastExchangeCount = state.exchanges().size();
            scrollToBottom();
        }
    }

    private void renderInputState(AssistantUiState state) {
        String draft = viewModel.getDraftText();
        if (!instructionView.getText().toString().equals(draft)) {
            instructionView.setText(draft);
            instructionView.setSelection(instructionView.getText().length());
        }
        if (state.attachmentName() == null) {
            attachmentChip.setVisibility(View.GONE);
            attachmentName.setText("");
        } else {
            attachmentName.setText(getString(R.string.task_assistant_attachment_label, state.attachmentName()));
            attachmentChip.setVisibility(View.VISIBLE);
        }
    }

    private void setLoading(boolean hasKey, boolean loading) {
        loadingView.setVisibility(loading ? View.VISIBLE : View.GONE);
        sendButton.setEnabled(hasKey && !loading);
        sendButton.setAlpha((!hasKey || loading) ? UiConstants.ALPHA_DISABLED : UiConstants.ALPHA_ENABLED);
    }

    private void scrollToBottom() {
        int count = historyAdapter.getItemCount();
        if (count > 0) {
            historyView.post(() -> historyView.scrollToPosition(count - 1));
        }
    }
}
