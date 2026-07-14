package com.autosecretary.features.task.application.assistant.internal;

import static com.autosecretary.features.task.application.assistant.internal.AssistantJson.asString;
import static com.autosecretary.features.task.application.assistant.internal.AssistantJson.optDate;
import static com.autosecretary.features.task.application.assistant.internal.AssistantJson.optString;
import static com.autosecretary.features.task.application.assistant.internal.AssistantJson.requireArray;
import static com.autosecretary.features.task.application.assistant.internal.AssistantJson.requireDate;

import com.autosecretary.features.task.application.assistant.AssistantConversation;
import com.autosecretary.features.task.application.assistant.AssistantProposals.PendingProposal;
import com.autosecretary.features.task.application.assistant.AssistantProposals.TransactionDraft;
import com.autosecretary.features.task.application.assistant.AssistantProposals.TransactionImportProposal;
import com.autosecretary.features.task.application.internal.budget.AssistantBudgetGateway;
import com.autosecretary.features.task.application.internal.budget.AssistantTransactionImportExecutor;
import com.autosecretary.shared.ClaudeApiException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The assistant's budget tools: read accounts/categories and transactions, and propose importing the
 * transactions parsed from the last attached PDF statement. Budget access goes through
 * {@link AssistantBudgetGateway} and {@link AssistantTransactionImportExecutor} (the foreign
 * budget-domain seam); the current statement is supplied by the conversation.
 */
public final class BudgetTools {

    private static final String NO_STATEMENT =
            "Kein Kontoauszug angehängt. Hänge zuerst eine PDF-Datei an.";

    private static final String GET_BUDGET_CONTEXT_DESCRIPTION =
            "Liefert die Budget-Konten (id, name, balanceCents) und Kategorien (id, name, direction "
            + "INCOME/EXPENSE). Nutze es, um accountId/categoryId für andere Tools aufzulösen.";
    private static final String GET_BUDGET_CONTEXT_SCHEMA = "{\"type\":\"object\",\"properties\":{}}";

    private static final String GET_TRANSACTIONS_DESCRIPTION =
            "Liefert Transaktionen im Datumsbereich [from, to] (ISO-Datum, inklusiv), optional auf ein "
            + "Konto beschränkt. amountCents ist der Absolutbetrag, direction (INCOME/EXPENSE) trägt das "
            + "Vorzeichen; categoryName ist eingesetzt.";
    private static final String GET_TRANSACTIONS_SCHEMA =
            "{\"type\":\"object\",\"properties\":{"
            + "\"from\":{\"type\":\"string\",\"description\":\"Startdatum YYYY-MM-DD\"},"
            + "\"to\":{\"type\":\"string\",\"description\":\"Enddatum YYYY-MM-DD\"},"
            + "\"accountId\":{\"type\":\"string\",\"description\":\"Optionale Konto-id\"}},"
            + "\"required\":[\"from\",\"to\"]}";

    private static final String PROPOSE_TRANSACTION_IMPORT_DESCRIPTION =
            "Schlägt den Import der Transaktionen aus dem zuletzt angehängten Kontoauszug vor (erst nach "
            + "Bestätigung importiert; Duplikate werden übersprungen). Benötigt einen zuvor angehängten "
            + "PDF-Kontoauszug. amountCents ist SIGNIERT (negativ = Ausgabe, positiv = Einnahme).";
    private static final String PROPOSE_TRANSACTION_IMPORT_SCHEMA =
            "{\"type\":\"object\",\"properties\":{"
            + "\"accountId\":{\"type\":\"string\",\"description\":\"Optional; sonst Standardkonto\"},"
            + "\"periodStart\":{\"type\":\"string\",\"description\":\"YYYY-MM-DD\"},"
            + "\"periodEnd\":{\"type\":\"string\",\"description\":\"YYYY-MM-DD\"},"
            + "\"transactions\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{"
            + "\"date\":{\"type\":\"string\",\"description\":\"YYYY-MM-DD\"},"
            + "\"amountCents\":{\"type\":\"integer\",\"description\":\"signiert\"},"
            + "\"payee\":{\"type\":\"string\"},\"description\":{\"type\":\"string\"},"
            + "\"categoryId\":{\"type\":\"string\"}},\"required\":[\"date\",\"amountCents\"]}}},"
            + "\"required\":[\"transactions\"]}";

    private final AssistantBudgetGateway gateway;
    private final AssistantTransactionImportExecutor importExecutor;
    private final Supplier<AssistantConversation.Statement> currentStatement;
    private final DbCalls db;

