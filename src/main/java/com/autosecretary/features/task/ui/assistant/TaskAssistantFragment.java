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
import android.widget.ScrollView;
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

import com.autosecretary.R;
import com.autosecretary.app.AutoSecretaryApplication;
import com.autosecretary.features.task.ui.assistant.TaskAssistantViewModel.AssistantExchange;
import com.autosecretary.features.task.ui.assistant.TaskAssistantViewModel.PendingAttachment;
import com.autosecretary.features.task.ui.assistant.TaskAssistantViewModel.ProposalHandle;
import com.autosecretary.features.task.ui.assistant.TaskAssistantViewModel.ProposalStatus;
import com.autosecretary.features.task.ui.assistant.TaskAssistantViewModel.Status;
import com.autosecretary.features.task.ui.list.TaskViewModel;
import com.autosecretary.features.task.ui.list.TaskViewModelFactory;
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
 * <p>Everything runs through {@link TaskAssistantViewModel} (activity-scoped, so its chat history
 * survives tab switches). When no Claude API key is configured, the screen shows a hint pointing to
 * settings instead of an error toast.
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
    private TaskViewModel taskViewModel;
    private ContentDocumentReader contentDocumentReader;
    private ExecutorService ioExecutor;
    private ClaudeModelStore modelStore;

    private ScrollView historyScroll;
    private ViewGroup historyContainer;
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
        TaskViewModelFactory taskFactory = app.getTaskViewModelFactory();
        taskViewModel = new ViewModelProvider(requireActivity(), taskFactory).get(TaskViewModel.class);
        contentDocumentReader = app.getContentDocumentReader();
        ioExecutor = app.getIoExecutor();
        modelStore = new ClaudeModelStore(requireContext());

        historyScroll = view.findViewById(R.id.AssistantHistoryScroll);
        historyContainer = view.findViewById(R.id.AssistantHistory);
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

        sendButton.setOnClickListener(v -> sendMessage());
        undoButton.setOnClickListener(v -> undoLast());
        newChatButton.setOnClickListener(v -> startNewChat());
        attachButton.setOnClickListener(v -> attachmentLauncher.launch(ATTACH_MIME_TYPES));
        attachmentClear.setOnClickListener(v -> clearAttachment());
        instructionView.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                viewModel.setDraftText(s.toString());
            }
        });

        setupModelSpinner();
        thinkingSwitch.setChecked(viewModel.isThinkingEnabled());
        thinkingSwitch.setOnCheckedChangeListener((button, checked) -> viewModel.setThinkingEnabled(checked));
        viewModel.getStateVersion().observe(getViewLifecycleOwner(), ignored -> renderAssistantState());

        renderAssistantState();
    }

    @Override
    public void onResume() {
        super.onResume();
        // The user may configure the key in settings and return without the fragment being recreated.
        renderAssistantState();
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
        return new ClaudeApiKeyStore(requireContext()).getApiKey() != null;
    }

    private void refreshKeyState() {
        boolean hasKey = hasApiKey();
        boolean idle = !viewModel.isSending();
        instructionView.setEnabled(hasKey && idle);
        attachButton.setEnabled(hasKey && idle);
        newChatButton.setEnabled(idle);
        // Undo is a header icon shown only when there is a task change to undo.
        boolean canUndo = hasKey && viewModel.hasUndo();
        undoButton.setVisibility(canUndo ? View.VISIBLE : View.GONE);
        undoButton.setEnabled(canUndo && idle);
        setLoading(hasKey, viewModel.isSending());
        updateEmptyState(hasKey);
    }

    private void updateEmptyState(boolean hasKey) {
        boolean hasHistory = !viewModel.getHistory().isEmpty();
        if (!hasKey) {
            emptyStateTitle.setText(R.string.task_assistant_no_key_title);
            emptyStateSubtitle.setText(R.string.task_assistant_no_key_subtitle);
        } else {
            emptyStateTitle.setText(R.string.task_assistant_onboarding_title);
            emptyStateSubtitle.setText(R.string.task_assistant_onboarding_subtitle);
        }
        boolean showEmpty = !hasHistory;
        emptyState.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        historyScroll.setVisibility(showEmpty ? View.GONE : View.VISIBLE);
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
        if (isAdded()) {
            renderAssistantState();
        }
    }

    private static boolean isSupportedAttachment(DocumentContents contents) {
        String name = contents.displayName() != null ? contents.displayName().toLowerCase(Locale.ROOT) : "";
        if (name.endsWith(".pdf") || name.endsWith(".txt") || name.endsWith(".md")) {
            return true;
        }
        String mime = contents.mimeType() != null ? contents.mimeType().toLowerCase(Locale.ROOT) : "";
        return mime.equals("application/pdf") || mime.startsWith("text/");
    }

    private void clearAttachment() {
        viewModel.clearPendingAttachment();
        renderAssistantState();
    }

    // ---- Sending -----------------------------------------------------------

    private void sendMessage() {
        if (!hasApiKey()) {
            refreshKeyState();
            return;
        }
        if (viewModel.isSending()) {
            Toast.makeText(requireContext(), R.string.task_assistant_send_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }
        String text = instructionView.getText().toString().trim();
        viewModel.setDraftText(text);
        PendingAttachment attachment = viewModel.getPendingAttachment();
        if (text.isEmpty() && attachment == null) {
            Toast.makeText(requireContext(), R.string.task_assistant_empty_instruction, Toast.LENGTH_SHORT).show();
            return;
        }
        boolean accepted = viewModel.sendDraft(this::onAnswered, this::onSendError);
        if (!accepted) {
            Toast.makeText(requireContext(), R.string.task_assistant_send_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }
        renderAssistantState();
        scrollToBottom();
    }

    private void onAnswered(AssistantExchange exchange) {
        if (!isAdded()) {
            return;
        }
        renderAssistantState();
        scrollToBottom();
    }

    private void onSendError(AssistantExchange exchange) {
        if (!isAdded()) {
            return;
        }
        renderAssistantState();
        scrollToBottom();
    }

    private void startNewChat() {
        if (!viewModel.newChat()) {
            Toast.makeText(requireContext(), R.string.task_assistant_send_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }
        renderAssistantState();
    }

    private void undoLast() {
        viewModel.undoLast(undone -> {
            if (!isAdded()) {
                return;
            }
            Toast.makeText(requireContext(),
                    undone ? R.string.task_assistant_undone : R.string.task_assistant_nothing_to_undo,
                    Toast.LENGTH_SHORT).show();
            if (undone) {
                taskViewModel.refreshList();
                renderHistory();
            }
            refreshKeyState();
        });
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

    private void renderHistory() {
        historyContainer.removeAllViews();
        List<AssistantExchange> history = viewModel.getHistory();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (AssistantExchange exchange : history) {
            View item = inflater.inflate(R.layout.task_assistant_exchange_item, historyContainer, false);
            bindExchange(inflater, item, exchange);
            historyContainer.addView(item);
        }
    }

    private void renderAssistantState() {
        if (!isAdded()) {
            return;
        }
        renderInputState();
        renderHistory();
        refreshKeyState();
    }

    private void renderInputState() {
        String draft = viewModel.getDraftText();
        if (!instructionView.getText().toString().equals(draft)) {
            instructionView.setText(draft);
            instructionView.setSelection(instructionView.getText().length());
        }
        PendingAttachment attachment = viewModel.getPendingAttachment();
        if (attachment == null) {
            attachmentChip.setVisibility(View.GONE);
            attachmentName.setText("");
        } else {
            attachmentName.setText("📎 " + attachment.displayName());
            attachmentChip.setVisibility(View.VISIBLE);
        }
    }

    private void bindExchange(LayoutInflater inflater, View item, AssistantExchange exchange) {
        TextView userText = item.findViewById(R.id.ExchangeUserText);
        TextView attachment = item.findViewById(R.id.ExchangeAttachment);
        TextView thinkingToggle = item.findViewById(R.id.ExchangeThinkingToggle);
        TextView thinkingBody = item.findViewById(R.id.ExchangeThinkingBody);
        TextView answer = item.findViewById(R.id.ExchangeAnswer);
        ViewGroup proposals = item.findViewById(R.id.ExchangeProposals);
        TextView status = item.findViewById(R.id.ExchangeStatus);

        userText.setText(exchange.userText());
        if (exchange.attachmentName() != null) {
            attachment.setText("📎 " + exchange.attachmentName());
            attachment.setVisibility(View.VISIBLE);
        } else {
            attachment.setVisibility(View.GONE);
        }

        if (exchange.status() == Status.PENDING) {
            thinkingToggle.setVisibility(View.GONE);
            answer.setVisibility(View.GONE);
            proposals.removeAllViews();
            // The progress channel streams the model's live thinking; show it in the roomy, selectable
            // thinking body and keep the status line as a steady "denkt nach"-style label.
            String progress = viewModel.getPendingProgressText();
            if (progress != null && !progress.isBlank()) {
                thinkingBody.setText(progress);
                thinkingBody.setVisibility(View.VISIBLE);
            } else {
                thinkingBody.setVisibility(View.GONE);
            }
            status.setText(getString(R.string.task_assistant_status_pending));
            status.setVisibility(View.VISIBLE);
            return;
        }

        if (exchange.status() == Status.ERROR) {
            thinkingToggle.setVisibility(View.GONE);
            thinkingBody.setVisibility(View.GONE);
            answer.setVisibility(View.GONE);
            proposals.removeAllViews();
            status.setText(exchange.errorMessage() != null
                    ? exchange.errorMessage() : getString(R.string.task_assistant_no_changes));
            status.setVisibility(View.VISIBLE);
            return;
        }

        bindThinking(thinkingToggle, thinkingBody, exchange.thinkingText());

        String answerText = exchange.answerText();
        if (answerText != null && !answerText.isBlank()) {
            answer.setText(answerText);
            answer.setVisibility(View.VISIBLE);
        } else {
            answer.setVisibility(View.GONE);
        }

        proposals.removeAllViews();
        for (ProposalHandle handle : exchange.proposals()) {
            proposals.addView(buildProposalCard(inflater, proposals, handle));
        }
        status.setVisibility(View.GONE);
    }

    private void bindThinking(TextView toggle, TextView body, String thinkingText) {
        if (thinkingText == null || thinkingText.isBlank()) {
            toggle.setVisibility(View.GONE);
            body.setVisibility(View.GONE);
            return;
        }
        body.setText(thinkingText);
        body.setVisibility(View.GONE);
        toggle.setText(R.string.task_assistant_thinking_show);
        toggle.setVisibility(View.VISIBLE);
        toggle.setOnClickListener(v -> {
            boolean expand = body.getVisibility() != View.VISIBLE;
            body.setVisibility(expand ? View.VISIBLE : View.GONE);
            toggle.setText(expand ? R.string.task_assistant_thinking_hide : R.string.task_assistant_thinking_show);
        });
    }

    private View buildProposalCard(LayoutInflater inflater, ViewGroup parent, ProposalHandle handle) {
        View card = inflater.inflate(R.layout.task_assistant_proposal_card, parent, false);
        TextView summary = card.findViewById(R.id.ProposalSummary);
        Button applyButton = card.findViewById(R.id.ProposalApplyButton);
        TextView status = card.findViewById(R.id.ProposalStatus);

        summary.setText(AssistantProposalFormatter.summary(requireContext(), handle.proposal()));

        boolean applied = handle.status() == ProposalStatus.APPLIED;
        applyButton.setVisibility(applied ? View.GONE : View.VISIBLE);
        status.setVisibility(applied ? View.VISIBLE : View.GONE);
        applyButton.setOnClickListener(applied ? null : v -> confirmProposal(handle));
        return card;
    }

    private void confirmProposal(ProposalHandle handle) {
        viewModel.confirm(handle, summary -> {
            if (!isAdded()) {
                return;
            }
            Toast.makeText(requireContext(), summary, Toast.LENGTH_SHORT).show();
            taskViewModel.refreshList();
            renderHistory();
            refreshKeyState();
        }, this::showError);
    }

    private void setLoading(boolean hasKey, boolean loading) {
        loadingView.setVisibility(loading ? View.VISIBLE : View.GONE);
        sendButton.setEnabled(hasKey && !loading);
        sendButton.setAlpha((!hasKey || loading) ? UiConstants.ALPHA_DISABLED : UiConstants.ALPHA_ENABLED);
    }

    private void scrollToBottom() {
        historyScroll.post(() -> historyScroll.fullScroll(View.FOCUS_DOWN));
    }
}
