package activities.inApp.budgetTab;

import static activities.generic.ViewHelper.setupModalOverlay;
import static activities.generic.ViewHelper.spinnerAdapter;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.autosecretary.R;

import java.util.ArrayList;
import java.util.List;

import controller.ApiKeyManager;
import controller.ImportProcessor;
import controller.RecurringPatternDetector;
import controller.BudgetManager;
import controller.BudgetManager.AccountEntry;

public class ImportModal {

    public interface FilePickerCallback {
        void requestFilePicker();
    }

    public interface ImportSuccessCallback {
        void onSuccess(Long accountId, List<RecurringPatternDetector.RecurringCandidate> candidates);
    }

    private final Context context;
    private final BudgetManager manager;

    private FrameLayout overlay;
    private View root;
    private Spinner importSpinnerAccount;
    private TextView btnSelectFile;
    private TextView textFilename;
    private EditText inputApiKey;
    private TextView btnToggleKey;
    private CheckBox checkboxSaveKey;
    private LinearLayout progressSection;
    private ProgressBar progressBar;
    private TextView progressText;
    private LinearLayout resultSection;
    private TextView resultTitle;
    private TextView resultDetails;
    private TextView importErrorText;
    private TextView btnImportCancel;
    private TextView btnImport;

    private byte[] selectedFileBytes = null;
    private String selectedFileName = null;
    private String selectedFileMimeType = null;
    private boolean apiKeyVisible = false;
    private List<AccountEntry> accountsList = new ArrayList<>();

    private FilePickerCallback filePickerCallback;
    private ImportSuccessCallback importSuccessCallback;

    public ImportModal(Context context, BudgetManager manager) {
        this.context = context;
        this.manager = manager;
    }

    public void init(FrameLayout overlay, View root) {
        this.overlay = overlay;
        this.root = root;

        importSpinnerAccount = overlay.findViewById(R.id.spinner_account);
        btnSelectFile = overlay.findViewById(R.id.btn_select_file);
        textFilename = overlay.findViewById(R.id.text_filename);
        inputApiKey = overlay.findViewById(R.id.input_api_key);
        btnToggleKey = overlay.findViewById(R.id.btn_toggle_key);
        checkboxSaveKey = overlay.findViewById(R.id.checkbox_save_key);
        progressSection = overlay.findViewById(R.id.progress_section);
        progressBar = overlay.findViewById(R.id.progress_bar);
        progressText = overlay.findViewById(R.id.progress_text);
        resultSection = overlay.findViewById(R.id.result_section);
        resultTitle = overlay.findViewById(R.id.result_title);
        resultDetails = overlay.findViewById(R.id.result_details);
        importErrorText = overlay.findViewById(R.id.error_text);
        btnImportCancel = overlay.findViewById(R.id.btn_cancel);
        btnImport = overlay.findViewById(R.id.btn_import);

        setup();
    }

    public void setFilePickerCallback(FilePickerCallback callback) {
        this.filePickerCallback = callback;
    }

    public void setOnImportSuccess(ImportSuccessCallback callback) {
        this.importSuccessCallback = callback;
    }

    private void setup() {
        btnSelectFile.setOnClickListener(v -> {
            if (filePickerCallback != null) {
                filePickerCallback.requestFilePicker();
            }
        });

        btnToggleKey.setOnClickListener(v -> {
            apiKeyVisible = !apiKeyVisible;
            if (apiKeyVisible) {
                inputApiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                btnToggleKey.setText("\uD83D\uDE48");
            } else {
                inputApiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                btnToggleKey.setText("\uD83D\uDC41");
            }
            inputApiKey.setSelection(inputApiKey.getText().length());
        });

        btnImportCancel.setOnClickListener(v -> hide());
        btnImport.setOnClickListener(v -> startImport());

        setupModalOverlay(overlay, this::hide);
    }

