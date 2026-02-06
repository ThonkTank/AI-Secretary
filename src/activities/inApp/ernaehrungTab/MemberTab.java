package activities.inApp.ernaehrungTab;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import static activities.generic.ViewHelper.dp;
import static activities.generic.ViewHelper.parseInt;
import static activities.generic.ViewHelper.roundedBg;
import static activities.generic.ViewHelper.setupModalOverlay;
import static activities.generic.ViewHelper.spinnerAdapter;

import androidx.core.content.ContextCompat;

import com.autosecretary.R;

import java.util.ArrayList;
import java.util.List;

import controller.MealManager;
import controller.MealManager.MemberEntry;
import entities.HouseholdMember;

/**
 * Member-Verwaltung: Collapsible Haushalt-Sektion + Member CRUD Modal.
 * Extrahiert aus WeekPlanTab fuer bessere Separation of Concerns.
 */
public class MemberTab {

    private final Context context;
    private final MealManager manager;

    // State
    private boolean memberSectionExpanded = false;
    private List<MemberEntry> membersList = new ArrayList<>();

    // Member Modal
    private FrameLayout modalMemberOverlay;
    private TextView modalMemberTitle;
    private EditText inputMemberName;
    private EditText inputMemberBirthYear;
    private Spinner spinnerMemberGender;
    private EditText inputMemberHeight;
    private EditText inputMemberWeight;
    private Spinner spinnerMemberActivity;
    private TextView memberTdeePreview;
    private TextView btnMemberDelete;
    private TextView btnMemberCancel;
    private TextView btnMemberSave;
    private HouseholdMember editingMember;

    // Callback
    private MealTabListener listener;

    // ============================================================================
    // CONSTRUCTOR + INIT
    // ============================================================================

    public MemberTab(Context context, MealManager manager) {
        this.context = context;
        this.manager = manager;
    }

    public void setListener(MealTabListener listener) {
        this.listener = listener;
    }

    /**
     * Inflated und bindet das Member-Modal.
     * Jeder Sub-Tab besitzt sein eigenes Modal (kein Zugriff auf Parent-Layout).
     */
    public void initModals(FrameLayout rootContainer) {
        modalMemberOverlay = (FrameLayout) LayoutInflater.from(context)
            .inflate(R.layout.modal_member, rootContainer, false);
        modalMemberOverlay.setVisibility(View.GONE);
        rootContainer.addView(modalMemberOverlay);

        modalMemberTitle = modalMemberOverlay.findViewById(R.id.modal_member_title);
        inputMemberName = modalMemberOverlay.findViewById(R.id.input_member_name);
        inputMemberBirthYear = modalMemberOverlay.findViewById(R.id.input_member_birth_year);
        spinnerMemberGender = modalMemberOverlay.findViewById(R.id.spinner_member_gender);
        inputMemberHeight = modalMemberOverlay.findViewById(R.id.input_member_height);
        inputMemberWeight = modalMemberOverlay.findViewById(R.id.input_member_weight);
        spinnerMemberActivity = modalMemberOverlay.findViewById(R.id.spinner_member_activity);
        memberTdeePreview = modalMemberOverlay.findViewById(R.id.member_tdee_preview);
        btnMemberDelete = modalMemberOverlay.findViewById(R.id.btn_member_delete);
        btnMemberCancel = modalMemberOverlay.findViewById(R.id.btn_member_cancel);
        btnMemberSave = modalMemberOverlay.findViewById(R.id.btn_member_save);

        setupMemberModal();
    }

    // ============================================================================
    // RENDER - Collapsible Member Section
    // ============================================================================

