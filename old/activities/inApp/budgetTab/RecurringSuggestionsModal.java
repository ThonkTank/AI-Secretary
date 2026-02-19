package activities.inApp.budgetTab;

import static activities.generic.ViewHelper.setupModalOverlay;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.autosecretary.R;

import java.util.ArrayList;
import java.util.List;

import controller.budgetTab.RecurringPatternDetector;
import controller.budgetTab.BudgetManager;
import data.BudgetDisplayData;

public class RecurringSuggestionsModal {

    private final Context context;
    private final BudgetManager manager;

    private FrameLayout overlay;
    private TextView recurringCountBadge;
    private LinearLayout candidatesContainer;
    private TextView textSelectionInfo;
    private TextView btnRecurringSkip;
    private TextView btnRecurringCreate;

    private List<RecurringPatternDetector.RecurringCandidate> currentCandidates = new ArrayList<>();
    private List<Boolean> candidateSelections = new ArrayList<>();
    private Long importAccountId = null;

    public RecurringSuggestionsModal(Context context, BudgetManager manager) {
        this.context = context;
        this.manager = manager;
    }

    public void init(FrameLayout overlay) {
        this.overlay = overlay;

        recurringCountBadge = overlay.findViewById(R.id.text_count_badge);
        candidatesContainer = overlay.findViewById(R.id.candidates_container);
        textSelectionInfo = overlay.findViewById(R.id.text_selection_info);
        btnRecurringSkip = overlay.findViewById(R.id.btn_skip);
        btnRecurringCreate = overlay.findViewById(R.id.btn_create);

        setup();
    }

    private void setup() {
        btnRecurringSkip.setOnClickListener(v -> hide());
        btnRecurringCreate.setOnClickListener(v -> createSelectedTemplates());

        setupModalOverlay(overlay, this::hide);
    }

    public void show(List<RecurringPatternDetector.RecurringCandidate> candidates, Long accountId) {
        currentCandidates = candidates;
        importAccountId = accountId;
        candidateSelections = new ArrayList<>();

        for (int i = 0; i < candidates.size(); i++) {
            candidateSelections.add(true);
        }

        recurringCountBadge.setText(String.valueOf(candidates.size()));

        candidatesContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);

        for (int i = 0; i < candidates.size(); i++) {
            final int index = i;
            RecurringPatternDetector.RecurringCandidate candidate = candidates.get(i);

            View row = inflater.inflate(R.layout.item_recurring_candidate, candidatesContainer, false);

            ImageView checkbox = row.findViewById(R.id.candidate_checkbox);
            TextView payeeText = row.findViewById(R.id.candidate_payee);
            TextView patternText = row.findViewById(R.id.candidate_pattern);
            TextView countText = row.findViewById(R.id.candidate_count);
            TextView confidenceText = row.findViewById(R.id.candidate_confidence);
            TextView amountText = row.findViewById(R.id.candidate_amount);

            payeeText.setText(candidate.displayPayee());
            patternText.setText(getPatternDescription(candidate));
            countText.setText(candidate.transactionIds().size() + " Transaktionen");
            confidenceText.setText(String.format("%.0f%% sicher", candidate.confidenceScore() * 100));
            amountText.setText(BudgetDisplayData.formatCents(candidate.avgAmountCents()));

            int amountColor = candidate.avgAmountCents() >= 0
                ? ContextCompat.getColor(context, R.color.budget_income)
                : ContextCompat.getColor(context, R.color.budget_expense);
            amountText.setTextColor(amountColor);

            int confidenceColor;
            if (candidate.confidenceScore() >= 0.7) {
                confidenceColor = ContextCompat.getColor(context, R.color.status_success);
            } else if (candidate.confidenceScore() >= 0.5) {
                confidenceColor = ContextCompat.getColor(context, R.color.budget_bar_warning);
            } else {
                confidenceColor = ContextCompat.getColor(context, R.color.text_muted);
            }
            confidenceText.setTextColor(confidenceColor);

            updateCheckboxIcon(checkbox, candidateSelections.get(index));

            row.setOnClickListener(v -> {
                candidateSelections.set(index, !candidateSelections.get(index));
                updateCheckboxIcon(checkbox, candidateSelections.get(index));
                updateSelectionInfo();
            });

            candidatesContainer.addView(row);
        }

        updateSelectionInfo();
        overlay.setVisibility(View.VISIBLE);
    }

    public void hide() {
        overlay.setVisibility(View.GONE);
        currentCandidates = new ArrayList<>();
        candidateSelections = new ArrayList<>();
    }

    private void updateCheckboxIcon(ImageView checkbox, boolean selected) {
        checkbox.setImageResource(selected
            ? android.R.drawable.checkbox_on_background
            : android.R.drawable.checkbox_off_background);
    }

    private void updateSelectionInfo() {
        int selected = 0;
        for (Boolean sel : candidateSelections) {
            if (sel) selected++;
        }
        textSelectionInfo.setText(selected + " von " + candidateSelections.size() + " ausgewaehlt");
        btnRecurringCreate.setText("Erstellen (" + selected + ")");
        btnRecurringCreate.setEnabled(selected > 0);
        btnRecurringCreate.setBackgroundColor(selected > 0
            ? ContextCompat.getColor(context, R.color.accent)
            : ContextCompat.getColor(context, R.color.button_inactive));
    }

    private String getPatternDescription(RecurringPatternDetector.RecurringCandidate candidate) {
        if (candidate.suggestedType() == null) return "Unbekanntes Muster";

        String amount = BudgetDisplayData.formatCents(Math.abs(candidate.avgAmountCents()));

        return switch (candidate.suggestedType()) {
            case MONTHLY_DAY -> "Monatlich am " + candidate.suggestedValue() + ". \u2022 ~" + amount;
            case MONTHLY_LAST -> "Monatlich am Monatsende \u2022 ~" + amount;
            case WEEKLY -> "Woechentlich " + getDayName(candidate.suggestedDayOfWeek()) + " \u2022 ~" + amount;
            case INTERVAL -> "Alle " + candidate.suggestedValue() + " Tage \u2022 ~" + amount;
        };
    }

    private String getDayName(java.time.DayOfWeek dow) {
        if (dow == null) return "";
        return switch (dow) {
            case MONDAY -> "Mo";
            case TUESDAY -> "Di";
            case WEDNESDAY -> "Mi";
            case THURSDAY -> "Do";
            case FRIDAY -> "Fr";
            case SATURDAY -> "Sa";
            case SUNDAY -> "So";
        };
    }

    private void createSelectedTemplates() {
        if (importAccountId == null) {
            hide();
            return;
        }

        int created = 0;
        for (int i = 0; i < currentCandidates.size(); i++) {
            if (candidateSelections.get(i)) {
                RecurringPatternDetector.RecurringCandidate candidate = currentCandidates.get(i);

                Long templateId = manager.createRecurringTemplate(candidate, importAccountId);

                if (templateId != null) {
                    manager.linkTransactionsToTemplate(candidate.transactionIds(), templateId);
                    created++;
                }
            }
        }

        manager.notifyDataUpdated();
        hide();
    }
}
