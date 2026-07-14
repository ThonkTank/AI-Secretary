# budget/data/api — External API Integration

## Purpose

This package integrates with external APIs to fetch and parse financial data. It's part of the **data layer** and handles:
- Secure credential storage for API keys
- HTTP communication with external services
- Response parsing and error handling

## What's Here

### ClaudeStatementApiClient
Sends PDF bank statements to Claude's Messages API for structured transaction extraction.

**Key responsibilities:**
- Build the system prompt + PDF `document` attachment and delegate the call to the shared `ClaudeChatTransport` (`shared/ClaudeMessagesClient`)
- Parse JSON responses into domain objects (`ParsedStatement`)
- Strip markdown formatting if Claude wraps JSON in code fences

**Model + endpoint:** user-selectable — the model comes from `ClaudeModelStore` and the base URL from `ClaudeEndpointStore` (both in `shared/`), the same configuration the assistant chat uses. There is no hardcoded model. Default model: `claude-sonnet-5`.

HTTP communication, timeouts (30 s connect / 120 s read) and error mapping live in the shared `ClaudeMessagesClient`, not here.

### ClaudeApiKeyStore
Securely stores and retrieves Claude API keys using Android Keystore.

**Security:**
- AES-256-GCM authenticated encryption
- Encryption key stored in Android Keystore (trusted execution environment)
- Plaintext key never touches SharedPreferences
- Distinguishes permanent failures (key lost, auto-wipe) vs. temporary failures (keystore unavailable, preserve key)

## How It Fits in the Architecture

```
Domain Layer (features/budget/domain/)
  └─ ParsedStatement, ParsedTransaction (immutable DTOs)

Data Layer (features/budget/data/)
  ├─ data/dao/ (DAO interfaces), data/entity/ (Room entities)
  ├─ data/api/ (this — HTTP integration)
  │  ├─ ClaudeStatementApiClient (HTTP → JSON)
  │  └─ ClaudeApiKeyStore (encryption/decryption)
  └─ data/repository (Room database)

Application Layer (features/budget/application/)
  └─ StatementFileParser (routes by file type: CSV vs. PDF)
```

**Flow:**
1. User imports a PDF statement
2. `StatementFileParser` routes to `ClaudeStatementApiClient`
3. Client retrieves API key from `ClaudeApiKeyStore`
4. Client sends PDF + system prompt to Claude API
5. Client parses JSON response into `ParsedStatement`
6. Application layer commits to database

## Setting Up for Development

### 1. Generate an API Key
- Create an account at https://console.anthropic.com
- Generate a Claude API key
- Copy the key (you'll only see it once)

### 2. Store Securely in the App
Users enter their API key in the app settings:
1. Open Budget feature
2. Settings (gear icon)
3. Enter Claude API Key
4. `ClaudeApiKeyStore` encrypts it via Android Keystore

**Important:** The key is only used during PDF import; users can revoke it anytime in settings.

### 3. Public Resources
- **Anthropic Messages API:** https://docs.anthropic.com/en/api/messages
- **Android Keystore:** https://developer.android.com/training/articles/keystore
- **HTTP in Android:** https://developer.android.com/reference/java/net/HttpURLConnection

## Common Issues & Troubleshooting

| Issue | Likely Cause | Fix |
|-------|--------------|-----|
| "Ungültiger API-Key (401)" | API key is invalid or revoked | Regenerate key at console.anthropic.com; re-enter in settings |
| "API-Limit erreicht (429)" | Hit rate limit on Claude API | Wait a few minutes; consider PDF size (large PDFs = longer processing) |
| "Claude-Server nicht erreichbar (500, 502, 503)" | Anthropic API is down | Check https://status.anthropic.com; retry later |
| "Netzwerkfehler" | Network connection issue | Check internet connectivity; verify proxy settings if on corporate network |
| "Claude-Antwort ungültig" | Claude response was malformed | Rare; contact support or try a different statement |
| Read timeout (120s) after 2 minutes | PDF is too large or malformed | Try a smaller PDF; check statement format |

## Performance Notes

- **PDF parsing cost:** Each PDF costs a Claude API call (token-based pricing)
- **Parsing time:** Typically 10-30 seconds depending on PDF size and Anthropic API load
- **Network:** Requires internet connection; works on cellular but slower
- **Encryption overhead:** Minimal; Android Keystore operations are ~<1ms

## Key Design Decisions

**Why AES-256-GCM?**
- GCM provides authenticated encryption (detects tampering)
- Android Keystore handles key generation and storage securely
- Industry standard for protecting sensitive credentials on mobile

**Why Claude for PDFs, not local parsing?**
- PDFs have inconsistent formats; Claude handles variations well
- No need to bundle PDF parsing libraries (smaller APK)
- Can handle scanned/image-based statements
- Tradeoff: requires network access and API calls

**Why a system prompt?**
- Instructs Claude on exact JSON schema expected
- Constrains Claude's output to only valid categories
- Ensures repeatable, structured parsing

**Why strip markdown fences?**
- Claude sometimes wraps code examples in triple backticks (best practice for code)
- We want raw JSON, not code formatting
- Defensive: handles both wrapped and unwrapped responses
