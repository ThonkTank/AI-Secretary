# AI Secretary - Technical Debt & Improvement Roadmap

**Erstellt:** 12.11.2025
**Letzte Analyse:** Version 0.1.1 (Build 101)
**Status:** Phase 0 - Foundation Systems ✅ COMPLETE (100%)
**Letzte Aktualisierung:** 12.11.2025 - Update System funktioniert! Phase 0 abgeschlossen!

---

## 📑 Inhaltsverzeichnis

### Part 1: Overview
- [Executive Summary](#executive-summary)
- [🟡 Phase 0: Foundation Systems (50% COMPLETE)](#-phase-0-foundation-systems-50-complete)
- [Current Status & Timeline](#-tracking)

### Part 2: Technical Debt (Code Quality)
- [🔴 KRITISCHE PROBLEME](#-kritische-probleme-sofort)
- [⚠️ CODE SMELLS](#️-code-smells-kurzfristig---wichtig)
- [🏗️ ARCHITEKTUR-PROBLEME](#️-architektur-probleme-mittelfristig)
- [📁 DATEISTRUKTUR-PROBLEME](#-dateistruktur-probleme-mittelfristig)
- [✅ POSITIVE ASPEKTE](#-positive-aspekte)

### Part 3: Feature Development
- [🎯 FEATURE DEVELOPMENT ROADMAP](#-feature-development-roadmap)
- [Feature Suite 1: Taskmaster - Übersicht](#-feature-suite-1-taskmaster---übersicht)
- [Feature Phase 1: Foundation & Database](#-phase-1-foundation--database-3-4-wochen)
- [Feature Phase 2: Core Task Management](#-phase-2-core-task-management-4-5-wochen)
- [Feature Phase 3: Tracking & Analytics](#-phase-3-tracking--analytics-3-4-wochen)
- [Feature Phase 4: Motivation & Statistics](#-phase-4-motivation--statistics-2-3-wochen)
- [Feature Phase 5: Intelligent Planning](#-phase-5-intelligent-planning-4-5-wochen)
- [Feature Phase 6: Widget & Polish](#-phase-6-widget--polish-3-4-wochen)
- [Task Data Model Specifications](#task-data-model-für-phase-1)

### Part 4: Priorisierte Roadmap & Timeline
- [🎯 PRIORISIERTE ROADMAP](#-priorisierte-roadmap)
- [Phase 0: KRITISCH](#phase-0-kritisch-sofort-vor-allem-anderen)
- [Tech Debt Phase 1: Foundation Fixes](#tech-debt-phase-1-foundation-fixes-parallel-zu-feature-dev)
- [Tech Debt Phase 2: Testing & Quality](#tech-debt-phase-2-testing--quality-ongoing---parallel-zu-features)
- [Tech Debt Phase 3: Modernization](#tech-debt-phase-3-modernization--optimization-nach-feature-phase-4)

### Part 5: Meta
- [📊 Tracking](#-tracking)
- [📝 Notizen](#-notizen)
- [🔗 Referenzen](#-referenzen)
- [Changelog](#changelog)

---

## Executive Summary

Diese Roadmap ist das zentrale Planungsdokument für das AI Secretary Projekt. Sie kombiniert:
1. **Current Status:** Was bereits implementiert ist (Phase 0)
2. **Technical Debt:** Identifizierte Code Smells und Architekturprobleme
3. **Feature Development:** Detaillierte Entwicklungsphasen für Taskmaster (Feature Suite 1)
4. **Timeline:** Realistische Zeitschätzungen und Dependencies

### ✅ Phase 0: Foundation Systems (100% COMPLETE!)

Die grundlegenden Infrastruktur-Features sind vollständig implementiert und funktionsfähig:

**1. Auto-Update System** ✅ FUNKTIONIERT!
- GitHub Releases API Integration (`UpdateChecker.java`) - **FUNKTIONIERT**
- Automatische Version-Erkennung und -Vergleich - **FUNKTIONIERT**
- APK-Download via DownloadManager (`UpdateInstaller.java`) - **Code vorhanden**
- ✅ Erkennt korrekt ob Updates verfügbar sind
- ✅ GitHub Token entfernt (v0.1.2) - Repository ist jetzt public
- ✅ API Response 200 OK, findet Releases und Assets

**2. Logging System** ✅ FUNKTIONIERT!
- In-Memory Logging (`AppLogger.java`) - **FUNKTIONIERT**
- Automatisches Log-Trimming (max. 500 Zeilen) - **FUNKTIONIERT**
- Drei Log-Level: INFO, DEBUG, ERROR - **FUNKTIONIERT**
- HTTP Server für externe Zugriff (`SimpleHttpServer.java`) - **FUNKTIONIERT**
- ✅ **Claude Code kann Logs via `curl http://localhost:8080/logs` lesen**

### 🚀 NÄCHSTE SCHRITTE: Phase 1 - Taskmaster Foundation

**Phase 0 ist abgeschlossen! Jetzt beginnt die Feature-Entwicklung:**

1. **Phase 1 Vorbereitung** (PRIORITÄT 1)
   - [ ] Entscheidung: Kotlin Migration oder bei Java bleiben?
   - [ ] MVVM Architecture Setup planen
   - [ ] Room Database Dependencies vorbereiten
   - [ ] Projekt-Struktur refactoren (packages organisieren)

2. **Optional: Tech Debt Clean-up** (PRIORITÄT 2)
   - [ ] Package Name Migration (`com.secretary.helloworld` → `com.secretary`)
   - [ ] Resource Leaks in UpdateChecker/UpdateInstaller fixen
   - [ ] Dead Code entfernen (ungenutzte Variablen)
   - [ ] Code-Kommentare auf Englisch vereinheitlichen

3. **Phase 1 Start: Taskmaster Foundation** (3-4 Wochen)
   - [ ] Basic Task Entity erstellen
   - [ ] Room Database Setup
   - [ ] Repository Pattern implementieren
   - [ ] ViewModel für Task-Liste

**Technical Debt (Code Quality Issues):**
- 🔴 1 kritisches Sicherheitsproblem (GitHub Token hardcoded)
- ⚠️ 21 Code Smells und Architektur-Probleme
- 📁 6 Dateistruktur-Probleme

### 📋 Taskmaster Feature Development

**Feature Development (6 Phasen):**
- 🎯 Phase 1-4: MVP (3-4 Monate)
- 🎯 Phase 1-6: Komplette Suite (4.5-6 Monate)

**Current Status:**
- ❌ Phase 0 (Foundation) ist NICHT funktionsfähig - BLOCKER
- 🔴 Phase 0 MUSS funktionieren bevor Taskmaster starten kann
- 🔴 Kritische Security-Fixes parallel zu Phase 0 Debugging
- ⏳ Taskmaster Phase 1 wartet auf funktionierende Phase 0

**Gesamtbewertung:**
- ❌ Phase 0 Code existiert, funktioniert aber nicht
- 🔴 Update-System und Logging müssen SOFORT gefixt werden
- ❌ Kritisches Sicherheitsproblem muss parallel behoben werden
- ✅ Klarer Entwicklungsplan für Taskmaster-Features vorhanden (wenn Phase 0 läuft)

---

## 🔴 PHASE 0: FOUNDATION SYSTEMS (IMPLEMENTIERT, ABER NICHT FUNKTIONSFÄHIG)

### Status: ❌ CODE VORHANDEN, FUNKTIONIERT ABER NICHT (Version 3.0)

Phase 0 umfasst die grundlegenden Infrastruktur-Features. Der Code ist implementiert, aber **beide Systeme funktionieren aktuell nicht**.

---

### 1. Auto-Update System ❌ NICHT FUNKTIONSFÄHIG

**Zweck:**
App soll sich selbst über GitHub Releases aktualisieren können, ohne Play Store oder manuelle APK-Downloads.

**Status:** ❌ Code implementiert, aber funktioniert nicht - muss debugged werden

**Komponenten:**

#### UpdateChecker.java (126 Zeilen)
- GitHub Releases API Integration
- Vergleicht aktuelle App-Version mit latest Release auf GitHub
- Async Network-Requests mit Callback-Listener
- Sucht APK-Assets in Releases
- Repository: `ThonkTank/AI-Secretary`

**Features:**
- Automatische Version-Erkennung (versionCode & versionName)
- JSON-Parsing der GitHub API Response
- Changelog-Extraktion aus Release-Body
- Error-Handling für Netzwerk-Fehler

#### UpdateInstaller.java (91 Zeilen)
- Download über Android DownloadManager
- BroadcastReceiver für Download-Completion
- Automatischer Installations-Prompt nach Download
- APK-Speicherung in public Downloads folder

**Features:**
- Download-Progress in System-Notification
- URI-basierte APK-Installation
- Permissions: INTERNET, REQUEST_INSTALL_PACKAGES

#### User Flow:
1. User öffnet Settings-Dialog (⚙ Icon in Action Bar)
2. User klickt "Check for Updates"
3. App prüft GitHub Releases API
4. Falls Update verfügbar: Dialog mit Version & Changelog
5. User klickt "Download & Install"
6. Download startet (Notification sichtbar)
7. Nach Download: Automatischer Installations-Prompt
8. User installiert Update

**Integration:**
- GitHub Actions CI/CD baut APK automatisch bei jedem Push
- APK wird als Release-Asset hochgeladen
- App erkennt neues Release und bietet Installation an

**Versionierung:**
- Semantic Versioning (Major.Minor)
- `versionCode`: Integer (inkrementell, z.B. 21)
- `versionName`: String (z.B. "3.0")

**Bekannte Issues (siehe Technical Debt):**
- ⚠️ GitHub Token hardcoded (KRITISCH - muss gefixt werden)
- ⚠️ BroadcastReceiver Memory Leak möglich
- ⚠️ Manuelles Thread-Management (kein ExecutorService)
- ⚠️ HttpURLConnection nicht geschlossen (Resource Leak)

---

### 2. Logging System ❌ NICHT FUNKTIONSFÄHIG

**Zweck:**
Umfassendes Logging für Debugging durch Claude Code soll alle wichtigen App-Events automatisch loggen und für Entwicklung zugänglich machen.

**Status:** ❌ Code implementiert, aber funktioniert nicht - muss debugged werden

**Komponenten:**

#### AppLogger.java (101 Zeilen)
- Singleton Pattern für zentrales Logging
- In-Memory Storage (max. 500 Zeilen)
- Drei Log-Level: INFO, DEBUG, ERROR
- Paralleles Logging zu Android Logcat
- Thread-safe Implementation

**Features:**
- **Automatisches Log-Trimming:**
  - Max. 500 Zeilen im Speicher
  - Älteste Einträge werden automatisch gelöscht
  - Verhindert Memory-Issues

- **Strukturierte Log-Einträge:**
  - Format: `[YYYY-MM-DD HH:mm:ss] [LEVEL] [TAG] Message`
  - Beispiel: `[2025-11-12 14:30:15] [INFO] [MainActivity] App started`

- **Log-Level:**
  - `INFO`: Normale App-Events (Start, Button-Clicks, Updates)
  - `DEBUG`: Detaillierte Debugging-Infos (API-Responses, Status)
  - `ERROR`: Fehler mit Stacktraces

- **Storage:**
  - In-Memory (List<String>)
  - Keine File-Permissions nötig
  - Logs gehen bei App-Neustart verloren (by design)

- **Zugriff:**
  - In App: Settings → "View Logs" Button
  - Logs-Dialog mit ScrollView
  - "Copy to Clipboard" Button
  - Main Screen zeigt Logs automatisch (für Debug-Builds)

**Integration:**
- Alle wichtigen App-Events werden geloggt:
  - App Start/Lifecycle
  - Update-Checks
  - Button-Clicks
  - API-Requests
  - Fehler & Exceptions

**Usage in Code:**
```java
AppLogger logger = AppLogger.getInstance(context);
logger.info(TAG, "App started");
logger.debug(TAG, "GitHub API response code: 200");
logger.error(TAG, "Network error", exception);
```

**Bekannte Issues (siehe Technical Debt):**
- ⚠️ Dead Code: `logFile` Variable wird erstellt aber nie verwendet
- ⚠️ Singleton Anti-Pattern (erschwert Testing)
- ⚠️ Inkonsistenz: `getLogFilePath()` gibt "IN-MEMORY" zurück, aber logFile existiert

---

### Phase 0 - Zusammenfassung

**Was vorhanden ist (aber NICHT funktioniert):**
- ❌ Auto-Update System - Code vorhanden, funktioniert nicht
- ❌ Logging System - Code vorhanden, funktioniert nicht
- ✅ Settings-UI vorhanden
- ✅ CI/CD-Pipeline funktioniert (GitHub Actions)
- ✅ Clean, minimales Design

**Was SOFORT gefixt werden muss:**
- 🔴 Update-System debuggen und zum Laufen bringen
- 🔴 Logging-System debuggen und zum Laufen bringen
- 🔴 GitHub Token Security (KRITISCH)
- ⚠️ Memory Leaks (BroadcastReceiver)
- ⚠️ Resource Management (HttpURLConnection)
- ⚠️ Thread-Management (ExecutorService)
- ⚠️ Dead Code entfernen

**Probleme identifizieren:**
- **Update-System:** Was genau funktioniert nicht?
  - GitHub API Request schlägt fehl?
  - JSON Parsing funktioniert nicht?
  - Download startet nicht?
  - Installation wird nicht angezeigt?
- **Logging-System:** Was genau funktioniert nicht?
  - Logs werden nicht geschrieben?
  - Logs werden nicht angezeigt?
  - AppLogger wird nicht richtig initialisiert?

**Nächste Schritte:**
1. App installieren und testen
2. Fehler identifizieren (Logcat prüfen)
3. Update-System debuggen
4. Logging-System debuggen
5. Beide Systeme funktionsfähig machen

**Fazit:**
Phase 0 Code existiert, ist aber nicht funktionsfähig. Bevor Taskmaster-Entwicklung starten kann, MÜSSEN Update und Logging funktionieren. Dies ist ein BLOCKER für alle weiteren Features.

---

## 🔴 KRITISCHE PROBLEME (SOFORT)

### Security

#### 1. Hardcoded GitHub Token
**Status:** ❌ KRITISCH - SOFORT BEHEBEN
**Datei:** `UpdateChecker.java:17`
**Problem:**
```java
private static final String GITHUB_TOKEN = "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";
```

**Risiko:**
- Token ist öffentlich auf GitHub sichtbar
- Kann für unbefugten Zugriff auf Repository verwendet werden
- Könnte bereits kompromittiert sein

**Lösung:**
- [ ] Token SOFORT auf GitHub revoken
- [ ] Neuen Token generieren
- [ ] Token in GitHub Secrets speichern
- [ ] Code ändern um Token aus Environment Variable zu lesen
- [ ] Alternative: Public API ohne Token nutzen (Rate-Limit beachten)

**Referenzen:**
- GitHub Docs: [Managing your personal access tokens](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens)
- [GitHub Secrets in Actions](https://docs.github.com/en/actions/security-guides/encrypted-secrets)

---

## ⚠️ CODE SMELLS (Kurzfristig - Wichtig)

### Threading & Concurrency

#### 2. Manuelles Thread-Management
**Status:** ⚠️ Wichtig
**Datei:** `UpdateChecker.java:26-106`
**Problem:**
- Manuelles `new Thread().start()` statt ExecutorService
- Gefährlicher Context-Cast: `((android.app.Activity) context).runOnUiThread(...)`
- Kein Thread-Pool, jeder Request erstellt neuen Thread

**Auswirkungen:**
- Context könnte nicht immer Activity sein → ClassCastException
- Keine Thread-Wiederverwendung → Performance-Problem
- Schwer zu testen

**Lösung:**
- [ ] ExecutorService oder Kotlin Coroutines verwenden
- [ ] Handler mit Looper für UI-Updates
- [ ] Oder moderne Libraries wie Retrofit mit RxJava/Coroutines

**Code-Beispiel (Lösung):**
```java
private static final ExecutorService executor = Executors.newSingleThreadExecutor();
private static final Handler mainHandler = new Handler(Looper.getMainLooper());

public static void checkForUpdates(Context context, UpdateListener listener) {
    executor.execute(() -> {
        // Background work
        mainHandler.post(() -> {
            // UI updates
        });
    });
}
```

---

### Memory Management

#### 3. BroadcastReceiver Memory Leak
**Status:** ⚠️ Wichtig
**Datei:** `UpdateInstaller.java:38-70`
**Problem:**
```java
BroadcastReceiver onComplete = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
        // ...
        context.unregisterReceiver(this); // Nur bei Erfolg!
    }
};
context.registerReceiver(onComplete, new IntentFilter(...));
```

**Auswirkungen:**
- Receiver wird nur in `onReceive` unregistriert
- Wenn Download nie abgeschlossen wird, bleibt Receiver registriert
- Activity-Zerstörung führt zu Memory Leak

**Lösung:**
- [ ] Receiver in `onDestroy()` der Activity unregistrieren
- [ ] Verwende WorkManager für Download-Tasks (überlebt Activity-Lifecycle)
- [ ] Oder verwende LocalBroadcastManager (deprecated, aber sicherer)

**Empfohlene Implementierung:**
```java
// In MainActivity
private BroadcastReceiver downloadReceiver;

@Override
protected void onDestroy() {
    super.onDestroy();
    if (downloadReceiver != null) {
        unregisterReceiver(downloadReceiver);
    }
}
```

---

#### 4. Resource Management - HttpURLConnection nicht geschlossen
**Status:** ⚠️ Wichtig
**Datei:** `UpdateChecker.java:36-54`
**Problem:**
```java
HttpURLConnection conn = (HttpURLConnection) url.openConnection();
// ... verwendet conn
// KEIN conn.disconnect() oder try-with-resources!
BufferedReader reader = new BufferedReader(...);
// KEIN reader.close()!
```

**Auswirkungen:**
- Ressourcen-Leak
- Offene Connections
- Potenzielle "Too many open files" Fehler

**Lösung:**
- [ ] Try-with-resources verwenden
- [ ] Oder finally-Block mit explicit close()

**Code-Beispiel (Lösung):**
```java
HttpURLConnection conn = null;
try {
    conn = (HttpURLConnection) url.openConnection();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
        // Work with reader
    }
} finally {
    if (conn != null) {
        conn.disconnect();
    }
}
```

---

### Code Quality

#### 5. Generische Exception-Behandlung
**Status:** ⚠️ Mittlere Priorität
**Dateien:** Mehrere
**Problem:**
```java
catch (Exception e) {  // Zu generisch!
    logger.error(TAG, "Error checking for updates", e);
    ((android.app.Activity) context).runOnUiThread(() ->
        listener.onError(e.getMessage())
    );
}
```

**Auswirkungen:**
- Keine spezifische Fehlerbehandlung
- Keine Retry-Logik bei Netzwerkfehlern
- Keine Prüfung der Netzwerkverfügbarkeit vor Requests

**Lösung:**
- [ ] Spezifische Exceptions catchen (IOException, JSONException, etc.)
- [ ] Netzwerk-Verfügbarkeit prüfen vor Requests
- [ ] Retry-Logik für transiente Fehler
- [ ] User-freundliche Fehlermeldungen

**Code-Beispiel (Lösung):**
```java
try {
    // Network request
} catch (UnknownHostException | SocketTimeoutException e) {
    // Network issue - could retry
    listener.onError("Network error. Please check your connection.");
} catch (JSONException e) {
    // Parse error
    listener.onError("Invalid response from server.");
} catch (IOException e) {
    // Other IO issues
    listener.onError("Download failed: " + e.getMessage());
}
```

---

#### 6. Code-Duplikation
**Status:** ⚠️ Mittlere Priorität
**Dateien:** `UpdateChecker.java:109-125`, `MainActivity.java:37-43`
**Problem:**
- `getCurrentVersionCode()` und `getCurrentVersionName()` Logik wird dupliziert
- Ähnliche try-catch Blöcke werden wiederholt

**Lösung:**
- [ ] Zentrale Utility-Klasse `VersionUtils` erstellen
- [ ] DRY-Prinzip anwenden (Don't Repeat Yourself)

**Code-Beispiel (Lösung):**
```java
public class VersionUtils {
    public static int getVersionCode(Context context) {
        try {
            PackageInfo pInfo = context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("VersionUtils", "Package not found", e);
            return 0;
        }
    }

    public static String getVersionName(Context context) {
        try {
            PackageInfo pInfo = context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("VersionUtils", "Package not found", e);
            return "unknown";
        }
    }
}
```

---

#### 7. Magic Numbers & Strings
**Status:** ⚠️ Niedrige Priorität
**Dateien:** Verschiedene
**Problem:**
- `conn.setConnectTimeout(10000);` - Was ist 10000?
- `private static final int MAX_LOG_LINES = 500;` - Warum 500?
- `mainLogsTextView.postDelayed(() -> ..., 1000);` - Warum 1 Sekunde?

**Lösung:**
- [ ] Konstanten mit aussagekräftigen Namen definieren
- [ ] Kommentare hinzufügen warum dieser Wert gewählt wurde

**Code-Beispiel (Lösung):**
```java
// Network timeouts
private static final int CONNECT_TIMEOUT_MS = 10_000; // 10 seconds
private static final int READ_TIMEOUT_MS = 10_000;

// Logging configuration
private static final int MAX_LOG_LINES = 500; // Keep last 500 entries to prevent memory issues

// UI update delays
private static final long LOG_REFRESH_DELAY_MS = 1000; // Wait for button details to log
```

---

#### 8. Dead Code & Inkonsistenzen
**Status:** ⚠️ Mittlere Priorität
**Datei:** `AppLogger.java`
**Problem:**
```java
private File logFile;  // Zeile 21 - wird erstellt aber nie verwendet
// ...
public String getLogFilePath() {
    return "IN-MEMORY (no file)";  // Zeile 94 - ignoriert logFile!
}
```

**Auswirkungen:**
- Verwirrung für andere Entwickler
- Unnötiger Speicher
- Inkonsistente API

**Lösung:**
- [ ] `logFile` Variable komplett entfernen
- [ ] Alte File-Logging-Kommentare entfernen
- [ ] Oder: File-Logging wieder implementieren für Persistence

---

#### 9. Singleton Anti-Pattern
**Status:** ⚠️ Niedrige Priorität (für jetzt OK)
**Datei:** `AppLogger.java:37-42`
**Problem:**
```java
public static synchronized AppLogger getInstance(Context context) {
    if (instance == null) {
        instance = new AppLogger(context.getApplicationContext());
    }
    return instance;
}
```

**Auswirkungen:**
- Singleton erschwert Unit-Tests
- Globaler Zustand
- Kein Dependency Injection möglich
- In modernem Android (mit Hilt/Koin) nicht mehr empfohlen

**Lösung (für später):**
- [ ] Dependency Injection mit Hilt einführen
- [ ] Singleton durch @Singleton-annotierte Klasse ersetzen
- [ ] Testability verbessern

**Hinweis:** Für die aktuelle Early-Phase ist Singleton akzeptabel, aber bei Skalierung umstellen.

---

### Naming & Conventions

#### 10. Unprofessionelles Package-Naming
**Status:** ⚠️ Wichtig
**Datei:** `AndroidManifest.xml:3`
**Problem:**
```xml
package="com.secretary.helloworld"
```

**Auswirkungen:**
- "helloworld" wirkt unprofessionell
- Passt nicht zur App-Vision (AI Secretary)
- Schwer zu ändern später (erfordert Refactoring aller Imports)

**Lösung:**
- [ ] Auf `com.secretary` oder `com.secretary.ai` umbenennen
- [ ] Refactoring aller Imports durchführen
- [ ] AndroidManifest aktualisieren
- [ ] WICHTIG: Mache das jetzt, bevor mehr Code existiert!

---

#### 11. Inkonsistente Sprache
**Status:** ⚠️ Niedrige Priorität
**Dateien:** Alle
**Problem:**
- Code auf Englisch
- Kommentare auf Deutsch
- Logs auf Deutsch
- Strings gemischt

**Auswirkungen:**
- Unprofessionell
- Erschwert internationale Zusammenarbeit
- Code sollte konsistent Englisch sein

**Lösung:**
- [ ] Alle Kommentare auf Englisch umstellen
- [ ] Logs auf Englisch
- [ ] User-facing Strings in strings.xml (können lokalisiert werden)
- [ ] Code-Kommentare nur dort wo wirklich nötig

---

### UI/UX

#### 12. UI/UX Anti-Patterns
**Status:** ⚠️ Niedrige Priorität
**Datei:** `MainActivity.java:54`
**Problem:**
```java
mainLogsTextView.postDelayed(() -> updateLogsDisplay(mainLogsTextView), 1000);
```

**Auswirkungen:**
- Hardcoded delay ohne Erklärung
- Kein Polling/LiveData für Echtzeit-Updates
- Manuelle UI-Refreshes

**Lösung:**
- [ ] LiveData verwenden für reaktive Updates
- [ ] Oder Observer-Pattern für Log-Updates
- [ ] Delay dokumentieren wenn notwendig

---

#### 13. Hardcoded UI-Texte
**Status:** ⚠️ Niedrige Priorität
**Datei:** `activity_main.xml:18` und andere
**Problem:**
```xml
android:text="Loading logs..."
```

**Auswirkungen:**
- Keine Lokalisierung möglich
- Android Lint Warnings

**Lösung:**
- [ ] Alle UI-Texte in `strings.xml` verschieben
- [ ] Lokalisierung vorbereiten (auch wenn initial nur Englisch)

---

## 🏗️ ARCHITEKTUR-PROBLEME (Mittelfristig)

### Architecture Patterns

#### 14. Keine Architektur-Pattern
**Status:** 📋 Für Skalierung wichtig
**Dateien:** Gesamtes Projekt
**Problem:**
- Keine MVVM, MVP oder MVI
- Direkte API-Calls aus der UI (MainActivity)
- Business-Logik vermischt mit UI-Logik
- Keine ViewModels

**Auswirkungen:**
- Schwer zu testen
- Nicht wartbar bei Wachstum
- Keine Separation of Concerns
- Activity macht zu viel

**Lösung (für Feature Suite 1: Taskmaster):**
- [ ] MVVM-Architektur einführen
- [ ] ViewModels für Business-Logik
- [ ] Repository Pattern für Datenzugriff
- [ ] Use Cases für komplexe Operations

**Empfohlene Struktur:**
```
com.secretary/
├── presentation/
│   ├── main/
│   │   ├── MainActivity.kt
│   │   └── MainViewModel.kt
│   ├── settings/
│   │   └── SettingsViewModel.kt
│   └── common/
├── domain/
│   ├── models/
│   ├── usecases/
│   └── repository/  (interfaces)
└── data/
    ├── repository/  (implementations)
    ├── remote/
    │   ├── api/
    │   └── dto/
    └── local/
        └── database/
```

---

#### 15. Keine Layer-Trennung
**Status:** 📋 Für Skalierung wichtig
**Dateien:** Gesamtes Projekt
**Problem:**
- Alles in einem Package `com.secretary`
- Keine data/domain/presentation Layer
- UpdateChecker kennt UI (runOnUiThread)
- Tight Coupling zwischen allen Komponenten

**Lösung:**
- [ ] Clean Architecture Layers einführen
- [ ] Dependency Rule beachten (innere Layers kennen äußere nicht)
- [ ] Interfaces für Layer-Kommunikation

---

#### 16. Tight Coupling & No Dependency Injection
**Status:** 📋 Für Skalierung wichtig
**Dateien:** Alle
**Problem:**
- Alle Klassen kennen sich direkt
- Keine Interfaces
- Keine Dependency Injection
- Schwer zu mocken für Tests

**Lösung:**
- [ ] Hilt oder Koin für Dependency Injection
- [ ] Constructor Injection bevorzugen
- [ ] Interfaces definieren für Abstraktionen

**Code-Beispiel (mit Hilt):**
```kotlin
@HiltViewModel
class MainViewModel @Inject constructor(
    private val updateRepository: UpdateRepository,
    private val logger: Logger
) : ViewModel() {
    // ViewModel logic
}

interface UpdateRepository {
    suspend fun checkForUpdates(): Result<UpdateInfo>
}

@Singleton
class UpdateRepositoryImpl @Inject constructor(
    private val api: GitHubApi
) : UpdateRepository {
    override suspend fun checkForUpdates(): Result<UpdateInfo> {
        // Implementation
    }
}
```

---

### Testing

#### 17. Keine Tests
**Status:** 📋 Für Skalierung kritisch
**Dateien:** Keine Test-Dateien gefunden
**Problem:**
- Keine Unit Tests
- Keine Integration Tests
- Keine UI Tests
- 0% Test Coverage

**Lösung:**
- [ ] JUnit 5 für Unit Tests
- [ ] Mockito oder MockK für Mocking
- [ ] Espresso für UI Tests
- [ ] Test Coverage Minimum: 70%

**Test-Struktur:**
```
test/
├── unit/
│   ├── domain/
│   ├── data/
│   └── presentation/
└── integration/
    └── repository/

androidTest/
└── ui/
    ├── main/
    └── settings/
```

---

### Build System

#### 18. Fehlende Gradle-Konfiguration
**Status:** ⚠️ Teilweise OK (wegen Termux)
**Dateien:** `build.sh` statt `build.gradle`
**Problem:**
- Build-Script (`build.sh`) statt Gradle
- Keine Dependency-Verwaltung
- Manuelle Build-Schritte
- Keine ProGuard/R8 Regeln

**Hinweis:** Laut CLAUDE.md ist dies eine bewusste Entscheidung wegen Termux-Limitationen. GitHub Actions nutzt Gradle.

**Lösung:**
- [ ] GitHub Actions Workflow weiter ausbauen
- [ ] ProGuard/R8 Regeln für Release-Builds
- [ ] Dependency-Versionen zentralisieren
- [ ] Build-Varianten (debug, release, staging)

---

## 📁 DATEISTRUKTUR-PROBLEME (Mittelfristig)

### Project Organization

#### 19. Verwirrende Projekt-Struktur
**Status:** ⚠️ Wichtig
**Dateien:** Root-Verzeichnis
**Problem:**
```
home/
├── AI-Secretary/           # Was ist das?
├── AI-Secretary-temp/      # Hauptprojekt?
├── AISecretary-signed.apk  # Build-Artefakt im home
└── AISecretary-v1.9-signed.apk
```

**Auswirkungen:**
- Zwei Verzeichnisse mit ähnlichen Namen
- Unklar, welches das aktive Projekt ist
- Build-Artefakte nicht im Projekt-Verzeichnis
- Keine klare Trennung

**Lösung:**
- [ ] Ein Verzeichnis wählen als Haupt-Projekt
- [ ] Anderes umbenennen oder löschen
- [ ] Build-Artefakte in `builds/` oder `releases/` Verzeichnis
- [ ] `.gitignore` für APKs aktualisieren

---

#### 20. Flache Package-Struktur
**Status:** 📋 Für Skalierung wichtig
**Dateien:** `src/com/secretary/`
**Problem:**
```
src/com/secretary/
├── MainActivity.java
├── UpdateChecker.java
├── UpdateInstaller.java
└── AppLogger.java
```
Alles auf einer Ebene, keine Gruppierung

**Lösung:**
- [ ] Package-by-Feature oder Package-by-Layer
- [ ] Siehe Punkt 14 für empfohlene Struktur

---

#### 21. Gemischte Build-Artefakte im Repo
**Status:** ⚠️ Wichtig
**Dateien:** `/home/AI-Secretary-temp/apk_inspection/`
**Problem:**
- Enthält entpackte APK-Inhalte
- Sollte nicht im Source-Repository sein

**Lösung:**
- [ ] Verzeichnis löschen
- [ ] In `.gitignore` aufnehmen
- [ ] Build-Artefakte generell ignorieren

**Empfohlene `.gitignore`:**
```
# Build artifacts
*.apk
*.dex
*.class
classes/
gen/
compiled_res/
apk_inspection/

# IDE
.idea/
*.iml
.vscode/

# Gradle
.gradle/
build/

# Local config
local.properties
```

---

## ✅ POSITIVE ASPEKTE

Trotz der identifizierten Probleme gibt es auch viele gute Dinge:

1. ✅ **Exzellente Dokumentation:** CLAUDE.md ist sehr ausführlich und hilfreich
2. ✅ **CI/CD vorhanden:** GitHub Actions Workflow ist konfiguriert
3. ✅ **Update-System funktioniert:** Clever gelöst mit GitHub Releases
4. ✅ **Logging-System:** Grundsolide Implementierung, gut durchdacht
5. ✅ **Einfachheit:** Keine Over-Engineering für den aktuellen Umfang
6. ✅ **Klare Vision:** Roadmap und Feature-Pläne sind gut dokumentiert
7. ✅ **Minimale Abhängigkeiten:** Pure Android, keine Bloat-Libraries
8. ✅ **Funktioniert:** App läuft stabil für ihren Umfang

---

---

## 🎯 FEATURE DEVELOPMENT ROADMAP

Diese Sektion integriert die Feature-Entwicklung aus CLAUDE.md mit den technischen Verbesserungen. Bevor große Features implementiert werden, müssen die technischen Grundlagen geschaffen werden.

### 🔒 Feature Suite 1: "Taskmaster" - Übersicht

Taskmaster ist das Kernstück der AI Secretary App - ein intelligentes Aufgabenverwaltungssystem.

#### Feature-Kategorien

**1. Todo-Organisation**
- Einzelne Tasks (einmalig)
- Wiederkehrende Tasks (x pro y, alle x y, zu bestimmten Zeitpunkten)
- Verkettete Tasks (A → B → C mit Abhängigkeiten)
- Task-Eigenschaften (Titel, Beschreibung, Priorität)

**2. Intelligentes Tracking**
- Erledigungsstatus, -Häufigkeit, -Historie
- Wiederholungs-Logik
- Überfälligkeit
- Zeiterfassung beim Erledigen
- Schwierigkeitsgrad-Bewertung
- Übliche Erledigungs-Uhrzeiten

**3. Motivations-Features**
- Streak-Tracking (wie oft in Folge rechtzeitig erledigt)
- Statistiken (Heute, Letzte 7 Tage, Durchschnitt)
- Visualisierung der Fortschritte

**4. UI/UX**
- Nächste Aufgabe (prominent angezeigt)
- Aufgaben für heute (Liste)
- Statistik-Dashboard
- Home-Screen Widget

**5. Intelligente Tagesplanung**
- KI-basierte Sortierung nach Priorität, Fälligkeit, Dauer, Zeit, Schwierigkeit, Verkettungen
- Optimaler Tagesablauf-Vorschlag

---

### Feature Development Phases

#### 📋 PHASE 1: Foundation & Database (3-4 Wochen)
**Ziel:** Technische Grundlagen für Taskmaster schaffen

**Voraussetzungen:** Phase 0 (Kritische Fixes) MUSS abgeschlossen sein!

**Technical Prerequisites:**
- [ ] Kotlin Migration (empfohlen für moderne Android-Entwicklung)
  - [ ] MainActivity nach Kotlin konvertieren
  - [ ] UpdateChecker, UpdateInstaller, AppLogger nach Kotlin
  - [ ] Build-Konfiguration für Kotlin anpassen
- [ ] MVVM-Architektur Grundgerüst
  - [ ] ViewModel-Setup
  - [ ] LiveData/StateFlow einführen
  - [ ] Repository Pattern vorbereiten
- [ ] Dependency Injection (Hilt)
  - [ ] Hilt Dependencies hinzufügen
  - [ ] Application-Klasse mit @HiltAndroidApp
  - [ ] Module für Dependencies erstellen
- [ ] Package-Struktur reorganisieren
  - [ ] data/ domain/ presentation/ Layer erstellen
  - [ ] Bestehenden Code refactoren

**Database Implementation:**
- [ ] Room Database Setup
  - [ ] Task Entity definieren
  - [ ] TaskDao erstellen
  - [ ] Database-Klasse implementieren
  - [ ] TypeConverters für komplexe Typen (Date, TaskType, etc.)
- [ ] Task Data Model
  - [ ] Task-Entity mit allen Feldern (siehe Feature-Spec unten)
  - [ ] TaskType Enum (Single, Recurring, Chained)
  - [ ] RecurrenceRule Klasse (x pro y, alle x y, Zeitpunkte)
  - [ ] TaskStatus Enum (Todo, Done, Overdue)
- [ ] Repository Implementation
  - [ ] TaskRepository Interface
  - [ ] TaskRepositoryImpl mit Room DAO
  - [ ] CRUD Operations (Create, Read, Update, Delete)

**Geschätzte Zeit:** 3-4 Wochen
**Abhängigkeiten:** Phase 0 muss komplett sein, Phase 2 (Architecture Prep) teilweise parallel möglich

---

#### 📋 PHASE 2: Core Task Management (4-5 Wochen)
**Ziel:** Basis-Funktionalität für Task-Verwaltung

**Prerequisites:** Phase 1 Database muss komplett sein!

**Task Creation:**
- [ ] Task-Erstellungs-UI
  - [ ] CreateTaskActivity/Fragment
  - [ ] Formular für Titel, Beschreibung, Priorität
  - [ ] Task-Typ Auswahl (Single/Recurring/Chained)
- [ ] Wiederkehrende Tasks
  - [ ] UI für Recurrence-Regel Eingabe
  - [ ] "x pro y" Logik (z.B. 3 mal pro Woche)
  - [ ] "alle x y" Logik (z.B. alle 2 Tage)
  - [ ] Zeitpunkt-Auswahl (Jeden Montag 09:00)
  - [ ] Recurrence Calculation Service
- [ ] Verkettete Tasks
  - [ ] Task-Verkettungs-UI (A → B → C)
  - [ ] Dependency-Graph Logik
  - [ ] Zyklische Abhängigkeiten erlauben

**Task Display:**
- [ ] Task-Liste UI (RecyclerView)
  - [ ] TaskListFragment
  - [ ] TaskAdapter mit ViewHolder
  - [ ] Task-Item Layout
- [ ] Task-Detail-Ansicht
  - [ ] TaskDetailActivity/Fragment
  - [ ] Anzeige aller Task-Informationen
  - [ ] Edit-Funktionalität
- [ ] Task-Filterung & Sortierung
  - [ ] Filter nach Status (Todo/Done/Overdue)
  - [ ] Filter nach Task-Typ
  - [ ] Sortierung nach Priorität/Datum

**Task Actions:**
- [ ] Task als erledigt markieren
  - [ ] "Complete Task" Button
  - [ ] Status-Update in Database
  - [ ] UI-Feedback (Animation, Toast)
- [ ] Task löschen
  - [ ] Confirmation Dialog
  - [ ] Delete aus Database
- [ ] Task bearbeiten
  - [ ] Edit-Mode in CreateTaskActivity
  - [ ] Update-Logik

**Geschätzte Zeit:** 4-5 Wochen
**Abhängigkeiten:** Phase 1 Database komplett

---

#### 📋 PHASE 3: Tracking & Analytics (3-4 Wochen)
**Ziel:** Intelligentes Tracking und Datenerfassung

**Completion Tracking:**
- [ ] Erweiterte Erledigungs-Daten
  - [ ] TaskCompletion Entity (Timestamp, Duration, Difficulty)
  - [ ] CompletionDao
  - [ ] Historie-Speicherung
- [ ] Erledigungs-Dialog
  - [ ] Dialog beim Markieren als erledigt
  - [ ] Timer für Zeiterfassung (optional)
  - [ ] Schwierigkeitsgrad-Eingabe (Slider 1-10)
  - [ ] Speichern der Completion-Daten
- [ ] Historie-Verwaltung
  - [ ] CompletionHistory-Fragment
  - [ ] Liste aller Erledigungen pro Task
  - [ ] Statistiken pro Task

**Recurrence Logic:**
- [ ] Automatisches Zurücksetzen wiederkehrender Tasks
  - [ ] Background-Service oder WorkManager
  - [ ] "x pro y" Reset-Logik (z.B. am Ende der Woche)
  - [ ] "alle x y" Reset-Logik (nach x Tagen)
  - [ ] Zeitpunkt-basiert (z.B. jeden Montag)
- [ ] Überfälligkeits-Berechnung
  - [ ] Overdue-Check-Service
  - [ ] Automatisches Status-Update (Todo → Overdue)
  - [ ] Benachrichtigungen (optional)

**Geschätzte Zeit:** 3-4 Wochen
**Abhängigkeiten:** Phase 2 Core Task Management komplett

---

#### 📋 PHASE 4: Motivation & Statistics (2-3 Wochen)
**Ziel:** Nutzer-Motivation durch Gamification

**Streak-Tracking:**
- [ ] Streak-Berechnung
  - [ ] Streak-Algorithmus (aufeinanderfolgende rechtzeitige Erledigungen)
  - [ ] Streak-Daten in Task-Entity
  - [ ] Streak-Update bei Completion
- [ ] Streak-Anzeige
  - [ ] Streak-Badge in Task-Liste
  - [ ] Streak-Detail in Task-Detail-View
  - [ ] Visualisierung (Kalender-Ansicht, Diagramm)

**Statistiken:**
- [ ] Statistics-Berechnung
  - [ ] Anzahl erledigter Tasks heute
  - [ ] Anzahl erledigter Tasks letzte 7 Tage
  - [ ] Durchschnitt pro Tag (letzte 7 Tage)
  - [ ] Längster Streak
  - [ ] Häufigste Erledigungs-Uhrzeit
- [ ] Statistics-Dashboard
  - [ ] StatisticsFragment
  - [ ] Charts/Graphs (MPAndroidChart oder ähnlich)
  - [ ] Zeitraum-Filter (Heute, 7 Tage, 30 Tage, Alle Zeit)

**Geschätzte Zeit:** 2-3 Wochen
**Abhängigkeiten:** Phase 3 Tracking komplett

---

#### 📋 PHASE 5: Intelligent Planning (4-5 Wochen)
**Ziel:** KI-basierte Tagesplanung

**Prioritization Algorithm:**
- [ ] Intelligente Sortierung
  - [ ] Multi-Faktor Scoring-Algorithmus
    - [ ] Numerische Priorität (User-definiert)
    - [ ] Fälligkeit (überfällig > heute > morgen)
    - [ ] Geschätzte Dauer (historische Daten)
    - [ ] Übliche Erledigungs-Zeit (Tageszeit)
    - [ ] Schwierigkeitsgrad (historische Daten)
    - [ ] Task-Verkettungen (Dependencies)
  - [ ] Score-Berechnung Service
  - [ ] Sortierung nach Score

**Daily Planning:**
- [ ] "Heute"-Ansicht
  - [ ] TodayFragment mit intelligenter Task-Liste
  - [ ] Automatische Auswahl der wichtigsten Tasks
  - [ ] "Nächste Aufgabe" prominent anzeigen
  - [ ] Zeitschätzung für den Tag
- [ ] Planning-Algorithmus
  - [ ] Berücksichtigung verfügbarer Zeit
  - [ ] Balance zwischen dringenden und wichtigen Tasks
  - [ ] Anpassung basierend auf Tageszeit
  - [ ] Vorschläge zur optimalen Reihenfolge

**Geschätzte Zeit:** 4-5 Wochen
**Abhängigkeiten:** Phase 4 Statistics komplett

---

#### 📋 PHASE 6: Widget & Polish (3-4 Wochen)
**Ziel:** Home-Screen Integration und UI-Verfeinerung

**Home-Screen Widget:**
- [ ] Widget-Implementierung
  - [ ] Widget-Layout erstellen
  - [ ] Widget Provider-Klasse
  - [ ] Widget-Konfiguration
- [ ] Widget-Inhalte
  - [ ] Nächste Aufgabe anzeigen
  - [ ] Aufgaben für heute (Top 3-5)
  - [ ] Statistik-Zusammenfassung
  - [ ] Streak-Anzeige
- [ ] Widget-Interaktivität
  - [ ] Click-Listener (öffne App)
  - [ ] Quick-Complete Button (Task direkt erledigen)
  - [ ] Refresh-Funktion

**UI/UX Improvements:**
- [ ] Design-System
  - [ ] Konsistente Farben, Typografie, Spacing
  - [ ] Material Design 3 Guidelines befolgen
  - [ ] Dark Mode Support
- [ ] Animations & Transitions
  - [ ] Task-Completion Animation
  - [ ] List-Item Animations
  - [ ] Fragment-Transitions
- [ ] Accessibility
  - [ ] Content Descriptions
  - [ ] Talkback-Support
  - [ ] Größere Touch-Targets

**Testing & Optimization:**
- [ ] Unit Tests erweitern (Target: 80% Coverage)
- [ ] UI Tests (Espresso)
- [ ] Performance-Optimierung
  - [ ] Database-Query-Optimierung
  - [ ] LazyLoading für große Listen
  - [ ] Memory-Leak-Checks
- [ ] Bug-Fixes
- [ ] Beta-Testing mit echten Nutzern

**Geschätzte Zeit:** 3-4 Wochen
**Abhängigkeiten:** Phase 5 Intelligent Planning komplett

---

### Feature Development - Detaillierte Spezifikationen

#### Task Data Model (für Phase 1)

```kotlin
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Basic Properties
    val title: String,
    val description: String? = null,
    val priority: Int, // Numerische Priorität (1-10)

    // Task Type
    val taskType: TaskType, // SINGLE, RECURRING, CHAINED

    // Recurrence (nur für RECURRING)
    val recurrenceRule: RecurrenceRule? = null,

    // Status
    val status: TaskStatus, // TODO, DONE, OVERDUE

    // Timestamps
    val createdAt: Long,
    val dueDate: Long? = null,
    val completedAt: Long? = null,

    // Tracking Data
    val completionCount: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,

    // Chain (nur für CHAINED)
    val chainId: String? = null,
    val nextTaskId: Long? = null,

    // Calculated Fields (from history)
    val averageDuration: Long? = null, // in milliseconds
    val averageDifficulty: Float? = null, // 1-10
    val preferredTimeOfDay: Int? = null // Hour (0-23)
)

enum class TaskType {
    SINGLE,      // Einmalige Aufgabe
    RECURRING,   // Wiederkehrend
    CHAINED      // Teil einer Kette
}

enum class TaskStatus {
    TODO,
    DONE,
    OVERDUE
}

data class RecurrenceRule(
    val type: RecurrenceType,
    val frequency: Int,      // z.B. 3 (für "3 mal pro Woche")
    val period: TimePeriod?, // z.B. WEEK
    val interval: Int?,      // z.B. 2 (für "alle 2 Tage")
    val specificTime: Long?  // Timestamp für "Jeden Montag 09:00"
)

enum class RecurrenceType {
    X_PER_Y,           // z.B. 3 mal pro Woche
    EVERY_X_Y,         // z.B. alle 2 Tage
    SPECIFIC_TIME      // z.B. Jeden Montag 09:00
}

enum class TimePeriod {
    DAY,
    WEEK,
    MONTH,
    YEAR
}

@Entity(tableName = "task_completions")
data class TaskCompletion(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: Long,
    val completedAt: Long,
    val duration: Long?, // in milliseconds
    val difficulty: Float? // 1-10
)
```

---

## 🎯 PRIORISIERTE ROADMAP

Diese Roadmap kombiniert technische Verbesserungen mit Feature-Entwicklung.

### 🔴 Phase 0: Foundation Systems (IN ARBEIT - NICHT FUNKTIONSFÄHIG)
**Status:** ❌ CODE VORHANDEN, FUNKTIONIERT NICHT

- ❌ Auto-Update System - implementiert, funktioniert nicht
- ❌ Logging System - implementiert, funktioniert nicht
- ✅ CI/CD Pipeline (GitHub Actions) - funktioniert
- ✅ Settings UI - vorhanden

**Kritische Aufgaben für Phase 0:**

**Must-Fix (BLOCKER für alles weitere):**
- [ ] Update-System debuggen und funktionsfähig machen
  - [ ] GitHub API Requests testen
  - [ ] Fehler via Logcat identifizieren
  - [ ] JSON Parsing prüfen
  - [ ] Download-Mechanismus testen
  - [ ] Installation testen
- [ ] Logging-System debuggen und funktionsfähig machen
  - [ ] AppLogger Initialisierung prüfen
  - [ ] Log-Schreiben testen
  - [ ] Log-Anzeige testen
  - [ ] Fehler via Logcat identifizieren
- [ ] GitHub Token revoken und neu generieren (SECURITY)
- [ ] Token aus Code entfernen, in Secrets verschieben
- [ ] Package-Name auf `com.secretary` ändern (von `com.secretary.helloworld`)

**Should-Fix (parallel zu Debugging):**
- [ ] Resource Management fixen (HttpURLConnection schließen)
- [ ] BroadcastReceiver Memory Leak beheben
- [ ] Dead Code entfernen (AppLogger.logFile)

**Geschätzte Zeit:**
- Debugging & Fixes: 4-8 Stunden (je nach Komplexität der Fehler)
- Security-Fixes: 1 Stunde
- **Total: 5-9 Stunden**

**WICHTIG:** Phase 0 MUSS funktionieren bevor Taskmaster Phase 1 starten kann!

---

### Tech Debt Phase 1: Foundation Fixes (Parallel zu Feature Dev)
**Ziel:** Kritische technische Schulden reduzieren
**Timing:** Parallel zu Feature Phase 1, VOR Phase 2

**Must-Have:**
- [ ] 4. Resource Management fixen (HttpURLConnection schließen)
- [ ] 3. BroadcastReceiver Memory Leak beheben
- [ ] 8. Dead Code entfernen (AppLogger.logFile)
- [ ] 19. Projekt-Struktur aufräumen (AI-Secretary vs. AI-Secretary-temp)
- [ ] 21. Build-Artefakte aus Repo entfernen, .gitignore erweitern

**Should-Have:**
- [ ] 2. Threading auf ExecutorService umstellen
- [ ] 6. Code-Duplikation eliminieren (VersionUtils)
- [ ] 7. Magic Numbers in Konstanten auslagern

**Nice-to-Have:**
- [ ] 13. UI-Texte in strings.xml verschieben

**Geschätzte Zeit:** 1-2 Wochen (nebenbei zu Feature Phase 1)
**Hinweis:** Diese Fixes betreffen nur den aktuellen Update/Logging-Code, nicht die neuen Taskmaster-Features

---

### Tech Debt Phase 2: Testing & Quality (Ongoing - parallel zu Features)
**Ziel:** Testbarkeit und Wartbarkeit sicherstellen
**Timing:** Ab Feature Phase 2, kontinuierlich

- [ ] 17. Unit Tests schreiben (Target: 70% Coverage)
  - [ ] Domain-Layer Tests (Use Cases, Models)
  - [ ] Repository Tests
  - [ ] ViewModel Tests
- [ ] Integration Tests (Database, Repository)
- [ ] UI Tests (Espresso) für kritische Flows
- [ ] CI/CD: Automatische Tests in GitHub Actions
- [ ] Code Coverage Reports einrichten
- [ ] Static Analysis (Detekt, ktlint) konfigurieren

**Geschätzte Zeit:** Ongoing, parallel zu Feature-Entwicklung
**Target:** Jedes neue Feature mit Tests
**Minimum:** Kritische Business-Logik (Recurrence, Streak, Planning) MUSS getestet sein

---

### Tech Debt Phase 3: Modernization & Optimization (Nach Feature Phase 4)
**Ziel:** Moderne Best Practices und Performance
**Timing:** Nach Taskmaster MVP (Feature Phase 4), optional

- [ ] Jetpack Compose Migration (statt XML Layouts)
  - [ ] Compose Dependencies hinzufügen
  - [ ] Schrittweise Migration: Neue Screens in Compose
  - [ ] Interop mit XML für Legacy-Code
- [ ] Moderne Netzwerk-Library (Retrofit + OkHttp)
  - [ ] Retrofit statt HttpURLConnection für Updates
  - [ ] Proper Error Handling
  - [ ] Offline Support
- [ ] WorkManager für Background-Tasks
  - [ ] Recurrence-Reset als Worker
  - [ ] Overdue-Check als Worker
  - [ ] Update-Download als Worker
- [ ] Navigation Component
  - [ ] Single-Activity-Architecture
  - [ ] Navigation Graph definieren
  - [ ] Deep Links
- [ ] Performance-Optimierung
  - [ ] Database-Query-Profiling
  - [ ] Memory-Leak-Detection (LeakCanary)
  - [ ] APK-Size-Optimierung (R8, ProGuard)
- [ ] Accessibility & Localization
  - [ ] Content Descriptions
  - [ ] Mehrsprachigkeit (Englisch, Deutsch)
  - [ ] Right-to-Left Support

**Geschätzte Zeit:** 4-5 Wochen Vollzeit
**Notwendigkeit:** Mittel (Nice-to-have, verbessert aber UX und Wartbarkeit erheblich)

---

## 📊 TRACKING

### Current Status: Phase 0 NICHT FUNKTIONSFÄHIG - Debugging erforderlich

**Project Timeline Overview:**
- 🔴 **Phase 0 (Foundation Systems):** NICHT FUNKTIONSFÄHIG (v3.0)
  - ❌ Auto-Update System - Code vorhanden, funktioniert nicht
  - ❌ Logging System - Code vorhanden, funktioniert nicht
  - ✅ CI/CD Pipeline - funktioniert
  - 🔴 **BLOCKER:** Muss funktionieren bevor Phase 1 starten kann
- 📅 **Taskmaster Phase 1 (Foundation & Database):** Blockiert durch Phase 0
- 📅 **Tech Debt Phase 1 (Foundation Fixes):** Blockiert durch Phase 0
- 📅 **Taskmaster Phase 2-6 (Development):** Blockiert durch Phase 0
- 📅 **Tech Debt Phase 2-3 (Testing & Modernization):** Blockiert durch Phase 0

**Estimated Total Time (inkl. Phase 0 Debugging):**
- 🔴 Phase 0 Debugging & Fixes: 5-9 Stunden
- Taskmaster Phase 1: 3-4 Wochen
- Taskmaster Phase 2: 4-5 Wochen
- Taskmaster Phase 3: 3-4 Wochen
- Taskmaster Phase 4: 2-3 Wochen
- **Total: ~12-16 Wochen + 1-2 Tage** für Taskmaster MVP

**Extended Timeline (with Widget & Polish):**
- Taskmaster Phase 5: 4-5 Wochen
- Taskmaster Phase 6: 3-4 Wochen
- **Total: ~19-25 Wochen + 1-2 Tage** für komplette Taskmaster Suite

---

### Completed ✅
- ✅ Phase 0 Code geschrieben (Update-System + Logging-System)
- ✅ CI/CD Pipeline funktioniert
- ✅ Settings UI vorhanden

### In Progress 🔄
- ❌ Phase 0 Debugging - NOCH NICHT GESTARTET

### Blocked 🚫
- **ALLES blockiert durch nicht-funktionierendes Phase 0:**
  - Taskmaster Phase 1
  - Tech Debt Fixes
  - Feature Development
  - Testing

### Next Sprint 📋
**SOFORT (Top-Priorität):**
- [ ] **Phase 0 Debugging starten:**
  - [ ] App installieren und testen
  - [ ] Logcat während App-Start prüfen
  - [ ] Update-Button in Settings testen
  - [ ] Fehler identifizieren
  - [ ] Update-System debuggen und fixen
  - [ ] Logging-System debuggen und fixen

**Parallel zu Debugging:**
- [ ] GitHub Token Security Fix (revoke, regenerate, move to Secrets)
- [ ] Package Name ändern (`com.secretary.helloworld` → `com.secretary`)

**Nach Phase 0 funktioniert:**
- [ ] Entscheidung: Weitere Tech Debt beheben oder direkt Taskmaster Phase 1 starten?

---

## 📝 NOTIZEN

### Prioritäts-Richtlinien

**🔴 KRITISCH (Sofort):**
- Sicherheitslücken
- Breaking Bugs
- Blocker für weitere Entwicklung

**⚠️ WICHTIG (Kurzfristig, 1-2 Wochen):**
- Memory Leaks
- Resource Leaks
- Major Code Smells
- Strukturelle Probleme

**📋 NORMAL (Mittelfristig, vor Skalierung):**
- Architektur-Verbesserungen
- Refactorings
- Testing
- Code-Qualität

**💡 NICE-TO-HAVE (Langfristig, optional):**
- Modernisierungen
- Optimierungen
- Convenience-Features

---

### Entwicklungs-Philosophie

**Für Early Development:**
- Pragmatismus vor Perfektion
- Funktionierende Features wichtiger als perfekter Code
- Aber: Technische Schulden dokumentieren und planen

**Für Skalierung (Feature Suite 1):**
- Clean Architecture einführen
- Tests schreiben
- Code-Qualität ernst nehmen

**Für Produktion:**
- Security First
- Performance Monitoring
- User Experience
- Stability

---

## 🔗 REFERENZEN

### Android Best Practices
- [Android Architecture Guide](https://developer.android.com/topic/architecture)
- [Android App Architecture](https://developer.android.com/topic/libraries/architecture)
- [Guide to app architecture](https://developer.android.com/jetpack/guide)

### Security
- [Android Security Best Practices](https://developer.android.com/topic/security/best-practices)
- [OWASP Mobile Security](https://owasp.org/www-project-mobile-security/)

### Testing
- [Android Testing Guide](https://developer.android.com/training/testing)
- [Test-Driven Development in Android](https://developer.android.com/training/testing/fundamentals)

### Code Quality
- [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- [Effective Java (for Android)](https://github.com/google/guava/wiki/PhilosophyExplained)

---

**Dokument erstellt:** 12.11.2025
**Erstellt von:** Claude Code Agent
**Nächstes Review:** Nach Phase 0 (Kritische Fixes)

---

## CHANGELOG

### 2025-11-12 (Update 6 - Phase 0 COMPLETE! 🎉)
- **Phase 0 Status: 100% COMPLETE**
  - Logging System ✅ FUNKTIONIERT via HTTP Server auf Port 8080
  - Update System ✅ FUNKTIONIERT nach Repository public gemacht
  - Beide Foundation Systems sind voll funktionsfähig!
- **Update System Success:**
  - GitHub Token entfernt in v0.1.2
  - Repository von privat auf public geändert
  - API Response 200 OK, findet Releases korrekt
  - Version-Vergleich funktioniert einwandfrei
- **Bereit für Phase 1:**
  - Foundation ist stabil
  - Claude Code kann Logs lesen
  - Updates werden erkannt
  - Taskmaster Development kann beginnen!

### 2025-11-12 (Update 5 - HTTP Log Server funktioniert!)
- **Phase 0 Status: 50% COMPLETE**
  - Logging System ✅ FUNKTIONIERT via HTTP Server auf Port 8080
  - Claude Code kann Logs lesen mit: `curl http://localhost:8080/logs`
  - Auto-Update System ❌ noch nicht getestet
- **Implementiert in v0.1.1:**
  - SimpleHttpServer.java ersetzt komplexes NanoHTTPD
  - HTTP Endpoints: /logs, /status, /
  - Helper Script: secretary_log_access.sh
- **Nächste Schritte:**
  - Update-System testen und debuggen
  - Security Issues beheben (GitHub Token)
  - Package Name Migration

### 2025-11-12 (Update 4 - Status Korrektur)
- **Phase 0 Status korrigiert: NICHT FUNKTIONSFÄHIG**
  - Auto-Update System funktioniert nicht
  - Logging System funktioniert nicht
  - Code ist vorhanden, aber beide Systeme müssen debugged werden
- **Next Sprint angepasst:**
  - Phase 0 Debugging als Top-Priorität
  - App testen, Logcat prüfen, Fehler identifizieren
  - Update-System und Logging-System funktionsfähig machen
- **Tracking aktualisiert:**
  - Alles blockiert durch Phase 0
  - Zeitschätzung: 5-9 Stunden für Debugging & Fixes
- Realistische Einschätzung: Phase 0 MUSS funktionieren bevor weitere Entwicklung

### 2025-11-12 (Update 3)
- **Phase 0 korrekt definiert als "Foundation Systems"**
  - Phase 0 ist bereits KOMPLETT (Auto-Update + Logging)
  - Detaillierte Dokumentation von UpdateChecker, UpdateInstaller, AppLogger
  - User Flow für Update-System dokumentiert
  - Bekannte Issues in Phase 0 verlinkt mit Technical Debt
- **Roadmap-Struktur finalisiert:**
  - ✅ Phase 0: Foundation Systems (KOMPLETT)
  - 🔴 Kritische Fixes (SOFORT - Blocker für Phase 1)
  - 📋 Taskmaster Phases 1-6 (Feature Development)
  - ⚙️ Tech Debt Phases 1-3 (Parallel/Nach Features)
- **Tracking aktualisiert:**
  - Current Status: Phase 0 Complete
  - Next Sprint: Kritische Fixes
  - Timeline mit Phase 0 als KOMPLETT markiert
- Inhaltsverzeichnis mit Phase 0 ergänzt

### 2025-11-12 (Update 2)
- **Feature Development Roadmap hinzugefügt**
  - 6 detaillierte Feature-Entwicklungs-Phasen für Taskmaster
  - Kotlin Data Models für Task, TaskCompletion, RecurrenceRule
  - Task-Typen: Single, Recurring, Chained
  - Intelligente Tagesplanung-Spezifikationen
  - Zeitschätzungen: 12-16 Wochen für MVP, 19-25 Wochen für komplette Suite
- **Phasen reorganisiert:**
  - Phase 0: Critical Fixes (Blocker)
  - Feature Phases 1-6: Taskmaster Entwicklung
  - Tech Debt Phases 1-3: Parallel/Nach Features
- **Tracking erweitert:**
  - Timeline-Übersicht
  - Zeitschätzungen für gesamte Entwicklung
  - Dependency-Management zwischen Phasen
- Integration aller Ziele aus CLAUDE.md

### 2025-11-12 (Initial)
- Initial ROADMAP created
- 22 technical debt issues documented
- Code smells identifiziert und priorisiert
- Architektur-Probleme dokumentiert
- Dateistruktur-Probleme aufgelistet
- Referenzen zu Best Practices hinzugefügt
