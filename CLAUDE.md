# AI Secretary - Native Android App

## 🔒 Projektvision

**WICHTIG:** Sektionen, die mit dem 🔒 Emoji markiert sind, dürfen nur mit ausdrücklicher Erlaubnis des Projektinhabers editiert werden.

---

## 🔒 Gesamtkonzept

AI-Secretary ist als umfassendes Alltags-Planungstool konzipiert. Die App soll Nutzern helfen, ihre täglichen Aufgaben intelligent zu organisieren, zu tracken und zu priorisieren.

### Technische Entscheidung
- **Ursprünglicher Prototyp:** Hybrid-App (Capacitor + Web-Technologien)
- **Aktuelle Entwicklung:** Native Android-App (bessere Performance, System-Integration, kleinere APK)
- Der Hybrid-Prototyp dient als Proof of Concept

---

## 🔒 Feature Suite 1: "Taskmaster"

### Übersicht
Taskmaster ist die erste Feature-Suite der AI-Secretary App und bildet das Kernstück der Aufgabenverwaltung.

### 1. Umfassende Todo-Organisation

#### Task-Typen
- **Einzelne Tasks:** Einmalige Aufgaben
- **Wiederkehrende Tasks:**
  - `x pro y`: z.B. "3 mal pro Woche" (flexible Verteilung innerhalb des Zeitraums)
  - `alle x y`: z.B. "alle 2 Tage" (festes Intervall)
  - Zu bestimmten Zeitpunkten: z.B. "Jeden Montag 09:00 Uhr"
- **Verkettete Tasks:** Sequenzen wie A → B → C → A (zyklische Abhängigkeiten)

#### Task-Eigenschaften
- **Titel:** Kurze Beschreibung der Aufgabe
- **Beschreibung:** Ausführliche Details (optional)
- **Numerische Priorität:** Zahlenwert zur Priorisierung

### 2. Intelligentes Tracking

Die App trackt folgende Daten pro Task:
- **Erledigungsstatus:** Erledigt / Unerledigt / Überfällig
- **Erledigungs-Häufigkeit:** Wie oft wurde die Aufgabe erledigt?
- **Erledigungs-Historie:** Wann wurde sie die letzten Male erledigt?
- **Wiederholungs-Logik:** Wann wird eine wiederkehrende Aufgabe wieder auf "unerledigt" gesetzt?
- **Überfälligkeit:** Wie lange ist die Aufgabe bereits überfällig?

#### Zusätzliche Tracking-Daten beim Erledigen
- **Benötigte Zeit:** Wie lange hat die Erledigung gedauert?
- **Schwierigkeitsgrad:** Wie einfach/schwer war die Aufgabe? (User-Input)
- **Übliche Erledigungs-Uhrzeiten:** Zu welchen Tageszeiten wird die Aufgabe typischerweise erledigt?

### 3. Motivations-Features

#### Streak-Tracking
- Anzeige von "Streaks": Wie oft wurde eine Aufgabe rechtzeitig in Folge erledigt?
- Visualisierung der Streak-Länge

#### Statistiken
- **Heute:** Anzahl erledigter Aufgaben heute
- **Letzte 7 Tage:** Anzahl erledigter Aufgaben in den letzten 7 Tagen
- **Durchschnitt (7 Tage):** Durchschnittliche Anzahl erledigter Aufgaben pro Tag (letzten 7 Tage)

### 4. UI/UX-Features

#### App-Ansichten
- **Nächste Aufgabe:** Prominent angezeigte, wichtigste nächste Aufgabe
- **Aufgaben für heute:** Liste aller für heute geplanten Aufgaben
- **Statistik-Dashboard:** Streaks und Erledigungs-Statistiken

#### Home-Screen Widget
Das Widget zeigt auf dem Android-Homescreen:
- Nächste Aufgabe
- Aufgaben für heute
- Streak- und Statistik-Zusammenfassung

### 5. Intelligente Tagesplanung

Basierend auf allen gesammelten Daten soll die App einen intelligenten Tagesplan generieren:

