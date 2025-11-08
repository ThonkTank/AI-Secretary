# AI Secretary - Native Android App

Ein umfassendes Alltags-Planungstool für Android.

## Projekt-Status

🚧 **In Entwicklung** - Native Android App (ersetzt Hybrid-Prototyp)

## Dokumentation

- **[CLAUDE.md](./CLAUDE.md)** - Vollständige Projektvision und Feature-Spezifikation (🔒 geschützte Bereiche)

## Entwicklungsumgebung

### Setup (Termux auf Android)

```bash
# Projekt klonen
git clone <repository-url>
cd ai-secretary-native

# Build-Tools sind bereits installiert:
# - OpenJDK 21
# - Android Build Tools (aapt2, apksigner)
# - Git
```

### Projekt-Struktur

```
ai-secretary-native/
├── CLAUDE.md              # Projektvision & Features
├── README.md              # Dieses Dokument
├── .gitignore            # Git-Ignore-Regeln
└── app/
    └── src/
        └── main/
            ├── AndroidManifest.xml
            ├── java/com/aisecretary/taskmaster/  # Java/Kotlin Quellcode
            └── res/                               # Android Resources
                ├── layout/                        # UI Layouts
                ├── values/                        # Strings, Colors, etc.
                └── drawable/                      # Icons, Grafiken
```

## Feature-Suites

### 1. Taskmaster (In Entwicklung)
Umfassende Todo-Verwaltung mit intelligenter Tagesplanung.

Siehe [CLAUDE.md](./CLAUDE.md) für Details.

## Technologie

- **Sprache:** Kotlin oder Java (wird festgelegt)
- **Min SDK:** 23 (Android 6.0)
- **Target SDK:** 35 (Android 15)
- **Datenbank:** Room (geplant)
- **Architektur:** MVVM (geplant)

## Build

⚠️ Build-Prozess wird noch eingerichtet.

## Lizenz

TBD