    /**
     * Rendert die klappbare Haushalt-Sektion ins gegebene LinearLayout.
     */
    public void render(LinearLayout parent) {
        // Header
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        headerRow.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));

        TextView headerLabel = new TextView(context);
        headerLabel.setText("Haushalt");
        headerLabel.setTextSize(14);
        headerLabel.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
        headerLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        headerLabel.setLayoutParams(lp);
        headerRow.addView(headerLabel);

        TextView expandIcon = new TextView(context);
        expandIcon.setText(memberSectionExpanded ? "\u25BC" : "\u25B6");
        expandIcon.setTextSize(12);
        expandIcon.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
        expandIcon.setPadding(dp(context, 12), 0, dp(context, 4), 0);
        headerRow.addView(expandIcon);

        parent.addView(headerRow);

        // Content
        HorizontalScrollView memberScroll = buildMemberSection();
        memberScroll.setVisibility(memberSectionExpanded ? View.VISIBLE : View.GONE);
        parent.addView(memberScroll);

        // Toggle
        headerRow.setOnClickListener(v -> {
            memberSectionExpanded = !memberSectionExpanded;
            expandIcon.setText(memberSectionExpanded ? "\u25BC" : "\u25B6");
            memberScroll.setVisibility(memberSectionExpanded ? View.VISIBLE : View.GONE);
        });
    }

    private HorizontalScrollView buildMemberSection() {
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 0, 0, dp(context, 8));

        membersList = manager.provideMembers();
        LayoutInflater inflater = LayoutInflater.from(context);

        for (MemberEntry member : membersList) {
            View card = inflater.inflate(R.layout.item_member_card, row, false);

            TextView nameView = card.findViewById(R.id.member_name);
            TextView infoView = card.findViewById(R.id.member_info);
            TextView caloriesView = card.findViewById(R.id.member_calories);

            nameView.setText(member.name());
            infoView.setText(member.age() + " Jahre \u2022 " + member.activityLabel());
            caloriesView.setText(String.format("%,d kcal/Tag", member.dailyCalories()).replace(",", "."));

            int cardBg = ContextCompat.getColor(context, R.color.surface_card);
            card.setBackground(roundedBg(context, cardBg, 8));

            final Long memberId = member.id();
            card.setOnClickListener(v -> showMemberModal(memberId));

            row.addView(card);
        }

        // "+ Mitglied" Button
        TextView addButton = new TextView(context);
        addButton.setText("+ Mitglied");
        addButton.setTextSize(14);
        addButton.setTextColor(ContextCompat.getColor(context, R.color.accent));
        addButton.setGravity(android.view.Gravity.CENTER);
        addButton.setPadding(dp(context, 16), dp(context, 24), dp(context, 16), dp(context, 24));

        int bgColor = ContextCompat.getColor(context, R.color.surface_card);
        addButton.setBackground(roundedBg(context, bgColor, 8));

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
            dp(context, 100), LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(0, 0, dp(context, 8), 0);
        addButton.setLayoutParams(btnParams);

        addButton.setOnClickListener(v -> showMemberModal(null));
        row.addView(addButton);

        scroll.addView(row);
        return scroll;
    }

    // ============================================================================
    // MEMBER MODAL
    // ============================================================================

    private void setupMemberModal() {
        // Gender Spinner
        String[] genders = {"M\u00e4nnlich", "Weiblich", "Divers"};
        spinnerMemberGender.setAdapter(spinnerAdapter(context, genders));

        // Activity Spinner
        String[] activities = {"Sitzend", "Leicht aktiv", "Moderat aktiv", "Aktiv", "Sehr aktiv"};
        spinnerMemberActivity.setAdapter(spinnerAdapter(context, activities));

        // Buttons
        btnMemberCancel.setOnClickListener(v -> hideMemberModal());
        btnMemberSave.setOnClickListener(v -> saveMember());
        btnMemberDelete.setOnClickListener(v -> deleteMember());

        setupModalOverlay(modalMemberOverlay, this::hideMemberModal);

        // TDEE live update
        android.text.TextWatcher tdeeWatcher = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) { updateTdeePreview(); }
        };
        inputMemberBirthYear.addTextChangedListener(tdeeWatcher);
        inputMemberHeight.addTextChangedListener(tdeeWatcher);
        inputMemberWeight.addTextChangedListener(tdeeWatcher);
        spinnerMemberGender.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { updateTdeePreview(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        spinnerMemberActivity.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { updateTdeePreview(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
    }

    private void showMemberModal(Long memberId) {
        editingMember = memberId != null ? manager.getMember(memberId) : null;

        if (editingMember == null) {
            modalMemberTitle.setText("Mitglied hinzuf\u00fcgen");
            inputMemberName.setText("");
            inputMemberBirthYear.setText("1990");
            spinnerMemberGender.setSelection(0);
            inputMemberHeight.setText("175");
            inputMemberWeight.setText("70");
            spinnerMemberActivity.setSelection(1);
            btnMemberDelete.setVisibility(View.GONE);
        } else {
            modalMemberTitle.setText("Mitglied bearbeiten");
            inputMemberName.setText(editingMember.name);
            inputMemberBirthYear.setText(String.valueOf(editingMember.birthYear));
            spinnerMemberGender.setSelection(editingMember.gender.ordinal());
            inputMemberHeight.setText(String.valueOf(editingMember.heightCm));
            inputMemberWeight.setText(String.valueOf(editingMember.weightKg));
            spinnerMemberActivity.setSelection(editingMember.activityLevel.ordinal());
            btnMemberDelete.setVisibility(View.VISIBLE);
        }

        updateTdeePreview();
        modalMemberOverlay.setVisibility(View.VISIBLE);
    }

    private void hideMemberModal() {
        modalMemberOverlay.setVisibility(View.GONE);
        editingMember = null;
    }

    private void saveMember() {
        String name = inputMemberName.getText().toString().trim();
        if (name.isEmpty()) {
            inputMemberName.setError("Name erforderlich");
            return;
        }

        int birthYear = parseInt(inputMemberBirthYear, 1990);
        int height = parseInt(inputMemberHeight, 175);
        int weight = parseInt(inputMemberWeight, 70);

        HouseholdMember.Gender gender = HouseholdMember.Gender.values()[spinnerMemberGender.getSelectedItemPosition()];
        HouseholdMember.ActivityLevel activity = HouseholdMember.ActivityLevel.values()[spinnerMemberActivity.getSelectedItemPosition()];

        HouseholdMember member = new HouseholdMember.Builder(name)
            .birthYear(birthYear).gender(gender).heightCm(height).weightKg(weight)
            .activityLevel(activity).build();

        if (editingMember != null) {
            member.id = editingMember.id;
            member.isActive = editingMember.isActive;
            manager.updateMember(member);
        } else {
            manager.createMember(member);
        }

        hideMemberModal();
        notifyChanged();
    }

    private void deleteMember() {
        if (editingMember != null && editingMember.id != null) {
            new AlertDialog.Builder(context)
                .setTitle("Mitglied l\u00f6schen")
                .setMessage("Soll \"" + editingMember.name + "\" wirklich gel\u00f6scht werden?")
                .setPositiveButton("L\u00f6schen", (d, w) -> {
                    manager.deleteMember(editingMember.id);
                    hideMemberModal();
                    notifyChanged();
                })
                .setNegativeButton("Abbrechen", null)
                .show();
        }
    }

    private void updateTdeePreview() {
        try {
            int birthYear = Integer.parseInt(inputMemberBirthYear.getText().toString().trim());
            int height = Integer.parseInt(inputMemberHeight.getText().toString().trim());
            int weight = Integer.parseInt(inputMemberWeight.getText().toString().trim());
            HouseholdMember.Gender gender = HouseholdMember.Gender.values()[spinnerMemberGender.getSelectedItemPosition()];
            HouseholdMember.ActivityLevel activity = HouseholdMember.ActivityLevel.values()[spinnerMemberActivity.getSelectedItemPosition()];

            HouseholdMember temp = new HouseholdMember.Builder("temp")
                .birthYear(birthYear).gender(gender).heightCm(height).weightKg(weight)
                .activityLevel(activity).build();
            int tdee = temp.calculateTDEE();
            memberTdeePreview.setText("Berechneter Tagesbedarf: " + String.format("%,d", tdee).replace(",", ".") + " kcal");
        } catch (NumberFormatException e) {
            memberTdeePreview.setText("Berechneter Tagesbedarf: \u2014 kcal");
        }
    }

    // ============================================================================
    // HELPERS
    // ============================================================================

    private void notifyChanged() {
        if (listener != null) listener.onDataChanged();
    }

}