#### Berücksichtigte Faktoren
- **Priorität:** Numerische Task-Priorität
- **Fälligkeit:** Überfällige und heute fällige Tasks
- **Geschätzte Dauer:** Basierend auf historischen Zeiterfassungen
- **Übliche Erledigungs-Zeit:** Zeitpunkt, zu dem die Aufgabe normalerweise erledigt wird
- **Schwierigkeitsgrad:** Historische Schwierigkeits-Bewertungen
- **Verkettungen:** Abhängigkeiten zwischen Tasks

#### Ziel
Automatische, intelligente Sortierung und Vorschläge für einen optimalen Tagesablauf.

---

## Entwicklungs-Roadmap

### Phase 1: Grundlagen (aktuell)
- [ ] Native Android Projekt-Setup
- [ ] Datenbank-Schema (Room)
- [ ] Basis-UI (MainActivity, Task-Liste)

### Phase 2: Core Taskmaster Features
- [ ] Task-Erstellung (alle Typen)
- [ ] Task-Tracking
- [ ] Erledigungs-Workflow (Zeit, Schwierigkeit)

### Phase 3: Intelligenz & Motivation
- [ ] Streak-Berechnung
- [ ] Statistiken
- [ ] Intelligente Sortierung

### Phase 4: Widget & Polish
- [ ] Home-Screen Widget
- [ ] UI/UX-Verbesserungen
- [ ] Testing & Optimierung

---

## Technologie-Stack

### Geplant
- **Sprache:** Kotlin (empfohlen) oder Java
- **UI-Framework:** Jetpack Compose oder XML-Layouts
- **Datenbank:** Room (SQLite)
- **Architektur:** MVVM (Model-View-ViewModel)
- **Build-System:** Gradle

### Entwicklungsumgebung
- **Primär:** Termux auf Android
- **Synchronisation:** Git (GitHub/GitLab)
- **Backup-Entwicklung:** Optional auf Laptop

---

## Build-System & Deployment

### Entscheidung: GitHub Actions für CI/CD (08.11.2024)

**Problem:** Gradle funktioniert nicht in Termux (JVM libiconv-Fehler)

**Lösung:** GitHub Actions Cloud-Build
- Automatischer APK-Build bei jedem Git Push
- Workflow: `.github/workflows/android-build.yml`
- Kein lokaler Gradle-Build erforderlich
- APK via GitHub Actions Artifacts verfügbar

**Implementierung:**
- Vollständige Gradle-Konfiguration erstellt (`app/build.gradle`, `settings.gradle`, `gradle.properties`)
- ProGuard-Regeln für Release-Builds (`app/proguard-rules.pro`)
- Debug-Keystore generiert (`~/.android/debug.keystore`)
- Material Design Themes (Holo → Material Migration)

**Status:** ✅ Produktionsreif - Bereit für GitHub Push

**Details:** Siehe `BUILD_INSTRUCTIONS.md`

---

## Notizen für Claude Code Agenten

- **Geschützte Bereiche:** Alle mit 🔒 markierten Sektionen nur mit expliziter User-Erlaubnis ändern
- **Feature-Requests:** Neue Features in "Entwicklungs-Roadmap" ergänzen
- **Design-Entscheidungen:** Bei Unklarheiten nachfragen, nicht annehmen

---

## Arbeitsumgebung - Detaillierte Analyse (09.11.2025)

### Hardware & System
- **Gerät:** Google Pixel 8 (aarch64)
- **Android Version:** 16
- **Kernel:** Linux 6.1.134-android14-11-g66e758f7d0c0-ab13748739
- **Termux Version:** googleplay.2025.10.05
- **Termux Tools:** 3.0.8

### Installierte Entwicklungswerkzeuge

#### Java Entwicklung
- **OpenJDK:** 21.0.9 (Termux Build)
- **Gradle:** 9.0.0
  - Kotlin: 2.2.0
  - Groovy: 4.0.27
  - Ant: 1.10.15
  - ⚠️ **Problem:** JVM libiconv-Fehler in Termux (bekanntes Issue)