    public void show() {
        selectedFileBytes = null;
        selectedFileName = null;
        selectedFileMimeType = null;
        textFilename.setText("Keine Datei ausgewaehlt");
        importErrorText.setVisibility(View.GONE);
        progressSection.setVisibility(View.GONE);
        resultSection.setVisibility(View.GONE);
        btnImport.setEnabled(true);
        btnImport.setBackgroundColor(ContextCompat.getColor(context, R.color.accent));

        accountsList = manager.provideAccounts();
        List<String> accountNames = new ArrayList<>();
        for (AccountEntry acc : accountsList) {
            accountNames.add(acc.icon() + " " + acc.name());
        }
        importSpinnerAccount.setAdapter(spinnerAdapter(context, accountNames));

        String savedKey = ApiKeyManager.getApiKey(context);
        if (savedKey != null) {
            inputApiKey.setText(savedKey);
            checkboxSaveKey.setChecked(true);
        } else {
            inputApiKey.setText("");
            checkboxSaveKey.setChecked(false);
        }

        overlay.setVisibility(View.VISIBLE);
    }

    public void hide() {
        overlay.setVisibility(View.GONE);
        selectedFileBytes = null;
        selectedFileName = null;
        selectedFileMimeType = null;
    }

    public void onFileSelected(String fileName, byte[] fileBytes, String mimeType) {
        this.selectedFileName = fileName;
        this.selectedFileBytes = fileBytes;
        this.selectedFileMimeType = mimeType;
        textFilename.setText(fileName);
    }

    private void startImport() {
        if (selectedFileBytes == null) {
            showImportError("Bitte Datei auswaehlen");
            return;
        }

        String apiKey = inputApiKey.getText().toString().trim();
        if (apiKey.isEmpty()) {
            showImportError("Bitte API-Key eingeben");
            return;
        }

        if (!ApiKeyManager.isValidFormat(apiKey)) {
            showImportError("Ungueltiges API-Key Format (erwartet: sk-ant-...)");
            return;
        }

        if (accountsList.isEmpty()) {
            showImportError("Kein Konto verfuegbar");
            return;
        }

        int accountIndex = importSpinnerAccount.getSelectedItemPosition();
        if (accountIndex < 0 || accountIndex >= accountsList.size()) {
            showImportError("Bitte Konto auswaehlen");
            return;
        }

        Long accountId = accountsList.get(accountIndex).accountId();

        if (checkboxSaveKey.isChecked()) {
            ApiKeyManager.saveApiKey(context, apiKey);
        }

        importErrorText.setVisibility(View.GONE);
        resultSection.setVisibility(View.GONE);
        progressSection.setVisibility(View.VISIBLE);
        progressText.setText("Initialisiere...");
        btnImport.setEnabled(false);
        btnImport.setBackgroundColor(ContextCompat.getColor(context, R.color.button_inactive));

        ImportProcessor processor = new ImportProcessor(context);
        processor.processImportAsync(accountId, selectedFileName, selectedFileBytes,
            selectedFileMimeType, apiKey, new ImportProcessor.ImportCallback() {
                @Override
                public void onProgress(String message) {
                    progressText.setText(message);
                }

                @Override
                public void onSuccess(ImportProcessor.ImportResult result) {
                    progressSection.setVisibility(View.GONE);
                    resultSection.setVisibility(View.VISIBLE);
                    resultSection.setBackgroundColor(ContextCompat.getColor(context, R.color.budget_positive_bg));
                    resultTitle.setText("Import abgeschlossen");
                    resultTitle.setTextColor(ContextCompat.getColor(context, R.color.budget_positive));
                    resultDetails.setText(result.newTransactions() + " neue Transaktionen\n" +
                        result.duplicates() + " Duplikate uebersprungen");
                    btnImportCancel.setText("Schliessen");
                    btnImport.setVisibility(View.GONE);

                    if (result.recurringCandidates() != null && !result.recurringCandidates().isEmpty()) {
                        root.postDelayed(() -> {
                            hide();
                            if (importSuccessCallback != null) {
                                importSuccessCallback.onSuccess(accountId, result.recurringCandidates());
                            }
                        }, 1500);
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    progressSection.setVisibility(View.GONE);
                    showImportError(errorMessage);
                    btnImport.setEnabled(true);
                    btnImport.setBackgroundColor(ContextCompat.getColor(context, R.color.accent));
                }
            });
    }

    private void showImportError(String message) {
        importErrorText.setText(message);
        importErrorText.setVisibility(View.VISIBLE);
    }
}
