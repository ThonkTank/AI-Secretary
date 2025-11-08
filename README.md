# AI Secretary - Native Android App

Ein umfassendes Alltags-Planungstool für Android mit intelligenter Tagesplanung und Streak-Tracking.

## Projekt-Status

✅ **Produktionsreif** - Vollständig implementiert mit GitHub Actions CI/CD

![Build Status](https://github.com/YOUR_USERNAME/ai-secretary-native/workflows/Android%20CI%20Build/badge.svg)

## Features

- ✅ **Task-Management:** Einzelne, wiederkehrende und verkettete Tasks
- ✅ **Streak-Tracking:** Motivations-System mit Milestone-Achievements
- ✅ **Intelligente Planung:** KI-basierte Tagesplanung mit Multi-Faktor-Scoring
- ✅ **Home-Screen Widgets:** 3 Größen (4x4, 4x2, 2x2)
- ✅ **Statistik-Dashboard:** Produktivitäts-Analyse und Trend-Tracking
- ✅ **Dark Mode:** Vollständiger Theme-Support
- ✅ **Backup/Restore:** JSON-Export/Import

## Dokumentation

- **[CLAUDE.md](./CLAUDE.md)** - Projektvision und Feature-Spezifikation
- **[BUILD_INSTRUCTIONS.md](./BUILD_INSTRUCTIONS.md)** - Detaillierte Build-Anleitung
- **[DESIGN.md](./DESIGN.md)** - UX/UI Design Document
- **[ROADMAP.md](./ROADMAP.md)** - Entwicklungs-Roadmap (v3.9)
- **[TEST.md](./TEST.md)** - Test-Plan und Checkliste

## Quick Start

### Installation (für Benutzer)

1. Gehe zu [Releases](https://github.com/YOUR_USERNAME/ai-secretary-native/releases)
2. Lade die neueste `app-debug.apk` herunter
3. Installiere auf Android-Gerät
4. Erlaube Installation aus unbekannten Quellen

### Entwicklung

**GitHub Actions (Empfohlen):**
```bash
# 1. Repository klonen
git clone https://github.com/YOUR_USERNAME/ai-secretary-native.git
cd ai-secretary-native

# 2. Änderungen machen & committen
git add .
git commit -m "Deine Änderung"

# 3. Push zu GitHub
git push

# 4. GitHub Actions baut automatisch APK
# 5. Download APK unter Actions → neuester Run → Artifacts
```

**Android Studio:**
```bash
# 1. Repository klonen
git clone https://github.com/YOUR_USERNAME/ai-secretary-native.git

# 2. In Android Studio öffnen
# 3. Gradle Sync abwarten
# 4. Build → Build APK
```

Siehe **[BUILD_INSTRUCTIONS.md](./BUILD_INSTRUCTIONS.md)** für Details.

### Projekt-Struktur

```
ai-secretary-native/
├── .github/workflows/        # GitHub Actions CI/CD
├── app/
│   ├── build.gradle         # App-Konfiguration
│   ├── proguard-rules.pro   # Code-Obfuscation
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/aisecretary/taskmaster/  # 34 Java-Klassen
│       │   ├── database/    # SQLite DAO
│       │   ├── repository/  # Business Logic
│       │   ├── utils/       # Managers (Streak, Stats, etc.)
│       │   ├── widget/      # Home-Screen Widgets
│       │   └── service/     # Background Services
│       └── res/
│           ├── layout/      # 13 XML Layouts
│           ├── values/      # Themes, Colors, Strings
│           └── values-night/# Dark Mode
├── build.gradle             # Root Gradle Config
├── settings.gradle          # Module Settings
├── gradle.properties        # Build Properties
├── CLAUDE.md                # Projektvision
├── BUILD_INSTRUCTIONS.md    # Build-Anleitung
├── DESIGN.md                # UX/UI Design
├── ROADMAP.md               # Entwicklungs-Roadmap
└── TEST.md                  # Test-Plan
```

## Technologie-Stack

- **Sprache:** Java 11+
- **UI:** XML Layouts mit Material Design
- **Min SDK:** 23 (Android 6.0) - 36% weltweite Abdeckung
- **Target SDK:** 35 (Android 15) - Neueste API
- **Datenbank:** SQLite mit Custom DAO (kein Room)
- **Architektur:** MVVM (Model-View-ViewModel)
- **Build:** Gradle 8.4, GitHub Actions CI/CD
- **Code-Größe:** ~34 Klassen, ~6000 Zeilen

## Build & Deployment

**Status:** ✅ Produktionsreif

- **CI/CD:** GitHub Actions automatischer APK-Build
- **Signing:** Debug-Keystore konfiguriert, Release vorbereitet
- **ProGuard:** Code-Obfuscation für Release-Builds aktiviert
- **Testing:** Unit & Instrumented Test-Setup vorbereitet

**Problem:** Gradle funktioniert nicht in Termux (JVM libiconv-Fehler)
**Lösung:** GitHub Actions Cloud-Build (siehe BUILD_INSTRUCTIONS.md)

### Selbst bauen

```bash
# Option 1: GitHub Actions (empfohlen)
./setup-github.sh  # Interaktives Setup-Script

# Option 2: Android Studio
# Projekt öffnen → Build → Build APK

# Option 3: Kommandozeile (nicht in Termux)
./gradlew assembleDebug
```

## Testing

Manuelle Tests: Siehe [TEST.md](./TEST.md) (200+ Testfälle)

```bash
# Unit Tests
./gradlew test

# Instrumented Tests
./gradlew connectedAndroidTest
```

## Architektur

**MVVM Pattern:**
```
Model:      TaskEntity, CompletionHistoryEntity (SQLite)
View:       Activities, Fragments, Layouts
ViewModel:  TaskRepository (Singleton), Manager-Klassen

Layers:
├── UI Layer:         Activities, Fragments, Adapters
├── Business Layer:   Repository, Managers (Streak, Stats, Chain, etc.)
├── Data Layer:       TaskDao, CompletionHistoryDao (SQLite)
└── Service Layer:    RecurringTaskService, NotificationService
```

## Code-Qualität

- ✅ Professionelle MVVM-Architektur
- ✅ Repository Pattern für Datenzugriff
- ✅ Separation of Concerns
- ✅ Singleton Pattern für TaskRepository
- ✅ ViewHolder Pattern für RecyclerView
- ✅ Umfassendes Logging & Fehlerbehandlung

## Contributing

1. Fork das Repository
2. Erstelle Feature-Branch (`git checkout -b feature/AmazingFeature`)
3. Commit Änderungen (`git commit -m 'Add AmazingFeature'`)
4. Push zum Branch (`git push origin feature/AmazingFeature`)
5. Öffne Pull Request

## Lizenz

TBD

## Kontakt

GitHub Issues: [Issues](https://github.com/YOUR_USERNAME/ai-secretary-native/issues)

---

**Entwickelt mit [Claude Code](https://claude.com/claude-code)**