#### Android Build-Tools
- **AAPT2:** 2.19
- **AAPT:** 13.0.0.6-22
- **Android Tools:** 35.0.2-5
- **dx:** ✅ Verfügbar
- **d8:** ❌ Nicht verfügbar (Alternative: dx)
- **zipalign:** ✅ Verfügbar
- **apksigner:** ✅ Verfügbar

#### Android SDK
- **Pfad:** `/data/data/com.termux/files/usr/lib/android-sdk`
- **Verfügbare Plattformen:**
  - Android API 28 (android.jar: 44.6 MB)
  - Android API 33 (android.jar: 25.9 MB)
  - Android API 35 (android.jar: 25.8 MB) ✅ **Empfohlen**

#### Zusätzliche Tools
- **Git:** 2.51.0 (Update verfügbar: 2.51.2)
- **Node.js:** (falls erforderlich)
- **npm:** (falls erforderlich)

---

## Funktionierende Build-Workflows für Termux

### Option 1: GitHub Actions (Aktuell implementiert) ✅ **EMPFOHLEN**

**Vorteile:**
- Keine lokalen Gradle-Probleme
- Automatisierte Builds bei jedem Push
- Professionelle CI/CD-Pipeline
- APK-Download via GitHub Artifacts

**Status:** Produktionsreif, bereits konfiguriert

**Workflow:**
1. Code lokal in Termux bearbeiten
2. Git commit & push zu GitHub
3. GitHub Actions baut APK automatisch
4. APK herunterladen und auf Gerät installieren

---

### Option 2: Manueller AAPT2-Build (Alternative) 🔧

Für lokale Entwicklung ohne GitHub Actions ist ein manueller Build-Prozess möglich:

#### Vollständiger Build-Prozess

**1. Projektstruktur erstellen:**
```
project/
├── AndroidManifest.xml
├── res/
│   ├── values/
│   │   └── strings.xml
│   ├── layout/
│   │   └── activity_main.xml
│   └── drawable/
└── src/
    └── com/
        └── example/
            └── MainActivity.java
```

**2. Ressourcen kompilieren:**
```bash
aapt2 compile \
  res/values/strings.xml \
  res/layout/activity_main.xml \
  -o compiled_res/
```

**3. Ressourcen verlinken (APK erstellen):**
```bash
aapt2 link \
  -o app_unsigned.apk \
  -I $PREFIX/lib/android-sdk/platforms/android-35/android.jar \
  --manifest AndroidManifest.xml \
  -R compiled_res/*.flat \
  --java gen/ \
  --auto-add-overlay
```

**4. Java-Code kompilieren:**
```bash
javac -d classes/ \
  -classpath $PREFIX/lib/android-sdk/platforms/android-35/android.jar \
  -sourcepath src/ \
  src/com/example/MainActivity.java \
  gen/com/example/R.java
```

**5. DEX erstellen (Bytecode konvertieren):**
```bash
dx --dex \
  --output=classes.dex \
  classes/
```

**6. DEX zur APK hinzufügen:**
```bash
# AAPT2 unterstützt kein DEX-Hinzufügen, daher manuell:
cd app_unsigned.apk  # APK ist ein ZIP
zip -uj app_unsigned.apk classes.dex
```

**7. APK alignieren:**
```bash
zipalign -v -p 4 \
  app_unsigned.apk \
  app_aligned.apk
```

**8. APK signieren:**
```bash
# Debug-Keystore (wenn vorhanden)
apksigner sign \
  --ks ~/.android/debug.keystore \
  --ks-key-alias androiddebugkey \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out app_signed.apk \
  app_aligned.apk
```

**9. APK installieren:**
```bash
# Über Termux API
termux-media-scan app_signed.apk
# Dann manuell installieren oder:
pm install app_signed.apk
```

#### Limitierungen des manuellen Builds

⚠️ **Wichtige Einschränkungen:**
- **Keine Support-Bibliotheken:** AppCompat, Material Components etc. sind schwer zu integrieren
- **Manuelle Dependency-Verwaltung:** Keine automatische AAR-Auflösung
- **Fehleranfällig:** Viele manuelle Schritte
- **Zeitaufwändig:** Jeder Build erfordert alle Schritte

