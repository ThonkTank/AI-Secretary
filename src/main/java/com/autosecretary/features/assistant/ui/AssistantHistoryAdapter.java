package com.autosecretary.features.assistant.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.autosecretary.R;
import com.autosecretary.features.assistant.ui.AssistantUiState.ExchangeItem;
import com.autosecretary.features.assistant.ui.AssistantUiState.ProposalItem;
import com.autosecretary.features.assistant.ui.AssistantUiState.ProposalStatus;
import com.autosecretary.features.assistant.ui.AssistantUiState.Status;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders the assistant chat history in a RecyclerView. The ViewModel publishes immutable
 * {@link ExchangeItem}s and only ever replaces changed ones, so item identity is the stable
 * {@code id} and content equality is reference equality — during streaming exactly one row (the
 * pending exchange) rebinds per tick. The diff is computed synchronously so a {@link #submit} is
 * fully applied when it returns. Thinking-section expansion lives here (per exchange id) so it
 * survives rebinds and streaming.
 */
final class AssistantHistoryAdapter extends RecyclerView.Adapter<AssistantHistoryAdapter.ExchangeViewHolder> {

    /** Confirms the proposal at {@code proposalIndex} of the exchange with {@code exchangeId}. */
    interface ProposalListener {
        void onConfirm(long exchangeId, int proposalIndex);
    }

    private final List<ExchangeItem> items = new ArrayList<>();
    private final Set<Long> expandedThinking = new HashSet<>();
    private final ProposalListener proposalListener;

    AssistantHistoryAdapter(ProposalListener proposalListener) {
        this.proposalListener = proposalListener;
    }

    void submit(List<ExchangeItem> newItems) {
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffCallback(items, newItems));
        items.clear();
        items.addAll(newItems);
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ExchangeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.assistant_exchange_item, parent, false);
        return new ExchangeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ExchangeViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    final class ExchangeViewHolder extends RecyclerView.ViewHolder {
        private final TextView userText;
        private final TextView attachment;
        private final TextView thinkingToggle;
        private final TextView thinkingBody;
        private final TextView answer;
        private final ViewGroup proposals;
        private final TextView status;

        ExchangeViewHolder(@NonNull View item) {
            super(item);
            userText = item.findViewById(R.id.ExchangeUserText);
            attachment = item.findViewById(R.id.ExchangeAttachment);
            thinkingToggle = item.findViewById(R.id.ExchangeThinkingToggle);
            thinkingBody = item.findViewById(R.id.ExchangeThinkingBody);
            answer = item.findViewById(R.id.ExchangeAnswer);
            proposals = item.findViewById(R.id.ExchangeProposals);
            status = item.findViewById(R.id.ExchangeStatus);
        }

        void bind(ExchangeItem exchange) {
            userText.setText(exchange.userText());
            if (exchange.attachmentName() != null) {
                attachment.setText(context().getString(
                        R.string.assistant_attachment_label, exchange.attachmentName()));
                attachment.setVisibility(View.VISIBLE);
            } else {
                attachment.setVisibility(View.GONE);
            }

            if (exchange.status() == Status.PENDING) {
                bindPending(exchange);
                return;
            }
            if (exchange.status() == Status.ERROR) {
                bindError(exchange);
                return;
            }
            bindAnswered(exchange);
        }

        private void bindPending(ExchangeItem exchange) {
            thinkingToggle.setVisibility(View.GONE);
            answer.setVisibility(View.GONE);
            proposals.removeAllViews();
            // The progress channel streams the model's live thinking; show it in the roomy, selectable
            // thinking body and keep the status line as a steady "denkt nach"-style label.
            String progress = exchange.progressText();
            if (progress != null && !progress.isBlank()) {
                thinkingBody.setText(progress);
                thinkingBody.setVisibility(View.VISIBLE);
            } else {
                thinkingBody.setVisibility(View.GONE);
            }
            status.setText(context().getString(R.string.assistant_status_pending));
            status.setVisibility(View.VISIBLE);
        }

        private void bindError(ExchangeItem exchange) {
            thinkingToggle.setVisibility(View.GONE);
            thinkingBody.setVisibility(View.GONE);
            answer.setVisibility(View.GONE);
            proposals.removeAllViews();
            status.setText(exchange.errorMessage() != null
                    ? exchange.errorMessage() : context().getString(R.string.assistant_no_changes));
            status.setVisibility(View.VISIBLE);
        }

        private void bindAnswered(ExchangeItem exchange) {
            bindThinking(exchange);

            String answerText = exchange.answerText();
            if (answerText != null && !answerText.isBlank()) {
                answer.setText(answerText);
                answer.setVisibility(View.VISIBLE);
            } else {
                answer.setVisibility(View.GONE);
            }

            proposals.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(context());
            List<ProposalItem> cards = exchange.proposals();
            for (int i = 0; i < cards.size(); i++) {
                proposals.addView(buildProposalCard(inflater, exchange.id(), i, cards.get(i)));
            }
            status.setVisibility(View.GONE);
        }

        private void bindThinking(ExchangeItem exchange) {
            String thinkingText = exchange.thinkingText();
            if (thinkingText == null || thinkingText.isBlank()) {
                thinkingToggle.setVisibility(View.GONE);
                thinkingBody.setVisibility(View.GONE);
                return;
            }
            long id = exchange.id();
            thinkingBody.setText(thinkingText);
            thinkingToggle.setVisibility(View.VISIBLE);
            applyThinkingExpansion(id, expandedThinking.contains(id));
            thinkingToggle.setOnClickListener(v -> {
                boolean expand = !expandedThinking.contains(id);
                if (expand) {
                    expandedThinking.add(id);
                } else {
                    expandedThinking.remove(id);
                }
                applyThinkingExpansion(id, expand);
            });
        }

        private void applyThinkingExpansion(long id, boolean expanded) {
            thinkingBody.setVisibility(expanded ? View.VISIBLE : View.GONE);
            thinkingToggle.setText(expanded
                    ? R.string.assistant_thinking_hide : R.string.assistant_thinking_show);
        }

        private View buildProposalCard(LayoutInflater inflater, long exchangeId, int proposalIndex,
                                       ProposalItem item) {
            View card = inflater.inflate(R.layout.assistant_proposal_item, proposals, false);
            TextView summary = card.findViewById(R.id.ProposalSummary);
            Button applyButton = card.findViewById(R.id.ProposalApplyButton);
            TextView cardStatus = card.findViewById(R.id.ProposalStatus);

            summary.setText(AssistantProposalFormatter.summary(context(), item.proposal()));

            boolean applied = item.status() == ProposalStatus.APPLIED;
            applyButton.setVisibility(applied ? View.GONE : View.VISIBLE);
            cardStatus.setVisibility(applied ? View.VISIBLE : View.GONE);
            applyButton.setOnClickListener(applied ? null
                    : v -> proposalListener.onConfirm(exchangeId, proposalIndex));
            return card;
        }

        private android.content.Context context() {
            return itemView.getContext();
        }
    }

    /** Items are the same iff their stable id matches; content equality is reference equality. */
    private static final class DiffCallback extends DiffUtil.Callback {
        private final List<ExchangeItem> oldItems;
        private final List<ExchangeItem> newItems;

        DiffCallback(List<ExchangeItem> oldItems, List<ExchangeItem> newItems) {
            this.oldItems = oldItems;
            this.newItems = newItems;
        }

        @Override
        public int getOldListSize() {
            return oldItems.size();
        }

        @Override
        public int getNewListSize() {
            return newItems.size();
        }

        @Override
        public boolean areItemsTheSame(int oldPos, int newPos) {
            return oldItems.get(oldPos).id() == newItems.get(newPos).id();
        }

        @Override
        public boolean areContentsTheSame(int oldPos, int newPos) {
            // The ViewModel replaces changed exchanges and keeps unchanged ones, so an unchanged item
            // is the same object — reference equality avoids a deep compare over domain proposals.
            return oldItems.get(oldPos) == newItems.get(newPos);
        }
    }
}