    public BudgetTools(AssistantBudgetGateway gateway, AssistantTransactionImportExecutor importExecutor,
                       Supplier<AssistantConversation.Statement> currentStatement, DbCalls db) {
        this.gateway = gateway;
        this.importExecutor = importExecutor;
        this.currentStatement = currentStatement;
        this.db = db;
    }

    public List<AssistantTool> tools() {
        return List.of(
                AssistantTool.read("get_budget_context", GET_BUDGET_CONTEXT_DESCRIPTION,
                        GET_BUDGET_CONTEXT_SCHEMA, "Prüfe Budget…", input -> getBudgetContext()),
                AssistantTool.read("get_transactions", GET_TRANSACTIONS_DESCRIPTION, GET_TRANSACTIONS_SCHEMA,
                        "Prüfe Transaktionen…", this::getTransactions),
                AssistantTool.proposal("propose_transaction_import", PROPOSE_TRANSACTION_IMPORT_DESCRIPTION,
                        PROPOSE_TRANSACTION_IMPORT_SCHEMA, AssistantTool.PROGRESS_PROPOSAL,
                        this::proposeTransactionImport));
    }

    // ---- read tools -----------------------------------------------------------

    private String getBudgetContext() {
        AssistantBudgetGateway.BudgetContext context = db.call(gateway::accountsContext);
        try {
            JSONArray accounts = new JSONArray();
            for (AssistantBudgetGateway.AccountInfo account : context.accounts()) {
                accounts.put(new JSONObject()
                        .put("id", account.id())
                        .put("name", account.name())
                        .put("balanceCents", account.balanceCents()));
            }
            JSONArray categories = new JSONArray();
            for (AssistantBudgetGateway.CategoryInfo category : context.categories()) {
                categories.put(new JSONObject()
                        .put("id", category.id())
                        .put("name", category.name())
                        .put("direction", category.direction()));
            }
            return new JSONObject().put("accounts", accounts).put("categories", categories).toString();
        } catch (JSONException e) {
            throw new ClaudeApiException("Budget-Kontext konnte nicht serialisiert werden: " + e.getMessage(), e);
        }
    }

    private String getTransactions(JSONObject input) {
        LocalDate from = requireDate(input, "from");
        LocalDate to = requireDate(input, "to");
        String accountId = optString(input, "accountId");
        List<AssistantBudgetGateway.TransactionInfo> transactions =
                db.call(() -> gateway.transactions(from, to, accountId));
        try {
            JSONArray array = new JSONArray();
            for (AssistantBudgetGateway.TransactionInfo tx : transactions) {
                array.put(new JSONObject()
                        .put("date", asString(tx.date()))
                        .put("amountCents", tx.amountCents())
                        .put("direction", tx.direction())
                        .put("payee", tx.payee())
                        .put("note", tx.note())
                        .put("categoryName", tx.categoryName()));
            }
            return new JSONObject().put("transactions", array).toString();
        } catch (JSONException e) {
            throw new ClaudeApiException("Transaktionen konnten nicht serialisiert werden: " + e.getMessage(), e);
        }
    }

    // ---- proposal tool --------------------------------------------------------

    private PendingProposal proposeTransactionImport(JSONObject input) {
        AssistantConversation.Statement statement = currentStatement.get();
        if (statement == null) {
            throw new IllegalArgumentException(NO_STATEMENT);
        }
        JSONArray transactionsJson = requireArray(input, "transactions");
        List<TransactionDraft> drafts = new ArrayList<>();
        for (int i = 0; i < transactionsJson.length(); i++) {
            JSONObject entry = transactionsJson.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            drafts.add(new TransactionDraft(
                    requireDate(entry, "date"),
                    entry.optLong("amountCents"),
                    optString(entry, "payee"),
                    optString(entry, "description"),
                    optString(entry, "categoryId")));
        }
        if (drafts.isEmpty()) {
            throw new IllegalArgumentException("Der Kontoauszug enthält keine Transaktionen.");
        }
        String accountId = optString(input, "accountId");
        LocalDate periodStart = optDate(input, "periodStart");
        LocalDate periodEnd = optDate(input, "periodEnd");
        int duplicates = db.call(() -> importExecutor.duplicatePreview(accountId, drafts));
        return new TransactionImportProposal(accountId, statement.fileName(), statement.fileHash(),
                periodStart, periodEnd, drafts, duplicates);
    }
}