**Empfehlung:** Nur für einfache "Hello World"-Apps oder Learning. Für echte Apps GitHub Actions nutzen.

---

### Option 3: Hybrid-Ansatz (Optimal für Entwicklung) 🎯

**Kombination beider Ansätze:**

1. **Lokale Entwicklung:**
   - Code in Termux schreiben/editieren
   - Schnelle Syntax-Checks
   - Git für Versionskontrolle

2. **Build & Test:**
   - Push zu GitHub
   - GitHub Actions baut APK
   - Lokale Installation & Test

3. **Debugging:**
   - Logs via `adb logcat` (wenn USB-Debugging)
   - Oder Termux-basierte Log-Tools

**Vorteile:**
- Beste Developer Experience
- Professioneller Workflow
- Keine Gradle-Probleme
- Schnelle Iterationen möglich

---

## Empfehlungen für AI-Secretary Projekt

### Kurzfristig (Hello World Phase)

1. **Einfache App ohne Dependencies:**
   - Nutze Standard-Android-Klassen (`Activity` statt `AppCompatActivity`)
   - Nutze System-Themes (`Theme.Material3.DayNight`)
   - Vermeide externe Libraries

2. **Build-Strategie:**
   - **Für Lernen:** Manueller AAPT2-Build zum Verständnis
   - **Für Produktion:** GitHub Actions (bereits konfiguriert)

3. **Test-APK erstellen:**
   - Minimale `AndroidManifest.xml`
   - Eine `MainActivity.java`
   - Ein Layout mit TextView "Hello World"

### Mittelfristig (Feature-Entwicklung)

1. **Dependency-Management:**
   - GitHub Actions erlaubt Gradle → voller Zugriff auf Maven-Dependencies
   - Room, Material Components, etc. problemlos nutzbar

2. **Entwicklungsumgebung:**
   - Code-Editing in Termux (Claude Code, vim, nano)
   - Commits via Git
   - Builds via GitHub Actions

3. **Testing:**
   - Unit Tests in GitHub Actions
   - Manuelle Tests auf dem Pixel 8
   - Optional: Firebase Test Lab

### Langfristig (Produktion)

1. **CI/CD-Pipeline erweitern:**
   - Automatische Tests
   - Release-Builds
   - Play Store Deployment (optional)

2. **Code-Qualität:**
   - Linting (ktlint für Kotlin)
   - Static Analysis
   - Code Coverage

---

## Nächste Schritte für Hello World App

1. **Projektstruktur erstellen:**
   ```
   AI-Secretary/
   ├── app/
   │   ├── src/main/
   │   │   ├── AndroidManifest.xml
   │   │   ├── java/com/secretary/
   │   │   │   └── MainActivity.java
   │   │   └── res/
   │   │       ├── layout/activity_main.xml
   │   │       └── values/strings.xml
   │   └── build.gradle
   ├── build.gradle
   └── settings.gradle
   ```

2. **Minimale Dateien erstellen:**
   - AndroidManifest.xml (targetSdk 35)
   - MainActivity.java (einfache Activity)
   - Layout mit "Hello World" TextView

3. **Build-Methode wählen:**
   - [ ] Manueller AAPT2-Build für lokales Lernen
   - [ ] GitHub Actions Push für produktive APK

4. **Installation & Test:**
   - APK auf Pixel 8 installieren
   - App öffnen und Funktionalität prüfen

---

## Termux-Spezifische Notizen

### Bekannte Probleme
- **Gradle libiconv-Fehler:** Gradle kann nicht lokal in Termux ausgeführt werden
- **D8 nicht verfügbar:** Alternative `dx` verwenden
- **LD_PRELOAD:** Wird für libtermux-exec.so gesetzt

### Workarounds
- GitHub Actions für Gradle-Builds
- `dx` statt `d8` für DEX-Konvertierung
- Manual zip für DEX-Integration in APK

### Hilfreiche Befehle
```bash
# Geräteinformationen
termux-info
getprop ro.build.version.release  # Android Version
getprop ro.product.model          # Gerätemodell

# Package-Verwaltung
pkg list-installed | grep android
pkg search aapt

# APK-Installation
pm install app.apk
am start -n com.secretary/.MainActivity

# Logs
logcat | grep Secretary
```

---

## Ressourcen & Referenzen

- **Termux Wiki:** https://wiki.termux.com/wiki/Main_Page
- **AAPT2 Docs:** https://developer.android.com/tools/aapt2
- **Android CLI Build:** https://developer.android.com/tools/building
- **Stack Overflow:** Zahlreiche Threads zu "build APK without Gradle"

---

**Analyse durchgeführt:** 09.11.2025
**Analysiert von:** Claude Code Agent
**Status:** ✅ Umgebung vollständig analysiert und dokumentiert

---

## Auto-Update System (Version 1.0+)

### Übersicht

Die App verfügt über ein vollautomatisches Update-System, das GitHub Releases nutzt um neue Versionen zu erkennen und zu installieren.

### Komponenten

#### 1. UpdateChecker.java
**Zweck:** Prüft GitHub Releases API nach neuen Versionen

**Funktionsweise:**
- Verbindet zu: `https://api.github.com/repos/ThonkTank/AI-Secretary/releases/latest`
- Verwendet GitHub Personal Access Token für API-Zugriff
- Vergleicht aktuelle App-Version mit neuester Release-Version
- Sucht nach `.apk` Assets im Release

**Key Features:**
- Asynchrone Ausführung (eigener Thread)
- Listener-Pattern für UI-Updates
- Umfangreiches Logging aller Schritte
- Fehlerbehandlung mit detaillierten Fehlermeldungen

**Verwendung:**
```java
UpdateChecker.checkForUpdates(context, new UpdateChecker.UpdateListener() {
    @Override
    public void onUpdateAvailable(String version, String downloadUrl, String changelog) {
        // Zeige Update-Dialog
    }

    @Override
    public void onNoUpdateAvailable() {
        // Informiere User
    }

    @Override
    public void onError(String error) {
        // Fehlerbehandlung
    }
});
```

#### 2. UpdateInstaller.java
**Zweck:** Lädt APK herunter und startet Installation

**Funktionsweise:**
- Nutzt Android DownloadManager für APK-Download
- Speichert APK in öffentlichem Downloads-Ordner
- BroadcastReceiver wartet auf Download-Abschluss
- Startet Package Installer automatisch

**Key Features:**
- System-Benachrichtigung während Download
- Automatische Installation nach Download
- Logging des gesamten Flows
- Fehlerbehandlung bei Download-Problemen

**Permissions erforderlich:**
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

#### 3. MainActivity Integration
**Version 1.0-1.1:** Auto-Check beim App-Start
**Version 1.2+:** Manueller Check über Settings-Dialog

**User Flow (Version 1.2):**
1. User öffnet App
2. Klickt auf Settings-Button (⚙ oben rechts)
3. Klickt auf "Check for Updates"
4. Bei verfügbarem Update: Dialog mit Changelog
5. "Download & Install" startet Download
6. System-Notification zeigt Fortschritt
7. Nach Download: Automatische Installation

### GitHub Actions Workflow

**Datei:** `.github/workflows/build-and-release.yml`

**Trigger:** Jeder Push auf main-Branch

**Build-Schritte:**
1. Checkout Code
2. Setup JDK 17
3. Android SDK Installation
4. AAPT2 Ressourcen-Kompilierung
5. Java-Kompilierung (Source 8, Target 8)
6. DEX-Erstellung mit d8
7. APK Alignment
8. APK Signierung (Debug-Keystore)
9. GitHub Release Erstellung
10. APK Upload als Release Asset

**Release-Format:**
- Tag: `v{version}` (z.B. v1.2)
- Asset: `AISecretary-signed.apk`
- Body: Automatisch generiert mit Commit-Message und Build-Info

### Versionierung

**Schema:** Semantic Versioning (Major.Minor)
- **Major:** Breaking Changes oder größere Feature-Suites
- **Minor:** Neue Features, UI-Änderungen, Bugfixes

**Tracking:**
- `android:versionCode` - Integer, inkrementell (für Android)
- `android:versionName` - String, für User sichtbar

**Beispiel:**
```xml
<manifest
    android:versionCode="3"
    android:versionName="1.2">
```

### API Rate Limits

**GitHub API:**
- Mit Token: 5000 Requests/Stunde
- Ohne Token: 60 Requests/Stunde

**In der App:** Token ist im Code eingebettet (für Entwicklung OK, für Production sollte Token server-side sein)

### Update-Flow Debugging

**Typische Log-Sequenz:**
```
[INFO] [MainActivity] === Application started ===
[INFO] [MainActivity] App version: 1.1 (code: 2)
[INFO] [MainActivity] Settings button clicked
[INFO] [MainActivity] User initiated update check from settings
[INFO] [UpdateChecker] Starting update check. Current version: 1.1 (code: 2)
[DEBUG] [UpdateChecker] GitHub API response code: 200
[INFO] [UpdateChecker] Latest version from GitHub: 1.2
[DEBUG] [UpdateChecker] Found 1 assets in release
[INFO] [UpdateChecker] Found APK asset: AISecretary-signed.apk
[INFO] [UpdateChecker] Update available! 1.1 -> 1.2
[INFO] [MainActivity] Update dialog shown to user for version 1.2
[INFO] [MainActivity] User accepted update download
[INFO] [UpdateInstaller] Starting download for version 1.2
[INFO] [UpdateInstaller] Download enqueued with ID: 12345
[INFO] [UpdateInstaller] Download completed for ID: 12345
[INFO] [UpdateInstaller] Starting installation for: file:///...
[INFO] [UpdateInstaller] APK installer intent launched successfully
```

### Bekannte Limitierungen

1. **Automatischer Download kann fehlschlagen:**
   - Permissions-Probleme
   - Netzwerk-Issues
   - Storage-Probleme
   - **Workaround:** Manuelle APK-Installation aus Downloads

2. **Keine Delta-Updates:**
   - Jedes Update lädt vollständige APK
   - Für kleine App (20KB) akzeptabel

3. **Keine Rollback-Funktion:**
   - Kein automatisches Rollback bei Problemen
   - User muss manuell alte Version installieren

### Zukünftige Verbesserungen

**Geplant:**
- [ ] In-App Update API (Google Play Core Library)
- [ ] Changelog-Formatierung (Markdown-Rendering)
- [ ] Update-Häufigkeit begrenzen (max. 1x pro Tag prüfen)
- [ ] Background-Updates (WorkManager)
- [ ] Delta-Updates für größere APKs

---

## Logging-System (Version 1.1+)

### Übersicht

Umfassendes Logging-System für Debugging und Fehleranalyse durch Claude Code.

### Komponente: AppLogger.java

**Zweck:** Zentrale Logging-Klasse mit automatischer Log-Rotation

**Features:**
- Thread-safe Singleton-Pattern
- Automatische Log-Bereinigung (max. 500 Zeilen)
- Drei Log-Level: INFO, DEBUG, ERROR
- Timestamp für jeden Eintrag
- Parallel-Logging zu Android Logcat

**Log-Format:**
```
[YYYY-MM-DD HH:mm:ss] [LEVEL] [TAG] Message
```

**Beispiel:**
```
[2025-11-09 14:05:32] [INFO] [MainActivity] === Application started ===
[2025-11-09 14:05:32] [DEBUG] [UpdateChecker] GitHub API response code: 200
[2025-11-09 14:05:33] [ERROR] [UpdateInstaller] Download failed with status: 403
```

### Log-Speicherort

**Pfad:** `/data/data/com.secretary.helloworld/files/app_logs.txt`

**Zugriff:**
- Für App: Interner Storage (volle Rechte)
- Für Claude Code: Lesbar via Read tool
- Für User: Nicht direkt sichtbar (nur via Settings möglich)

### Log-Rotation

**Mechanismus:**
- Maximale Zeilen: 500
- Bereinigung: ~2% Chance bei jedem Log (statistisch)
- Alternatativ: Manuelle Bereinigung über Logger-Methode

**Bereinigungsprozess:**
1. Alle Logs lesen
2. Nur letzte 500 Zeilen behalten
3. Datei neu schreiben mit Header
4. Header: `=== Logs automatisch bereinigt ===`

### Verwendung in Code

**Initialisierung:**
```java
AppLogger logger = AppLogger.getInstance(context);
```

**Logging:**
```java
// Info-Level
logger.info("TAG", "Normal information message");

// Debug-Level (für detaillierte Infos)
logger.debug("TAG", "Detailed debug information");

// Error-Level
logger.error("TAG", "Error message");
logger.error("TAG", "Error with exception", exception);
```

### Log-Kategorien

**MainActivity:**
- App-Starts
- User-Interaktionen (Button-Clicks)
- Dialog-Events
- Versionsinformationen

**UpdateChecker:**
- Update-Check Start
- GitHub API Requests/Responses
- Version-Vergleiche
- Asset-Suche
- Erfolg/Fehler bei Update-Erkennung

**UpdateInstaller:**
- Download-Start
- Download-IDs
- Download-Completion
- Installation-Start
- Fehler bei Download/Installation

### Logs auslesen (für Claude Code)

**Befehl:**
```bash
cat /data/data/com.secretary.helloworld/files/app_logs.txt
```

**Oder via Read tool:**
```
Read tool mit file_path: /data/data/com.secretary.helloworld/files/app_logs.txt
```

**Log-Analyse:**
1. Chronologische Reihenfolge beachten
2. Nach ERROR-Einträgen suchen
3. Flow zwischen Komponenten nachvollziehen
4. Timestamps für Performance-Analyse

### Best Practices

**DO:**
- ✅ Jede wichtige User-Aktion loggen
- ✅ API-Responses loggen
- ✅ Fehler mit Context loggen
- ✅ Start/Ende von Operationen markieren

**DON'T:**
- ❌ Passwörter oder sensible Daten loggen
- ❌ Excessive Logging in Loops
- ❌ User-identifizierbare Informationen
- ❌ Vollständige API-Responses (nur Status)

### Debugging-Workflow

**Typischer Ablauf:**
1. User meldet Problem
2. User reproduziert Problem in App
3. Claude liest Logs aus
4. Analyse der Log-Sequenz
5. Identifikation der Fehlerursache
6. Fix implementieren

**Beispiel-Session:**
```bash
# Logs lesen
cat /data/data/com.secretary.helloworld/files/app_logs.txt

# Nach Fehlern suchen
grep "ERROR" /data/data/com.secretary.helloworld/files/app_logs.txt

# Bestimmten Zeitraum analysieren
grep "14:05" /data/data/com.secretary.helloworld/files/app_logs.txt
```

### Performance-Überlegungen

**Impact:**
- File I/O: Minimal (gepuffert)
- Memory: ~50KB für 500 Zeilen
- CPU: Vernachlässigbar
- Battery: Keine messbare Auswirkung

**Optimierungen:**
- Asynchrones Schreiben (nicht implementiert, aber möglich)
- Batch-Writing (nicht notwendig bei aktueller Größe)
- Kompression (nicht notwendig)

---

## Version History

### v1.2 (Build 3) - 09.11.2025
**UI Overhaul with Settings Menu**
- Removed "Hello World" message
- Added Settings button (⚙) top-right
- Settings dialog with version info and update controls
- No automatic update check on startup
- Clean main screen design

### v1.1 (Build 2) - 09.11.2025
**Comprehensive Logging System**
- Added AppLogger class
- Automatic log rotation (500 lines)
- Logging integrated in all components
- Logs accessible for debugging

### v1.0 (Build 1) - 09.11.2025
**Initial Release with Auto-Update**
- GitHub Releases integration
- Automatic update detection
- One-click download & install
- Basic UI with update status

---

**Dokumentation aktualisiert:** 09.11.2025
**Status:** ✅ Update-System und Logging vollständig dokumentiert
